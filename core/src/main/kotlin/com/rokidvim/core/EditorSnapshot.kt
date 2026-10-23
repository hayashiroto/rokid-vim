package com.rokidvim.core

/** バッファ上の位置（0 始まり。col は Char インデックス）。 */
data class Position(val line: Int, val col: Int)

/**
 * 描画に必要な状態の不変スナップショット。
 * UI 層はこれだけを見てグリッドを描く。
 */
data class EditorSnapshot(
    val lines: List<String>,
    val cursor: Position,
    val mode: EditorMode,
    /** 入力途中のカウント・オペレータ表示（例: "3d"）。 */
    val pending: String,
    /** COMMAND モード中のコマンドライン内容（プレフィックス込み。例: ":w", "/foo"）。 */
    val commandLine: String?,
    val message: String,
    val messageIsError: Boolean,
    /** VISUAL モード中の選択起点。 */
    val visualAnchor: Position?,
    val fileName: String?,
    val modified: Boolean,
    val options: EditorOptions,
    val colorScheme: ColorScheme,
) {
    /** ステータス行に出すモード表示。 */
    val modeLabel: String
        get() = when (mode) {
            EditorMode.NORMAL -> ""
            EditorMode.INSERT -> "-- INSERT --"
            EditorMode.VISUAL -> "-- VISUAL --"
            EditorMode.COMMAND -> ""
        }

    /** 選択範囲（開始・終了を正規化した閉区間）。VISUAL 以外は null。 */
    val selection: Pair<Position, Position>?
        get() {
            val anchor = visualAnchor ?: return null
            val a = anchor
            val b = cursor
            return if (a.line < b.line || (a.line == b.line && a.col <= b.col)) a to b else b to a
        }
}
