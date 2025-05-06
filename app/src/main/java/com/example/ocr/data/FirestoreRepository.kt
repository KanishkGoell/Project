package com.application.ocr.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.application.ocr.model.Document
import com.application.ocr.model.Receipt
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Date
import java.util.UUID

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://ocrapp-78bed.appspot.com")

    private val documentsCollection = "documents"
    private val receiptsCollection = "receipts"

    // Get current user ID, or empty string if not logged in
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    // Save document to Firestore
    suspend fun saveDocument(document: Document): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1) inject the UID
            val docWithUserId = document.copy(userId = currentUserId)

            // 2) dump the map for inspection
            val data = docWithUserId.toMap()
            Log.d("FIRESTORE", "🔍 map going up = $data")

            // 3) write it
            val documentRef = db.collection(documentsCollection)
                .document(docWithUserId.id)
            documentRef.set(data).await()

            return@withContext true
        } catch (e: Exception) {
            Log.e("FIRESTORE", "saveDocument failed", e)
            return@withContext false
        }
    }

    // Update existing document
    suspend fun updateDocument(document: Document): Boolean = withContext(Dispatchers.IO) {
        try {
            val updatedDoc = document.copy(updatedAt = Date(), userId = currentUserId)
            val documentRef = db.collection(documentsCollection).document(document.id)
            documentRef.update(updatedDoc.toMap()).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Delete document
    suspend fun deleteDocument(documentId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection(documentsCollection)
                .document(documentId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Get all documents for current user
    suspend fun getAllDocuments(): List<Document> = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection(documentsCollection)
                .whereEqualTo("userId", currentUserId)
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .await()
            return@withContext snapshot.documents.mapNotNull { it.toObject(Document::class.java) }
        } catch (e: Exception) {
            Log.e("FIRESTORE", "getAllDocuments failed", e)
            return@withContext emptyList()
        }
    }

    // Get document by ID
    suspend fun getDocumentById(id: String): Document? = withContext(Dispatchers.IO) {
        try {
            val documentRef = db.collection(documentsCollection).document(id)
            val document = documentRef.get().await()
            document.toObject(Document::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Update document order
    suspend fun updateOrder(documents: List<Document>) = withContext(Dispatchers.IO) {
        try {
            val batch = db.batch()

            documents.forEachIndexed { index, doc ->
                val docRef = db.collection(documentsCollection).document(doc.id)
                batch.update(docRef, mapOf("order" to index))
            }

            batch.commit().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Upload image to Firebase Storage
    suspend fun uploadImage(
        context: Context,
        imageUri: Uri
    ): String? = withContext(Dispatchers.IO) {
        try {
            val filename = "images/$currentUserId/${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child(filename)

            // 1) content://  → simple putFile
            // 2) file:// or anything else → openInputStream & putStream
            val uploadTask = when (imageUri.scheme) {
                "content" -> ref.putFile(imageUri)
                else -> {
                    val stream: InputStream =
                        context.contentResolver.openInputStream(imageUri)
                            ?: return@withContext null   // couldn't read
                    ref.putStream(stream)
                }
            }

            uploadTask.await()
            ref.downloadUrl.await().toString()            // public URL
        } catch (e: Exception) {
            Log.e("STORAGE", "uploadImage failed for $imageUri", e)
            null
        }
    }

    // RECEIPT METHODS - NEW

    // Save receipt to Firestore
    suspend fun saveReceipt(receipt: Receipt): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1) inject the UID
            val receiptWithUserId = receipt.copy(userId = currentUserId)

            // 2) dump the map for inspection
            val data = receiptWithUserId.toMap()
            Log.d("FIRESTORE", "🔍 receipt map going up = $data")

            // 3) write it
            val receiptRef = db.collection(receiptsCollection)
                .document(receiptWithUserId.id)
            receiptRef.set(data).await()

            return@withContext true
        } catch (e: Exception) {
            Log.e("FIRESTORE", "saveReceipt failed", e)
            return@withContext false
        }
    }

    // Update existing receipt
    suspend fun updateReceipt(receipt: Receipt): Boolean = withContext(Dispatchers.IO) {
        try {
            val updatedReceipt = receipt.copy(updatedAt = Date(), userId = currentUserId)
            val receiptRef = db.collection(receiptsCollection).document(receipt.id)
            receiptRef.update(updatedReceipt.toMap()).await()
            true
        } catch (e: Exception) {
            Log.e("FIRESTORE", "updateReceipt failed", e)
            false
        }
    }

    // Delete receipt
    suspend fun deleteReceipt(receiptId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection(receiptsCollection)
                .document(receiptId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.e("FIRESTORE", "deleteReceipt failed", e)
            false
        }
    }

    // Get all receipts for current user
    suspend fun getAllReceipts(): List<Receipt> = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection(receiptsCollection)
                .whereEqualTo("userId", currentUserId)
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .await()
            return@withContext snapshot.documents.mapNotNull { it.toObject(Receipt::class.java) }
        } catch (e: Exception) {
            Log.e("FIRESTORE", "getAllReceipts failed", e)
            return@withContext emptyList()
        }
    }

    // Get receipt by ID
    suspend fun getReceiptById(id: String): Receipt? = withContext(Dispatchers.IO) {
        try {
            val receiptRef = db.collection(receiptsCollection).document(id)
            val receipt = receiptRef.get().await()
            receipt.toObject(Receipt::class.java)
        } catch (e: Exception) {
            Log.e("FIRESTORE", "getReceiptById failed", e)
            null
        }
    }

    // Update receipt order
    suspend fun updateReceiptOrder(receipts: List<Receipt>) = withContext(Dispatchers.IO) {
        try {
            val batch = db.batch()

            receipts.forEachIndexed { index, receipt ->
                val receiptRef = db.collection(receiptsCollection).document(receipt.id)
                batch.update(receiptRef, mapOf("order" to index))
            }

            batch.commit().await()
            true
        } catch (e: Exception) {
            Log.e("FIRESTORE", "updateReceiptOrder failed", e)
            false
        }
    }
}