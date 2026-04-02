package com.sasayaki.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sasayaki.data.db.dao.DictationDao
import com.sasayaki.data.db.dao.DictionaryDao
import com.sasayaki.data.db.entity.Dictation
import com.sasayaki.data.db.entity.DictionaryWord

@Database(
    entities = [Dictation::class, DictionaryWord::class],
    version = 3,
    exportSchema = true
)
abstract class SasayakiDatabase : RoomDatabase() {
    abstract fun dictationDao(): DictationDao
    abstract fun dictionaryDao(): DictionaryDao
}
