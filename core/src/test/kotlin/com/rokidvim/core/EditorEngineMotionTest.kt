package com.rokidvim.core

import kotlin.test.Test
import kotlin.test.assertEquals

class EditorEngineMotionTest {

    private fun engine(text: String): EditorEngine =
        EditorEngine().apply { loadText(text) }

    @Test
    fun `hjkl で移動できる`() {
        val e = engine("abc\ndef\nghi")
        e.type("ll")
        assertEquals(Position(0, 2), e.cursor)
        e.type("j")
        assertEquals(Position(1, 2), e.cursor)
        e.type("h")
        assertEquals(Position(1, 1), e.cursor)
        e.type("k")
        assertEquals(Position(0, 1), e.cursor)
    }

    @Test
    fun `l は行末で止まり h は行頭で止まる`() {
        val e = engine("ab")
        e.type("llll")
        assertEquals(Position(0, 1), e.cursor)
        e.type("hhhh")
        assertEquals(Position(0, 0), e.cursor)
    }

    @Test
    fun `j k は行の長さに合わせてクランプされる`() {
        val e = engine("abcdef\nab\nabcdef")
        e.type("$")
        assertEquals(Position(0, 5), e.cursor)
        e.type("j")
        assertEquals(Position(1, 1), e.cursor)
        e.type("j")
        // 望み桁が保持されて元の桁に戻る
        assertEquals(Position(2, 5), e.cursor)
    }

    @Test
    fun `カウント付き移動`() {
        val e = engine("a\nb\nc\nd\ne")
        e.type("3j")
        assertEquals(Position(3, 0), e.cursor)
        e.type("2k")
        assertEquals(Position(1, 0), e.cursor)
    }

    @Test
    fun `0 と ドル で行頭行末`() {
        val e = engine("hello world")
        e.type("$")
        assertEquals(Position(0, 10), e.cursor)
        e.type("0")
        assertEquals(Position(0, 0), e.cursor)
    }

    @Test
    fun `gg と G で先頭末尾`() {
        val e = engine("one\ntwo\nthree")
        e.type("G")
        assertEquals(Position(2, 0), e.cursor)
        e.type("gg")
        assertEquals(Position(0, 0), e.cursor)
        e.type("2G")
        assertEquals(Position(1, 0), e.cursor)
        e.type("3gg")
        assertEquals(Position(2, 0), e.cursor)
    }

    @Test
    fun `G は first non-blank に移動する`() {
        val e = engine("one\n   indented")
        e.type("G")
        assertEquals(Position(1, 3), e.cursor)
    }

    @Test
    fun `w で次の単語頭へ`() {
        val e = engine("foo bar  baz")
        e.type("w")
        assertEquals(Position(0, 4), e.cursor)
        e.type("w")
        assertEquals(Position(0, 9), e.cursor)
    }

    @Test
    fun `w は記号と単語を区別する`() {
        val e = engine("foo(bar)")
        e.type("w")
        assertEquals(Position(0, 3), e.cursor) // (
        e.type("w")
        assertEquals(Position(0, 4), e.cursor) // bar
        e.type("w")
        assertEquals(Position(0, 7), e.cursor) // )
    }

    @Test
    fun `w は行をまたぐ`() {
        val e = engine("foo\nbar")
        e.type("w")
        assertEquals(Position(1, 0), e.cursor)
    }

    @Test
    fun `w は空行で止まる`() {
        val e = engine("foo\n\nbar")
        e.type("w")
        assertEquals(Position(1, 0), e.cursor)
        e.type("w")
        assertEquals(Position(2, 0), e.cursor)
    }

    @Test
    fun `w はバッファ末尾で止まる`() {
        val e = engine("foo bar")
        e.type("www")
        assertEquals(Position(0, 6), e.cursor)
    }

    @Test
    fun `b で前の単語頭へ`() {
        val e = engine("foo bar baz")
        e.type("$")
        e.type("b")
        assertEquals(Position(0, 8), e.cursor)
        e.type("b")
        assertEquals(Position(0, 4), e.cursor)
        e.type("b")
        assertEquals(Position(0, 0), e.cursor)
        e.type("b")
        assertEquals(Position(0, 0), e.cursor)
    }

    @Test
    fun `b は行をまたぐ`() {
        val e = engine("foo\nbar")
        e.type("j")
        e.type("b")
        assertEquals(Position(0, 0), e.cursor)
    }

    @Test
    fun `e で単語末尾へ`() {
        val e = engine("foo bar")
        e.type("e")
        assertEquals(Position(0, 2), e.cursor)
        e.type("e")
        assertEquals(Position(0, 6), e.cursor)
    }

    @Test
    fun `日本語の文字種で w が区切られる`() {
        val e = engine("これはテスト漢字です")
        // ひらがな → カタカナ → 漢字 → ひらがな
        e.type("w")
        assertEquals(Position(0, 3), e.cursor) // テスト
        e.type("w")
        assertEquals(Position(0, 6), e.cursor) // 漢字
        e.type("w")
        assertEquals(Position(0, 8), e.cursor) // です
    }

    @Test
    fun `矢印キーでも移動できる`() {
        val e = engine("abc\ndef")
        e.handleKey(EditorKey.special(SpecialKey.RIGHT))
        assertEquals(Position(0, 1), e.cursor)
        e.handleKey(EditorKey.special(SpecialKey.DOWN))
        assertEquals(Position(1, 1), e.cursor)
        e.handleKey(EditorKey.special(SpecialKey.LEFT))
        assertEquals(Position(1, 0), e.cursor)
        e.handleKey(EditorKey.special(SpecialKey.UP))
        assertEquals(Position(0, 0), e.cursor)
    }

    @Test
    fun `全角文字の縦移動は表示桁を保つ`() {
        val e = engine("あいう\nabcdef")
        e.type("ll") // カーソル「う」= 表示桁4
        assertEquals(Position(0, 2), e.cursor)
        e.type("j")
        // 表示桁4 → abcdef の e (index 4)
        assertEquals(Position(1, 4), e.cursor)
        e.type("k")
        assertEquals(Position(0, 2), e.cursor)
    }
}
