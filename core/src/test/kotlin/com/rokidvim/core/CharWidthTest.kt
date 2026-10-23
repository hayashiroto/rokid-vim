package com.rokidvim.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CharWidthTest {

    @Test
    fun `半角は幅1`() {
        assertEquals(1, CharWidth.of('a'.code))
        assertEquals(1, CharWidth.of('0'.code))
        assertEquals(1, CharWidth.of(' '.code))
        assertEquals(1, CharWidth.of('ｱ'.code)) // 半角カナ (U+FF71 は 0xFF61-0xFF9F → Halfwidth)
    }

    @Test
    fun `全角は幅2`() {
        assertEquals(2, CharWidth.of('あ'.code))
        assertEquals(2, CharWidth.of('ア'.code))
        assertEquals(2, CharWidth.of('漢'.code))
        assertEquals(2, CharWidth.of('。'.code))
        assertEquals(2, CharWidth.of('Ａ'.code)) // 全角英字
        assertEquals(2, CharWidth.of('￥'.code))
    }

    @Test
    fun `結合文字は幅0`() {
        assertEquals(0, CharWidth.of(0x3099)) // 濁点（結合）
    }

    @Test
    fun `displayCol は全角を2桁で数える`() {
        assertEquals(0, CharWidth.displayCol("あiう", 0))
        assertEquals(2, CharWidth.displayCol("あiう", 1))
        assertEquals(3, CharWidth.displayCol("あiう", 2))
        assertEquals(5, CharWidth.displayCol("あiう", 3))
    }

    @Test
    fun `displayWidth はタブをタブストップまで伸ばす`() {
        assertEquals(4, CharWidth.displayWidth("\t", 4))
        assertEquals(4, CharWidth.displayWidth("ab\t", 4))
        assertEquals(8, CharWidth.displayWidth("abcd\t", 4))
        assertEquals(5, CharWidth.displayWidth("\ta", 4))
    }

    @Test
    fun `charIndexForDisplayCol は全角の途中なら手前に止まる`() {
        // "あいう" 表示桁: あ=0-1, い=2-3, う=4-5
        assertEquals(0, CharWidth.charIndexForDisplayCol("あいう", 0))
        assertEquals(0, CharWidth.charIndexForDisplayCol("あいう", 1))
        assertEquals(1, CharWidth.charIndexForDisplayCol("あいう", 2))
        assertEquals(2, CharWidth.charIndexForDisplayCol("あいう", 5))
        assertEquals(3, CharWidth.charIndexForDisplayCol("あいう", 6))
        assertEquals(3, CharWidth.charIndexForDisplayCol("あいう", 100))
    }

    @Test
    fun `サロゲートペアも1文字として扱う`() {
        val s = "a\uD842\uDFB7b" // 𠮷 (U+20BB7)
        assertEquals(1, CharWidth.displayCol(s, 1))
        assertEquals(3, CharWidth.displayCol(s, 3))
        assertEquals(4, CharWidth.displayWidth(s))
    }
}
