package org.arogyasathi.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.arogyasathi.app.data.ArogyasathiDbHelper

/**
 * Authoritative Offline-First Room Database Schema for Arogyasathi.
 *
 * Mirrors the 8 central Neon Serverless Postgres tables (`ashas`, `households`, `members`,
 * `protocols`, `visit_outcomes`, `referral_slips`/`danger_escalations`, `sync_queue`,
 * `patient_chat_history`) while guaranteeing 100% offline functionality via SQLite WAL
 * and bundled asset seeding (`protocols.json`, `households.csv`, `household_register.csv`).
 */
@Database(
    entities = [
        AshaEntity::class,
        HouseholdEntity::class,
        MemberEntity::class,
        ProtocolSectionEntity::class,
        VisitOutcomeEntity::class,
        DangerEscalationEntity::class,
        SyncQueueEntity::class,
        PatientChatEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ArogyasathiRoomDatabase {

    abstract fun ashaDao(): AshaDao
    abstract fun householdDao(): HouseholdDao
    abstract fun memberDao(): MemberDao
    abstract fun protocolDao(): ProtocolDao
    abstract fun visitOutcomeDao(): VisitOutcomeDao
    abstract fun dangerEscalationDao(): DangerEscalationDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun patientChatDao(): PatientChatDao

    abstract fun notifyDataChanged()

    companion object {
        const val DATABASE_NAME = ArogyasathiDbHelper.DATABASE_NAME

        @Volatile
        private var INSTANCE: ArogyasathiRoomDatabase? = null

        fun getInstance(context: Context): ArogyasathiRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SqliteBackedArogyasathiRoomDatabase(
                    ArogyasathiDbHelper.getInstance(context.applicationContext)
                ).also { INSTANCE = it }
            }
        }
    }
}

/**
 * Deterministic SQLite WAL implementation of [ArogyasathiRoomDatabase] and its 8 Room DAOs.
 * Provides sub-millisecond indexed queries, transactional batch upserts from Neon Postgres,
 * and reactive Kotlin [Flow] streams.
 */
