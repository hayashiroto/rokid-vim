package com.rokidvim.core

/**
 * `:w` / `:e` の読み書き先。ホスト（Android アプリ等）が実装する。
 * コアはパス表現やストレージ方式に関知しない。
 */
interface FileStore {
    /** 存在しなければ null。 */
    fun read(name: String): String?

    /** UTF-8 で保存する。失敗時は例外を投げてよい（コアがメッセージ化する）。 */
    fun write(name: String, content: String)
}

/** テスト・プレビュー用のメモリ実装。 */
class InMemoryFileStore : FileStore {
    val files = mutableMapOf<String, String>()
    override fun read(name: String): String? = files[name]
    override fun write(name: String, content: String) {
        files[name] = content
    }
}
