package com.sasayaki.service.voice

import com.sasayaki.R
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.Profile
import com.sasayaki.service.bubble.BubblePhase
import com.sasayaki.service.dictation.ProcessedRecording
import com.sasayaki.service.dictation.ProcessesRecordings
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInputSessionTest {
    private val typed = mutableListOf<String>()
    private val retried = mutableListOf<Long>()
    private val retryResult: Result<String> = Result.success("Let's meet at 9.")

    private val processor = object : ProcessesRecordings {
        override suspend fun process(
            pcm: File,
            durationMs: Long,
            appContext: AppContext?,
            profile: Profile?,
            onPostProcessing: () -> Unit,
            incognito: Boolean
        ) = ProcessedRecording.Empty

        override suspend fun retry(entryId: Long, onPostProcessing: () -> Unit): Result<String> {
            retried += entryId
            onPostProcessing()
            return retryResult
        }
    }

    private fun TestScope.session() = VoiceInputSession(
        scope = backgroundScope,
        processor = processor,
        preferences = flowOf(UserPreferences()),
        activeProfile = { Profile(name = "Chat") },
        pickProfile = { _, id -> Profile(id = id, name = "Email") },
        cacheDir = File("build/tmp"),
        onRecordingChanged = {},
        onText = {
            typed += it
            true
        },
        getString = { resId -> "string $resId" },
        describeError = { error -> "failed: ${error.message}" }
    )

    @Test
    fun `password fields are refused without recording`() = runTest {
        val session = session()

        session.start(VoiceInputTarget(appContext = null, sensitive = true, micAllowed = true, incognito = false))

        val phase = session.state.value.phase as BubblePhase.Failed
        assertEquals("string ${R.string.dictation_error_sensitive_field}", phase.message)
        assertEquals(false, phase.canRetry)
    }

    @Test
    fun `a missing microphone permission is explained`() = runTest {
        val session = session()

        session.start(VoiceInputTarget(appContext = null, sensitive = false, micAllowed = false, incognito = false))

        assertEquals("string ${R.string.voice_error_microphone_permission}", (session.state.value.phase as BubblePhase.Failed).message)
    }

    @Test
    fun `a refused field cannot be retried into recording`() = runTest {
        val session = session()
        session.start(VoiceInputTarget(appContext = null, sensitive = true, micAllowed = true, incognito = false))

        session.retry()
        runCurrent()

        assertTrue(session.state.value.phase is BubblePhase.Failed)
        assertTrue(retried.isEmpty())
    }

    @Test
    fun `cancel leaves the field untouched`() = runTest {
        val session = session()
        session.start(VoiceInputTarget(appContext = null, sensitive = true, micAllowed = true, incognito = false))

        session.cancel()

        assertEquals(BubblePhase.Idle, session.state.value.phase)
        assertTrue(typed.isEmpty())
    }

    @Test
    fun `prepare waits for a tap instead of recording`() = runTest {
        val session = session()

        val mayRecord = session.prepare(VoiceInputTarget(appContext = null, sensitive = false, micAllowed = true, incognito = false))

        assertTrue(mayRecord)
        assertEquals(BubblePhase.Idle, session.state.value.phase)
    }

    @Test
    fun `picking a profile shows it at once`() = runTest {
        val session = session()
        session.prepare(VoiceInputTarget(appContext = null, sensitive = false, micAllowed = true, incognito = false))

        session.selectProfile(2)
        runCurrent()

        assertEquals("Email", session.state.value.profileName)
    }
}
