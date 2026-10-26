package com.sasayaki.service

import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.Profile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * DictationHandover passes a running dictation from the keyboard panel to the bubble. Keyboard
 * recordings run in [scope], which outlives both services, so the microphone keeps recording while
 * the dictation changes hands.
 */
object DictationHandover {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var pending: CarriedDictation? = null

    /** carrying is true while the bubble holds a dictation the keyboard panel handed over. */
    @Volatile var carrying = false

    // release is set by the bubble while it carries a dictation, and gives the dictation back.
    @Volatile var release: (() -> CarriedDictation?)? = null

    // onCarryEnded is set by a hidden panel that stays the keyboard, so it can hand back to the
    // previous keyboard if the bubble finishes the dictation before the panel opens again.
    @Volatile var onCarryEnded: (() -> Unit)? = null

    fun offer(dictation: CarriedDictation) {
        pending = dictation
    }

    fun take(): CarriedDictation? = pending.also { pending = null }

    /** reclaim takes back the dictation the bubble carries, still recording, or returns null. */
    fun reclaim(): CarriedDictation? {
        onCarryEnded = null
        return release?.invoke()
    }

    /** carryEnded tells a waiting hidden panel that the bubble finished its dictation. */
    fun carryEnded() {
        onCarryEnded.also { onCarryEnded = null }?.invoke()
    }
}

/** CarriedDictation is a recording in progress, with what the bubble needs to finish it. */
class CarriedDictation(
    val recorder: AudioRecorder,
    val pcm: File,
    val job: Job?,
    val recordedMs: Long,
    val paused: Boolean,
    val appContext: AppContext?,
    val profile: Deferred<Profile>?,
    val incognito: Boolean
)
