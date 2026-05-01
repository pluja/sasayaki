package com.sasayaki.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager
import com.sasayaki.data.repository.ProfileRepository
import com.sasayaki.domain.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FanMenuController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val profileRepository: ProfileRepository,
    private val scope: CoroutineScope,
    private val hapticFeedback: HapticFeedback?
) {
    private var fanMenuView: FanMenuView? = null
    private var dismissJob: Job? = null
    private var profiles: List<Profile> = emptyList()
    var isShowing: Boolean = false
        private set

    fun show(anchorX: Float, anchorY: Float) {
        if (isShowing) return

        val view = FanMenuView(
            context = context,
            onItemTap = { index -> onItemTapped(index) },
            onDismiss = { dismiss() }
        )
        view.anchorX = anchorX
        view.anchorY = anchorY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
        view.collapse { removeView(view) }
        isShowing = false
        fanMenuView = null
    }

    private fun removeView(view: FanMenuView) {
        view.cleanup()
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
    }

    private fun onItemTapped(index: Int) {
        hapticFeedback?.tick()
        scope.launch {
            profiles.getOrNull(index)?.let { profileRepository.activate(it.id) }
            refreshItems(fanMenuView ?: return@launch)
            resetDismissTimer()
        }
    }

    private fun refreshItems(view: FanMenuView) {
        scope.launch {
            profiles = profileRepository.profiles.first()
            view.items = profiles.map { profile ->
                FanMenuItem(label = profile.name, active = profile.isActive)
            }
        }
    }

    private fun startDismissTimer() {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(4000)
            dismiss()
        }
    }

    private fun resetDismissTimer() {
        startDismissTimer()
    }
}
