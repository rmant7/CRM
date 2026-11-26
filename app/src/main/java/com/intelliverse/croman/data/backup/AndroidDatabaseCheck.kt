package com.intelliverse.croman.data.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Checks a restored SQLite file before it replaces the live database. */
object AndroidDatabaseCheck : DatabaseValidator {
    override fun check(database: File, expectedIdentity: String?): String? {
        val db = SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != "ok") return "integrity"
            }
            if (expectedIdentity != null) {
                val hash = identity(db) ?: return "schema"
                if (hash != expectedIdentity) return "schema"
            }
            null
        } catch (error: Exception) {
            error.message ?: "schema"
        } finally {
            db.close()
        }
    }

    fun identityOf(file: File): String? {
        if (!file.isFile) return null
        return try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                identity(db)
            } finally {
                db.close()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun identity(db: SQLiteDatabase): String? =
        db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
