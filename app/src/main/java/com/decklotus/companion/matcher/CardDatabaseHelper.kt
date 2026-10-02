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
    /**
     * Null when nobody has priced this printing — 15,361 of the 112,815 rows.
     * It is not zero and it is not the bulk-common rate: the asset used to
     * substitute $0.26 for these, which showed as a confident price for a card
     * the server would decline to value at all.
     */
    val priceCents: Int?,
    /**
     * Which price row the figure came from, 'normal' or 'foil', or null with
     * the price. A foil-derived figure is the most inflated one available —
     * the 10,972 printings with no normal price are the showcase and
     * serialised ones — so the UI has to be able to mark it rather than quote
     * it flat. Mirrors what the server sends the web scanner.
     */
    val priceType: String?
) {
    val priceUsd: Double?
        get() = priceCents?.let { it / 100.0 }

    /** True when the only figure available came from the foil row. */
    val isFoilDerivedPrice: Boolean
        get() = priceType == "foil"
}

/**
 * High-speed SQLite helper for reading card identities (name, set, collector number, price, printing_id)
 * for all 112,815 MTG printings directly on-device.
 */
/**
 * The columns every identity query selects, in the order [readIdentities]
 * reads them. One list so a schema change lands in one place rather than in
 * five rawQuery strings that have to be kept in step by hand.
 */
private const val IDENTITY_COLUMNS =
    "row_id, printing_id, name, set_code, collector_number, price_cents, price_type"

/**
 * Walk a cursor opened over [IDENTITY_COLUMNS] and close it.
 *
 * price_cents and price_type are read as nullable: getInt() would turn an
 * unpriced row into a confident $0.00, which is the same lie the old $0.26
 * placeholder told, one column further along.
 */
private fun android.database.Cursor.readIdentities(): List<CardIdentity> {
    val list = mutableListOf<CardIdentity>()
    try {
        while (moveToNext()) {
            list.add(
                CardIdentity(
                    rowId = getInt(0),
                    printingId = getInt(1),
                    name = getString(2),
                    setCode = getString(3),
                    collectorNumber = getString(4),
                    priceCents = if (isNull(5)) null else getInt(5),
                    priceType = if (isNull(6)) null else getString(6)
                )
            )
        }
    } finally {
        close()
    }
    return list
}

data class DatabaseStats(
    val totalPrintings: Int = 0,
    val totalSets: Int = 0,
    val lastSyncTimestamp: Long = 0L,
    val fileSizeBytes: Long = 0L
)

class CardDatabaseHelper(private val context: Context) {

    private var db: SQLiteDatabase? = null
    // 4 adds price_type and makes price_cents nullable. A bump re-extracts
    // the asset, which is the only way an installed app picks up a new schema.
    private val DB_VERSION = 4

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

    suspend fun reload() = withContext(Dispatchers.IO) {
        close()
        val dbFile = File(context.filesDir, "card-identities.db")
        if (dbFile.exists()) {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            Log.d("CardDatabaseHelper", "Reloaded card-identities.db from ${dbFile.absolutePath}")
        }
    }

    suspend fun getStats(): DatabaseStats = withContext(Dispatchers.IO) {
        if (db == null || !db!!.isOpen) {
            openDatabase()
        }
        val database = db ?: return@withContext DatabaseStats()
        val dbFile = File(context.filesDir, "card-identities.db")
        val prefs = context.getSharedPreferences("card_db_prefs", Context.MODE_PRIVATE)
        val lastSync = prefs.getLong("last_sync_timestamp", 0L)

        var totalPrintings = 0
        var totalSets = 0

        try {
            database.rawQuery("SELECT count(*), count(distinct set_code) FROM printings", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    totalPrintings = cursor.getInt(0)
                    totalSets = cursor.getInt(1)
                }
            }
        } catch (e: Exception) {
            Log.w("CardDatabaseHelper", "Failed to query database stats: ${e.message}")
        }

