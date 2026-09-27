package com.projectcenter.app.core.zip

/**
 * Extracts a short, user-facing error message from throwable chains.
 */
object ErrorExtractor {
    fun message(t: Throwable?, fallback: String = "Unknown error"): String {
        if (t == null) return fallback
        val msg = t.message?.trim().orEmpty()
        if (msg.isNotEmpty()) return msg.take(200)
        return t.javaClass.simpleName.ifEmpty { fallback }
    }
}
