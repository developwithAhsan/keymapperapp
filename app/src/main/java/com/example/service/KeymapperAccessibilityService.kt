package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.data.ButtonType
import com.example.data.MappedButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class KeymapperAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: KeymapperAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        KeymapperEngine.isServiceActive = true
        KeymapperEngine.log("Accessibility Service connected!")
        Toast.makeText(this, "Nexus KeyMapper Service Active", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        KeymapperEngine.isServiceActive = false
        KeymapperEngine.log("Accessibility Service disconnected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Required, though unused for keymapping
    }

    override fun onInterrupt() {
        // Required
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!KeymapperEngine.isServiceActive) return false

        val keyCode = event.keyCode
        val action = event.action

        // Check for Mouse Lock/Unlock Toggle hotkey dynamically configured
        if (keyCode == KeymapperEngine.mouseLockKeyCode && action == KeyEvent.ACTION_DOWN) {
            KeymapperEngine.toggleMouseLock()
            OverlayWindowManager.updateLockState(this)
            return true // Consume to prevent key noise inside the active game application
        }

        // Only process key presses (DOWN) for our mapping system tap triggers
        if (action == KeyEvent.ACTION_DOWN) {
            val matchedButton = KeymapperEngine.activeButtons.find { it.keyCode == keyCode }
            if (matchedButton != null) {
                KeymapperEngine.log("Key Intercepted: ${KeyEvent.keyCodeToString(keyCode)} -> Performing Touch")
                triggerButtonTouch(matchedButton)
                return true // Consume key to prevent default keyboard noise inside games
            }
        }

        // Match some default standard characters if profile doesn't map them
        // e.g. mapping W, A, S, D, Space to screen centers
        return false
    }

    /**
     * Executes a programmatic click gesture at the relative button percent coordinates.
     */
    fun triggerButtonTouch(button: MappedButton) {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val absX = button.xPercent * width
        val absY = button.yPercent * height

        if (button.type == ButtonType.MACRO) {
            executeMacroSequence(button.macroSequence, absX, absY)
        } else {
            injectTap(absX, absY)
        }
    }

    /**
     * Injects a standard fast single-tap gesture at raw coordinates.
     */
    fun injectTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0, 45) // Ultra-quick touch latency
        val gestureBuilder = GestureDescription.Builder().apply {
            addStroke(stroke)
        }
        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                KeymapperEngine.log("Tap gesture cancelled internally")
            }
        }, handler)
    }

    /**
     * Injects a drag/swipe gesture. Crucial for converting mouse movement to touches.
     */
    fun injectSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 60) {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gestureBuilder = GestureDescription.Builder().apply {
            addStroke(stroke)
        }
        dispatchGesture(gestureBuilder.build(), null, handler)
    }

    /**
     * Executes a simulated combo macro sequence (e.g., tap action A, pause, tap action B).
     */
    private fun executeMacroSequence(sequence: String, startX: Float, startY: Float) {
        if (sequence.isEmpty()) {
            // Fallback tap
            injectTap(startX, startY)
            return
        }
        
        KeymapperEngine.log("Executing Macro combo: $sequence")
        val macroScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        macroScope.launch {
            // Example layout format: "tap_0.2_0.3;delay_100;tap_0.5_0.5"
            val parts = sequence.split(";")
            val displayMetrics = resources.displayMetrics
            val w = displayMetrics.widthPixels
            val h = displayMetrics.heightPixels

            for (part in parts) {
                val args = part.split("_")
                when (args.firstOrNull()) {
                    "tap" -> {
                        val rx = args.getOrNull(1)?.toFloatOrNull() ?: 0.5f
                        val ry = args.getOrNull(2)?.toFloatOrNull() ?: 0.5f
                        injectTap(rx * w, ry * h)
                    }
                    "delay" -> {
                        val ms = args.getOrNull(1)?.toLongOrNull() ?: 100L
                        delay(ms)
                    }
                }
            }
        }
    }
}
