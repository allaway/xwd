package com.allaway.xwd.puz

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class RealFileSmokeTest {
    @Test
    fun parsesRealDownloadedPuzzles() {
        // Drop real .puz files at /tmp/smoke*.puz to smoke-test the parser
        // locally; real puzzle files are never committed to the repository.
        val files = File("/tmp").listFiles { f -> f.name.startsWith("smoke") && f.name.endsWith(".puz") }
            ?.sorted().orEmpty()
        assumeTrue(files.isNotEmpty())
        for (f in files) {
            val p = PuzParser.parse(f.readBytes())
            println("${f.name}: TITLE=${p.title} AUTHOR=${p.author} ${p.width}x${p.height} clues=${p.clues.size} scrambled=${p.scrambled}")
            assertTrue("${f.name} grid too small", p.width >= 4 && p.height >= 4)
            assertTrue("${f.name} too few clues", p.clues.size >= 4)
            assertTrue(
                "${f.name} has malformed clues",
                p.clues.all { it.text.isNotBlank() && it.cells.isNotEmpty() },
            )
        }
    }
}
