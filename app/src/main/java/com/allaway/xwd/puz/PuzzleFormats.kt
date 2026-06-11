package com.allaway.xwd.puz

import com.allaway.xwd.model.Puzzle

/** Entry point for parsing a downloaded puzzle file of either format. */
object PuzzleFormats {

    /**
     * Detects .ipuz (JSON text, possibly BOM-prefixed) vs .puz (binary) by
     * content rather than file name, since some feeds serve either through
     * opaque URLs.
     */
    fun parse(bytes: ByteArray): Puzzle =
        if (looksLikeJson(bytes)) IpuzParser.parse(bytes.decodeToString().removePrefix("\uFEFF"))
        else PuzParser.parse(bytes)

    private fun looksLikeJson(bytes: ByteArray): Boolean {
        var i = 0
        // Skip a UTF-8 byte-order mark if present.
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            i = 3
        }
        while (i < bytes.size && bytes[i].toInt().toChar().isWhitespace()) i++
        return i < bytes.size && bytes[i] == '{'.code.toByte()
    }
}
