package com.rokidvim.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import com.rokidvim.android.EditorHudView
import com.rokidvim.android.InputPipeline
import com.rokidvim.core.EditorEngine
import com.rokidvim.core.NvimRc
import java.io.File

/**
 * エディタ本体の Activity。
 *
 * - BT キーボード（TE96 想定）の KeyEvent を dispatchKeyEvent /
 *   dispatchKeyShortcutEvent の両方で受けて [InputPipeline] に通す。
 * - .nvimrc を読み込み、:w / :e はアプリ内部ストレージに保存する。
 * - :q で終了、:diag で診断画面を開く。
 * - onPause で自動保存（<ファイル名>.autosave）。
 */
class EditorActivity : Activity(), EditorEngine.Listener {

    companion object {
        private const val DEFAULT_FILE = "notes.txt"
        private const val MAPPING_TIMEOUT_MS = 600L
        const val AUTOSAVE_SUFFIX = ".autosave"
    }

    private lateinit var engine: EditorEngine
    private lateinit var hudView: EditorHudView
    private lateinit var store: AndroidFileStore
    private val pipeline = InputPipeline(backAsEscape = true)
    private val handler = Handler(Looper.getMainLooper())
    private val flushMappings = Runnable {
        engine.flushPendingMappings()
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        store = AndroidFileStore(File(filesDir, "documents"))
        engine = EditorEngine(fileStore = store, listener = this)

        // .nvimrc（なければ既定を書き出す）
        val rcFile = File(filesDir, NvimRc.DEFAULT_FILE_NAME)
        if (!rcFile.exists()) {
            rcFile.writeText(NvimRc.DEFAULT_CONTENT, Charsets.UTF_8)
        }
        val rcResult = NvimRc.apply(engine, rcFile.readText(Charsets.UTF_8))

        engine.loadText(store.read(DEFAULT_FILE) ?: "", DEFAULT_FILE)
        if (!rcResult.ok) {
            val first = rcResult.errors.first()
            engine.showMessage(".nvimrc:${first.lineNumber}: ${first.message}", isError = true)
        }

        hudView = EditorHudView(this)
        setContentView(hudView)
        applyInputOptions()
        refresh()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val result = pipeline.onKeyEvent(event)
        result.key?.let { feedKey(it) }
        return result.consumed || super.dispatchKeyEvent(event)
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
        val result = pipeline.onKeyShortcutEvent(event)
        result.key?.let { feedKey(it) }
        return result.consumed || super.dispatchKeyShortcutEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            // フォーカスを失うと up を取りこぼすので修飾キー状態をリセット
            pipeline.reset()
        }
    }

    override fun onPause() {
        super.onPause()
        autosave()
    }

    // --- EditorEngine.Listener ---

    override fun onQuitRequested() {
        finish()
    }

    override fun onUnknownExCommand(command: String): Boolean {
        if (command.trim() == "diag") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
            return true
        }
        return false
    }

    // --- 内部 ---

    private fun feedKey(key: com.rokidvim.core.EditorKey) {
        handler.removeCallbacks(flushMappings)
        engine.handleKey(key)
        if (engine.hasPendingMapping) {
            handler.postDelayed(flushMappings, MAPPING_TIMEOUT_MS)
        }
        applyInputOptions()
        refresh()
    }

    private fun applyInputOptions() {
        val o = engine.options
        pipeline.repeat.delayMs = o.repeatDelayMs.toLong()
        pipeline.repeat.intervalMs = o.repeatIntervalMs.toLong()
    }

    private fun refresh() {
        hudView.render(engine.snapshot())
    }

    private fun autosave() {
        val name = engine.currentFileName ?: return
        if (!engine.isModified) return
        runCatching {
            store.write(name + AUTOSAVE_SUFFIX, engine.text())
        }
    }
}
