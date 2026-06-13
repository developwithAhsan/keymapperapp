package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM game_profiles ORDER BY id ASC")
    fun getAllProfilesFlow(): Flow<List<GameProfile>>

    @Query("SELECT * FROM game_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Int): GameProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: GameProfile): Long

    @Delete
    suspend fun deleteProfile(profile: GameProfile)

    @Query("SELECT * FROM mapped_buttons WHERE profileId = :profileId")
    fun getButtonsForProfileFlow(profileId: Int): Flow<List<MappedButton>>

    @Query("SELECT * FROM mapped_buttons WHERE profileId = :profileId")
    suspend fun getButtonsForProfileSync(profileId: Int): List<MappedButton>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertButton(button: MappedButton): Long

    @Delete
    suspend fun deleteButton(button: MappedButton)

    @Query("DELETE FROM mapped_buttons WHERE profileId = :profileId")
    suspend fun deleteButtonsForProfile(profileId: Int)
}
