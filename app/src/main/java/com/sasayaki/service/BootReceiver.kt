package com.sasayaki.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.sasayaki.data.preferences.PreferencesDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restores the dictation bubble after a reboot.
 *
 * The bubble only becomes visible once a keyboard appears, so this brings back the
 * service (and its notification), not a bubble on the home screen.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var preferencesDataStore: PreferencesDataStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        // The overlay permission survives reboots, but it can be revoked while the device
        // is off. Without it BubbleService stops itself immediately, so skip the start.
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Skipping boot start: overlay permission not granted")
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (preferencesDataStore.preferences.first().startOnBoot) {
                    BubbleService.start(appContext)
                }
            } catch (e: Exception) {
                // A denied background start must not crash the boot broadcast.
                Log.e(TAG, "Failed to start bubble service on boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
