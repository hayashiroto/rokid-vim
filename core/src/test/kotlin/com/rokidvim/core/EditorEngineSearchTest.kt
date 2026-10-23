package com.rokidvim.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorEngineSearchTest {

    private fun engine(text: String): EditorEngine =
        EditorEngine().apply { loadText(text) }

    private fun EditorEngine.search(pattern: String) {
        type("/")
        assertEquals(EditorMode.COMMAND, snapshot().mode)
        type(pattern)
        enter()
    }

    @Test
    fun `スラッシュ検索で次の一致へ飛ぶ`() {
        val e = engine("foo bar\nbaz foo")
        e.search("foo")
        assertEquals(Position(1, 4), e.cursor)
    }

    @Test
    fun `コマンドライン表示にパターンが出る`() {
        val e = engine("abc")
        e.type("/ab")
        assertEquals("/ab", e.snapshot().commandLine)
        e.esc()
        assertEquals(null, e.snapshot().commandLine)
    }

    @Test
    fun `n で次へ N で前へ`() {
        val e = engine("a foo b\nfoo\nc foo")
        e.search("foo")
        assertEquals(Position(0, 2), e.cursor)
        e.type("n")
        assertEquals(Position(1, 0), e.cursor)
        e.type("n")
        assertEquals(Position(2, 2), e.cursor)
        e.type("N")
        assertEquals(Position(1, 0), e.cursor)
    }

    @Test
    fun `末尾まで行くと先頭に折り返す`() {
        val e = engine("foo\nbar\nfoo")
        e.search("foo")
        assertEquals(Position(2, 0), e.cursor)
        e.type("n")
        assertEquals(Position(0, 0), e.cursor)
        assertTrue(e.snapshot().message.contains("TOP"))
    }

    @Test
    fun `見つからないとエラーメッセージ`() {
        val e = engine("abc")
        e.search("xyz")
        assertTrue(e.snapshot().messageIsError)
        assertTrue(e.snapshot().message.contains("E486"))
        assertEquals(Position(0, 0), e.cursor)
    }

    @Test
    fun `空パターンは直前の検索を繰り返す`() {
        val e = engine("foo\nfoo\nfoo")
        e.search("foo")
        assertEquals(Position(1, 0), e.cursor)
        e.search("")
        assertEquals(Position(2, 0), e.cursor)
    }

    @Test
    fun `検索履歴なしで n はエラー`() {
        val e = engine("abc")
        e.type("n")
        assertTrue(e.snapshot().message.contains("E35"))
    }

    @Test
    fun `日本語も検索できる`() {
        val e = engine("これはテスト\nテストです")
        e.search("テスト")
        assertEquals(Position(0, 3), e.cursor)
        e.type("n")
        assertEquals(Position(1, 0), e.cursor)
    }
}
