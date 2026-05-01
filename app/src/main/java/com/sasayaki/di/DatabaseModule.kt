package com.sasayaki.di

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sasayaki.data.db.SasayakiDatabase
import com.sasayaki.data.db.dao.DictationDao
import com.sasayaki.data.db.dao.PostProcessingPromptDao
import com.sasayaki.data.db.dao.ProfileDao
import com.sasayaki.data.db.dao.TextReplacementRuleDao
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

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `dictations` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'SUCCESS'")
            database.execSQL("ALTER TABLE `dictations` ADD COLUMN `errorMessage` TEXT")
            database.execSQL("ALTER TABLE `dictations` ADD COLUMN `profileId` INTEGER")
            database.execSQL("ALTER TABLE `dictations` ADD COLUMN `audioPath` TEXT")
            database.execSQL("DROP TABLE IF EXISTS `dictionary_words`")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `profiles` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `asrModel` TEXT NOT NULL,
                    `language` TEXT,
                    `llmEnabled` INTEGER NOT NULL,
                    `llmModel` TEXT NOT NULL,
                    `profilePrompt` TEXT NOT NULL,
                    `selectedRuleIds` TEXT NOT NULL,
                    `selectedPromptIds` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_profiles_isActive` ON `profiles` (`isActive`)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `text_replacement_rules` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `pattern` TEXT NOT NULL,
                    `replacement` TEXT NOT NULL,
                    `isRegex` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `post_processing_prompts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `prompt` TEXT NOT NULL,
                    `builtIn` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `dictations` ADD COLUMN `sourceAppPackage` TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SasayakiDatabase {
        return Room.databaseBuilder(
            context,
            SasayakiDatabase::class.java,
            "sasayaki.db"
        ).addMigrations(migration1To2, migration2To3, migration3To4, migration4To5)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideDictationDao(db: SasayakiDatabase): DictationDao = db.dictationDao()

    @Provides
    fun provideProfileDao(db: SasayakiDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideTextReplacementRuleDao(db: SasayakiDatabase): TextReplacementRuleDao = db.textReplacementRuleDao()

    @Provides
    fun providePostProcessingPromptDao(db: SasayakiDatabase): PostProcessingPromptDao = db.postProcessingPromptDao()
}
