package com.royal.insightlens.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.royal.insightlens.R
import com.royal.insightlens.data.local.entity.BookEntity
import com.royal.insightlens.ui.viewmodel.HistoryListItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onItemClick: (BookEntity) -> Unit
) : ListAdapter<HistoryListItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_BOOK   = 1

        val DiffCallback = object : DiffUtil.ItemCallback<HistoryListItem>() {
            override fun areItemsTheSame(a: HistoryListItem, b: HistoryListItem): Boolean {
                return when {
                    a is HistoryListItem.Header && b is HistoryListItem.Header ->
                        a.label == b.label

                    a is HistoryListItem.BookItem && b is HistoryListItem.BookItem ->
                        a.book.id == b.book.id

                    else -> false
                }
            }
            override fun areContentsTheSame(a: HistoryListItem, b: HistoryListItem) = a == b
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is HistoryListItem.Header   -> VIEW_TYPE_HEADER
        is HistoryListItem.BookItem -> VIEW_TYPE_BOOK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_history_header, parent, false)
            )
            else -> BookViewHolder(
                inflater.inflate(R.layout.item_history_book, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryListItem.Header   -> (holder as HeaderViewHolder).bind(item)
            is HistoryListItem.BookItem -> (holder as BookViewHolder).bind(item.book)
        }
    }

    // ─── Header ViewHolder ────────────────────────────────────────────────

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.history_header_label)
        fun bind(item: HistoryListItem.Header) {
            label.text = item.label
        }
    }

    // ─── Book ViewHolder ──────────────────────────────────────────────────

    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.history_book_thumbnail)
        private val title: TextView      = itemView.findViewById(R.id.history_book_title)
        private val subtitle: TextView   = itemView.findViewById(R.id.history_book_subtitle)
        private val arrow: ImageView     = itemView.findViewById(R.id.history_book_arrow)

        fun bind(book: BookEntity) {
            title.text    = book.title
            subtitle.text = "${book.author} • ${formatDate(book.scanTimestamp)}"

            Glide.with(itemView.context)
                .load(book.thumbnailUrl)
                .placeholder(R.drawable.bg_book_cover_placeholder)
                .error(R.drawable.bg_book_cover_placeholder)
                .centerCrop()
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                .into(thumbnail)

            itemView.setOnClickListener { onItemClick(book) }
        }

        private fun formatDate(ts: Long): String {
            val now  = System.currentTimeMillis()
            val diff = now - ts
            return when {
                diff < 60_000 -> "Just now"
                diff < 3_600_000 -> "${diff / 60_000} min ago"
                diff < 86_400_000 -> SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))
                else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ts))
            }
        }
    }
}