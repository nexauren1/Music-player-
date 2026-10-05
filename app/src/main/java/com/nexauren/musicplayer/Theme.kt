package com.musicplayer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class AppThemeStyle(val label: String) {
    VIOLET("Violet"), OCEAN("Ocean"), SUNSET("Sunset"), MINT("Mint")
}

enum class AppBackgroundStyle(val label: String) {
    CLEAN("Clean"), GRADIENT("Gradient"), AURORA("Aurora"), MIDNIGHT("Midnight")
}

data class AppearanceState(
    val theme: AppThemeStyle = AppThemeStyle.VIOLET,
    val background: AppBackgroundStyle = AppBackgroundStyle.GRADIENT,
    val darkMode: Boolean = false,
    val configured: Boolean = false
)

object AppearanceStore {
    private const val PREFS = "appearance_state"

    fun load(context: android.content.Context): AppearanceState {
        val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val theme = runCatching { AppThemeStyle.valueOf(p.getString("theme", AppThemeStyle.VIOLET.name) ?: AppThemeStyle.VIOLET.name) }.getOrDefault(AppThemeStyle.VIOLET)
        val background = runCatching { AppBackgroundStyle.valueOf(p.getString("background", AppBackgroundStyle.GRADIENT.name) ?: AppBackgroundStyle.GRADIENT.name) }.getOrDefault(AppBackgroundStyle.GRADIENT)
        return AppearanceState(theme, background, p.getBoolean("dark", false), p.getBoolean("configured", false))
    }

    fun save(context: android.content.Context, theme: AppThemeStyle, background: AppBackgroundStyle, darkMode: Boolean) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putString("theme", theme.name)
            .putString("background", background.name)
            .putBoolean("dark", darkMode)
            .putBoolean("configured", true)
            .apply()
    }
}

fun themeColors(theme: AppThemeStyle, dark: Boolean) =
    if (dark) when (theme) {
        AppThemeStyle.VIOLET -> darkColorScheme(
            primary = Color(0xFFD1C4FF), primaryContainer = Color(0xFF3D2D76),
            secondary = Color(0xFFFF9CC7), secondaryContainer = Color(0xFF5A2642),
            tertiary = Color(0xFF85DDF2), tertiaryContainer = Color(0xFF164A58)
        )
        AppThemeStyle.OCEAN -> darkColorScheme(
            primary = Color(0xFF9FD8FF), primaryContainer = Color(0xFF164C6A),
            secondary = Color(0xFF71E1D0), secondaryContainer = Color(0xFF13453E),
            tertiary = Color(0xFFB8C7FF), tertiaryContainer = Color(0xFF20325E)
        )
        AppThemeStyle.SUNSET -> darkColorScheme(
            primary = Color(0xFFFFB5A1), primaryContainer = Color(0xFF6A2C25),
            secondary = Color(0xFFFFD06B), secondaryContainer = Color(0xFF594411),
            tertiary = Color(0xFFFF9EC7), tertiaryContainer = Color(0xFF56233D)
        )
        AppThemeStyle.MINT -> darkColorScheme(
            primary = Color(0xFFA0F3CF), primaryContainer = Color(0xFF17553F),
            secondary = Color(0xFFB5DBFF), secondaryContainer = Color(0xFF1C405D),
            tertiary = Color(0xFFF1C5FF), tertiaryContainer = Color(0xFF45294F)
        )
    } else when (theme) {
        AppThemeStyle.VIOLET -> lightColorScheme(
            primary = Color(0xFF6D42E8), primaryContainer = Color(0xFFE9DEFF),
            secondary = Color(0xFFD63D83), secondaryContainer = Color(0xFFFFD9E8),
            tertiary = Color(0xFF148BA8), tertiaryContainer = Color(0xFFCDEFFF)
        )
        AppThemeStyle.OCEAN -> lightColorScheme(
            primary = Color(0xFF006B94), primaryContainer = Color(0xFFC5E8FF),
            secondary = Color(0xFF008777), secondaryContainer = Color(0xFFB8F1E8),
            tertiary = Color(0xFF4B5FD1), tertiaryContainer = Color(0xFFDDE4FF)
        )
        AppThemeStyle.SUNSET -> lightColorScheme(
            primary = Color(0xFFC54836), primaryContainer = Color(0xFFFFDBD3),
            secondary = Color(0xFF8E6100), secondaryContainer = Color(0xFFFFE9B3),
            tertiary = Color(0xFFB33C73), tertiaryContainer = Color(0xFFFFDFEF)
        )
        AppThemeStyle.MINT -> lightColorScheme(
            primary = Color(0xFF087A58), primaryContainer = Color(0xFFB9F3D7),
            secondary = Color(0xFF3E67A4), secondaryContainer = Color(0xFFD9E7FF),
            tertiary = Color(0xFF7A4B9D), tertiaryContainer = Color(0xFFF0DFFF)
        )
    }

fun backgroundBrush(style: AppBackgroundStyle, dark: Boolean, theme: AppThemeStyle): Brush {
    val base = if (dark) Color(0xFF07090E) else Color(0xFFF8F7FC)
    return when (style) {
        AppBackgroundStyle.CLEAN -> Brush.verticalGradient(listOf(base, base))
        AppBackgroundStyle.GRADIENT -> Brush.linearGradient(
            when (theme) {
                AppThemeStyle.VIOLET -> listOf(if (dark) Color(0xFF11111B) else Color(0xFFF8F5FF), if (dark) Color(0xFF1B1120) else Color(0xFFFFF5FB))
                AppThemeStyle.OCEAN -> listOf(if (dark) Color(0xFF0A131A) else Color(0xFFF2FBFF), if (dark) Color(0xFF0D1E25) else Color(0xFFF1FFFC))
                AppThemeStyle.SUNSET -> listOf(if (dark) Color(0xFF160B0C) else Color(0xFFFFF6F1), if (dark) Color(0xFF22100D) else Color(0xFFFFF9E9))
                AppThemeStyle.MINT -> listOf(if (dark) Color(0xFF08100C) else Color(0xFFF2FBF7), if (dark) Color(0xFF0B1913) else Color(0xFFF3F8FF))
            }
        )
        AppBackgroundStyle.AURORA -> Brush.linearGradient(
            listOf(
                if (dark) Color(0xFF100C20) else Color(0xFFF3E9FF),
                if (dark) Color(0xFF10242A) else Color(0xFFE6F8FF),
                if (dark) Color(0xFF241127) else Color(0xFFFFEAF5)
            )
        )
        AppBackgroundStyle.MIDNIGHT -> Brush.radialGradient(
            listOf(
                if (dark) Color(0xFF1B1731) else Color(0xFFE8DEFF),
                base
            )
        )
    }
}

@Composable
fun AppBackdrop(style: AppBackgroundStyle, dark: Boolean, theme: AppThemeStyle, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(backgroundBrush(style, dark, theme)), content = content)
}

fun lightColors() = themeColors(AppThemeStyle.VIOLET, false)
fun darkColors() = themeColors(AppThemeStyle.VIOLET, true)
