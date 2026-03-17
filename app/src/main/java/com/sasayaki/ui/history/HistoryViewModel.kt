package com.sasayaki.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.data.db.dao.DictationDao
import com.sasayaki.data.db.entity.Dictation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class DayGroup(
    val date: String,
    val totalWords: Int,
    val dictations: List<Dictation>
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dictationDao: DictationDao
) : ViewModel() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())

    val dayGroups: StateFlow<List<DayGroup>> = dictationDao.getAll().map { dictations ->
        dictations
            .groupBy { dateFormat.format(Date(it.timestamp)) }
            .map { (_, items) ->
                DayGroup(
                    date = displayFormat.format(Date(items.first().timestamp)),
                    totalWords = items.sumOf { it.wordCount },
                    dictations = items
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch {
            dictationDao.delete(id)
        }
    }
}
