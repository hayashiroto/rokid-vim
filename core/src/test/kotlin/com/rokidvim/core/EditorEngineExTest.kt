package com.rokidvim.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorEngineExTest {

    private class QuitSpy : EditorEngine.Listener {
        var quitRequested = false
        var unknownCommands = mutableListOf<String>()
        var handleUnknown = false
        override fun onQuitRequested() {
            quitRequested = true
        }
        override fun onUnknownExCommand(command: String): Boolean {
            unknownCommands.add(command)
            return handleUnknown
        }
    }

    @Test
    fun `w でファイルに保存される`() {
        val store = InMemoryFileStore()
        val e = EditorEngine(fileStore = store)
        e.loadText("hello\nworld", "memo.txt")
        e.type("ccc")
        e.ex("w")
        assertEquals("hello\nworld", store.files["memo.txt"])
        assertTrue(e.snapshot().message.contains("written"))
        assertFalse(e.isModified)
    }

    @Test
    fun `w は変更を保存してフラグを下ろす`() {
        val store = InMemoryFileStore()
        val e = EditorEngine(fileStore = store)
        e.loadText("a", "f.txt")
        e.type("ib")
        e.esc()
        assertTrue(e.isModified)
        e.ex("w")
        assertEquals("ba", store.files["f.txt"])
        assertFalse(e.isModified)
    }

    @Test
    fun `ファイル名なしの w はエラー`() {
        val e = EditorEngine(fileStore = InMemoryFileStore())
        e.loadText("a")
        e.ex("w")
        assertTrue(e.snapshot().message.contains("E32"))
    }

    @Test
    fun `w に名前を渡せる`() {
        val store = InMemoryFileStore()
        val e = EditorEngine(fileStore = store)
        e.loadText("content")
        e.ex("w note.txt")
        assertEquals("content", store.files["note.txt"])
        assertEquals("note.txt", e.currentFileName)
    }

    @Test
    fun `q で終了要求が飛ぶ`() {
        val spy = QuitSpy()
        val e = EditorEngine(listener = spy)
        e.loadText("a")
        e.ex("q")
        assertTrue(spy.quitRequested)
    }

    @Test
    fun `変更ありの q はエラーで q! なら通る`() {
        val spy = QuitSpy()
        val e = EditorEngine(fileStore = InMemoryFileStore(), listener = spy)
        e.loadText("a", "f.txt")
        e.type("x")
        e.ex("q")
        assertFalse(spy.quitRequested)
        assertTrue(e.snapshot().message.contains("E37"))
        e.ex("q!")
        assertTrue(spy.quitRequested)
    }

    @Test
    fun `wq は保存してから終了する`() {
        val store = InMemoryFileStore()
        val spy = QuitSpy()
        val e = EditorEngine(fileStore = store, listener = spy)
        e.loadText("a", "f.txt")
        e.type("x")
        e.ex("wq")
        assertEquals("", store.files["f.txt"])
        assertTrue(spy.quitRequested)
    }

    @Test
    fun `e でファイルを開く`() {
        val store = InMemoryFileStore()
        store.files["other.txt"] = "zzz"
        val e = EditorEngine(fileStore = store)
        e.loadText("a", "f.txt")
        e.ex("e other.txt")
        assertEquals(listOf("zzz"), e.bufferLines)
        assertEquals("other.txt", e.currentFileName)
    }

    @Test
    fun `e で存在しない名前は新規ファイル`() {
        val e = EditorEngine(fileStore = InMemoryFileStore())
        e.loadText("a", "f.txt")
        e.ex("e new.txt")
        assertEquals(listOf(""), e.bufferLines)
        assertTrue(e.snapshot().message.contains("New File"))
    }

    @Test
    fun `set number と set nonumber`() {
        val e = EditorEngine()
        e.loadText("a")
        assertFalse(e.options.number)
        e.ex("set number")
        assertTrue(e.options.number)
        e.ex("set nonumber")
        assertFalse(e.options.number)
    }

    @Test
    fun `set tabstop や columns`() {
        val e = EditorEngine()
        e.loadText("a")
        e.ex("set tabstop=8")
        assertEquals(8, e.options.tabstop)
        e.ex("set columns=40")
        assertEquals(40, e.options.columns)
        e.ex("set repeatdelay=500 repeatinterval=100")
        assertEquals(500, e.options.repeatDelayMs)
        assertEquals(100, e.options.repeatIntervalMs)
    }

    @Test
    fun `未知のオプションはエラー`() {
        val e = EditorEngine()
        e.loadText("a")
        e.ex("set wibble")
        assertTrue(e.snapshot().message.contains("E518"))
    }

    @Test
    fun `colorscheme を切り替えられる`() {
        val e = EditorEngine()
        e.loadText("a")
        assertEquals("rokid-green", e.colorScheme.name)
        e.ex("colorscheme rokid-soft")
        assertEquals("rokid-soft", e.colorScheme.name)
        e.ex("colorscheme nope")
        assertTrue(e.snapshot().message.contains("E185"))
        assertEquals("rokid-soft", e.colorScheme.name)
    }

    @Test
    fun `未知のコマンドは E492`() {
        val e = EditorEngine()
        e.loadText("a")
        e.ex("frobnicate")
        assertTrue(e.snapshot().message.contains("E492"))
    }

    @Test
    fun `未知のコマンドをリスナーが処理できる`() {
        val spy = QuitSpy().apply { handleUnknown = true }
        val e = EditorEngine(listener = spy)
        e.loadText("a")
        e.ex("diag")
        assertEquals(listOf("diag"), spy.unknownCommands)
        assertFalse(e.snapshot().message.contains("E492"))
    }

    @Test
    fun `コマンドラインを Esc で取り消せる`() {
        val e = EditorEngine()
        e.loadText("abc")
        e.type(":dd")
        assertEquals(":dd", e.snapshot().commandLine)
        e.esc()
        assertEquals(EditorMode.NORMAL, e.snapshot().mode)
        assertEquals(listOf("abc"), e.bufferLines)
    }

    @Test
    fun `コマンドラインの Backspace`() {
        val e = EditorEngine()
        e.loadText("a")
        e.type(":wq")
        e.backspace()
        assertEquals(":w", e.snapshot().commandLine)
        e.backspace()
        e.backspace()
        // 空の状態で Backspace するとノーマルへ戻る
        assertEquals(EditorMode.NORMAL, e.snapshot().mode)
    }
}
