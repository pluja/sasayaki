package com.sasayaki.service

import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import com.sasayaki.service.bubble.BubblePhase

internal fun Modifier.keepScreenOnWhileRecording(phase: BubblePhase, enabled: Boolean): Modifier =
    if (enabled && phase is BubblePhase.Recording && !phase.paused) keepScreenOn() else this
