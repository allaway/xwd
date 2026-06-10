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
    val addedAt: Long,
) {
    val filledCount: Int get() = progress.count { it != '.' && it != '-' }
    val whiteCount: Int get() = progress.count { it != '.' }
    val isCompleted: Boolean get() = completedAt != null
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

@Database(entities = [PuzzleEntity::class], version = 1, exportSchema = false)
abstract class XwdDatabase : RoomDatabase() {
    abstract fun puzzleDao(): PuzzleDao

    companion object {
        @Volatile
        private var instance: XwdDatabase? = null

        fun get(context: Context): XwdDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    XwdDatabase::class.java,
                    "xwd.db",
                ).build().also { instance = it }
            }
    }
}
