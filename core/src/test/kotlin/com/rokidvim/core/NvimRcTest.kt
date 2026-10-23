package com.rokidvim.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NvimRcTest {

    @Test
    fun `設定ファイルを一括適用できる`() {
        val e = EditorEngine()
        e.loadText("")
        val result = NvimRc.apply(
            e,
            """
            |" コメント行
            |set number
            |set tabstop=8
            |colorscheme rokid-soft
            |inoremap jk <Esc>
            |
            |map Y yy
            """.trimMargin(),
        )
        assertTrue(result.ok)
        assertTrue(e.options.number)
        assertEquals(8, e.options.tabstop)
        assertEquals("rokid-soft", e.colorScheme.name)

        // マッピングが効いていること
        e.type("iabc")
        e.type("jk")
        assertEquals(EditorMode.NORMAL, e.snapshot().mode)
    }

    @Test
    fun `エラー行は行番号つきで報告される`() {
        val e = EditorEngine()
        e.loadText("")
        val result = NvimRc.apply(
            e,
            """
            |set number
            |set bogus
            |colorscheme nope
            """.trimMargin(),
        )
        assertEquals(2, result.errors.size)
        assertEquals(2, result.errors[0].lineNumber)
        assertTrue(result.errors[0].message.contains("E518"))
        assertEquals(3, result.errors[1].lineNumber)
        assertTrue(result.errors[1].message.contains("E185"))
        // エラーがあっても他の行は適用される
        assertTrue(e.options.number)
    }

    @Test
    fun `既定設定はエラーなく読み込める`() {
        val e = EditorEngine()
        e.loadText("")
        val result = NvimRc.apply(e, NvimRc.DEFAULT_CONTENT)
        assertTrue(result.ok, "errors: ${result.errors}")
        assertTrue(e.options.number)
    }
}
