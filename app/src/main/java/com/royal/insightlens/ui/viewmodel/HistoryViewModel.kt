package com.royal.insightlens.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.royal.insightlens.data.local.entity.BookEntity
import com.royal.insightlens.data.repository.BookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * HistoryViewModel
 *
 * Observes all books sorted by timestamp, with tab-based filtering.
 * Tabs: All Scans | Books | Articles | Documents
 */
class HistoryViewModel(
    private val repository: BookRepository
) : ViewModel() {

    // ─── Active Tab ───────────────────────────────────────────────────────

    enum class Tab(val key: String) {
        ALL("all"),
        BOOKS("books"),
        ARTICLES("articles"),
        DOCUMENTS("documents")
    }

    private val _activeTab = MutableStateFlow(Tab.ALL)
    val activeTab: StateFlow<Tab> = _activeTab.asStateFlow()

    fun selectTab(tab: Tab) {
        _activeTab.value = tab
    }

    // ─── Books List — reacts to tab changes ───────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyState: StateFlow<HistoryUiState> = _activeTab
        .flatMapLatest { tab ->
            repository.observeBooksByCategory(tab.key)
        }
        .map { books ->
            if (books.isEmpty()) HistoryUiState.Empty
            else HistoryUiState.HasBooks(groupByDate(books))
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState.Loading
        )

    // ─── Date Grouping ────────────────────────────────────────────────────

    /**
     * Groups books into Today / Yesterday / date sections
     * for the history list header decoration.
     */
    private fun groupByDate(books: List<BookEntity>): List<HistoryListItem> {
        val result = mutableListOf<HistoryListItem>()
        val now = System.currentTimeMillis()
        val oneDayMs = 86_400_000L

        var lastHeader = ""

        for (book in books) {
            val age = now - book.scanTimestamp
            val header = when {
                age < oneDayMs       -> "TODAY"
                age < oneDayMs * 2   -> "YESTERDAY"
                age < oneDayMs * 7   -> "THIS WEEK"
                age < oneDayMs * 30  -> "THIS MONTH"
                else                 -> "OLDER"
            }

            if (header != lastHeader) {
                result.add(HistoryListItem.Header(header))
                lastHeader = header
            }

            result.add(HistoryListItem.BookItem(book))
        }

        return result
    }

    class Factory(private val repository: BookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(repository) as T
    }
}

// ─── UI State ─────────────────────────────────────────────────────────────

sealed class HistoryUiState {
    object Loading : HistoryUiState()
    object Empty   : HistoryUiState()
    data class HasBooks(val items: List<HistoryListItem>) : HistoryUiState()
}

// ─── List Items ───────────────────────────────────────────────────────────

sealed class HistoryListItem {
    data class Header(val label: String)         : HistoryListItem()
    data class BookItem(val book: BookEntity)    : HistoryListItem()
}