        DatabaseStats(
            totalPrintings = totalPrintings,
            totalSets = totalSets,
            lastSyncTimestamp = lastSync,
            fileSizeBytes = if (dbFile.exists()) dbFile.length() else 0L
        )
    }

    private fun normalizeName(raw: String): String =
        raw.replace("’", "'")
            .replace("`", "'")
            .replace("‘", "'")
            .replace(Regex("""[0-9/\{\}★☆]"""), "")
            .trim()

    fun findBestMatchingCardFromLines(lines: List<String>): List<CardIdentity> {
        val database = db ?: return emptyList()

        for (rawLine in lines) {
            val line = normalizeName(rawLine)
            if (line.length < 3) continue

            // 1. Exact match or DFC front-face match
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
        val clean = normalizeName(name)
        if (clean.length < 2) return emptyList()

        // 1. If set code hint is known, query exact name or DFC in that specific set first
        if (!setCodeHint.isNullOrBlank()) {
            val setSpecific = queryByNameAndSet(clean, setCodeHint)
            if (setSpecific.isNotEmpty()) return setSpecific
        }

        // 2. Exact name match or DFC across all sets (high limit to include all basic lands & reprints)
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
            "SELECT $IDENTITY_COLUMNS FROM printings WHERE (name = ? COLLATE NOCASE OR name LIKE ? COLLATE NOCASE) AND set_code = ? COLLATE NOCASE LIMIT 100",
            arrayOf(name, "$name // %", setCode)
        )
        return cursor.readIdentities()
    }

    private fun queryByNameExact(clean: String): List<CardIdentity> {
        val database = db ?: return emptyList()
        val cursor = database.rawQuery(
            "SELECT $IDENTITY_COLUMNS FROM printings WHERE name = ? COLLATE NOCASE OR name LIKE ? COLLATE NOCASE LIMIT 250",
            arrayOf(clean, "$clean // %")
        )
        return cursor.readIdentities()
    }

    private fun queryByNamePrefix(clean: String): List<CardIdentity> {
        val database = db ?: return emptyList()
        val cursor = database.rawQuery(
            "SELECT $IDENTITY_COLUMNS FROM printings WHERE name LIKE ? LIMIT 150",
            arrayOf("$clean%")
        )
        return cursor.readIdentities()
    }

    private fun queryByNameSubstring(clean: String): List<CardIdentity> {
        val database = db ?: return emptyList()
        val cursor = database.rawQuery(
            "SELECT $IDENTITY_COLUMNS FROM printings WHERE name LIKE ? LIMIT 100",
            arrayOf("%$clean%")
        )
        return cursor.readIdentities()
    }

    fun findCardsByCollectorNumbers(collectorNumbers: List<String>, setCodes: Collection<String>): List<CardIdentity> {
        val database = db ?: return emptyList()
        val validNums = collectorNumbers.map { it.trimStart('0').ifEmpty { "0" } }.filter { it.isNotBlank() }.distinct()
        val validSets = setCodes.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct()
        if (validNums.isEmpty()) return emptyList()

        val numPlaceholders = validNums.joinToString(",") { "?" }
        val args = mutableListOf<String>()
        args.addAll(validNums)
        args.addAll(validNums)

        val query = if (validSets.isNotEmpty()) {
            val setPlaceholders = validSets.joinToString(",") { "?" }
            args.addAll(validSets)
            "SELECT $IDENTITY_COLUMNS FROM printings WHERE (collector_number IN ($numPlaceholders) OR ltrim(collector_number, '0') IN ($numPlaceholders)) AND set_code IN ($setPlaceholders) COLLATE NOCASE LIMIT 50"
        } else {
            "SELECT $IDENTITY_COLUMNS FROM printings WHERE (collector_number IN ($numPlaceholders) OR ltrim(collector_number, '0') IN ($numPlaceholders)) LIMIT 50"
        }

        return database.rawQuery(query, args.toTypedArray()).readIdentities()
    }

    fun getIdentitiesForRows(rowIds: List<Int>): Map<Int, CardIdentity> {
        val database = db ?: return emptyMap()
        if (rowIds.isEmpty()) return emptyMap()

        val inClause = rowIds.joinToString(",")
        val query = "SELECT $IDENTITY_COLUMNS FROM printings WHERE row_id IN ($inClause)"
        return database.rawQuery(query, null)
            .readIdentities()
            .associateBy { it.rowId }
    }

    fun close() {
        try {
            db?.close()
        } catch (_: Exception) {}
        db = null
    }
}