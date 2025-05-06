package com.application.ocr.model

import java.io.Serializable

data class ReceiptRow(
    val item: String = "",
    val price: Double = 0.0
) : Serializable {
    // Empty constructor for Firestore
    constructor() : this("", 0.0)
}