package com.projectcenter.app.core.zip

import java.io.File

/**
 * Lists relative file paths under a project root (for replace-mode diffing).
 */
object ProjectFileLister {

    private val SKIP_DIRS = setOf(".git", "node_modules", "build", ".gradle", ".idea")

    fun listRelativePaths(root: File): List<String> {
        if (!root.exists()) return emptyList()
        val paths = mutableListOf<String>()
        root.walkTopDown()
            .onEnter { dir -> dir.name !in SKIP_DIRS }
            .forEach { f ->
                if (f.isFile) {
                    val rel = f.relativeTo(root).path.replace('\\', '/')
                    paths += rel
                }
            }
        return paths
    }
}
