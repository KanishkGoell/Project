package com.application.ocr.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.application.ocr.model.Document
import java.util.Date

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "OCRDocuments.db"

        // Table name
        private const val TABLE_DOCUMENTS = "documents"

        // Column names
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_CONTENT = "content"
        private const val KEY_IMAGE_PATH = "image_path"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_UPDATED_AT = "updated_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_DOCUMENTS (
                $KEY_ID TEXT PRIMARY KEY,
                $KEY_TITLE TEXT,
                $KEY_CONTENT TEXT,
                $KEY_IMAGE_PATH TEXT,
                $KEY_CREATED_AT INTEGER,
                $KEY_UPDATED_AT INTEGER,
                ord INTEGER
                
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion<2){
            db.execSQL("ALTER TABLE $TABLE_DOCUMENTS ADD COLUMN ord INTEGER DEFAULT 0")

        }
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DOCUMENTS")
        onCreate(db)
    }

    fun saveDocument(document: Document): Long {
        val db = this.writableDatabase
        val values = ContentValues()

        document.toContentValues().forEach { (key, value) ->
            when (value) {
                is String -> values.put(key, value)
                is Long -> values.put(key, value)
                is Int -> values.put(key, value)
            }
        }

        val result = db.insert(TABLE_DOCUMENTS, null, values)
        db.close()
        return result
    }

    fun updateDocument(document: Document): Int {
        val db = this.writableDatabase
        val values = ContentValues()

        document.toContentValues().forEach { (key, value) ->
            when (value) {
                is String -> values.put(key, value)
                is Long -> values.put(key, value)
                is Int -> values.put(key, value)
            }
        }

        val result = db.update(
            TABLE_DOCUMENTS,
            values,
            "$KEY_ID = ?",
            arrayOf(document.id)
        )
        db.close()
        return result
    }

    fun deleteDocument(documentId: String): Int {
        val db = this.writableDatabase
        val result = db.delete(
            TABLE_DOCUMENTS,
            "$KEY_ID = ?",
            arrayOf(documentId)
        )
        db.close()
        return result
    }

    fun getAllDocuments(): List<Document> {
        val documents = mutableListOf<Document>()
        val selectQuery = "SELECT * FROM $TABLE_DOCUMENTS ORDER BY ord ASC"

        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val document = cursorToDocument(cursor)
                documents.add(document)
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        return documents
    }

    fun getDocumentById(id: String): Document? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_DOCUMENTS,
            null,
            "$KEY_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )

        var document: Document? = null
        if (cursor.moveToFirst()) {
            document = cursorToDocument(cursor)
        }

        cursor.close()
        db.close()
        return document
    }

    private fun cursorToDocument(cursor: Cursor): Document {
        val idIndex = cursor.getColumnIndex(KEY_ID)
        val titleIndex = cursor.getColumnIndex(KEY_TITLE)
        val contentIndex = cursor.getColumnIndex(KEY_CONTENT)
        val imagePathIndex = cursor.getColumnIndex(KEY_IMAGE_PATH)
        val createdAtIndex = cursor.getColumnIndex(KEY_CREATED_AT)
        val updatedAtIndex = cursor.getColumnIndex(KEY_UPDATED_AT)

        return Document(
            id = cursor.getString(idIndex),
            title = cursor.getString(titleIndex),
            content = cursor.getString(contentIndex),
            imagePath = cursor.getString(imagePathIndex),
            createdAt = Date(cursor.getLong(createdAtIndex)),
            updatedAt = Date(cursor.getLong(updatedAtIndex))
        )
    }
}