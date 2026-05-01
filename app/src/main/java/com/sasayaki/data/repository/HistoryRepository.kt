package com.sasayaki.data.repository

import android.content.Context
import com.sasayaki.data.db.dao.DictationDao
import com.sasayaki.data.db.entity.DictationSummary
import com.sasayaki.domain.transcription.TranscriptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dictationDao: DictationDao,
    private val transcriptionManager: TranscriptionManager
) {
    fun recent(limit: Int = 500): Flow<List<DictationSummary>> = dictationDao.getRecent(limit)

    suspend fun getRawText(id: Long): String? = dictationDao.getRawText(id)

    suspend fun removeFromHistory(id: Long) {
        dictationDao.getAudioPath(id)?.let(::deleteAudioPath)
        dictationDao.removeFromHistory(id)
    }

    suspend fun removeAllFromHistory() {
        dictationDao.getAllVisibleAudioPaths().forEach(::deleteAudioPath)
        dictationDao.removeAllFromHistory()
    }

    suspend fun retry(id: Long): Result<String> = transcriptionManager.retry(id)

    private fun deleteAudioPath(path: String) {
        runCatching {
            val file = File(path)
            if (file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                file.delete()
            }
        }
    }
}

