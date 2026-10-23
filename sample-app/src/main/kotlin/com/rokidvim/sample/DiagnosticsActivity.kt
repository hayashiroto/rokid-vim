package com.rokidvim.sample

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView

/**
 * 実機検証用の KeyEvent 診断画面。
 *
 * 届いた KeyEvent を keyCode / metaState / unicodeChar / deviceId / source /
 * repeatCount / eventTime まで全て表示する。TE96 直結時の実際のコードや、
 * Ctrl chord の down 欠落（keyUp だけ届く）をここで確認する。
 *
 * 秘密情報・MAC アドレス・InputDevice の descriptor は表示も保存もしない。
 * ログは画面上のみでどこにも永続化しない。
 *
 * 終了: BACK（タッチパッド戻る）を 2 回連続。
 */
class DiagnosticsActivity : Activity() {

    companion object {
        private const val MAX_LINES = 300
        private const val DOUBLE_BACK_WINDOW_MS = 1200L
    }

    private lateinit var textView: TextView
    private lateinit var scrollView: ScrollView
    private val lines = ArrayDeque<String>()
    private var lastBackAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        textView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.rgb(0x40, 0xFF, 0x5E))
            setBackgroundColor(Color.BLACK)
            setPadding(8, 8, 8, 8)
        }
        scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(textView)
        }
        setContentView(scrollView)

        appendLine("== rokid-vim キー診断 ==")
        appendLine("終了: BACK を2回連続")
        appendLine("")
        dumpInputDevices()
        appendLine("")
        appendLine("-- KeyEvent ログ --")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        logEvent("key", event)
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            val now = SystemClock.uptimeMillis()
            if (now - lastBackAt < DOUBLE_BACK_WINDOW_MS) {
                finish()
                return true
            }
            lastBackAt = now
            appendLine("(BACK をもう一度押すと終了)")
        }
        return true // 全イベントを消費して観察する
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
        logEvent("SHORTCUT", event)
        return true
    }

    private fun dumpInputDevices() {
        appendLine("-- InputDevice 一覧 --")
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            val external = if (Build.VERSION.SDK_INT >= 29) dev.isExternal.toString() else "?"
            appendLine(
                "id=%d name=%s src=0x%08x kbdType=%d virtual=%b external=%s".format(
                    dev.id, dev.name, dev.sources, dev.keyboardType, dev.isVirtual, external,
                ),
            )
        }
    }

    private fun logEvent(tag: String, event: KeyEvent) {
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP  "
            else -> event.action.toString()
        }
        val uni = event.unicodeChar
        val uniStr = when {
            uni == 0 -> "-"
            Character.isISOControl(uni) -> "0x%02x".format(uni)
            else -> "'${String(Character.toChars(uni))}'(0x%04x)".format(uni)
        }
        appendLine(
            "%s %s %s(%d) scan=%d rep=%d meta=0x%08x uni=%s dev=%d src=0x%x t=%d dt=%d flg=0x%x".format(
                tag,
                action,
                KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_"),
                event.keyCode,
                event.scanCode,
                event.repeatCount,
                event.metaState,
                uniStr,
                event.deviceId,
                event.source,
                event.eventTime,
                event.downTime,
                event.flags,
            ),
        )
    }

    private fun appendLine(line: String) {
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        textView.text = lines.joinToString("\n")
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
