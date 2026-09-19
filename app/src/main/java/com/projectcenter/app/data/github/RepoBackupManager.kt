package com.projectcenter.app.data.github

import android.content.Context
import com.projectcenter.app.core.storage.DownloadsWriter
import com.projectcenter.app.domain.models.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads a full ZIP snapshot of a repository (via GitHub's zipball export) and
 * saves it into the device's Downloads folder, so a deleted repository can always
 * be restored by re-pushing that same ZIP from Home.
 */
class RepoBackupManager(
    private val githubRepo: GitHubRepository
) {
    sealed class BackupResult {
        data class Success(val fileName: String) : BackupResult()
        data class Error(val message: String) : BackupResult()
    }

    suspend fun backup(context: Context, repository: Repository): BackupResult = withContext(Dispatchers.IO) {
        val ref = repository.defaultBranch.ifBlank { "main" }
        val result = githubRepo.downloadRepositoryZipball(repository.owner, repository.name, ref)
        val body = result.getOrElse {
            return@withContext BackupResult.Error(it.message ?: "Could not download repository ZIP")
        }
        try {
            body.byteStream().use { input ->
                val fileName = DownloadsWriter.timestampedName(repository.name, "zip")
                when (val write = DownloadsWriter.writeStream(context, fileName, "application/zip", input)) {
                    is DownloadsWriter.WriteResult.Success -> BackupResult.Success(write.displayName)
                    is DownloadsWriter.WriteResult.Error -> BackupResult.Error(write.message)
                }
            }
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Backup failed")
        } finally {
            body.close()
        }
    }
}
