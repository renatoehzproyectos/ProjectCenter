package com.projectcenter.app.core.zip

import com.projectcenter.app.domain.models.ProjectType
import com.projectcenter.app.domain.models.RootCandidateInfo
import com.projectcenter.app.domain.models.ZipAnalysis
import java.io.File

/**
 * Analyzes an extracted project directory: root detection, project type, workflows.
 * Reuses ZipRootDetector logic internally.
 */
object ZipAnalyzer {

    private val ANDROID_MARKERS = listOf(
        "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
        "AndroidManifest.xml", "gradlew"
    )
    private val NODE_MARKERS = listOf(
        "package.json", "vite.config.js", "vite.config.ts", "next.config.js",
        "next.config.mjs", "nuxt.config.js", "webpack.config.js"
    )
    private val PYTHON_MARKERS = listOf(
        "requirements.txt", "pyproject.toml", "setup.py", "manage.py"
    )

    fun analyze(extractedRoot: File): ZipAnalysis {
        val candidates = ZipRootDetector.detect(extractedRoot)
        val isAmbiguous = candidates.size > 1 &&
            candidates[0].score - candidates.getOrNull(1)?.score.orElse(0) < 20
        val selected = if (isAmbiguous) null else candidates.firstOrNull()
        val rootDir = selected?.file ?: extractedRoot

        val projectType = detectType(rootDir)
        val workflows = findWorkflows(rootDir)
        val (fileCount, totalSize) = countFiles(rootDir)

        return ZipAnalysis(
            extractedDir = extractedRoot,
            selectedRoot = selected,
            rootCandidates = candidates,
            isAmbiguous = isAmbiguous,
            projectType = projectType,
            hasGitHubActions = workflows.isNotEmpty(),
            workflowPaths = workflows,
            fileCount = fileCount,
            totalSizeBytes = totalSize
        )
    }

    private fun Int?.orElse(default: Int) = this ?: default

    private fun detectType(dir: File): ProjectType {
        val names = dir.walkTopDown().maxDepth(3).filter { it.isFile }.map { it.name }.toSet()
        return when {
            ANDROID_MARKERS.any { it in names } -> ProjectType.ANDROID
            NODE_MARKERS.any { it in names } -> ProjectType.NODE
            PYTHON_MARKERS.any { it in names } -> ProjectType.PYTHON
            else -> ProjectType.UNKNOWN
        }
    }

    private fun findWorkflows(dir: File): List<String> {
        val wfDir = File(dir, ".github/workflows")
        if (!wfDir.isDirectory) return emptyList()
        return wfDir.listFiles { f -> f.isFile && (f.name.endsWith(".yml") || f.name.endsWith(".yaml")) }
            ?.map { ".github/workflows/${it.name}" }
            ?: emptyList()
    }

    private fun countFiles(dir: File): Pair<Int, Long> {
        var count = 0
        var size = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                count++
                size += f.length()
            }
        }
        return count to size
    }
}

/**
 * Detects the strongest unambiguous project root inside an extracted ZIP.
 */
object ZipRootDetector {

    private val MARKERS = mapOf(
        "build.gradle.kts" to 30,
        "build.gradle" to 28,
        "settings.gradle.kts" to 25,
        "settings.gradle" to 24,
        "package.json" to 30,
        "AndroidManifest.xml" to 20,
        "gradlew" to 15,
        "pom.xml" to 20,
        "Cargo.toml" to 20,
        "go.mod" to 20,
        "pyproject.toml" to 18,
        "requirements.txt" to 12,
        "vite.config.ts" to 15,
        "vite.config.js" to 15,
        "next.config.js" to 15,
        "next.config.mjs" to 15
    )

    fun detect(extractedRoot: File): List<RootCandidateInfo> {
        val candidates = mutableListOf<RootCandidateInfo>()

        // Score the extract root itself
        scoreDir(extractedRoot, "", candidates)

        // Score immediate children (common single-folder ZIP layout)
        extractedRoot.listFiles { f -> f.isDirectory }?.forEach { child ->
            scoreDir(child, child.name, candidates)
        }

        // Also check one level deeper if extract root is empty-ish
        extractedRoot.listFiles { f -> f.isDirectory }?.forEach { child ->
            child.listFiles { f -> f.isDirectory }?.forEach { grand ->
                scoreDir(grand, "${child.name}/${grand.name}", candidates)
            }
        }

        return candidates
            .sortedByDescending { it.score }
            .distinctBy { it.relativePath }
            .filter { it.score > 0 }
            .ifEmpty {
                listOf(
                    RootCandidateInfo(
                        relativePath = "",
                        file = extractedRoot,
                        score = 1,
                        markers = listOf("(fallback root)")
                    )
                )
            }
    }

    private fun scoreDir(dir: File, relativePath: String, out: MutableList<RootCandidateInfo>) {
        val found = mutableListOf<String>()
        var score = 0
        dir.listFiles()?.forEach { f ->
            val weight = MARKERS[f.name] ?: 0
            if (weight > 0) {
                found += f.name
                score += weight
            }
        }
        // Bonus for src/ or app/ subdirs
        if (File(dir, "src").isDirectory) {
            found += "src/"
            score += 8
        }
        if (File(dir, "app").isDirectory) {
            found += "app/"
            score += 8
        }
        if (score > 0) {
            out += RootCandidateInfo(relativePath, dir, score, found)
        }
    }
}
