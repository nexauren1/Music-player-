package com.musicplayer.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppThemeStyle(val label: String) {
    VIOLET("Violet"),
    OCEAN("Ocean"),
    SUNSET("Sunset"),
    MINT("Mint"),
    NEON("Neon"),
    FIRE("Fire"),
    CYBER("Cyber"),
    LAGOON("Lagoon")
}

enum class AppBackgroundStyle(val label: String) {
    CLEAN("Clean"),
    GRADIENT("Gradient"),
    AURORA("Aurora"),
    MIDNIGHT("Midnight"),
    NEON_WAVE("Neon Wave")
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
        val theme = runCatching {
            AppThemeStyle.valueOf(
                p.getString("theme", AppThemeStyle.VIOLET.name) ?: AppThemeStyle.VIOLET.name
            )
        }.getOrDefault(AppThemeStyle.VIOLET)
        val background = runCatching {
            AppBackgroundStyle.valueOf(
                p.getString("background", AppBackgroundStyle.GRADIENT.name)
                    ?: AppBackgroundStyle.GRADIENT.name
            )
        }.getOrDefault(AppBackgroundStyle.GRADIENT)
        return AppearanceState(
            theme,
            background,
            p.getBoolean("dark", false),
            p.getBoolean("configured", false)
        )
    }

    fun save(
        context: android.content.Context,
        theme: AppThemeStyle,
        background: AppBackgroundStyle,
        darkMode: Boolean
    ) {
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
        AppThemeStyle.NEON -> darkColorScheme(
            primary = Color(0xFFF3A7FF), primaryContainer = Color(0xFF4D185B),
            secondary = Color(0xFF70F7FF), secondaryContainer = Color(0xFF124C55),
            tertiary = Color(0xFFA3FF8F), tertiaryContainer = Color(0xFF205A28)
        )
        AppThemeStyle.FIRE -> darkColorScheme(
            primary = Color(0xFFFFB59F), primaryContainer = Color(0xFF6B2319),
            secondary = Color(0xFFFFD66B), secondaryContainer = Color(0xFF5D4612),
            tertiary = Color(0xFFFF89B8), tertiaryContainer = Color(0xFF5B1B38)
        )
        AppThemeStyle.CYBER -> darkColorScheme(
            primary = Color(0xFFB9C7FF), primaryContainer = Color(0xFF26356E),
            secondary = Color(0xFF76F4DD), secondaryContainer = Color(0xFF164D44),
            tertiary = Color(0xFFFFB6EF), tertiaryContainer = Color(0xFF5D2151)
        )
        AppThemeStyle.LAGOON -> darkColorScheme(
            primary = Color(0xFFA5E6FF), primaryContainer = Color(0xFF16455B),
            secondary = Color(0xFF7EF0CE), secondaryContainer = Color(0xFF124E40),
            tertiary = Color(0xFFCAB3FF), tertiaryContainer = Color(0xFF38265F)
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
        AppThemeStyle.NEON -> lightColorScheme(
            primary = Color(0xFF8A2AA8), primaryContainer = Color(0xFFF1D4FF),
            secondary = Color(0xFF007A85), secondaryContainer = Color(0xFFB9F4F7),
            tertiary = Color(0xFF3B7A1D), tertiaryContainer = Color(0xFFD8F5C7)
        )
        AppThemeStyle.FIRE -> lightColorScheme(
            primary = Color(0xFFBF3C20), primaryContainer = Color(0xFFFFDAD1),
            secondary = Color(0xFF8B5F00), secondaryContainer = Color(0xFFFFE8B1),
            tertiary = Color(0xFFB12B6C), tertiaryContainer = Color(0xFFFFD9E8)
        )
        AppThemeStyle.CYBER -> lightColorScheme(
            primary = Color(0xFF4458BE), primaryContainer = Color(0xFFDCE1FF),
            secondary = Color(0xFF087C6F), secondaryContainer = Color(0xFFB8F4E9),
            tertiary = Color(0xFF9B3B86), tertiaryContainer = Color(0xFFFFD9F5)
        )
        AppThemeStyle.LAGOON -> lightColorScheme(
            primary = Color(0xFF006C8E), primaryContainer = Color(0xFFC8ECFA),
            secondary = Color(0xFF007E63), secondaryContainer = Color(0xFFB7F0DB),
            tertiary = Color(0xFF6550AC), tertiaryContainer = Color(0xFFE7DEFF)
        )
    }

fun backgroundBrush(style: AppBackgroundStyle, dark: Boolean, theme: AppThemeStyle): Brush {
    val base = if (dark) Color(0xFF07090E) else Color(0xFFF8F7FC)
    val accent = themeColors(theme, dark)
    return when (style) {
        AppBackgroundStyle.CLEAN -> Brush.verticalGradient(listOf(base, base))
        AppBackgroundStyle.GRADIENT -> Brush.linearGradient(
            listOf(accent.primaryContainer, base, accent.secondaryContainer)
        )
        AppBackgroundStyle.AURORA -> Brush.linearGradient(
            listOf(
                accent.primaryContainer,
                accent.tertiaryContainer,
                accent.secondaryContainer
            )
        )
        AppBackgroundStyle.MIDNIGHT -> Brush.radialGradient(
            listOf(accent.primaryContainer, base)
        )
        AppBackgroundStyle.NEON_WAVE -> Brush.sweepGradient(
            listOf(
                accent.primary,
                accent.secondary,
                accent.tertiary,
                accent.primary
            )
        )
    }
}

@Composable
fun AppBackdrop(
    style: AppBackgroundStyle,
    dark: Boolean,
    theme: AppThemeStyle,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "backdrop")
    val shift by transition.animateFloat(
        initialValue = -36f,
        targetValue = 36f,
        animationSpec = infiniteRepeatable(tween(6000), RepeatMode.Reverse),
        label = "shift"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(backgroundBrush(style, dark, theme))
    ) {
        if (style != AppBackgroundStyle.CLEAN) {
            Box(
                Modifier
                    .size(220.dp)
                    .offset(x = shift.dp, y = (-40).dp)
                    .alpha(0.20f)
                    .blur(46.dp)
                    .background(themeColors(theme, dark).primary, androidx.compose.foundation.shape.CircleShape)
            )
            Box(
                Modifier
                    .size(180.dp)
                    .offset(x = (-30).dp, y = (560 + shift / 2).dp)
                    .alpha(0.14f)
                    .blur(42.dp)
                    .background(themeColors(theme, dark).secondary, androidx.compose.foundation.shape.CircleShape)
            )
        }
        content()
    }
}

fun lightColors() = themeColors(AppThemeStyle.VIOLET, false)
fun darkColors() = themeColors(AppThemeStyle.VIOLET, true)
