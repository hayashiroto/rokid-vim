package com.rokidvim.android

import com.rokidvim.core.Shade

/**
 * 緑単色 HUD の配色。
 *
 * Rokid Glasses は緑単色・黒が透過なので、#40FF5E を最大とし
 * 100 / 72 / 48 / 24 / 12 % の5段階の「暗い緑」で階調をつける。
 * 黒（= 透過）に近いほど現実側が透けて見える。
 */
object GreenPalette {

    /** 最大輝度の緑。 */
    const val BASE: Int = 0xFF40FF5E.toInt()

    /** 黒 = 眼鏡では透過。 */
    const val BLACK: Int = 0xFF000000.toInt()

    fun color(shade: Shade): Int = when (shade) {
        Shade.FULL -> scale(1.00f)
        Shade.BRIGHT -> scale(0.72f)
        Shade.MID -> scale(0.48f)
        Shade.DIM -> scale(0.24f)
        Shade.FAINT -> scale(0.12f)
    }

    private fun scale(factor: Float): Int {
        val r = ((BASE shr 16 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = ((BASE shr 8 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((BASE and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
