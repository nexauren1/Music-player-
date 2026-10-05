package com.musicplayer.app

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

fun lightColors() = lightColorScheme(
    primary = Color(0xFF635BFF),
    secondary = Color(0xFFE94B8F),
    tertiary = Color(0xFF2B8FFF)
)

fun darkColors() = darkColorScheme(
    primary = Color(0xFFB8B3FF),
    secondary = Color(0xFFFFA2C8),
    tertiary = Color(0xFF9BC8FF)
)
