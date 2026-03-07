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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentScanAdapter(
    private val onItemClick: (BookEntity) -> Unit
) : ListAdapter<BookEntity, RecentScanAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_scan, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.item_thumbnail)
        private val title: TextView      = itemView.findViewById(R.id.item_title)
        private val timestamp: TextView  = itemView.findViewById(R.id.item_timestamp)

        fun bind(book: BookEntity) {
            title.text = book.title
            timestamp.text = formatTimestamp(book.scanTimestamp)

            Glide.with(itemView.context)
                .load(book.thumbnailUrl)
                .placeholder(R.drawable.bg_book_cover_placeholder)
                .error(R.drawable.bg_book_cover_placeholder)
                .centerCrop()
                .into(thumbnail)

            itemView.setOnClickListener { onItemClick(book) }
        }

        private fun formatTimestamp(ts: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - ts
            return when {
                diff < 3_600_000  -> "${diff / 60_000} min ago"
                diff < 86_400_000 -> "${diff / 3_600_000} hours ago"
                diff < 172_800_000 -> "Yesterday"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<BookEntity>() {
        override fun areItemsTheSame(a: BookEntity, b: BookEntity) = a.volumeId == b.volumeId
        override fun areContentsTheSame(a: BookEntity, b: BookEntity) = a == b
    }
}