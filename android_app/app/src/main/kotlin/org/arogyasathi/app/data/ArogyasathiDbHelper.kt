package org.arogyasathi.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.arogyasathi.app.core.*
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

class ArogyasathiDbHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "arogyasathi.db"
        const val DATABASE_VERSION = 1

        const val TABLE_HOUSEHOLDS = "households"
        const val TABLE_MEMBERS = "members"
        const val TABLE_PROTOCOLS = "protocols"
        const val TABLE_VISIT_OUTCOMES = "visit_outcomes"
        const val TABLE_SYNC_QUEUE = "sync_queue"

        @Volatile
        private var instance: ArogyasathiDbHelper? = null

        fun getInstance(context: Context): ArogyasathiDbHelper {
            return instance ?: synchronized(this) {
                instance ?: ArogyasathiDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_HOUSEHOLDS (
                household_id TEXT PRIMARY KEY,
                village TEXT,
                asha_id TEXT,
                house_no TEXT,
                head_member_id TEXT,
                head_name TEXT,
                members_count INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MEMBERS (
                member_id TEXT PRIMARY KEY,
                household_id TEXT,
                village TEXT,
                asha_id TEXT,
                name TEXT,
                relation TEXT,
                sex TEXT,
                dob TEXT,
                is_pregnant INTEGER,
                lmp_date TEXT,
                edd TEXT,
                anc_visit_dates TEXT,
                last_anc_bp TEXT,
                last_hb_gdl REAL,
                td_booster_eligible INTEGER,
                td_doses TEXT,
                place_of_delivery TEXT,
                birth_weight_kg REAL,
                hbnc_visits_done TEXT,
                child_vaccines TEXT,
                last_muac_cm REAL,
                last_muac_date TEXT,
                bilateral_pitting_oedema INTEGER,
                ncd_condition TEXT,
                last_bp_check_date TEXT,
                last_bp_reading TEXT,
                last_sugar_check_date TEXT,
                last_rbs_mg_dl REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PROTOCOLS (
                section_id TEXT PRIMARY KEY,
                title TEXT,
                body TEXT,
                category TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_VISIT_OUTCOMES (
                outcome_id TEXT PRIMARY KEY,
                household_id TEXT,
                member_id TEXT,
                member_name TEXT,
                due_item TEXT,
                status TEXT,
                notes TEXT,
                timestamp INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SYNC_QUEUE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT,
                entity_id TEXT,
                payload_json TEXT,
                status TEXT,
                retry_count INTEGER,
                created_at INTEGER
            )
            """.trimIndent()
        )

        // Create indexes for sub-millisecond lookups on low-end hardware
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_members_hh ON $TABLE_MEMBERS (household_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_members_village ON $TABLE_MEMBERS (village)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_households_village ON $TABLE_HOUSEHOLDS (village)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_queue_status ON $TABLE_SYNC_QUEUE (status)")

        // Seed data in transaction
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HOUSEHOLDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEMBERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PROTOCOLS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_VISIT_OUTCOMES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SYNC_QUEUE")
        onCreate(db)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        try {
            seedProtocols(db)
            seedHouseholds(db)
            seedMembers(db)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun seedProtocols(db: SQLiteDatabase) {
        try {
            val jsonString = context.assets.open("protocols.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonString)
            db.beginTransaction()
            try {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val cv = ContentValues().apply {
                        put("section_id", obj.getString("section_id"))
                        put("title", obj.getString("title"))
                        put("body", obj.getString("text"))
                        put("category", obj.optString("category", "GEN"))
                    }
                    db.insertWithOnConflict(TABLE_PROTOCOLS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun seedHouseholds(db: SQLiteDatabase) {
        try {
            context.assets.open("households.csv").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line = reader.readLine() // header
                    db.beginTransaction()
                    try {
                        while (reader.readLine()?.also { line = it } != null) {
                            val parts = line!!.split(",")
                            if (parts.size >= 7) {
                                val cv = ContentValues().apply {
                                    put("household_id", parts[0].trim())
                                    put("village", parts[1].trim())
                                    put("asha_id", parts[2].trim())
                                    put("house_no", parts[3].trim())
                                    put("head_member_id", parts[4].trim())
                                    put("head_name", parts[5].trim())
                                    put("members_count", parts[6].trim().toIntOrNull() ?: 1)
                                }
                                db.insertWithOnConflict(TABLE_HOUSEHOLDS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                            }
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun seedMembers(db: SQLiteDatabase) {
        try {
            context.assets.open("household_register.csv").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line = reader.readLine() // header
                    // member_id,household_id,village,asha_id,name,relation,sex,dob,pregnant,lmp_date,edd,anc_visit_dates,
                    // last_anc_bp,last_hb_gdl,td_booster_eligible,td_doses,place_of_delivery,birth_weight_kg,hbnc_visits_done,
                    // child_vaccines,last_muac_cm,last_muac_date,bilateral_pitting_oedema,ncd_condition,last_bp_check_date,
                    // last_bp_reading,last_sugar_check_date,last_rbs_mg_dl
                    db.beginTransaction()
                    try {
                        while (reader.readLine()?.also { line = it } != null) {
                            val row = parseCsvLine(line!!)
                            if (row.size >= 8) {
                                val cv = ContentValues().apply {
                                    put("member_id", row[0].trim())
                                    put("household_id", row[1].trim())
                                    put("village", row[2].trim())
                                    put("asha_id", row[3].trim())
                                    put("name", row[4].trim())
                                    put("relation", row[5].trim())
                                    put("sex", row[6].trim())
                                    put("dob", row[7].trim())
                                    put("is_pregnant", if (row.getOrNull(8)?.trim()?.equals("Y", ignoreCase = true) == true) 1 else 0)
                                    put("lmp_date", row.getOrNull(9)?.trim()?.ifEmpty { null })
                                    put("edd", row.getOrNull(10)?.trim()?.ifEmpty { null })
                                    put("anc_visit_dates", row.getOrNull(11)?.trim()?.ifEmpty { null })
                                    put("last_anc_bp", row.getOrNull(12)?.trim()?.ifEmpty { null })
                                    put("last_hb_gdl", row.getOrNull(13)?.trim()?.toDoubleOrNull())
                                    put("td_booster_eligible", if (row.getOrNull(14)?.trim()?.equals("Y", ignoreCase = true) == true) 1 else 0)
                                    put("td_doses", row.getOrNull(15)?.trim()?.ifEmpty { null })
                                    put("place_of_delivery", row.getOrNull(16)?.trim()?.ifEmpty { null })
                                    put("birth_weight_kg", row.getOrNull(17)?.trim()?.toDoubleOrNull())
                                    put("hbnc_visits_done", row.getOrNull(18)?.trim()?.ifEmpty { null })
                                    put("child_vaccines", row.getOrNull(19)?.trim()?.ifEmpty { null })
                                    put("last_muac_cm", row.getOrNull(20)?.trim()?.toDoubleOrNull())
                                    put("last_muac_date", row.getOrNull(21)?.trim()?.ifEmpty { null })
                                    put("bilateral_pitting_oedema", if (row.getOrNull(22)?.trim()?.equals("Y", ignoreCase = true) == true) 1 else 0)
                                    put("ncd_condition", row.getOrNull(23)?.trim()?.ifEmpty { null })
                                    put("last_bp_check_date", row.getOrNull(24)?.trim()?.ifEmpty { null })
                                    put("last_bp_reading", row.getOrNull(25)?.trim()?.ifEmpty { null })
                                    put("last_sugar_check_date", row.getOrNull(26)?.trim()?.ifEmpty { null })
                                    put("last_rbs_mg_dl", row.getOrNull(27)?.trim()?.toDoubleOrNull())
                                }
                                db.insertWithOnConflict(TABLE_MEMBERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                            }
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '\"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    tokens.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(char)
            }
        }
        tokens.add(sb.toString())
        return tokens
    }
}
