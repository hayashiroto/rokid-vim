package com.rokidvim.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorEngineUndoTest {

    private fun engine(text: String): EditorEngine =
        EditorEngine().apply { loadText(text) }

    @Test
    fun `x を u で戻せる`() {
        val e = engine("abc")
        e.type("x")
        assertEquals(listOf("bc"), e.bufferLines)
        e.type("u")
        assertEquals(listOf("abc"), e.bufferLines)
    }

    @Test
    fun `dd を u で戻して Ctrl-r でやり直せる`() {
        val e = engine("one\ntwo")
        e.type("dd")
        assertEquals(listOf("two"), e.bufferLines)
        e.type("u")
        assertEquals(listOf("one", "two"), e.bufferLines)
        e.ctrl('r')
        assertEquals(listOf("two"), e.bufferLines)
    }

    @Test
    fun `挿入セッション全体が1回の undo になる`() {
        val e = engine("x")
        e.type("ihello")
        e.esc()
        assertEquals(listOf("hellox"), e.bufferLines)
        e.type("u")
        assertEquals(listOf("x"), e.bufferLines)
    }

    @Test
    fun `o で開いた行と挿入が1回の undo になる`() {
        val e = engine("one")
        e.type("onew line")
        e.esc()
        assertEquals(listOf("one", "new line"), e.bufferLines)
        e.type("u")
        assertEquals(listOf("one"), e.bufferLines)
    }

    @Test
    fun `挿入せずに i → Esc しても undo 履歴は増えない`() {
        val e = engine("abc")
        e.type("x")       // undo 1件
        e.type("i")
        e.esc()           // 変更なし
        e.type("u")
        assertEquals(listOf("abc"), e.bufferLines)
        e.type("u")
        assertTrue(e.snapshot().message.contains("oldest"))
    }

    @Test
    fun `複数回の undo と redo`() {
        val e = engine("abc")
        e.type("x")  // bc
        e.type("x")  // c
        e.type("2u")
        assertEquals(listOf("abc"), e.bufferLines)
        e.ctrl('r')
        assertEquals(listOf("bc"), e.bufferLines)
        e.ctrl('r')
        assertEquals(listOf("c"), e.bufferLines)
    }

    @Test
    fun `新しい変更で redo は消える`() {
        val e = engine("abc")
        e.type("x")
        e.type("u")
        e.type("dd")
        e.ctrl('r')
        assertTrue(e.snapshot().message.contains("newest"))
        assertEquals(listOf(""), e.bufferLines)
    }

    @Test
    fun `undo はカーソル位置も戻す`() {
        val e = engine("one\ntwo\nthree")
        e.type("G")
        e.type("dd")
        e.type("u")
        assertEquals(Position(2, 0), e.cursor)
    }
}
