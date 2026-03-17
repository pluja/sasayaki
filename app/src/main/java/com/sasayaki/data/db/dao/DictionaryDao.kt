package com.sasayaki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Insert
    suspend fun insert(word: com.sasayaki.data.db.entity.DictionaryWord): Long

    @Update
    suspend fun update(word: com.sasayaki.data.db.entity.DictionaryWord)

    @Query("SELECT * FROM dictionary_words ORDER BY word ASC")
    fun getAll(): Flow<List<com.sasayaki.data.db.entity.DictionaryWord>>

    @Query("SELECT word FROM dictionary_words")
    suspend fun getAllWords(): List<String>

    @Query("DELETE FROM dictionary_words WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM dictionary_words WHERE word LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<com.sasayaki.data.db.entity.DictionaryWord>>
}
