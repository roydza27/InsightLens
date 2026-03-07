package com.royal.insightlens.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.royal.insightlens.data.local.entity.BookEntity
import com.royal.insightlens.data.repository.BookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * HomeViewModel
 *
 * Observes recent scans from Room DB as SSOT.
 * UI never touches repository directly.
 */
class HomeViewModel(
    repository: BookRepository
) : ViewModel() {

    // Recent 10 scans for horizontal RecyclerView on home screen
    val recentScans: StateFlow<HomeUiState> = repository
        .observeRecentScans()
        .map { books ->
            if (books.isEmpty()) HomeUiState.Empty
            else HomeUiState.HasBooks(books)
        }
        .stateIn(
            scope           = viewModelScope,
            started         = SharingStarted.WhileSubscribed(5_000),
            initialValue    = HomeUiState.Loading
        )

    class Factory(private val repository: BookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repository) as T
    }
}

sealed class HomeUiState {
    object Loading                          : HomeUiState()
    object Empty                            : HomeUiState()
    data class HasBooks(val books: List<BookEntity>) : HomeUiState()
}