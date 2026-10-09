package com.projectcenter.app.data.github

import android.util.Base64
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import com.projectcenter.app.domain.models.UpdateMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import retrofit2.HttpException
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fast upload via Git Data API with parallel blobs.
 * Uses [channelFlow] so progress can be emitted safely from concurrent workers.
 */
class ProjectUploader(
    private val api: GitHubApi
) {

    sealed class UploadProgress {
        data class Preparing(val message: String) : UploadProgress()
        data class UploadingFiles(val current: Int, val total: Int) : UploadProgress()
        data class CreatingTree(val message: String) : UploadProgress()
        data class Committing(val message: String) : UploadProgress()
        /** Files GitHub cannot accept (>100 MB) that were left out of the commit. */
        data class Skipped(val files: List<String>) : UploadProgress()
        data class Success(val commitSha: String) : UploadProgress()
        data class Error(val message: String, val cause: Throwable? = null) : UploadProgress()
    }

    fun upload(
        owner: String,
        repo: String,
        projectRoot: File,
        branch: String = "main",
        commitMessage: String = "Update project from Project Center",
        mode: UpdateMode = UpdateMode.REPLACE
    ): Flow<UploadProgress> = channelFlow {
        try {
            send(UploadProgress.Preparing("Collecting files…"))

            val allFiles = collectFiles(projectRoot)
                .filterNot { it.relativePath.startsWith(".git/") }
            // GitHub hard-rejects any single file over 100 MB (Git LFS is required for those).
            val (tooBig, files) = allFiles.partition { it.file.length() > GITHUB_MAX_FILE_BYTES }
            if (tooBig.isNotEmpty()) {
                send(UploadProgress.Skipped(tooBig.map {
                    "${it.relativePath} (${it.file.length() / (1024 * 1024)} MB)"
                }))
            }
            if (files.isEmpty()) {
                send(UploadProgress.Error("No files found in project root"))
                return@channelFlow
            }

            send(UploadProgress.Preparing("Preparing repository…"))
            ensureRepoHasCommit(owner, repo, branch)

            val total = files.size
            val done = AtomicInteger(0)

            suspend fun bump() {
                val n = done.incrementAndGet()
                if (n == total || n % 3 == 0 || n <= 5) {
                    send(UploadProgress.UploadingFiles(n, total))
                }
            }

            send(UploadProgress.UploadingFiles(0, total))

            val embeddable = mutableListOf<FileEntry>()
            val needBlob = mutableListOf<FileEntry>()
            for (entry in files) {
                if (canEmbedInTree(entry.file)) embeddable.add(entry)
                else needBlob.add(entry)
            }

            val treeItems = mutableListOf<TreeItem>()

            for (entry in embeddable) {
                val text = entry.file.readText(Charsets.UTF_8)
                treeItems.add(
                    TreeItem(
                        path = entry.relativePath,
                        mode = modeFor(entry.file),
                        type = "blob",
                        sha = null,
                        content = text
                    )
                )
                bump()
            }

            if (needBlob.isNotEmpty()) {
                send(UploadProgress.Preparing("Uploading ${needBlob.size} binary/large files…"))
                // Files are streamed from disk straight into the HTTP body (base64 encoded on the
                // fly), so memory use per upload is a few KB no matter how large the file is.
                // That lets us run a plain work-queue with high parallelism instead of
                // memory-bounded batches. Largest files go first so they never become the tail.
                val ordered = needBlob.sortedByDescending { it.file.length() }
                val semaphore = Semaphore(PARALLEL_BLOBS)
                val blobItems = coroutineScope {
                    ordered.map { entry ->
                        async {
                            semaphore.withPermit {
                                val sha = uploadBlobStreaming(owner, repo, entry.file)
                                bump()
                                TreeItem(
                                    path = entry.relativePath,
                                    mode = modeFor(entry.file),
                                    type = "blob",
                                    sha = sha,
                                    content = null
                                )
                            }
                        }
                    }.awaitAll()
                }
                treeItems.addAll(blobItems)
            }

            // Ensure final progress shows complete
            send(UploadProgress.UploadingFiles(total, total))
            send(UploadProgress.CreatingTree("Building tree (${treeItems.size} entries)…"))

            val parentSha = resolveHeadSha(owner, repo, branch)
                ?: throw IllegalStateException("Repository still has no branch after bootstrap")

            val treeSha = createTreePossiblyChunked(owner, repo, treeItems)

            send(UploadProgress.Committing("Creating commit…"))

            val commit = api.createCommit(
                owner = owner,
                repo = repo,
                body = CreateCommitRequest(
                    message = commitMessage,
                    tree = treeSha,
                    parents = listOf(parentSha)
                )
            )

            api.updateRef(
                owner = owner,
                repo = repo,
                branch = branch,
                body = UpdateRefRequest(sha = commit.sha, force = true)
            )

            send(UploadProgress.Success(commit.sha))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // preserve structured-concurrency cancellation; don't swallow it
        } catch (e: Throwable) {
            // Throwable (not just Exception) so an OutOfMemoryError from a huge file no longer
            // crashes the whole app — it's reported as a normal, recoverable upload error instead.
            send(UploadProgress.Error(friendlyError(e), e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Builds the tree in bounded chunks (entry count and inline-content bytes), chaining each
     * chunk onto the previous one via base_tree. Keeps every request small even for repos with
     * tens of thousands of files.
     */
    private suspend fun createTreePossiblyChunked(
        owner: String,
        repo: String,
        items: List<TreeItem>
    ): String {
        var baseTree: String? = null
        var chunk = ArrayList<TreeItem>()
        var chunkBytes = 0L

        suspend fun flush() {
            if (chunk.isEmpty()) return
            baseTree = withRetry {
                api.createTree(owner, repo, CreateTreeRequest(baseTree = baseTree, tree = chunk)).sha
            }
            chunk = ArrayList()
            chunkBytes = 0L
        }

        for (item in items) {
            val inline = item.content?.length?.toLong() ?: 0L
            if (chunk.isNotEmpty() &&
                (chunk.size >= TREE_CHUNK_ENTRIES || chunkBytes + inline > TREE_CHUNK_BYTES)
            ) flush()
            chunk.add(item)
            chunkBytes += inline
        }
        flush()
        return baseTree ?: throw IllegalStateException("Nothing to commit")
    }

    /** Uploads one file as a git blob without ever holding it in memory. */
    private suspend fun uploadBlobStreaming(owner: String, repo: String, file: File): String =
        withRetry {
            api.createBlobStream(owner, repo, Base64JsonBody(file)).sha
        }

    /**
     * Retries transient failures: network errors, 5xx, and GitHub's secondary rate limit
     * (403/429), honoring Retry-After / x-ratelimit-reset when provided.
     */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: HttpException) {
                val code = e.code()
                val retryable = code == 429 || code in 500..599 ||
                    (code == 403 && isRateLimited(e))
                if (!retryable || ++attempt > MAX_RETRIES) throw e
                delay(retryDelayMs(e, attempt))
            } catch (e: IOException) {
                if (++attempt > MAX_RETRIES) throw e
                delay(backoffMs(attempt))
            }
        }
    }

    private fun isRateLimited(e: HttpException): Boolean {
        val h = e.response()?.headers() ?: return false
        if (h["Retry-After"] != null) return true
        if (h["x-ratelimit-remaining"] == "0") return true
        // Body is not re-read here (it may already be consumed); header check is enough
        return false
    }

    private fun retryDelayMs(e: HttpException, attempt: Int): Long {
        val h = e.response()?.headers()
        h?.get("Retry-After")?.toLongOrNull()?.let { return (it * 1000).coerceIn(1_000, 120_000) }
        if (h?.get("x-ratelimit-remaining") == "0") {
            h["x-ratelimit-reset"]?.toLongOrNull()?.let { reset ->
                val wait = reset * 1000 - System.currentTimeMillis()
                return wait.coerceIn(1_000, 120_000)
            }
        }
        return backoffMs(attempt)
    }

    private fun backoffMs(attempt: Int): Long =
        (1_000L shl (attempt - 1).coerceAtMost(5)).coerceAtMost(30_000L)

    /**
     * JSON body `{"encoding":"base64","content":"<file as base64>"}` streamed from disk.
     * contentLength is computed exactly up front so OkHttp sends a fixed-length request
     * (no chunked encoding, no buffering).
     */
    private class Base64JsonBody(private val file: File) : RequestBody() {
        private val prefix = """{"encoding":"base64","content":"""".toByteArray()
        private val suffix = """"}""".toByteArray()

        override fun contentType() = "application/json".toMediaType()

        override fun contentLength(): Long {
            val n = file.length()
            return prefix.size + ((n + 2) / 3) * 4 + suffix.size
        }

        override fun writeTo(sink: BufferedSink) {
            sink.write(prefix)
            val enc = java.util.Base64.getEncoder().wrap(object : java.io.OutputStream() {
                override fun write(b: Int) { sink.writeByte(b) }
                override fun write(b: ByteArray, off: Int, len: Int) { sink.write(b, off, len) }
            })
            file.inputStream().use { input ->
                val buf = ByteArray(192 * 1024) // multiple of 3 -> clean base64 groups
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    enc.write(buf, 0, n)
                }
            }
            enc.close() // flushes final padding; does not close sink
            sink.write(suffix)
        }
    }

    private fun canEmbedInTree(file: File): Boolean {
        if (file.length() > MAX_EMBED_BYTES) return false
        val name = file.name.lowercase()
        val binaryExt = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "zip", "jar", "apk",
            "so", "dylib", "a", "o", "class", "dex", "bin", "mp3", "mp4", "ttf", "otf",
            "woff", "woff2", "7z", "gz", "xz", "aar", "keystore", "jks"
        )
        val ext = name.substringAfterLast('.', "")
        if (ext in binaryExt) return false
        val probe = file.inputStream().use { it.readNBytes(512) }
        if (probe.any { it == 0.toByte() }) return false
        return true
    }

    private fun modeFor(file: File): String =
        if (file.canExecute()) "100755" else "100644"

    private suspend fun ensureRepoHasCommit(owner: String, repo: String, branch: String) {
        if (resolveHeadSha(owner, repo, branch) != null) return

        val marker = Base64.encodeToString(
            "Initialized by Project Center\n".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        try {
            api.createOrUpdateFile(
                owner = owner,
                repo = repo,
                path = ".projectcenter",
                body = CreateFileRequest(
                    message = "Initialize repository",
                    content = marker,
                    branch = branch
                )
            )
        } catch (e: HttpException) {
            if (e.code() != 422) throw e
        }

        if (resolveHeadSha(owner, repo, branch) == null &&
            resolveHeadSha(owner, repo, "master") == null
        ) {
            throw IllegalStateException(
                "Could not initialize empty repository. Create it with a README on GitHub, then retry."
            )
        }
    }

    private suspend fun resolveHeadSha(owner: String, repo: String, preferred: String): String? {
        for (b in listOf(preferred, "main", "master").distinct()) {
            try {
                return api.getRef(owner, repo, b).`object`.sha
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun friendlyError(e: Throwable): String {
        if (e is OutOfMemoryError) {
            return "Ran out of memory while uploading a large file. Close other apps and try again, or split very large files further."
        }
        if (e is HttpException) {
            val body = try {
                e.response()?.errorBody()?.string()?.take(300)
            } catch (_: Exception) {
                null
            }
            val detail = body?.let { " — $it" } ?: ""
            return when (e.code()) {
                401, 403 -> "Permission denied (HTTP ${e.code()}). Token needs repo scope.$detail"
                404 -> "Repository or ref not found (HTTP 404).$detail"
                409 -> {
                    if (body?.contains("empty", ignoreCase = true) == true)
                        "Repository was empty — retry upload.$detail"
                    else "Git conflict (HTTP 409).$detail"
                }
                422 -> "GitHub rejected the request (HTTP 422).$detail"
                else -> "HTTP ${e.code()}$detail"
            }
        }
        return e.message ?: "Upload failed"
    }

    private data class FileEntry(val file: File, val relativePath: String)

    private fun collectFiles(root: File): List<FileEntry> =
        com.projectcenter.app.core.zip.ProjectFileLister.listRelativePaths(root)
            .map { relative -> FileEntry(File(root, relative), relative) }

    companion object {
        // Memory is constant per upload now (streaming), so we can run many at once.
        private const val PARALLEL_BLOBS = 16
        private const val MAX_EMBED_BYTES = 100_000
        private const val GITHUB_MAX_FILE_BYTES = 100L * 1024 * 1024
        private const val TREE_CHUNK_ENTRIES = 2_000
        private const val TREE_CHUNK_BYTES = 2L * 1024 * 1024
        private const val MAX_RETRIES = 5
    }
}
