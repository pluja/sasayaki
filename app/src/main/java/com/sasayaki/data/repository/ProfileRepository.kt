package com.sasayaki.data.repository

import com.sasayaki.data.db.dao.PostProcessingPromptDao
import com.sasayaki.data.db.dao.ProfileDao
import com.sasayaki.data.db.entity.PostProcessingPromptEntity
import com.sasayaki.data.db.entity.ProfileEntity
import com.sasayaki.data.db.entity.toEntity
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.processing.BuiltInPrompt
import com.sasayaki.domain.processing.BuiltInPrompts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val promptDao: PostProcessingPromptDao,
    private val preferencesDataStore: PreferencesDataStore
) {
    val profiles: Flow<List<Profile>> = profileDao.observeProfiles()
        .map { profiles -> profiles.map(ProfileEntity::toDomain) }

    val activeProfile: Flow<Profile?> = profileDao.observeActiveProfile()
        .map { it?.toDomain() }

    suspend fun ensureDefaults() {
        seedBuiltInPrompts()
        if (profileDao.countProfiles() == 0) {
            val prefs = preferencesDataStore.preferences.first()
            profileDao.insert(
                ProfileEntity(
                    name = "Default",
                    isActive = true,
                    asrModel = prefs.asrModel,
                    language = prefs.activeLanguage,
                    llmEnabled = prefs.llmEnabled,
                    llmModel = prefs.llmModel
                )
            )
        } else if (profileDao.activeCount() == 0) {
            profileDao.firstProfileId()?.let { profileDao.activate(it) }
        }
    }

    suspend fun getActiveProfile(): Profile {
        ensureDefaults()
        return profileDao.getActiveProfile()?.toDomain()
            ?: Profile(name = "Default", isActive = true)
    }

    suspend fun getProfile(id: Long): Profile? = profileDao.getProfile(id)?.toDomain()

    suspend fun save(profile: Profile): Long {
        val cleanProfile = profile.copy(name = profile.name.trim().ifBlank { "Untitled profile" })
        return if (cleanProfile.id == 0L) {
            profileDao.insert(cleanProfile.toEntity())
        } else {
            val existing = profileDao.getProfile(cleanProfile.id)
            profileDao.update(cleanProfile.toEntity().copy(createdAt = existing?.createdAt ?: System.currentTimeMillis()))
            cleanProfile.id
        }
    }

    suspend fun activate(id: Long) {
        profileDao.activate(id)
    }

    suspend fun duplicate(profile: Profile) {
        profileDao.insert(
            profile.copy(
                id = 0,
                name = "${profile.name} copy",
                isActive = false
            ).toEntity()
        )
    }

    suspend fun delete(id: Long) {
        val wasActive = profileDao.getProfile(id)?.isActive == true
        profileDao.deleteIfNotLast(id)
        if (wasActive && profileDao.activeCount() == 0) {
            profileDao.firstProfileId()?.let { profileDao.activate(it) }
        }
    }

    private suspend fun seedBuiltInPrompts(): Set<Long> {
        val existingBuiltIns = promptDao.getBuiltIns()

        BUILT_IN_PROMPTS.forEach { seed ->
            val matchingPrompts = existingBuiltIns
                .filter { prompt -> prompt.title == seed.title || prompt.title in seed.legacyTitles }
                .sortedBy(PostProcessingPromptEntity::createdAt)

            val retainedPrompt = matchingPrompts.firstOrNull()
            if (retainedPrompt == null) {
                promptDao.insert(seed.toEntity())
            } else {
                promptDao.update(
                    retainedPrompt.copy(
                        title = seed.title,
                        prompt = seed.prompt,
                        builtIn = true
                    )
                )
                matchingPrompts.drop(1).forEach { duplicate ->
                    promptDao.deleteBuiltInById(duplicate.id)
                }
            }
        }
        return promptDao.getBuiltIns().map { it.id }.toSet()
    }

    private companion object {
        private val BUILT_IN_PROMPTS = BuiltInPrompts.ALL

        private fun BuiltInPrompt.toEntity(): PostProcessingPromptEntity = PostProcessingPromptEntity(
            title = title,
            prompt = prompt,
            builtIn = true
        )
    }
}
