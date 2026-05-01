package com.sasayaki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sasayaki.data.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("SELECT * FROM profiles ORDER BY isActive DESC, updatedAt DESC")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    fun observeActiveProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfile(id: Long): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun countProfiles(): Int

    @Query("SELECT id FROM profiles ORDER BY updatedAt DESC LIMIT 1")
    suspend fun firstProfileId(): Long?

    @Query("SELECT COUNT(*) FROM profiles WHERE isActive = 1")
    suspend fun activeCount(): Int

    @Query("UPDATE profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE profiles SET isActive = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markActive(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    suspend fun activate(id: Long) {
        deactivateAll()
        markActive(id)
    }

    @Query("DELETE FROM profiles WHERE id = :id AND (SELECT COUNT(*) FROM profiles) > 1")
    suspend fun deleteIfNotLast(id: Long)
}

