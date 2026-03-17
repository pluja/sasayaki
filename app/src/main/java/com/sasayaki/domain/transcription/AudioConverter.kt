package com.sasayaki.domain.transcription

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioConverter {
    private const val SAMPLE_RATE = 16000
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    fun pcmToWav(pcmFile: File, wavFile: File): File {
        val pcmData = pcmFile.readBytes()
        val dataSize = pcmData.size
        val headerSize = 44
        val totalSize = headerSize + dataSize

        FileOutputStream(wavFile).use { out ->
            val header = ByteBuffer.allocate(headerSize).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                // RIFF header
                put("RIFF".toByteArray())
                putInt(totalSize - 8)
                put("WAVE".toByteArray())
                // fmt chunk
                put("fmt ".toByteArray())
                putInt(16) // chunk size
                putShort(1) // PCM format
                putShort(CHANNELS.toShort())
                putInt(SAMPLE_RATE)
                putInt(SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8) // byte rate
                putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort()) // block align
                putShort(BITS_PER_SAMPLE.toShort())
                // data chunk
                put("data".toByteArray())
                putInt(dataSize)
            }
            out.write(header.array())
            out.write(pcmData)
        }
        return wavFile
    }
}
