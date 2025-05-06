package com.application.ocr.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.application.ocr.databinding.ItemReceiptBinding
import com.application.ocr.model.Receipt
import com.application.ocr.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.Locale

class ReceiptAdapter(
    private val onItemClick: (Receipt) -> Unit,
    private val onItemLongClick: (Receipt) -> Boolean
) : ListAdapter<Receipt, ReceiptAdapter.ReceiptViewHolder>(ReceiptDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiptViewHolder {
        val binding = ItemReceiptBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReceiptViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReceiptViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReceiptViewHolder(
        private val binding: ItemReceiptBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemClick(getItem(pos))
            }
            binding.root.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemLongClick(getItem(pos))
                else false
            }
        }

        fun bind(receipt: Receipt) = with(binding) {
            tvMerchantName.text = receipt.merchantName

            // Format date
            val dateFmt = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
            tvReceiptDate.text = dateFmt.format(receipt.updatedAt)

            // Show total amount
            tvTotalAmount.text = "$${String.format("%.2f", receipt.totalAmount)}"

            // Show number of items
            tvItemCount.text = "${receipt.items.size} items"

            // Set receipt image thumbnail
            receipt.imagePath?.let { path ->
                val bmp = FileUtils.loadImageFromPath(binding.root.context, path)
                bmp?.let { ivReceiptThumbnail.setImageBitmap(it) }
            }
        }
    }

    private class ReceiptDiffCallback : DiffUtil.ItemCallback<Receipt>() {
        override fun areItemsTheSame(oldItem: Receipt, newItem: Receipt) =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Receipt, newItem: Receipt) =
            oldItem == newItem
    }
}