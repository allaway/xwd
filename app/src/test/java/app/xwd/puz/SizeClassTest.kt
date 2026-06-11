package app.xwd.puz

import app.xwd.model.SizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class SizeClassTest {

    @Test
    fun classifiesCommonGridSizes() {
        assertEquals(SizeClass.MINI, SizeClass.forDimensions(5, 5)) // Crosshare daily mini
        assertEquals(SizeClass.MINI, SizeClass.forDimensions(8, 8))
        assertEquals(SizeClass.MIDI, SizeClass.forDimensions(11, 11))
        assertEquals(SizeClass.MIDI, SizeClass.forDimensions(13, 13))
        assertEquals(SizeClass.MAXI, SizeClass.forDimensions(15, 15)) // standard daily
        assertEquals(SizeClass.MAXI, SizeClass.forDimensions(17, 17))
        assertEquals(SizeClass.SUPERMAXI, SizeClass.forDimensions(21, 21)) // Sunday
        assertEquals(SizeClass.ULTRAMAXI, SizeClass.forDimensions(23, 23))
        assertEquals(SizeClass.ULTRAMAXI, SizeClass.forDimensions(31, 31))
    }

    @Test
    fun nonSquareGridsClassifyByArea() {
        assertEquals(SizeClass.MIDI, SizeClass.forDimensions(15, 8)) // 120 cells
        assertEquals(SizeClass.SUPERMAXI, SizeClass.forDimensions(25, 17)) // 425 cells
    }
}
