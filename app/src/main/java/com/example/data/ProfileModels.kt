package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_profiles")
data class GameProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sensitivity: Float = 1.0f,
    val packageId: String = ""
)

@Entity(tableName = "mapped_buttons")
data class MappedButton(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: Int,
    val label: String, // e.g. "Fire", "Aim", "Reload", "Jump", "Custom"
    val keyChar: String, // e.g. "F", "RClick", "R", "Space"
    val keyCode: Int, // e.g. KeyEvent.KEYCODE_F
    val type: ButtonType = ButtonType.TAP, // TAP, LOOK_AIM, MACRO
    val xPercent: Float, // horizontal relative coordinate (0.0 to 1.0)
    val yPercent: Float, // vertical relative coordinate (0.0 to 1.0)
    val sizeDp: Int = 48,
    val macroSequence: String = "" // e.g. "tap_0.2_0.3;delay_100;tap_0.5_0.5"
)

enum class ButtonType {
    TAP,
    LOOK_AIM, // Mouse look container
    MACRO
}
