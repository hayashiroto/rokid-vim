package com.rokidvim.android

import android.view.KeyEvent
import com.rokidvim.core.EditorKey
import com.rokidvim.core.input.KeyPressGate
import com.rokidvim.core.input.KeyRepeatGovernor
import com.rokidvim.core.input.ModifierTracker

/**
 * KeyEvent → EditorKey の入力パイプライン。
 *
 * Rokid 眼鏡での既知の問題に対応する:
 * - Ctrl 付きキーがシステムに先取りされ、keyDown が来ず keyUp だけ届く
 *   → dispatchKeyEvent と dispatchKeyShortcutEvent の両方で受け、
 *     down を見ていない up から合成する（CtrlTrack 相当）。
 * - 同じ押下が複数経路から届く → (keyCode, downTime) で二重処理を除去（KeyPressGate 相当）。
 * - 押しっぱなしの暴走 → リピートを設定可能な遅延・間隔に間引く。
 *
 * Activity の dispatchKeyEvent / dispatchKeyShortcutEvent から呼ぶ。
 */
class InputPipeline(
    /** BACK キーを Esc として扱う（眼鏡に物理 Esc が無い場合の代替）。 */
    var backAsEscape: Boolean = true,
) {
    val modifiers = ModifierTracker()
    val gate = KeyPressGate()
    val repeat = KeyRepeatGovernor()

    /**
     * @param key エディタに渡すキー（null なら渡すものなし）
     * @param consumed true ならイベントを消費した（super に回さない）
     */
    data class Result(val key: EditorKey?, val consumed: Boolean) {
        companion object {
            val CONSUMED = Result(null, true)
            val PASS = Result(null, false)
        }
    }

    /** Activity.dispatchKeyEvent から呼ぶ。 */
    fun onKeyEvent(event: KeyEvent): Result {
        val mod = KeyEventMapper.modifierOf(event.keyCode)
        if (mod != null) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> modifiers.onModifier(mod, true)
                KeyEvent.ACTION_UP -> modifiers.onModifier(mod, false)
            }
            return Result.CONSUMED
        }

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> onDown(event)
            KeyEvent.ACTION_UP -> onUp(event)
            else -> Result.PASS
        }
    }

    /**
     * Activity.dispatchKeyShortcutEvent から呼ぶ。
     * Ctrl 付き chord が通常経路で握り潰された場合、ここに届くことがある。
     */
    fun onKeyShortcutEvent(event: KeyEvent): Result {
        if (!gate.shouldProcessDown(event.keyCode, event.downTime, event.eventTime)) {
            return Result.CONSUMED
        }
        val key = KeyEventMapper.toEditorKey(
            event,
            ctrlDown = true, // shortcut イベントは Ctrl chord
            altDown = modifiers.alt,
            backAsEscape = backAsEscape,
        )
        return if (key != null) Result(key, true) else Result.PASS
    }

    /** フォーカス喪失時などに呼ぶ（修飾キーの押しっぱなし対策）。 */
    fun reset() {
        modifiers.reset()
        gate.reset()
        repeat.reset()
    }

    private fun onDown(event: KeyEvent): Result {
        if (!repeat.allow(event.keyCode, event.repeatCount, event.eventTime)) {
            return Result.CONSUMED
        }
        if (event.repeatCount == 0 &&
            !gate.shouldProcessDown(event.keyCode, event.downTime, event.eventTime)
        ) {
            return Result.CONSUMED
        }
        val key = KeyEventMapper.toEditorKey(event, modifiers.ctrl, modifiers.alt, backAsEscape)
        return if (key != null) Result(key, true) else Result.PASS
    }

    private fun onUp(event: KeyEvent): Result {
        val sawDown = gate.sawDown(event.keyCode)
        gate.onKeyUp(event.keyCode)

        // down がシステムに先取りされて up だけ届いた Ctrl chord を合成する
        val effectiveCtrl = modifiers.ctrl || event.isCtrlPressed
        if (!sawDown && effectiveCtrl) {
            val key = KeyEventMapper.toEditorKey(event, modifiers.ctrl, modifiers.alt, backAsEscape)
            if (key != null && key.ctrl) {
                return Result(key, true)
            }
        }

        // BACK を Esc にしている場合は up も消費してシステムの「戻る」を抑止する
        if (backAsEscape && event.keyCode == KeyEvent.KEYCODE_BACK) {
            return Result.CONSUMED
        }
        return Result.PASS
    }
}
