package com.rokidvim.core.input

/**
 * キーリピートの制御。
 *
 * 押しっぱなしでの入力暴走を防ぐため、OS のリピートイベント
 * （repeatCount > 0）を設定した遅延・間隔に間引く。
 */
class KeyRepeatGovernor(
    var delayMs: Long = 350,
    var intervalMs: Long = 70,
    /** false ならリピートを全て捨てる。 */
    var repeatEnabled: Boolean = true,
) {
    private var downKeyId: Int? = null
    private var downAt: Long = 0
    private var lastAllowedAt: Long = 0

    /**
     * このイベントを通すか。
     *
     * @param keyId キー識別値
     * @param repeatCount 0 なら初回押下
     * @param eventTime イベント時刻（ms）
     */
    fun allow(keyId: Int, repeatCount: Int, eventTime: Long): Boolean {
        if (repeatCount == 0) {
            downKeyId = keyId
            downAt = eventTime
            lastAllowedAt = eventTime
            return true
        }
        if (!repeatEnabled) return false
        if (downKeyId != keyId) {
            // 初回 down を見ていないリピート: 受け付けて基準を作る
            downKeyId = keyId
            downAt = eventTime
            lastAllowedAt = eventTime
            return true
        }
        if (eventTime - downAt < delayMs) return false
        if (eventTime - lastAllowedAt < intervalMs) return false
        lastAllowedAt = eventTime
        return true
    }

    fun reset() {
        downKeyId = null
    }
}
