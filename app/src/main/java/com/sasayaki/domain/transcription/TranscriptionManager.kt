package com.sasayaki.domain.transcription

import android.util.Log
import com.sasayaki.data.db.dao.DictationDao
import com.sasayaki.data.db.dao.DictionaryDao
import com.sasayaki.data.db.entity.Dictation
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.domain.processing.TextProcessor
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionManager @Inject constructor(
    private val whisperEngine: WhisperEngine,
    private val textProcessor: TextProcessor,
    private val dictationDao: DictationDao,
    private val dictionaryDao: DictionaryDao,
    private val preferencesDataStore: PreferencesDataStore,
    private val networkMonitor: NetworkMonitor
) {
    companion object {
        private const val TAG = "TranscriptionManager"
    }

    /**
     * Transcribe a pre-recorded audio file via the remote Whisper API.
     * On-device SpeechRecognizer cannot transcribe files — it must be used
     * as a live recording path instead (see BubbleService).
     */
    suspend fun transcribe(audioFile: File, durationMs: Long, sourceApp: String?): Result<String> {
        val dictionaryWords = dictionaryDao.getAllWords()

        val prefs = preferencesDataStore.preferences.first()
        if (prefs.asrBaseUrl.isBlank() || prefs.asrApiKey.isBlank()) {
            return Result.failure(Exception("ASR not configured. Go to Settings to set up your Whisper endpoint."))
        }

        if (!whisperEngine.isAvailable()) {
            return Result.failure(Exception("No network connection. Cannot reach ASR server."))
        }

        Log.d(TAG, "Sending ${audioFile.length()} bytes to Whisper API (model=${prefs.asrModel})")
        val rawResult = whisperEngine.transcribe(audioFile, dictionaryWords)

        val rawText = rawResult.getOrElse { error ->
            Log.e(TAG, "Whisper API error", error)
            return Result.failure(error)
        }

        Log.d(TAG, "Raw transcription: '$rawText'")
        if (rawText.isBlank()) return Result.success("")

        // LLM post-processing
        val processedText = textProcessor.process(rawText, dictionaryWords)
        val wordCount = processedText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size

        Log.d(TAG, "Processed text: '$processedText' ($wordCount words)")

        // Save to history
        dictationDao.insert(
            Dictation(
                text = processedText,
                rawText = rawText,
                wordCount = wordCount,
                sourceApp = sourceApp,
                durationMs = durationMs
            )
        )

        return Result.success(processedText)
    }
}
