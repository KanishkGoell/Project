package com.application.ocr.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    private const val TAG = "FileUtils"
    private const val IMAGE_DIRECTORY = "OCRScanner/Images"
    private const val PDF_DIRECTORY = "OCRScanner/PDFs"

    /* ---------- image helpers ---------- */

    fun saveImageToInternalStorage(context: Context, bitmap: Bitmap): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "IMG_$timeStamp.jpg"
        return try {
            val dir = File(context.filesDir, IMAGE_DIRECTORY).apply { mkdirs() }
            val file = File(dir, filename)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Error saving image: ${e.message}")
            null
        }
    }

    /** Decodes either a content://, file:// or plain path string into a Bitmap. */
    fun loadImageFromPath(context: Context, path: String?): Bitmap? {
        if (path == null) return null
        return try {
            val uri = Uri.parse(path)
            when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT,
                ContentResolver.SCHEME_FILE -> {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                else -> BitmapFactory.decodeFile(path)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image: ${e.message}")
            null
        }
    }

    /* ---------- PDF helpers ---------- */

    fun createPdfFromText(context: Context, title: String, content: String): Uri? {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdf.startPage(pageInfo)

        val canvas = page.canvas
        val titlePaint = android.graphics.Paint().apply {
            textSize = 18f
            color = android.graphics.Color.BLACK
        }
        val bodyPaint = android.graphics.Paint().apply { textSize = 12f }

        canvas.drawText(title, 50f, 50f, titlePaint)
        var y = 80f
        content.split("\n").forEach {
            canvas.drawText(it, 50f, y, bodyPaint)
            y += 20f
        }
        pdf.finishPage(page)

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${title.replace(" ", "_")}_$stamp.pdf"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                savePdfToMediaStore(context, pdf, fileName)
            else
                savePdfToExternalStorage(pdf, fileName)
        } catch (e: IOException) {
            Log.e(TAG, "Error creating PDF: ${e.message}")
            null
        } finally {
            pdf.close()
        }
    }

    private fun savePdfToExternalStorage(pdf: PdfDocument, fileName: String): Uri? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            PDF_DIRECTORY
        ).apply { mkdirs() }

        return try {
            val file = File(dir, fileName)
            pdf.writeTo(FileOutputStream(file))
            Uri.fromFile(file)
        } catch (e: IOException) {
            Log.e(TAG, "Error saving PDF: ${e.message}")
            null
        }
    }

    private fun savePdfToMediaStore(context: Context, pdf: PdfDocument, fileName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOCUMENTS}/$PDF_DIRECTORY"
            )
        }
        return context.contentResolver.insert(
            MediaStore.Files.getContentUri("external"),
            values
        )?.also { uri ->
            context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
        }
    }
}
