package chat.revolt.ui.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat

val RevoltColorScheme = darkColorScheme(
    primary = Color(0xffda4e5b),
    onPrimary = Color(0xffffffff),
    primaryContainer = Color(0xffda4e5b),
    onPrimaryContainer = Color(0xffffffff),
    secondary = Color(0xfff84848),
    onSecondary = Color(0xffffffff),
    secondaryContainer = Color(0xfff84848),
    onSecondaryContainer = Color(0xffffffff),
    tertiary = Color(0xff3abf7e),
    onTertiary = Color(0xffffffff),
    tertiaryContainer = Color(0xff3abf7e),
    onTertiaryContainer = Color(0xffffffff),
    background = Color(0xff191919),
    onBackground = Color(0xffffffff),
    surfaceVariant = Color(0xff242424),
    onSurfaceVariant = Color(0xffffffff),
    surface = Color(0xff191919),
    onSurface = Color(0xffffffff),
    surfaceTint = Color(0xFFC0C0C0),
)

val AmoledColorScheme = RevoltColorScheme.copy(
    background = Color(0xff000000),
    onBackground = Color(0xffffffff),
    surfaceVariant = Color(0xff131313),
    onSurfaceVariant = Color(0xffffffff),
    surface = Color(0xff000000),
    onSurface = Color(0xffffffff)
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xffda4e5b),
    onPrimary = Color(0xffffffff),
    primaryContainer = Color(0xffda4e5b),
    onPrimaryContainer = Color(0xffffffff),
    secondary = Color(0xfff84848),
    onSecondary = Color(0xffffffff),
    secondaryContainer = Color(0xfff84848),
    onSecondaryContainer = Color(0xffffffff),
    tertiary = Color(0xff3abf7e),
    onTertiary = Color(0xffffffff),
    tertiaryContainer = Color(0xff3abf7e),
    onTertiaryContainer = Color(0xffffffff),
    background = Color(0xffffffff),
    onBackground = Color(0xff000000),
    surfaceVariant = Color(0xffe0e0e0),
    onSurfaceVariant = Color(0xff000000),
    surface = Color(0xffffffff),
    onSurface = Color(0xff000000),
    surfaceTint = Color(0xff5658b9)
)

enum class Theme {
    None,
    Revolt,
    Light,
    M3Dynamic,
    Amoled
}

@Composable
fun getColorScheme(
    requestedTheme: Theme,
    colourOverrides: OverridableColourScheme? = null
): ColorScheme {
    val context = LocalContext.current

    val systemInDarkTheme = isSystemInDarkTheme()
    val m3Supported = systemSupportsDynamicColors()

    val colorScheme = when {
        m3Supported && requestedTheme == Theme.M3Dynamic && systemInDarkTheme -> dynamicDarkColorScheme(
            context
        )

        m3Supported && requestedTheme == Theme.M3Dynamic && !systemInDarkTheme -> dynamicLightColorScheme(
            context
        )

        requestedTheme == Theme.Revolt -> RevoltColorScheme
        requestedTheme == Theme.Light -> LightColorScheme
        requestedTheme == Theme.Amoled -> AmoledColorScheme
        requestedTheme == Theme.None && systemInDarkTheme -> RevoltColorScheme
        requestedTheme == Theme.None && !systemInDarkTheme -> LightColorScheme
        else -> RevoltColorScheme
    }.copy()

    val colorSchemeIsDark = when {
        m3Supported && requestedTheme == Theme.M3Dynamic -> isSystemInDarkTheme()
        requestedTheme == Theme.Revolt -> true
        requestedTheme == Theme.Light -> false
        requestedTheme == Theme.Amoled -> true
        requestedTheme == Theme.None && systemInDarkTheme -> true
        requestedTheme == Theme.None && !systemInDarkTheme -> false
        else -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            @Suppress("DEPRECATION")
            ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars =
                !colorSchemeIsDark
        }
    }

    if (colourOverrides == null) return colorScheme
    return colourOverrides.applyTo(colorScheme)
}

@SuppressLint("NewApi")
@Composable
fun RevoltTheme(
    requestedTheme: Theme,
    colourOverrides: OverridableColourScheme? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(requestedTheme, colourOverrides)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RevoltTypography,
        content = content
    )
}

fun systemSupportsDynamicColors(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

fun getDefaultTheme(): Theme {
    return when {
        systemSupportsDynamicColors() -> Theme.M3Dynamic
        else -> Theme.Revolt
    }
}

fun isThemeDark(theme: Theme, systemIsDark: Boolean): Boolean {
    return when (theme) {
        Theme.Revolt, Theme.Amoled -> true
        Theme.Light -> false
        Theme.M3Dynamic, Theme.None -> systemIsDark
    }
}

@Composable
fun isThemeDark(theme: Theme) = isThemeDark(theme, isSystemInDarkTheme())