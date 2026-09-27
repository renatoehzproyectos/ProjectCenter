package com.projectcenter.app.core.zip

/**
 * Extracts a short, user-facing error message from throwable chains or build logs.
 */
object ErrorExtractor {
    fun message(t: Throwable?, fallback: String = "Unknown error"): String {
        if (t == null) return fallback
        val msg = t.message?.trim().orEmpty()
        if (msg.isNotEmpty()) return msg.take(200)
        return t.javaClass.simpleName.ifEmpty { fallback }
    }

    /**
     * Pulls the most useful lines from a GitHub Actions job log
     * (error/failure/exception lines, last non-empty chunk).
     */
    fun extractImportantLines(log: String, maxLines: Int = 12): String {
        if (log.isBlank()) return "No log content"
        val lines = log.lines()
        val interesting = lines.filter { line ->
            val l = line.lowercase()
            "error" in l || "exception" in l || "failed" in l || "failure" in l ||
                "e:" in l || "fatal" in l || "what went wrong" in l
        }
        val selected = if (interesting.isNotEmpty()) {
            interesting.takeLast(maxLines)
        } else {
            lines.filter { it.isNotBlank() }.takeLast(maxLines)
        }
        return selected.joinToString("\n").take(1500).ifBlank { "No log content" }
    }
}
