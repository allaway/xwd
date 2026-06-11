package com.allaway.xwd.puz

import com.allaway.xwd.model.Puzzle
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Entry point for parsing a downloaded puzzle file of either format. */
object PuzzleFormats {

    /**
     * Detects .ipuz (JSON text, possibly BOM-prefixed) vs .puz (binary) by
     * content rather than file name, since some feeds serve either through
     * opaque URLs. ZIP archives (some feeds wrap the puzzle file in one)
     * are unwrapped first.
     */
    fun parse(bytes: ByteArray): Puzzle = when {
        looksLikeZip(bytes) -> parse(unwrapZip(bytes))
        looksLikeJson(bytes) -> IpuzParser.parse(bytes.decodeToString().removePrefix("\uFEFF"))
        else -> PuzParser.parse(bytes)
    }

    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2].toInt() == 3 && bytes[3].toInt() == 4

    /** The first .puz or .ipuz entry of a ZIP archive. */
    private fun unwrapZip(bytes: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }
                .filter { !it.isDirectory }
                .forEach { entry ->
                    val name = entry.name.substringAfterLast('/').lowercase()
                    // "._foo.puz" entries are macOS AppleDouble metadata, not puzzles.
                    if (!name.startsWith(".") && (name.endsWith(".puz") || name.endsWith(".ipuz"))) {
                        return zip.readBytes()
                    }
                }
        }
        throw PuzFormatException("ZIP archive contains no .puz or .ipuz file")
    }

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
