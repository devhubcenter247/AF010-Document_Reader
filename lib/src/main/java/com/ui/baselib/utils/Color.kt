package com.ui.baselib.utils

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.core.graphics.toColorInt

@ColorInt
val transparent: Int = Color.TRANSPARENT

private val HEX_COLOR_REGEX = Regex("^#?(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")
private val HEX_BODY_REGEX = Regex("^(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
private val WHITESPACE_REGEX = Regex("\\s+")

@ColorInt
fun fromColor(code: String): Int = code.parseHexColorOrNull() ?: Color.TRANSPARENT

@ColorInt
fun String.toColorOpacity(@IntRange(0, 100) opacity: Int): Int {
    val color = parseHexColorOrNull() ?: return Color.TRANSPARENT
    return color.opacity(opacity)
}

@ColorInt
fun fromColor(r: Int, g: Int, b: Int, opacity: Int = 100): Int {
    val alpha = (opacity.coerceIn(0, 100) * 255 / 100)
    return Color.argb(alpha, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}

fun String.isValidHexColor(): Boolean = trim().matches(HEX_COLOR_REGEX)

/**
 * Parses "#RGB", "#RRGGBB" or "#AARRGGBB" (the '#' is optional). Returns null for anything
 * else, where [toColorInt] would throw (it rejects the short form and a missing '#').
 */
@ColorInt
fun String.parseHexColorOrNull(): Int? {
    val body = trim().removePrefix("#")
    if (!body.matches(HEX_BODY_REGEX)) return null
    val full = if (body.length == 3) body.map { "$it$it" }.joinToString("") else body
    return "#$full".toColorInt()
}

/** Parses a whitespace-separated color list such as "#FF0000 #0F0 00FF00", skipping bad tokens. */
fun String.parseHexColors(): IntArray =
    trim().split(WHITESPACE_REGEX).mapNotNull { it.parseHexColorOrNull() }.toIntArray()

@ColorInt
fun Int.opacity(@IntRange(0, 100) opacity: Int = 0): Int {
    val clampedOpacity = opacity.coerceIn(0, 100)
    val alpha = (clampedOpacity * 255) / 100
    val red = Color.red(this)
    val green = Color.green(this)
    val blue = Color.blue(this)
    return Color.argb(alpha, red, green, blue)
}
