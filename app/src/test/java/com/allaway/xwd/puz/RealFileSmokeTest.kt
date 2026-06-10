package com.allaway.xwd.puz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class RealFileSmokeTest {
    @Test
    fun parsesRealWsjPuzzle() {
        val f = File("/tmp/wsj.puz")
        assumeTrue(f.exists())
        val p = PuzParser.parse(f.readBytes())
        println("TITLE=${p.title} AUTHOR=${p.author} ${p.width}x${p.height} clues=${p.clues.size} scrambled=${p.scrambled}")
        assertEquals(15, p.width)
        assertEquals(15, p.height)
        assertTrue(p.clues.size > 60)
        assertTrue(p.clues.all { it.text.isNotBlank() && it.cells.isNotEmpty() })
    }
}
