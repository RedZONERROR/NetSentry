package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val uid: Int,
    val packageName: String,
    val appName: String,
    val ruleState: String, // ALLOWED, WHITELISTED, BLOCKED
    val bytesCap: Long, // 0 means unlimited
    val bytesUsed: Long = 0L,
    val isThrottled: Boolean = false,
    val isPinned: Boolean = false
)

@Entity(tableName = "traffic_logs")
data class TrafficLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val uid: Int,
    val packageName: String,
    val appName: String,
    val domain: String,
    val ip: String,
    val protocol: String,
    val bytesSent: Long,
    val bytesReceived: Long,
    val allowed: Boolean
)

@Dao
interface NetSentryDao {
    @Query("SELECT * FROM app_rules ORDER BY appName ASC")
    fun getAllRulesFlow(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules")
    suspend fun getAllRules(): List<AppRule>

    @Query("SELECT * FROM app_rules WHERE uid = :uid")
    suspend fun getRuleByUid(uid: Int): AppRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AppRule)

    @Update
    suspend fun updateRule(rule: AppRule)

    @Query("UPDATE app_rules SET bytesUsed = :bytesUsed, isThrottled = :isThrottled WHERE uid = :uid")
    suspend fun updateUsage(uid: Int, bytesUsed: Long, isThrottled: Boolean)

    @Query("SELECT * FROM traffic_logs ORDER BY timestamp DESC LIMIT 400")
    fun getRecentTrafficLogsFlow(): Flow<List<TrafficLog>>

    @Query("SELECT * FROM traffic_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTrafficLogs(limit: Int): List<TrafficLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrafficLog(log: TrafficLog)

    @Query("DELETE FROM traffic_logs")
    suspend fun clearLogs()
}

@Database(entities = [AppRule::class, TrafficLog::class], version = 2, exportSchema = false)
abstract class NetSentryDatabase : RoomDatabase() {
    abstract fun netSentryDao(): NetSentryDao

    companion object {
        @Volatile
        private var INSTANCE: NetSentryDatabase? = null

        fun getDatabase(context: android.content.Context): NetSentryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NetSentryDatabase::class.java,
                    "netsentry_db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
