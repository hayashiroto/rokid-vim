package com.rokidvim.core

/** 1 つのキーマッピング。 */
data class Mapping(
    val lhs: List<EditorKey>,
    val rhs: List<EditorKey>,
    /** true なら rhs を再マップしない（nnoremap / inoremap）。 */
    val noremap: Boolean,
)

/**
 * モード別マッピングの解決器。
 *
 * 複数キー lhs（例: `inoremap jk <Esc>`）に対応するため、入力を保留しながら
 * 前方一致を追う。保留中のキーはタイムアウト時に UI 層が [flushPending] で流す。
 */
class MappingResolver {

    private val tables = mutableMapOf<EditorMode, MutableList<Mapping>>()
    private val pending = mutableListOf<EditorKey>()
    private var pendingMode: EditorMode? = null

    companion object {
        /** map 再帰展開の深さ上限（無限ループ対策）。 */
        const val MAX_DEPTH = 16
    }

    fun addMapping(mode: EditorMode, mapping: Mapping) {
        val list = tables.getOrPut(mode) { mutableListOf() }
        // 同じ lhs は上書き
        list.removeAll { it.lhs == mapping.lhs }
        list.add(mapping)
    }

    fun clear() {
        tables.clear()
        pending.clear()
        pendingMode = null
    }

    val hasPending: Boolean get() = pending.isNotEmpty()

    /**
     * キーを 1 つ受け取り、確定したキー列を返す。
     * マッピング待ちの間は空リストを返す。
     */
    fun offer(mode: EditorMode, key: EditorKey): List<EditorKey> {
        if (pendingMode != null && pendingMode != mode) {
            // モードが変わったら保留は破棄してやり直す
            pending.clear()
        }
        pendingMode = mode
        pending.add(key)

        val result = mutableListOf<EditorKey>()
        consumePending(mode, result)
        return result
    }

    /** 保留中のキーを確定させる（タイムアウト時に UI 層が呼ぶ）。 */
    fun flushPending(): List<EditorKey> {
        val mode = pendingMode
        if (mode != null && pending.isNotEmpty()) {
            val exact = tables[mode].orEmpty().find { it.lhs == pending }
            if (exact != null) {
                pending.clear()
                val out = mutableListOf<EditorKey>()
                expand(mode, exact, 0, out)
                return out
            }
        }
        val out = pending.toList()
        pending.clear()
        return out
    }

    private fun consumePending(mode: EditorMode, out: MutableList<EditorKey>) {
        while (pending.isNotEmpty()) {
            val table = tables[mode].orEmpty()
            val exact = table.find { it.lhs == pending }
            val longerExists = table.any { it.lhs.size > pending.size && it.lhs.subList(0, pending.size) == pending }
            if (longerExists) {
                // さらに長い候補があるので待つ。確定はタイムアウト時の flushPending で行う。
                return
            }
            if (exact != null) {
                pending.clear()
                expand(mode, exact, 0, out)
                return
            }
            // 不成立: 先頭キーを確定して残りで再試行
            out.add(pending.removeAt(0))
        }
    }

    private fun expand(mode: EditorMode, mapping: Mapping, depth: Int, out: MutableList<EditorKey>) {
        if (depth >= MAX_DEPTH) {
            // E223 相当: 再帰が深すぎる場合は展開を打ち切る
            return
        }
        if (mapping.noremap) {
            out.addAll(mapping.rhs)
            return
        }
        // 再帰マップ: rhs の中の完全一致 lhs を再帰的に展開する
        val table = tables[mode].orEmpty()
        var i = 0
        while (i < mapping.rhs.size) {
            var matched: Mapping? = null
            // 長い lhs を優先して前方一致を探す
            for (m in table.sortedByDescending { it.lhs.size }) {
                if (i + m.lhs.size <= mapping.rhs.size && mapping.rhs.subList(i, i + m.lhs.size) == m.lhs) {
                    matched = m
                    break
                }
            }
            if (matched != null && matched !== mapping) {
                expand(mode, matched, depth + 1, out)
                i += matched.lhs.size
            } else {
                out.add(mapping.rhs[i])
                i++
            }
        }
    }
}
