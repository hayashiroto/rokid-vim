package com.rokidvim.core.input

/** 修飾キーの種別。 */
enum class ModifierKey {
    CTRL_LEFT, CTRL_RIGHT,
    ALT_LEFT, ALT_RIGHT,
    SHIFT_LEFT, SHIFT_RIGHT,
}

/**
 * 修飾キー状態の自前追跡（clawsses の CtrlTrack 相当）。
 *
 * Rokid 眼鏡では Ctrl 付きキーがシステムに先取りされ、文字キーの keyDown が来ず
 * keyUp だけ届くことがある。また KeyEvent の metaState が信用できない経路もあるため、
 * 修飾キー自身の down/up を追跡して「実効 Ctrl 状態」を自前で持つ。
 *
 * イベントの metaState と OR で合成して使う想定。
 */
class ModifierTracker {

    private val down = mutableSetOf<ModifierKey>()

    fun onModifier(key: ModifierKey, isDown: Boolean) {
        if (isDown) down.add(key) else down.remove(key)
    }

    val ctrl: Boolean get() = ModifierKey.CTRL_LEFT in down || ModifierKey.CTRL_RIGHT in down
    val alt: Boolean get() = ModifierKey.ALT_LEFT in down || ModifierKey.ALT_RIGHT in down
    val shift: Boolean get() = ModifierKey.SHIFT_LEFT in down || ModifierKey.SHIFT_RIGHT in down

    /**
     * フォーカス喪失時などに呼ぶ。up イベントの取りこぼしで修飾キーが
     * 押しっぱなし扱いになるのを防ぐ。
     */
    fun reset() {
        down.clear()
    }
}
