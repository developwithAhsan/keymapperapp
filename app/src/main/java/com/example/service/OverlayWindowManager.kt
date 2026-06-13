package com.example.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.example.data.ButtonType
import com.example.data.MappedButton
import kotlinx.coroutines.*

object OverlayWindowManager {

    private var windowManager: WindowManager? = null
    
    // Transparent Fullscreen Focus Grabber for Mouse Aiming
    private var captureView: CaptureOverlayView? = null
    
    // Drag-Trigger Floating Bubble
    private var bubbleView: View? = null
    
    // Layout Key indicator badges
    private var badgesOverlayView: FrameLayout? = null

    /**
     * Initializes the floating bubble and key visual indicators.
     */
    fun showOverlays(context: Context) {
        if (windowManager == null) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        try {
            createBubbleView(context)
            createBadgesOverlay(context)
            KeymapperEngine.isOverlayShowing = true
            KeymapperEngine.log("Overlay windows initialized successfully.")
        } catch (e: Exception) {
            KeymapperEngine.log("Error launching overlay: ${e.message}")
            Toast.makeText(context, "Enable 'Display over other apps' permissions", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Clear and remove all overlay views.
     */
    fun hideOverlays(context: Context) {
        try {
            bubbleView?.let { windowManager?.removeView(it) }
            badgesOverlayView?.let { windowManager?.removeView(it) }
            captureView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            // Ignored
        } finally {
            bubbleView = null
            badgesOverlayView = null
            captureView = null
            KeymapperEngine.isOverlayShowing = false
            KeymapperEngine.isMouseLocked = false
        }
    }

    /**
     * Updates the opacity of all key mapping markers in real-time.
     */
    fun updateOverlayOpacity() {
        val badgesView = badgesOverlayView ?: return
        try {
            badgesView.post {
                badgesView.alpha = KeymapperEngine.overlayOpacity
            }
        } catch (e: Exception) {
            // safe fallback
        }
    }

    /**
     * Updates pointer capture state. Spawns/Removes mouse lock overlay.
     */
    fun updateLockState(context: Context) {
        val wm = windowManager ?: return
        
        if (KeymapperEngine.isMouseLocked) {
            // Spawn full screen capturer
            if (captureView == null) {
                captureView = CaptureOverlayView(context)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                    else 
                        2038, // TYPE_PHONE fallback
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
                )
                try {
                    wm.addView(captureView, params)
                    KeymapperEngine.log("Aim capture overlay spawned. Relative pointer capture active.")
                } catch (e: Exception) {
                    KeymapperEngine.log("Failed to spawn lock capture overlay: ${e.message}")
                    KeymapperEngine.isMouseLocked = false
                }
            }
        } else {
            // Remove full screen capturer
            captureView?.let {
                try {
                    it.releaseCapture()
                    wm.removeView(it)
                } catch (e: Exception) { /* ignored */ }
            }
            captureView = null
            KeymapperEngine.log("Aim capture overlay removed. Relative pointer released.")
        }
    }

    private fun createBubbleView(context: Context) {
        if (bubbleView != null) return

        val bubbleSize = 120 // pixels (approx 45dp)
        bubbleView = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            
            // Add a styled circular visual indicator
            val circle = FrameLayout(context).apply {
                val size = 110
                val shapeParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
                layoutParams = shapeParams
                // Stylish dark translucent gaming theme indicator with border
                val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.parseColor("#E1071424")) // Dark space blue
                    setStroke(4, Color.parseColor("#FF00D2FF")) // Cyan border
                }
                background = bgDrawable

                // Controller graphic text
                val label = TextView(context).apply {
                    text = "🎮"
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                }
                addView(label)
            }
            addView(circle)
        }

