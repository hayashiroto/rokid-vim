package com.rokidvim.core

/**
 * `:set` と .nvimrc で変えられるオプション。
 * UI 層（列数・キーリピート）にもここから渡す。
 */
data class EditorOptions(
    /** 行番号表示（set number / set nonumber）。 */
    val number: Boolean = false,
    /** タブ幅（set tabstop=4）。挿入時はスペース展開する。 */
    val tabstop: Int = 4,
    /** カーソル上下の確保行数（set scrolloff=2）。 */
    val scrolloff: Int = 2,
    /** 画面列数の希望値。null なら UI が自動決定（set columns=40）。 */
    val columns: Int? = null,
    /** キーリピート開始までの遅延 ms（set repeatdelay=350）。 */
    val repeatDelayMs: Int = 350,
    /** キーリピート間隔 ms（set repeatinterval=70）。 */
    val repeatIntervalMs: Int = 70,
)
