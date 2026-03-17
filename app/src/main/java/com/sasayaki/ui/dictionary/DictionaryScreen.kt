package com.sasayaki.ui.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.data.db.entity.DictionaryWord
import com.sasayaki.ui.common.EmptyStateCard
import com.sasayaki.ui.common.SasayakiScaffold
import com.sasayaki.ui.common.SasayakiTopBar
import com.sasayaki.ui.common.SectionCard
import com.sasayaki.ui.common.StatusPill

@Composable
fun DictionaryScreen(
    onBack: () -> Unit,
    viewModel: DictionaryViewModel = hiltViewModel()
) {
    val words by viewModel.words.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var newWord by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf("") }

    SasayakiScaffold(
        topBar = {
            SasayakiTopBar(
                title = "Dictionary",
                subtitle = "Teach Sasayaki the names, jargon, and phrases you use every day.",
                onBack = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    newWord = ""
                    newCategory = ""
                    showAddDialog = true
                },
                text = { Text("Add word") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = dictionaryContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(
                    title = "Custom vocabulary",
                    subtitle = if (words.isEmpty()) {
                        "Start a vocabulary list for product names, people, and domain-specific terms."
                    } else {
                        "${words.size} saved ${if (words.size == 1) "term" else "terms"}. Search or refine your list below."
                    }
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search terms") },
                        placeholder = { Text("Find a word or category") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true
                    )
                }
            }

            if (words.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = if (searchQuery.isBlank()) "No custom terms yet" else "No matches found",
                        description = if (searchQuery.isBlank()) {
                            "Add words that are often mistranscribed so they stay consistent wherever you dictate."
                        } else {
                            "Try a broader search, or add the missing term to your dictionary."
                        }
                    )
                }
            } else {
                items(words, key = { it.id }) { word ->
                    DictionaryWordCard(
                        word = word,
                        onDelete = { viewModel.deleteWord(word.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add a dictionary term") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Use categories for context, like customer names, product lines, or acronyms.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newWord,
                        onValueChange = { newWord = it },
                        label = { Text("Word or phrase") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Optional") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newWord.isNotBlank(),
                    onClick = {
                        viewModel.addWord(newWord, newCategory)
                        showAddDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DictionaryWordCard(
    word: DictionaryWord,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = word.word, style = MaterialTheme.typography.titleMedium)
                if (word.category.isNotBlank()) {
                    StatusPill(
                        label = word.category,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete word",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun dictionaryContentPadding(padding: PaddingValues): PaddingValues {
    return PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = padding.calculateTopPadding() + 16.dp,
        bottom = padding.calculateBottomPadding() + 96.dp
    )
}
