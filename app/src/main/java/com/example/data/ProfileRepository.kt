package com.example.data

import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val profileDao: ProfileDao) {

    val allProfilesFlow: Flow<List<GameProfile>> = profileDao.getAllProfilesFlow()

    fun getButtonsForProfileFlow(profileId: Int): Flow<List<MappedButton>> =
        profileDao.getButtonsForProfileFlow(profileId)

    suspend fun getProfileById(id: Int): GameProfile? =
        profileDao.getProfileById(id)

    suspend fun getButtonsForProfileSync(profileId: Int): List<MappedButton> =
        profileDao.getButtonsForProfileSync(profileId)

    suspend fun insertProfile(profile: GameProfile): Int {
        return profileDao.insertProfile(profile).toInt()
    }

    suspend fun deleteProfile(profile: GameProfile) {
        profileDao.deleteButtonsForProfile(profile.id)
        profileDao.deleteProfile(profile)
    }

    suspend fun insertButton(button: MappedButton): Int {
        return profileDao.insertButton(button).toInt()
    }

    suspend fun deleteButton(button: MappedButton) {
        profileDao.deleteButton(button)
    }

    suspend fun deleteButtonsForProfile(profileId: Int) {
        profileDao.deleteButtonsForProfile(profileId)
    }
}
