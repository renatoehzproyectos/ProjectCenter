package com.projectcenter.app.ui.theme

enum class AppThemeOption(
    val id: String,
    val displayName: String,
    val description: String
) {
    VERCEL_DARK("vercel_dark", "Vercel Dark", "Black primary · White accent"),
    VERCEL_LIGHT("vercel_light", "Vercel Light", "White primary · Black accent"),
    MIDNIGHT("midnight", "Midnight", "Deep indigo developer theme"),
    OCEAN("ocean", "Ocean", "Cool blue night theme"),
    FOREST("forest", "Forest", "Green terminal vibe"),
    ROSE("rose", "Rose", "Warm rose dark theme");

    companion object {
        fun fromId(id: String?): AppThemeOption =
            entries.find { it.id == id } ?: VERCEL_DARK
    }
}
