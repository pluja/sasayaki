package com.sasayaki.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.ui.common.MICROPHONE_KEY
import com.sasayaki.ui.common.SetupItem
import com.sasayaki.ui.common.rememberPermissionItems
import com.sasayaki.ui.common.rememberRestrictedSettingsLikely
import com.sasayaki.ui.common.rememberVoiceInputItem
import com.sasayaki.ui.components.SetupGroup
import com.sasayaki.ui.settings.ConnectionResultCard
import com.sasayaki.ui.settings.DefaultModels
import com.sasayaki.ui.settings.ProviderConnectionForm
import com.sasayaki.ui.settings.ProviderEditorActions
import com.sasayaki.ui.settings.ProviderEditorUiState
import com.sasayaki.ui.settings.ProviderForm
import com.sasayaki.ui.settings.providerEditorActions
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.SasayakiLogo
import com.sasayaki.ui.theme.Spacing

enum class OnboardingStep { WELCOME, PROVIDER, METHOD, PERMISSIONS, TRY }

enum class DictationMethod { BUBBLE, KEYBOARD }

@Immutable
data class OnboardingSetup(
    val method: DictationMethod,
    val items: List<SetupItem>,
    val restrictedSettingsLikely: Boolean,
    val bubbleRunning: Boolean
) {
    val ready: Boolean get() = items.none { it.required && !it.done }
}

class OnboardingActions(
    val onStep: (OnboardingStep) -> Unit,
    val onMethod: (DictationMethod) -> Unit,
    val onSaveProvider: () -> Unit,
    val onFinish: () -> Unit
)

@Composable
fun OnboardingRoute(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var method by rememberSaveable { mutableStateOf(DictationMethod.BUBBLE) }
    val providerState by viewModel.providerState.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val bubbleItems = rememberPermissionItems()
    val voiceInput = rememberVoiceInputItem()
    val setup = OnboardingSetup(
        method = method,
        items = when (method) {
            DictationMethod.BUBBLE -> bubbleItems
            DictationMethod.KEYBOARD -> bubbleItems.filter { it.key == MICROPHONE_KEY } + voiceInput
        },
        restrictedSettingsLikely = rememberRestrictedSettingsLikely(),
        bubbleRunning = serviceRunning
    )

    LaunchedEffect(step, setup.ready, method) {
        if (step == OnboardingStep.TRY && method == DictationMethod.BUBBLE && setup.ready && !serviceRunning) viewModel.startBubble()
    }

    OnboardingScreen(
        step = step,
        providerForm = viewModel.draft.form,
        providerState = providerState,
        providerActions = providerEditorActions(viewModel.draft, onAdd = {}, onDelete = {}, onBack = {}),
        setup = setup,
        actions = OnboardingActions(
            onStep = { step = it },
            onMethod = { method = it },
            onSaveProvider = { viewModel.saveProvider { step = OnboardingStep.METHOD } },
            onFinish = { viewModel.complete(onFinished) }
        )
    )
}

@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    providerForm: ProviderForm,
    providerState: ProviderEditorUiState,
    providerActions: ProviderEditorActions,
    setup: OnboardingSetup,
    actions: OnboardingActions
) {
    val previous = OnboardingStep.entries.getOrNull(step.ordinal - 1)
    BackHandler(enabled = previous != null) { previous?.let(actions.onStep) }
    val progress by animateFloatAsState(
        targetValue = (step.ordinal + 1f) / OnboardingStep.entries.size,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "progress"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            if (step != OnboardingStep.WELCOME) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xxl, start = Spacing.xs, end = Spacing.xl),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { previous?.let(actions.onStep) }) {
                        Icon(SasayakiIcons.ArrowBack, contentDescription = "Back")
                    }
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Step ${step.ordinal + 1} of ${OnboardingStep.entries.size}" }
                    )
                }
            }
        },
        bottomBar = { OnboardingBottomBar(step, providerForm, setup, actions) }
    ) { padding ->
        val forward = MaterialTheme.motionScheme.defaultEffectsSpec<IntOffset>()
        val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(forward) { it / 3 * direction } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(forward) { -it / 3 * direction } + fadeOut(fade))
            },
            label = "onboarding"
        ) { current ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screenHorizontal,
                    end = Spacing.screenHorizontal,
                    top = padding.calculateTopPadding() + Spacing.l,
                    bottom = padding.calculateBottomPadding() + Spacing.l
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.l)
            ) {
                when (current) {
                    OnboardingStep.WELCOME -> welcome()
                    OnboardingStep.PROVIDER -> provider(providerForm, providerState, providerActions)
                    OnboardingStep.METHOD -> methodStep(setup.method, actions.onMethod)
                    OnboardingStep.PERMISSIONS -> permissionsStep(setup)
                    OnboardingStep.TRY -> tryIt(setup)
                }
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(step: OnboardingStep, providerForm: ProviderForm, setup: OnboardingSetup, actions: OnboardingActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Spacing.xl, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (step) {
            OnboardingStep.WELCOME -> PrimaryButton("Get started") { actions.onStep(OnboardingStep.PROVIDER) }

            OnboardingStep.PROVIDER -> {
                val blocker = when {
                    providerForm.url.isBlank() -> "Enter your server address to continue."
                    providerForm.speechModel == null -> "Test the connection or pick a speech to text model to continue."
                    else -> null
                }
                blocker?.let { BlockerText(it) }
                PrimaryButton("Continue", enabled = blocker == null, onClick = actions.onSaveProvider)
                TextButton(onClick = { actions.onStep(OnboardingStep.METHOD) }) { Text("Set up later") }
            }

            OnboardingStep.METHOD -> PrimaryButton("Continue") { actions.onStep(OnboardingStep.PERMISSIONS) }

            OnboardingStep.PERMISSIONS -> {
                PrimaryButton("Continue", enabled = setup.ready) { actions.onStep(OnboardingStep.TRY) }
                TextButton(onClick = { actions.onStep(OnboardingStep.TRY) }) { Text("Skip for now") }
            }

            OnboardingStep.TRY -> PrimaryButton("Done", onClick = actions.onFinish)
        }
    }
}

