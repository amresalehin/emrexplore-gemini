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
    primary = EmrExplorePrimaryDark,
    onPrimary = EmrExploreOnPrimaryDark,
    primaryContainer = EmrExplorePrimaryContainerDark,
    onPrimaryContainer = EmrExploreOnPrimaryContainerDark,
    secondary = EmrExploreSecondaryDark,
    onSecondary = EmrExploreOnSecondaryDark,
    secondaryContainer = EmrExploreSecondaryContainerDark,
    onSecondaryContainer = EmrExploreOnSecondaryContainerDark,
    tertiary = EmrExploreTertiaryDark,
    onTertiary = EmrExploreOnTertiaryDark,
    background = EmrExploreBackgroundDark,
    onBackground = EmrExploreOnBackgroundDark,
    surface = EmrExploreSurfaceDark,
    onSurface = EmrExploreOnSurfaceDark,
    surfaceVariant = EmrExploreSurfaceVariantDark,
    onSurfaceVariant = EmrExploreOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmrExplorePrimaryLight,
    onPrimary = EmrExploreOnPrimaryLight,
    primaryContainer = EmrExplorePrimaryContainerLight,
    onPrimaryContainer = EmrExploreOnPrimaryContainerLight,
    secondary = EmrExploreSecondaryLight,
    onSecondary = EmrExploreOnSecondaryLight,
    secondaryContainer = EmrExploreSecondaryContainerLight,
    onSecondaryContainer = EmrExploreOnSecondaryContainerLight,
    tertiary = EmrExploreTertiaryLight,
    onTertiary = EmrExploreOnTertiaryLight,
    background = EmrExploreBackgroundLight,
    onBackground = EmrExploreOnBackgroundLight,
    surface = EmrExploreSurfaceLight,
    onSurface = EmrExploreOnSurfaceLight,
    surfaceVariant = EmrExploreSurfaceVariantLight,
    onSurfaceVariant = EmrExploreOnSurfaceVariantLight
)

@Composable
fun EmrExploreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

@Composable
fun FossifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    EmrExploreTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
