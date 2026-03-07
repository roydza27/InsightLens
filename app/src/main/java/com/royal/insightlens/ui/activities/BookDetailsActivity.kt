package com.royal.insightlens.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.royal.insightlens.R
import com.royal.insightlens.data.local.AppDatabase
import com.royal.insightlens.data.local.entity.BookEntity
import com.royal.insightlens.data.repository.BookRepository
import com.royal.insightlens.ui.viewmodel.BookDetailUiState
import com.royal.insightlens.ui.viewmodel.BookDetailsViewModel
import kotlinx.coroutines.launch

class BookDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VOLUME_ID = "volume_id"
    }

    private val volumeId: String by lazy {
        intent.getStringExtra(EXTRA_VOLUME_ID) ?: ""
    }

    private val viewModel: BookDetailsViewModel by viewModels {
        BookDetailsViewModel.Factory(
            BookRepository(AppDatabase.getInstance(this).bookDao()),
            volumeId
        )
    }

    private lateinit var backBtn: ImageView
    private lateinit var shareBtn: ImageView
    private lateinit var bookCover: ImageView
    private lateinit var bookTitle: TextView
    private lateinit var bookAuthor: TextView
    private lateinit var ratingText: TextView
    private lateinit var aboutBody: TextView
    private lateinit var purchaseBtn: MaterialButton

    // Info row values — accessed through the <include> parent views
    private lateinit var publisherLabel: TextView
    private lateinit var publisherValue: TextView
    private lateinit var yearLabel: TextView
    private lateinit var yearValue: TextView
    private lateinit var isbnLabel: TextView
    private lateinit var isbnValue: TextView
    private lateinit var pagesLabel: TextView
    private lateinit var pagesValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_details)
        bindViews()
        setupClickListeners()
        observeViewModel()
    }

    private fun bindViews() {
        backBtn     = findViewById(R.id.details_back_btn)
        shareBtn    = findViewById(R.id.details_share_btn)
        bookCover   = findViewById(R.id.details_book_cover)
        bookTitle   = findViewById(R.id.details_book_title)
        bookAuthor  = findViewById(R.id.details_book_author)
        ratingText  = findViewById(R.id.details_rating_text)
        aboutBody   = findViewById(R.id.details_about_body)
        purchaseBtn = findViewById(R.id.details_purchase_btn)

        // The layout uses <include id="info_row_publisher" layout="@layout/component_book_info_row" />
        // Inner views book_info_label / book_info_value must be accessed via the include's root
        val rowPublisher = findViewById<View>(R.id.info_row_publisher)
        publisherLabel   = rowPublisher.findViewById(R.id.book_info_label)
        publisherValue   = rowPublisher.findViewById(R.id.book_info_value)

        val rowYear = findViewById<View>(R.id.info_row_year)
        yearLabel   = rowYear.findViewById(R.id.book_info_label)
        yearValue   = rowYear.findViewById(R.id.book_info_value)

        val rowIsbn = findViewById<View>(R.id.info_row_isbn)
        isbnLabel   = rowIsbn.findViewById(R.id.book_info_label)
        isbnValue   = rowIsbn.findViewById(R.id.book_info_value)

        val rowPages = findViewById<View>(R.id.info_row_pages)
        pagesLabel   = rowPages.findViewById(R.id.book_info_label)
        pagesValue   = rowPages.findViewById(R.id.book_info_value)

        // Static labels
        publisherLabel.text = getString(R.string.label_publisher)
        yearLabel.text      = getString(R.string.label_year)
        isbnLabel.text      = getString(R.string.label_isbn)
        pagesLabel.text     = getString(R.string.label_pages)
    }

    private fun setupClickListeners() {
        backBtn.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.detailState.collect { state ->
                    when (state) {
                        is BookDetailUiState.Loading  -> Unit
                        is BookDetailUiState.NotFound -> finish()
                        is BookDetailUiState.Loaded   -> bindBook(state.book)
                    }
                }
            }
        }
    }

    private fun bindBook(book: BookEntity) {
        Glide.with(this)
            .load(book.thumbnailUrl)
            .placeholder(R.drawable.bg_book_cover_placeholder)
            .error(R.drawable.bg_book_cover_placeholder)
            .centerCrop()
            .into(bookCover)

        bookTitle.text  = book.title
        bookAuthor.text = book.author
        ratingText.text = if (book.rating != null) {
            getString(R.string.rating_format, book.rating, book.reviewCount ?: 0)
        } else {
            getString(R.string.no_ratings)
        }

        publisherValue.text = book.publisher ?: getString(R.string.value_unknown)
        yearValue.text      = book.publishedDate?.take(4) ?: getString(R.string.value_unknown)
        isbnValue.text      = book.isbn ?: getString(R.string.value_unknown)
        pagesValue.text     = if (book.pageCount != null) {
            getString(R.string.pages_format, book.pageCount)
        } else {
            getString(R.string.value_unknown)
        }

        aboutBody.text = book.overview.ifBlank { getString(R.string.no_description) }

        if (book.bookLink.isNotBlank()) {
            purchaseBtn.visibility = View.VISIBLE
            purchaseBtn.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, book.bookLink.toUri()))
            }
        } else {
            purchaseBtn.visibility = View.GONE
        }

        shareBtn.setOnClickListener {
            val shareText = getString(R.string.share_text, book.title, book.author, book.bookLink)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    },
                    getString(R.string.share_book)
                )
            )
        }
    }
}