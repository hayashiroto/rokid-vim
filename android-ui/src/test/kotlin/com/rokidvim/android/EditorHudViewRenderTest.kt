package com.rokidvim.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.rokidvim.core.EditorEngine
import com.rokidvim.core.EditorKey
import com.rokidvim.core.InMemoryFileStore
import com.rokidvim.core.NvimRc
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * EditorHudView を実際に描画して検証する（Robolectric ネイティブグラフィックス）。
 *
 * - 緑系ピクセルが描かれること（緑単色 HUD の前提）
 * - 480x640（公称の眼鏡解像度）でクラッシュせず描けること
 *
 * ついでに PNG を build/outputs/render-test/ に書き出し、目視確認に使う。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorHudViewRenderTest {

    private val width = 480
    private val height = 640

    private fun newView(): EditorHudView {
        val view = EditorHudView(RuntimeEnvironment.getApplication())
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return view
    }

    private fun drawToBitmap(view: EditorHudView): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    private fun savePng(bmp: Bitmap, name: String) {
        val dir = File("build/outputs/render-test")
        dir.mkdirs()
        FileOutputStream(File(dir, name)).use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun greenPixelCount(bmp: Bitmap): Int {
        var count = 0
        for (y in 0 until bmp.height step 4) {
            for (x in 0 until bmp.width step 4) {
                val p = bmp.getPixel(x, y)
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                if (g > 24 && g > r) count++
            }
        }
        return count
    }

    private fun sampleEngine(): EditorEngine {
        val engine = EditorEngine(fileStore = InMemoryFileStore())
        NvimRc.apply(engine, NvimRc.DEFAULT_CONTENT)
        engine.executeExCommand("set columns=40")
        engine.loadText(
            """
            |rokid-vim 動作サンプル
            |
            |The quick brown fox jumps
            |over the lazy dog.
            |日本語の全角文字も 2 桁で
            |そろえて表示できます。
            |	タブはタブストップまで
            """.trimMargin(),
            "sample.txt",
        )
        return engine
    }

    @Test
    fun normalModeRendersGreenGrid() {
        val engine = sampleEngine()
        engine.type("3j2l")
        val view = newView()
        view.render(engine.snapshot())
        val bmp = drawToBitmap(view)
        savePng(bmp, "01-normal.png")
        assertTrue("緑のピクセルが描かれていること", greenPixelCount(bmp) > 100)
    }

    @Test
    fun insertModeRendersModeLabel() {
        val engine = sampleEngine()
        engine.type("ji")
        val view = newView()
        view.render(engine.snapshot())
        val bmp = drawToBitmap(view)
        savePng(bmp, "02-insert.png")
        assertTrue(greenPixelCount(bmp) > 100)
    }

    @Test
    fun visualSelectionRenders() {
        val engine = sampleEngine()
        engine.type("2jv$")
        val view = newView()
        view.render(engine.snapshot())
        val bmp = drawToBitmap(view)
        savePng(bmp, "03-visual.png")
        assertTrue(greenPixelCount(bmp) > 100)
    }

    @Test
    fun commandLineRenders() {
        val engine = sampleEngine()
        engine.type(":w")
        val view = newView()
        view.render(engine.snapshot())
        val bmp = drawToBitmap(view)
        savePng(bmp, "04-command.png")
        assertTrue(greenPixelCount(bmp) > 100)
    }

    private fun EditorEngine.type(text: String) {
        text.forEach { handleKey(EditorKey.ofChar(it)) }
    }
}
