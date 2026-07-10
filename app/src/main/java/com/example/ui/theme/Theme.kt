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
    primary = BlueSecondary,
    secondary = GreenSuccess,
    tertiary = AmberCoin,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = LightBackground,
    onSurface = LightBackground
)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    secondary = BlueSecondary,
    tertiary = AmberCoin,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = DarkBackground,
    onSurface = DarkBackground
)

// Green Theme Color Schemes
private val GreenDarkColorScheme = darkColorScheme(
    primary = GreenSecondary,
    secondary = GreenTertiary,
    tertiary = AmberCoin,
    background = GreenDarkBackground,
    surface = GreenDarkSurface,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = LightBackground,
    onSurface = LightBackground
)

private val GreenLightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    secondary = GreenSecondary,
    tertiary = AmberCoin,
    background = GreenLightBackground,
    surface = LightSurface,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = DarkBackground,
    onSurface = DarkBackground
)

// Purple Theme Color Schemes
private val PurpleDarkColorScheme = darkColorScheme(
    primary = PurpleSecondary,
    secondary = PurpleTertiary,
    tertiary = AmberCoin,
    background = PurpleDarkBackground,
    surface = PurpleDarkSurface,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = LightBackground,
    onSurface = LightBackground
)

private val PurpleLightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    secondary = PurpleSecondary,
    tertiary = AmberCoin,
    background = PurpleLightBackground,
    surface = LightSurface,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = DarkBackground,
    onSurface = DarkBackground
)

// Orange Theme Color Schemes
private val OrangeDarkColorScheme = darkColorScheme(
    primary = OrangeSecondary,
    secondary = OrangeTertiary,
    tertiary = AmberCoin,
    background = OrangeDarkBackground,
    surface = OrangeDarkSurface,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = LightBackground,
    onSurface = LightBackground
)

private val OrangeLightColorScheme = lightColorScheme(
    primary = OrangePrimary,
    secondary = OrangeSecondary,
    tertiary = AmberCoin,
    background = OrangeLightBackground,
    surface = LightSurface,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = DarkBackground,
    onSurface = DarkBackground
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color to maintain a highly branded English Master visual identity
    dynamicColor: Boolean = false,
    selectedTheme: String = "blue",
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> when (selectedTheme) {
            "green" -> GreenDarkColorScheme
            "purple" -> PurpleDarkColorScheme
            "orange" -> OrangeDarkColorScheme
            else -> DarkColorScheme
        }
        else -> when (selectedTheme) {
            "green" -> GreenLightColorScheme
            "purple" -> PurpleLightColorScheme
            "orange" -> OrangeLightColorScheme
            else -> LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

