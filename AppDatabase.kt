package com.screenwise.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_limits")
data class AppLimit(@PrimaryKey val packageName: String, val minutes: Int, val enabled: Boolean = true)

@Entity(tableName = "app_categories")
data class AppCategory(@PrimaryKey val packageName: String, val category: String)

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long, val endedAt: Long,
    val plannedMinutes: Int, val mode: String, val completed: Boolean
)

@Entity(tableName = "break_records")
data class BreakRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val completedAt: Long, val durationMinutes: Int
)

@Entity(tableName = "manual_calls")
data class ManualCall(
    @PrimaryKey val date: String, val minutes: Int
)

@Dao interface WellnessDao {
    @Query("SELECT * FROM app_limits ORDER BY packageName") fun limits(): Flow<List<AppLimit>>
    @Query("SELECT * FROM app_categories") fun categories(): Flow<List<AppCategory>>
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC") fun focus(): Flow<List<FocusSession>>
    @Query("SELECT * FROM break_records ORDER BY completedAt DESC") fun breaks(): Flow<List<BreakRecord>>
    @Query("SELECT * FROM manual_calls ORDER BY date DESC") fun calls(): Flow<List<ManualCall>>
    @Query("SELECT * FROM app_limits WHERE enabled = 1") suspend fun activeLimits(): List<AppLimit>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLimit(item: AppLimit)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCategory(item: AppCategory)
    @Insert suspend fun addFocus(item: FocusSession)
    @Insert suspend fun addBreak(item: BreakRecord)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setCall(item: ManualCall)
    @Query("DELETE FROM app_limits") suspend fun clearLimits()
    @Query("DELETE FROM app_categories") suspend fun clearCategories()
    @Query("DELETE FROM focus_sessions") suspend fun clearFocus()
    @Query("DELETE FROM break_records") suspend fun clearBreaks()
    @Query("DELETE FROM manual_calls") suspend fun clearCalls()
    @Query("DELETE FROM focus_sessions WHERE endedAt < :cutoff") suspend fun pruneFocus(cutoff: Long)
    @Query("DELETE FROM break_records WHERE completedAt < :cutoff") suspend fun pruneBreaks(cutoff: Long)
    @Query("DELETE FROM manual_calls WHERE date < :date") suspend fun pruneCalls(date: String)
}

@Database(entities = [AppLimit::class, AppCategory::class, FocusSession::class, BreakRecord::class, ManualCall::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): WellnessDao
    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext, AppDatabase::class.java, "screenwise-local.db"
        ).build()
    }
}
