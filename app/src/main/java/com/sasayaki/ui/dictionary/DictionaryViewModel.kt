package com.sasayaki.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.data.db.dao.DictionaryDao
import com.sasayaki.data.db.entity.DictionaryWord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val dictionaryDao: DictionaryDao
) : ViewModel() {
    companion object {
        private const val SEARCH_DEBOUNCE_MS = 200L
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val words: StateFlow<List<DictionaryWord>> = _searchQuery
        .map(String::trim)
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) dictionaryDao.getAll()
            else dictionaryDao.search(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
