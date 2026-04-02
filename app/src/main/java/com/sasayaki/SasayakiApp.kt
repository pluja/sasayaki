package com.sasayaki

import android.app.Application
import com.sasayaki.data.preferences.PreferencesDataStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SasayakiApp : Application() {
    @Inject lateinit var preferencesDataStore: PreferencesDataStore

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startupScope.launch {
            preferencesDataStore.runStartupMigrations()
        }
    }
}
