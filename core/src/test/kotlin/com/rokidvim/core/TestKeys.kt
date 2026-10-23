package com.rokidvim.core

/** テスト用: 文字列を 1 文字ずつキー入力する。 */
fun EditorEngine.type(text: String) {
    text.forEach { handleKey(EditorKey.ofChar(it)) }
}

fun EditorEngine.esc() = handleKey(EditorKey.ESC)
fun EditorEngine.enter() = handleKey(EditorKey.CR)
fun EditorEngine.backspace() = handleKey(EditorKey.BS)
fun EditorEngine.ctrl(c: Char) = handleKey(EditorKey.ctrl(c))

/** ex コマンドをコマンドラインから打鍵して実行する。 */
fun EditorEngine.ex(command: String) {
    type(":")
    type(command)
    enter()
}

val EditorEngine.cursor: Position get() = snapshot().cursor
val EditorEngine.bufferLines: List<String> get() = snapshot().lines
