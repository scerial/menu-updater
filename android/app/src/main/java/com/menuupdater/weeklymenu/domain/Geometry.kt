package com.menuupdater.weeklymenu.domain

/**
 * A normalized bounding box in the iOS Vision convention: origin at the bottom-left, y growing
 * upward, all values in 0..1. The parser was designed against this space, so ML Kit's top-left
 * boxes are converted on the way in (see [fromTopDown]).
 */
data class Rect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val midX: Double get() = x + width / 2
    val midY: Double get() = y + height / 2
    val minX: Double get() = x
    val minY: Double get() = y

    companion object {
        /**
         * Builds a bottom-left [Rect] from a ML Kit box given as `[left, top, right, bottom]` in
         * top-left normalized space (y growing downward).
         */
        fun fromTopDown(left: Double, top: Double, right: Double, bottom: Double): Rect =
            Rect(x = left, y = 1.0 - bottom, width = right - left, height = bottom - top)
    }
}

/** One line of recognized text with its position. */
data class RecognizedLine(val text: String, val box: Rect)
