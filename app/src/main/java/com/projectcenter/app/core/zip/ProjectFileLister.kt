package com.projectcenter.app.core.zip

import java.io.File

/**
 * Lists project files the same way for both the uploader and the pre-push diff check,
 * so "what will be uploaded" and "what will be compared against the repo" never disagree.
 */
object ProjectFileLister {

    fun listRelativePaths(root: File): List<String> {
        val result = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relative = file.relativeTo(root).path.replace('\\', '/')
                if (relative.isNotBlank() && !relative.startsWith(".git/")) {
                    result.add(relative)
                }
            }
        return result
    }
}
