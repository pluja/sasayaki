package com.sasayaki.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sasayaki.ui.theme.SasayakiLogo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** StoreScreenshotTest renders the logo that scripts/store-images.sh turns into the listing icon. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w512dp-h512dp-mdpi", application = Application::class)
class StoreScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun icon() = compose.screenshot("store_icon") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SasayakiLogo(size = 512.dp) }
    }
}
