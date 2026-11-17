package com.sasayaki.domain.transcription

import android.content.Context
import android.util.Log
import com.sasayaki.R
import com.sasayaki.data.db.entity.Dictation
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.repository.DictationRecord
import com.sasayaki.data.repository.HistoryRepository
import com.sasayaki.data.repository.ProfileRepository
import com.sasayaki.data.repository.ProviderRepository
import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.processing.AppCategory
import com.sasayaki.domain.processing.TextProcessor
import com.sasayaki.domain.processing.appCategoryOf
import com.sasayaki.domain.processing.speechPrompt
import com.sasayaki.domain.processing.withoutRecitedGlossary
import com.sasayaki.domain.provider.Purpose
import com.sasayaki.domain.provider.Resolution
import com.sasayaki.domain.provider.describeConnectionError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** DictationFailure carries the history entry a failed dictation can be retried from, if one was kept. */
class DictationFailure(override val cause: Throwable, val retryEntryId: Long?) : Exception(cause.message, cause)

@Singleton
class TranscriptionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: TranscriptionEngine,
    private val textProcessor: TextProcessor,
    private val historyRepository: HistoryRepository,
    private val preferencesDataStore: PreferencesDataStore,
    private val profileRepository: ProfileRepository,
    private val providerRepository: ProviderRepository
) {
    /** transcribe fails only with [DictationFailure]. */
    suspend fun transcribe(
        audioFile: File,
        durationMs: Long,
        appContext: AppContext?,
        profile: Profile? = null,
        onPostProcessing: (() -> Unit)? = null,
        incognito: Boolean = false
    ): Result<String> {
        val dictation = DictationRecord(
            audio = audioFile,
            durationMs = durationMs,
            appContext = appContext,
            profile = profile ?: profileRepository.getActiveProfile(),
            incognito = incognito
        )
        return process(dictation, onPostProcessing)
    }

    /** retry fails only with [DictationFailure]. */
    suspend fun retry(dictationId: Long, onPostProcessing: (() -> Unit)? = null): Result<String> {
        val entry = historyRepository.entry(dictationId)
            ?: return unretriable(context.getString(R.string.error_history_entry_missing))
        val audioFile = entry.audioPath?.let(::File)?.takeIf { it.exists() }
            ?: return unretriable(context.getString(R.string.error_audio_missing))
        val profile = entry.profileId?.let { profileRepository.getProfile(it) }
            ?: profileRepository.getActiveProfile()
        val dictation = DictationRecord(
            audio = audioFile,
            durationMs = entry.durationMs,
            appContext = entry.toAppContext(),
            profile = profile,
            retryEntryId = entry.id
        )
        return process(dictation, onPostProcessing)
    }

    private suspend fun process(dictation: DictationRecord, onPostProcessing: (() -> Unit)?): Result<String> {
        val prefs = preferencesDataStore.preferences.first()
        val profile = dictation.profile
        val endpoint = when (val speech = providerRepository.resolve(profile, Purpose.SPEECH)) {
            is Resolution.Ready -> speech.endpoint

            Resolution.NoProvider -> return fail(dictation, Exception(context.getString(R.string.error_speech_not_set_up)))

            is Resolution.NoModel -> return fail(
                dictation,
                Exception(context.getString(R.string.error_speech_model_missing, speech.provider.name))
            )
        }
        if (!engine.isAvailable()) return fail(dictation, Exception(context.getString(R.string.error_offline)))

        Log.d(TAG, "Sending ${dictation.audio.length()} bytes for transcription (model=${endpoint.model})")
        val rawText = engine.transcribe(dictation.audio, endpoint, profile.language, speechPrompt(prefs.vocabulary))
            .map { withoutRecitedGlossary(it, prefs.vocabulary) }
            .getOrElse { return fail(dictation, it) }

        if (rawText.isBlank()) {
            historyRepository.recordSuccess(dictation, text = "", rawText = "")
            return Result.success("")
        }

        val appContext = dictation.appContext
        val skipCleanup = prefs.skipCleanupInAiChats && appCategoryOf(appContext?.packageName) == AppCategory.AI_CHAT
        val text = textProcessor.process(rawText, profile, appContext, skipCleanup, onPostProcessing)
        historyRepository.recordSuccess(dictation, text = text, rawText = rawText)
        return Result.success(text)
    }

    private suspend fun fail(dictation: DictationRecord, error: Throwable): Result<String> {
        Log.e(TAG, "Dictation failed", error)
        val retryEntryId = historyRepository.recordFailure(dictation, describeConnectionError(error, context.resources))
        return Result.failure(DictationFailure(error, retryEntryId))
    }

    private fun unretriable(message: String): Result<String> =
        Result.failure(DictationFailure(IllegalStateException(message), retryEntryId = null))

    // Rows written before sourceAppPackage existed hold only a display name, which may be
    // the package name itself or the placeholder "App".
    private fun Dictation.toAppContext(): AppContext? {
        val storedLabel = sourceApp?.takeIf { it.isNotBlank() && !it.equals("App", ignoreCase = true) }
        val inferredPackage = sourceAppPackage ?: storedLabel?.takeIf { it.contains('.') }
        val context = AppContext(
            label = storedLabel?.takeUnless { sourceAppPackage == null && it.contains('.') },
            packageName = inferredPackage
        )
        return context.takeIf { it.hasData }
    }

    private companion object {
        const val TAG = "TranscriptionManager"
    }
}
