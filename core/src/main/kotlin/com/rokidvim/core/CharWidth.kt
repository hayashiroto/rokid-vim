package com.rokidvim.core

/**
 * 表示幅（東アジア幅）の計算。
 *
 * 全角（East Asian Wide / Fullwidth）の文字は 2 桁、それ以外は 1 桁として扱う。
 * 結合文字は 0 桁。タブは桁位置依存なので [displayWidthAt] を使う。
 */
object CharWidth {

    /** コードポイント単体の表示幅（タブを除く）。 */
    fun of(codePoint: Int): Int {
        if (codePoint == 0) return 0
        // 結合文字（主要ブロックのみ）
        if (codePoint in 0x0300..0x036F) return 0
        if (codePoint in 0x20D0..0x20FF) return 0
        if (codePoint in 0xFE00..0xFE0F) return 0
        if (codePoint in 0x3099..0x309A) return 0
        return if (isWide(codePoint)) 2 else 1
    }

    /** East Asian Wide / Fullwidth 判定（主要ブロックのハードコード）。 */
    fun isWide(codePoint: Int): Boolean = when {
        codePoint < 0x1100 -> false
        codePoint in 0x1100..0x115F -> true   // Hangul Jamo
        codePoint in 0x2E80..0x303E -> true   // CJK Radicals .. CJK Symbols（〿を除く）
        codePoint in 0x3041..0x33FF -> true   // ひらがな・カタカナ・CJK互換
        codePoint in 0x3400..0x4DBF -> true   // CJK 拡張A
        codePoint in 0x4E00..0x9FFF -> true   // CJK 統合漢字
        codePoint in 0xA000..0xA4CF -> true   // Yi
        codePoint in 0xAC00..0xD7A3 -> true   // ハングル音節
        codePoint in 0xF900..0xFAFF -> true   // CJK 互換漢字
        codePoint in 0xFE30..0xFE4F -> true   // CJK 互換形
        codePoint in 0xFF00..0xFF60 -> true   // 全角英数・記号
        codePoint in 0xFFE0..0xFFE6 -> true   // 全角記号
        codePoint in 0x1F300..0x1FAFF -> true // 絵文字
        codePoint in 0x20000..0x3FFFD -> true // CJK 拡張B以降
        else -> false
    }

    /** 桁位置 [col] に置かれたコードポイントの表示幅（タブはタブストップまで伸びる）。 */
    fun displayWidthAt(codePoint: Int, col: Int, tabstop: Int): Int {
        if (codePoint == '\t'.code) return tabstop - (col % tabstop)
        return of(codePoint)
    }

    /** 行 [line] の文字インデックス [charIndex] 直前までの表示桁数。 */
    fun displayCol(line: String, charIndex: Int, tabstop: Int = 4): Int {
        var col = 0
        var i = 0
        while (i < charIndex && i < line.length) {
            val cp = line.codePointAt(i)
            col += displayWidthAt(cp, col, tabstop)
            i += Character.charCount(cp)
        }
        return col
    }

    /** 行全体の表示幅。 */
    fun displayWidth(line: String, tabstop: Int = 4): Int = displayCol(line, line.length, tabstop)

    /**
     * 表示桁 [targetCol] 以下で最も近い文字インデックスを返す（縦移動の桁合わせに使う）。
     * 返る値は常にコードポイント境界。
     */
    fun charIndexForDisplayCol(line: String, targetCol: Int, tabstop: Int = 4): Int {
        var col = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val w = displayWidthAt(cp, col, tabstop)
            if (col + w > targetCol) return i
            col += w
            i += Character.charCount(cp)
        }
        return line.length
    }
}
