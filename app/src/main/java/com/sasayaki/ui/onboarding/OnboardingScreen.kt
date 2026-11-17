package com.sasayaki.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.R
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
                val stepDescription = stringResource(R.string.onboarding_step_progress, step.ordinal + 1, OnboardingStep.entries.size)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xxl, start = Spacing.xs, end = Spacing.xl),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { previous?.let(actions.onStep) }) {
                        Icon(SasayakiIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = stepDescription }
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
            OnboardingStep.WELCOME -> PrimaryButton(stringResource(R.string.onboarding_get_started)) {
                actions.onStep(OnboardingStep.PROVIDER)
            }

            OnboardingStep.PROVIDER -> {
                val blocker = when {
                    providerForm.url.isBlank() -> R.string.onboarding_provider_needs_url
                    providerForm.speechModel == null -> R.string.onboarding_provider_needs_model
                    else -> null
                }
                blocker?.let { BlockerText(stringResource(it)) }
                PrimaryButton(stringResource(R.string.action_continue), enabled = blocker == null, onClick = actions.onSaveProvider)
                TextButton(onClick = { actions.onStep(OnboardingStep.METHOD) }) { Text(stringResource(R.string.onboarding_set_up_later)) }
            }

            OnboardingStep.METHOD -> PrimaryButton(stringResource(R.string.action_continue)) { actions.onStep(OnboardingStep.PERMISSIONS) }

            OnboardingStep.PERMISSIONS -> {
                PrimaryButton(stringResource(R.string.action_continue), enabled = setup.ready) { actions.onStep(OnboardingStep.TRY) }
                TextButton(onClick = { actions.onStep(OnboardingStep.TRY) }) { Text(stringResource(R.string.onboarding_skip_for_now)) }
            }

            OnboardingStep.TRY -> PrimaryButton(stringResource(R.string.onboarding_done), onClick = actions.onFinish)
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

private fun LazyListScope.stepHeader(@StringRes title: Int, @StringRes body: Int) {
    item(key = "header") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(horizontal = Spacing.xs)) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.onboarding_welcome_body),
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
                WelcomePoint(
                    SasayakiIcons.Keyboard,
                    stringResource(R.string.onboarding_point_apps_title),
                    stringResource(R.string.onboarding_point_apps_body)
                )
                WelcomePoint(
                    SasayakiIcons.Shield,
                    stringResource(R.string.onboarding_point_servers_title),
                    stringResource(R.string.onboarding_point_servers_body)
                )
                WelcomePoint(
                    SasayakiIcons.AutoAwesome,
                    stringResource(R.string.onboarding_point_cleanup_title),
                    stringResource(R.string.onboarding_point_cleanup_body)
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
    stepHeader(R.string.onboarding_provider_title, R.string.onboarding_provider_body)
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
                Text(stringResource(R.string.onboarding_test_connection))
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
                text = stringResource(if (cleanupChosen) R.string.onboarding_cleanup_on else R.string.onboarding_cleanup_optional),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun LazyListScope.methodStep(selected: DictationMethod, onSelect: (DictationMethod) -> Unit) {
    stepHeader(R.string.onboarding_method_title, R.string.onboarding_method_body)
    item(key = "methods") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.selectableGroup()) {
            MethodCard(
                icon = SasayakiIcons.ChatBubble,
                title = stringResource(R.string.onboarding_method_bubble_title),
                body = stringResource(R.string.onboarding_method_bubble_body),
                needs = stringResource(R.string.onboarding_method_bubble_needs),
                selected = selected == DictationMethod.BUBBLE,
                onClick = { onSelect(DictationMethod.BUBBLE) }
            )
            MethodCard(
                icon = SasayakiIcons.Keyboard,
                title = stringResource(R.string.onboarding_method_keyboard_title),
                body = stringResource(R.string.onboarding_method_keyboard_body),
                needs = stringResource(R.string.onboarding_method_keyboard_needs),
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
        DictationMethod.BUBBLE -> stepHeader(R.string.onboarding_permissions_bubble_title, R.string.onboarding_permissions_bubble_body)

        DictationMethod.KEYBOARD -> stepHeader(
            R.string.onboarding_permissions_keyboard_title,
            R.string.onboarding_permissions_keyboard_body
        )
    }
    item(key = "permissions") { SetupGroup(items = setup.items, restrictedSettingsLikely = setup.restrictedSettingsLikely) }
}

private fun LazyListScope.tryIt(setup: OnboardingSetup) {
    stepHeader(
        R.string.onboarding_try_title,
        when {
            !setup.ready -> R.string.onboarding_try_not_ready
            setup.method == DictationMethod.KEYBOARD -> R.string.onboarding_try_keyboard
            else -> R.string.onboarding_try_bubble
        }
    )
    item(key = "field") { TryField(enabled = setup.ready) }
    if (setup.method == DictationMethod.BUBBLE && setup.ready && !setup.bubbleRunning) {
        item(key = "starting") {
            Text(
                stringResource(R.string.onboarding_starting_bubble),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            placeholder = { Text(stringResource(R.string.onboarding_try_placeholder)) },
            minLines = 5
        )
    }
}
