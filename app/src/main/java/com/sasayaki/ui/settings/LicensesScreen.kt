package com.sasayaki.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import com.sasayaki.ui.components.SegmentedGroup
import com.sasayaki.ui.components.SettingsPage
import com.sasayaki.ui.components.pageIntro
import com.sasayaki.ui.components.segmentedItemColors

@Immutable
data class OpenSourceLibrary(val name: String, val license: String, val url: String)

private const val APACHE_2 = "Apache License 2.0"

// Keep in step with the implementation dependencies in app/build.gradle.kts.
val openSourceLibraries = listOf(
    OpenSourceLibrary("Android Jetpack (AndroidX)", APACHE_2, "https://developer.android.com/jetpack/androidx"),
    OpenSourceLibrary("Jetpack Compose and Material 3", APACHE_2, "https://developer.android.com/compose"),
    OpenSourceLibrary("Room", APACHE_2, "https://developer.android.com/jetpack/androidx/releases/room"),
    OpenSourceLibrary("DataStore", APACHE_2, "https://developer.android.com/jetpack/androidx/releases/datastore"),
    OpenSourceLibrary("Dagger and Hilt", APACHE_2, "https://github.com/google/dagger"),
    OpenSourceLibrary("Kotlin and kotlinx.coroutines", APACHE_2, "https://github.com/JetBrains/kotlin"),
    OpenSourceLibrary("kotlinx.serialization", APACHE_2, "https://github.com/Kotlin/kotlinx.serialization"),
    OpenSourceLibrary("OkHttp and Okio", APACHE_2, "https://github.com/square/okhttp"),
    OpenSourceLibrary("Retrofit", APACHE_2, "https://github.com/square/retrofit"),
    OpenSourceLibrary("Gson", APACHE_2, "https://github.com/google/gson"),
    OpenSourceLibrary("Material Symbols", APACHE_2, "https://github.com/google/material-design-icons")
)

@Composable
fun LicensesRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    LicensesScreen(
        libraries = openSourceLibraries,
        onOpen = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        onBack = onBack
    )
}

@Composable
fun LicensesScreen(libraries: List<OpenSourceLibrary>, onOpen: (String) -> Unit, onBack: () -> Unit) {
    SettingsPage(title = "Open-source licenses", onBack = onBack) {
        pageIntro("Sasayaki is free software under the GPL-3.0, built on these open-source projects.")
        item(key = "libraries") {
            SegmentedGroup(
                items = libraries.map { library ->
                    { shapes ->
                        SegmentedListItem(
                            onClick = { onOpen(library.url) },
                            shapes = shapes,
                            colors = segmentedItemColors(),
                            supportingContent = { Text(library.license) }
                        ) {
                            Text(library.name)
                        }
                    }
                }
            )
        }
    }
}
