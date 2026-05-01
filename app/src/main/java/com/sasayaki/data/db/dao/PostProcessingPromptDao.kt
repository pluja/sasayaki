package com.sasayaki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sasayaki.data.db.entity.PostProcessingPromptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostProcessingPromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prompt: PostProcessingPromptEntity): Long

    @Update
    suspend fun update(prompt: PostProcessingPromptEntity)

    @Query("SELECT * FROM post_processing_prompts ORDER BY builtIn DESC, createdAt ASC")
    fun observeAll(): Flow<List<PostProcessingPromptEntity>>

    @Query("SELECT * FROM post_processing_prompts ORDER BY builtIn DESC, createdAt ASC")
    suspend fun getAll(): List<PostProcessingPromptEntity>

    @Query("SELECT * FROM post_processing_prompts WHERE id = :id")
    suspend fun getById(id: Long): PostProcessingPromptEntity?

    @Query("SELECT * FROM post_processing_prompts WHERE builtIn = 1 ORDER BY createdAt ASC")
    suspend fun getBuiltIns(): List<PostProcessingPromptEntity>

    @Query("SELECT COUNT(*) FROM post_processing_prompts WHERE builtIn = 1")
    suspend fun builtInCount(): Int

    @Query("DELETE FROM post_processing_prompts WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustomById(id: Long)

    @Query("DELETE FROM post_processing_prompts WHERE id = :id AND builtIn = 1")
    suspend fun deleteBuiltInById(id: Long)
}
