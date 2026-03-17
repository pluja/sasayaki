package com.sasayaki.ui.home

import androidx.lifecycle.ViewModel
import com.sasayaki.data.db.dao.DictationDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dictationDao: DictationDao
) : ViewModel() {
    private val startOfToday: Long
        get() {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

    val todayCount: Flow<Int> = dictationDao.getTodayCount(startOfToday)
    val todayWordCount: Flow<Int> = dictationDao.getTodayWordCount(startOfToday)
}
