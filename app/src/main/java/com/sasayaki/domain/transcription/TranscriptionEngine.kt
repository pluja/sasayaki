package com.sasayaki.domain.transcription

interface TranscriptionEngine {
    suspend fun transcribe(audioFile: java.io.File, dictionaryWords: List<String>): Result<String>
    fun isAvailable(): Boolean
}
