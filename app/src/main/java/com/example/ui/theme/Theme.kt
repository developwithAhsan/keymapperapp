package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = ThemePrimary,
    secondary = ThemeSecondContainer,
    background = ThemeBackground,
    surface = ThemeCardBackground,
    onPrimary = Color(0xFF381E72),
    onSecondary = Color.White,
    onBackground = ThemeText,
    onSurface = ThemeText
  )

private val LightColorScheme =
  darkColorScheme( // Keep same geometric layout aesthetic for both modes to guarantee the design theme is applied correctly
    primary = ThemePrimary,
    secondary = ThemeSecondContainer,
    background = ThemeBackground,
    surface = ThemeCardBackground,
    onPrimary = Color(0xFF381E72),
    onSecondary = Color.White,
    onBackground = ThemeText,
    onSurface = ThemeText
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default to preserve the exact geometric balance palette
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
