package com.sasayaki.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.data.repository.HistoryRepository
import com.sasayaki.data.db.entity.DictationSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
data class DayGroup(
    val key: String,
    val date: String,
    val totalWords: Int,
    val dictations: List<DictationSummary>
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val displayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

    val dayGroups: StateFlow<List<DayGroup>> = historyRepository.recent().map { dictations ->
        dictations
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, items) ->
                DayGroup(
                    key = date.toString(),
                    date = formatDate(date),
                    totalWords = items.sumOf { it.wordCount },
                    dictations = items
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _retryingIds = MutableStateFlow<Set<Long>>(emptySet())
    val retryingIds: StateFlow<Set<Long>> = _retryingIds.asStateFlow()

    fun removeFromHistory(id: Long) {
        viewModelScope.launch {
            historyRepository.removeFromHistory(id)
        }
    }

    fun removeAllFromHistory() {
        viewModelScope.launch {
            historyRepository.removeAllFromHistory()
        }
    }

    fun retry(id: Long) {
        viewModelScope.launch {
            _retryingIds.update { it + id }
            try {
                historyRepository.retry(id)
            } finally {
                _retryingIds.update { it - id }
            }
        }
    }

    suspend fun getRawText(id: Long): String? {
        return historyRepository.getRawText(id)
    }

    private fun formatDate(date: LocalDate): String {
        val today = LocalDate.now(zoneId)
        if (date == today) return "Today"
        if (date == today.minusDays(1)) return "Yesterday"
        return displayFormat.format(date)
    }
}
