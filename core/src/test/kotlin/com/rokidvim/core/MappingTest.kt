package com.rokidvim.core

import kotlin.test.Test
import kotlin.test.assertEquals

class MappingTest {

    private fun engine(text: String): EditorEngine =
        EditorEngine().apply { loadText(text) }

    @Test
    fun `inoremap jk で挿入モードを抜けられる`() {
        val e = engine("")
        e.ex("inoremap jk <Esc>")
        e.type("ihello")
        e.type("jk")
        assertEquals(EditorMode.NORMAL, e.snapshot().mode)
        assertEquals(listOf("hello"), e.bufferLines)
    }

    @Test
    fun `マッピング途中の j はタイムアウトで確定する`() {
        val e = engine("")
        e.ex("inoremap jk <Esc>")
        e.type("ia")
        e.type("j")
        // j は保留中でまだ挿入されていない
        assertEquals(listOf("a"), e.bufferLines)
        assertEquals(true, e.hasPendingMapping)
        e.flushPendingMappings()
        assertEquals(listOf("aj"), e.bufferLines)
        assertEquals(EditorMode.INSERT, e.snapshot().mode)
    }

    @Test
    fun `マッピングに一致しない連なりはそのまま入力される`() {
        val e = engine("")
        e.ex("inoremap jk <Esc>")
        e.type("ijjab")
        // 1つ目の j は2文字目の j が来た時点で確定する
        assertEquals(listOf("jjab"), e.bufferLines.map { it })
        assertEquals(EditorMode.INSERT, e.snapshot().mode)
    }

    @Test
    fun `nnoremap で normal のキーを差し替える`() {
        val e = engine("one\ntwo\nthree")
        e.ex("nnoremap J 3j")
        e.type("J")
        // J は3行下へ（バッファは3行なので最終行）
        assertEquals(Position(2, 0), e.cursor)
    }

    @Test
    fun `nnoremap は insert モードに影響しない`() {
        val e = engine("")
        e.ex("nnoremap x dd")
        e.type("ix")
        e.esc()
        assertEquals(listOf("x"), e.bufferLines)
    }

    @Test
    fun `map は normal と visual の両方に効く`() {
        val e = engine("abcdef")
        e.ex("map L $")
        e.type("L")
        assertEquals(Position(0, 5), e.cursor)
        e.type("0vLd")
        assertEquals(listOf(""), e.bufferLines)
    }

    @Test
    fun `map は再帰的に展開される`() {
        val e = engine("one\ntwo\nthree")
        e.ex("map Y yy")
        e.ex("map Z Yp")
        e.type("Z")
        assertEquals(listOf("one", "one", "two", "three"), e.bufferLines)
    }

    @Test
    fun `noremap は再帰展開しない`() {
        val e = engine("abc")
        e.ex("nnoremap q x")
        e.ex("nnoremap x j")
        e.type("q")
        // q の rhs の x は再マップされず、組み込みの x（1文字削除）になる
        assertEquals(listOf("bc"), e.bufferLines)
        assertEquals(Position(0, 0), e.cursor)
    }

    @Test
    fun `相互参照マップでも無限ループしない`() {
        val e = engine("abc")
        e.ex("map q w")
        e.ex("map w q")
        e.type("q")
        // 展開は深さ上限で打ち切られ、ハングせずバッファも壊れない
        assertEquals(listOf("abc"), e.bufferLines)
    }

    @Test
    fun `KeyNotation が各種表記をパースする`() {
        assertEquals(listOf(EditorKey.ESC), KeyNotation.parse("<Esc>"))
        assertEquals(listOf(EditorKey.CR), KeyNotation.parse("<CR>"))
        assertEquals(listOf(EditorKey.ctrl('r')), KeyNotation.parse("<C-r>"))
        assertEquals(
            listOf(EditorKey.ofChar('j'), EditorKey.ofChar('k')),
            KeyNotation.parse("jk"),
        )
        assertEquals(listOf(EditorKey.ofChar(' ')), KeyNotation.parse("<Space>"))
        assertEquals(listOf(EditorKey.ofChar('<')), KeyNotation.parse("<lt>"))
        assertEquals(null, KeyNotation.parse("<Nope>"))
        assertEquals(null, KeyNotation.parse("<C-"))
    }

    @Test
    fun `不正なマッピングは E474`() {
        val e = engine("a")
        val err = e.executeExCommand("map x")
        assertEquals(true, err?.contains("E474"))
    }
}
