package com.application.ocr.data

import android.content.Context
import com.application.ocr.model.Document
import java.util.Date

class DocumentRepository(context: Context) {
    private val dbHelper = DatabaseHelper(context)

    // DocumentRepository.kt
    fun updateOrder(documents: List<Document>) {
        documents.forEachIndexed { index, doc ->
            // copy in-place with new order and only update the ord column
            val updated = doc.copy(order = index)
            dbHelper.updateDocument(updated)   // your update method writes ord too
        }
    }


    fun saveDocument(document: Document): Boolean {
        return dbHelper.saveDocument(document) != -1L
    }

    fun updateDocument(document: Document): Boolean {
        val updatedDocument = document.copy(updatedAt = Date())
        return dbHelper.updateDocument(updatedDocument) > 0
    }

    fun deleteDocument(documentId: String): Boolean {
        return dbHelper.deleteDocument(documentId) > 0
    }

    fun getAllDocuments(): List<Document> {
        return dbHelper.getAllDocuments()
    }

    fun getDocumentById(id: String): Document? {
        return dbHelper.getDocumentById(id)
    }
}