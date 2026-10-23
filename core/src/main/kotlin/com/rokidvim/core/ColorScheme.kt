package com.rokidvim.core

/**
 * 緑単色 HUD 向けの明度段階。
 * 実際の色は UI 層が決める（例: #40FF5E を最大として 100/72/48/24/12%）。
 */
enum class Shade {
    FULL,   // 100%
    BRIGHT, // 72%
    MID,    // 48%
    DIM,    // 24%
    FAINT,  // 12%
}

/** 画面要素ごとの明度割り当て。黒背景＝透過を前提にした配色。 */
data class ColorScheme(
    val name: String,
    val text: Shade,
    val lineNumber: Shade,
    val currentLineNumber: Shade,
    val statusText: Shade,
    val modeIndicator: Shade,
    val commandText: Shade,
    val message: Shade,
    val error: Shade,
    val cursorBlock: Shade,
    val visualBackground: Shade,
    val tilde: Shade,
)

object ColorSchemes {
    /** 既定。本文を最大輝度、装飾は抑えめにして可読性を優先。 */
    val ROKID_GREEN = ColorScheme(
        name = "rokid-green",
        text = Shade.FULL,
        lineNumber = Shade.MID,
        currentLineNumber = Shade.BRIGHT,
        statusText = Shade.BRIGHT,
        modeIndicator = Shade.FULL,
        commandText = Shade.FULL,
        message = Shade.BRIGHT,
        error = Shade.FULL,
        cursorBlock = Shade.FULL,
        visualBackground = Shade.MID,
        tilde = Shade.DIM,
    )

    /** 輝度を一段落とした省電力・低眩惑版。 */
    val ROKID_SOFT = ColorScheme(
        name = "rokid-soft",
        text = Shade.BRIGHT,
        lineNumber = Shade.DIM,
        currentLineNumber = Shade.MID,
        statusText = Shade.MID,
        modeIndicator = Shade.BRIGHT,
        commandText = Shade.BRIGHT,
        message = Shade.MID,
        error = Shade.BRIGHT,
        cursorBlock = Shade.BRIGHT,
        visualBackground = Shade.DIM,
        tilde = Shade.FAINT,
    )

    /** 全要素を最大コントラストに寄せた版。 */
    val ROKID_MONO = ColorScheme(
        name = "rokid-mono",
        text = Shade.FULL,
        lineNumber = Shade.FULL,
        currentLineNumber = Shade.FULL,
        statusText = Shade.FULL,
        modeIndicator = Shade.FULL,
        commandText = Shade.FULL,
        message = Shade.FULL,
        error = Shade.FULL,
        cursorBlock = Shade.FULL,
        visualBackground = Shade.BRIGHT,
        tilde = Shade.MID,
    )

    val all: List<ColorScheme> = listOf(ROKID_GREEN, ROKID_SOFT, ROKID_MONO)

    fun byName(name: String): ColorScheme? = all.find { it.name == name }
}