@Composable
private fun BlockerText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = Spacing.xs)
    )
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.MediumContentPadding
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

private fun LazyListScope.stepHeader(title: String, body: String) {
    item(key = "header") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(horizontal = Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun LazyListScope.welcome() {
    item(key = "hero") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xxxl, bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            WelcomeMark()
            Text(
                "Speak, and it's typed.",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center
            )
            Text(
                "Sasayaki turns your voice into text in any app, using speech models on servers you choose.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.l)
            )
        }
    }
    item(key = "points") {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
            Column(modifier = Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                WelcomePoint(SasayakiIcons.Keyboard, "Works in every app", "Dictate from a floating bubble or your keyboard's mic key.")
                WelcomePoint(SasayakiIcons.Shield, "Your servers, your words", "Recordings go only to the servers you add.")
                WelcomePoint(
                    SasayakiIcons.AutoAwesome,
                    "Optional cleanup",
                    "Add a chat model and Sasayaki drops filler words and applies your self-corrections."
                )
            }
        }
    }
}

@Composable
private fun WelcomeMark() {
    val transition = rememberInfiniteTransition(label = "mark")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing)),
        label = "markRotation"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(152.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        SasayakiLogo(size = 104.dp)
    }
}

@Composable
private fun WelcomePoint(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun LazyListScope.provider(form: ProviderForm, state: ProviderEditorUiState, actions: ProviderEditorActions) {
    stepHeader(
        "Connect speech to text",
        "Add the server that turns your voice into text. Any OpenAI-compatible API works: your own or a hosted one."
    )
    item(key = "form") { ProviderConnectionForm(form, state, actions) }
    item(key = "test") {
        Button(
            onClick = actions.onTest,
            enabled = !state.testing && form.url.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.testing) {
                LoadingIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Test connection")
            }
        }
    }
    state.result?.let { result -> item(key = "result") { ConnectionResultCard(result) } }
    item(key = "models") { DefaultModels(form, state, actions) }
    item(key = "cleanup") { CleanupNote(cleanupChosen = form.cleanupModel != null) }
}

@Composable
private fun CleanupNote(cleanupChosen: Boolean) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(modifier = Modifier.padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(SasayakiIcons.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = if (cleanupChosen) {
                    "Cleanup is on: a chat model will tidy each dictation before it is typed."
                } else {
                    "Cleanup is optional. It needs a chat model (an LLM) to remove filler words and fix " +
                        "self-corrections. Pick one under Cleanup above, or add it later in Settings."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun LazyListScope.methodStep(selected: DictationMethod, onSelect: (DictationMethod) -> Unit) {
    stepHeader("How do you want to dictate?", "Pick one to start with. You can turn on the other later in Settings.")
    item(key = "methods") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.selectableGroup()) {
            MethodCard(
                icon = SasayakiIcons.ChatBubble,
                title = "Floating bubble",
                body = "Appears whenever a keyboard opens, in any app. Tap it to dictate into the field you are in.",
                needs = "Needs: display over other apps, and accessibility access to type the text.",
                selected = selected == DictationMethod.BUBBLE,
                onClick = { onSelect(DictationMethod.BUBBLE) }
            )
            MethodCard(
                icon = SasayakiIcons.Keyboard,
                title = "Keyboard mic key",
                body = "Keyboards like HeliBoard and FlorisBoard open Sasayaki from their mic key, right in the keyboard area.",
                needs = "Needs: only the microphone.",
                selected = selected == DictationMethod.KEYBOARD,
                onClick = { onSelect(DictationMethod.KEYBOARD) }
            )
        }
    }
}

@Composable
private fun MethodCard(icon: ImageVector, title: String, body: String, needs: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceBright,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
    ) {
        Row(modifier = Modifier.padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(top = Spacing.xxs))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Text(needs, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.78f))
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

private fun LazyListScope.permissionsStep(setup: OnboardingSetup) {
    when (setup.method) {
        DictationMethod.BUBBLE -> stepHeader(
            "Let Sasayaki listen and type",
            "These let the bubble record when you tap it and type the result where you are."
        )

        DictationMethod.KEYBOARD -> stepHeader(
            "Connect your keyboard",
            "Allow the microphone, then turn on Sasayaki voice input so your keyboard's mic key can open it."
        )
    }
    item(key = "permissions") { SetupGroup(items = setup.items, restrictedSettingsLikely = setup.restrictedSettingsLikely) }
}

private fun LazyListScope.tryIt(setup: OnboardingSetup) {
    stepHeader(
        "Try it",
        when {
            !setup.ready -> "Finish the previous step to start dictating. You can also do it later from Home."
            setup.method == DictationMethod.KEYBOARD -> "Tap the field below, then the mic key on your keyboard and say something."
            else -> "Tap the field below, then tap the bubble that appears and say something."
        }
    )
    item(key = "field") { TryField(enabled = setup.ready) }
    if (setup.method == DictationMethod.BUBBLE && setup.ready && !setup.bubbleRunning) {
        item(key = "starting") {
            Text("Starting the bubble…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TryField(enabled: Boolean) {
    var value by rememberSaveable { mutableStateOf("") }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.l),
            placeholder = { Text("Your words appear here") },
            minLines = 5
        )
    }
}
