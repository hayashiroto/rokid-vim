package com.rokidvim.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.rokidvim.core.CharWidth
import com.rokidvim.core.EditorMode
import com.rokidvim.core.EditorSnapshot
import com.rokidvim.core.Shade
import com.rokidvim.core.Viewport
import kotlin.math.abs
import kotlin.math.floor

/**
 * 等幅グリッドのエディタ描画ビュー。
 *
 * - 緑単色・黒透過前提の配色（[GreenPalette]）
 * - 全角 2 桁、タブはタブストップまで、のセル配置
 * - 反転ブロックカーソル（INSERT 中は縦バー）
 * - 行番号・ステータス行・コマンド行・ビューポートスクロール
 *
 * 状態は持たず、[render] に渡された [EditorSnapshot] を描くだけ。
 */
class EditorHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** options.columns が未指定のときに使う文字サイズ（px）。 */
    var defaultTextSizePx: Float = 22f
        set(value) {
            field = value
            invalidate()
        }

    private var snapshot: EditorSnapshot? = null
    private val viewport = Viewport()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 22f
    }
    private val fillPaint = Paint()

    fun render(s: EditorSnapshot) {
        snapshot = s
        invalidate()
    }

    fun resetViewport() {
        viewport.reset()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(GreenPalette.BLACK)
        val s = snapshot ?: return
        if (width <= 0 || height <= 0) return

        val scheme = s.colorScheme
        val tabstop = s.options.tabstop

        // --- セル寸法（columns 指定があればフィットさせる） ---
        textPaint.textSize = defaultTextSizePx
        var cellW = textPaint.measureText("M")
        val targetCols = s.options.columns
        if (targetCols != null && targetCols > 0) {
            val desired = width.toFloat() / targetCols
            if (abs(cellW - desired) > 0.01f) {
                textPaint.textSize = textPaint.textSize * desired / cellW
                cellW = textPaint.measureText("M")
            }
        }
        val fm = textPaint.fontMetrics
        val cellH = fm.descent - fm.ascent
        val baseline = -fm.ascent

        val totalCols = floor(width / cellW).toInt().coerceAtLeast(1)
        val totalRows = floor(height / cellH).toInt().coerceAtLeast(3)
        val textRows = totalRows - 2

        val gutterCols = if (s.options.number) digits(s.lines.size) + 1 else 0
        val textCols = (totalCols - gutterCols).coerceAtLeast(1)
        val gutterPx = gutterCols * cellW

        // --- ビューポート ---
        val cursorLineText = s.lines[s.cursor.line]
        val cursorDc = CharWidth.displayCol(cursorLineText, s.cursor.col, tabstop)
        val cursorW = if (s.cursor.col < cursorLineText.length) {
            CharWidth.displayWidthAt(cursorLineText.codePointAt(s.cursor.col), cursorDc, tabstop)
        } else {
            1
        }
        viewport.ensureVisible(
            cursorLine = s.cursor.line,
            cursorDisplayCol = cursorDc,
            cursorWidth = cursorW,
            rows = textRows,
            cols = textCols,
            scrolloff = s.options.scrolloff,
            totalLines = s.lines.size,
        )
        val top = viewport.topLine
        val left = viewport.leftCol

        val selection = s.selection

        // --- 本文 ---
        for (row in 0 until textRows) {
            val lineIdx = top + row
            val y = row * cellH
            if (lineIdx >= s.lines.size) {
                textPaint.color = GreenPalette.color(scheme.tilde)
                canvas.drawText("~", 0f, y + baseline, textPaint)
                continue
            }
            val line = s.lines[lineIdx]

            if (gutterCols > 0) {
                val num = (lineIdx + 1).toString()
                val shade = if (lineIdx == s.cursor.line) scheme.currentLineNumber else scheme.lineNumber
                textPaint.color = GreenPalette.color(shade)
                val x = (gutterCols - 1 - num.length) * cellW
                canvas.drawText(num, x.coerceAtLeast(0f), y + baseline, textPaint)
            }

            drawLine(
                canvas, s, line, lineIdx, y, baseline, cellH, cellW,
                gutterPx, left, textCols, tabstop, selection,
            )
        }

        // --- ステータス行 ---
        val statusY = textRows * cellH
        fillPaint.color = GreenPalette.color(Shade.DIM)
        canvas.drawRect(0f, statusY, width.toFloat(), statusY + 1.5f, fillPaint)
        textPaint.color = GreenPalette.color(scheme.statusText)
        val fileLabel = (s.fileName ?: "[No Name]") + if (s.modified) " [+]" else ""
        canvas.drawText(clip(fileLabel, totalCols - 12), 0f, statusY + baseline, textPaint)
        val posLabel = "${s.cursor.line + 1},${cursorDc + 1}"
        canvas.drawText(posLabel, width - (posLabel.length + 1) * cellW, statusY + baseline, textPaint)

        // --- コマンド行 / メッセージ行 ---
        val cmdY = (textRows + 1) * cellH
        val cmd = s.commandLine
        when {
            cmd != null -> {
                textPaint.color = GreenPalette.color(scheme.commandText)
                canvas.drawText(clip(cmd, totalCols - 2), 0f, cmdY + baseline, textPaint)
                // コマンドラインのキャレット
                val caretX = CharWidth.displayWidth(cmd, tabstop) * cellW
                fillPaint.color = GreenPalette.color(scheme.cursorBlock)
                canvas.drawRect(caretX, cmdY, caretX + cellW, cmdY + cellH, fillPaint)
            }
            s.message.isNotEmpty() -> {
                textPaint.color = GreenPalette.color(if (s.messageIsError) scheme.error else scheme.message)
                canvas.drawText(clip(s.message, totalCols), 0f, cmdY + baseline, textPaint)
            }
            s.modeLabel.isNotEmpty() -> {
                textPaint.color = GreenPalette.color(scheme.modeIndicator)
                canvas.drawText(s.modeLabel, 0f, cmdY + baseline, textPaint)
            }
        }
        // 入力途中のカウント・オペレータ（showcmd 相当）を右端に
        if (s.pending.isNotEmpty()) {
            textPaint.color = GreenPalette.color(scheme.statusText)
            canvas.drawText(s.pending, width - (s.pending.length + 1) * cellW, cmdY + baseline, textPaint)
        }
    }

    private fun drawLine(
        canvas: Canvas,
        s: EditorSnapshot,
        line: String,
        lineIdx: Int,
        y: Float,
        baseline: Float,
        cellH: Float,
        cellW: Float,
        gutterPx: Float,
        left: Int,
        textCols: Int,
        tabstop: Int,
        selection: Pair<com.rokidvim.core.Position, com.rokidvim.core.Position>?,
    ) {
        val scheme = s.colorScheme
        val isCursorLine = lineIdx == s.cursor.line
        var dc = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val w = CharWidth.displayWidthAt(cp, dc, tabstop)
            if (dc >= left + textCols) break
            if (dc + w > left) {
                val x = gutterPx + (dc - left) * cellW
                val cellPx = w * cellW
                val selected = selection != null && inSelection(selection, lineIdx, i)
                val isCursorCell = isCursorLine && i == s.cursor.col

                when {
                    isCursorCell && s.mode == EditorMode.INSERT -> {
                        fillPaint.color = GreenPalette.color(scheme.cursorBlock)
                        canvas.drawRect(x, y, x + 3f, y + cellH, fillPaint)
                        drawChar(canvas, cp, x, y + baseline, GreenPalette.color(scheme.text))
                    }
                    isCursorCell -> {
                        val shade = if (s.mode == EditorMode.COMMAND) Shade.MID else scheme.cursorBlock
                        fillPaint.color = GreenPalette.color(shade)
                        canvas.drawRect(x, y, x + cellPx, y + cellH, fillPaint)
                        drawChar(canvas, cp, x, y + baseline, GreenPalette.BLACK)
                    }
                    selected -> {
                        fillPaint.color = GreenPalette.color(scheme.visualBackground)
                        canvas.drawRect(x, y, x + cellPx, y + cellH, fillPaint)
                        drawChar(canvas, cp, x, y + baseline, GreenPalette.BLACK)
                    }
                    else -> {
                        drawChar(canvas, cp, x, y + baseline, GreenPalette.color(scheme.text))
                    }
                }
            }
            dc += w
            i += Character.charCount(cp)
        }

        // 行末カーソル（空行や $ の先、挿入モードの行末）
        if (isCursorLine && s.cursor.col >= line.length) {
            val x = gutterPx + (dc - left) * cellW
            if (dc >= left && dc < left + textCols) {
                fillPaint.color = GreenPalette.color(scheme.cursorBlock)
                if (s.mode == EditorMode.INSERT) {
                    canvas.drawRect(x, y, x + 3f, y + cellH, fillPaint)
                } else {
                    canvas.drawRect(x, y, x + cellW, y + cellH, fillPaint)
                }
            }
        }
    }

    private fun drawChar(canvas: Canvas, cp: Int, x: Float, baselineY: Float, color: Int) {
        if (cp == '\t'.code) return
        textPaint.color = color
        canvas.drawText(String(Character.toChars(cp)), x, baselineY, textPaint)
    }

    private fun inSelection(
        selection: Pair<com.rokidvim.core.Position, com.rokidvim.core.Position>,
        line: Int,
        col: Int,
    ): Boolean {
        val (start, end) = selection
        if (line < start.line || line > end.line) return false
        if (line == start.line && col < start.col) return false
        if (line == end.line && col > end.col) return false
        return true
    }

    private fun clip(text: String, maxCols: Int): String {
        if (maxCols <= 0) return ""
        var dc = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            dc += CharWidth.of(cp)
            if (dc > maxCols) return text.substring(0, i)
            i += Character.charCount(cp)
        }
        return text
    }

    private fun digits(n: Int): Int {
        var d = 1
        var v = n
        while (v >= 10) {
            v /= 10
            d++
        }
        return d
    }
}
