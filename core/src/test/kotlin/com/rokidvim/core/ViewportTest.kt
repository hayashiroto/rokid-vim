package com.rokidvim.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewportTest {

    @Test
    fun `カーソルが下に出たらスクロールする`() {
        val vp = Viewport()
        vp.ensureVisible(
            cursorLine = 15, cursorDisplayCol = 0, cursorWidth = 1,
            rows = 10, cols = 40, scrolloff = 0, totalLines = 100,
        )
        assertEquals(6, vp.topLine)
        assertEquals(0, vp.leftCol)
    }

    @Test
    fun `カーソルが上に出たら戻る`() {
        val vp = Viewport()
        vp.ensureVisible(15, 0, 1, 10, 40, 0, 100)
        vp.ensureVisible(3, 0, 1, 10, 40, 0, 100)
        assertEquals(3, vp.topLine)
    }

    @Test
    fun `scrolloff が効く`() {
        val vp = Viewport()
        vp.ensureVisible(9, 0, 1, 10, 40, 2, 100)
        // 行9 が下から3行目以内に収まるように
        assertEquals(2, vp.topLine)
    }

    @Test
    fun `横スクロール`() {
        val vp = Viewport()
        vp.ensureVisible(0, 50, 1, 10, 40, 0, 1)
        assertEquals(11, vp.leftCol)
        vp.ensureVisible(0, 5, 1, 10, 40, 0, 1)
        assertEquals(5, vp.leftCol)
    }

    @Test
    fun `全角カーソルは2桁ぶん見えるようにする`() {
        val vp = Viewport()
        vp.ensureVisible(0, 39, 2, 10, 40, 0, 1)
        assertEquals(1, vp.leftCol)
    }

    @Test
    fun `先頭に戻ると 0 に戻る`() {
        val vp = Viewport()
        vp.ensureVisible(50, 50, 1, 10, 40, 0, 100)
        vp.ensureVisible(0, 0, 1, 10, 40, 0, 100)
        assertEquals(0, vp.topLine)
        assertEquals(0, vp.leftCol)
    }
}
