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
    val printingId: Int,
    val name: String,
    val setCode: String,
    val collectorNumber: String,
    val priceCents: Int
) {
    val priceUsd: Double
        get() = priceCents / 100.0
}

/**
 * High-speed SQLite helper for reading card identities (name, set, collector number, price, printing_id)
 * for all 112,815 MTG printings directly on-device.
 */
class CardDatabaseHelper(private val context: Context) {

    private var db: SQLiteDatabase? = null
    private val DB_VERSION = 3 // Bump to force refresh

    suspend fun openDatabase() = withContext(Dispatchers.IO) {
        if (db != null && db!!.isOpen) return@withContext

        val dbFile = File(context.filesDir, "card-identities.db")
        val prefs = context.getSharedPreferences("card_db_prefs", Context.MODE_PRIVATE)
        val installedVersion = prefs.getInt("installed_version", 0)

        if (!dbFile.exists() || installedVersion < DB_VERSION) {
            Log.d("CardDatabaseHelper", "Extracting fresh card-identities.db v$DB_VERSION from assets...")
            if (dbFile.exists()) dbFile.delete()
            context.assets.open("card-identities.db").use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            prefs.edit().putInt("installed_version", DB_VERSION).apply()
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

    fun findCardsByName(name: String, setCodeHint: String? = null): List<CardIdentity> {
        val clean = name.replace(Regex("""[0-9/\{\}★☆]"""), "").trim()
        if (clean.length < 2) return emptyList()

        // 1. If set code hint is known, query exact name in that specific set first
        if (!setCodeHint.isNullOrBlank()) {
            val setSpecific = queryByNameAndSet(clean, setCodeHint)
            if (setSpecific.isNotEmpty()) return setSpecific
        }

        // 2. Exact name match across all sets (high limit to include all basic lands & reprints)
        val exact = queryByNameExact(clean)
        if (exact.isNotEmpty()) return exact

        // 3. Prefix match
        val prefix = queryByNamePrefix(clean)
        if (prefix.isNotEmpty()) return prefix

        // 4. Substring match
        val sub = queryByNameSubstring(clean)
        if (sub.isNotEmpty()) return sub

        // 5. Fuzzy Word Fallback: If OCR had a typo in one word (e.g. "Disdainful Stroke" -> "Disdainful Strike")
        val words = clean.split(" ").filter { it.length >= 4 }
        if (words.isNotEmpty()) {
            val firstWord = words.first()
            val fuzzy = queryByNamePrefix(firstWord)
            if (fuzzy.isNotEmpty()) return fuzzy
        }

        return emptyList()
    }

    private fun queryByNameAndSet(name: String, setCode: String): List<CardIdentity> {
        val database = db ?: return emptyList()
        val cursor = database.rawQuery(
            "SELECT row_id, printing_id, name, set_code, collector_number, price_cents FROM printings WHERE name = ? COLLATE NOCASE AND set_code = ? COLLATE NOCASE LIMIT 100",
            arrayOf(name, setCode)
        )
        val list = mutableListOf<CardIdentity>()
        try {
            while (cursor.moveToNext()) {
                list.add(CardIdentity(cursor.getInt(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5)))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    private fun queryByNameExact(clean: String): List<CardIdentity> {
        val database = db ?: return emptyList()
        val cursor = database.rawQuery(
            "SELECT row_id, printing_id, name, set_code, collector_number, price_cents FROM printings WHERE name = ? COLLATE NOCASE LIMIT 250",
            arrayOf(clean)
        )
        val list = mutableListOf<CardIdentity>()
        try {
            while (cursor.moveToNext()) {
                list.add(CardIdentity(cursor.getInt(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5)))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    private fun queryByNamePrefix(clean: String): List<CardIdentity> {
        val database = db ?: return emptyList()
        val cursor = database.rawQuery(
            "SELECT row_id, printing_id, name, set_code, collector_number, price_cents FROM printings WHERE name LIKE ? LIMIT 150",
            arrayOf("$clean%")
        )
        val list = mutableListOf<CardIdentity>()
        try {
            while (cursor.moveToNext()) {
                list.add(CardIdentity(cursor.getInt(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5)))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    private fun queryByNameSubstring(clean: String): List<CardIdentity> {
        val database = db ?: return emptyList()
        val cursor = database.rawQuery(
            "SELECT row_id, printing_id, name, set_code, collector_number, price_cents FROM printings WHERE name LIKE ? LIMIT 100",
            arrayOf("%$clean%")
        )
        val list = mutableListOf<CardIdentity>()
        try {
            while (cursor.moveToNext()) {
                list.add(CardIdentity(cursor.getInt(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5)))
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
        val query = "SELECT row_id, printing_id, name, set_code, collector_number, price_cents FROM printings WHERE row_id IN ($inClause)"
        val cursor = database.rawQuery(query, null)
        val result = mutableMapOf<Int, CardIdentity>()

        try {
            while (cursor.moveToNext()) {
                val rowId = cursor.getInt(0)
                val printingId = cursor.getInt(1)
                val name = cursor.getString(2)
                val setCode = cursor.getString(3)
                val collector = cursor.getString(4)
                val priceCents = cursor.getInt(5)
                result[rowId] = CardIdentity(rowId, printingId, name, setCode, collector, priceCents)
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