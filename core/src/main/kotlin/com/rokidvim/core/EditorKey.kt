package com.rokidvim.core

/** 文字キー以外の特殊キー。 */
enum class SpecialKey {
    ESCAPE,
    ENTER,
    BACKSPACE,
    DELETE,
    TAB,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    HOME,
    END,
    PAGE_UP,
    PAGE_DOWN,
}

/**
 * プラットフォーム非依存のキー表現。
 *
 * - 印字可能キーは [char] に Shift 適用済みの文字を入れる（この場合 [shift] は false のまま）。
 * - Ctrl コードは [char] に基底の小文字を入れて [ctrl] = true とする（例: Ctrl-r）。
 * - 特殊キーは [special] を使う。
 *
 * Android の KeyEvent からの変換は android-ui モジュールが行う。
 */
data class EditorKey(
    val char: Char? = null,
    val special: SpecialKey? = null,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    val isPrintable: Boolean get() = char != null && !ctrl && !alt

    companion object {
        /** 印字可能文字キー（Shift 適用済み文字を渡す）。 */
        fun ofChar(c: Char): EditorKey = EditorKey(char = c)

        /** Ctrl+文字（基底文字は小文字に正規化）。 */
        fun ctrl(c: Char): EditorKey = EditorKey(char = c.lowercaseChar(), ctrl = true)

        fun special(s: SpecialKey): EditorKey = EditorKey(special = s)

        val ESC = special(SpecialKey.ESCAPE)
        val CR = special(SpecialKey.ENTER)
        val BS = special(SpecialKey.BACKSPACE)

        /** 文字列を1文字ずつのキー列にする（テストやマッピング用）。 */
        fun sequence(text: String): List<EditorKey> = text.map { ofChar(it) }
    }

    override fun toString(): String = buildString {
        if (ctrl || alt) {
            append('<')
            if (ctrl) append("C-")
            if (alt) append("A-")
            if (shift && char == null) append("S-")
            append(char ?: specialName(special))
            append('>')
        } else if (special != null) {
            append('<').append(specialName(special)).append('>')
        } else {
            append(char)
        }
    }

    private fun specialName(s: SpecialKey?): String = when (s) {
        SpecialKey.ESCAPE -> "Esc"
        SpecialKey.ENTER -> "CR"
        SpecialKey.BACKSPACE -> "BS"
        SpecialKey.DELETE -> "Del"
        SpecialKey.TAB -> "Tab"
        SpecialKey.UP -> "Up"
        SpecialKey.DOWN -> "Down"
        SpecialKey.LEFT -> "Left"
        SpecialKey.RIGHT -> "Right"
        SpecialKey.HOME -> "Home"
        SpecialKey.END -> "End"
        SpecialKey.PAGE_UP -> "PageUp"
        SpecialKey.PAGE_DOWN -> "PageDown"
        null -> "?"
    }
}
