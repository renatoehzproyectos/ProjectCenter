package com.projectcenter.app.core.zip

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.ZipFile

/**
 * Safe ZIP extraction (Zip-Slip protected). Entries are inflated in parallel using a
 * large copy buffer, which is much faster than single-threaded 8 KB copies for 1 GB+ ZIPs.
 */
object SafeZipExtractor {

    data class ExtractResult(
        val success: Boolean,
        val extractedRoot: File? = null,
        val error: String? = null,
        val fileCount: Int = 0
    )

    private const val BUFFER = 256 * 1024

    fun extract(zipFile: File, destDir: File): ExtractResult {
        if (!zipFile.exists()) {
            return ExtractResult(false, error = "ZIP file not found")
        }
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val pool = Executors.newFixedThreadPool(threads)
        return try {
            ZipFile(zipFile).use { zip ->
                val jobs = ArrayList<java.util.concurrent.Future<*>>()
                var count = 0
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val target = File(destDir, entry.name)
                    val canonicalTarget = target.canonicalPath
                    if (!canonicalTarget.startsWith(canonicalDest + File.separator) &&
                        canonicalTarget != canonicalDest
                    ) {
                        return ExtractResult(false, error = "Zip-Slip blocked: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    count++
                    jobs += pool.submit(Callable {
                        val buf = ByteArray(BUFFER)
                        zip.getInputStream(entry).use { input ->
                            target.outputStream().buffered(BUFFER).use { output ->
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    output.write(buf, 0, n)
                                }
                            }
                        }
                    })
                }
                for (j in jobs) j.get() // rethrows any extraction failure
                ExtractResult(success = true, extractedRoot = destDir, fileCount = count)
            }
        } catch (e: Exception) {
            val cause = e.cause?.message ?: e.message
            ExtractResult(false, error = cause ?: "Extraction failed")
        } finally {
            pool.shutdownNow()
        }
    }
}
