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
import kotlinx.coroutines.launch

// BubbleServiceController starts and stops the bubble for the app's screens and the tile, and
// remembers the choice so a boot or an update restores it.
@Singleton
class BubbleServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataStore: PreferencesDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val running: StateFlow<Boolean> = BubbleService.runningState

    fun start() {
        BubbleService.start(context)
        scope.launch { preferencesDataStore.setShowBubble(true) }
    }

    fun stop() {
        BubbleService.stop(context)
        scope.launch { preferencesDataStore.setShowBubble(false) }
    }
}
