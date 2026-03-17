package com.sasayaki.di

import android.content.Context
import androidx.room.Room
import com.sasayaki.data.db.SasayakiDatabase
import com.sasayaki.data.db.dao.DictationDao
import com.sasayaki.data.db.dao.DictionaryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SasayakiDatabase {
        return Room.databaseBuilder(
            context,
            SasayakiDatabase::class.java,
            "sasayaki.db"
        ).build()
    }

    @Provides
    fun provideDictationDao(db: SasayakiDatabase): DictationDao = db.dictationDao()

    @Provides
    fun provideDictionaryDao(db: SasayakiDatabase): DictionaryDao = db.dictionaryDao()
}
