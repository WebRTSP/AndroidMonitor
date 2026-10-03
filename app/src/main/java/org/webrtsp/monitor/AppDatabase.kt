package org.webrtsp.monitor

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom

const val ACCESS_TOKEN_RAW_SIZE = 8

enum class SourceOrigin {
    WsDiscovery, // ONVIF cam
    User,
}

typealias SourceId = Long

private fun generateToken(): String {
    val tokenBytes = ByteArray(ACCESS_TOKEN_RAW_SIZE)
    SecureRandom().nextBytes(tokenBytes)
    return Base32Encode(tokenBytes)
}

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: SourceId?,
    val url: String,
    val origin: SourceOrigin,
    @ColumnInfo(name = "user_name")
    val userName: String?,
    val password: String?,
    val name: String?,
    val urn: String?,
    @ColumnInfo(name = "access_token")
    val accessToken: String,
)

data class SourceEntityUpdate(
    val id: SourceId,
    val url: String,
    @ColumnInfo(name = "user_name")
    val userName: String?,
    val password: String?,
    val name: String?,
)

val SourceEntity?.onvif get() = this?.origin == SourceOrigin.WsDiscovery
val SourceEntity?.maybeOnvif get() = this?.url?.startsWith("http://", true) ?: false

private fun Source.toSourceEntity(): SourceEntity {
    require(id == null)
    return SourceEntity(
        id,
        url.toString(),
        origin,
        userName,
        password,
        name,
        urn,
        generateToken()
    )
}

private fun Source.toSourceEntityUpdate(): SourceEntityUpdate {
    require(id != null)
    return SourceEntityUpdate(
        id,
        url.toString(),
        userName,
        password,
        name,
    )
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE tmp_sources
            AS
            SELECT *, hex(randomblob($ACCESS_TOKEN_RAW_SIZE)) as access_token
            FROM sources
            """.trimIndent())
        connection.execSQL("DELETE FROM sources")
        connection.execSQL("ALTER TABLE sources ADD COLUMN access_token TEXT NOT NULL")
        connection.execSQL("INSERT INTO sources SELECT * FROM tmp_sources")
        connection.execSQL("DROP TABLE tmp_sources")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "app.db"
        )
        .addMigrations(MIGRATION_1_2)
        .build()
    }

    @Provides
    fun provideSourcesDao(database: AppDatabase): SourcesDao {
        return database.sourceDao()
    }
}

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

    @Insert
    suspend fun insert(source: SourceEntity): Long
    suspend fun insert(source: Source): Long = insert(source.toSourceEntity())

    @Update(entity = SourceEntity::class)
    suspend fun update(source: SourceEntityUpdate)
    suspend fun update(source: Source) = update(source.toSourceEntityUpdate())

    @Query("UPDATE sources SET user_name = :userName, password = :password WHERE id = :id")
    suspend fun update(id: String, userName: String, password: String)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: SourceId)
}

@Database(
    entities = [SourceEntity::class],
    version = 2,
    autoMigrations = [],
    exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourcesDao
}
