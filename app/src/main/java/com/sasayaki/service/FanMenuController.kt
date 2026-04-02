package com.sasayaki.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager
import com.sasayaki.data.preferences.PreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FanMenuController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val preferencesDataStore: PreferencesDataStore,
    private val scope: CoroutineScope,
    private val hapticFeedback: HapticFeedback?
) {
    private var fanMenuView: FanMenuView? = null
    private var dismissJob: Job? = null
    var isShowing: Boolean = false
        private set

    fun show(bubbleX: Int, bubbleY: Int, bubbleSizePx: Int) {
        if (isShowing) return

        val screenWidth = context.resources.displayMetrics.widthPixels
        val bubbleCenterX = bubbleX + bubbleSizePx / 2f
        val bubbleCenterY = bubbleY + bubbleSizePx / 2f
        val fanRight = bubbleCenterX < screenWidth / 2f

        val view = FanMenuView(
            context = context,
            onItemTap = { index -> onItemTapped(index) },
            onDismiss = { dismiss() }
        )
        view.anchorX = bubbleCenterX
        view.anchorY = bubbleCenterY
        view.fanRight = fanRight

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(view, params)
            fanMenuView = view
            isShowing = true
            hapticFeedback?.tick()
            refreshItems(view)
            view.expand()
            startDismissTimer()
        } catch (e: Exception) {
            isShowing = false
        }
    }

    fun dismiss() {
        val view = fanMenuView ?: return
        if (!isShowing) return
        dismissJob?.cancel()

        view.collapse {
            removeView(view)
        }
        isShowing = false
        fanMenuView = null
    }

    private fun removeView(view: FanMenuView) {
        view.cleanup()
        try { windowManager.removeView(view) } catch (_: Exception) {}
    }

    private fun onItemTapped(index: Int) {
        hapticFeedback?.tick()
        scope.launch {
            val prefs = preferencesDataStore.preferences.first()
            when (index) {
                0 -> cycleLanguage(prefs.preferredLanguages, prefs.activeLanguage)
                1 -> preferencesDataStore.toggleLlmEnabled()
                2 -> preferencesDataStore.toggleHistoryEnabled()
            }
            refreshItems(fanMenuView ?: return@launch)
            resetDismissTimer()
        }
    }

    private suspend fun cycleLanguage(preferredLanguages: List<String>, current: String?) {
        if (preferredLanguages.isEmpty()) return
        val cycle = preferredLanguages + listOf(null)
        val currentIndex = cycle.indexOf(current)
        val nextIndex = (currentIndex + 1) % cycle.size
        preferencesDataStore.updateActiveLanguage(cycle[nextIndex])
    }

    private fun refreshItems(view: FanMenuView) {
        scope.launch {
            val prefs = preferencesDataStore.preferences.first()
            view.items = listOf(
                FanMenuItem(
                    label = prefs.activeLanguage?.uppercase() ?: "AUTO",
                    active = prefs.activeLanguage != null
                ),
                FanMenuItem(
                    label = "LLM",
                    active = prefs.llmEnabled
                ),
                FanMenuItem(
                    label = "SAVE",
                    active = prefs.historyEnabled
                )
            )
        }
    }

    private fun startDismissTimer() {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(3000)
            dismiss()
        }
    }

    private fun resetDismissTimer() {
        startDismissTimer()
    }
}
