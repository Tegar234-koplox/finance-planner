package com.tegar.finance.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.tegar.finance.domain.model.ThemeMode

private val Light = lightColorScheme(primary = Color(0xFF235946), onPrimary = Color.White,
    primaryContainer = Color(0xFFDAEDE1), onPrimaryContainer = Color(0xFF123528),
    background = Color(0xFFF8FAF7), surface = Color(0xFFF8FAF7))
private val Dark = darkColorScheme(primary = Color(0xFFA8D4BB), onPrimary = Color(0xFF103828),
    primaryContainer = Color(0xFF244E3D), onPrimaryContainer = Color(0xFFD1ECDC),
    background = Color(0xFF121914), surface = Color(0xFF121914))

@Composable
fun FinanceTheme(mode: ThemeMode, onDarkTheme: (Boolean) -> Unit = {}, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SideEffect { onDarkTheme(dark) }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
