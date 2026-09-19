package com.projectcenter.app.core.zip

/**
 * Extracts the most useful error lines from a build log.
 * Prioritizes compiler errors, FAILED tasks, exceptions, and file:line references.
 */
object ErrorExtractor {

    private val IMPORTANT_PATTERNS = listOf(
        Regex(""".*\bERROR\b.*""", RegexOption.IGNORE_CASE),
        Regex(""".*\bFAILURE\b.*""", RegexOption.IGNORE_CASE),
        Regex(""".*\bFAILED\b.*""", RegexOption.IGNORE_CASE),
        Regex(""".*> Task :.* FAILED.*"""),
        Regex(""".*e: .*"""),                          // Kotlin compiler
        Regex(""".*error: .*""", RegexOption.IGNORE_CASE),
        Regex(""".*Exception.*"""),
        Regex(""".*\.kt:\d+:\d+.*"""),
        Regex(""".*\.java:\d+:\d+.*"""),
        Regex(""".*Unresolved reference:.*"""),
        Regex(""".*BUILD FAILED.*""")
    )

    fun extractImportantLines(fullLog: String, maxLines: Int = 40): String {
        val lines = fullLog.lines()
        val important = mutableListOf<String>()

        lines.forEachIndexed { index, line ->
            if (IMPORTANT_PATTERNS.any { it.matches(line) || it.containsMatchIn(line) }) {
                // Include a little context
                val start = (index - 1).coerceAtLeast(0)
                val end = (index + 2).coerceAtMost(lines.lastIndex)
                for (i in start..end) {
                    val candidate = lines[i]
                    if (candidate !in important) important.add(candidate)
                }
            }
        }

        return if (important.isEmpty()) {
            // Fallback: last 30 lines
            lines.takeLast(30).joinToString("\n")
        } else {
            important.take(maxLines).joinToString("\n")
        }
    }
}
