package com.sasayaki.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sasayaki.R
import com.sasayaki.ui.components.SegmentedGroup
import com.sasayaki.ui.components.SettingsPage
import com.sasayaki.ui.components.pageIntro
import com.sasayaki.ui.components.segmentedItemColors

@Immutable
data class OpenSourceLibrary(val name: String, @StringRes val license: Int, val url: String)

private val APACHE_2 = R.string.licenses_apache_2

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
    val intro = stringResource(R.string.licenses_intro)
    SettingsPage(title = stringResource(R.string.licenses_title), onBack = onBack) {
        pageIntro(intro)
        item(key = "libraries") {
            SegmentedGroup(
                items = libraries.map { library ->
                    { shapes ->
                        SegmentedListItem(
                            onClick = { onOpen(library.url) },
                            shapes = shapes,
                            colors = segmentedItemColors(),
                            supportingContent = { Text(stringResource(library.license)) }
                        ) {
                            Text(library.name)
                        }
                    }
                }
            )
        }
    }
}
