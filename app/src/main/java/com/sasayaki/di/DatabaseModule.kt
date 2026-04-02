package com.sasayaki.di

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
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
    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_dictations_timestamp` ON `dictations` (`timestamp`)"
            )
        }
    }

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE `dictations` ADD COLUMN `historyVisible` INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SasayakiDatabase {
        return Room.databaseBuilder(
            context,
            SasayakiDatabase::class.java,
            "sasayaki.db"
        ).addMigrations(migration1To2, migration2To3)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideDictationDao(db: SasayakiDatabase): DictationDao = db.dictationDao()

    @Provides
    fun provideDictionaryDao(db: SasayakiDatabase): DictionaryDao = db.dictionaryDao()
}
