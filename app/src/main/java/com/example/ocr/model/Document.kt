package com.application.ocr.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.io.Serializable
import java.util.Date
import java.util.UUID

data class Document(
    @DocumentId
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val imagePath: String? = null,
    @ServerTimestamp
    val createdAt: Date = Date(),
    @ServerTimestamp
    val updatedAt: Date = Date(),
    val order: Int = 0,
    val userId: String = ""  // Assign the user's ID to documents
) : Serializable {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        title = "",
        content = "",
        imagePath = null,
        createdAt = Date(),
        updatedAt = Date(),
        order = 0,
        userId = ""
    )

    // Convert to Map for Firestore
    fun toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "content" to content,
        "imagePath" to imagePath,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "order" to order,
        "userId" to userId
    )
}