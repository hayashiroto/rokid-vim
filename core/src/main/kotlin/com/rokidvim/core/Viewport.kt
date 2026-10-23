package com.rokidvim.core

/**
 * スクロール位置の計算（PromptViewport 相当のキャレット可視化）。
 * UI 層が毎フレーム [ensureVisible] を呼び、topLine / leftCol を描画に使う。
 */
class Viewport {
    /** 最上段に表示するバッファ行（0 始まり）。 */
    var topLine: Int = 0
        private set

    /** 左端の表示桁（0 始まり）。 */
    var leftCol: Int = 0
        private set

    fun reset() {
        topLine = 0
        leftCol = 0
    }

    /**
     * カーソルが見えるように topLine / leftCol を調整する。
     *
     * @param cursorLine カーソル行
     * @param cursorDisplayCol カーソルの表示桁（行番号ガターを除く本文内の桁）
     * @param cursorWidth カーソル下の文字の表示幅（全角なら 2）
     * @param rows 本文領域の行数
     * @param cols 本文領域の桁数（行番号ガターを除く）
     * @param scrolloff カーソル上下に確保する行数
     * @param totalLines バッファ総行数
     */
    fun ensureVisible(
        cursorLine: Int,
        cursorDisplayCol: Int,
        cursorWidth: Int,
        rows: Int,
        cols: Int,
        scrolloff: Int,
        totalLines: Int,
    ) {
        if (rows <= 0 || cols <= 0) return
        val off = scrolloff.coerceAtMost((rows - 1) / 2).coerceAtLeast(0)

        if (cursorLine < topLine + off) {
            topLine = (cursorLine - off).coerceAtLeast(0)
        }
        if (cursorLine > topLine + rows - 1 - off) {
            topLine = cursorLine - rows + 1 + off
        }
        topLine = topLine.coerceIn(0, (totalLines - 1).coerceAtLeast(0))

        val w = cursorWidth.coerceAtLeast(1)
        if (cursorDisplayCol < leftCol) {
            leftCol = cursorDisplayCol
        }
        if (cursorDisplayCol + w > leftCol + cols) {
            leftCol = cursorDisplayCol + w - cols
        }
        leftCol = leftCol.coerceAtLeast(0)
    }
}
