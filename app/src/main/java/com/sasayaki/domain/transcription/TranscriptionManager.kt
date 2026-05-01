package com.sasayaki.domain.transcription

import android.content.Context
import android.util.Log
import com.sasayaki.data.db.dao.DictationDao
import com.sasayaki.data.db.entity.Dictation
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.repository.ProfileRepository
import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.DictationStatus
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.processing.TextProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisperEngine: WhisperEngine,
    private val textProcessor: TextProcessor,
    private val dictationDao: DictationDao,
    private val preferencesDataStore: PreferencesDataStore,
    private val profileRepository: ProfileRepository
) {
    companion object {
        private const val TAG = "TranscriptionManager"
        private const val RETAINED_AUDIO_DIR = "retained_audio"
    }

    suspend fun transcribe(audioFile: File, durationMs: Long, appContext: AppContext?): Result<String> {
        val profile = profileRepository.getActiveProfile()
        return transcribeWithProfile(audioFile, durationMs, appContext, profile, retryEntryId = null)
    }

    suspend fun retry(dictationId: Long): Result<String> {
        val entry = dictationDao.getById(dictationId)
            ?: return Result.failure(IllegalArgumentException("History entry not found."))
        val audioPath = entry.audioPath
            ?: return Result.failure(IllegalStateException("No saved audio is available for retry."))
        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            return Result.failure(IllegalStateException("Saved audio file is missing."))
        }

        val profile = entry.profileId?.let { profileRepository.getProfile(it) }
            ?: profileRepository.getActiveProfile()
        return transcribeWithProfile(audioFile, entry.durationMs, entry.toAppContext(), profile, retryEntryId = entry.id)
    }

    suspend fun latestRetriableFailureId(): Long? = dictationDao.getLatestRetriableFailureId()

    private suspend fun transcribeWithProfile(
        audioFile: File,
        durationMs: Long,
        appContext: AppContext?,
        profile: Profile,
        retryEntryId: Long?
    ): Result<String> {
        val prefs = preferencesDataStore.preferences.first()
        if (prefs.asrBaseUrl.isBlank() || prefs.asrApiKey.isBlank()) {
            val error = Exception("ASR not configured. Go to Settings to set up your Whisper endpoint.")
            persistFailure(audioFile, durationMs, appContext, profile, prefs.historyEnabled, error, retryEntryId)
            return Result.failure(error)
        }

        if (!whisperEngine.isAvailable()) {
            val error = Exception("No network connection. Cannot reach ASR server.")
            persistFailure(audioFile, durationMs, appContext, profile, prefs.historyEnabled, error, retryEntryId)
            return Result.failure(error)
        }

        Log.d(TAG, "Sending ${audioFile.length()} bytes to Whisper API (model=${profile.asrModel})")
        val rawResult = whisperEngine.transcribe(audioFile, profile.asrModel, profile.language)
        val rawText = rawResult.getOrElse { error ->
            Log.e(TAG, "Whisper API error", error)
            persistFailure(audioFile, durationMs, appContext, profile, prefs.historyEnabled, error, retryEntryId)
            return Result.failure(error)
        }

        if (rawText.isBlank()) {
            persistSuccess("", "", durationMs, appContext, profile, prefs.historyEnabled, prefs.keepStatsWithoutHistory, audioFile, retryEntryId)
            return Result.success("")
        }

        val processedText = textProcessor.process(rawText, profile, appContext)
        persistSuccess(
            text = processedText,
            rawText = rawText,
            durationMs = durationMs,
            appContext = appContext,
            profile = profile,
            historyEnabled = prefs.historyEnabled,
            keepStatsWithoutHistory = prefs.keepStatsWithoutHistory,
            audioFile = audioFile,
            retryEntryId = retryEntryId
        )
        return Result.success(processedText)
    }

    private suspend fun persistSuccess(
        text: String,
        rawText: String,
        durationMs: Long,
        appContext: AppContext?,
        profile: Profile,
        historyEnabled: Boolean,
        keepStatsWithoutHistory: Boolean,
        audioFile: File,
        retryEntryId: Long?
    ) {
        if (!historyEnabled && !keepStatsWithoutHistory && retryEntryId == null) return
        val wordCount = text.split("\\s+".toRegex()).count { it.isNotBlank() }
        val savedAudioPath = if (historyEnabled) retainAudio(audioFile, retryEntryId) else null
        upsertHistory(
            retryEntryId = retryEntryId,
            text = if (historyEnabled) text else "",
            rawText = if (historyEnabled) rawText else "",
            wordCount = wordCount,
            durationMs = durationMs,
            appContext = if (historyEnabled) appContext else null,
            profile = profile,
            status = DictationStatus.SUCCESS,
            errorMessage = null,
            audioPath = savedAudioPath,
            historyVisible = historyEnabled
        )
        pruneHistory()
    }

    private suspend fun persistFailure(
        audioFile: File,
        durationMs: Long,
        appContext: AppContext?,
        profile: Profile,
        historyEnabled: Boolean,
        error: Throwable,
        retryEntryId: Long?
    ) {
        if (!historyEnabled && retryEntryId == null) return
        val savedAudioPath = if (historyEnabled) retainAudio(audioFile, retryEntryId) else null
        upsertHistory(
            retryEntryId = retryEntryId,
            text = "",
            rawText = "",
            wordCount = 0,
            durationMs = durationMs,
            appContext = appContext,
            profile = profile,
            status = DictationStatus.FAILURE,
            errorMessage = error.message ?: "Unknown transcription error",
            audioPath = savedAudioPath,
            historyVisible = historyEnabled
        )
        pruneHistory()
    }

    private suspend fun upsertHistory(
        retryEntryId: Long?,
        text: String,
        rawText: String,
        wordCount: Int,
        durationMs: Long,
        appContext: AppContext?,
        profile: Profile,
        status: DictationStatus,
        errorMessage: String?,
        audioPath: String?,
        historyVisible: Boolean
    ) {
        val timestamp = System.currentTimeMillis()
        if (retryEntryId == null) {
            dictationDao.insert(
                Dictation(
                    text = text,
                    rawText = rawText,
                    wordCount = wordCount,
                    timestamp = timestamp,
                    sourceApp = appContext?.label,
                    sourceAppPackage = appContext?.packageName,
                    durationMs = durationMs,
                    historyVisible = historyVisible,
                    status = status.name,
                    errorMessage = errorMessage,
                    profileId = profile.id.takeIf { it != 0L },
                    audioPath = audioPath
                )
            )
        } else {
            val previousAudioPath = dictationDao.getAudioPath(retryEntryId)
            if (previousAudioPath != null && previousAudioPath != audioPath) {
                deleteRetainedAudio(previousAudioPath)
            }
            dictationDao.updateRetryResult(
                id = retryEntryId,
                text = text,
                rawText = rawText,
                wordCount = wordCount,
                timestamp = timestamp,
                status = status.name,
                errorMessage = errorMessage,
                sourceApp = appContext?.label,
                sourceAppPackage = appContext?.packageName,
                profileId = profile.id.takeIf { it != 0L },
                audioPath = audioPath,
                historyVisible = historyVisible
            )
        }
    }

    private suspend fun pruneHistory() {
        val limit = preferencesDataStore.preferences.first().historyRetentionLimit
        dictationDao.getPrunableAudioPaths(limit).forEach(::deleteRetainedAudio)
        dictationDao.pruneOldEntries(limit)
    }

    private fun retainAudio(audioFile: File, retryEntryId: Long?): String? {
        if (retryEntryId != null && audioFile.exists() && audioFile.parentFile?.name == RETAINED_AUDIO_DIR) {
            return audioFile.absolutePath
        }
        return runCatching {
            val dir = File(context.filesDir, RETAINED_AUDIO_DIR).apply { mkdirs() }
            val retained = File(dir, "dictation_${System.currentTimeMillis()}.wav")
            audioFile.copyTo(retained, overwrite = true)
            retained.absolutePath
        }.onFailure { Log.e(TAG, "Could not retain audio", it) }.getOrNull()
    }

    private fun deleteRetainedAudio(path: String) {
        runCatching {
            val file = File(path)
            if (file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                file.delete()
            }
        }
    }

    private fun Dictation.toAppContext(): AppContext? {
        val storedLabel = sourceApp?.takeIf { it.isNotBlank() && !it.equals("App", ignoreCase = true) }
        val inferredPackage = sourceAppPackage ?: storedLabel?.takeIf { it.contains('.') }
        val context = AppContext(
            label = storedLabel?.takeUnless { sourceAppPackage == null && it.contains('.') },
            packageName = inferredPackage
        )
        return context.takeIf { it.hasData }
    }
}
