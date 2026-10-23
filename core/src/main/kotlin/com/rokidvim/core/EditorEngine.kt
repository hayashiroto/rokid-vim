package com.rokidvim.core

/** 無名レジスタの中身。 */
data class Register(val text: String, val linewise: Boolean)

/** undo/redo 用のスナップショット。 */
private data class UndoState(
    val lines: List<String>,
    val cursorLine: Int,
    val cursorCol: Int,
    val modified: Boolean,
)

/**
 * エディタ本体。モード遷移・バッファ・カーソル・レジスタ・undo を持つ純 Kotlin の状態機械。
 *
 * UI 層は [handleKey] に [EditorKey] を渡し、[snapshot] を描画する。
 * Android には依存しない。
 */
class EditorEngine(
    private val fileStore: FileStore? = null,
    private val listener: Listener? = null,
) {
    interface Listener {
        /** :q が受理されたとき。 */
        fun onQuitRequested() {}

        /** 未知の ex コマンド。処理したら true（例: サンプルアプリの :diag）。 */
        fun onUnknownExCommand(command: String): Boolean = false
    }

    companion object {
        private const val MAX_UNDO = 100
        private const val MAX_COUNT = 9999
    }

    // --- バッファ・カーソル ---
    private val lines = mutableListOf("")
    private var cursorLine = 0
    private var cursorCol = 0
    private var desiredDisplayCol = 0

    // --- モード・入力途中状態 ---
    private var mode = EditorMode.NORMAL
    private var count = 0
    private var pendingOperator: Char? = null
    private var visualAnchor: Position? = null

    // --- コマンドライン ---
    private var commandPrefix = ':'
    private val commandBuffer = StringBuilder()

    // --- レジスタ・undo ---
    private var register: Register? = null
    private val undoStack = ArrayDeque<UndoState>()
    private val redoStack = ArrayDeque<UndoState>()
    private var insertUndoPushed = false

    // --- 検索 ---
    private var lastSearchPattern: String? = null

    // --- ファイル・オプション ---
    private var fileName: String? = null
    private var modified = false
    var options = EditorOptions()
        private set
    var colorScheme: ColorScheme = ColorSchemes.ROKID_GREEN
        private set

    // --- メッセージ ---
    private var message = ""
    private var messageIsError = false

    // --- マッピング ---
    private val mappings = MappingResolver()

    // ======================================================================
    // 公開 API
    // ======================================================================

    /** バッファ全文（UTF-8 保存用）。 */
    fun text(): String = lines.joinToString("\n")

    /** テキストを読み込んでバッファを置き換える（undo 履歴はリセット）。 */
    fun loadText(text: String, name: String? = null) {
        lines.clear()
        lines.addAll(text.split("\n"))
        if (lines.isEmpty()) lines.add("")
        fileName = name
        modified = false
        cursorLine = 0
        cursorCol = 0
        desiredDisplayCol = 0
        undoStack.clear()
        redoStack.clear()
        mode = EditorMode.NORMAL
        visualAnchor = null
    }

    val currentFileName: String? get() = fileName
    val isModified: Boolean get() = modified
    val currentMode: EditorMode get() = mode

    /** マッピング解決待ちのキーがあるか（UI がタイムアウトを仕掛ける）。 */
    val hasPendingMapping: Boolean get() = mappings.hasPending

    /** マッピング待ちをタイムアウト確定する（UI 層が遅延実行で呼ぶ）。 */
    fun flushPendingMappings() {
        mappings.flushPending().forEach { processKey(it) }
    }

    /** キー入力の入り口。マッピングを解決してから各モードの処理へ。 */
    fun handleKey(key: EditorKey) {
        if (mode == EditorMode.COMMAND) {
            // コマンドラインにはマッピングを適用しない
            processKey(key)
            return
        }
        mappings.offer(mode, key).forEach { processKey(it) }
    }

    /** ex コマンドを実行する（.nvimrc の行もこれで処理）。エラー文字列か null を返す。 */
    fun executeExCommand(command: String): String? = executeEx(command)

    /** ホストアプリがメッセージ行に表示したいとき（.nvimrc のエラー通知など）。 */
    fun showMessage(msg: String, isError: Boolean = false) {
        message = msg
        messageIsError = isError
    }

    /** 描画用スナップショット。 */
    fun snapshot(): EditorSnapshot = EditorSnapshot(
        lines = lines.toList(),
        cursor = Position(cursorLine, cursorCol),
        mode = mode,
        pending = buildString {
            if (count > 0) append(count)
            pendingOperator?.let { append(it) }
        },
        commandLine = if (mode == EditorMode.COMMAND) "$commandPrefix$commandBuffer" else null,
        message = message,
        messageIsError = messageIsError,
        visualAnchor = if (mode == EditorMode.VISUAL) visualAnchor else null,
        fileName = fileName,
        modified = modified,
        options = options,
        colorScheme = colorScheme,
    )

    // ======================================================================
    // キー処理（モード分岐）
    // ======================================================================

    private fun processKey(key: EditorKey) {
        clearMessage()
        when (mode) {
            EditorMode.NORMAL -> handleNormal(key)
            EditorMode.INSERT -> handleInsert(key)
            EditorMode.VISUAL -> handleVisual(key)
            EditorMode.COMMAND -> handleCommand(key)
        }
    }

    // --- NORMAL ---

    private fun handleNormal(key: EditorKey) {
        if (key.special == SpecialKey.ESCAPE) {
            count = 0
            pendingOperator = null
            return
        }

        val op = pendingOperator
        if (op != null) {
            pendingOperator = null
            val n = countOrDefault()
            count = 0
            when {
                op == 'd' && key.isChar('d') -> deleteLines(n)
                op == 'y' && key.isChar('y') -> yankLines(n)
                op == 'g' && key.isChar('g') -> gotoLine(if (n == 1 && !lastCountExplicit) 1 else n)
                else -> Unit // オペレータ中断
            }
            lastCountExplicit = false
            return
        }

        val c = key.char
        if (key.ctrl) {
            when (c) {
                'r' -> redo(countOrDefaultAndClear())
            }
            return
        }

        if (c != null && c.isDigit() && !(c == '0' && count == 0)) {
            count = (count * 10 + (c - '0')).coerceAtMost(MAX_COUNT)
            lastCountExplicit = true
            return
        }

        if (applyMotion(key)) return

        when (c) {
            'i' -> enterInsert()
            'a' -> {
                val line = lines[cursorLine]
                cursorCol = if (line.isEmpty()) 0 else nextCp(line, cursorCol).coerceAtMost(line.length)
                enterInsert()
            }
            'o' -> {
                pushUndo()
                lines.add(cursorLine + 1, "")
                cursorLine++
                cursorCol = 0
                modified = true
                enterInsert(undoAlreadyPushed = true)
            }
            'O' -> {
                pushUndo()
                lines.add(cursorLine, "")
                cursorCol = 0
                modified = true
                enterInsert(undoAlreadyPushed = true)
            }
            'x' -> deleteChars(countOrDefaultAndClear())
            'd' -> pendingOperator = 'd'
            'y' -> pendingOperator = 'y'
            'p' -> paste(countOrDefaultAndClear())
            'u' -> undo(countOrDefaultAndClear())
            'v' -> {
                visualAnchor = Position(cursorLine, cursorCol)
                mode = EditorMode.VISUAL
                count = 0
            }
            ':' -> enterCommand(':')
            '/' -> enterCommand('/')
            'n' -> repeatSearch(forward = true)
            'N' -> repeatSearch(forward = false)
            else -> {
                count = 0
                lastCountExplicit = false
            }
        }
    }

    /** gg/G でカウント有無を区別するためのフラグ。 */
    private var lastCountExplicit = false

    // --- INSERT ---

    private fun handleInsert(key: EditorKey) {
        when {
            key.special == SpecialKey.ESCAPE -> leaveInsert()
            key.special == SpecialKey.ENTER -> {
                ensureInsertUndo()
                val line = lines[cursorLine]
                lines[cursorLine] = line.substring(0, cursorCol)
                lines.add(cursorLine + 1, line.substring(cursorCol))
                cursorLine++
                cursorCol = 0
                modified = true
            }
            key.special == SpecialKey.BACKSPACE -> {
                if (cursorCol > 0) {
                    ensureInsertUndo()
                    val line = lines[cursorLine]
                    val prev = prevCp(line, cursorCol)
                    lines[cursorLine] = line.removeRange(prev, cursorCol)
                    cursorCol = prev
                    modified = true
                } else if (cursorLine > 0) {
                    ensureInsertUndo()
                    val prevLen = lines[cursorLine - 1].length
                    lines[cursorLine - 1] = lines[cursorLine - 1] + lines[cursorLine]
                    lines.removeAt(cursorLine)
                    cursorLine--
                    cursorCol = prevLen
                    modified = true
                }
            }
            key.special == SpecialKey.DELETE -> {
                val line = lines[cursorLine]
                if (cursorCol < line.length) {
                    ensureInsertUndo()
                    lines[cursorLine] = line.removeRange(cursorCol, nextCp(line, cursorCol))
                    modified = true
                } else if (cursorLine < lines.size - 1) {
                    ensureInsertUndo()
                    lines[cursorLine] = line + lines[cursorLine + 1]
                    lines.removeAt(cursorLine + 1)
                    modified = true
                }
            }
            key.special == SpecialKey.TAB -> {
                ensureInsertUndo()
                val line = lines[cursorLine]
                val dcol = CharWidth.displayCol(line, cursorCol, options.tabstop)
                val spaces = options.tabstop - (dcol % options.tabstop)
                lines[cursorLine] = line.substring(0, cursorCol) + " ".repeat(spaces) + line.substring(cursorCol)
                cursorCol += spaces
                modified = true
            }
            key.special == SpecialKey.LEFT -> moveHorizontal(-1)
            key.special == SpecialKey.RIGHT -> moveHorizontal(1)
            key.special == SpecialKey.UP -> moveVertical(-1)
            key.special == SpecialKey.DOWN -> moveVertical(1)
            key.special == SpecialKey.HOME -> { cursorCol = 0; updateDesired() }
            key.special == SpecialKey.END -> { cursorCol = lines[cursorLine].length; updateDesired() }
            key.isPrintable -> {
                ensureInsertUndo()
                val c = key.char!!
                val line = lines[cursorLine]
                lines[cursorLine] = line.substring(0, cursorCol) + c + line.substring(cursorCol)
                cursorCol++
                modified = true
            }
        }
        if (key.special != SpecialKey.UP && key.special != SpecialKey.DOWN) {
            updateDesired()
        }
    }

    private fun enterInsert(undoAlreadyPushed: Boolean = false) {
        mode = EditorMode.INSERT
        insertUndoPushed = undoAlreadyPushed
        count = 0
        lastCountExplicit = false
    }

    private fun leaveInsert() {
        mode = EditorMode.NORMAL
        val line = lines[cursorLine]
        cursorCol = if (cursorCol > 0) prevCp(line, cursorCol) else 0
        cursorCol = cursorCol.coerceAtMost(maxColNormal(line))
        updateDesired()
    }

    private fun ensureInsertUndo() {
        if (!insertUndoPushed) {
            pushUndo()
            insertUndoPushed = true
        }
    }

    // --- VISUAL ---

    private fun handleVisual(key: EditorKey) {
        if (key.special == SpecialKey.ESCAPE) {
            exitVisual()
            return
        }
        val c = key.char
        if (key.ctrl) return

        val op = pendingOperator
        if (op != null) {
            pendingOperator = null
            val n = countOrDefault()
            count = 0
            if (op == 'g' && key.isChar('g')) {
                gotoLine(if (n == 1 && !lastCountExplicit) 1 else n)
            }
            lastCountExplicit = false
            return
        }

        if (c != null && c.isDigit() && !(c == '0' && count == 0)) {
            count = (count * 10 + (c - '0')).coerceAtMost(MAX_COUNT)
            lastCountExplicit = true
            return
        }

        if (applyMotion(key)) return

        when (c) {
            'v' -> exitVisual()
            'd', 'x' -> deleteSelection()
            'y' -> yankSelection()
            'n' -> repeatSearch(forward = true)
            'N' -> repeatSearch(forward = false)
            else -> {
                count = 0
                lastCountExplicit = false
            }
        }
    }

    private fun exitVisual() {
        mode = EditorMode.NORMAL
        visualAnchor = null
        count = 0
        pendingOperator = null
    }

    private fun selectionRange(): Pair<Position, Position> {
        val a = visualAnchor ?: Position(cursorLine, cursorCol)
        val b = Position(cursorLine, cursorCol)
        return if (a.line < b.line || (a.line == b.line && a.col <= b.col)) a to b else b to a
    }

    private fun extractSelection(): String {
        val (start, end) = selectionRange()
        val endLine = lines[end.line]
        val endEx = if (endLine.isEmpty()) 0 else nextCp(endLine, end.col).coerceAtMost(endLine.length)
        return if (start.line == end.line) {
            lines[start.line].substring(start.col, endEx)
        } else {
            buildString {
                append(lines[start.line].substring(start.col))
                for (l in start.line + 1 until end.line) {
                    append('\n').append(lines[l])
                }
                append('\n').append(endLine.substring(0, endEx))
            }
        }
    }

    private fun deleteSelection() {
        val (start, end) = selectionRange()
        register = Register(extractSelection(), linewise = false)
        pushUndo()
        val endLine = lines[end.line]
        val endEx = if (endLine.isEmpty()) 0 else nextCp(endLine, end.col).coerceAtMost(endLine.length)
        if (start.line == end.line) {
            lines[start.line] = lines[start.line].removeRange(start.col, endEx)
        } else {
            val merged = lines[start.line].substring(0, start.col) + endLine.substring(endEx)
            lines[start.line] = merged
            for (l in end.line downTo start.line + 1) lines.removeAt(l)
        }
        if (lines.isEmpty()) lines.add("")
        cursorLine = start.line.coerceAtMost(lines.size - 1)
        cursorCol = start.col.coerceAtMost(maxColNormal(lines[cursorLine]))
        updateDesired()
        modified = true
        exitVisual()
    }

    private fun yankSelection() {
        val (start, _) = selectionRange()
        register = Register(extractSelection(), linewise = false)
        cursorLine = start.line
        cursorCol = start.col.coerceAtMost(maxColNormal(lines[cursorLine]))
        updateDesired()
        exitVisual()
    }

    // --- COMMAND ---

    private fun enterCommand(prefix: Char) {
        mode = EditorMode.COMMAND
        commandPrefix = prefix
        commandBuffer.clear()
        count = 0
        lastCountExplicit = false
    }

    private fun handleCommand(key: EditorKey) {
        when {
            key.special == SpecialKey.ESCAPE -> {
                commandBuffer.clear()
                mode = EditorMode.NORMAL
            }
            key.special == SpecialKey.BACKSPACE -> {
                if (commandBuffer.isEmpty()) {
                    mode = EditorMode.NORMAL
                } else {
                    val s = commandBuffer.toString()
                    commandBuffer.setLength(prevCp(s, s.length))
                }
            }
            key.special == SpecialKey.ENTER -> {
                val cmd = commandBuffer.toString()
                commandBuffer.clear()
                mode = EditorMode.NORMAL
                when (commandPrefix) {
                    ':' -> executeEx(cmd)
                    '/' -> executeSearch(cmd)
                }
            }
            key.isPrintable -> commandBuffer.append(key.char)
        }
    }

    // ======================================================================
    // モーション
    // ======================================================================

    /** モーションキーなら適用して true。NORMAL / VISUAL 共通。 */
    private fun applyMotion(key: EditorKey): Boolean {
        val n = countOrDefault()
        val c = key.char
        val handled = when {
            c == 'h' || key.special == SpecialKey.LEFT -> { moveHorizontal(-n); true }
            c == 'l' || key.special == SpecialKey.RIGHT -> { moveHorizontal(n); true }
            c == 'j' || key.special == SpecialKey.DOWN -> { moveVertical(n); true }
            c == 'k' || key.special == SpecialKey.UP -> { moveVertical(-n); true }
            c == 'w' -> { repeat(n) { motionWordForward() }; updateDesired(); true }
            c == 'b' -> { repeat(n) { motionWordBack() }; updateDesired(); true }
            c == 'e' -> { repeat(n) { motionWordEnd() }; updateDesired(); true }
            c == '0' || key.special == SpecialKey.HOME -> { cursorCol = 0; updateDesired(); true }
            c == '$' || key.special == SpecialKey.END -> {
                cursorCol = maxColNormal(lines[cursorLine]); updateDesired(); true
            }
            c == 'G' -> {
                gotoLine(if (lastCountExplicit) n else lines.size)
                true
            }
            c == 'g' -> { pendingOperator = 'g'; return true }
            else -> false
        }
        if (handled) {
            count = 0
            lastCountExplicit = false
        }
        return handled
    }

    private fun moveHorizontal(delta: Int) {
        val line = lines[cursorLine]
        var col = cursorCol
        if (delta > 0) {
            repeat(delta) {
                if (line.isEmpty()) return@repeat
                val next = nextCp(line, col)
                val limit = if (mode == EditorMode.INSERT) line.length else maxColNormal(line)
                if (next <= limit) col = next else if (mode == EditorMode.INSERT && next <= line.length) col = next
            }
        } else {
            repeat(-delta) {
                if (col > 0) col = prevCp(line, col)
            }
        }
        cursorCol = col
        updateDesired()
    }

    private fun moveVertical(delta: Int) {
        cursorLine = (cursorLine + delta).coerceIn(0, lines.size - 1)
        val line = lines[cursorLine]
        cursorCol = CharWidth.charIndexForDisplayCol(line, desiredDisplayCol, options.tabstop)
        val limit = if (mode == EditorMode.INSERT) line.length else maxColNormal(line)
        cursorCol = cursorCol.coerceAtMost(limit)
    }

    private fun gotoLine(oneBased: Int) {
        cursorLine = (oneBased - 1).coerceIn(0, lines.size - 1)
        cursorCol = firstNonBlank(lines[cursorLine])
        updateDesired()
    }

    private fun firstNonBlank(line: String): Int {
        var i = 0
        while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
        return i.coerceAtMost(maxColNormal(line))
    }

    // --- 単語モーション ---

    /** 文字クラス: 0=空白 1=半角単語 2=記号 3=ひらがな 4=カタカナ 5=漢字 6=その他全角 */
    private fun charClass(cp: Int): Int = when {
        cp == ' '.code || cp == '\t'.code -> 0
        cp == '_'.code || Character.isLetterOrDigit(cp) && cp < 0x3000 -> 1
        cp in 0x3040..0x309F -> 3
        cp in 0x30A0..0x30FF || cp in 0x31F0..0x31FF -> 4
        cp in 0x3400..0x9FFF || cp in 0xF900..0xFAFF || cp in 0x20000..0x3FFFD -> 5
        CharWidth.isWide(cp) -> 6
        else -> 2
    }

    private fun motionWordForward() {
        var l = cursorLine
        var c = cursorCol
        val cur = lines[l]
        if (c < cur.length) {
            val cls = charClass(cur.codePointAt(c))
            if (cls != 0) {
                while (c < lines[l].length && charClass(lines[l].codePointAt(c)) == cls) {
                    c = nextCp(lines[l], c)
                }
            }
        }
        // 空白をスキップ（行をまたぐ。空行は停止点）
        while (true) {
            val line = lines[l]
            while (c < line.length && charClass(line.codePointAt(c)) == 0) c = nextCp(line, c)
            if (c < line.length) break
            if (l == lines.size - 1) {
                c = maxColNormal(line)
                break
            }
            l++
            c = 0
            if (lines[l].isEmpty()) break
        }
        cursorLine = l
        cursorCol = c.coerceAtMost(maxColNormal(lines[l]))
    }

    /** 1 つ前の文字位置。バッファ先頭なら null。 */
    private fun backPos(l: Int, c: Int): Position? {
        if (c > 0) return Position(l, prevCp(lines[l], c))
        if (l == 0) return null
        val prev = lines[l - 1]
        return Position(l - 1, if (prev.isEmpty()) 0 else maxColNormal(prev))
    }

    /** 1 つ次の文字位置。バッファ末尾なら null。 */
    private fun fwdPos(l: Int, c: Int): Position? {
        val line = lines[l]
        val next = if (line.isEmpty()) line.length else nextCp(line, c)
        if (next < line.length) return Position(l, next)
        return if (l < lines.size - 1) Position(l + 1, 0) else null
    }

    private fun isBlankAt(p: Position): Boolean {
        val line = lines[p.line]
        if (line.isEmpty()) return false // 空行は「空白」ではなく停止点として扱う
        return charClass(line.codePointAt(p.col)) == 0
    }

    private fun motionWordBack() {
        var p = backPos(cursorLine, cursorCol) ?: return
        while (isBlankAt(p)) {
            p = backPos(p.line, p.col) ?: run {
                cursorLine = 0; cursorCol = 0; return
            }
        }
        if (lines[p.line].isEmpty()) {
            cursorLine = p.line
            cursorCol = 0
            return
        }
        val cls = charClass(lines[p.line].codePointAt(p.col))
        while (true) {
            val q = backPos(p.line, p.col) ?: break
            if (q.line != p.line) break
            if (lines[q.line].isEmpty() || charClass(lines[q.line].codePointAt(q.col)) != cls) break
            p = q
        }
        cursorLine = p.line
        cursorCol = p.col
    }

    private fun motionWordEnd() {
        var p = fwdPos(cursorLine, cursorCol) ?: return
        // 空白と空行をスキップ
        while (lines[p.line].isEmpty() || isBlankAt(p)) {
            p = fwdPos(p.line, p.col) ?: return
        }
        val cls = charClass(lines[p.line].codePointAt(p.col))
        while (true) {
            val q = fwdPos(p.line, p.col) ?: break
            if (q.line != p.line) break
            if (charClass(lines[q.line].codePointAt(q.col)) != cls) break
            p = q
        }
        cursorLine = p.line
        cursorCol = p.col
    }

    // ======================================================================
    // 編集オペレーション
    // ======================================================================

    private fun deleteChars(n: Int) {
        val line = lines[cursorLine]
        if (line.isEmpty()) return
        pushUndo()
        var end = cursorCol
        repeat(n) {
            if (end < line.length) end = nextCp(line, end)
        }
        register = Register(line.substring(cursorCol, end), linewise = false)
        val newLine = line.removeRange(cursorCol, end)
        lines[cursorLine] = newLine
        cursorCol = cursorCol.coerceAtMost(maxColNormal(newLine))
        updateDesired()
        modified = true
    }

    private fun deleteLines(n: Int) {
        pushUndo()
        val end = (cursorLine + n - 1).coerceAtMost(lines.size - 1)
        register = Register(
            (cursorLine..end).joinToString("\n") { lines[it] },
            linewise = true,
        )
        for (l in end downTo cursorLine) lines.removeAt(l)
        if (lines.isEmpty()) lines.add("")
        cursorLine = cursorLine.coerceAtMost(lines.size - 1)
        cursorCol = firstNonBlank(lines[cursorLine])
        updateDesired()
        modified = true
    }

    private fun yankLines(n: Int) {
        val end = (cursorLine + n - 1).coerceAtMost(lines.size - 1)
        register = Register(
            (cursorLine..end).joinToString("\n") { lines[it] },
            linewise = true,
        )
        if (end - cursorLine + 1 >= 3) {
            setMessage("${end - cursorLine + 1} lines yanked")
        }
    }

    private fun paste(times: Int) {
        val reg = register ?: return
        pushUndo()
        if (reg.linewise) {
            val content = mutableListOf<String>()
            repeat(times) { content.addAll(reg.text.split("\n")) }
            lines.addAll(cursorLine + 1, content)
            cursorLine++
            cursorCol = firstNonBlank(lines[cursorLine])
        } else {
            val text = reg.text.repeat(times)
            val line = lines[cursorLine]
            val insertAt = if (line.isEmpty()) 0 else nextCp(line, cursorCol).coerceAtMost(line.length)
            val parts = text.split("\n")
            if (parts.size == 1) {
                lines[cursorLine] = line.substring(0, insertAt) + text + line.substring(insertAt)
                cursorCol = if (text.isEmpty()) cursorCol else insertAt + text.length - lastCpLen(text)
            } else {
                val tail = line.substring(insertAt)
                lines[cursorLine] = line.substring(0, insertAt) + parts[0]
                for (i in 1 until parts.size) {
                    lines.add(cursorLine + i, parts[i])
                }
                val lastIdx = cursorLine + parts.size - 1
                lines[lastIdx] = lines[lastIdx] + tail
                cursorLine = lastIdx
                val lastPart = parts.last()
                cursorCol = if (lastPart.isEmpty()) 0 else lastPart.length - lastCpLen(lastPart)
                cursorCol = cursorCol.coerceAtMost(maxColNormal(lines[cursorLine]))
            }
        }
        updateDesired()
        modified = true
    }

    // ======================================================================
    // undo / redo
    // ======================================================================

    private fun captureState(): UndoState =
        UndoState(lines.toList(), cursorLine, cursorCol, modified)

    private fun restoreState(s: UndoState) {
        lines.clear()
        lines.addAll(s.lines)
        if (lines.isEmpty()) lines.add("")
        cursorLine = s.cursorLine.coerceIn(0, lines.size - 1)
        cursorCol = s.cursorCol.coerceAtMost(maxColNormal(lines[cursorLine]))
        modified = s.modified
        updateDesired()
    }

    private fun pushUndo() {
        undoStack.addLast(captureState())
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun undo(n: Int) {
        repeat(n) {
            val s = undoStack.removeLastOrNull()
            if (s == null) {
                setMessage("Already at oldest change")
                return
            }
            redoStack.addLast(captureState())
            restoreState(s)
        }
    }

    private fun redo(n: Int) {
        repeat(n) {
            val s = redoStack.removeLastOrNull()
            if (s == null) {
                setMessage("Already at newest change")
                return
            }
            undoStack.addLast(captureState())
            restoreState(s)
        }
    }

    // ======================================================================
    // 検索
    // ======================================================================

    private fun executeSearch(pattern: String) {
        val pat = if (pattern.isEmpty()) {
            lastSearchPattern ?: run {
                setError("E35: No previous search pattern")
                return
            }
        } else {
            pattern
        }
        lastSearchPattern = pat
        searchMove(pat, forward = true)
    }

    private fun repeatSearch(forward: Boolean) {
        val pat = lastSearchPattern ?: run {
            setError("E35: No previous search pattern")
            return
        }
        searchMove(pat, forward)
    }

    private fun searchMove(pat: String, forward: Boolean) {
        val found = if (forward) findForward(pat) else findBackward(pat)
        if (found == null) {
            setError("E486: Pattern not found: $pat")
            return
        }
        val (pos, wrapped) = found
        cursorLine = pos.line
        cursorCol = pos.col.coerceAtMost(maxColNormal(lines[pos.line]))
        updateDesired()
        if (wrapped) {
            setMessage(
                if (forward) "search hit BOTTOM, continuing at TOP"
                else "search hit TOP, continuing at BOTTOM",
            )
        }
    }

    private fun findForward(pat: String): Pair<Position, Boolean>? {
        val startCol = cursorCol + 1
        val idx = lines[cursorLine].indexOf(pat, startCol)
        if (idx >= 0) return Position(cursorLine, idx) to false
        for (l in cursorLine + 1 until lines.size) {
            val i = lines[l].indexOf(pat)
            if (i >= 0) return Position(l, i) to false
        }
        for (l in 0..cursorLine) {
            val i = lines[l].indexOf(pat)
            if (i >= 0) return Position(l, i) to true
        }
        return null
    }

    private fun findBackward(pat: String): Pair<Position, Boolean>? {
        if (cursorCol > 0) {
            val idx = lines[cursorLine].lastIndexOf(pat, cursorCol - 1)
            if (idx >= 0) return Position(cursorLine, idx) to false
        }
        for (l in cursorLine - 1 downTo 0) {
            val i = lines[l].lastIndexOf(pat)
            if (i >= 0) return Position(l, i) to false
        }
        for (l in lines.size - 1 downTo cursorLine) {
            val i = lines[l].lastIndexOf(pat)
            if (i >= 0) return Position(l, i) to true
        }
        return null
    }

    // ======================================================================
    // ex コマンド
    // ======================================================================

    private fun executeEx(raw: String): String? {
        val command = raw.trim()
        if (command.isEmpty()) return null
        val name = command.takeWhile { !it.isWhitespace() }
        val arg = command.drop(name.length).trim()
        return when (name) {
            "w", "write" -> exWrite(arg.ifEmpty { null })
            "q", "quit" -> exQuit(force = false)
            "q!", "quit!" -> exQuit(force = true)
            "wq" -> {
                val err = exWrite(arg.ifEmpty { null })
                if (err == null) exQuit(force = false) else err
            }
            "e", "edit" -> exEdit(arg, force = false)
            "e!", "edit!" -> exEdit(arg, force = true)
            "set", "se" -> exSet(arg)
            "colorscheme", "colo" -> exColorScheme(arg)
            "map" -> exMap(arg, listOf(EditorMode.NORMAL, EditorMode.VISUAL), noremap = false)
            "nnoremap" -> exMap(arg, listOf(EditorMode.NORMAL), noremap = true)
            "inoremap" -> exMap(arg, listOf(EditorMode.INSERT), noremap = true)
            else -> {
                if (listener?.onUnknownExCommand(command) == true) {
                    null
                } else {
                    setError("E492: Not an editor command: $name")
                }
            }
        }
    }

    private fun exWrite(nameArg: String?): String? {
        val target = nameArg ?: fileName ?: return setError("E32: No file name")
        val store = fileStore ?: return setError("E212: Can't open file for writing: $target")
        return try {
            store.write(target, text())
            if (fileName == null || target == fileName) {
                fileName = target
                modified = false
            }
            setMessage("\"$target\" ${lines.size}L written")
            null
        } catch (e: Exception) {
            setError("E212: Can't open file for writing: $target")
        }
    }

    private fun exQuit(force: Boolean): String? {
        if (modified && !force) {
            return setError("E37: No write since last change (add ! to override)")
        }
        listener?.onQuitRequested()
        return null
    }

    private fun exEdit(nameArg: String, force: Boolean): String? {
        if (nameArg.isEmpty()) return setError("E32: No file name")
        if (modified && !force) {
            return setError("E37: No write since last change (add ! to override)")
        }
        val store = fileStore ?: return setError("E484: Can't open file $nameArg")
        val content = store.read(nameArg)
        loadText(content ?: "", nameArg)
        setMessage(
            if (content == null) "\"$nameArg\" [New File]"
            else "\"$nameArg\" ${lines.size}L",
        )
        return null
    }

    private fun exSet(arg: String): String? {
        if (arg.isEmpty()) return null
        var firstError: String? = null
        for (token in arg.split(Regex("\\s+"))) {
            val err = setOne(token)
            if (err != null && firstError == null) firstError = err
        }
        return firstError
    }

    private fun setOne(token: String): String? {
        val eq = token.indexOf('=')
        if (eq >= 0) {
            val optName = token.substring(0, eq)
            val valueStr = token.substring(eq + 1)
            val value = valueStr.toIntOrNull()
                ?: return setError("E521: Number required after =: $token")
            options = when (optName) {
                "tabstop", "ts" -> options.copy(tabstop = value.coerceIn(1, 16))
                "scrolloff", "so" -> options.copy(scrolloff = value.coerceIn(0, 20))
                "columns", "cols" -> options.copy(columns = value.coerceIn(20, 100))
                "repeatdelay" -> options.copy(repeatDelayMs = value.coerceIn(50, 2000))
                "repeatinterval" -> options.copy(repeatIntervalMs = value.coerceIn(10, 1000))
                else -> return setError("E518: Unknown option: $token")
            }
            return null
        }
        options = when (token) {
            "number", "nu" -> options.copy(number = true)
            "nonumber", "nonu" -> options.copy(number = false)
            else -> return setError("E518: Unknown option: $token")
        }
        return null
    }

    private fun exColorScheme(arg: String): String? {
        if (arg.isEmpty()) {
            setMessage(colorScheme.name)
            return null
        }
        val scheme = ColorSchemes.byName(arg)
            ?: return setError("E185: Cannot find color scheme '$arg'")
        colorScheme = scheme
        return null
    }

    private fun exMap(arg: String, modes: List<EditorMode>, noremap: Boolean): String? {
        val sp = arg.indexOfFirst { it.isWhitespace() }
        if (sp <= 0) return setError("E474: Invalid argument: $arg")
        val lhsStr = arg.substring(0, sp)
        val rhsStr = arg.substring(sp).trim()
        val lhs = KeyNotation.parse(lhsStr) ?: return setError("E474: Invalid argument: $lhsStr")
        val rhs = KeyNotation.parse(rhsStr) ?: return setError("E474: Invalid argument: $rhsStr")
        if (lhs.isEmpty() || rhs.isEmpty()) return setError("E474: Invalid argument: $arg")
        for (m in modes) {
            mappings.addMapping(m, Mapping(lhs, rhs, noremap))
        }
        return null
    }

    // ======================================================================
    // ヘルパ
    // ======================================================================

    private fun EditorKey.isChar(c: Char): Boolean = char == c && !ctrl && !alt

    private fun countOrDefault(): Int = if (count > 0) count else 1

    private fun countOrDefaultAndClear(): Int {
        val n = countOrDefault()
        count = 0
        lastCountExplicit = false
        return n
    }

    private fun updateDesired() {
        desiredDisplayCol = CharWidth.displayCol(lines[cursorLine], cursorCol, options.tabstop)
    }

    private fun clearMessage() {
        message = ""
        messageIsError = false
    }

    private fun setMessage(msg: String): String? {
        message = msg
        messageIsError = false
        return null
    }

    private fun setError(msg: String): String {
        message = msg
        messageIsError = true
        return msg
    }

    private fun nextCp(s: String, i: Int): Int =
        if (i >= s.length) s.length else i + Character.charCount(s.codePointAt(i))

    private fun prevCp(s: String, i: Int): Int =
        if (i <= 0) 0 else s.offsetByCodePoints(i, -1)

    private fun lastCpLen(s: String): Int =
        if (s.isEmpty()) 0 else s.length - s.offsetByCodePoints(s.length, -1)

    private fun maxColNormal(line: String): Int =
        if (line.isEmpty()) 0 else prevCp(line, line.length)
}
