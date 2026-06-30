package it.casa.clima

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/* ----------------------------------------------------------------------------
 * Design system "Scuro elettrico" — identità di brand di Clima Casa.
 * Sfondo near-black con tinta blu, superfici elevate, accenti neon ciano/lime.
 * Il tema è dark-first per scelta di brand: coerente su tutti i dispositivi.
 * ------------------------------------------------------------------------- */

// Palette
val Ink            = Color(0xFF070B11) // background
val Surface1       = Color(0xFF0E141C) // surface
val Surface2       = Color(0xFF141C26) // card base
val Surface3       = Color(0xFF1B2531) // card elevata
val Surface4       = Color(0xFF243140) // hover / highest

val NeonCyan       = Color(0xFF1FE3CC) // primary
val NeonCyanSoft   = Color(0xFF7CFFEF)
val ElectricLime   = Color(0xFFB8FF49) // secondary
val ElectricBlue   = Color(0xFF6FA8FF) // tertiary
val Danger         = Color(0xFFFF5A6E)
val Amber          = Color(0xFFFFC247)

val OnInk          = Color(0xFFE7EEF5)
val OnInkMuted     = Color(0xFF93A4B5)
val OutlineSoft    = Color(0xFF38485A)
val OutlineDim     = Color(0xFF243040)

private val ClimaDarkColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF0A4A43),
    onPrimaryContainer = NeonCyanSoft,
    secondary = ElectricLime,
    onSecondary = Color(0xFF18260A),
    secondaryContainer = Color(0xFF324A0E),
    onSecondaryContainer = Color(0xFFD7FF9B),
    tertiary = ElectricBlue,
    onTertiary = Color(0xFF06214A),
    tertiaryContainer = Color(0xFF1E3B66),
    onTertiaryContainer = Color(0xFFCFE0FF),
    background = Ink,
    onBackground = OnInk,
    surface = Surface1,
    onSurface = OnInk,
    surfaceVariant = Surface3,
    onSurfaceVariant = OnInkMuted,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface2,
    surfaceContainerHigh = Surface3,
    surfaceContainerHighest = Surface4,
    surfaceTint = NeonCyan,
    error = Danger,
    onError = Color(0xFF3A0010),
    errorContainer = Color(0xFF5C1623),
    onErrorContainer = Color(0xFFFFD9DE),
    outline = OutlineSoft,
    outlineVariant = OutlineDim,
    scrim = Color(0xFF000000),
)

private val ClimaType = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        labelMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.4.sp,
        ),
    )
}

/** Tema dell'app: scuro elettrico, sempre attivo per coerenza di brand. */
@Composable
fun ClimaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClimaDarkColors,
        typography = ClimaType,
        content = content,
    )
}
