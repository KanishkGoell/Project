package com.application.ocr.model

import android.net.Uri
import java.io.Serializable
import java.util.Date
import java.util.UUID

// Document.kt
data class Document(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val imagePath: String?,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val order: Int = 0
) : Serializable {
    fun toContentValues(): Map<String, Any> = mapOf(
        "id" to id,
        "title" to title,
        "content" to content,
        "image_path" to (imagePath ?: ""),
        "created_at" to createdAt.time,
        "updated_at" to updatedAt.time,
        "ord" to order
    )
}
