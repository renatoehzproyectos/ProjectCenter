package com.projectcenter.app.core.storage

import com.projectcenter.app.domain.models.ManagedFile
import com.projectcenter.app.domain.models.RelatedFileGroup
import java.io.File
import java.util.zip.ZipFile

/**
 * Conservative related-file detection for the Downloads folder.
 * Uses multiple signals; never auto-deletes or auto-moves.
 */
object RelatedFileDetector {

    private val VERSION_SUFFIXES = listOf(
        Regex("""[-_]?(debug|release|prod|dev)$""", RegexOption.IGNORE_CASE),
        Regex("""[-_]?v\d+(\.\d+)*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(\d+\)$"""),
        Regex("""[-_]?(copy|backup|old|new)$""", RegexOption.IGNORE_CASE),
        Regex("""[-_]?\d{8}$"""), // date-like
        Regex("""[-_]?\d{10,}$""") // timestamp-like
    )

    private val PROJECT_EXTENSIONS = setOf("zip", "apk", "json", "aab")

    fun detectGroups(files: List<ManagedFile>): List<RelatedFileGroup> {
        if (files.isEmpty()) return emptyList()

        // Normalize → cluster
        val byKey = linkedMapOf<String, MutableList<ManagedFile>>()
        for (f in files) {
            val key = normalizeName(f.name)
            if (key.length < 2) continue
            // Skip obviously non-project personal files unless they share a strong key
            if (!isLikelyProjectFile(f) && files.none { normalizeName(it.name) == key && isLikelyProjectFile(it) }) {
                continue
            }
            byKey.getOrPut(key) { mutableListOf() }.add(f)
        }

        val groups = mutableListOf<RelatedFileGroup>()
        for ((key, members) in byKey) {
            if (members.size < 2) continue
            val confidence = computeConfidence(key, members)
            if (confidence < 0.35f) continue
            groups += RelatedFileGroup(
                name = key.replaceFirstChar { it.uppercase() },
                files = members.sortedByDescending { it.lastModified },
                confidence = confidence
            )
        }
        return groups.sortedByDescending { it.confidence }
    }

    /** Normalize filename for grouping. */
    fun normalizeName(raw: String): String {
        var name = raw
        // Strip extension for comparison key
        val dot = name.lastIndexOf('.')
        if (dot > 0) name = name.substring(0, dot)
        // Remove version/build/download suffixes iteratively
        var changed = true
        while (changed) {
            changed = false
            for (rx in VERSION_SUFFIXES) {
                val next = name.replace(rx, "")
                if (next != name && next.isNotBlank()) {
                    name = next
                    changed = true
                }
            }
        }
        return name.trim().lowercase().replace(Regex("""[\s_]+"""), "")
    }

    private fun isLikelyProjectFile(f: ManagedFile): Boolean {
        if (f.isDirectory) return true
        val ext = f.extension?.lowercase()
        return ext in PROJECT_EXTENSIONS
    }

    private fun computeConfidence(key: String, members: List<ManagedFile>): Float {
        var score = 0.4f // base for shared normalized name

        // Extension diversity among project types
        val exts = members.mapNotNull { it.extension }.toSet()
        if (exts.size >= 2) score += 0.1f

        // Directory with matching name boosts confidence
        if (members.any { it.isDirectory && normalizeName(it.name) == key }) {
            score += 0.2f
        }

        // Similar sizes among ZIPs
        val zipSizes = members.filter { it.extension == "zip" }.map { it.sizeBytes }
        if (zipSizes.size >= 2) {
            val avg = zipSizes.average()
            if (avg > 0 && zipSizes.all { kotlin.math.abs(it - avg) / avg < 0.35 }) {
                score += 0.1f
            }
        }

        // ZIP content root matching (best-effort, only first zip to stay lightweight)
        val zip = members.firstOrNull { it.extension == "zip" }
        if (zip != null) {
            try {
                ZipFile(File(zip.path)).use { zf ->
                    val topDirs = zf.entries().asSequence()
                        .map { it.name.substringBefore('/') }
                        .filter { it.isNotBlank() }
                        .toSet()
                    if (topDirs.any { normalizeName(it) == key }) {
                        score += 0.2f
                    }
                }
            } catch (_: Exception) {
                // ignore unreadable zips
            }
        }

        return score.coerceIn(0f, 1f)
    }
}