        val params = WindowManager.LayoutParams(
            130, 
            130,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                2038,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        // Draggability Logic
        bubbleView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var touchTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(bubbleView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - touchTime
                        val distance = Math.hypot((event.rawX - initialTouchX).toDouble(), (event.rawY - initialTouchY).toDouble())
                        if (duration < 250 && distance < 10) {
                            // Simple Tap: Toggle visual badges overlay
                            val toggled = !KeymapperEngine.isOverlayShowing
                            if (toggled) {
                                createBadgesOverlay(context)
                            } else {
                                badgesOverlayView?.let { 
                                    try { windowManager?.removeView(it) } catch (err: Exception) {} 
                                }
                                badgesOverlayView = null
                            }
                            KeymapperEngine.isOverlayShowing = toggled
                            KeymapperEngine.log("Floating trigger: Visual key overlays toggled to: $toggled")
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(bubbleView, params)
    }

    /**
     * Builds floating on-screen letters/labels showing active keybindings mappings.
     */
    fun createBadgesOverlay(context: Context) {
        val wm = windowManager ?: return
        if (badgesOverlayView != null) {
            try { wm.removeView(badgesOverlayView) } catch (e: Exception) {}
        }

        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        badgesOverlayView = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            alpha = KeymapperEngine.overlayOpacity
        }

        val buttons = KeymapperEngine.activeButtons
        buttons.forEach { button ->
            val absX = button.xPercent * screenW
            val absY = button.yPercent * screenH
            val sizePx = (button.sizeDp * metrics.density).toInt()

            val indicatorView = FrameLayout(context).apply {
                val shapeDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = if (button.type == ButtonType.LOOK_AIM) 
                        android.graphics.drawable.GradientDrawable.RECTANGLE 
                    else 
                        android.graphics.drawable.GradientDrawable.OVAL
                    
                    if (button.type == ButtonType.LOOK_AIM) {
                        setStroke(3, Color.parseColor("#4400FF00")) // Translucent Green Aim Area
                        setColor(Color.parseColor("#1100FF00"))
                        cornerRadius = sizePx / 4f
                    } else {
                        setStroke(2, Color.parseColor("#7700E5FF")) // Cyan Tap Badge
                        setColor(Color.parseColor("#5F071221")) // Semi-translucent dark blue
                    }
                }
                background = shapeDrawable

                val labelTv = TextView(context).apply {
                    text = if (button.type == ButtonType.LOOK_AIM) "AIM LOOK" else button.keyChar
                    textSize = if (button.type == ButtonType.LOOK_AIM) 10f else 11f
                    setTextColor(if (button.type == ButtonType.LOOK_AIM) Color.GREEN else Color.parseColor("#FF00E5FF"))
                    gravity = Gravity.CENTER
                }
                addView(labelTv)
            }

            val relativeParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                leftMargin = (absX - sizePx / 2f).toInt()
                topMargin = (absY - sizePx / 2f).toInt()
            }
            badgesOverlayView?.addView(indicatorView, relativeParams)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                2038,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        wm.addView(badgesOverlayView, params)
    }

    /**
     * Background full screen overlay capturing mouse movements and clicks.
     */
    private class CaptureOverlayView(context: Context) : FrameLayout(context) {

        private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private var tickerJob: Job? = null
        private var autoFireJob: Job? = null

        private var accumDeltaX = 0f
        private var accumDeltaY = 0f
        
        private var isFiringLeft = false
        private var isFiringRight = false

