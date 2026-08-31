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
 * SQLite helper for reading card identities (name, set, collector number, price)
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

    fun findByOcr(name: String?, setCode: String?, collector: String?): List<CardIdentity> {
        val database = db ?: return emptyMap<Int, CardIdentity>().values.toList()
        if (name == null && setCode == null && collector == null) return emptyList()

        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (!setCode.isNullOrBlank()) {
            conditions.add("set_code = ?")
            args.add(setCode.uppercase())
        }
        if (!collector.isNullOrBlank()) {
            conditions.add("collector_number = ?")
            args.add(collector)
        }
        if (!name.isNullOrBlank() && conditions.isEmpty()) {
            conditions.add("name LIKE ?")
            args.add("%$name%")
        }

        if (conditions.isEmpty()) return emptyList()

        val query = "SELECT row_id, name, set_code, collector_number, price_cents FROM printings WHERE " + conditions.joinToString(" AND ") + " LIMIT 10"
        val cursor = database.rawQuery(query, args.toTypedArray())
        val list = mutableListOf<CardIdentity>()

        try {
            while (cursor.moveToNext()) {
                list.add(
                    CardIdentity(
                        rowId = cursor.getInt(0),
                        name = cursor.getString(1),
                        setCode = cursor.getString(2),
                        collectorNumber = cursor.getString(3),
                        priceCents = cursor.getInt(4)
                    )
                )
            }
        } finally {
            cursor.close()
        }

        return list
    }

    fun close() {
        try {
            db?.close()
        } catch (_: Exception) {}
        db = null
    }
}