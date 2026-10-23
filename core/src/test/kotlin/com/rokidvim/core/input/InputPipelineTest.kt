package com.rokidvim.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModifierTrackerTest {

    @Test
    fun `Ctrl の down up を追跡する`() {
        val t = ModifierTracker()
        assertFalse(t.ctrl)
        t.onModifier(ModifierKey.CTRL_LEFT, true)
        assertTrue(t.ctrl)
        t.onModifier(ModifierKey.CTRL_LEFT, false)
        assertFalse(t.ctrl)
    }

    @Test
    fun `左右の Ctrl を区別して合成する`() {
        val t = ModifierTracker()
        t.onModifier(ModifierKey.CTRL_LEFT, true)
        t.onModifier(ModifierKey.CTRL_RIGHT, true)
        t.onModifier(ModifierKey.CTRL_LEFT, false)
        assertTrue(t.ctrl)
        t.onModifier(ModifierKey.CTRL_RIGHT, false)
        assertFalse(t.ctrl)
    }

    @Test
    fun `reset で押しっぱなし扱いを解除する`() {
        val t = ModifierTracker()
        t.onModifier(ModifierKey.CTRL_LEFT, true)
        t.onModifier(ModifierKey.SHIFT_LEFT, true)
        t.reset()
        assertFalse(t.ctrl)
        assertFalse(t.shift)
    }
}

class KeyPressGateTest {

    @Test
    fun `同じ downTime の二重 down を弾く`() {
        val gate = KeyPressGate()
        assertTrue(gate.shouldProcessDown(42, downTime = 1000, eventTime = 1000))
        // dispatchKeyShortcutEvent 経由で同じ押下がもう一度届いた
        assertFalse(gate.shouldProcessDown(42, downTime = 1000, eventTime = 1001))
    }

    @Test
    fun `up の後は再び通す`() {
        val gate = KeyPressGate()
        assertTrue(gate.shouldProcessDown(42, 1000, 1000))
        gate.onKeyUp(42)
        assertTrue(gate.shouldProcessDown(42, 2000, 2000))
    }

    @Test
    fun `downTime が取れない場合は時間窓で弾く`() {
        val gate = KeyPressGate(dedupeWindowMs = 50)
        assertTrue(gate.shouldProcessDown(42, 0, 1000))
        assertFalse(gate.shouldProcessDown(42, 0, 1030))
        assertTrue(gate.shouldProcessDown(42, 0, 1100))
    }

    @Test
    fun `別のキーは弾かない`() {
        val gate = KeyPressGate()
        assertTrue(gate.shouldProcessDown(42, 1000, 1000))
        assertTrue(gate.shouldProcessDown(43, 1000, 1000))
    }

    @Test
    fun `sawDown で down の取りこぼしを検出できる`() {
        val gate = KeyPressGate()
        // Ctrl chord がシステムに先取りされ down が来ないケース:
        // up しか来ていないので sawDown は false → up から合成入力できる
        assertFalse(gate.sawDown(42))
        gate.shouldProcessDown(42, 1000, 1000)
        assertTrue(gate.sawDown(42))
    }
}

class KeyRepeatGovernorTest {

    @Test
    fun `初回押下は常に通す`() {
        val g = KeyRepeatGovernor(delayMs = 350, intervalMs = 70)
        assertTrue(g.allow(1, 0, 1000))
    }

    @Test
    fun `リピートは遅延まで捨てる`() {
        val g = KeyRepeatGovernor(delayMs = 350, intervalMs = 70)
        g.allow(1, 0, 1000)
        assertFalse(g.allow(1, 1, 1100))
        assertFalse(g.allow(1, 2, 1300))
        assertTrue(g.allow(1, 3, 1360))
    }

    @Test
    fun `リピートは間隔で間引かれる`() {
        val g = KeyRepeatGovernor(delayMs = 100, intervalMs = 70)
        g.allow(1, 0, 1000)
        assertTrue(g.allow(1, 1, 1150))
        assertFalse(g.allow(1, 2, 1180))
        assertTrue(g.allow(1, 3, 1230))
    }

    @Test
    fun `repeatEnabled=false なら全リピートを捨てる`() {
        val g = KeyRepeatGovernor(repeatEnabled = false)
        assertTrue(g.allow(1, 0, 1000))
        assertFalse(g.allow(1, 1, 5000))
        assertFalse(g.allow(1, 10, 9000))
    }

    @Test
    fun `別キーの初回はリピート状態に影響されない`() {
        val g = KeyRepeatGovernor(delayMs = 350, intervalMs = 70)
        g.allow(1, 0, 1000)
        assertTrue(g.allow(2, 0, 1010))
    }
}
