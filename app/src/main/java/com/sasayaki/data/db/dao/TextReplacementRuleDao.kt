package com.sasayaki.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sasayaki.data.db.entity.TextReplacementRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TextReplacementRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: TextReplacementRuleEntity): Long

    @Update
    suspend fun update(rule: TextReplacementRuleEntity)

    @Delete
    suspend fun delete(rule: TextReplacementRuleEntity)

    @Query("SELECT * FROM text_replacement_rules ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TextReplacementRuleEntity>>

    @Query("SELECT * FROM text_replacement_rules ORDER BY createdAt ASC")
    suspend fun getAll(): List<TextReplacementRuleEntity>

    @Query("SELECT * FROM text_replacement_rules WHERE id = :id")
    suspend fun getById(id: Long): TextReplacementRuleEntity?

    @Query("DELETE FROM text_replacement_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

