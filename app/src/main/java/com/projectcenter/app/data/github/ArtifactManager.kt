package com.projectcenter.app.data.github

import com.projectcenter.app.core.zip.SafeZipExtractor
import com.projectcenter.app.domain.models.Artifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream

/**
 * Downloads GitHub Actions artifacts and finds APKs inside them.
 */
class ArtifactManager(
    private val api: GitHubApi,
    private val cacheDir: File
) {

    sealed class DownloadProgress {
        data class Starting(val name: String) : DownloadProgress()
        data class Downloading(val bytes: Long) : DownloadProgress()
        data object Extracting : DownloadProgress()
        data class Success(
            val artifactDir: File,
            val apkFiles: List<File>
        ) : DownloadProgress()
        data class Error(val message: String) : DownloadProgress()
    }

    fun downloadAndExtract(
        owner: String,
        repo: String,
        artifact: Artifact
    ): Flow<DownloadProgress> = flow {
        try {
            emit(DownloadProgress.Starting(artifact.name))

            val zipFile = File(cacheDir, "artifact_${artifact.id}.zip")
            if (zipFile.exists()) zipFile.delete()

            api.downloadArtifact(owner, repo, artifact.id).byteStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        total += read
                        emit(DownloadProgress.Downloading(total))
                    }
                }
            }

            emit(DownloadProgress.Extracting)

            val extractDir = File(cacheDir, "artifact_extract_${artifact.id}")
            if (extractDir.exists()) extractDir.deleteRecursively()

            val result = SafeZipExtractor.extract(zipFile, extractDir)
            if (!result.success || result.extractedRoot == null) {
                emit(DownloadProgress.Error(result.error ?: "Extraction failed"))
                return@flow
            }

            val apks = findApks(result.extractedRoot)
            emit(DownloadProgress.Success(result.extractedRoot, apks))
        } catch (e: Exception) {
            emit(DownloadProgress.Error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    fun findApks(dir: File): List<File> {
        return dir.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .sortedByDescending { it.length() }
            .toList()
    }
}
