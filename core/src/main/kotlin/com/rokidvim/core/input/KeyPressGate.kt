package com.rokidvim.core.input

/**
 * 二重 down の除去（clawsses の KeyPressGate 相当）。
 *
 * 同じ物理キー押下が dispatchKeyEvent と dispatchKeyShortcutEvent の両方から
 * 届くことがあるため、(keyId, downTime) の組で重複を弾く。
 * また「down を見ていないのに up が来た」ケース（Ctrl chord のシステム先取り）を
 * 検出できるよう、down を見たキーを記録する。
 */
class KeyPressGate(
    /** downTime が取れないイベント向けの重複判定ウィンドウ（ms）。 */
    private val dedupeWindowMs: Long = 50,
) {
    private data class Entry(val downTime: Long, val processedAt: Long)

    private val processedDown = mutableMapOf<Int, Entry>()

    /**
     * down イベントを処理すべきか。重複なら false。
     *
     * @param keyId プラットフォームのキーコードなど、キーを識別できる値
     * @param downTime 押下開始時刻（ms）。取れなければ 0
     * @param eventTime イベント時刻（ms）
     */
    fun shouldProcessDown(keyId: Int, downTime: Long, eventTime: Long): Boolean {
        val prev = processedDown[keyId]
        if (prev != null) {
            if (downTime != 0L && prev.downTime == downTime) return false
            if (downTime == 0L && eventTime - prev.processedAt < dedupeWindowMs) return false
        }
        processedDown[keyId] = Entry(downTime, eventTime)
        return true
    }

    /** このキーの down を（この押下サイクルで）処理済みか。 */
    fun sawDown(keyId: Int): Boolean = processedDown.containsKey(keyId)

    /** up イベントで呼ぶ。押下サイクルを閉じる。 */
    fun onKeyUp(keyId: Int) {
        processedDown.remove(keyId)
    }

    fun reset() {
        processedDown.clear()
    }
}
