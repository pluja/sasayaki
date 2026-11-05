package com.sasayaki.service

import android.content.Context
import com.sasayaki.data.preferences.PreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

@Singleton
class BubbleServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    preferencesDataStore: PreferencesDataStore
) {
    private val choices = BubbleChoiceWriter(
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        preferencesDataStore::setShowBubble
    )

    val running: StateFlow<Boolean> = BubbleService.runningState

    fun start() {
        BubbleService.start(context)
        choices.remember(true)
    }

    fun stop() {
        BubbleService.stop(context)
        choices.remember(false)
    }
}
