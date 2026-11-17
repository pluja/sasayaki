package com.sasayaki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sasayaki.data.db.entity.ProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY createdAt ASC")
    suspend fun getAll(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getById(id: Long): ProviderEntity?

    @Query("SELECT COUNT(*) FROM providers")
    suspend fun count(): Int

    @Insert
    suspend fun insert(provider: ProviderEntity): Long

    @Update
    suspend fun update(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM providers")
    suspend fun deleteAll()

    @Query("UPDATE profiles SET speechProviderId = NULL, asrModel = '' WHERE speechProviderId = :id")
    suspend fun detachSpeechProfiles(id: Long)

    @Query("UPDATE profiles SET cleanupProviderId = NULL, llmModel = '' WHERE cleanupProviderId = :id")
    suspend fun detachCleanupProfiles(id: Long)

    /** delete detaches profiles first, so they fall back to another provider instead of pointing at a deleted one. */
    @Transaction
    suspend fun delete(id: Long) {
        detachSpeechProfiles(id)
        detachCleanupProfiles(id)
        deleteById(id)
    }
}
