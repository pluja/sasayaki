package com.sasayaki.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticFeedback(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun recordStart() {
        vibrate(longArrayOf(0, 50, 30, 50))
    }

    fun recordStop() {
        vibrate(longArrayOf(0, 30))
    }

    fun complete() {
        vibrate(longArrayOf(0, 20, 40, 20, 40, 20))
    }

    fun error() {
        vibrate(longArrayOf(0, 100, 50, 100))
    }

    fun tick() {
        vibrate(longArrayOf(0, 15))
    }

    private fun vibrate(pattern: LongArray) {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
