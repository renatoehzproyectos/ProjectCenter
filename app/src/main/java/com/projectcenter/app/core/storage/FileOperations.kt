package com.projectcenter.app.core.storage

import com.projectcenter.app.domain.models.ManagedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Explicit, confirmed file operations for the File Manager.
 * Never silently overwrite or delete.
 */
object FileOperations {

    data class OpResult(val success: Boolean, val error: String? = null)

    suspend fun rename(path: String, newName: String): OpResult = withContext(Dispatchers.IO) {
        val src = File(path)
        if (!src.exists()) return@withContext OpResult(false, "File not found")
        val dest = File(src.parentFile, newName)
        if (dest.exists()) return@withContext OpResult(false, "A file with that name already exists")
        if (src.renameTo(dest)) OpResult(true) else OpResult(false, "Rename failed")
    }

    suspend fun delete(paths: List<String>): OpResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        for (p in paths) {
            val f = File(p)
            if (!f.exists()) continue
            val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
            if (!ok) errors += f.name
        }
        if (errors.isEmpty()) OpResult(true)
        else OpResult(false, "Failed to delete: ${errors.joinToString()}")
    }

    suspend fun createFolder(parentPath: String, name: String): OpResult = withContext(Dispatchers.IO) {
        val dest = File(parentPath, name)
        if (dest.exists()) return@withContext OpResult(false, "Already exists")
        if (dest.mkdirs()) OpResult(true) else OpResult(false, "Could not create folder")
    }

    suspend fun createFile(parentPath: String, name: String, content: String = ""): OpResult =
        withContext(Dispatchers.IO) {
            val dest = File(parentPath, name)
            if (dest.exists()) return@withContext OpResult(false, "Already exists")
            try {
                dest.writeText(content)
                OpResult(true)
            } catch (e: Exception) {
                OpResult(false, e.message)
            }
        }

    /**
     * Copy with explicit replace confirmation handled by UI.
     * @param overwrite if false and dest exists, fails.
     */
    suspend fun copy(srcPath: String, destPath: String, overwrite: Boolean = false): OpResult =
        withContext(Dispatchers.IO) {
            val src = File(srcPath)
            val dest = File(destPath)
            if (!src.exists()) return@withContext OpResult(false, "Source not found")
            if (dest.exists() && !overwrite) {
                return@withContext OpResult(false, "Destination exists")
            }
            try {
                if (src.isDirectory) {
                    src.copyRecursively(dest, overwrite = overwrite)
                } else {
                    dest.parentFile?.mkdirs()
                    FileInputStream(src).use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                }
                OpResult(true)
            } catch (e: Exception) {
                OpResult(false, e.message)
            }
        }

    suspend fun move(srcPath: String, destPath: String, overwrite: Boolean = false): OpResult =
        withContext(Dispatchers.IO) {
            val src = File(srcPath)
            val dest = File(destPath)
            if (!src.exists()) return@withContext OpResult(false, "Source not found")
            if (dest.exists() && !overwrite) {
                return@withContext OpResult(false, "Destination exists")
            }
            try {
                if (dest.exists()) {
                    if (dest.isDirectory) dest.deleteRecursively() else dest.delete()
                }
                dest.parentFile?.mkdirs()
                if (src.renameTo(dest)) {
                    OpResult(true)
                } else {
                    // Cross-filesystem fallback
                    val copy = copy(srcPath, destPath, overwrite = true)
                    if (copy.success) {
                        if (src.isDirectory) src.deleteRecursively() else src.delete()
                        OpResult(true)
                    } else copy
                }
            } catch (e: Exception) {
                OpResult(false, e.message)
            }
        }

    /**
     * Organize related files into a shared folder. Reuses existing folder if present.
     */
    suspend fun organizeIntoFolder(
        files: List<ManagedFile>,
        parentDir: String,
        folderName: String
    ): OpResult = withContext(Dispatchers.IO) {
        val destDir = File(parentDir, folderName)
        if (!destDir.exists()) {
            if (!destDir.mkdirs()) return@withContext OpResult(false, "Could not create $folderName")
        }
        val errors = mutableListOf<String>()
        for (f in files) {
            // Skip if already inside the target folder
            if (f.path.startsWith(destDir.absolutePath + File.separator) ||
                f.path == destDir.absolutePath
            ) continue
            val dest = File(destDir, f.name)
            val result = move(f.path, dest.absolutePath, overwrite = false)
            if (!result.success) {
                // Name collision inside folder — keep original name with note
                errors += "${f.name}: ${result.error}"
            }
        }
        if (errors.isEmpty()) OpResult(true)
        else OpResult(false, errors.joinToString("\n"))
    }
}
