package org.webrtsp.monitor

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

enum class SourceOrigin {
    WsDiscovery, // ONVIF cam
    User,
}

typealias SourceId = Long

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: SourceId?,
    val url: String,
    val origin: SourceOrigin,
    @ColumnInfo(name = "user_name") val userName: String?,
    val password: String?,
    val name: String?,
    val urn: String?,
)


@Dao
interface SourcesDao {
    companion object {
        const val UPDATED = -1L
    }

    @Query("SELECT * FROM sources")
    fun getAll(): List<SourceEntity>

    @Query("SELECT * FROM sources")
    fun all(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1")
    suspend fun findById(id: SourceId): SourceEntity?

    @Upsert
    suspend fun upsert(source: SourceEntity): Long

    @Query("UPDATE sources SET user_name = :userName, password = :password WHERE id = :id")
    suspend fun update(id: String, userName: String, password: String)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: SourceId)
}

@Database(entities = [SourceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourcesDao
}
