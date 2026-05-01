package com.sasayaki.domain.transcription

interface TranscriptionEngine {
    suspend fun transcribe(audioFile: java.io.File, model: String, language: String?): Result<String>
    fun isAvailable(): Boolean
}
