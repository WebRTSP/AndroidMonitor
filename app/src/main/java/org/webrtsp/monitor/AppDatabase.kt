package org.webrtsp.monitor

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow


@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val urn: String,
    val endpoint: String,
    val userName: String?,
    val password: String?,
    val name: String?,
)

@Dao
interface SourcesDao {
    @Query("SELECT * FROM sources")
    fun getAll(): List<SourceEntity>

    @Query("SELECT * FROM sources")
    fun all(): Flow<List<SourceEntity>>
}

@Database(entities = [SourceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourcesDao
}
