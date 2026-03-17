package com.sasayaki.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.data.db.dao.DictionaryDao
import com.sasayaki.data.db.entity.DictionaryWord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val dictionaryDao: DictionaryDao
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val words: StateFlow<List<DictionaryWord>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) dictionaryDao.getAll()
        else dictionaryDao.search(query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addWord(word: String, category: String = "") {
        viewModelScope.launch {
            dictionaryDao.insert(DictionaryWord(word = word.trim(), category = category.trim()))
        }
    }

    fun deleteWord(id: Long) {
        viewModelScope.launch {
            dictionaryDao.delete(id)
        }
    }
}
