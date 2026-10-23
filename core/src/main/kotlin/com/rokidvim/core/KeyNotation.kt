package com.rokidvim.core

/**
 * `<C-r>` `<Esc>` `<CR>` などの Vim 風キー表記をパースする。
 * map / nnoremap / inoremap の lhs・rhs で使う。
 */
object KeyNotation {

    /** 表記文字列をキー列に変換する。不正な表記は null。 */
    fun parse(notation: String): List<EditorKey>? {
        val keys = mutableListOf<EditorKey>()
        var i = 0
        while (i < notation.length) {
            val c = notation[i]
            if (c == '<') {
                val end = notation.indexOf('>', i + 1)
                if (end < 0) return null
                val inner = notation.substring(i + 1, end)
                val key = parseAngle(inner) ?: return null
                keys.add(key)
                i = end + 1
            } else {
                keys.add(EditorKey.ofChar(c))
                i++
            }
        }
        return keys
    }

    private fun parseAngle(inner: String): EditorKey? {
        var ctrl = false
        var alt = false
        var shift = false
        var rest = inner
        loop@ while (true) {
            when {
                rest.length > 2 && (rest.startsWith("C-") || rest.startsWith("c-")) -> {
                    ctrl = true; rest = rest.substring(2)
                }
                rest.length > 2 && (rest.startsWith("A-") || rest.startsWith("M-")) -> {
                    alt = true; rest = rest.substring(2)
                }
                rest.length > 2 && rest.startsWith("S-") -> {
                    shift = true; rest = rest.substring(2)
                }
                else -> break@loop
            }
        }
        val special = specialByName(rest)
        if (special != null) {
            return EditorKey(special = special, ctrl = ctrl, alt = alt, shift = shift)
        }
        if (rest.length == 1) {
            val ch = if (ctrl || alt) rest[0].lowercaseChar() else rest[0]
            return EditorKey(char = ch, ctrl = ctrl, alt = alt, shift = shift)
        }
        if (rest.equals("lt", ignoreCase = true)) {
            return EditorKey(char = '<', ctrl = ctrl, alt = alt, shift = shift)
        }
        if (rest.equals("Space", ignoreCase = true)) {
            return EditorKey(char = ' ', ctrl = ctrl, alt = alt, shift = shift)
        }
        return null
    }

    private fun specialByName(name: String): SpecialKey? = when (name.lowercase()) {
        "esc" -> SpecialKey.ESCAPE
        "cr", "enter", "return" -> SpecialKey.ENTER
        "bs" -> SpecialKey.BACKSPACE
        "del" -> SpecialKey.DELETE
        "tab" -> SpecialKey.TAB
        "up" -> SpecialKey.UP
        "down" -> SpecialKey.DOWN
        "left" -> SpecialKey.LEFT
        "right" -> SpecialKey.RIGHT
        "home" -> SpecialKey.HOME
        "end" -> SpecialKey.END
        "pageup" -> SpecialKey.PAGE_UP
        "pagedown" -> SpecialKey.PAGE_DOWN
        else -> null
    }
}
