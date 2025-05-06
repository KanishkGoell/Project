package com.application.ocr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.application.ocr.data.FirestoreRepository
import com.application.ocr.databinding.ActivityReceiptViewerBinding
import com.application.ocr.model.Receipt
import com.application.ocr.model.ReceiptRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.util.Date
import java.util.UUID

class ReceiptViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECEIPT_ID = "extra_receipt_id"
        const val EXTRA_TABLE = "extra_table"
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_IS_NEW_RECEIPT = "extra_is_new_receipt"
        const val EXTRA_MERCHANT_NAME = "extra_merchant_name"
    }

    private lateinit var binding: ActivityReceiptViewerBinding
    private val repository = FirestoreRepository()

    private var receiptId: String? = null
    private var isNewReceipt: Boolean = false
    private var rows: List<ReceiptRow> = emptyList()
    private var imagePath: String? = null
    private var currentReceipt: Receipt? = null
    private var merchantName: String = "Unknown Merchant"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiptViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        processIntent()
        setupViews()
        setupListeners()
    }

    private fun processIntent() {
        intent?.let {
            receiptId = it.getStringExtra(EXTRA_RECEIPT_ID)
            isNewReceipt = it.getBooleanExtra(EXTRA_IS_NEW_RECEIPT, false)
            imagePath = it.getStringExtra(EXTRA_IMAGE_PATH)
            merchantName = it.getStringExtra(EXTRA_MERCHANT_NAME) ?: "Unknown Merchant"
            // inside processIntent():
            merchantName = it.getStringExtra(EXTRA_MERCHANT_NAME) ?: "Unknown Merchant"
            binding.etMerchantName.setText(merchantName)
            @Suppress("UNCHECKED_CAST")
            val extractedRows = it.getSerializableExtra(EXTRA_TABLE) as? ArrayList<ReceiptRow>
            if (extractedRows != null) {
                rows = extractedRows
            }

            if (receiptId != null) {
                loadReceipt(receiptId!!)
            } else {
                // Set up for new receipt
                setupRecyclerView(rows)

                // Load image if available
                if (imagePath != null) {
                    try {
                        val uri = Uri.parse(imagePath)
                        binding.ivReceipt.setImageURI(uri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Set the merchant name in the toolbar
                supportActionBar?.title = merchantName
            }
        }
    }

    private fun setupViews() {
        binding.toolbar.title = if (isNewReceipt) {
            getString(R.string.receipt_viewer)
        } else {
            binding.etMerchantName.text.toString().ifBlank { "Receipt Viewer" }
        }
    }


    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveReceipt()
        }

        binding.btnExportCsv.setOnClickListener {
            shareCsv()
        }
    }

    private fun loadReceipt(id: String) {
        lifecycleScope.launch {
            val receipt = repository.getReceiptById(id)
            receipt?.let {
                currentReceipt = it
                rows = it.items
                supportActionBar?.title = it.merchantName
                setupRecyclerView(it.items)

                it.imagePath?.let { path ->
                    try {
                        binding.ivReceipt.setImageURI(Uri.parse(path))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun setupRecyclerView(items: List<ReceiptRow>) {
        binding.rvTable.layoutManager = LinearLayoutManager(this)
        binding.rvTable.adapter = TableAdapter(items)
    }

    private fun saveReceipt() {
        lifecycleScope.launch {
            // ─────────────────────── 1. Prepare image ───────────────────────────
            var finalImagePath = imagePath
            if (!imagePath.isNullOrEmpty() && !imagePath!!.startsWith("http")) {
                // Upload to Firebase Storage
                val safeUri = runCatching {
                    val original = Uri.parse(imagePath)
                    if (original.scheme == "content") original
                    else {
                        val file = File(original.path!!)
                        FileProvider.getUriForFile(
                            this@ReceiptViewerActivity,
                            "${packageName}.fileprovider",
                            file
                        )
                    }
                }.getOrNull()

                safeUri?.let { uri ->
                    repository.uploadImage(this@ReceiptViewerActivity, uri)
                        ?.let { uploadedUrl ->
                            finalImagePath = uploadedUrl
                        }
                }
            }

            // ─────────────────────── 2. Calculate total amount ─────────────────────
            val totalAmount = rows.sumOf { it.price }

            // ─────────────────────── 3. Build Receipt object ───────────────────
            val receipt = Receipt(
                id = receiptId ?: UUID.randomUUID().toString(),
                items = rows,
                imagePath = finalImagePath,
                createdAt = if (isNewReceipt) Date() else currentReceipt?.createdAt ?: Date(),
                updatedAt = Date(),
                order = currentReceipt?.order ?: 0,
                totalAmount = totalAmount,
                merchantName = binding.etMerchantName.text.toString().trim(),
            )

            // ─────────────────────── 4. Save or update ──────────────────────────
            val success = if (isNewReceipt || receiptId == null) {
                repository.saveReceipt(receipt)
            } else {
                repository.updateReceipt(receipt)
            }

            Toast.makeText(
                this@ReceiptViewerActivity,
                if (success) R.string.receipt_saved else R.string.error_saving,
                Toast.LENGTH_SHORT
            ).show()

            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_receipt_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_share -> {
                shareReceipt()
                true
            }
            R.id.action_delete -> {
                deleteReceipt()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun deleteReceipt() {
        receiptId?.let {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_delete))
                .setPositiveButton(R.string.yes) { _, _ ->
                    lifecycleScope.launch {
                        val ok = repository.deleteReceipt(it)
                        Toast.makeText(
                            this@ReceiptViewerActivity,
                            if (ok) R.string.delete_success else R.string.error_deleting,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    private fun shareReceipt() {
        val shareText = buildString {
            append("Receipt from ${currentReceipt?.merchantName ?: merchantName}\n\n")
            rows.forEach { row ->
                append("${row.item}: $${String.format("%.2f", row.price)}\n")
            }
            append("\nTotal: $${String.format("%.2f", rows.sumOf { it.price })}")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Receipt from ${currentReceipt?.merchantName ?: merchantName}")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    private fun shareCsv() {
        if (rows.isEmpty()) {
            Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { createCsv() } ?: return@launch
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.also { startActivity(Intent.createChooser(it, getString(R.string.export_csv))) }
        }
    }

    private fun createCsv(): Uri? = try {
        val file = File(cacheDir, "receipt_${UUID.randomUUID()}.csv")
        FileWriter(file).use { w ->
            // Write header
            w.append("Item,Price\n")
            // Write data
            rows.forEach { r -> w.append("${r.item},${r.price}\n") }
            // Write total
            w.append("Total,${rows.sumOf { it.price }}\n")
        }
        FileProvider.getUriForFile(this, "$packageName.provider", file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}