        init {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = true
            isFocusableInTouchMode = true
            
            // Auto request relative pointer grab once view binds
            addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.requestPointerCapture()
                    KeymapperEngine.log("Pointer capture triggered successfully!")
                    startPollingLoops()
                }
                override fun onViewDetachedFromWindow(v: View) {
                    stopPollingLoops()
                }
            })
        }

        private fun startPollingLoops() {
            stopPollingLoops()
            
            var smoothedDx = 0f
            var smoothedDy = 0f
            
            // Look panning polling loop
            tickerJob = uiScope.launch {
                while (isActive) {
                    delay(20) // 50Hz refresh rate tick rate
                    val dx = accumDeltaX
                    val dy = accumDeltaY
                    
                    accumDeltaX = 0f
                    accumDeltaY = 0f

                    val smoothing = KeymapperEngine.mouseSmoothing.coerceIn(0f, 0.95f)
                    val alpha = 1.0f - smoothing
                    
                    smoothedDx = smoothedDx * smoothing + dx * alpha
                    smoothedDy = smoothedDy * smoothing + dy * alpha

                    // Apply a small noise/deadzone threshold to prevent infinitely small drifting
                    val finalDx = if (kotlin.math.abs(smoothedDx) < 0.05f) 0f else smoothedDx
                    val finalDy = if (kotlin.math.abs(smoothedDy) < 0.05f) 0f else smoothedDy

                    if (finalDx != 0f || finalDy != 0f) {
                        val lookButton = KeymapperEngine.activeButtons.find { it.type == ButtonType.LOOK_AIM }
                        
                        val m = context.resources.displayMetrics
                        val w = m.widthPixels
                        val h = m.heightPixels

                        val startX = (lookButton?.xPercent ?: 0.65f) * w
                        val startY = (lookButton?.yPercent ?: 0.50f) * h

                        // Apply independent X and Y sensitivities
                        val speedX = KeymapperEngine.mouseSensitivity * KeymapperEngine.mouseSensitivityX * 4.5f
                        val speedY = KeymapperEngine.mouseSensitivity * KeymapperEngine.mouseSensitivityY * 4.5f
                        
                        val endX = startX + finalDx * speedX
                        val endY = startY + finalDy * speedY

                        KeymapperAccessibilityService.instance?.injectSwipe(
                            startX, startY, endX, endY, 20
                        )
                    }
                }
            }
        }

        private fun stopPollingLoops() {
            tickerJob?.cancel()
            tickerJob = null
            stopContinuousFiring()
        }

        private fun startContinuousFiring(button: MappedButton) {
            if (autoFireJob != null) return
            
            if (KeymapperEngine.isAutoFireEnabled) {
                autoFireJob = uiScope.launch {
                    while (isActive) {
                        KeymapperAccessibilityService.instance?.triggerButtonTouch(button)
                        delay(KeymapperEngine.autoFireRateMs)
                    }
                }
            } else {
                // Single tap only if auto-fire is off
                KeymapperAccessibilityService.instance?.triggerButtonTouch(button)
            }
        }

        private fun stopContinuousFiring() {
            autoFireJob?.cancel()
            autoFireJob = null
        }

        fun releaseCapture() {
            stopPollingLoops()
            releasePointerCapture()
        }

        /**
         * Intercept mouse motion deltas while pointer screen lock is active.
         * Leverages onCapturedPointerEvent for premium accuracy under ChromeOS / Android pointer capture.
         */
        override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
            return processPointerInput(event)
        }

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            if (processPointerInput(event)) {
                return true
            }
            return super.dispatchGenericMotionEvent(event)
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (processPointerInput(event)) {
                return true
            }
            return super.dispatchTouchEvent(event)
        }

        /**
         * Centralized Mouse Handler that extracts relative movements and click states safely.
         */
        private fun processPointerInput(event: MotionEvent): Boolean {
            // 1. Accumulate relative mouse motion (Look camera)
            val rx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
            } else {
                0f
            }
            val ry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
            } else {
                0f
            }
            
            if (rx != 0f || ry != 0f) {
                accumDeltaX += rx
                accumDeltaY += ry
            }

            // 2. Track left and right button click states dynamically
            val btnState = event.buttonState
            val leftPressed = (btnState and MotionEvent.BUTTON_PRIMARY) != 0
            if (leftPressed) {
                if (!isFiringLeft) {
                    isFiringLeft = true
                    val fireBtn = KeymapperEngine.activeButtons.find { it.keyChar == "LClick" }
                    if (fireBtn != null) {
                        startContinuousFiring(fireBtn)
                    }
                }
            } else {
                if (isFiringLeft) {
                    isFiringLeft = false
                    stopContinuousFiring()
                }
            }

            val rightPressed = (btnState and MotionEvent.BUTTON_SECONDARY) != 0
            if (rightPressed) {
                if (!isFiringRight) {
                    isFiringRight = true
                    val scopeBtn = KeymapperEngine.activeButtons.find { it.keyChar == "RClick" }
                    if (scopeBtn != null) {
                        KeymapperAccessibilityService.instance?.triggerButtonTouch(scopeBtn)
                    }
                }
            } else {
                if (isFiringRight) {
                    isFiringRight = false
                }
            }

            // Consume all mouse actions when mouse layout is locked
            return true
        }
    }
}
