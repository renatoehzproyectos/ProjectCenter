package com.projectcenter.app.core.zip

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Safe ZIP extraction that prevents Zip Slip attacks.
 * Never extracts outside the target directory.
 */
object SafeZipExtractor {

    private const val MAX_ENTRY_SIZE = 500L * 1024 * 1024 // 500 MB single file limit
    private const val MAX_TOTAL_SIZE = 2L * 1024 * 1024 * 1024 // 2 GB total

    data class ExtractResult(
        val success: Boolean,
        val extractedRoot: File?,
        val fileCount: Int,
        val error: String? = null
    )

    fun extract(zipFile: File, targetDir: File): ExtractResult {
        if (!zipFile.exists() || !zipFile.canRead()) {
            return ExtractResult(false, null, 0, "ZIP file not readable")
        }

        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        var totalBytes = 0L
        var fileCount = 0

        try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    // Zip Slip protection
                    val destFile = File(targetDir, name)
                    val destCanonical = destFile.canonicalPath
                    val targetCanonical = targetDir.canonicalPath

                    if (!destCanonical.startsWith(targetCanonical + File.separator) && destCanonical != targetCanonical) {
                        return ExtractResult(false, null, fileCount, "Zip Slip detected: $name")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        var entrySize = 0L
                        FileOutputStream(destFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                entrySize += len
                                totalBytes += len
                                if (entrySize > MAX_ENTRY_SIZE) {
                                    return ExtractResult(false, null, fileCount, "Entry too large: $name")
                                }
                                if (totalBytes > MAX_TOTAL_SIZE) {
                                    return ExtractResult(false, null, fileCount, "ZIP total size exceeded limit")
                                }
                                fos.write(buffer, 0, len)
                            }
                        }
                        fileCount++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            return ExtractResult(true, targetDir, fileCount)
        } catch (e: Exception) {
            return ExtractResult(false, null, fileCount, e.message ?: "Extraction failed")
        }
    }
}
