package com.musicplayer.app

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

fun lightColors() = lightColorScheme(
    primary = Color(0xFF6750E8),
    secondary = Color(0xFFE94B8F),
    tertiary = Color(0xFF1EA7C8),
    background = Color(0xFFF8F7FC),
    surface = Color(0xFFF8F7FC),
    surfaceVariant = Color(0xFFEDEAF6)
)

fun darkColors() = darkColorScheme(
    primary = Color(0xFFC7B9FF),
    secondary = Color(0xFFFF9CC7),
    tertiary = Color(0xFF83DBEE),
    background = Color(0xFF0E0D13),
    surface = Color(0xFF0E0D13),
    surfaceVariant = Color(0xFF211F2A)
)
