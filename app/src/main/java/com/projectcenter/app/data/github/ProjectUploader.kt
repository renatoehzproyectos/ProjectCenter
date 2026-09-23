package com.projectcenter.app.data.github

import android.util.Base64
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

            val files = collectFiles(projectRoot)
                .filterNot { it.relativePath.startsWith(".git/") }
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
                val blobItems = mutableListOf<TreeItem>()
                // Batch large files by a byte budget instead of a fixed file-count semaphore.
                // A handful of huge files (e.g. split WASM/data chunks) held fully in memory at
                // once — raw bytes + base64 string each — is what was causing OutOfMemoryError
                // crashes on real devices. Capping in-flight bytes keeps peak memory bounded.
                var index = 0
                while (index < needBlob.size) {
                    var batchBytes = 0L
                    val batch = mutableListOf<FileEntry>()
                    while (index < needBlob.size) {
                        val candidate = needBlob[index]
                        val size = candidate.file.length()
                        if (batch.isNotEmpty() && batchBytes + size > BLOB_BYTE_BUDGET) break
                        batch.add(candidate)
                        batchBytes += size
                        index++
                    }
                    coroutineScope {
                        val semaphore = Semaphore(PARALLEL_BLOBS)
                        val results = batch.map { entry ->
                            async {
                                semaphore.withPermit {
                                    var bytes: ByteArray? = entry.file.readBytes()
                                    val base64 = Base64.encodeToString(bytes!!, Base64.NO_WRAP)
                                    bytes = null // allow GC to reclaim the raw copy before the network call
                                    val blob = api.createBlob(
                                        owner = owner,
                                        repo = repo,
                                        body = CreateBlobRequest(content = base64, encoding = "base64")
                                    )
                                    bump()
                                    TreeItem(
                                        path = entry.relativePath,
                                        mode = modeFor(entry.file),
                                        type = "blob",
                                        sha = blob.sha,
                                        content = null
                                    )
                                }
                            }
                        }.awaitAll()
                        blobItems.addAll(results)
                    }
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

    private suspend fun createTreePossiblyChunked(
        owner: String,
        repo: String,
        items: List<TreeItem>
    ): String {
        return try {
            api.createTree(
                owner = owner,
                repo = repo,
                body = CreateTreeRequest(baseTree = null, tree = items)
            ).sha
        } catch (e: HttpException) {
            if (e.code() == 422 || e.code() == 413) {
                val onlySha = items.map { item ->
                    if (item.sha != null) item
                    else {
                        val content = item.content
                            ?: throw IllegalStateException("Tree item missing content and sha: ${item.path}")
                        val base64 = Base64.encodeToString(
                            content.toByteArray(Charsets.UTF_8),
                            Base64.NO_WRAP
                        )
                        val blob = api.createBlob(
                            owner, repo,
                            CreateBlobRequest(content = base64, encoding = "base64")
                        )
                        item.copy(sha = blob.sha, content = null)
                    }
                }
                api.createTree(
                    owner = owner,
                    repo = repo,
                    body = CreateTreeRequest(baseTree = null, tree = onlySha)
                ).sha
            } else throw e
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
        private const val PARALLEL_BLOBS = 12
        private const val MAX_EMBED_BYTES = 100_000
        // Cap total raw bytes of large files processed concurrently, so a handful of huge
        // files (each held in memory as raw bytes + a ~33% bigger base64 string at once)
        // can't blow past the app's heap and crash it. Small/medium files still batch together
        // freely up to this budget; a single oversized file is still uploaded alone.
        private const val BLOB_BYTE_BUDGET = 24L * 1024 * 1024
    }
}
