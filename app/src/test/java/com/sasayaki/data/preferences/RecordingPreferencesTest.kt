package com.sasayaki.data.preferences

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RecordingPreferencesTest {
    @Test
    fun `keeping the screen on defaults to enabled and remembers changes`() = runBlocking {
        withTimeout(5000) {
            val context = RuntimeEnvironment.getApplication()
            val secure = SecurePreferencesStore(context)
            val store = PreferencesDataStore(context, secure)
            assertTrue(UserPreferences().keepScreenOnWhileRecording)
            assertTrue(store.preferences.first().keepScreenOnWhileRecording)

            store.setKeepScreenOnWhileRecording(false)
            val reopened = PreferencesDataStore(context, secure)
            assertFalse(reopened.preferences.first().keepScreenOnWhileRecording)

            reopened.setKeepScreenOnWhileRecording(true)
            assertTrue(store.preferences.first().keepScreenOnWhileRecording)
        }
    }
}
