package com.sasayaki.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.sasayaki.R
import com.sasayaki.domain.model.SpeechLanguage
import com.sasayaki.domain.model.speechLanguages
import com.sasayaki.domain.provider.Provider
import com.sasayaki.domain.provider.Purpose
import com.sasayaki.ui.components.SectionLabel
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.Spacing

@Composable
private fun PickerSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}

@Composable
private fun RadioRow(label: String, supporting: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LanguageSheet(selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember { speechLanguages() }
    val suggested = remember(all) {
        val deviceLocales = LocaleListCompat.getAdjustedDefault()
        val codes = (0 until deviceLocales.size()).mapNotNull { deviceLocales[it]?.language }.distinct()
        codes.mapNotNull { code -> all.firstOrNull { it.code == code } }
    }
    val matches = if (query.isBlank()) {
        all
    } else {
        all.filter {
            it.name.contains(query.trim(), ignoreCase = true) ||
                it.code == query.trim().lowercase()
        }
    }

    PickerSheet(title = stringResource(R.string.profile_language), onDismiss = onDismiss) {
        Text(
            stringResource(R.string.profile_language_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.profile_language_search)) },
            leadingIcon = { Icon(SasayakiIcons.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
        )
        LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
            if (query.isBlank()) {
                item(key = "auto") {
                    RadioRow(
                        stringResource(R.string.profile_language_detect),
                        stringResource(R.string.profile_language_detect_description),
                        selected == null
                    ) { onSelect(null) }
                }
                if (suggested.isNotEmpty()) {
                    item(key = "suggested-label") {
                        SectionLabel(stringResource(R.string.profile_language_on_device), Modifier.padding(top = Spacing.m))
                    }
                    items(suggested, key = { "s-${it.code}" }) { language -> LanguageRow(language, selected, onSelect) }
                    item(key = "all-label") {
                        SectionLabel(stringResource(R.string.profile_language_all), Modifier.padding(top = Spacing.m))
                    }
                }
            }
            items(matches, key = { it.code }) { language -> LanguageRow(language, selected, onSelect) }
        }
    }
}

@Composable
private fun LanguageRow(language: SpeechLanguage, selected: String?, onSelect: (String?) -> Unit) {
    RadioRow(language.name, null, language.code == selected) { onSelect(language.code) }
}

@Composable
fun ProviderSheet(
    purpose: Purpose,
    providers: List<Provider>,
    selectedId: Long?,
    automaticLabel: String,
    onSelect: (Long?) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (purpose == Purpose.SPEECH) R.string.profile_speech_provider_title else R.string.profile_cleanup_provider_title
    PickerSheet(title = stringResource(title), onDismiss = onDismiss) {
        Column {
            RadioRow(stringResource(R.string.profile_provider_automatic), automaticLabel, selectedId == null) { onSelect(null) }
            providers.forEach { provider ->
                val default = provider.defaultModel(purpose)
                RadioRow(
                    label = provider.name,
                    supporting = default?.let { stringResource(R.string.profile_provider_default_model, it) }
                        ?: stringResource(R.string.profile_provider_no_default_model),
                    selected = provider.id == selectedId
                ) { onSelect(provider.id) }
            }
        }
        TextButton(onClick = onManage, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.profile_manage_providers)) }
    }
}

data class ChecklistEntry(val id: Long, val label: String, val supporting: String?)

@Composable
fun ChecklistSheet(
    title: String,
    description: String,
    entries: List<ChecklistEntry>,
    selected: Set<Long>,
    emptyText: String,
    manageLabel: String,
    onToggle: (Long) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    PickerSheet(title = title, onDismiss = onDismiss) {
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entries.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = Spacing.m))
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = entry.id in selected, onValueChange = { onToggle(entry.id) }, role = Role.Checkbox)
                            .padding(vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
                    ) {
                        Checkbox(checked = entry.id in selected, onCheckedChange = null)
                        Column {
                            Text(entry.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (entry.supporting != null) {
                                Text(
                                    entry.supporting,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        TextButton(onClick = onManage, modifier = Modifier.align(Alignment.End)) { Text(manageLabel) }
    }
}
