package com.rokidvim.android

import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.rokidvim.core.EditorKey
import com.rokidvim.core.SpecialKey
import com.rokidvim.core.input.ModifierKey

/**
 * Android の KeyEvent を抽象キー [EditorKey] に正規化する。
 * コアは Android 非依存なので、変換はすべてここで行う。
 */
object KeyEventMapper {

    /** 修飾キーなら対応する [ModifierKey]。 */
    fun modifierOf(keyCode: Int): ModifierKey? = when (keyCode) {
        KeyEvent.KEYCODE_CTRL_LEFT -> ModifierKey.CTRL_LEFT
        KeyEvent.KEYCODE_CTRL_RIGHT -> ModifierKey.CTRL_RIGHT
        KeyEvent.KEYCODE_ALT_LEFT -> ModifierKey.ALT_LEFT
        KeyEvent.KEYCODE_ALT_RIGHT -> ModifierKey.ALT_RIGHT
        KeyEvent.KEYCODE_SHIFT_LEFT -> ModifierKey.SHIFT_LEFT
        KeyEvent.KEYCODE_SHIFT_RIGHT -> ModifierKey.SHIFT_RIGHT
        else -> null
    }

    /**
     * 特殊キーの対応。
     * BACK は眼鏡側に物理 Esc が無い場合の代替 Esc として扱える（[backAsEscape]）。
     * DPAD_CENTER（タッチパッドのタップ相当）は Enter 扱い。
     */
    fun specialOf(keyCode: Int, backAsEscape: Boolean): SpecialKey? = when (keyCode) {
        KeyEvent.KEYCODE_ESCAPE -> SpecialKey.ESCAPE
        KeyEvent.KEYCODE_BACK -> if (backAsEscape) SpecialKey.ESCAPE else null
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER -> SpecialKey.ENTER
        KeyEvent.KEYCODE_DEL -> SpecialKey.BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> SpecialKey.DELETE
        KeyEvent.KEYCODE_TAB -> SpecialKey.TAB
        KeyEvent.KEYCODE_DPAD_UP -> SpecialKey.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> SpecialKey.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> SpecialKey.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> SpecialKey.RIGHT
        KeyEvent.KEYCODE_MOVE_HOME -> SpecialKey.HOME
        KeyEvent.KEYCODE_MOVE_END -> SpecialKey.END
        KeyEvent.KEYCODE_PAGE_UP -> SpecialKey.PAGE_UP
        KeyEvent.KEYCODE_PAGE_DOWN -> SpecialKey.PAGE_DOWN
        else -> null
    }

    /**
     * KeyEvent → EditorKey。変換できないキーは null。
     *
     * @param ctrlDown [com.rokidvim.core.input.ModifierTracker] による実効 Ctrl 状態。
     *   metaState が信用できない経路（clawsses #0143 相当）に備えて OR で合成する。
     */
    fun toEditorKey(
        event: KeyEvent,
        ctrlDown: Boolean,
        altDown: Boolean,
        backAsEscape: Boolean,
    ): EditorKey? {
        val ctrl = ctrlDown || event.isCtrlPressed
        val alt = altDown || event.isAltPressed
        val shift = event.isShiftPressed

        val special = specialOf(event.keyCode, backAsEscape)
        if (special != null) {
            return EditorKey(special = special, ctrl = ctrl, alt = alt, shift = shift)
        }

        // Ctrl/Alt を外した metaState で文字を得る（Ctrl 併用だと 0 や制御文字になるため）
        val meta = event.metaState and (KeyEvent.META_SHIFT_MASK or KeyEvent.META_CAPS_LOCK_ON)
        var ch = event.getUnicodeChar(meta)
        if (ch == 0) {
            ch = fallbackChar(event.keyCode, shift)
        }
        if (ch == 0) return null
        if (ch and KeyCharacterMap.COMBINING_ACCENT != 0) return null
        if (ch > 0xFFFF) return null // 初版は BMP のみ
        if (Character.isISOControl(ch)) return null

        val c = ch.toChar()
        return if (ctrl || alt) {
            EditorKey(char = c.lowercaseChar(), ctrl = ctrl, alt = alt, shift = shift)
        } else {
            EditorKey(char = c)
        }
    }

    /** getUnicodeChar が 0 を返したときの保険（英数字のみ）。 */
    private fun fallbackChar(keyCode: Int, shift: Boolean): Int = when (keyCode) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
            val base = 'a' + (keyCode - KeyEvent.KEYCODE_A)
            (if (shift) base.uppercaseChar() else base).code
        }
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> ('0' + (keyCode - KeyEvent.KEYCODE_0)).code
        else -> 0
    }
}
