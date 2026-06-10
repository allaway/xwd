package com.allaway.xwd.puz

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class RealFileSmokeTest {
    @Test
    fun parsesRealDownloadedPuzzle() {
        // Drop any real .puz at this path to smoke-test the parser locally;
        // real puzzle files are never committed to the repository.
        val f = File("/tmp/smoke.puz")
        assumeTrue(f.exists())
        val p = PuzParser.parse(f.readBytes())
        println("TITLE=${p.title} AUTHOR=${p.author} ${p.width}x${p.height} clues=${p.clues.size} scrambled=${p.scrambled}")
        assertTrue(p.width >= 5 && p.height >= 5)
        assertTrue(p.clues.size > 20)
        assertTrue(p.clues.all { it.text.isNotBlank() && it.cells.isNotEmpty() })
    }
}
