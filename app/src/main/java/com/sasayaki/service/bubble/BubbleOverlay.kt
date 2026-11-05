package com.sasayaki.service.bubble

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.core.content.edit
import androidx.core.graphics.Insets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.domain.model.Profile
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * BubbleOverlay owns the overlay windows: the bubble itself, the drag-to-remove target shown while
 * dragging, and the profile menu. The bubble docks to the nearest screen edge, anchored
 * by gravity on that side so it grows away from the edge as it expands.
 */
class BubbleOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val scope: CoroutineScope,
    private val uiState: StateFlow<BubbleUiState>,
    private val levels: StateFlow<List<Float>>,
    private val profiles: StateFlow<List<Profile>>,
    private val appearance: StateFlow<BubbleAppearance>,
    private val scales: StateFlow<BubbleScales>,
    private val keepScreenOnWhileRecording: StateFlow<Boolean>,
    private val actions: BubbleActions,
    private val onSelectProfile: (Long) -> Unit,
    private val onRemove: () -> Unit
) {
    private enum class Side { START, END }

    private val density = context.resources.displayMetrics.density
    private val edgeMargin = (8 * density).roundToInt()
    private val removeRadius = 96 * density
    private val positions = context.getSharedPreferences(POSITION_PREFS, Context.MODE_PRIVATE)
    private val lifecycleOwner = OverlayLifecycleOwner()

    private var side by mutableStateOf(if (positions.getBoolean(KEY_START_SIDE, false)) Side.START else Side.END)
    private var bubbleRoot: DraggableOverlayLayout? = null
    private var bubbleShown = false
    private val bubbleParams = overlayParams().apply {
        width = windowWidthPx(BubbleMode.IDLE)
        height = windowHeightPx(BubbleMode.IDLE)
        gravity = Gravity.TOP or side.gravity
        y = positions.getInt(KEY_Y, (screenHeight() * 0.4f).roundToInt())
    }

    private var targetView: ComposeView? = null
    private var targetArmed by mutableStateOf(false)
    private var menuView: ComposeView? = null
    private var menuTimeout: Job? = null
    private var menuClosedByOutsideTapAt = 0L
    private var dragStartX = 0
    private var dragStartY = 0
    private var snapAnimator: ValueAnimator? = null
    private var windowSizeJob: Job? = null

    // A tap on the bubble that closes the profile menu must not also start a recording.
    private val bubbleActions = BubbleActions(
        onStart = { if (SystemClock.uptimeMillis() - menuClosedByOutsideTapAt > OUTSIDE_TAP_GRACE_MS) actions.onStart() },
        onStop = actions.onStop,
        onCancel = actions.onCancel,
        onTogglePause = actions.onTogglePause,
        onCancelProcessing = actions.onCancelProcessing,
        onRetry = actions.onRetry,
        onDismissError = actions.onDismissError
    )

    fun create() {
        lifecycleOwner.start()
        val compose = composeView {
            val state by uiState.collectAsStateWithLifecycle()
            val levelState = levels.collectAsStateWithLifecycle()
            val bubbleScales by scales.collectAsStateWithLifecycle()
            val keepScreenOn by keepScreenOnWhileRecording.collectAsStateWithLifecycle()
            BubbleContent(
                state = state,
                levels = { levelState.value },
                actions = bubbleActions,
                onShowProfiles = ::showProfileMenu,
                alignment = if (side == Side.END) Alignment.CenterEnd else Alignment.CenterStart,
                scales = bubbleScales,
                keepScreenOnWhileRecording = keepScreenOn
            )
        }
        bubbleRoot = DraggableOverlayLayout(context, ::onDragStart, ::onDrag, ::onDragEnd).apply {
            lifecycleOwner.attachTo(this)
            addView(compose)
        }
        windowSizeJob = scope.launch {
            combine(uiState.map { it.phase.mode }.distinctUntilChanged(), scales, ::Pair)
                .collectLatest { (mode, _) ->
                    val width = windowWidthPx(mode).coerceAtMost(screenWidth() - 2 * edgeMargin)
                    val height = windowHeightPx(mode)
                    // Grow the window before the content morphs; shrink it only once the
                    // content has finished shrinking, so neither is ever clipped. The bubble and
                    // the controls are sized separately, so one dimension can grow while the
                    // other shrinks.
                    val shrinks = width < bubbleParams.width || height < bubbleParams.height
                    bubbleParams.width = maxOf(width, bubbleParams.width)
                    bubbleParams.height = maxOf(height, bubbleParams.height)
                    bubbleRoot?.let(::update)
                    if (shrinks) {
                        delay(SHRINK_DELAY_MS)
                        bubbleParams.width = width
                        bubbleParams.height = height
                        bubbleRoot?.let(::update)
                    }
                }
        }
    }

    fun show() {
        val root = bubbleRoot ?: return
        if (bubbleShown) return
        bubbleParams.x = dockedX()
        bubbleParams.y = clampY(bubbleParams.y, bubbleParams.height)
        try {
            windowManager.addView(root, bubbleParams)
            bubbleShown = true
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not show the bubble", e)
        }
    }

    fun hide() {
        dismissProfileMenu()
        hideTarget()
        val root = bubbleRoot ?: return
        if (!bubbleShown) return
        removeSafely(root)
        bubbleShown = false
    }

    /** onDisplayChanged keeps a saved position on screen after rotation or a display change. */
    fun onDisplayChanged() {
        dismissProfileMenu()
        val root = bubbleRoot ?: return
        snapAnimator?.cancel()
        bubbleParams.x = dockedX()
        bubbleParams.y = clampY(bubbleParams.y, bubbleParams.height)
        update(root)
    }

    fun destroy() {
        snapAnimator?.cancel()
        windowSizeJob?.cancel()
        hide()
        lifecycleOwner.destroy()
        bubbleRoot = null
    }

    fun dismissProfileMenu() {
        menuTimeout?.cancel()
        menuView?.let(::removeSafely)
        menuView = null
    }

    private fun onDragStart() {
        snapAnimator?.cancel()
        dismissProfileMenu()
        val root = bubbleRoot ?: return
        // Switch to start-anchored coordinates so the drag can move freely.
        if (side == Side.END) {
            bubbleParams.x = screenWidth() - root.width - bubbleParams.x
            bubbleParams.gravity = Gravity.TOP or Gravity.START
            side = Side.START
            update(root)
        }
        dragStartX = bubbleParams.x
        dragStartY = bubbleParams.y
        showTarget()
    }

    private fun onDrag(dx: Float, dy: Float) {
        val root = bubbleRoot ?: return
        bubbleParams.x = dragStartX + dx.roundToInt()
        bubbleParams.y = clampY(dragStartY + dy.roundToInt(), root.height)
        update(root)
        val armed = distanceToTarget(root) < removeRadius
        if (armed != targetArmed) {
            targetArmed = armed
            if (armed) root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun onDragEnd() {
        val root = bubbleRoot ?: return
        val remove = targetArmed
        hideTarget()
        if (remove) {
            root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onRemove()
            return
        }
        snapToEdge(root)
    }

    private fun snapToEdge(root: View) {
        val width = screenWidth()
        val center = bubbleParams.x + root.width / 2
        val toEnd = center > width / 2
        val target = if (toEnd) width - root.width - edgeMargin - endInset() else edgeMargin + startInset()
        snapAnimator = ValueAnimator.ofInt(bubbleParams.x, target).apply {
            duration = SNAP_DURATION_MS
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                bubbleParams.x = it.animatedValue as Int
                update(root)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (toEnd) {
                        side = Side.END
                        bubbleParams.gravity = Gravity.TOP or Gravity.END
                        bubbleParams.x = dockedX()
                        update(root)
                    }
                    positions.edit {
                        putBoolean(KEY_START_SIDE, !toEnd)
                        putInt(KEY_Y, bubbleParams.y)
                    }
                }
            })
            start()
        }
    }

    private fun showTarget() {
        if (targetView != null) return
        val view = composeView { DismissTarget(armed = targetArmed) }
        lifecycleOwner.attachTo(view)
        // Overlays draw below the keyboard, which is usually open, so the target sits at the top.
        val params = overlayParams(touchable = false).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topInset() + (TARGET_OFFSET_DP * density).roundToInt()
        }
        try {
            windowManager.addView(view, params)
            targetView = view
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not show the remove target", e)
        }
    }

    private fun hideTarget() {
        targetArmed = false
        targetView?.let(::removeSafely)
        targetView = null
    }

    private fun distanceToTarget(root: View): Float {
        val bubbleX = bubbleParams.x + root.width / 2f
        val bubbleY = bubbleParams.y + root.height / 2f
        val targetX = screenWidth() / 2f
        val targetY = topInset() + (TARGET_OFFSET_DP + TARGET_SIZE_DP / 2) * density
        return hypot(bubbleX - targetX, bubbleY - targetY)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showProfileMenu() {
        val root = bubbleRoot ?: return
        if (menuView != null || profiles.value.size < 2) return
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val view = composeView {
            val list by profiles.collectAsStateWithLifecycle()
            ProfileMenu(list) { id ->
                onSelectProfile(id)
                dismissProfileMenu()
            }
        }
        lifecycleOwner.attachTo(view)
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                menuClosedByOutsideTapAt = SystemClock.uptimeMillis()
                dismissProfileMenu()
            }
            false
        }
        val params = overlayParams(watchOutside = true).apply {
            gravity = Gravity.TOP or side.gravity
            x = bubbleParams.x
            y = bubbleParams.y
            alpha = 0f
        }
        // The menu's height is only known after layout; place it above the bubble, or
        // below when there is no room, then reveal it.
        view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                if (v.height == 0) return
                v.removeOnLayoutChangeListener(this)
                val above = bubbleParams.y - v.height
                params.y = if (above > 0) above else bubbleParams.y + root.height
                params.alpha = 1f
                update(v, params)
            }
        })
        try {
            windowManager.addView(view, params)
            menuView = view
            menuTimeout = scope.launch {
                delay(MENU_TIMEOUT_MS)
                dismissProfileMenu()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not show the profile menu", e)
        }
    }

    private fun composeView(content: @Composable () -> Unit) = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        setContent {
            val look by appearance.collectAsStateWithLifecycle()
            BubbleTheme(look, content)
        }
    }

    private fun overlayParams(touchable: Boolean = true, watchOutside: Boolean = false) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
            (if (watchOutside) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else 0),
        PixelFormat.TRANSLUCENT
    ).apply {
        // Positions are computed against the whole display, so the window must not be
        // shifted inside the system bars.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
        // The system otherwise animates every position change. When the bubble shrinks its
        // left edge moves, so the content would snap into place while the window slid after
        // it, and drags would trail the finger.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setCanPlayMoveAnimation(false)
    }

    private fun update(view: View, params: WindowManager.LayoutParams = bubbleParams) {
        if (!view.isAttachedToWindow) return
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay was already removed", e)
        }
    }

    private fun removeSafely(view: View) {
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay was already removed", e)
        }
    }

    private fun windowWidthPx(mode: BubbleMode): Int = windowPx(mode.windowWidth, mode)

    private fun windowHeightPx(mode: BubbleMode): Int = windowPx(mode.windowHeight, mode)

    private fun windowPx(size: Dp, mode: BubbleMode): Int = (size.value * scales.value.of(mode) * density).roundToInt()

    private fun clampY(y: Int, height: Int): Int {
        val min = topInset()
        val max = screenHeight() - bottomInset() - height
        return y.coerceIn(min, max.coerceAtLeast(min))
    }

    /** dockedX is the gravity offset that keeps the bubble clear of a side system bar. */
    private fun dockedX(): Int = edgeMargin + if (side == Side.END) endInset() else startInset()

    private fun startInset(): Int = systemBarInsets()?.left ?: 0

    private fun endInset(): Int = systemBarInsets()?.right ?: 0

    private fun topInset(): Int = systemBarInsets()?.top ?: (24 * density).roundToInt()

    private fun bottomInset(): Int = systemBarInsets()?.bottom ?: (48 * density).roundToInt()

    private fun systemBarInsets(): Insets? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insets = windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        Insets.toCompatInsets(insets)
    } else {
        null
    }

    private fun screenWidth(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.width()
    } else {
        context.resources.displayMetrics.widthPixels
    }

    private fun screenHeight(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.height()
    } else {
        context.resources.displayMetrics.heightPixels
    }

    private val Side.gravity: Int get() = if (this == Side.START) Gravity.START else Gravity.END

    private companion object {
        const val TAG = "BubbleOverlay"
        const val POSITION_PREFS = "bubble_position"
        const val KEY_START_SIDE = "start_side"
        const val KEY_Y = "y"
        const val SNAP_DURATION_MS = 280L
        const val MENU_TIMEOUT_MS = 6000L
        const val OUTSIDE_TAP_GRACE_MS = 400L
        const val SHRINK_DELAY_MS = 450L
        const val TARGET_OFFSET_DP = 24
        const val TARGET_SIZE_DP = 96
    }
}
