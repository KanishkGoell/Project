package com.application.ocr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.application.ocr.data.DocumentRepository
import com.application.ocr.databinding.ActivityDocumentViewerBinding
import com.application.ocr.model.Document
import com.application.ocr.utils.FileUtils
import java.util.Date
import java.util.UUID          // ← add at the top






class DocumentViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOCUMENT_ID = "extra_document_id"
        const val EXTRA_TEXT_CONTENT = "extra_text_content"
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_IS_NEW_DOCUMENT = "extra_is_new_document"
    }

    private lateinit var binding: ActivityDocumentViewerBinding
    private lateinit var repository: DocumentRepository

    private var documentId: String? = null
    private var isNewDocument: Boolean = false
    private var textContent: String? = null
    private var imagePath: String? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        repository = DocumentRepository(this)

        processIntent()
        setupViews()
        setupListeners()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_document_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }



            R.id.action_share -> {
                shareDocument()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun processIntent() {
        intent?.let {
            documentId = it.getStringExtra(EXTRA_DOCUMENT_ID)
            textContent = it.getStringExtra(EXTRA_TEXT_CONTENT)
            imagePath = it.getStringExtra(EXTRA_IMAGE_PATH)
            isNewDocument = it.getBooleanExtra(EXTRA_IS_NEW_DOCUMENT, false)

            if (documentId != null) {
                loadDocument(documentId!!)
            } else if (textContent != null) {
                // New scan from camera or gallery
                binding.etDocumentContent.setText(textContent)

                // Generate a title from the first line of content
                val title = textContent?.split("\n")?.firstOrNull()?.take(30) ?: "Scanned Document"
                binding.etDocumentTitle.setText(title)

                // Load image if available
                if (imagePath != null) {
                    try {
                        val uri = Uri.parse(imagePath)
                        binding.ivDocumentImage.setImageURI(uri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun setupViews() {
        binding.toolbar.title = if (isNewDocument) {
            getString(R.string.document_viewer)
        } else {
            binding.etDocumentTitle.text.toString()
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveDocument()
        }

        binding.btnExportPdf.setOnClickListener {
            exportToPdf()
        }
    }

    private fun loadDocument(id: String) {
        val document = repository.getDocumentById(id)
        document?.let {
            binding.etDocumentTitle.setText(it.title)
            binding.etDocumentContent.setText(it.content)

            it.imagePath?.let { path ->
                val bmp = FileUtils.loadImageFromPath(this, path)
                bmp?.let { binding.ivDocumentImage.setImageBitmap(it) }
            }
        }
    }


    private fun saveDocument() {
        val title = binding.etDocumentTitle.text.toString().trim()
        val content = binding.etDocumentContent.text.toString().trim()

        if (title.isEmpty()) {
            binding.etDocumentTitle.error = getString(R.string.title_required)
            return
        }

        val doc = Document(
            id = documentId ?: UUID.randomUUID().toString(),
            title = title,
            content = content,
            imagePath = imagePath,
            createdAt = if (isNewDocument) Date() else repository.getDocumentById(documentId!!)!!.createdAt,
            updatedAt = Date()
        )

        val success = if (isNewDocument || documentId == null) {
            repository.saveDocument(doc)
        } else {
            repository.updateDocument(doc)
        }

        Toast.makeText(
            this,
            if (success) R.string.document_saved else R.string.error_saving,
            Toast.LENGTH_SHORT
        ).show()

        finish()
    }
    private fun deleteDocument() {
        documentId?.let {
            val ok = repository.deleteDocument(it)
            Toast.makeText(this,
                if (ok) R.string.delete else R.string.error_saving,
                Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun exportToPdf() {
        // 1. Create the PDF
        val title   = binding.etDocumentTitle.text.toString().trim()
        val content = binding.etDocumentContent.text.toString().trim()
        val pdfUri  = FileUtils.createPdfFromText(this, title, content)

        if (pdfUri != null) {
            // 2. Tell the user
            Toast.makeText(this, R.string.pdf_created, Toast.LENGTH_SHORT).show()

            // 3. Fire share intent
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.also { shareIntent ->
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            }
        } else {
            Toast.makeText(this, R.string.error_creating_pdf, Toast.LENGTH_SHORT).show()
        }
    }


    private fun shareDocument() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, binding.etDocumentTitle.text.toString())
            putExtra(Intent.EXTRA_TEXT, binding.etDocumentContent.text.toString())
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }

}