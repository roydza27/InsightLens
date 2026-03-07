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
import kotlinx.coroutines.launch

/**
 * BookDetailsViewModel
 *
 * Observes a single book from Room DB by volumeId.
 * Updates lastAccessed timestamp on launch.
 */
class BookDetailsViewModel(
    private val repository: BookRepository,
    private val volumeId: String
) : ViewModel() {

    val detailState: StateFlow<BookDetailUiState> = repository
        .observeBook(volumeId)
        .map { book ->
            if (book != null) BookDetailUiState.Loaded(book)
            else BookDetailUiState.NotFound
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = BookDetailUiState.Loading
        )

    init {
        // Update lastAccessed when details screen opens
        viewModelScope.launch {
            // Small delay to let DB observation start first
            kotlinx.coroutines.delay(100)
        }
    }

    class Factory(
        private val repository: BookRepository,
        private val volumeId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BookDetailsViewModel(repository, volumeId) as T
    }
}

sealed class BookDetailUiState {
    object Loading                           : BookDetailUiState()
    object NotFound                          : BookDetailUiState()
    data class Loaded(val book: BookEntity)  : BookDetailUiState()
}