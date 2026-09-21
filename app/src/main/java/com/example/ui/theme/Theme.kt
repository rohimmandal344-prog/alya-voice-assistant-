package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SleekPurplePrimaryDark,
    onPrimary = SleekPurpleOnPrimaryDark,
    primaryContainer = SleekPurpleContainerDark,
    onPrimaryContainer = SleekPurpleOnContainerDark,
    secondary = SleekLavenderSecondaryDark,
    onSecondary = SleekLavenderOnSecondaryDark,
    secondaryContainer = SleekLavenderContainerDark,
    onSecondaryContainer = SleekLavenderOnContainerDark,
    tertiary = SleekLilacTertiaryDark,
    onTertiary = SleekLilacOnTertiaryDark,
    tertiaryContainer = SleekLilacContainerDark,
    onTertiaryContainer = SleekLilacOnContainerDark,
    background = SleekBackgroundDark,
    surface = SleekSurfaceDark,
    onBackground = SleekOnBackgroundDark,
    onSurface = SleekOnSurfaceDark,
    surfaceVariant = SleekSurfaceVariantDark,
    onSurfaceVariant = SleekOnSurfaceVariantDark,
    outline = SleekOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = SleekPurplePrimary,
    onPrimary = SleekPurpleOnPrimary,
    primaryContainer = SleekPurpleContainer,
    onPrimaryContainer = SleekPurpleOnContainer,
    secondary = SleekLavenderSecondary,
    onSecondary = SleekLavenderOnSecondary,
    secondaryContainer = SleekLavenderContainer,
    onSecondaryContainer = SleekLavenderOnContainer,
    tertiary = SleekLilacTertiary,
    onTertiary = SleekLilacOnTertiary,
    tertiaryContainer = SleekLilacContainer,
    onTertiaryContainer = SleekLilacOnContainer,
    background = SleekBackgroundLight,
    surface = SleekSurfaceLight,
    onBackground = SleekOnBackgroundLight,
    onSurface = SleekOnSurfaceLight,
    surfaceVariant = SleekSurfaceVariantLight,
    onSurfaceVariant = SleekOnSurfaceVariantLight,
    outline = SleekOutlineLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent Sleek Interface branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
