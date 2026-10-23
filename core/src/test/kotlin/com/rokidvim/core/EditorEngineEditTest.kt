package com.rokidvim.core

import kotlin.test.Test
import kotlin.test.assertEquals

class EditorEngineEditTest {

    private fun engine(text: String): EditorEngine =
        EditorEngine().apply { loadText(text) }

    // --- 挿入 ---

    @Test
    fun `i で挿入して Esc で戻る`() {
        val e = engine("world")
        e.type("ihello ")
        assertEquals(EditorMode.INSERT, e.snapshot().mode)
        e.esc()
        assertEquals(EditorMode.NORMAL, e.snapshot().mode)
        assertEquals(listOf("hello world"), e.bufferLines)
        // Esc で1つ左に戻る（vim 準拠）
        assertEquals(Position(0, 5), e.cursor)
    }

    @Test
    fun `a はカーソルの後ろに挿入する`() {
        val e = engine("ab")
        e.type("aX")
        e.esc()
        assertEquals(listOf("aXb"), e.bufferLines)
    }

    @Test
    fun `o は下に行を開いて挿入する`() {
        val e = engine("one\ntwo")
        e.type("onew")
        e.esc()
        assertEquals(listOf("one", "new", "two"), e.bufferLines)
        assertEquals(Position(1, 2), e.cursor)
    }

    @Test
    fun `O は上に行を開いて挿入する`() {
        val e = engine("one\ntwo")
        e.type("j")
        e.type("Onew")
        e.esc()
        assertEquals(listOf("one", "new", "two"), e.bufferLines)
    }

    @Test
    fun `挿入モードの Enter で行が分かれる`() {
        val e = engine("abcd")
        e.type("ll")
        e.type("i")
        e.enter()
        e.esc()
        assertEquals(listOf("ab", "cd"), e.bufferLines)
        assertEquals(Position(1, 0), e.cursor)
    }

    @Test
    fun `挿入モードの Backspace は行頭で行を結合する`() {
        val e = engine("ab\ncd")
        e.type("j")
        e.type("i")
        e.backspace()
        assertEquals(listOf("abcd"), e.bufferLines)
        assertEquals(Position(0, 2), e.cursor)
        e.backspace()
        assertEquals(listOf("acd"), e.bufferLines)
    }

    @Test
    fun `Tab はタブ幅までスペースを入れる`() {
        val e = engine("")
        e.type("ia")
        e.handleKey(EditorKey.special(SpecialKey.TAB))
        e.type("b")
        e.esc()
        assertEquals(listOf("a   b"), e.bufferLines)
    }

    @Test
    fun `日本語を挿入できる`() {
        val e = engine("")
        e.type("iこんにちは")
        e.esc()
        assertEquals(listOf("こんにちは"), e.bufferLines)
        assertEquals(Position(0, 4), e.cursor)
    }

    // --- x / dd / yy / p ---

    @Test
    fun `x で1文字消す`() {
        val e = engine("abc")
        e.type("x")
        assertEquals(listOf("bc"), e.bufferLines)
        e.type("2x")
        assertEquals(listOf(""), e.bufferLines)
    }

    @Test
    fun `x は行末でカーソルをクランプする`() {
        val e = engine("ab")
        e.type("\$x")
        assertEquals(listOf("a"), e.bufferLines)
        assertEquals(Position(0, 0), e.cursor)
    }

    @Test
    fun `x した文字を p で貼れる`() {
        val e = engine("abc")
        e.type("x")  // "a" をレジスタへ
        e.type("p")  // b の後ろに貼る
        assertEquals(listOf("bac"), e.bufferLines)
        assertEquals(Position(0, 1), e.cursor)
    }

    @Test
    fun `dd で行を消す`() {
        val e = engine("one\ntwo\nthree")
        e.type("jdd")
        assertEquals(listOf("one", "three"), e.bufferLines)
        assertEquals(Position(1, 0), e.cursor)
    }

    @Test
    fun `2dd で2行消す`() {
        val e = engine("one\ntwo\nthree")
        e.type("2dd")
        assertEquals(listOf("three"), e.bufferLines)
    }

    @Test
    fun `最後の行を dd すると空バッファになる`() {
        val e = engine("only")
        e.type("dd")
        assertEquals(listOf(""), e.bufferLines)
        assertEquals(Position(0, 0), e.cursor)
    }

    @Test
    fun `dd した行を p で下に貼れる`() {
        val e = engine("one\ntwo")
        e.type("dd")
        assertEquals(listOf("two"), e.bufferLines)
        e.type("p")
        assertEquals(listOf("two", "one"), e.bufferLines)
        assertEquals(Position(1, 0), e.cursor)
    }

    @Test
    fun `yy と p で行を複製する`() {
        val e = engine("one\ntwo")
        e.type("yyp")
        assertEquals(listOf("one", "one", "two"), e.bufferLines)
    }

    @Test
    fun `2yy で2行ヤンクして p で貼る`() {
        val e = engine("one\ntwo\nthree")
        e.type("2yy")
        e.type("G")
        e.type("p")
        assertEquals(listOf("one", "two", "three", "one", "two"), e.bufferLines)
    }

    @Test
    fun `3p で3回貼る`() {
        val e = engine("x")
        e.type("yy3p")
        assertEquals(listOf("x", "x", "x", "x"), e.bufferLines)
    }

    @Test
    fun `d の後に d 以外を押すとオペレータは中断される`() {
        val e = engine("abc")
        e.type("dx")
        assertEquals(listOf("abc"), e.bufferLines)
        // 中断後の x は普通に効く
        e.type("x")
        assertEquals(listOf("bc"), e.bufferLines)
    }

    // --- VISUAL ---

    @Test
    fun `visual 選択を d で消す`() {
        val e = engine("hello world")
        e.type("v4l")  // h〜o を選択
        assertEquals(EditorMode.VISUAL, e.snapshot().mode)
        e.type("d")
        assertEquals(listOf(" world"), e.bufferLines)
        assertEquals(EditorMode.NORMAL, e.snapshot().mode)
    }

    @Test
    fun `visual 選択を y で貼り直せる`() {
        val e = engine("abc")
        e.type("vly")  // "ab" をヤンク（カーソルは選択先頭へ）
        assertEquals(Position(0, 0), e.cursor)
        e.type("p")
        assertEquals(listOf("aabbc"), e.bufferLines)
    }

    @Test
    fun `複数行の visual 削除`() {
        val e = engine("one\ntwo\nthree")
        e.type("lvjl")  // (0,1) から (1,2) まで = "ne" + 改行 + "two"
        e.type("d")
        assertEquals(listOf("o", "three"), e.bufferLines)
        assertEquals(Position(0, 0), e.cursor)
    }

    @Test
    fun `visual は逆向き選択でも動く`() {
        val e = engine("abcde")
        e.type("\$vhh")  // e から後ろ向きに c まで
        e.type("d")
        assertEquals(listOf("ab"), e.bufferLines)
    }

    @Test
    fun `Esc で visual を抜ける`() {
        val e = engine("abc")
        e.type("vl")
        e.esc()
        assertEquals(EditorMode.NORMAL, e.snapshot().mode)
        assertEquals(null, e.snapshot().visualAnchor)
    }

    @Test
    fun `全角文字を x で消す`() {
        val e = engine("あいう")
        e.type("x")
        assertEquals(listOf("いう"), e.bufferLines)
    }

    @Test
    fun `全角文字を含む visual 削除`() {
        val e = engine("aあbいc")
        e.type("lvl")  // あ〜b
        e.type("d")
        assertEquals(listOf("aいc"), e.bufferLines)
    }
}
