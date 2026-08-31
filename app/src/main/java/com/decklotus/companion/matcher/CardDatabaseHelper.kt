package com.decklotus.companion.matcher

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class CardIdentity(
    val rowId: Int,
    val name: String,
    val setCode: String,
    val collectorNumber: String,
    val priceCents: Int
) {
    val priceUsd: Double
        get() = priceCents / 100.0
}

/**
 * High-speed SQLite helper for reading card identities (name, set, collector number, price)
 * for all 112,815 MTG printings directly on-device.
 */
class CardDatabaseHelper(private val context: Context) {

    private var db: SQLiteDatabase? = null

    suspend fun openDatabase() = withContext(Dispatchers.IO) {
        if (db != null && db!!.isOpen) return@withContext

        val dbFile = File(context.filesDir, "card-identities.db")
        if (!dbFile.exists() || dbFile.length() < 1000) {
            Log.d("CardDatabaseHelper", "Extracting card-identities.db from assets...")
            context.assets.open("card-identities.db").use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("CardDatabaseHelper", "Extracted card-identities.db (${dbFile.length() / 1024} KB)")
        }

        db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun findBestMatchingCardFromLines(lines: List<String>): List<CardIdentity> {
        val database = db ?: return emptyList()

        for (rawLine in lines) {
            val line = rawLine.replace(Regex("""[0-9/\{\}★☆]"""), "").trim()
            if (line.length < 3) continue

            // 1. Exact match (case-insensitive)
            val exactList = queryByNameExact(line)
            if (exactList.isNotEmpty()) return exactList

            // 2. Prefix match if line is long enough
            if (line.length >= 5) {
                val prefixList = queryByNamePrefix(line)
                if (prefixList.isNotEmpty()) return prefixList
            }
        }

        return emptyList()
    }

    fun findCardsByName(name: String): List<CardIdentity> {
        val clean = name.replace(Regex("""[0-9/\{\}★☆]"""), "").trim()
        if (clean.length < 2) return emptyList()

        val exact = queryByNameExact(clean)
        if (exact.isNotEmpty()) return exact

        val prefix = queryByNamePrefix(clean)
        if (prefix.isNotEmpty()) return prefix

        return queryByNameSubstring(clean)
    }

    private fun queryByNameExact(clean: String): List<CardIdentity> {
        val database = db ?: return emptyList()
        val cursor = database.rawQuery(
            "SELECT row_id, name, set_code, collector_number, price_cents FROM printings WHERE name = ? COLLATE NOCASE LIMIT 40",
            arrayOf(clean)
        )
        val list = mutableListOf<CardIdentity>()
        try {
            while (cursor.moveToNext()) {
                list.add(CardIdentity(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4)))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    private fun queryByNamePrefix(clean: String): List<CardIdentity> {
        val database = db ?: return emptyList()
        val cursor = database.rawQuery(
            "SELECT row_id, name, set_code, collector_number, price_cents FROM printings WHERE name LIKE ? LIMIT 40",
            arrayOf("$clean%")
        )
        val list = mutableListOf<CardIdentity>()
        try {
            while (cursor.moveToNext()) {
                list.add(CardIdentity(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4)))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    private fun queryByNameSubstring(clean: String): List<CardIdentity> {
        val database = db ?: return emptyList()
        val cursor = database.rawQuery(
            "SELECT row_id, name, set_code, collector_number, price_cents FROM printings WHERE name LIKE ? LIMIT 40",
            arrayOf("%$clean%")
        )
        val list = mutableListOf<CardIdentity>()
        try {
            while (cursor.moveToNext()) {
                list.add(CardIdentity(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4)))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun getIdentitiesForRows(rowIds: List<Int>): Map<Int, CardIdentity> {
        val database = db ?: return emptyMap()
        if (rowIds.isEmpty()) return emptyMap()

        val inClause = rowIds.joinToString(",")
        val query = "SELECT row_id, name, set_code, collector_number, price_cents FROM printings WHERE row_id IN ($inClause)"
        val cursor = database.rawQuery(query, null)
        val result = mutableMapOf<Int, CardIdentity>()

        try {
            while (cursor.moveToNext()) {
                val rowId = cursor.getInt(0)
                val name = cursor.getString(1)
                val setCode = cursor.getString(2)
                val collector = cursor.getString(3)
                val priceCents = cursor.getInt(4)
                result[rowId] = CardIdentity(rowId, name, setCode, collector, priceCents)
            }
        } finally {
            cursor.close()
        }

        return result
    }

    fun close() {
        try {
            db?.close()
        } catch (_: Exception) {}
        db = null
    }
}