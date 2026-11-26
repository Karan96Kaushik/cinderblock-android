package com.bayonetbarontech.cinderblock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.bayonetbarontech.cinderblock.R
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class CinderColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val primary: Color,
    val primaryForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val destructive: Color,
    val border: Color,
    val input: Color,
    val neonOrange: Color,
    val neonYellow: Color,
    val neonAmber: Color,
    val neonRed: Color,
    val success: Color,
    val light: Boolean,
)

data class CinderType(
    val display: FontFamily,
    val body: FontFamily,
)

val LocalColors = staticCompositionLocalOf { orangeColors() }
val LocalType = staticCompositionLocalOf {
    CinderType(FontFamily.SansSerif, FontFamily.SansSerif)
}

private val ShareTech = FontFamily(Font(R.font.share_tech_mono))

fun colorsFor(theme: String): CinderColors = when (theme) {
    "amber" -> palette(0.15, 0.038, 45.0, 0.70, 0.15, 55.0, 0.76, 0.12, 80.0, 0.66, 0.14, 60.0, false)
    "green" -> palette(0.15, 0.035, 155.0, 0.78, 0.14, 155.0, 0.76, 0.11, 130.0, 0.64, 0.10, 145.0, false)
    "blue" -> palette(0.15, 0.035, 250.0, 0.72, 0.12, 230.0, 0.74, 0.08, 220.0, 0.64, 0.09, 240.0, false)
    "white" -> palette(0.97, 0.012, 200.0, 0.62, 0.14, 168.0, 0.72, 0.12, 155.0, 0.58, 0.08, 190.0, true)
    "cream" -> palette(0.97, 0.018, 80.0, 0.52, 0.18, 55.0, 0.68, 0.16, 80.0, 0.58, 0.17, 65.0, true)
    else -> nightColors()
}

fun nightColors() = palette(0.17, 0.032, 255.0, 0.84, 0.14, 168.0, 0.86, 0.11, 155.0, 0.74, 0.08, 200.0, false)

fun orangeColors() = nightColors()

private fun palette(
    l: Double,
    c: Double,
    h: Double,
    pl: Double,
    pc: Double,
    ph: Double,
    yl: Double,
    yc: Double,
    yh: Double,
    al: Double,
    ac: Double,
    ah: Double,
    light: Boolean,
): CinderColors {
    val background = oklch(l, c, h)
    val foreground = if (light) oklch(0.22, 0.03, h) else oklch(0.80, 0.022, h)
    val primary = oklch(pl, pc, ph)
    return CinderColors(
        background = background,
        foreground = foreground,
        card = oklch((l + if (light) 0.0 else 0.045).coerceAtMost(0.99), c * if (light) 1.0 else 0.7, h),
        primary = primary,
        primaryForeground = if (light) Color.White else background,
        muted = oklch((l + 0.08).coerceAtMost(0.96), c * 0.8, h),
        mutedForeground = if (light) oklch(0.42, 0.02, h) else oklch(0.58, 0.03, h),
        destructive = oklch(0.50, 0.18, 25.0),
        border = oklch((l + if (light) -0.08 else 0.09).coerceIn(0.1, 0.9), c, h),
        input = oklch((l + if (light) -0.02 else 0.04).coerceIn(0.1, 0.98), c, h),
        neonOrange = primary,
        neonYellow = oklch(yl, yc, yh),
        neonAmber = oklch(al, ac, ah),
        neonRed = oklch(0.55, 0.18, 30.0),
        success = oklch(0.72, 0.17, 150.0),
        light = light,
    )
}

fun oklch(l: Double, c: Double, h: Double): Color {
    val hr = Math.toRadians(h)
    val a = c * cos(hr)
    val b = c * sin(hr)
    val l_ = l + 0.3963377774 * a + 0.2158037573 * b
    val m_ = l - 0.1055613458 * a - 0.0638541728 * b
    val s_ = l - 0.0894841775 * a - 1.2914855480 * b
    val l3 = l_ * l_ * l_
    val m3 = m_ * m_ * m_
    val s3 = s_ * s_ * s_
    val r = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
    val g = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
    val bl = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
    fun gamma(v: Double): Float {
        val clamped = v.coerceIn(0.0, 1.0)
        val encoded = if (clamped <= 0.0031308) 12.92 * clamped else 1.055 * clamped.pow(1.0 / 2.4) - 0.055
        return encoded.toFloat()
    }
    return Color(gamma(r), gamma(g), gamma(bl))
}

fun fontScaleFor(size: String): Float = when (size) {
    "sm" -> 0.875f
    "lg" -> 1.15f
    "xl" -> 1.3f
    else -> 1f
}

fun typeFor(preset: String): CinderType {
    val (display, body) = when (preset) {
        "mono" -> ShareTech to ShareTech
        else -> FontFamily.SansSerif to FontFamily.SansSerif
    }
    return CinderType(display, body)
}

@Composable
fun CinderTheme(theme: String, fontPreset: String, fontSize: String, content: @Composable () -> Unit) {
    val colors = colorsFor(theme)
    val type = typeFor(fontPreset)
    val system = LocalDensity.current
    val density = Density(system.density, system.fontScale * fontScaleFor(fontSize))
    val scheme = if (colors.light) {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.primaryForeground,
            background = colors.background,
            onBackground = colors.foreground,
            surface = colors.card,
            onSurface = colors.foreground,
            error = colors.destructive,
            outline = colors.border,
        )
    } else {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.primaryForeground,
            background = colors.background,
            onBackground = colors.foreground,
            surface = colors.card,
            onSurface = colors.foreground,
            error = colors.destructive,
            outline = colors.border,
        )
    }
    CompositionLocalProvider(LocalColors provides colors, LocalType provides type, LocalDensity provides density) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
