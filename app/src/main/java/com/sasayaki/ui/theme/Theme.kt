package com.sasayaki.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = Pine80,
    onPrimary = Pine110,
    primaryContainer = Pine100,
    onPrimaryContainer = Pine50,
    secondary = Sand90,
    onSecondary = Sand110,
    secondaryContainer = Sand100,
    onSecondaryContainer = Sand50,
    tertiary = Clay80,
    onTertiary = Clay110,
    tertiaryContainer = Clay100,
    onTertiaryContainer = Clay50,
    background = Ink100,
    onBackground = Ink10,
    surface = Slate110,
    onSurface = Ink10,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate80,
    surfaceContainer = Color(0xFF212826),
    surfaceContainerHigh = Color(0xFF2A322F)
)

private val LightColorScheme = lightColorScheme(
    primary = Pine100,
    onPrimary = Color.White,
    primaryContainer = Pine90,
    onPrimaryContainer = Pine110,
    secondary = Sand100,
    onSecondary = Color.White,
    secondaryContainer = Sand70,
    onSecondaryContainer = Sand110,
    tertiary = Clay100,
    onTertiary = Color.White,
    tertiaryContainer = Clay90,
    onTertiaryContainer = Clay110,
    background = Ink10,
    onBackground = Ink90,
    surface = Color.White,
    onSurface = Ink90,
    surfaceVariant = Ink20,
    onSurfaceVariant = Ink40,
    surfaceContainer = Color(0xFFF0F1EE),
    surfaceContainerHigh = Color(0xFFE8EAE6)
)

@Composable
fun SasayakiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
