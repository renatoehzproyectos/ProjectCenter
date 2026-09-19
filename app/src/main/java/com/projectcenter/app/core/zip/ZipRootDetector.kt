package com.projectcenter.app.core.zip

import java.io.File

/**
 * Detects the real project root inside an extracted ZIP.
 * Avoids nested folders like ZIP/root/actual-project/...
 */
object ZipRootDetector {

    private val ANDROID_MARKERS = setOf(
        "settings.gradle", "settings.gradle.kts",
        "build.gradle", "build.gradle.kts",
        "gradlew", "gradlew.bat",
        "app"
    )

    private val NODE_MARKERS = setOf(
        "package.json", "vite.config.js", "vite.config.ts",
        "next.config.js", "next.config.mjs", "next.config.ts",
        "src", "public"
    )

    private val PYTHON_MARKERS = setOf(
        "pyproject.toml", "requirements.txt", "setup.py", "manage.py"
    )

    data class RootCandidate(
        val path: File,
        val relativePath: String,
        val score: Int,
        val markersFound: List<String>
    )

    /**
     * Analyzes the extracted directory and returns possible roots sorted by confidence.
     */
    fun detectRoots(extractedDir: File): List<RootCandidate> {
        val candidates = mutableListOf<RootCandidate>()
        walkAndScore(extractedDir, extractedDir, candidates, depth = 0, maxDepth = 4)
        return candidates.sortedByDescending { it.score }
    }

    private fun walkAndScore(
        root: File,
        current: File,
        out: MutableList<RootCandidate>,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth || !current.isDirectory) return

        val children = current.listFiles() ?: return
        val names = children.map { it.name }.toSet()

        val markers = mutableListOf<String>()
        var score = 0

        ANDROID_MARKERS.forEach { m ->
            if (names.contains(m) || (m == "app" && children.any { it.isDirectory && it.name == "app" })) {
                markers.add(m)
                score += if (m.startsWith("settings") || m.startsWith("build") || m == "gradlew") 10 else 5
            }
        }
        NODE_MARKERS.forEach { m ->
            if (names.contains(m) || names.any { it.startsWith(m.removeSuffix(".*")) }) {
                markers.add(m)
                score += 8
            }
        }
        PYTHON_MARKERS.forEach { m ->
            if (names.contains(m)) {
                markers.add(m)
                score += 7
            }
        }

        // Prefer directories that look like real projects
        if (score > 0) {
            val relative = root.toURI().relativize(current.toURI()).path.trimEnd('/')
            out.add(RootCandidate(current, relative.ifEmpty { "." }, score, markers))
        }

        // Continue only if we haven't found a strong root or to find nested ones
        children.filter { it.isDirectory && !it.name.startsWith(".") }.forEach { child ->
            walkAndScore(root, child, out, depth + 1, maxDepth)
        }
    }

    /**
     * Chooses the best single root. If multiple strong candidates, caller should ask the user.
     */
    fun chooseBestRoot(candidates: List<RootCandidate>): RootCandidate? {
        if (candidates.isEmpty()) return null
        val best = candidates.first()
        // If second is close, treat as ambiguous
        if (candidates.size > 1 && candidates[1].score >= best.score - 3) {
            return null // signal ambiguity
        }
        return best
    }
}
