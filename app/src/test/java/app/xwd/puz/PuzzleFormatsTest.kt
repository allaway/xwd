package app.xwd.puz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PuzzleFormatsTest {

    private val minimalIpuz = """
        {
          "version": "http://ipuz.org/v2",
          "kind": ["http://ipuz.org/crossword#1"],
          "title": "Zipped",
          "dimensions": {"width": 2, "height": 1},
          "puzzle": [[1, 0]],
          "solution": [["H", "I"]],
          "clues": {"Across": [[1, "Greeting"]], "Down": []}
        }
    """.trimIndent()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun unwrapsZippedPuzzleFiles() {
        val zip = zipOf("Themed Wednesday #18.ipuz" to minimalIpuz.toByteArray())
        assertEquals("Zipped", PuzzleFormats.parse(zip).title)
    }

    @Test
    fun skipsAppleDoubleMetadataEntriesInZips() {
        val zip = zipOf(
            "__MACOSX/._puzzle.ipuz" to byteArrayOf(0, 5, 22, 7),
            "puzzle.ipuz" to minimalIpuz.toByteArray(),
        )
        assertEquals("Zipped", PuzzleFormats.parse(zip).title)
    }

    @Test
    fun rejectsZipsWithoutAPuzzleFile() {
        val zip = zipOf("solution.pdf" to "%PDF-1.4".toByteArray())
        assertThrows(PuzFormatException::class.java) { PuzzleFormats.parse(zip) }
    }

    @Test
    fun stillParsesBareIpuzAndDetectsByContent() {
        assertEquals("Zipped", PuzzleFormats.parse(minimalIpuz.toByteArray()).title)
    }
}
