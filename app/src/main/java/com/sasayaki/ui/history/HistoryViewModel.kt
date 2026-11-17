package com.sasayaki.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.repository.HistoryRepository
import com.sasayaki.di.ApplicationScope
import com.sasayaki.domain.transcription.TranscriptionManager
import com.sasayaki.ui.navigation.HistoryDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class HistoryUiState(
    val loaded: Boolean = false,
    val historyEnabled: Boolean = true,
    val hasHistory: Boolean = false,
    val query: String = "",
    val groups: List<DayGroup> = emptyList(),
    val retryingIds: Set<Long> = emptySet(),
    val focusId: Long = 0
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val transcriptionManager: TranscriptionManager,
    private val preferencesDataStore: PreferencesDataStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val focusId = savedStateHandle.toRoute<HistoryDestination>().focusId
    private val zone = ZoneId.systemDefault()
    private val query = MutableStateFlow("")
    private val pendingDeletion = MutableStateFlow<Set<Long>>(emptySet())
    private val retryingIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.history,
        preferencesDataStore.preferences,
        query,
        pendingDeletion,
        retryingIds
    ) { dictations, prefs, query, pending, retrying ->
        HistoryUiState(
            loaded = true,
            historyEnabled = prefs.historyEnabled,
            hasHistory = dictations.any { it.id !in pending },
            query = query,
            groups = groupByDay(dictations, zone, LocalDate.now(zone), query, pending),
            retryingIds = retrying,
            focusId = focusId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** requestDelete hides [id] at once; it is only erased by [commitDelete], so [undoDelete] can restore it. */
    fun requestDelete(id: Long) {
        pendingDeletion.update { it + id }
    }

    fun undoDelete(id: Long) {
        pendingDeletion.update { it - id }
    }

    fun commitDelete(id: Long) {
        if (id !in pendingDeletion.value) return
        applicationScope.launch {
            historyRepository.removeFromHistory(id)
            pendingDeletion.update { it - id }
        }
    }

    fun clearAll() {
        applicationScope.launch { historyRepository.removeAllFromHistory() }
    }

    fun enableHistory() {
        viewModelScope.launch { preferencesDataStore.setHistoryEnabled(true) }
    }

    fun retry(id: Long) {
        viewModelScope.launch {
            retryingIds.update { it + id }
            try {
                transcriptionManager.retry(id)
            } finally {
                retryingIds.update { it - id }
            }
        }
    }

    suspend fun rawText(id: Long): String? = historyRepository.getRawText(id)

    override fun onCleared() {
        // A delete whose undo snackbar was still showing when the screen closed.
        val pending = pendingDeletion.value
        if (pending.isNotEmpty()) {
            applicationScope.launch { pending.forEach { historyRepository.removeFromHistory(it) } }
        }
    }
}
