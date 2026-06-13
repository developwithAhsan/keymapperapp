package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [GameProfile::class, MappedButton::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nexus_keymapper_db"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        val coroutineScope = CoroutineScope(Dispatchers.IO)
                        coroutineScope.launch {
                            val dao = getDatabase(context).profileDao()
                            populateDatabase(dao)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateDatabase(dao: ProfileDao) {
            // Pre-populate with typical game presets
            val ffId = dao.insertProfile(
                GameProfile(
                    name = "Free Fire Preset", 
                    sensitivity = 1.2f, 
                    packageId = "com.dts.freefireth"
                )
            )
            val pubgId = dao.insertProfile(
                GameProfile(
                    name = "PUBG Mobile Preset", 
                    sensitivity = 1.0f, 
                    packageId = "com.tencent.ig"
                )
            )
            val codId = dao.insertProfile(
                GameProfile(
                    name = "COD Mobile Preset", 
                    sensitivity = 1.0f, 
                    packageId = "com.activision.callofduty.shooter"
                )
            )
            val customId = dao.insertProfile(
                GameProfile(
                    name = "Custom FPS Preset", 
                    sensitivity = 1.5f, 
                    packageId = ""
                )
            )

            // Button mappings keyCodes reference:
            // LClick = 1, RClick = 2, Space = 62, C = 31, R = 46, Shift = 59, 1 = 8, 2 = 9, 3 = 10, Tab = 61 (Tab is mouse unlock / toggle!)
            
            // Buttons for Free Fire
            dao.insertButton(MappedButton(profileId = ffId.toInt(), label = "Look/Aim Control", keyChar = "Mouse_Look", keyCode = 0, type = ButtonType.LOOK_AIM, xPercent = 0.65f, yPercent = 0.50f, sizeDp = 180))
            dao.insertButton(MappedButton(profileId = ffId.toInt(), label = "Fire", keyChar = "LClick", keyCode = 1, type = ButtonType.TAP, xPercent = 0.83f, yPercent = 0.72f))
            dao.insertButton(MappedButton(profileId = ffId.toInt(), label = "Scope Zoom", keyChar = "RClick", keyCode = 2, type = ButtonType.TAP, xPercent = 0.90f, yPercent = 0.35f))
            dao.insertButton(MappedButton(profileId = ffId.toInt(), label = "Jump", keyChar = "Space", keyCode = 62, type = ButtonType.TAP, xPercent = 0.94f, yPercent = 0.55f))
            dao.insertButton(MappedButton(profileId = ffId.toInt(), label = "Crouch", keyChar = "C", keyCode = 31, type = ButtonType.TAP, xPercent = 0.94f, yPercent = 0.80f))
            dao.insertButton(MappedButton(profileId = ffId.toInt(), label = "Reload", keyChar = "R", keyCode = 46, type = ButtonType.TAP, xPercent = 0.76f, yPercent = 0.92f))
            dao.insertButton(MappedButton(profileId = ffId.toInt(), label = "Sprint", keyChar = "Shift", keyCode = 59, type = ButtonType.TAP, xPercent = 0.15f, yPercent = 0.30f))

            // Buttons for PUBG Mobile
            dao.insertButton(MappedButton(profileId = pubgId.toInt(), label = "Look/Aim Control", keyChar = "Mouse_Look", keyCode = 0, type = ButtonType.LOOK_AIM, xPercent = 0.70f, yPercent = 0.50f, sizeDp = 180))
            dao.insertButton(MappedButton(profileId = pubgId.toInt(), label = "Fire", keyChar = "LClick", keyCode = 1, type = ButtonType.TAP, xPercent = 0.88f, yPercent = 0.66f))
            dao.insertButton(MappedButton(profileId = pubgId.toInt(), label = "Scope", keyChar = "RClick", keyCode = 2, type = ButtonType.TAP, xPercent = 0.84f, yPercent = 0.30f))
            dao.insertButton(MappedButton(profileId = pubgId.toInt(), label = "Jump", keyChar = "Space", keyCode = 62, type = ButtonType.TAP, xPercent = 0.95f, yPercent = 0.58f))
            dao.insertButton(MappedButton(profileId = pubgId.toInt(), label = "Reload", keyChar = "R", keyCode = 46, type = ButtonType.TAP, xPercent = 0.76f, yPercent = 0.85f))

            // Buttons for Custom FPS Preset
            dao.insertButton(MappedButton(profileId = customId.toInt(), label = "Look/Aim Control", keyChar = "Mouse_Look", keyCode = 0, type = ButtonType.LOOK_AIM, xPercent = 0.65f, yPercent = 0.50f, sizeDp = 200))
            dao.insertButton(MappedButton(profileId = customId.toInt(), label = "Shoot", keyChar = "LClick", keyCode = 1, type = ButtonType.TAP, xPercent = 0.80f, yPercent = 0.70f))
        }
    }
}
