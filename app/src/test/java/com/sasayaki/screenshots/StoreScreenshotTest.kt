@file:Suppress("ktlint:standard:max-line-length") // Prose and fixture rows read better unwrapped.

package com.sasayaki.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sasayaki.data.db.entity.DictationStats
import com.sasayaki.data.db.entity.DictationSummary
import com.sasayaki.data.repository.ConnectionResult
import com.sasayaki.domain.model.PostProcessingPrompt
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.TextReplacementRule
import com.sasayaki.domain.provider.ModelInfo
import com.sasayaki.domain.provider.ModelKind
import com.sasayaki.domain.provider.Provider
import com.sasayaki.service.bubble.BubbleActions
import com.sasayaki.service.bubble.BubbleAppearance
import com.sasayaki.service.bubble.BubbleContent
import com.sasayaki.service.bubble.BubbleMode
import com.sasayaki.service.bubble.BubblePhase
import com.sasayaki.service.bubble.BubbleTheme
import com.sasayaki.service.bubble.BubbleUiState
import com.sasayaki.service.voice.VoiceInputPanel
import com.sasayaki.ui.history.HistoryScreen
import com.sasayaki.ui.history.HistoryUiState
import com.sasayaki.ui.history.groupByDay
import com.sasayaki.ui.home.HomeScreen
import com.sasayaki.ui.home.HomeUiState
import com.sasayaki.ui.profiles.ProfileEditorNavigation
import com.sasayaki.ui.profiles.ProfileEditorScreen
import com.sasayaki.ui.profiles.ProfileEditorUiState
import com.sasayaki.ui.settings.ProviderEditorActions
import com.sasayaki.ui.settings.ProviderEditorScreen
import com.sasayaki.ui.settings.ProviderEditorUiState
import com.sasayaki.ui.settings.ProviderForm
import com.sasayaki.ui.theme.SasayakiLogo
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * StoreScreenshotTest renders the scenes behind the app store screenshots; scripts/store-images.sh
 * frames them with captions. App names stay generic so no real brand appears in the listing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class StoreScreenshotTest {
    @get:Rule
    val compose = androidx.compose.ui.test.junit4.v2.createComposeRule()

    private val now = System.currentTimeMillis()
    private val levels = List(40) { index -> listOf(0.2f, 0.55f, 0.9f, 0.4f, 0.7f, 0.3f, 1f, 0.5f)[index % 8] }
    private val bubbleActions = BubbleActions({}, {}, {}, {}, {}, {}, {})
    private val profiles = listOf(
        Profile(id = 1, name = "Chat", isActive = true, language = "en", llmEnabled = true),
        Profile(id = 2, name = "Email", llmEnabled = true, rewriteMode = RewriteMode.POLISH),
        Profile(id = 3, name = "Notes")
    )
    private val providers = listOf(
        Provider(1, "Home server", "https://asr.home.arpa", speechModel = "faster-whisper-large-v3-turbo"),
        Provider(2, "Local LLM", "https://llm.home.arpa", cleanupModel = "gpt-oss-20b")
    )
    private val dictations = listOf(
        DictationSummary(1, "Booked! Table for four at 7:30, see you there.", 9, now - 300_000, "Messages", null, 4_000, "SUCCESS", null, 1, null),
        DictationSummary(
            2, "Hi Marta, thanks for the notes. I'll review the draft tonight and send my comments tomorrow morning.", 17,
            now - 3_900_000, "Mail", null, 7_000, "SUCCESS", null, 2, null
        ),
        DictationSummary(3, "Ideas for the trip: book the train early, ask Sam about the cabin, pack the rain jackets.", 17, now - 9_000_000, "Notes", null, 8_000, "SUCCESS", null, 3, null),
        DictationSummary(4, "Can you pick up bread and oat milk on the way home?", 11, now - 90_000_000, "Messages", null, 4_000, "SUCCESS", null, 1, null),
        DictationSummary(5, "Moving the stand-up to 10 tomorrow so everyone can make it.", 11, now - 95_000_000, "Chat", null, 4_500, "SUCCESS", null, 1, null)
    )

    @Test
    fun bubble() = compose.screenshot("store_1_bubble") {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChatThread(modifier = Modifier.weight(1f), draft = "")
            Box(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
                BubbleTheme(BubbleAppearance()) {
                    Box(Modifier.size(BubbleMode.RECORDING.windowWidth, BubbleMode.RECORDING.windowHeight)) {
                        BubbleContent(
                            BubbleUiState(BubblePhase.Recording(paused = false), elapsedSeconds = 6, profileName = "Chat"),
                            { levels },
                            bubbleActions,
                            {}
                        )
                    }
                }
            }
            FakeKeyboard()
        }
    }

    @Test
    fun keyboard() = compose.screenshot("store_2_keyboard") {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChatThread(modifier = Modifier.weight(1f), draft = "Booked! Table for four at 7:30, see you there.")
            VoiceInputPanel(
                BubbleUiState(BubblePhase.Idle, justInserted = true),
                { levels },
                profiles,
                bubbleActions,
                BubbleAppearance(),
                onSelectProfile = {},
                onSwitchKeyboard = {}
            )
        }
    }

    @Test
    fun home() = compose.screenshot("store_3_home") {
        HomeScreen(
            state = HomeUiState(
                loaded = true,
                serviceRunning = true,
                speechProviderConfigured = true,
                profiles = profiles,
                today = DictationStats(23, 1_184, 420_000),
                lifetime = DictationStats(860, 38_900, 15_600_000),
                recent = dictations.take(3)
            ),
            setupSteps = emptyList(),
            voiceInputEnabled = true,
            restrictedSettingsLikely = false,
            snackbarHostState = SnackbarHostState(),
            onStartService = {},
            onStopService = {},
            onSelectProfile = {},
            onOpenProfiles = {},
            onOpenHistory = {},
            onOpenDictation = {},
            onCopy = {}
        )
    }

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun style() = compose.screenshot("store_4_style") {
        ProfileEditorScreen(
            profile = profiles.first(),
            state = ProfileEditorUiState(
                providers = providers,
                rules = listOf(TextReplacementRule(1, "", "sasa yaki", "Sasayaki")),
                customPrompts = listOf(PostProcessingPrompt(10, "Digits", "Write numbers as digits.")),
                profileCount = 3,
                active = true
            ),
            navigation = ProfileEditorNavigation({}, {}, {}, {}, {}),
            onUpdate = {},
            onActivate = {},
            onDuplicate = {},
            onDelete = {},
            onLoadModels = {}
        )
    }

    @Test
    fun provider() = compose.screenshot("store_5_provider") {
        val models = listOf(
            ModelInfo("faster-whisper-large-v3-turbo", ModelKind.SPEECH),
            ModelInfo("parakeet-tdt-0.6b-v3", ModelKind.SPEECH),
            ModelInfo("gpt-oss-20b", ModelKind.CHAT),
            ModelInfo("gemma-3-12b-it", ModelKind.CHAT)
        )
        ProviderEditorScreen(
            isNew = false,
            form = ProviderForm("Home server", "https://asr.home.arpa", "", "faster-whisper-large-v3-turbo", "gpt-oss-20b"),
            state = ProviderEditorUiState(loaded = true, result = ConnectionResult.Connected(models), models = models),
            actions = ProviderEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {})
        )
    }

    @Test
    fun history() = compose.screenshot("store_6_history") {
        val zone = ZoneId.systemDefault()
        HistoryScreen(
            state = HistoryUiState(
                loaded = true,
                hasHistory = true,
                groups = groupByDay(dictations, zone, LocalDate.now(zone), "", emptySet())
            ),
            snackbarHostState = SnackbarHostState(),
            onQueryChange = {},
            onCopy = {},
            onRetry = {},
            onDelete = {},
            onClearAll = {},
            onEnableHistory = {},
            loadRawText = { null }
        )
    }

    @Test
    @Config(qualifiers = "w512dp-h512dp-mdpi")
    fun icon() = compose.screenshot("store_icon") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SasayakiLogo(size = 512.dp) }
    }
}

@Composable
private fun ChatThread(draft: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("M", color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("Marta", style = MaterialTheme.typography.titleMedium)
                Text("online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
        ) {
            Message("Are we still on for dinner on Saturday?", mine = false)
            Message("Yes! The new Thai place by the park?", mine = true)
            Message("Perfect. Could you book a table for four?", mine = false)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    draft.ifEmpty { "Message" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (draft.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun Message(text: String, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun FakeKeyboard() {
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = (index * 14).dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                row.forEach { key -> Key(key.toString(), Modifier.weight(1f)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Key("?123", Modifier.weight(1.5f))
            Key(",", Modifier.weight(1f))
            Key("", Modifier.weight(5f))
            Key(".", Modifier.weight(1f))
            Key("⏎", Modifier.weight(1.5f))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Key(label: String, modifier: Modifier) {
    Surface(modifier = modifier.height(46.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceBright) {
        Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.titleMedium) }
    }
}
