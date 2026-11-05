package com.sasayaki.service

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class BubbleChoiceWriter(scope: CoroutineScope, persist: suspend (Boolean) -> Unit) {
    private val choices = Channel<Boolean>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (enabled in choices) {
                try {
                    persist(enabled)
                } catch (e: IOException) {
                    Log.e("BubbleChoiceWriter", "Could not save bubble choice", e)
                }
            }
        }
    }

    fun remember(enabled: Boolean) {
        choices.trySend(enabled).getOrThrow()
    }
}
