package app.xwd.puz

import app.xwd.data.Backup
import app.xwd.data.CatalogEntity
import app.xwd.data.PuzzleEntity
import app.xwd.sources.CustomFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private fun puzzle(
        id: String = "jonesin-2026-06-11",
        progress: String = "----.----",
        elapsed: Long = 0,
        completedAt: Long? = null,
        addedAt: Long = 1_000,
    ) = PuzzleEntity(
        id = id,
        sourceId = "jonesin",
        sourceName = "Jonesin'",
        date = "2026-06-11",
        uniqueKey = "2026-06-11",
        title = "Themeless",
        author = "Matt Jones",
        puzzleJson = """{"width":3,"height":3}""",
        progress = progress,
        elapsedSeconds = elapsed,
        completedAt = completedAt,
        addedAt = addedAt,
    )

    @Test
    fun freshPhoneTakesTheBackupCopy() {
        val incoming = puzzle(progress = "ABC-.----", elapsed = 90)
        assertEquals(incoming, Backup.merge(local = null, incoming = incoming))
    }

    @Test
    fun moreFilledCopyWins() {
        val local = puzzle(progress = "AB--.----", elapsed = 400)
        val incoming = puzzle(progress = "ABCD.----", elapsed = 10)
        assertEquals("ABCD.----", Backup.merge(local, incoming).progress)
    }

    @Test
    fun completedCopyBeatsPartialOne() {
        val local = puzzle(progress = "ABCD.----", elapsed = 999)
        val incoming = puzzle(progress = "AB--.----", completedAt = 5_000)
        assertEquals(5_000L, Backup.merge(local, incoming).completedAt)
    }

    @Test
    fun tieKeepsWhatIsAlreadyOnThisPhone() {
        val local = puzzle(progress = "AB--.----", elapsed = 60, completedAt = null)
        val incoming = puzzle(progress = "CD--.----", elapsed = 60)
        assertEquals("AB--.----", Backup.merge(local, incoming).progress)
    }

    @Test
    fun elapsedTimeBreaksTiesOnEqualFill() {
        val local = puzzle(progress = "AB--.----", elapsed = 60)
        val incoming = puzzle(progress = "CD--.----", elapsed = 61)
        assertEquals("CD--.----", Backup.merge(local, incoming).progress)
    }

    @Test
    fun mergedRowKeepsTheEarlierAcquisitionDate() {
        val local = puzzle(progress = "ABCD.----", addedAt = 9_000)
        val incoming = puzzle(progress = "AB--.----", addedAt = 2_000)
        val merged = Backup.merge(local, incoming)
        assertEquals("ABCD.----", merged.progress) // local still wins the fill
        assertEquals(2_000L, merged.addedAt)
    }

    @Test
    fun puzzleRowSurvivesTheRoundTrip() {
        val row = puzzle(progress = "ABCD.EF--", elapsed = 321, completedAt = 12_345)
            .copy(revealCount = 2, checkCount = 3, firstFillCell = 0, lastFillCell = 8)
        assertEquals(row, Backup.decodePuzzle(Backup.encodePuzzle(row)))
    }

    @Test
    fun catalogRowSurvivesTheRoundTrip() {
        val row = CatalogEntity(
            id = "beq-1895",
            sourceId = "beq",
            uniqueKey = "1895",
            title = "Themeless Monday",
            date = null,
            url = "https://example.com/1895.puz",
            sortDate = "2026-06-10",
            discoveredAt = 7,
        )
        assertEquals(row, Backup.decodeCatalog(Backup.encodeCatalog(row)))
    }

    @Test
    fun encodedPuzzleIsOneLine() {
        assertTrue(Backup.encodePuzzle(puzzle()).none { it == '\n' })
    }

    @Test
    fun otherFilesAreRejectedBeforeAnythingIsWritten() {
        assertThrows(Backup.InvalidBackupException::class.java) { Backup.validate(null) }
        assertThrows(Backup.InvalidBackupException::class.java) {
            Backup.validate(Backup.Manifest(app = "something-else"))
        }
    }

    @Test
    fun backupsFromANewerAppAreRejected() {
        val error = assertThrows(Backup.InvalidBackupException::class.java) {
            Backup.validate(Backup.Manifest(formatVersion = Backup.FORMAT_VERSION + 1))
        }
        assertTrue(error.message!!.contains("newer version"))
    }

    @Test
    fun ourOwnManifestPasses() {
        Backup.validate(Backup.Manifest(puzzleCount = 12, catalogCount = 30))
    }

    private val feedA = CustomFeed("custom-a", "Rossword", "https://rosswords.example/puzzles")
    private val feedB = CustomFeed("custom-b", "Cryptics", "https://cryptics.example/downloads")

    @Test
    fun restoredSettingsWinButLocalFeedsAreKept() {
        val local = Backup.SettingsSnapshot(
            skinName = "TERMINAL",
            autocheckDefault = false,
            customFeeds = listOf(feedB),
            disabledSources = listOf("custom-b"),
        )
        val incoming = Backup.SettingsSnapshot(
            skinName = "MARGINS",
            autocheckDefault = true,
            autoDownloadProspective = true,
            customFeeds = listOf(feedA),
            disabledSources = listOf("club72"),
        )
        val merged = Backup.mergeSettings(local, incoming)
        assertEquals("MARGINS", merged.skinName)
        assertTrue(merged.autocheckDefault)
        assertTrue(merged.autoDownloadProspective)
        assertEquals(listOf(feedB, feedA), merged.customFeeds)
        // The backup's off-switches apply; the feed only this phone has keeps its own.
        assertEquals(listOf("club72", "custom-b"), merged.disabledSources)
    }

    @Test
    fun theSameFeedAddedOnBothPhonesIsNotDuplicated() {
        val sameUrlDifferentShape = feedA.copy(id = "custom-z", pageUrl = "HTTP://RossWords.example/puzzles/")
        val merged = Backup.mergeSettings(
            local = Backup.SettingsSnapshot(customFeeds = listOf(sameUrlDifferentShape)),
            incoming = Backup.SettingsSnapshot(customFeeds = listOf(feedA)),
        )
        assertEquals(listOf(sameUrlDifferentShape), merged.customFeeds)
    }

    @Test
    fun archivePositionKeepsWhicheverPhoneReadDeeper() {
        val merged = Backup.mergeSettings(
            local = Backup.SettingsSnapshot(catalogPageCursors = mapOf("beq" to 7, "club72" to 1)),
            incoming = Backup.SettingsSnapshot(catalogPageCursors = mapOf("beq" to 3, "stella" to 5)),
        )
        assertEquals(mapOf("beq" to 7, "club72" to 1, "stella" to 5), merged.catalogPageCursors)
    }

    @Test
    fun aBackupWithoutTheKeyLeavesThisPhonesKeyAlone() {
        val merged = Backup.mergeSettings(
            local = Backup.SettingsSnapshot(apiKey = "sk-local"),
            incoming = Backup.SettingsSnapshot(apiKey = null),
        )
        assertEquals("sk-local", merged.apiKey)
    }

    @Test
    fun aBackupCarryingTheKeyBringsItOver() {
        val merged = Backup.mergeSettings(
            local = Backup.SettingsSnapshot(apiKey = null),
            incoming = Backup.SettingsSnapshot(apiKey = "sk-moved"),
        )
        assertEquals("sk-moved", merged.apiKey)
    }

    @Test
    fun backupFileNameCarriesTheDate() {
        assertEquals("xwd-backup-2026-08-17.zip", Backup.fileName("2026-08-17"))
    }
}
