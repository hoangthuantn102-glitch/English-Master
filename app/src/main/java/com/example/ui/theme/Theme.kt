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

// --- PREMIUM SHOP THEMES ---

// Sakura Theme (Pink Blossom)
private val SakuraLightColorScheme = lightColorScheme(
    primary = Color(0xFFD81B60),
    secondary = Color(0xFFF06292),
    tertiary = AmberCoin,
    background = Color(0xFFFFF1F5),
    surface = LightSurface,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = Color(0xFF4A1525),
    onSurface = Color(0xFF4A1525)
)

private val SakuraDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF48FB1),
    secondary = Color(0xFFAD1457),
    tertiary = AmberCoin,
    background = Color(0xFF1E0E13),
    surface = Color(0xFF2E1920),
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = Color(0xFFFFF1F5),
    onSurface = Color(0xFFFFF1F5)
)

// Cyberpunk Neon Theme (Cyan/Cyber)
private val NeonLightColorScheme = lightColorScheme(
    primary = Color(0xFF00ACC1),
    secondary = Color(0xFF00E5FF),
    tertiary = AmberCoin,
    background = Color(0xFFE0F7FA),
    surface = LightSurface,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = Color(0xFF004D40),
    onSurface = Color(0xFF004D40)
)

private val NeonDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    secondary = Color(0xFF00838F),
    tertiary = AmberCoin,
    background = Color(0xFF0A192F),
    surface = Color(0xFF172A45),
    onPrimary = Color(0xFF0A192F),
    onSecondary = Color(0xFF00E5FF),
    onTertiary = DarkBackground,
    onBackground = Color(0xFFE0F7FA),
    onSurface = Color(0xFFE0F7FA)
)

// Midnight Obsidian Theme (Pitch Black/Sleek Minimal)
private val MidnightLightColorScheme = lightColorScheme(
    primary = Color(0xFF37474F),
    secondary = Color(0xFF78909C),
    tertiary = AmberCoin,
    background = Color(0xFFECEFF1),
    surface = LightSurface,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = DarkBackground,
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121)
)

private val MidnightDarkColorScheme = darkColorScheme(
    primary = Color(0xFFECEFF1),
    secondary = Color(0xFF37474F),
    tertiary = AmberCoin,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color(0xFF121212),
    onSecondary = Color(0xFFECEFF1),
    onTertiary = DarkBackground,
    onBackground = Color(0xFFECEFF1),
    onSurface = Color(0xFFECEFF1)
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
            "sakura" -> SakuraDarkColorScheme
            "neon" -> NeonDarkColorScheme
            "midnight" -> MidnightDarkColorScheme
            else -> DarkColorScheme
        }
        else -> when (selectedTheme) {
            "green" -> GreenLightColorScheme
            "purple" -> PurpleLightColorScheme
            "orange" -> OrangeLightColorScheme
            "sakura" -> SakuraLightColorScheme
            "neon" -> NeonLightColorScheme
            "midnight" -> MidnightLightColorScheme
            else -> LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

