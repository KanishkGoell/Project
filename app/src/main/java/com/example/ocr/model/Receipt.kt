package com.application.ocr.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.io.Serializable
import java.util.Date
import java.util.UUID

data class Receipt(
    @DocumentId
    val id: String = UUID.randomUUID().toString(),
    val items: List<ReceiptRow> = emptyList(),
    val imagePath: String? = null,
    @ServerTimestamp
    val createdAt: Date = Date(),
    @ServerTimestamp
    val updatedAt: Date = Date(),
    val order: Int = 0,
    val userId: String = "",  // Assign the user's ID to documents
    val totalAmount: Double = 0.0,
    val merchantName: String = "Unknown Merchant"
) : Serializable {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        items = emptyList(),
        imagePath = null,
        createdAt = Date(),
        updatedAt = Date(),
        order = 0,
        userId = "",
        totalAmount = 0.0,
        merchantName = "Unknown Merchant"
    )

    // Convert to Map for Firestore
    fun toMap(): Map<String, Any?> = mapOf(
        "items" to items,
        "imagePath" to imagePath,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "order" to order,
        "userId" to userId,
        "totalAmount" to totalAmount,
        "merchantName" to merchantName
    )
}