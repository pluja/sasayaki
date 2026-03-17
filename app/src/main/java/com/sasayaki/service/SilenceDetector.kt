package com.sasayaki.service

import kotlinx.coroutines.flow.StateFlow

class SilenceDetector(
    private val audioLevel: StateFlow<Float>,
    private val silenceThresholdMs: Long = 2000L,
    private val silenceLevel: Float = 0.01f
) {
    private var silenceStartTime: Long? = null

    fun checkSilence(): Boolean {
        val level = audioLevel.value
        val now = System.currentTimeMillis()

        if (level < silenceLevel) {
            if (silenceStartTime == null) {
                silenceStartTime = now
            }
            val silenceDuration = now - (silenceStartTime ?: now)
            return silenceDuration >= silenceThresholdMs
        } else {
            silenceStartTime = null
            return false
        }
    }

    fun reset() {
        silenceStartTime = null
    }
}
