package com.sasayaki.service

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

class AudioRecorder {
    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    val bufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(4096)
    }

    @SuppressLint("MissingPermission")
    suspend fun record(outputFile: File): Unit = withContext(Dispatchers.IO) {
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        audioRecord = recorder
        isRecording = true
        val buffer = ShortArray(bufferSize / 2)

        recorder.startRecording()

        FileOutputStream(outputFile).use { fos ->
            while (isRecording && isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    // Calculate RMS for audio level
                    var sum = 0.0
                    for (i in 0 until read) {
                        sum += buffer[i].toDouble() * buffer[i].toDouble()
                    }
                    val rms = sqrt(sum / read)
                    _audioLevel.value = (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)

                    // Write PCM data
                    val byteBuffer = ByteArray(read * 2)
                    for (i in 0 until read) {
                        byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    fos.write(byteBuffer)
                }
            }
        }

        recorder.stop()
        recorder.release()
        audioRecord = null
        _audioLevel.value = 0f
    }

    fun stop() {
        isRecording = false
    }

    fun getRmsFromBuffer(buffer: ShortArray, read: Int): Double {
        var sum = 0.0
        for (i in 0 until read) {
            sum += buffer[i].toDouble() * buffer[i].toDouble()
        }
        return sqrt(sum / read)
    }
}
