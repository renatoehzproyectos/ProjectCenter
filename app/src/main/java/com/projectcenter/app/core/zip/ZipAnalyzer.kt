package com.projectcenter.app.core.zip

import com.projectcenter.app.domain.models.ProjectType
import com.projectcenter.app.domain.models.RootCandidateInfo
import com.projectcenter.app.domain.models.ZipAnalysis
import java.io.File

/**
 * Orchestrates safe extraction + root detection + project type / Actions detection.
 */
object ZipAnalyzer {

    fun analyze(extractedDir: File): ZipAnalysis {
        val candidates = ZipRootDetector.detectRoots(extractedDir)
        val best = ZipRootDetector.chooseBestRoot(candidates)

        val rootCandidatesInfo = candidates.map {
            RootCandidateInfo(
                relativePath = it.relativePath,
                absolutePath = it.path.absolutePath,
                score = it.score,
                markers = it.markersFound
            )
        }

        val selectedRootInfo = best?.let {
            RootCandidateInfo(
                relativePath = it.relativePath,
                absolutePath = it.path.absolutePath,
                score = it.score,
                markers = it.markersFound
            )
        }

        val rootForAnalysis = best?.path ?: extractedDir

        val projectType = detectProjectType(rootForAnalysis)
        val (hasActions, workflows) = detectGitHubActions(rootForAnalysis)

        val fileCount = countFiles(extractedDir)
        val totalSize = calculateSize(extractedDir)

        return ZipAnalysis(
            extractedDir = extractedDir,
            rootCandidates = rootCandidatesInfo,
            selectedRoot = selectedRootInfo,
            isAmbiguous = best == null && candidates.size > 1,
            projectType = projectType,
            hasGitHubActions = hasActions,
            workflowPaths = workflows,
            fileCount = fileCount,
            totalSizeBytes = totalSize
        )
    }

    private fun detectProjectType(root: File): ProjectType {
        val names = root.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        return when {
            names.any { it == "settings.gradle" || it == "settings.gradle.kts" || it == "build.gradle" || it == "build.gradle.kts" || it == "gradlew" } ||
                    root.resolve("app").isDirectory -> ProjectType.ANDROID
            names.contains("package.json") || names.any { it.startsWith("vite.config") || it.startsWith("next.config") } -> ProjectType.NODE
            names.any { it == "pyproject.toml" || it == "requirements.txt" || it == "setup.py" } -> ProjectType.PYTHON
            else -> ProjectType.UNKNOWN
        }
    }

    private fun detectGitHubActions(root: File): Pair<Boolean, List<String>> {
        val workflowsDir = root.resolve(".github/workflows")
        if (!workflowsDir.isDirectory) return false to emptyList()
        val files = workflowsDir.listFiles()
            ?.filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
            ?.map { ".github/workflows/${it.name}" }
            ?: emptyList()
        return files.isNotEmpty() to files
    }

    private fun countFiles(dir: File): Int {
        var count = 0
        dir.walkTopDown().forEach { if (it.isFile) count++ }
        return count
    }

    private fun calculateSize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }
}
