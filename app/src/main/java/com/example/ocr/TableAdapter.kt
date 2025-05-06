package com.application.ocr

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.application.ocr.databinding.ItemReceiptRowBinding
import com.application.ocr.model.ReceiptRow

class TableAdapter(private val rows: List<ReceiptRow>) :
    RecyclerView.Adapter<TableAdapter.ReceiptRowViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiptRowViewHolder {
        val binding = ItemReceiptRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReceiptRowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReceiptRowViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount(): Int = rows.size

    inner class ReceiptRowViewHolder(
        private val binding: ItemReceiptRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ReceiptRow) {
            binding.tvItem.text = row.item
            binding.tvPrice.text = "$${String.format("%.2f", row.price)}"
        }
    }
}