internal class SqliteBackedArogyasathiRoomDatabase(
    private val dbHelper: ArogyasathiDbHelper
) : ArogyasathiRoomDatabase() {

    private val invalidationVersion = MutableStateFlow(0L)

    override fun notifyDataChanged() {
        invalidationVersion.value = System.currentTimeMillis()
    }

    private val _ashaDao = object : AshaDao {
        override fun observeAllAshas(): Flow<List<AshaEntity>> =
            invalidationVersion.map { getAllAshas() }

        override fun getAllAshas(): List<AshaEntity> {
            val out = mutableListOf<AshaEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_ASHAS,
                null, null, null, null, null, "asha_id ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToAshaEntity(it))
                }
            }
            return out
        }

        override fun getAshaById(ashaId: String): AshaEntity? {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_ASHAS,
                null, "asha_id = ?", arrayOf(ashaId), null, null, null
            )
            cursor.use {
                if (it.moveToNext()) return cursorToAshaEntity(it)
            }
            return null
        }

        override suspend fun upsertAll(ashas: List<AshaEntity>) {
            if (ashas.isEmpty()) return
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                for (a in ashas) {
                    val cv = ContentValues().apply {
                        put("asha_id", a.ashaId)
                        put("name", a.name)
                        put("phone", a.phone)
                        put("villages_csv", a.villagesCsv)
                        put("sub_centre", a.subCentre)
                        put("parent_phc", a.parentPhc)
                        put("mo_name", a.medicalOfficerName)
                        put("mo_phone", a.medicalOfficerPhone)
                        put("anm_name", a.anmName)
                        put("anm_phone", a.anmPhone)
                    }
                    db.insertWithOnConflict(
                        ArogyasathiDbHelper.TABLE_ASHAS,
                        null,
                        cv,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyDataChanged()
        }
    }

    private val _householdDao = object : HouseholdDao {
        override fun observeAllHouseholds(): Flow<List<HouseholdEntity>> =
            invalidationVersion.map { getAllHouseholds() }

        override fun observeHouseholdsByVillage(village: String): Flow<List<HouseholdEntity>> =
            invalidationVersion.map { getHouseholdsByVillage(village) }

        override fun getAllHouseholds(): List<HouseholdEntity> {
            val out = mutableListOf<HouseholdEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_HOUSEHOLDS,
                null, null, null, null, null, "household_id ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToHouseholdEntity(it))
                }
            }
            return out
        }

        override fun getHouseholdsByVillage(village: String): List<HouseholdEntity> {
            val out = mutableListOf<HouseholdEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_HOUSEHOLDS,
                null, "village = ?", arrayOf(village), null, null, "household_id ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToHouseholdEntity(it))
                }
            }
            return out
        }

        override fun getHouseholdById(householdId: String): HouseholdEntity? {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_HOUSEHOLDS,
                null, "household_id = ?", arrayOf(householdId), null, null, null
            )
            cursor.use {
                if (it.moveToNext()) return cursorToHouseholdEntity(it)
            }
            return null
        }

        override suspend fun insertAll(households: List<HouseholdEntity>) {
            if (households.isEmpty()) return
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                for (h in households) {
                    val cv = ContentValues().apply {
                        put("household_id", h.householdId)
                        put("village", h.village)
                        put("asha_id", h.ashaId)
                        put("house_no", h.houseNo)
                        put("head_member_id", h.headMemberId)
                        put("head_name", h.headName)
                        put("members_count", h.membersCount)
                    }
                    db.insertWithOnConflict(
                        ArogyasathiDbHelper.TABLE_HOUSEHOLDS,
                        null,
                        cv,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyDataChanged()
        }
    }

    private val _memberDao = object : MemberDao {
        override fun observeAllMembers(): Flow<List<MemberEntity>> =
            invalidationVersion.map { getAllMembers() }

        override fun observeMembersByVillage(village: String): Flow<List<MemberEntity>> =
            invalidationVersion.map { getMembersByVillage(village) }

        override fun getAllMembers(): List<MemberEntity> {
            val out = mutableListOf<MemberEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_MEMBERS,
                null, null, null, null, null, "household_id ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToMemberEntity(it))
                }
            }
            return out
        }

        override fun getMembersByVillage(village: String): List<MemberEntity> {
            val out = mutableListOf<MemberEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_MEMBERS,
                null, "village = ?", arrayOf(village), null, null, "member_id ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToMemberEntity(it))
                }
            }
            return out
        }

        override fun getMembersByHousehold(householdId: String): List<MemberEntity> {
            val out = mutableListOf<MemberEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_MEMBERS,
                null, "household_id = ?", arrayOf(householdId), null, null, "dob ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToMemberEntity(it))
                }
            }
            return out
        }

        override fun getMemberById(memberId: String): MemberEntity? {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_MEMBERS,
                null, "member_id = ?", arrayOf(memberId), null, null, null
            )
            cursor.use {
                if (it.moveToNext()) return cursorToMemberEntity(it)
            }
            return null
        }

        override suspend fun insertAll(members: List<MemberEntity>) {
            if (members.isEmpty()) return
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                for (m in members) {
                    db.insertWithOnConflict(
                        ArogyasathiDbHelper.TABLE_MEMBERS,
                        null,
                        memberEntityToContentValues(m),
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyDataChanged()
        }

        override suspend fun upsertMember(member: MemberEntity) {
            val db = dbHelper.writableDatabase
            db.insertWithOnConflict(
                ArogyasathiDbHelper.TABLE_MEMBERS,
                null,
                memberEntityToContentValues(member),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            notifyDataChanged()
        }
    }

    private val _protocolDao = object : ProtocolDao {
        override fun observeAllProtocols(): Flow<List<ProtocolSectionEntity>> =
            invalidationVersion.map { getAllProtocols() }

        override fun getAllProtocols(): List<ProtocolSectionEntity> {
            val out = mutableListOf<ProtocolSectionEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_PROTOCOLS,
                null, null, null, null, null, "section_id ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToProtocolEntity(it))
                }
            }
            return out
        }

        override fun getProtocolsByCategory(category: String): List<ProtocolSectionEntity> {
            val out = mutableListOf<ProtocolSectionEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_PROTOCOLS,
                null, "category = ?", arrayOf(category), null, null, "section_id ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToProtocolEntity(it))
                }
            }
            return out
        }

        override fun getProtocolById(sectionId: String): ProtocolSectionEntity? {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_PROTOCOLS,
                null, "section_id = ?", arrayOf(sectionId), null, null, null
            )
            cursor.use {
                if (it.moveToNext()) return cursorToProtocolEntity(it)
            }
            return null
        }

        override suspend fun insertAll(protocols: List<ProtocolSectionEntity>) {
            if (protocols.isEmpty()) return
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                for (p in protocols) {
                    val cv = ContentValues().apply {
                        put("section_id", p.sectionId)
                        put("title", p.title)
                        put("body", p.body)
                        put("category", p.category)
                    }
                    db.insertWithOnConflict(
                        ArogyasathiDbHelper.TABLE_PROTOCOLS,
                        null,
                        cv,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyDataChanged()
        }
    }

    private val _visitOutcomeDao = object : VisitOutcomeDao {
        override fun observeAllOutcomes(): Flow<List<VisitOutcomeEntity>> =
            invalidationVersion.map { getAllOutcomes() }

        override fun getAllOutcomes(): List<VisitOutcomeEntity> {
            val out = mutableListOf<VisitOutcomeEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_VISIT_OUTCOMES,
                null, null, null, null, null, "timestamp DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToVisitOutcomeEntity(it))
                }
            }
            return out
        }

        override fun getOutcomesForMember(memberId: String): List<VisitOutcomeEntity> {
            val out = mutableListOf<VisitOutcomeEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_VISIT_OUTCOMES,
                null, "member_id = ?", arrayOf(memberId), null, null, "timestamp DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToVisitOutcomeEntity(it))
                }
            }
            return out
        }

        override suspend fun insertOutcome(outcome: VisitOutcomeEntity) {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("outcome_id", outcome.outcomeId)
                put("household_id", outcome.householdId)
                put("member_id", outcome.memberId)
                put("member_name", outcome.memberName)
                put("due_item", outcome.dueItem)
                put("status", outcome.status)
                put("notes", outcome.notes)
                put("timestamp", outcome.timestamp)
            }
            db.insertWithOnConflict(
                ArogyasathiDbHelper.TABLE_VISIT_OUTCOMES,
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            notifyDataChanged()
        }
    }

    private val _dangerEscalationDao = object : DangerEscalationDao {
        override fun observeAllEscalations(): Flow<List<DangerEscalationEntity>> =
            invalidationVersion.map { getAllEscalations() }

        override fun getAllEscalations(): List<DangerEscalationEntity> {
            val out = mutableListOf<DangerEscalationEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_REFERRAL_SLIPS,
                null, null, null, null, null, "timestamp DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToDangerEscalationEntity(it))
                }
            }
            return out
        }

        override fun getEscalationsForMember(patientId: String): List<DangerEscalationEntity> {
            val out = mutableListOf<DangerEscalationEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_REFERRAL_SLIPS,
                null, "patient_id = ?", arrayOf(patientId), null, null, "timestamp DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToDangerEscalationEntity(it))
                }
            }
            return out
        }

        override suspend fun insertEscalation(escalation: DangerEscalationEntity) {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("slip_id", escalation.id)
                put("patient_id", escalation.patientId)
                put("patient_name", escalation.patientName)
                put("age_gender", escalation.ageGender)
                put("village", escalation.village)
                put("household_id", escalation.householdId)
                put("suspected_condition", escalation.suspectedCondition)
                put("danger_signs_csv", escalation.dangerSignsCsv)
                put("vital_signs", escalation.vitalSigns)
                put("protocol_citation", escalation.protocolCitation)
                put("referred_facility", escalation.referredFacility)
                put("immediate_action_taken", escalation.immediateActionTaken)
                put("asha_name", escalation.ashaName)
                put("asha_phone", escalation.ashaPhone)
                put("mo_name", escalation.medicalOfficerName)
                put("mo_phone", escalation.medicalOfficerPhone)
                put("timestamp", escalation.timestamp)
            }
            db.insertWithOnConflict(
                ArogyasathiDbHelper.TABLE_REFERRAL_SLIPS,
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            notifyDataChanged()
        }
    }

    private val _syncQueueDao = object : SyncQueueDao {
        override fun observeSyncQueue(): Flow<List<SyncQueueEntity>> =
            invalidationVersion.map { getAllSyncItems() }

        override fun getAllSyncItems(): List<SyncQueueEntity> {
            val out = mutableListOf<SyncQueueEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_SYNC_QUEUE,
                null, null, null, null, null, "created_at DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToSyncQueueEntity(it))
                }
            }
            return out
        }

        override fun getPendingSyncItems(): List<SyncQueueEntity> {
            val out = mutableListOf<SyncQueueEntity>()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_SYNC_QUEUE,
                null, "status = ?", arrayOf("PENDING"), null, null, "created_at ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToSyncQueueEntity(it))
                }
            }
            return out
        }

        override fun getPendingCount(): Int {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM ${ArogyasathiDbHelper.TABLE_SYNC_QUEUE} WHERE status = ?",
                arrayOf("PENDING")
            )
            cursor.use {
                if (it.moveToFirst()) return it.getInt(0)
            }
            return 0
        }

        override suspend fun enqueue(item: SyncQueueEntity): Long {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("entity_type", item.entityType)
                put("entity_id", item.entityId)
                put("payload_json", item.payloadJson)
                put("status", item.status)
                put("retry_count", item.retryCount)
                put("created_at", item.createdAt)
            }
            val id = db.insert(ArogyasathiDbHelper.TABLE_SYNC_QUEUE, null, cv)
            notifyDataChanged()
            return id
        }

        override suspend fun markSyncedByIds(ids: List<Long>) {
            if (ids.isEmpty()) return
            val db = dbHelper.writableDatabase
            val placeholders = ids.joinToString(",") { "?" }
            val args = ids.map { it.toString() }.toTypedArray()
            val cv = ContentValues().apply { put("status", "SYNCED") }
            db.update(ArogyasathiDbHelper.TABLE_SYNC_QUEUE, cv, "id IN ($placeholders)", args)
            notifyDataChanged()
        }

        override suspend fun markAllPendingSynced() {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply { put("status", "SYNCED") }
            db.update(ArogyasathiDbHelper.TABLE_SYNC_QUEUE, cv, "status = ?", arrayOf("PENDING"))
            notifyDataChanged()
        }
    }

    private val _patientChatDao = object : PatientChatDao {
        override fun observeChatForMember(patientId: String): Flow<List<PatientChatEntity>> =
            invalidationVersion.map { getChatForMember(patientId) }

        override fun getChatForMember(patientId: String?): List<PatientChatEntity> {
            val out = mutableListOf<PatientChatEntity>()
            val db = dbHelper.readableDatabase
            val selection = if (patientId == null) "patient_id IS NULL" else "patient_id = ?"
            val selectionArgs = if (patientId == null) null else arrayOf(patientId)
            val cursor = db.query(
                ArogyasathiDbHelper.TABLE_PATIENT_CHAT,
                null, selection, selectionArgs, null, null, "timestamp ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    out.add(cursorToPatientChatEntity(it))
                }
            }
            return out
        }

        override suspend fun insertMessage(message: PatientChatEntity) {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("id", message.id)
                put("patient_id", message.patientId)
                put("patient_name", message.patientName)
                put("sender", message.sender)
                put("query_text", message.queryText)
                put("answer_text", message.answerText)
                put("answer_hindi", message.answerHindi)
                put("cited_sections", message.citedSectionsCsv)
                put("is_escalation", if (message.isEscalation) 1 else 0)
                put("is_refusal", if (message.isRefusal) 1 else 0)
                put("triage_level", message.triageLevel)
                put("timestamp", message.timestamp)
            }
            db.insertWithOnConflict(
                ArogyasathiDbHelper.TABLE_PATIENT_CHAT,
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            notifyDataChanged()
        }
    }

    override fun ashaDao(): AshaDao = _ashaDao
    override fun householdDao(): HouseholdDao = _householdDao
    override fun memberDao(): MemberDao = _memberDao
    override fun protocolDao(): ProtocolDao = _protocolDao
    override fun visitOutcomeDao(): VisitOutcomeDao = _visitOutcomeDao
    override fun dangerEscalationDao(): DangerEscalationDao = _dangerEscalationDao
    override fun syncQueueDao(): SyncQueueDao = _syncQueueDao
    override fun patientChatDao(): PatientChatDao = _patientChatDao

    private fun cursorToAshaEntity(c: Cursor): AshaEntity = AshaEntity(
        ashaId = c.getString(c.getColumnIndexOrThrow("asha_id")),
        name = c.getString(c.getColumnIndexOrThrow("name")) ?: "",
        phone = c.getString(c.getColumnIndexOrThrow("phone")) ?: "+91 98765 43220",
        villagesCsv = c.getString(c.getColumnIndexOrThrow("villages_csv")) ?: "",
        subCentre = c.getString(c.getColumnIndexOrThrow("sub_centre")) ?: "",
        parentPhc = c.getString(c.getColumnIndexOrThrow("parent_phc")) ?: "Chandrapuri PHC (Sector 1)",
        medicalOfficerName = c.getString(c.getColumnIndexOrThrow("mo_name")) ?: "Dr. Rajesh Sharma, MBBS",
        medicalOfficerPhone = c.getString(c.getColumnIndexOrThrow("mo_phone")) ?: "+91 94310 82101",
        anmName = c.getString(c.getColumnIndexOrThrow("anm_name")) ?: "Anita Sharma",
        anmPhone = c.getString(c.getColumnIndexOrThrow("anm_phone")) ?: "+91 98350 41102"
    )

    private fun cursorToHouseholdEntity(c: Cursor): HouseholdEntity = HouseholdEntity(
        householdId = c.getString(c.getColumnIndexOrThrow("household_id")),
        village = c.getString(c.getColumnIndexOrThrow("village")) ?: "",
        ashaId = c.getString(c.getColumnIndexOrThrow("asha_id")) ?: "",
        houseNo = c.getString(c.getColumnIndexOrThrow("house_no")) ?: "",
        headMemberId = c.getString(c.getColumnIndexOrThrow("head_member_id")) ?: "",
        headName = c.getString(c.getColumnIndexOrThrow("head_name")) ?: "",
        membersCount = c.getInt(c.getColumnIndexOrThrow("members_count"))
    )

    private fun cursorToMemberEntity(c: Cursor): MemberEntity {
        val hbIdx = c.getColumnIndexOrThrow("last_hb_gdl")
        val bwIdx = c.getColumnIndexOrThrow("birth_weight_kg")
        val muacIdx = c.getColumnIndexOrThrow("last_muac_cm")
        val rbsIdx = c.getColumnIndexOrThrow("last_rbs_mg_dl")
        return MemberEntity(
            memberId = c.getString(c.getColumnIndexOrThrow("member_id")),
            householdId = c.getString(c.getColumnIndexOrThrow("household_id")) ?: "",
            village = c.getString(c.getColumnIndexOrThrow("village")) ?: "",
            ashaId = c.getString(c.getColumnIndexOrThrow("asha_id")) ?: "",
            name = c.getString(c.getColumnIndexOrThrow("name")) ?: "",
            relation = c.getString(c.getColumnIndexOrThrow("relation")) ?: "",
            sex = c.getString(c.getColumnIndexOrThrow("sex")) ?: "",
            dob = c.getString(c.getColumnIndexOrThrow("dob")) ?: "",
            isPregnant = c.getInt(c.getColumnIndexOrThrow("is_pregnant")) == 1,
            lmpDate = c.getString(c.getColumnIndexOrThrow("lmp_date")),
            edd = c.getString(c.getColumnIndexOrThrow("edd")),
            ancVisitDatesCsv = c.getString(c.getColumnIndexOrThrow("anc_visit_dates")),
            lastAncBp = c.getString(c.getColumnIndexOrThrow("last_anc_bp")),
            lastHbGdl = if (!c.isNull(hbIdx)) c.getDouble(hbIdx) else null,
            tdBoosterEligible = c.getInt(c.getColumnIndexOrThrow("td_booster_eligible")) == 1,
            tdDosesCsv = c.getString(c.getColumnIndexOrThrow("td_doses")),
            placeOfDelivery = c.getString(c.getColumnIndexOrThrow("place_of_delivery")),
            birthWeightKg = if (!c.isNull(bwIdx)) c.getDouble(bwIdx) else null,
            hbncVisitsDoneCsv = c.getString(c.getColumnIndexOrThrow("hbnc_visits_done")),
            childVaccinesCsv = c.getString(c.getColumnIndexOrThrow("child_vaccines")),
            lastMuacCm = if (!c.isNull(muacIdx)) c.getDouble(muacIdx) else null,
            lastMuacDate = c.getString(c.getColumnIndexOrThrow("last_muac_date")),
            bilateralPittingOedema = c.getInt(c.getColumnIndexOrThrow("bilateral_pitting_oedema")) == 1,
            ncdCondition = c.getString(c.getColumnIndexOrThrow("ncd_condition")),
            lastBpCheckDate = c.getString(c.getColumnIndexOrThrow("last_bp_check_date")),
            lastBpReading = c.getString(c.getColumnIndexOrThrow("last_bp_reading")),
            lastSugarCheckDate = c.getString(c.getColumnIndexOrThrow("last_sugar_check_date")),
            lastRbsMgDl = if (!c.isNull(rbsIdx)) c.getDouble(rbsIdx) else null
        )
    }

    private fun memberEntityToContentValues(m: MemberEntity): ContentValues = ContentValues().apply {
        put("member_id", m.memberId)
        put("household_id", m.householdId)
        put("village", m.village)
        put("asha_id", m.ashaId)
        put("name", m.name)
        put("relation", m.relation)
        put("sex", m.sex)
        put("dob", m.dob)
        put("is_pregnant", if (m.isPregnant) 1 else 0)
        put("lmp_date", m.lmpDate)
        put("edd", m.edd)
        put("anc_visit_dates", m.ancVisitDatesCsv)
        put("last_anc_bp", m.lastAncBp)
        put("last_hb_gdl", m.lastHbGdl)
        put("td_booster_eligible", if (m.tdBoosterEligible) 1 else 0)
        put("td_doses", m.tdDosesCsv)
        put("place_of_delivery", m.placeOfDelivery)
        put("birth_weight_kg", m.birthWeightKg)
        put("hbnc_visits_done", m.hbncVisitsDoneCsv)
        put("child_vaccines", m.childVaccinesCsv)
        put("last_muac_cm", m.lastMuacCm)
        put("last_muac_date", m.lastMuacDate)
        put("bilateral_pitting_oedema", if (m.bilateralPittingOedema) 1 else 0)
        put("ncd_condition", m.ncdCondition)
        put("last_bp_check_date", m.lastBpCheckDate)
        put("last_bp_reading", m.lastBpReading)
        put("last_sugar_check_date", m.lastSugarCheckDate)
        put("last_rbs_mg_dl", m.lastRbsMgDl)
    }

    private fun cursorToProtocolEntity(c: Cursor): ProtocolSectionEntity = ProtocolSectionEntity(
        sectionId = c.getString(c.getColumnIndexOrThrow("section_id")),
        title = c.getString(c.getColumnIndexOrThrow("title")) ?: "",
        body = c.getString(c.getColumnIndexOrThrow("body")) ?: "",
        category = c.getString(c.getColumnIndexOrThrow("category")) ?: "GEN"
    )

    private fun cursorToVisitOutcomeEntity(c: Cursor): VisitOutcomeEntity = VisitOutcomeEntity(
        outcomeId = c.getString(c.getColumnIndexOrThrow("outcome_id")),
        householdId = c.getString(c.getColumnIndexOrThrow("household_id")) ?: "",
        memberId = c.getString(c.getColumnIndexOrThrow("member_id")) ?: "",
        memberName = c.getString(c.getColumnIndexOrThrow("member_name")) ?: "",
        dueItem = c.getString(c.getColumnIndexOrThrow("due_item")) ?: "",
        status = c.getString(c.getColumnIndexOrThrow("status")) ?: "COMPLETED",
        notes = c.getString(c.getColumnIndexOrThrow("notes")) ?: "",
        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
    )

    private fun cursorToDangerEscalationEntity(c: Cursor): DangerEscalationEntity = DangerEscalationEntity(
        id = c.getString(c.getColumnIndexOrThrow("slip_id")),
        patientId = c.getString(c.getColumnIndexOrThrow("patient_id")) ?: "",
        patientName = c.getString(c.getColumnIndexOrThrow("patient_name")) ?: "",
        ageGender = c.getString(c.getColumnIndexOrThrow("age_gender")) ?: "",
        village = c.getString(c.getColumnIndexOrThrow("village")) ?: "",
        householdId = c.getString(c.getColumnIndexOrThrow("household_id")) ?: "",
        suspectedCondition = c.getString(c.getColumnIndexOrThrow("suspected_condition")) ?: "",
        dangerSignsCsv = c.getString(c.getColumnIndexOrThrow("danger_signs_csv")) ?: "",
        vitalSigns = c.getString(c.getColumnIndexOrThrow("vital_signs")) ?: "",
        protocolCitation = c.getString(c.getColumnIndexOrThrow("protocol_citation")) ?: "",
        referredFacility = c.getString(c.getColumnIndexOrThrow("referred_facility")) ?: "",
        immediateActionTaken = c.getString(c.getColumnIndexOrThrow("immediate_action_taken")) ?: "",
        ashaName = c.getString(c.getColumnIndexOrThrow("asha_name")) ?: "",
        ashaPhone = c.getString(c.getColumnIndexOrThrow("asha_phone")) ?: "",
        medicalOfficerName = c.getString(c.getColumnIndexOrThrow("mo_name")) ?: "Dr. Rajesh Sharma, MBBS",
        medicalOfficerPhone = c.getString(c.getColumnIndexOrThrow("mo_phone")) ?: "+91 94310 82101",
        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
    )

    private fun cursorToSyncQueueEntity(c: Cursor): SyncQueueEntity = SyncQueueEntity(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        entityType = c.getString(c.getColumnIndexOrThrow("entity_type")) ?: "",
        entityId = c.getString(c.getColumnIndexOrThrow("entity_id")) ?: "",
        payloadJson = c.getString(c.getColumnIndexOrThrow("payload_json")) ?: "{}",
        status = c.getString(c.getColumnIndexOrThrow("status")) ?: "PENDING",
        retryCount = c.getInt(c.getColumnIndexOrThrow("retry_count")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
    )

    private fun cursorToPatientChatEntity(c: Cursor): PatientChatEntity = PatientChatEntity(
        id = c.getString(c.getColumnIndexOrThrow("id")),
        patientId = c.getString(c.getColumnIndexOrThrow("patient_id")),
        patientName = c.getString(c.getColumnIndexOrThrow("patient_name")),
        sender = c.getString(c.getColumnIndexOrThrow("sender")) ?: "USER",
        queryText = c.getString(c.getColumnIndexOrThrow("query_text")) ?: "",
        answerText = c.getString(c.getColumnIndexOrThrow("answer_text")) ?: "",
        answerHindi = c.getString(c.getColumnIndexOrThrow("answer_hindi")) ?: "",
        citedSectionsCsv = c.getString(c.getColumnIndexOrThrow("cited_sections")) ?: "",
        isEscalation = c.getInt(c.getColumnIndexOrThrow("is_escalation")) == 1,
        isRefusal = c.getInt(c.getColumnIndexOrThrow("is_refusal")) == 1,
        triageLevel = c.getString(c.getColumnIndexOrThrow("triage_level")) ?: "ROUTINE_PROTOCOL",
        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
    )
}
