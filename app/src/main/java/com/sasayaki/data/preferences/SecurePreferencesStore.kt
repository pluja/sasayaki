package com.sasayaki.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val FILE_NAME = "secure_settings"
        private const val ASR_API_KEY = "asr_api_key"
        private const val LLM_API_KEY = "llm_api_key"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getAsrApiKey(): String = sharedPreferences.getString(ASR_API_KEY, "") ?: ""

    fun getLlmApiKey(): String = sharedPreferences.getString(LLM_API_KEY, "") ?: ""

    fun updateAsrApiKey(apiKey: String) {
        sharedPreferences.edit().putString(ASR_API_KEY, apiKey).apply()
    }

    fun updateLlmApiKey(apiKey: String) {
        sharedPreferences.edit().putString(LLM_API_KEY, apiKey).apply()
    }
}
