package com.vythera.stride.data.db

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
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Long,
    val distanceMeters: Double,
    val calories: Double,
    val goal: Int,
    val source: String
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAtEpochDay: Long
)

@Dao
interface SummaryDao {
    @Upsert
    suspend fun upsert(entity: DailySummaryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<DailySummaryEntity>)

    @Query("SELECT * FROM daily_summary WHERE epochDay = :epochDay")
    fun observeDay(epochDay: Long): Flow<DailySummaryEntity?>

    @Query("SELECT * FROM daily_summary WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    fun observeRange(from: Long, to: Long): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summary ORDER BY epochDay ASC")
    suspend fun getAll(): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_summary WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    suspend fun getRange(from: Long, to: Long): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_summary WHERE epochDay = :epochDay")
    suspend fun getDay(epochDay: Long): DailySummaryEntity?
}

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(entity: AchievementEntity)

    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun getAll(): List<AchievementEntity>
}

@Database(
    entities = [DailySummaryEntity::class, AchievementEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StrideDatabase : RoomDatabase() {
    abstract fun summaryDao(): SummaryDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        fun build(context: Context): StrideDatabase =
            Room.databaseBuilder(context, StrideDatabase::class.java, "stride.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
