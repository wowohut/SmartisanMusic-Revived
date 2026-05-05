package com.smartisanos.music.data.library

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(
    tableName = "library_index",
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["stableKey"], unique = true),
        Index(value = ["valid"]),
    ],
)
internal data class LibraryIndexEntity(
    val mediaId: String,
    @PrimaryKey val stableKey: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val albumId: Long?,
    val durationMs: Long,
    val track: Int?,
    val year: Int?,
    val volumeName: String,
    val relativePath: String,
    val displayName: String,
    val mimeType: String?,
    val dateAdded: Long?,
    val dateModified: Long?,
    val generationAdded: Long?,
    val generationModified: Long?,
    val titleSortKey: String,
    val titleSection: String,
    val qualityBadge: String?,
    val indexedAt: Long,
    val valid: Boolean,
)

@Entity(tableName = "library_index_snapshot")
internal data class LibraryIndexSnapshotEntity(
    @PrimaryKey val id: Int = 0,
    val snapshotKey: String,
    val updatedAt: Long,
)

@Dao
internal interface LibraryIndexDao {

    @Query(
        """
        SELECT * FROM library_index
        WHERE valid = 1
        ORDER BY generationAdded DESC, dateAdded DESC, titleSortKey ASC
        """,
    )
    fun getValidIndexes(): List<LibraryIndexEntity>

    @Query("SELECT * FROM library_index WHERE valid = 1 AND mediaId IN (:mediaIds)")
    fun getValidIndexesByMediaIds(mediaIds: List<String>): List<LibraryIndexEntity>

    @Query("SELECT * FROM library_index WHERE valid = 1 AND stableKey IN (:stableKeys)")
    fun getValidIndexesByStableKeys(stableKeys: List<String>): List<LibraryIndexEntity>

    @Query("SELECT * FROM library_index")
    fun getAllIndexes(): List<LibraryIndexEntity>

    @Query("SELECT COUNT(*) FROM library_index WHERE valid = 1")
    fun getValidIndexCount(): Int

    @Query("SELECT snapshotKey FROM library_index_snapshot WHERE id = 0")
    fun getSnapshotKey(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertIndexes(indexes: List<LibraryIndexEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSnapshot(snapshot: LibraryIndexSnapshotEntity)

    @Query("UPDATE library_index SET valid = 0, indexedAt = :indexedAt WHERE stableKey IN (:stableKeys)")
    fun markInvalid(stableKeys: List<String>, indexedAt: Long)

    @Query("DELETE FROM library_index")
    fun deleteAllIndexes()
}

@Database(
    entities = [LibraryIndexEntity::class, LibraryIndexSnapshotEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class LibraryIndexDatabase : RoomDatabase() {
    abstract fun libraryIndexDao(): LibraryIndexDao

    companion object {
        @Volatile
        private var instance: LibraryIndexDatabase? = null

        fun getInstance(context: Context): LibraryIndexDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryIndexDatabase::class.java,
                    "library_index.db",
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
