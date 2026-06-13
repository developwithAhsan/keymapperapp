package com.example.service

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.AppDatabase
import com.example.data.ButtonType
import com.example.data.GameProfile
import com.example.data.MappedButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object KeymapperEngine {

    // Reactive states for UI bindings
    var isServiceActive by mutableStateOf(false)
    var isOverlayShowing by mutableStateOf(false)
    var isMouseLocked by mutableStateOf(false)
    var mouseSensitivity by mutableStateOf(1.0f)
    var mouseSensitivityX by mutableStateOf(1.2f)
    var mouseSensitivityY by mutableStateOf(1.2f)
    var mouseSmoothing by mutableStateOf(0.15f)
    var overlayOpacity by mutableStateOf(0.7f)
    
    var isAutoFireEnabled by mutableStateOf(true)
    var autoFireRateMs by mutableStateOf(110L)
    
    var mouseLockKeyCode by mutableStateOf(61) // KeyEvent.KEYCODE_TAB (61)
    var mouseLockKeyName by mutableStateOf("TAB")

    var activeProfile: GameProfile? by mutableStateOf(null)
    var activeButtons by mutableStateOf<List<MappedButton>>(emptyList())

    private val _engineLog = MutableStateFlow<List<String>>(listOf("Engine initialized."))
    val engineLog: StateFlow<List<String>> = _engineLog.asStateFlow()

    fun log(msg: String) {
        val current = _engineLog.value.toMutableList()
        current.add(0, "[${System.currentTimeMillis() % 100000}] $msg")
        if (current.size > 20) current.removeAt(current.size - 1)
        _engineLog.value = current
    }

    fun loadProfile(profileId: Int, context: Context, onComplete: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val profile = db.profileDao().getProfileById(profileId)
            if (profile != null) {
                val buttons = db.profileDao().getButtonsForProfileSync(profileId)
                
                // Set thread-safely
                CoroutineScope(Dispatchers.Main).launch {
                    activeProfile = profile
                    activeButtons = buttons
                    mouseSensitivity = profile.sensitivity
                    log("Profile loaded: ${profile.name} (${buttons.size} keys)")
                    onComplete?.invoke()
                }
            } else {
                log("Profile ID $profileId not found in database.")
            }
        }
    }

    fun toggleMouseLock() {
        isMouseLocked = !isMouseLocked
        log("Mouse lock toggled to: $isMouseLocked")
    }

    fun updateButtonPositions(buttons: List<MappedButton>, context: Context) {
        activeButtons = buttons
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            buttons.forEach {
                db.profileDao().insertButton(it)
            }
            log("Saved updated overlay layouts to Database.")
        }
    }
}
