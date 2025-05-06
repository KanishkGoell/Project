//DocumentAdapter.kt
package com.application.ocr.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.application.ocr.databinding.ItemDocumentBinding
import com.application.ocr.model.Document
import com.application.ocr.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.Locale

class DocumentAdapter(
    private val onItemClick: (Document) -> Unit,
    private val onItemLongClick: (Document) -> Boolean
) : ListAdapter<Document, DocumentAdapter.DocumentViewHolder>(DocumentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val binding = ItemDocumentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DocumentViewHolder(binding)
    }
    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DocumentViewHolder(
        private val binding: ItemDocumentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemClick(getItem(pos))
            }
            binding.root.setOnLongClickListener { false
            }
        }

        fun bind(document: Document) = with(binding) {
            tvDocumentTitle.text = document.title

            // date
            val dateFmt = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
            tvDocumentDate.text = dateFmt.format(document.updatedAt)

            // preview
            tvDocumentPreview.text =
                if (document.content.length > 100)
                    "${document.content.substring(0, 97)}..."
                else document.content

            // thumbnail
            document.imagePath?.let { path ->
                val bmp = FileUtils.loadImageFromPath(binding.root.context, path)
                bmp?.let { ivDocumentThumbnail.setImageBitmap(it) }
            }
        }
    }

    private class DocumentDiffCallback : DiffUtil.ItemCallback<Document>() {
        override fun areItemsTheSame(oldItem: Document, newItem: Document) =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Document, newItem: Document) =
            oldItem == newItem
    }
}