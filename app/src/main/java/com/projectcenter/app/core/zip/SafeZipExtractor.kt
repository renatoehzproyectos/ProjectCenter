package com.projectcenter.app.core.zip

import java.io.File
import java.util.zip.ZipFile

/**
 * Safe ZIP extraction that guards against Zip-Slip path traversal.
 */
object SafeZipExtractor {

    data class ExtractResult(
        val success: Boolean,
        val extractedRoot: File? = null,
        val error: String? = null,
        val fileCount: Int = 0
    )

    fun extract(zipFile: File, destDir: File): ExtractResult {
        if (!zipFile.exists()) {
            return ExtractResult(false, error = "ZIP file not found")
        }
        destDir.mkdirs()
        var count = 0
        return try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val target = File(destDir, entry.name)
                    // Zip-Slip protection
                    val canonicalDest = destDir.canonicalPath
                    val canonicalTarget = target.canonicalPath
                    if (!canonicalTarget.startsWith(canonicalDest + File.separator) &&
                        canonicalTarget != canonicalDest
                    ) {
                        return ExtractResult(false, error = "Zip-Slip blocked: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            target.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        count++
                    }
                }
            }
            ExtractResult(success = true, extractedRoot = destDir, fileCount = count)
        } catch (e: Exception) {
            ExtractResult(false, error = e.message ?: "Extraction failed")
        }
    }
}
