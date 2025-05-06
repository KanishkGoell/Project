package com.application.ocr

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.application.ocr.adapter.DocumentAdapter
import com.application.ocr.adapter.ReceiptAdapter
import com.application.ocr.data.AuthRepository
import com.application.ocr.data.FirestoreRepository
import com.application.ocr.databinding.ActivityHomeBinding
import com.application.ocr.model.Document
import com.application.ocr.model.Receipt
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var documentAdapter: DocumentAdapter
    private lateinit var receiptAdapter: ReceiptAdapter
    private val repository = FirestoreRepository()
    private val authRepository = AuthRepository()

    // For undo
    private var recentlyDeletedDoc: Document? = null
    private var recentlyDeletedReceipt: Receipt? = null

    // Tab indices
    private val TAB_DOCUMENTS = 0
    private val TAB_RECEIPTS = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        Firebase.firestore.apply {
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)      // <── disks cache on
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            firestoreSettings = settings
        }
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Check if user is logged in
        if (!authRepository.isUserLoggedIn) {
            navigateToLogin()
            return
        }

        setupTabs()
        setupRecyclerViews()
        setupListeners()
        setupItemTouchHelpers()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupTabs() {
        binding.tabLayout.apply {
            addTab(newTab().setText(R.string.tab_documents))
            addTab(newTab().setText(R.string.tab_receipts))

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    updateCurrentView(tab.position)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }
    }

    private fun updateCurrentView(tabPosition: Int) {
        when (tabPosition) {
            TAB_DOCUMENTS -> {
                binding.rvDocuments.visibility = View.VISIBLE
                binding.rvReceipts.visibility = View.GONE
                binding.tvEmptyStateDocuments.visibility =
                    if (documentAdapter.currentList.isEmpty()) View.VISIBLE else View.GONE
                binding.tvEmptyStateReceipts.visibility = View.GONE
                binding.fabScan.contentDescription = getString(R.string.scan_document)
            }
            TAB_RECEIPTS -> {
                binding.rvDocuments.visibility = View.GONE
                binding.rvReceipts.visibility = View.VISIBLE
                binding.tvEmptyStateDocuments.visibility = View.GONE
                binding.tvEmptyStateReceipts.visibility =
                    if (receiptAdapter.currentList.isEmpty()) View.VISIBLE else View.GONE
                binding.fabScan.contentDescription = getString(R.string.scan_receipt)
            }
        }
    }

    private fun setupRecyclerViews() {
        // Document RecyclerView
        documentAdapter = DocumentAdapter(
            onItemClick = { doc -> navigateToDocumentViewer(doc) },
            onItemLongClick = { doc ->
                showDeleteDocumentDialog(doc)
                true
            }
        )
        binding.rvDocuments.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = this@HomeActivity.documentAdapter
        }

        // Receipt RecyclerView
        receiptAdapter = ReceiptAdapter(
            onItemClick = { receipt -> navigateToReceiptViewer(receipt) },
            onItemLongClick = { receipt ->
                showDeleteReceiptDialog(receipt)
                true
            }
        )
        binding.rvReceipts.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = this@HomeActivity.receiptAdapter
        }
    }

    private fun setupListeners() {
        binding.fabScan.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)

            // Pass info about what we're scanning based on the selected tab
            when (binding.tabLayout.selectedTabPosition) {
                TAB_DOCUMENTS -> intent.putExtra(ScanActivity.EXTRA_SCAN_TYPE, ScanActivity.SCAN_TYPE_DOCUMENT)
                TAB_RECEIPTS -> intent.putExtra(ScanActivity.EXTRA_SCAN_TYPE, ScanActivity.SCAN_TYPE_RECEIPT)
            }

            startActivity(intent)
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            // Load documents
            val docs = repository.getAllDocuments()
            documentAdapter.submitList(docs)
            binding.tvEmptyStateDocuments.visibility =
                if (docs.isEmpty() && binding.tabLayout.selectedTabPosition == TAB_DOCUMENTS)
                    View.VISIBLE else View.GONE

            // Load receipts
            val receipts = repository.getAllReceipts()
            receiptAdapter.submitList(receipts)
            binding.tvEmptyStateReceipts.visibility =
                if (receipts.isEmpty() && binding.tabLayout.selectedTabPosition == TAB_RECEIPTS)
                    View.VISIBLE else View.GONE

            // Update the current view
            updateCurrentView(binding.tabLayout.selectedTabPosition)
        }
    }

    private fun navigateToDocumentViewer(document: Document) {
        Intent(this, DocumentViewerActivity::class.java).also { intent ->
            intent.putExtra(DocumentViewerActivity.EXTRA_DOCUMENT_ID, document.id)
            startActivity(intent)
        }
    }

    private fun navigateToReceiptViewer(receipt: Receipt) {
        Intent(this, ReceiptViewerActivity::class.java).also { intent ->
            intent.putExtra(ReceiptViewerActivity.EXTRA_RECEIPT_ID, receipt.id)
            intent.putExtra(ReceiptViewerActivity.EXTRA_IS_NEW_RECEIPT, false)
            startActivity(intent)
        }
    }

    private fun showDeleteDocumentDialog(document: Document) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    repository.deleteDocument(document.id)
                    loadData()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun showDeleteReceiptDialog(receipt: Receipt) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    repository.deleteReceipt(receipt.id)
                    loadData()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun setupItemTouchHelpers() {
        setupDocumentItemTouchHelper()
        setupReceiptItemTouchHelper()
    }

    private fun setupDocumentItemTouchHelper() {
        // Pre-fetch drawables & paints from outer scope
        val deleteIcon = ContextCompat.getDrawable(this, R.drawable.ic_delete)
        val iconWidth = deleteIcon?.intrinsicWidth ?: 0
        val iconHeight = deleteIcon?.intrinsicHeight ?: 0
        val bgFillColor = Color.parseColor("#f44336")         // red 500
        val bgStrokeColor = Color.parseColor("#33000000")     // translucent black
        val cornerRadius = resources.getDimension(R.dimen.card_corner_radius)  // match your 8dp card
        val strokeWidth = resources.getDimension(R.dimen.stroke_width)         // e.g. 2dp
        val extraOffset = resources.getDimensionPixelSize(R.dimen.swipe_offset) // e.g. 16dp

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT
        ) {
            override fun isLongPressDragEnabled() = true
            override fun isItemViewSwipeEnabled() = true

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
                val list = documentAdapter.currentList.toMutableList()
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                list.add(to, list.removeAt(from))
                documentAdapter.submitList(list)

                lifecycleScope.launch {
                    repository.updateOrder(list)
                }

                return true
            }

            // Swipe: delete + undo
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.bindingAdapterPosition
                val doc = documentAdapter.currentList[pos]
                recentlyDeletedDoc = doc

                lifecycleScope.launch {
                    repository.deleteDocument(doc.id)
                    loadData()
                }

                Snackbar.make(
                    binding.root,
                    getString(R.string.delete) + ": \"${doc.title}\"",
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.undo) {
                    recentlyDeletedDoc?.let {
                        lifecycleScope.launch {
                            repository.saveDocument(it)
                            loadData()
                        }
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
                    val iconLeft = (right - iconMargin - iconWidth).toInt()
                    val iconTop = (top + iconMargin).toInt()
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

    private fun setupReceiptItemTouchHelper() {
        // Pre-fetch drawables & paints from outer scope
        val deleteIcon = ContextCompat.getDrawable(this, R.drawable.ic_delete)
        val iconWidth = deleteIcon?.intrinsicWidth ?: 0
        val iconHeight = deleteIcon?.intrinsicHeight ?: 0
        val bgFillColor = Color.parseColor("#f44336")         // red 500
        val bgStrokeColor = Color.parseColor("#33000000")     // translucent black
        val cornerRadius = resources.getDimension(R.dimen.card_corner_radius)  // match your 8dp card
        val strokeWidth = resources.getDimension(R.dimen.stroke_width)         // e.g. 2dp
        val extraOffset = resources.getDimensionPixelSize(R.dimen.swipe_offset) // e.g. 16dp

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT
        ) {
            override fun isLongPressDragEnabled() = true
            override fun isItemViewSwipeEnabled() = true

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
                val list = receiptAdapter.currentList.toMutableList()
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                list.add(to, list.removeAt(from))
                receiptAdapter.submitList(list)

                lifecycleScope.launch {
                    repository.updateReceiptOrder(list)
                }

                return true
            }

            // Swipe: delete + undo
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.bindingAdapterPosition
                val receipt = receiptAdapter.currentList[pos]
                recentlyDeletedReceipt = receipt

                lifecycleScope.launch {
                    repository.deleteReceipt(receipt.id)
                    loadData()
                }

                Snackbar.make(
                    binding.root,
                    getString(R.string.delete) + ": \"${receipt.merchantName}\"",
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.undo) {
                    recentlyDeletedReceipt?.let {
                        lifecycleScope.launch {
                            repository.saveReceipt(it)
                            loadData()
                        }
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
                    val iconLeft = (right - iconMargin - iconWidth).toInt()
                    val iconTop = (top + iconMargin).toInt()
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

        ItemTouchHelper(callback).attachToRecyclerView(binding.rvReceipts)
    }

    private fun logout() {
        authRepository.logout()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}