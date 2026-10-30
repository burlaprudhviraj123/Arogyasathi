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
        const val DATABASE_VERSION = 4

        const val TABLE_ASHAS = "ashas"
        const val TABLE_HOUSEHOLDS = "households"
        const val TABLE_MEMBERS = "members"
        const val TABLE_PROTOCOLS = "protocols"
        const val TABLE_VISIT_OUTCOMES = "visit_outcomes"
        const val TABLE_REFERRAL_SLIPS = "referral_slips"
        const val TABLE_SYNC_QUEUE = "sync_queue"
        const val TABLE_PATIENT_CHAT = "patient_chat_history"

        @Volatile
        private var instance: ArogyasathiDbHelper? = null

        fun getInstance(context: Context): ArogyasathiDbHelper {
            return instance ?: synchronized(this) {
                instance ?: ArogyasathiDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ASHAS (
                asha_id TEXT PRIMARY KEY,
                name TEXT,
                phone TEXT,
                villages_csv TEXT,
                sub_centre TEXT,
                parent_phc TEXT,
                mo_name TEXT,
                mo_phone TEXT,
                anm_name TEXT,
                anm_phone TEXT,
                password TEXT
            )
            """.trimIndent()
        )

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
            CREATE TABLE IF NOT EXISTS $TABLE_REFERRAL_SLIPS (
                slip_id TEXT PRIMARY KEY,
                patient_id TEXT,
                patient_name TEXT,
                age_gender TEXT,
                village TEXT,
                household_id TEXT,
                suspected_condition TEXT,
                danger_signs_csv TEXT,
                vital_signs TEXT,
                protocol_citation TEXT,
                referred_facility TEXT,
                immediate_action_taken TEXT,
                asha_name TEXT,
                asha_phone TEXT,
                mo_name TEXT,
                mo_phone TEXT,
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

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PATIENT_CHAT (
                id TEXT PRIMARY KEY,
                patient_id TEXT,
                patient_name TEXT,
                sender TEXT,
                query_text TEXT,
                answer_text TEXT,
                answer_hindi TEXT,
                cited_sections TEXT,
                is_escalation INTEGER,
                is_refusal INTEGER,
                triage_level TEXT,
                timestamp INTEGER
            )
            """.trimIndent()
        )

        // Create indexes for sub-millisecond lookups on low-end hardware
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_members_hh ON $TABLE_MEMBERS (household_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_members_village ON $TABLE_MEMBERS (village)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_households_village ON $TABLE_HOUSEHOLDS (village)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_queue_status ON $TABLE_SYNC_QUEUE (status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_patient ON $TABLE_PATIENT_CHAT (patient_id, timestamp)")

        // Seed data in transaction
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE $TABLE_ASHAS ADD COLUMN password TEXT DEFAULT 'Asha@2026'")
            } catch (e: Exception) {
                // Column might already exist
            }
            seedAshas(db)
            return
        }
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ASHAS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HOUSEHOLDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEMBERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PROTOCOLS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_VISIT_OUTCOMES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_REFERRAL_SLIPS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SYNC_QUEUE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PATIENT_CHAT")
        onCreate(db)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        try {
            seedAshas(db)
            seedProtocols(db)
            seedHouseholds(db)
            seedMembers(db)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun seedAshas(db: SQLiteDatabase) {
        try {
            val initialAshas = listOf(
                ContentValues().apply {
                    put("asha_id", "ASHA-RRR5")
                    put("name", "Kalavati Kumari")
                    put("phone", "+91 98765 43220")
                    put("villages_csv", "Belwadi Tola")
                    put("sub_centre", "Belwadi Tola Ayushman Arogya Mandir (HWC)")
                    put("parent_phc", "Chandrapuri PHC (Sector 1)")
                    put("mo_name", "Dr. Rajesh Sharma, MBBS")
                    put("mo_phone", "+91 94310 82101")
                    put("anm_name", "Anita Sharma")
                    put("anm_phone", "+91 98350 41102")
                    put("password", "Asha@2026")
                },
                ContentValues().apply {
                    put("asha_id", "ASHA-096P")
                    put("name", "Sunaina Devi")
                    put("phone", "+91 98765 43221")
                    put("villages_csv", "Amrapur")
                    put("sub_centre", "Amrapur Health & Wellness Centre")
                    put("parent_phc", "Chandrapuri PHC (Sector 1)")
                    put("mo_name", "Dr. Rajesh Sharma, MBBS")
                    put("mo_phone", "+91 94310 82101")
                    put("anm_name", "Sunita Kumari")
                    put("anm_phone", "+91 98350 41103")
                    put("password", "Asha@2026")
                },
                ContentValues().apply {
                    put("asha_id", "ASHA-YFK5")
                    put("name", "Rinku Devi")
                    put("phone", "+91 98765 43222")
                    put("villages_csv", "Kesarganj")
                    put("sub_centre", "Kesarganj Health & Wellness Centre")
                    put("parent_phc", "Chandrapuri PHC (Sector 2)")
                    put("mo_name", "Dr. Priyadarshini Sen, MBBS, DGO")
                    put("mo_phone", "+91 94310 82102")
                    put("anm_name", "Kavita Rawat")
                    put("anm_phone", "+91 98350 41104")
                    put("password", "Asha@2026")
                },
                ContentValues().apply {
                    put("asha_id", "ASHA-L9R8")
                    put("name", "Shabnam Khatoon")
                    put("phone", "+91 98765 43223")
                    put("villages_csv", "Dhanpura")
                    put("sub_centre", "Dhanpura Health & Wellness Centre")
                    put("parent_phc", "Chandrapuri PHC (Sector 2)")
                    put("mo_name", "Dr. Priyadarshini Sen, MBBS, DGO")
                    put("mo_phone", "+91 94310 82102")
                    put("anm_name", "Fatima Bano")
                    put("anm_phone", "+91 98350 41105")
                    put("password", "Asha@2026")
                },
                ContentValues().apply {
                    put("asha_id", "ASHA-KQXR")
                    put("name", "Malti Devi")
                    put("phone", "+91 98765 43224")
                    put("villages_csv", "Navagaon Khurd;Sitapahari")
                    put("sub_centre", "Navagaon Khurd Health & Wellness Centre")
                    put("parent_phc", "Chandrapuri PHC (Sector 3)")
                    put("mo_name", "Dr. Alok Ranjan, MBBS, MD")
                    put("mo_phone", "+91 94310 82103")
                    put("anm_name", "Bimla Soren")
                    put("anm_phone", "+91 98350 41106")
                    put("password", "Asha@2026")
                }
            )

            db.beginTransaction()
            try {
                for (cv in initialAshas) {
                    db.insertWithOnConflict(TABLE_ASHAS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
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

    fun authenticateAshaOffline(rawPhone: String, passwordAttempt: String): AshaAuthResult {
        val cleanPhone = rawPhone.replace(Regex("[^0-9]"), "").takeLast(10)
        if (cleanPhone.length < 10) {
            return AshaAuthResult.UserNotFound
        }

        val db = readableDatabase
        val cursor = db.query(TABLE_ASHAS, null, null, null, null, null, null)
        cursor.use { c ->
            val idIdx = c.getColumnIndex("asha_id")
            val nameIdx = c.getColumnIndex("name")
            val phoneIdx = c.getColumnIndex("phone")
            val villagesIdx = c.getColumnIndex("villages_csv")
            val subCentreIdx = c.getColumnIndex("sub_centre")
            val parentPhcIdx = c.getColumnIndex("parent_phc")
            val moNameIdx = c.getColumnIndex("mo_name")
            val moPhoneIdx = c.getColumnIndex("mo_phone")
            val anmNameIdx = c.getColumnIndex("anm_name")
            val anmPhoneIdx = c.getColumnIndex("anm_phone")
            val passwordIdx = c.getColumnIndex("password")

            while (c.moveToNext()) {
                val dbPhone = if (phoneIdx != -1) c.getString(phoneIdx) ?: "" else ""
                val dbClean = dbPhone.replace(Regex("[^0-9]"), "")
                if (dbClean.endsWith(cleanPhone)) {
                    val dbPass = if (passwordIdx != -1) c.getString(passwordIdx) ?: "Asha@2026" else "Asha@2026"
                    val name = if (nameIdx != -1) c.getString(nameIdx) ?: "ASHA Worker" else "ASHA Worker"
                    val phc = if (parentPhcIdx != -1) c.getString(parentPhcIdx) ?: "Chandrapuri PHC (Sector 1)" else "Chandrapuri PHC (Sector 1)"
                    val cleanAttempt = passwordAttempt.trim()

                    // Match exact password, or PIN fallback "2026" / "1234"
                    val isMatch = cleanAttempt == dbPass ||
                            cleanAttempt.equals("Asha@2026", ignoreCase = true) ||
                            cleanAttempt == "2026" ||
                            cleanAttempt == "1234"

                    if (isMatch) {
                        val ashaId = if (idIdx != -1) c.getString(idIdx) ?: "ASHA-RRR5" else "ASHA-RRR5"
                        val villagesStr = if (villagesIdx != -1) c.getString(villagesIdx) ?: "" else ""
                        val subCentre = if (subCentreIdx != -1) c.getString(subCentreIdx) ?: "" else ""
                        val moName = if (moNameIdx != -1) c.getString(moNameIdx) ?: "" else ""
                        val moPhone = if (moPhoneIdx != -1) c.getString(moPhoneIdx) ?: "" else ""
                        val anmName = if (anmNameIdx != -1) c.getString(anmNameIdx) ?: "" else ""
                        val anmPhone = if (anmPhoneIdx != -1) c.getString(anmPhoneIdx) ?: "" else ""

                        val profile = AshaProfile(
                            ashaId = ashaId,
                            name = name,
                            phone = dbPhone,
                            villages = villagesStr.split(";").map { it.trim() }.filter { it.isNotEmpty() },
                            subCentre = subCentre,
                            parentPhc = phc,
                            medicalOfficerName = moName,
                            medicalOfficerPhone = moPhone,
                            anmName = anmName,
                            anmPhone = anmPhone,
                            password = dbPass
                        )
                        return AshaAuthResult.Success(profile)
                    } else {
                        return AshaAuthResult.InvalidPassword(name, phc)
                    }
                }
            }
        }
        return AshaAuthResult.UserNotFound
    }

    fun findAshaByPhoneOffline(rawPhone: String): AshaProfile? {
        val cleanPhone = rawPhone.replace(Regex("[^0-9]"), "").takeLast(10)
        if (cleanPhone.length < 10) return null

        val db = readableDatabase
        val cursor = db.query(TABLE_ASHAS, null, null, null, null, null, null)
        cursor.use { c ->
            val idIdx = c.getColumnIndex("asha_id")
            val nameIdx = c.getColumnIndex("name")
            val phoneIdx = c.getColumnIndex("phone")
            val villagesIdx = c.getColumnIndex("villages_csv")
            val subCentreIdx = c.getColumnIndex("sub_centre")
            val parentPhcIdx = c.getColumnIndex("parent_phc")
            val moNameIdx = c.getColumnIndex("mo_name")
            val moPhoneIdx = c.getColumnIndex("mo_phone")
            val anmNameIdx = c.getColumnIndex("anm_name")
            val anmPhoneIdx = c.getColumnIndex("anm_phone")
            val passwordIdx = c.getColumnIndex("password")

            while (c.moveToNext()) {
                val dbPhone = if (phoneIdx != -1) c.getString(phoneIdx) ?: "" else ""
                val dbClean = dbPhone.replace(Regex("[^0-9]"), "")
                if (dbClean.endsWith(cleanPhone)) {
                    val ashaId = if (idIdx != -1) c.getString(idIdx) ?: "ASHA-RRR5" else "ASHA-RRR5"
                    val name = if (nameIdx != -1) c.getString(nameIdx) ?: "ASHA Worker" else "ASHA Worker"
                    val villagesStr = if (villagesIdx != -1) c.getString(villagesIdx) ?: "" else ""
                    val subCentre = if (subCentreIdx != -1) c.getString(subCentreIdx) ?: "" else ""
                    val phc = if (parentPhcIdx != -1) c.getString(parentPhcIdx) ?: "Chandrapuri PHC (Sector 1)" else "Chandrapuri PHC (Sector 1)"
                    val moName = if (moNameIdx != -1) c.getString(moNameIdx) ?: "" else ""
                    val moPhone = if (moPhoneIdx != -1) c.getString(moPhoneIdx) ?: "" else ""
                    val anmName = if (anmNameIdx != -1) c.getString(anmNameIdx) ?: "" else ""
                    val anmPhone = if (anmPhoneIdx != -1) c.getString(anmPhoneIdx) ?: "" else ""
                    val dbPass = if (passwordIdx != -1) c.getString(passwordIdx) ?: "Asha@2026" else "Asha@2026"

                    return AshaProfile(
                        ashaId = ashaId,
                        name = name,
                        phone = dbPhone,
                        villages = villagesStr.split(";").map { it.trim() }.filter { it.isNotEmpty() },
                        subCentre = subCentre,
                        subCentreId = when (ashaId) {
                            "ASHA-RRR5" -> "SC-BWT-101"
                            "ASHA-096P" -> "SC-AMP-102"
                            "ASHA-YFK5" -> "SC-KSG-103"
                            "ASHA-L9R8" -> "SC-DHP-104"
                            "ASHA-KQXR" -> "SC-NGK-105"
                            else -> "SC-${ashaId.takeLast(4)}-101"
                        },
                        parentPhc = phc,
                        medicalOfficerName = moName,
                        medicalOfficerPhone = moPhone,
                        anmName = anmName,
                        anmPhone = anmPhone,
                        password = dbPass
                    )
                }
            }
        }
        return null
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
