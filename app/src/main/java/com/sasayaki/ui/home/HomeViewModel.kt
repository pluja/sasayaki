package com.sasayaki.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.data.db.dao.DictationDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dictationDao: DictationDao
) : ViewModel() {
    private val startOfToday = MutableStateFlow(currentStartOfDay())

    val todayCount: StateFlow<Int> = startOfToday
        .flatMapLatest(dictationDao::getTodayCount)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayWordCount: StateFlow<Int> = startOfToday
        .flatMapLatest(dictationDao::getTodayWordCount)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayDurationMs: StateFlow<Long> = startOfToday
        .flatMapLatest(dictationDao::getTodayDurationMs)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalCount: StateFlow<Int> = dictationDao.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalWordCount: StateFlow<Int> = dictationDao.getTotalWordCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalDurationMs: StateFlow<Long> = dictationDao.getTotalDurationMs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(millisUntilNextDay())
                startOfToday.value = currentStartOfDay()
            }
        }
    }

    private fun currentStartOfDay(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun millisUntilNextDay(): Long {
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (nextMidnight.timeInMillis - System.currentTimeMillis()).coerceAtLeast(1000L)
    }
}
