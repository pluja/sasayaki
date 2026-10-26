package com.sasayaki.service

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.sasayaki.MainActivity
import com.sasayaki.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BubbleTileService : TileService() {
    @Inject lateinit var bubbleServiceController: BubbleServiceController

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        when {
            BubbleService.isRunning -> bubbleServiceController.stop()

            !canRunBubble() -> openApp()

            else -> try {
                bubbleServiceController.start()
            } catch (e: IllegalStateException) {
                // Android may refuse a foreground start from a tile; Home has the same switch.
                Log.w(TAG, "Could not start the bubble from the tile", e)
                openApp()
            }
        }
        refresh()
    }

    private fun canRunBubble(): Boolean = Settings.canDrawOverlays(this) &&
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val running = BubbleService.isRunning
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (running) R.string.tile_on else R.string.tile_off)
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "BubbleTileService"

        /** requestRefresh asks the system to redraw the tile after the bubble starts or stops. */
        fun requestRefresh(context: Context) {
            requestListeningState(context, ComponentName(context, BubbleTileService::class.java))
        }
    }
}
