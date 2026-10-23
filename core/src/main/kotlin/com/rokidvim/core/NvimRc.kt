package com.rokidvim.core

/**
 * .nvimrc 風設定の読み込み。
 *
 * 各行をそのまま ex コマンドとして実行する。
 * `"` 始まりの行はコメント。空行は無視。
 *
 * 例:
 * ```
 * " rokid-vim 設定
 * set number
 * set tabstop=4
 * colorscheme rokid-green
 * inoremap jk <Esc>
 * nnoremap <Space>w :w<CR>   " ← rhs のコマンド実行は未対応（今後）
 * map Y yy
 * ```
 */
object NvimRc {

    const val DEFAULT_FILE_NAME = ".nvimrc"

    data class LineError(val lineNumber: Int, val message: String)

    data class Result(val errors: List<LineError>) {
        val ok: Boolean get() = errors.isEmpty()
    }

    /** 設定テキストを 1 行ずつ ex コマンドとして適用する。 */
    fun apply(engine: EditorEngine, content: String): Result {
        val errors = mutableListOf<LineError>()
        content.split("\n").forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("\"")) return@forEachIndexed
            val err = engine.executeExCommand(line)
            if (err != null) {
                errors.add(LineError(index + 1, err))
            }
        }
        return Result(errors)
    }

    /** 初回起動時に書き出す既定の設定。 */
    val DEFAULT_CONTENT = """
        |" rokid-vim 設定ファイル（.nvimrc 風）
        |" 行番号を表示
        |set number
        |" タブ幅
        |set tabstop=4
        |" カーソル上下に確保する行数
        |set scrolloff=2
        |" 配色: rokid-green / rokid-soft / rokid-mono
        |colorscheme rokid-green
        |" 挿入モードから jk で抜ける
        |inoremap jk <Esc>
        |" Y で行ヤンク
        |map Y yy
    """.trimMargin()
}
