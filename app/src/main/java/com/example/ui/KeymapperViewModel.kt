package com.example.ui

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.KeymapperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class KeymapperViewModel(private val repository: ProfileRepository) : ViewModel() {

    // Profiles List State
    val profiles: StateFlow<List<GameProfile>> = repository.allProfilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently selected/edited profile
    private val _selectedProfile = MutableStateFlow<GameProfile?>(null)
    val selectedProfile: StateFlow<GameProfile?> = _selectedProfile.asStateFlow()

    // Active loaded buttons for current profile
    private val _editedButtons = MutableStateFlow<List<MappedButton>>(emptyList())
    val editedButtons: StateFlow<List<MappedButton>> = _editedButtons.asStateFlow()

    // Hardware Peripheral connection markers
    private val _keyboardConnected = MutableStateFlow(false)
    val keyboardConnected = _keyboardConnected.asStateFlow()

    private val _mouseConnected = MutableStateFlow(false)
    val mouseConnected = _mouseConnected.asStateFlow()

    private val _isChromebookMode = MutableStateFlow(true)
    val isChromebookMode = _isChromebookMode.asStateFlow()

    fun setChromebookMode(enabled: Boolean) {
        _isChromebookMode.value = enabled
    }

    fun selectProfileForEditing(profile: GameProfile) {
        _selectedProfile.value = profile
        viewModelScope.launch(Dispatchers.IO) {
            val buttons = repository.getButtonsForProfileSync(profile.id)
            _editedButtons.value = buttons
        }
    }

    fun makeProfileActive(profile: GameProfile, context: Context) {
        KeymapperEngine.loadProfile(profile.id, context) {
            // Callback completed
        }
    }

    fun updateSensitivity(profile: GameProfile, sensitivity: Float, context: Context) {
        val updated = profile.copy(sensitivity = sensitivity)
        _selectedProfile.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertProfile(updated)
            // Save inside engine too if active
            if (KeymapperEngine.activeProfile?.id == profile.id) {
                KeymapperEngine.mouseSensitivity = sensitivity
            }
        }
    }

    fun createNewProfile(name: String, packageId: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val profileId = repository.insertProfile(GameProfile(name = name, packageId = packageId))
            // Create a default aim and shoot mapping for custom profiles
            repository.insertButton(MappedButton(
                profileId = profileId,
                label = "Look Control",
                keyChar = "Mouse_Look",
                keyCode = 0,
                type = ButtonType.LOOK_AIM,
                xPercent = 0.65f,
                yPercent = 0.50f,
                sizeDp = 180
            ))
            repository.insertButton(MappedButton(
                profileId = profileId,
                label = "Fire Trigger",
                keyChar = "LClick",
                keyCode = 1,
                type = ButtonType.TAP,
                xPercent = 0.82f,
                yPercent = 0.70f
            ))
            KeymapperEngine.log("Profile '$name' created dynamically.")
        }
    }

    fun deleteProfile(profile: GameProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProfile(profile)
            if (KeymapperEngine.activeProfile?.id == profile.id) {
                KeymapperEngine.activeProfile = null
                KeymapperEngine.activeButtons = emptyList()
            }
            if (_selectedProfile.value?.id == profile.id) {
                _selectedProfile.value = null
                _editedButtons.value = emptyList()
            }
            KeymapperEngine.log("Profile '${profile.name}' deleted.")
        }
    }

    fun cloneProfile(profileId: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val originalProfile = repository.getProfileById(profileId) ?: return@launch
            val buttons = repository.getButtonsForProfileSync(profileId)
            
            val newProfileId = repository.insertProfile(
                GameProfile(
                    name = newName,
                    sensitivity = originalProfile.sensitivity,
                    packageId = originalProfile.packageId
                )
            )
            
            buttons.forEach { button ->
                repository.insertButton(
                    button.copy(id = 0, profileId = newProfileId)
                )
            }
            KeymapperEngine.log("Profile cloned as '$newName'.")
        }
    }

    fun renameProfile(profileId: Int, newName: String, newPackageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = repository.getProfileById(profileId) ?: return@launch
            val updated = profile.copy(name = newName, packageId = newPackageId)
            repository.insertProfile(updated)
            
            if (_selectedProfile.value?.id == profileId) {
                _selectedProfile.value = updated
            }
            if (KeymapperEngine.activeProfile?.id == profileId) {
                KeymapperEngine.activeProfile = updated
            }
            KeymapperEngine.log("Profile updated to '$newName'.")
        }
    }

    fun linkProfileToPackage(profileId: Int, packageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Clear other profiles of this packageId to ensure unique mapping
            val allProfiles = profiles.value
            allProfiles.forEach { p ->
                if (p.packageId == packageId && p.id != profileId) {
                    repository.insertProfile(p.copy(packageId = ""))
                }
            }
            
            val profile = repository.getProfileById(profileId) ?: return@launch
            val updated = profile.copy(packageId = packageId)
            repository.insertProfile(updated)
            
            if (_selectedProfile.value?.id == profileId) {
                _selectedProfile.value = updated
            }
            if (KeymapperEngine.activeProfile?.id == profileId) {
                KeymapperEngine.activeProfile = updated
            }
            KeymapperEngine.log("Profile '${profile.name}' linked to game '$packageId'.")
        }
    }

    fun exportProfileToJson(profileId: Int, callback: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getProfileById(profileId) ?: return@launch
                val buttons = repository.getButtonsForProfileSync(profileId)
                
                val root = org.json.JSONObject()
                root.put("name", profile.name)
                root.put("packageId", profile.packageId)
                root.put("sensitivity", profile.sensitivity.toDouble())
                
                val array = org.json.JSONArray()
                buttons.forEach { btn ->
                    val bObj = org.json.JSONObject()
                    bObj.put("label", btn.label)
                    bObj.put("keyChar", btn.keyChar)
                    bObj.put("keyCode", btn.keyCode)
                    bObj.put("type", btn.type.name)
                    bObj.put("xPercent", btn.xPercent.toDouble())
                    bObj.put("yPercent", btn.yPercent.toDouble())
                    bObj.put("sizeDp", btn.sizeDp)
                    bObj.put("macroSequence", btn.macroSequence)
                    array.put(bObj)
                }
                root.put("buttons", array)
                
                val jsonStr = root.toString(4)
                launch(Dispatchers.Main) {
                    callback(jsonStr)
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    callback("")
                }
            }
        }
    }

    fun importProfileFromJson(jsonString: String, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = org.json.JSONObject(jsonString)
                val name = root.optString("name", "Imported Layout")
                val packageId = root.optString("packageId", "")
                val sensitivity = root.optDouble("sensitivity", 1.0).toFloat()
                
                val profileId = repository.insertProfile(
                    GameProfile(
                        name = name,
                        packageId = packageId,
                        sensitivity = sensitivity
                    )
                )
                
                val array = root.optJSONArray("buttons")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val bObj = array.getJSONObject(i)
                        val typeStr = bObj.optString("type", "TAP")
                        val type = try {
                            ButtonType.valueOf(typeStr)
                        } catch (e: Exception) {
                            ButtonType.TAP
                        }
                        
                        repository.insertButton(
                            MappedButton(
                                profileId = profileId,
                                label = bObj.optString("label", "Button"),
                                keyChar = bObj.optString("keyChar", "A"),
                                keyCode = bObj.optInt("keyCode", 29),
                                type = type,
                                xPercent = bObj.optDouble("xPercent", 0.5).toFloat(),
                                yPercent = bObj.optDouble("yPercent", 0.5).toFloat(),
                                sizeDp = bObj.optInt("sizeDp", 48),
                                macroSequence = bObj.optString("macroSequence", "")
                            )
                        )
                    }
                }
                launch(Dispatchers.Main) {
                    callback(true, name)
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    callback(false, e.localizedMessage ?: "Invalid JSON formatting")
                }
            }
        }
    }

    fun updateEditedButtonPositions(buttons: List<MappedButton>) {
        _editedButtons.value = buttons
    }

    fun addNewButtonToProfile(button: MappedButton) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertButton(button)
            // Reload buttons
            val profileId = _selectedProfile.value?.id ?: return@launch
            val updated = repository.getButtonsForProfileSync(profileId)
            _editedButtons.value = updated
            
            // Sync with engine if active
            if (KeymapperEngine.activeProfile?.id == profileId) {
                KeymapperEngine.activeButtons = updated
            }
        }
    }

    fun deleteButtonFromProfile(button: MappedButton) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteButton(button)
            val profileId = _selectedProfile.value?.id ?: return@launch
            val updated = repository.getButtonsForProfileSync(profileId)
            _editedButtons.value = updated
            
            if (KeymapperEngine.activeProfile?.id == profileId) {
                KeymapperEngine.activeButtons = updated
            }
        }
    }

    fun saveAllEditedButtons(context: Context) {
        val buttons = _editedButtons.value
        val profileId = _selectedProfile.value?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            buttons.forEach {
                repository.insertButton(it)
            }
            // Repopulate Engine active parameters if it runs
            if (KeymapperEngine.activeProfile?.id == profileId) {
                KeymapperEngine.activeButtons = buttons
            }
            KeymapperEngine.log("Saved mappings layout for editing profile.")
        }
    }

    /**
     * Poll dynamic connected inputs, verifying USB OTG or Bluetooth status of mice and keyboards.
     */
    fun checkPeripheralsConnected(context: Context) {
        var hasKb = false
        var hasMs = false
        try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
            val ids = inputManager.inputDeviceIds
            for (id in ids) {
                val device = inputManager.getInputDevice(id) ?: continue
                val sources = device.sources
                
                // Track physical keys board
                if ((sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD) {
                    if (device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
                        hasKb = true
                    }
                }
                // Track mouse pointers
                if ((sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                    (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
                    hasMs = true
                }
            }
        } catch (e: Exception) {
            // Ignored, fallback false
        }

        // Chromebook Integrated Hardware Auto-Detection and Toggle Override
        val isChromebookSystem = context.packageManager.hasSystemFeature("org.chromium.arc") ||
                context.packageManager.hasSystemFeature("org.chromium.arc.device_management") ||
                android.os.Build.BRAND.lowercase().contains("chromium") ||
                android.os.Build.DEVICE.lowercase().contains("chromebook")

        if (isChromebookSystem || _isChromebookMode.value) {
            hasKb = true
            hasMs = true
        }

        _keyboardConnected.value = hasKb
        _mouseConnected.value = hasMs
    }
}

class ViewModelFactory(private val repository: ProfileRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KeymapperViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return KeymapperViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
