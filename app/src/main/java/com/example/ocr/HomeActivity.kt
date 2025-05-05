package com.application.ocr

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.application.ocr.adapter.DocumentAdapter
import com.application.ocr.data.DocumentRepository
import com.application.ocr.databinding.ActivityHomeBinding
import com.application.ocr.model.Document
import com.google.android.material.snackbar.Snackbar

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var adapter: DocumentAdapter
    private lateinit var repository: DocumentRepository

    // For undo
    private var recentlyDeletedDoc: Document? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        repository = DocumentRepository(this)
        setupRecyclerView()
        setupListeners()
        setupItemTouchHelper()
    }

    override fun onResume() {
        super.onResume()
        loadDocuments()
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(
            onItemClick = { doc -> navigateToDocumentViewer(doc) },
            onItemLongClick = {
                false
            }
        )
        binding.rvDocuments.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = this@HomeActivity.adapter
        }
    }

    private fun setupListeners() {
        binding.fabScan.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
    }

    private fun loadDocuments() {
        val docs = repository.getAllDocuments()
        adapter.submitList(docs)
        binding.tvEmptyState.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
        binding.rvDocuments.visibility = if (docs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun navigateToDocumentViewer(document: Document) {
        Intent(this, DocumentViewerActivity::class.java).also { intent ->
            intent.putExtra(DocumentViewerActivity.EXTRA_DOCUMENT_ID, document.id)
            startActivity(intent)
        }
    }

    private fun showDeleteDialog(document: Document) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(R.string.yes) { _, _ ->
                repository.deleteDocument(document.id)
                loadDocuments()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun setupItemTouchHelper() {
        // prefetch drawables & paints from outer scope
        val deleteIcon   = ContextCompat.getDrawable(this, R.drawable.ic_delete)
        val iconWidth    = deleteIcon?.intrinsicWidth ?: 0
        val iconHeight   = deleteIcon?.intrinsicHeight ?: 0
        val bgFillColor  = Color.parseColor("#f44336")         // red 500
        val bgStrokeColor= Color.parseColor("#33000000")       // translucent black
        val cornerRadius = resources.getDimension(R.dimen.card_corner_radius)  // match your 8dp card
        val strokeWidth  = resources.getDimension(R.dimen.stroke_width)       // e.g. 2dp
        val extraOffset  = resources.getDimensionPixelSize(R.dimen.swipe_offset) // e.g. 16dp

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT
        ) {
            override fun isLongPressDragEnabled() = true
            override fun isItemViewSwipeEnabled()    = true

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    // e.g. raise elevation & fade
                    viewHolder.itemView.alpha = 0.8f
                    viewHolder.itemView.elevation = 16f
                    viewHolder.itemView.performHapticFeedback(
                        HapticFeedbackConstants.LONG_PRESS
                    )
                }
            }

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                // restore
                viewHolder.itemView.alpha = 1f
                viewHolder.itemView.elevation = 0f
            }

            // Drag: swap positions in the list
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val list = adapter.currentList.toMutableList()
                val from = vh.bindingAdapterPosition
                val to   = target.bindingAdapterPosition
                list.add(to, list.removeAt(from))
                adapter.submitList(list)
                repository.updateOrder(list)

                return true
            }

            // Swipe: delete + undo
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.bindingAdapterPosition
                val doc = adapter.currentList[pos]
                recentlyDeletedDoc = doc
                repository.deleteDocument(doc.id)
                loadDocuments()
                Snackbar.make(binding.root,
                    getString(R.string.delete) + ": “${doc.title}”",
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.undo) {
                    recentlyDeletedDoc?.let {
                        repository.saveDocument(it)
                        loadDocuments()
                    }
                }.show()
            }

            // Draw the swipe background + icon
            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val item = vh.itemView
                    val top = item.top.toFloat()
                    val bottom = item.bottom.toFloat()
                    val left = item.right + dX - extraOffset
                    val right = item.right

                    // rounded rect fill
                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = bgFillColor
                        style = Paint.Style.FILL
                    }
                    val rectF = RectF(left, top, right.toFloat(), bottom)
                    c.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)

                    // rounded rect stroke
                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = bgStrokeColor
                        style = Paint.Style.STROKE
                        this.strokeWidth = strokeWidth
                    }
                    c.drawRoundRect(rectF, cornerRadius, cornerRadius, strokePaint)

                    // draw icon
                    val iconMargin = (item.height - iconHeight) / 2
                    val iconLeft   = (right - iconMargin - iconWidth).toInt()
                    val iconTop    = (top + iconMargin).toInt()
                    deleteIcon?.setBounds(
                        iconLeft, iconTop,
                        iconLeft + iconWidth,
                        iconTop + iconHeight
                    )
                    deleteIcon?.draw(c)
                }

                super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
            }
        }

        ItemTouchHelper(callback).attachToRecyclerView(binding.rvDocuments)
    }

}
