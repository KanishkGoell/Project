//package com.application.ocr.adapter
//
//import android.text.Editable
//import android.text.TextWatcher
//import android.view.LayoutInflater
//import android.view.ViewGroup
//import androidx.recyclerview.widget.RecyclerView
//import com.application.ocr.databinding.ItemReceiptRowBinding
//import com.application.ocr.model.ReceiptItem
//
//class ReceiptItemAdapter(
//    private val items: MutableList<ReceiptItem>,
//    private val onItemDeleted: (Int) -> Unit
//) : RecyclerView.Adapter<ReceiptItemAdapter.ReceiptItemViewHolder>() {
//
//    inner class ReceiptItemViewHolder(val binding: ItemReceiptRowBinding) : RecyclerView.ViewHolder(binding.root) {
//        init {
//            binding.btnDelete.setOnClickListener {
//                val position = adapterPosition
//                if (position != RecyclerView.NO_POSITION) {
//                    onItemDeleted(position)
//                }
//            }
//
//            // Set text watchers to update data model when user edits fields
//            binding.etItemName.addTextChangedListener(createTextWatcher { text ->
//                val position = adapterPosition
//                if (position != RecyclerView.NO_POSITION) {
//                    items[position].itemName = text
//                }
//            })
//
//            binding.etPrice.addTextChangedListener(createTextWatcher { text ->
//                val position = adapterPosition
//                if (position != RecyclerView.NO_POSITION) {
//                    items[position].price = text
//                }
//            })
//
//            binding.etQuantity.addTextChangedListener(createTextWatcher { text ->
//                val position = adapterPosition
//                if (position != RecyclerView.NO_POSITION) {
//                    items[position].quantity = text.ifEmpty { "1" }
//                }
//            })
//        }
//
//        fun bind(item: ReceiptItem) {
//            binding.etItemName.setText(item.itemName)
//            binding.etPrice.setText(item.price)
//            binding.etQuantity.setText(item.quantity)
//        }
//
//        private fun createTextWatcher(onTextChanged: (String) -> Unit): TextWatcher {
//            return object : TextWatcher {
//                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
//                override fun afterTextChanged(s: Editable?) {
//                    onTextChanged(s?.toString() ?: "")
//                }
//            }
//        }
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiptItemViewHolder {
//        val binding = ItemReceiptRowBinding.inflate(
//            LayoutInflater.from(parent.context),
//            parent,
//            false
//        )
//        return ReceiptItemViewHolder(binding)
//    }
//
//    override fun onBindViewHolder(holder: ReceiptItemViewHolder, position: Int) {
//        holder.bind(items[position])
//    }
//
//    override fun getItemCount(): Int = items.size
//
//    fun getItems(): List<ReceiptItem> = items
//
//    fun addItem(item: ReceiptItem) {
//        items.add(item)
//        notifyItemInserted(items.size - 1)
//    }
//
//    fun removeItem(position: Int) {
//        if (position in 0 until items.size) {
//            items.removeAt(position)
//            notifyItemRemoved(position)
//            notifyItemRangeChanged(position, items.size)
//        }
//    }
//}