package dev.cinema.crunchyroll.design

import android.animation.ValueAnimator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.cinema.crunchyroll.R
import dev.cinema.crunchyroll.data.MotionLevel

val Oled = Color.Black
val Surface = Color(0xFF141416)
val SurfaceRaised = Color(0xFF1E1E21)
val AccentSurface = Color(0xFF2C1C13)
val Ink = Color(0xFF111111)
val Paper = Color(0xFFF5F3F0)
val Muted = Color(0xFFA5A3AA)
val Orange = Color(0xFFFF7A26)
val Divider = Color(0xFF2D2C30)
val BodyFont = FontFamily(Font(R.font.dm_sans))
val DisplayFont = FontFamily(Font(R.font.barlow_condensed, FontWeight.Bold))
val LocalTv = staticCompositionLocalOf { false }
val LocalHaptics = staticCompositionLocalOf { true }
val LocalMotion = staticCompositionLocalOf { MotionLevel.Maximal }
val LocalConnected = staticCompositionLocalOf { false }

fun MotionLevel.millis(maximal: Int, standard: Int = 220): Int = when (this) {
    MotionLevel.Maximal -> maximal
    MotionLevel.Standard -> standard
    MotionLevel.Reduced -> 90
}

@Composable
fun CinemaTheme(motion: MotionLevel, isTv: Boolean, haptics: Boolean, content: @Composable () -> Unit) {
    // Compose additionally applies the platform duration scale to finite animations.
    val effectiveMotion = if (ValueAnimator.areAnimatorsEnabled()) motion else MotionLevel.Reduced
    val base = Typography()
    val type = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = DisplayFont),
        displayMedium = base.displayMedium.copy(fontFamily = DisplayFont),
        displaySmall = base.displaySmall.copy(fontFamily = DisplayFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = BodyFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = BodyFont),
        titleLarge = base.titleLarge.copy(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = base.titleMedium.copy(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = base.titleSmall.copy(fontFamily = BodyFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = BodyFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontFamily = BodyFont),
        labelLarge = base.labelLarge.copy(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = base.labelMedium.copy(fontFamily = BodyFont),
        labelSmall = base.labelSmall.copy(fontFamily = BodyFont),
    )
    CompositionLocalProvider(LocalMotion provides effectiveMotion, LocalTv provides isTv, LocalHaptics provides haptics) {
        MaterialTheme(
            colorScheme = darkColorScheme(primary = Orange, onPrimary = Ink, background = Oled, onBackground = Paper,
                surface = Surface, onSurface = Paper, surfaceVariant = SurfaceRaised, onSurfaceVariant = Muted,
                outline = Divider, outlineVariant = Divider, secondary = Orange, secondaryContainer = AccentSurface,
                onSecondaryContainer = Orange, surfaceContainer = Surface, surfaceContainerHigh = SurfaceRaised),
            typography = type,
        ) {
            androidx.tv.material3.MaterialTheme(
                colorScheme = androidx.tv.material3.darkColorScheme(primary = Orange, onPrimary = Ink, background = Oled,
                    onBackground = Paper, surface = Surface, onSurface = Paper),
            ) {
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Paper,
                    androidx.compose.material3.LocalTextStyle provides type.bodyLarge, content=content)
            }
        }
    }
}
