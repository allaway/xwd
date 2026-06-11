package com.allaway.xwd.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "puzzles")
data class PuzzleEntity(
    @PrimaryKey val id: String, // "<sourceId>-<isoDate>"
    val sourceId: String,
    val sourceName: String,
    val date: String, // ISO local date
    /** Stable per-source identity: the ISO date for dated feeds, file slug for scraped feeds. */
    val uniqueKey: String,
    val title: String,
    val author: String,
    /** Full Puzzle serialized as JSON. */
    val puzzleJson: String,
    /** One char per cell: '.' block, '-' empty, else the entered letter. */
    val progress: String,
    val elapsedSeconds: Long = 0,
    val completedAt: Long? = null,
    val autocheckUsed: Boolean = false,
    val revealCount: Int = 0,
    val checkCount: Int = 0,
    /** Row-major index of the first cell the solver typed into, once set. */
    val firstFillCell: Int? = null,
    /** Row-major index of the most recently typed cell (the last, once solved). */
    val lastFillCell: Int? = null,
    val addedAt: Long,
) {
    val filledCount: Int get() = progress.count { it != '.' && it != '-' }
    val whiteCount: Int get() = progress.count { it != '.' }
    val isCompleted: Boolean get() = completedAt != null
}

/**
 * A puzzle known to exist in a source's feed: the persistent record of
 * crosswords available for retrieval, surviving across launches so the
 * library feed is stable and the background refresher can add to it.
 */
@Entity(tableName = "catalog")
data class CatalogEntity(
    @PrimaryKey val id: String, // "<sourceId>-<uniqueKey>"
    val sourceId: String,
    val uniqueKey: String,
    val title: String,
    /** ISO publish date when the feed knows it (dated feeds only). */
    val date: String?,
    val url: String,
    /**
     * ISO date used to order the feed. The real publish date when known,
     * otherwise an approximation fixed at discovery time — never updated,
     * so cards keep their position once listed.
     */
    val sortDate: String,
    val discoveredAt: Long,
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog")
    fun observeAll(): Flow<List<CatalogEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<CatalogEntity>)

    @Query("SELECT id FROM catalog WHERE id IN (:ids)")
    suspend fun knownIds(ids: List<String>): List<String>

    @Query("SELECT MIN(sortDate) FROM catalog WHERE sourceId = :sourceId")
    suspend fun oldestSortDate(sourceId: String): String?
}

@Dao
interface PuzzleDao {
    @Query("SELECT * FROM puzzles ORDER BY date DESC, sourceName ASC")
    fun observeAll(): Flow<List<PuzzleEntity>>

    @Query("SELECT * FROM puzzles WHERE id = :id")
    suspend fun get(id: String): PuzzleEntity?

    @Query("SELECT uniqueKey FROM puzzles WHERE sourceId = :sourceId")
    suspend fun keysForSource(sourceId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PuzzleEntity): Long

    @Update
    suspend fun update(entity: PuzzleEntity)

    @Query("DELETE FROM puzzles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM puzzles WHERE completedAt IS NOT NULL")
    suspend fun completed(): List<PuzzleEntity>

    @Query("SELECT COUNT(*) FROM puzzles")
    suspend fun count(): Int
}

@Database(entities = [PuzzleEntity::class, CatalogEntity::class], version = 3, exportSchema = false)
abstract class XwdDatabase : RoomDatabase() {
    abstract fun puzzleDao(): PuzzleDao
    abstract fun catalogDao(): CatalogDao

    companion object {
        @Volatile
        private var instance: XwdDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE puzzles ADD COLUMN firstFillCell INTEGER")
                db.execSQL("ALTER TABLE puzzles ADD COLUMN lastFillCell INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catalog` (" +
                        "`id` TEXT NOT NULL, `sourceId` TEXT NOT NULL, " +
                        "`uniqueKey` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`date` TEXT, `url` TEXT NOT NULL, `sortDate` TEXT NOT NULL, " +
                        "`discoveredAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        fun get(context: Context): XwdDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    XwdDatabase::class.java,
                    "xwd.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
