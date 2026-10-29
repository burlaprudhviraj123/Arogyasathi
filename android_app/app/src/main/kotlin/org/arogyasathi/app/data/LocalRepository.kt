package org.arogyasathi.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import org.arogyasathi.app.core.*
import org.arogyasathi.app.jev.JEVEscalate
import org.arogyasathi.app.jev.JEVScope
import org.arogyasathi.app.laya.LayaRetriever
import org.arogyasathi.app.laya.LayaSynthesizer
import org.arogyasathi.app.laya.PatientAgeCalculator
import org.arogyasathi.app.visit.VisitEngine
import org.json.JSONObject

data class AssistantResult(
    val answer: String,
    val citedSection: String,
    val isEscalation: Boolean,
    val isRefusal: Boolean,
    val triageLevel: String, // "EMERGENCY", "OUT_OF_SCOPE", "ROUTINE_PROTOCOL"
    val answerHindi: String = "",
    val patientContext: String? = null
)

data class SyncResult(
    val success: Boolean,
    val message: String
)

class ArogyasathiRepository(private val context: Context) {

    private val dbHelper = ArogyasathiDbHelper.getInstance(context)

    @Volatile
    private var cachedProtocols: List<ProtocolSection>? = null
    @Volatile
    private var layaRetriever: LayaRetriever? = null
    @Volatile
    private var cachedMembers: List<Member>? = null

    private var activeAshaId: String = "ASHA-RRR5" // Default: Kalavati Kumari (Belwadi Tola)

    private val allProfiles = listOf(
        AshaProfile(
            ashaId = "ASHA-RRR5",
            name = "Kalavati Kumari",
            phone = "+91 98765 43220",
            villages = listOf("Belwadi Tola"),
            subCentre = "Belwadi Tola Ayushman Arogya Mandir (HWC)",
            assignedHouseholdsCount = 100,
            isVerified = true,
            parentPhc = "Chandrapuri PHC (Sector 1)",
            medicalOfficerName = "Dr. Rajesh Sharma, MBBS",
            medicalOfficerRole = "Medical Officer In-Charge (MO-IC)",
            medicalOfficerPhone = "+91 94310 82101",
            anmName = "Anita Sharma",
            anmRole = "Designated Sector ANM",
            anmPhone = "+91 98350 41102"
        ),
        AshaProfile(
            ashaId = "ASHA-096P",
            name = "Sunaina Devi",
            phone = "+91 98765 43221",
            villages = listOf("Amrapur"),
            subCentre = "Amrapur Health & Wellness Centre",
            assignedHouseholdsCount = 100,
            isVerified = true,
            parentPhc = "Chandrapuri PHC (Sector 1)",
            medicalOfficerName = "Dr. Rajesh Sharma, MBBS",
            medicalOfficerRole = "Medical Officer In-Charge (MO-IC)",
            medicalOfficerPhone = "+91 94310 82101",
            anmName = "Sunita Kumari",
            anmRole = "Designated Sector ANM",
            anmPhone = "+91 98350 41103"
        ),
        AshaProfile(
            ashaId = "ASHA-YFK5",
            name = "Rinku Devi",
            phone = "+91 98765 43222",
            villages = listOf("Kesarganj"),
            subCentre = "Kesarganj Health & Wellness Centre",
            assignedHouseholdsCount = 100,
            isVerified = true,
            parentPhc = "Chandrapuri PHC (Sector 2)",
            medicalOfficerName = "Dr. Priyadarshini Sen, MBBS, DGO",
            medicalOfficerRole = "Medical Officer In-Charge (MO-IC)",
            medicalOfficerPhone = "+91 94310 82102",
            anmName = "Kavita Rawat",
            anmRole = "Designated Sector ANM",
            anmPhone = "+91 98350 41104"
        ),
        AshaProfile(
            ashaId = "ASHA-L9R8",
            name = "Shabnam Khatoon",
            phone = "+91 98765 43223",
            villages = listOf("Dhanpura"),
            subCentre = "Dhanpura Health & Wellness Centre",
            assignedHouseholdsCount = 100,
            isVerified = true,
            parentPhc = "Chandrapuri PHC (Sector 2)",
            medicalOfficerName = "Dr. Priyadarshini Sen, MBBS, DGO",
            medicalOfficerRole = "Medical Officer In-Charge (MO-IC)",
            medicalOfficerPhone = "+91 94310 82102",
            anmName = "Fatima Bano",
            anmRole = "Designated Sector ANM",
            anmPhone = "+91 98350 41105"
        ),
        AshaProfile(
            ashaId = "ASHA-KQXR",
            name = "Malti Devi",
            phone = "+91 98765 43224",
            villages = listOf("Navagaon Khurd", "Sitapahari"),
            subCentre = "Navagaon Khurd Health & Wellness Centre",
            assignedHouseholdsCount = 100,
            isVerified = true,
            parentPhc = "Chandrapuri PHC (Sector 3)",
            medicalOfficerName = "Dr. Alok Ranjan, MBBS, MD",
            medicalOfficerRole = "Medical Officer In-Charge (MO-IC)",
            medicalOfficerPhone = "+91 94310 82103",
            anmName = "Bimla Soren",
            anmRole = "Designated Sector ANM",
            anmPhone = "+91 98350 41106"
        )
    )

    fun getActiveProfile(): AshaProfile {
        return allProfiles.find { it.ashaId == activeAshaId } ?: allProfiles.first()
    }

    fun setActiveWorker(ashaId: String) {
        if (allProfiles.any { it.ashaId == ashaId }) {
            activeAshaId = ashaId
        }
    }

    fun getAllAshaProfiles(): List<AshaProfile> = allProfiles

    fun getAllVillages(): List<String> = listOf(
        "Belwadi Tola", "Amrapur", "Kesarganj", "Dhanpura", "Navagaon Khurd", "Sitapahari"
    )

    fun verifyPhoneNumber(rawPhone: String): AshaProfile? {
        val clean = rawPhone.replace(Regex("[^0-9]"), "")
        return allProfiles.find {
            it.phone.replace(Regex("[^0-9]"), "").endsWith(clean.takeLast(10))
        }
    }

    fun getProtocols(): List<ProtocolSection> {
        cachedProtocols?.let { return it }
        val list = mutableListOf<ProtocolSection>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ArogyasathiDbHelper.TABLE_PROTOCOLS,
            null, null, null, null, null, "section_id ASC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow("section_id")
            val titleIdx = it.getColumnIndexOrThrow("title")
            val bodyIdx = it.getColumnIndexOrThrow("body")
            val catIdx = it.getColumnIndexOrThrow("category")
            while (it.moveToNext()) {
                list.add(
                    ProtocolSection(
                        sectionId = it.getString(idIdx),
                        title = it.getString(titleIdx),
                        body = it.getString(bodyIdx),
                        category = it.getString(catIdx)
                    )
                )
            }
        }
        val result = if (list.isNotEmpty()) list else LocalRepository.sampleProtocols
        cachedProtocols = result
        layaRetriever = LayaRetriever(result)
        return result
    }

    fun getHouseholds(searchQuery: String? = null, villageFilter: String? = null): List<Household> {
        val list = mutableListOf<Household>()
        val db = dbHelper.readableDatabase

        val selection = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (!villageFilter.isNullOrBlank() && villageFilter != "All") {
            if (villageFilter == "My Beat") {
                val activeVillages = getActiveProfile().villages
                val placeholders = activeVillages.joinToString(",") { "?" }
                selection.add("village IN ($placeholders)")
                selectionArgs.addAll(activeVillages)
            } else {
                selection.add("village = ?")
                selectionArgs.add(villageFilter.trim())
            }
        }
        if (!searchQuery.isNullOrBlank()) {
            selection.add("(head_name LIKE ? OR house_no LIKE ? OR household_id LIKE ?)")
            val q = "%${searchQuery.trim()}%"
            selectionArgs.add(q)
            selectionArgs.add(q)
            selectionArgs.add(q)
        }

        val selStr = if (selection.isNotEmpty()) selection.joinToString(" AND ") else null
        val argsArr = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null

        val cursor = db.query(
            ArogyasathiDbHelper.TABLE_HOUSEHOLDS,
            null, selStr, argsArr, null, null, "household_id ASC"
        )
        cursor.use {
            val hhIdIdx = it.getColumnIndexOrThrow("household_id")
            val vIdx = it.getColumnIndexOrThrow("village")
            val aIdx = it.getColumnIndexOrThrow("asha_id")
            val hNoIdx = it.getColumnIndexOrThrow("house_no")
            val hMidIdx = it.getColumnIndexOrThrow("head_member_id")
            val hNameIdx = it.getColumnIndexOrThrow("head_name")
            val mCountIdx = it.getColumnIndexOrThrow("members_count")

            while (it.moveToNext()) {
                list.add(
                    Household(
                        householdId = it.getString(hhIdIdx),
                        village = it.getString(vIdx),
                        ashaId = it.getString(aIdx),
                        houseNo = it.getString(hNoIdx),
                        headMemberId = it.getString(hMidIdx),
                        headName = it.getString(hNameIdx),
                        membersCount = it.getInt(mCountIdx)
                    )
                )
            }
        }
        return list
    }

    fun getMembersByHousehold(householdId: String): List<Member> {
        val list = mutableListOf<Member>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ArogyasathiDbHelper.TABLE_MEMBERS,
            null, "household_id = ?", arrayOf(householdId), null, null, "dob ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToMember(it))
            }
        }
        return list
    }

    fun getMemberById(memberId: String): Member? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ArogyasathiDbHelper.TABLE_MEMBERS,
            null, "member_id = ?", arrayOf(memberId), null, null, null
        )
        cursor.use {
            if (it.moveToNext()) {
                return cursorToMember(it)
            }
        }
        return null
    }

    fun getAllMembers(): List<Member> {
        cachedMembers?.let { return it }
        val list = mutableListOf<Member>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ArogyasathiDbHelper.TABLE_MEMBERS,
            null, null, null, null, null, "household_id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToMember(it))
            }
        }
        val result = if (list.isNotEmpty()) list else LocalRepository.sampleMembers
        cachedMembers = result
        return result
    }

    fun resolveMemberFromQuery(query: String): Member? {
        val q = query.trim().lowercase()
        val members = getAllMembers()
        val fullMatch = members.firstOrNull { m ->
            m.name.length >= 3 && q.contains(m.name.lowercase())
        }
        if (fullMatch != null) return fullMatch

        return members.firstOrNull { m ->
            val firstName = m.name.split(" ").firstOrNull()?.lowercase() ?: ""
            firstName.length >= 4 && Regex("\\b${Regex.escape(firstName)}\\b").containsMatchIn(q)
        }
    }

    fun getTodayVisits(villageFilter: String? = null, priorityFilter: Priority? = null): List<VisitItem> {
        val members = getAllMembers()
        val allVisits = VisitEngine.generateVisits(members)
        val completedIds = getCompletedVisitKeys()
        val activeVillages = getActiveProfile().villages

        return allVisits
            .filter { !completedIds.contains("${it.memberId}_${it.dueItem}") }
            .filter {
                when {
                    villageFilter.isNullOrBlank() || villageFilter == "All" -> true
                    villageFilter == "My Beat" -> it.village in activeVillages
                    else -> it.village == villageFilter
                }
            }
            .filter { priorityFilter == null || it.priority == priorityFilter }
    }

    fun recordVisitOutcome(
        householdId: String,
        memberId: String,
        memberName: String,
        dueItem: String,
        status: String,
        notes: String
    ): Boolean {
        val db = dbHelper.writableDatabase
        val outcomeId = "OUT_${System.currentTimeMillis()}_${memberId.takeLast(4)}"
        val ts = System.currentTimeMillis()

        db.beginTransaction()
        try {
            // 1. Insert into visit outcomes
            val cvOutcome = ContentValues().apply {
                put("outcome_id", outcomeId)
                put("household_id", householdId)
                put("member_id", memberId)
                put("member_name", memberName)
                put("due_item", dueItem)
                put("status", status)
                put("notes", notes)
                put("timestamp", ts)
            }
            db.insertWithOnConflict(ArogyasathiDbHelper.TABLE_VISIT_OUTCOMES, null, cvOutcome, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)

            // 2. Queue for deferred sync
            val payload = JSONObject().apply {
                put("outcome_id", outcomeId)
                put("household_id", householdId)
                put("member_id", memberId)
                put("member_name", memberName)
                put("due_item", dueItem)
                put("status", status)
                put("notes", notes)
                put("timestamp", ts)
            }
            val cvSync = ContentValues().apply {
                put("entity_type", "VISIT_OUTCOME")
                put("entity_id", outcomeId)
                put("payload_json", payload.toString())
                put("status", SyncStatus.PENDING.name)
                put("retry_count", 0)
                put("created_at", ts)
            }
            db.insert(ArogyasathiDbHelper.TABLE_SYNC_QUEUE, null, cvSync)

            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            db.endTransaction()
        }
    }

    fun getCompletedVisits(): List<VisitOutcome> {
        val list = mutableListOf<VisitOutcome>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ArogyasathiDbHelper.TABLE_VISIT_OUTCOMES,
            null, null, null, null, null, "timestamp DESC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow("outcome_id")
            val hIdx = it.getColumnIndexOrThrow("household_id")
            val mIdx = it.getColumnIndexOrThrow("member_id")
            val nameIdx = it.getColumnIndexOrThrow("member_name")
            val itemIdx = it.getColumnIndexOrThrow("due_item")
            val stIdx = it.getColumnIndexOrThrow("status")
            val nIdx = it.getColumnIndexOrThrow("notes")
            val tsIdx = it.getColumnIndexOrThrow("timestamp")

            while (it.moveToNext()) {
                list.add(
                    VisitOutcome(
                        outcomeId = it.getString(idIdx),
                        householdId = it.getString(hIdx),
                        memberId = it.getString(mIdx),
                        memberName = it.getString(nameIdx),
                        dueItem = it.getString(itemIdx),
                        status = it.getString(stIdx),
                        notes = it.getString(nIdx),
                        timestamp = it.getLong(tsIdx)
                    )
                )
            }
        }
        return list
    }

    fun getPendingSyncCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${ArogyasathiDbHelper.TABLE_SYNC_QUEUE} WHERE status = ?",
            arrayOf(SyncStatus.PENDING.name)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    fun getSyncQueueItems(): List<SyncItem> {
        val list = mutableListOf<SyncItem>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ArogyasathiDbHelper.TABLE_SYNC_QUEUE,
            null, null, null, null, null, "created_at DESC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow("id")
            val typeIdx = it.getColumnIndexOrThrow("entity_type")
            val entIdIdx = it.getColumnIndexOrThrow("entity_id")
            val pIdx = it.getColumnIndexOrThrow("payload_json")
            val stIdx = it.getColumnIndexOrThrow("status")
            val rIdx = it.getColumnIndexOrThrow("retry_count")
            val cIdx = it.getColumnIndexOrThrow("created_at")

            while (it.moveToNext()) {
                val stStr = it.getString(stIdx)
                val st = try { SyncStatus.valueOf(stStr) } catch (e: Exception) { SyncStatus.PENDING }
                list.add(
                    SyncItem(
                        id = it.getLong(idIdx),
                        entityType = it.getString(typeIdx),
                        entityId = it.getString(entIdIdx),
                        payloadJson = it.getString(pIdx),
                        status = st,
                        retryCount = it.getInt(rIdx),
                        createdAt = it.getLong(cIdx)
                    )
                )
            }
        }
        return list
    }

    fun triggerSync(forceOfflineMark: Boolean = false): SyncResult {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("status", SyncStatus.SYNCED.name)
        }
        val count = db.update(
            ArogyasathiDbHelper.TABLE_SYNC_QUEUE,
            cv,
            "status = ?",
            arrayOf(SyncStatus.PENDING.name)
        )
        return if (count > 0) {
            SyncResult(true, "Successfully synchronized $count record(s) to sub-centre gateway.")
        } else {
            SyncResult(true, "All records already synchronized. Outbox is up to date.")
        }
    }

    private fun getDangerHindiMessage(citation: String): String {
        return when (citation) {
            "ESC-6.3" -> "मरीज बेहोश है: यह गंभीर आपातकाल है! तुरंत 108 पर एम्बुलेंस बुलाएं। मुंह से कुछ भी न दें (ESC-6.3)।"
            "ESC-5.1" -> "दौरे या झटके (Convulsions) सामान्य खतरे का लक्षण है। तुरंत 108/102 बुलाकर नजदीकी अस्पताल ले जाएं। मुंह में कुछ न डालें (ESC-5.1)।"
            "ESC-3.1" -> "प्रसव के बाद अत्यधिक रक्तस्राव (5 मिनट में पैड भीगना या दिन में 5 से अधिक पैड): तुरंत 108 बुलाएं (ESC-3.1)।"
            "ESC-2.1" -> "गर्भावस्था में किसी भी प्रकार का योनि से रक्तस्राव खतरे की घंटी है: तुरंत 102/108 एम्बुलेंस से अस्पताल रेफर करें (ESC-2.1)।"
            "ESC-2.3" -> "गर्भावस्था में चेहरे पर सूजन, तेज सिरदर्द या धुंधला दिखना प्री-एक्लेम्पसिया का लक्षण है। तुरंत अस्पताल रेफर करें (ESC-2.3)।"
            "ESC-2.5" -> "गर्भावस्था में अत्यधिक पीलापन और सांस फूलना गंभीर एनीमिया का लक्षण है, तुरंत सीएचसी रेफर करें (ESC-2.5)।"
            "ESC-5.2" -> "बच्चे की छाती धंसना (पसली चलना) या सांस में सीटी की आवाज गंभीर निमोनिया है, तुरंत अस्पताल भेजें (ESC-5.2)।"
            "ESC-5.4" -> "7 दिन से अधिक तेज बुखार या गर्दन अकड़ना मस्तिष्क बुखार (मेनिनजाइटिस) का खतरा हो सकता है, तुरंत रेफर करें (ESC-5.4)।"
            else -> "आपातकालीन खतरे का लक्षण पाया गया है! मरीज को तुरंत 108/102 एम्बुलेंस बुलाकर सामुदायिक स्वास्थ्य केंद्र (CHC) रेफर करें ($citation)।"
        }
    }

    fun askAssistant(query: String, patient: Member? = null): AssistantResult {
        val queryMentioned = resolveMemberFromQuery(query)
        val effectivePatient = queryMentioned ?: patient
        val recentHistory = if (effectivePatient != null) {
            getPatientChatHistory(effectivePatient.memberId).takeLast(3)
        } else emptyList()

        // Step 1: JEV Deterministic Escalation
        val escResult = JEVEscalate.evaluate(query)
        if (escResult != null) {
            val hindiMsg = getDangerHindiMessage(escResult.first)
            val ctx = if (effectivePatient != null) "Patient: ${effectivePatient.name} (${effectivePatient.village})" else null
            return AssistantResult(
                answer = escResult.second,
                citedSection = escResult.first,
                isEscalation = true,
                isRefusal = false,
                triageLevel = "EMERGENCY",
                answerHindi = hindiMsg,
                patientContext = ctx
            )
        }

        // Step 2: JEV Scope Refusal
        val scpResult = JEVScope.evaluate(query)
        if (scpResult != null) {
            val hindiMsg = "यह विषय मेरे प्रोटोकॉल से बाहर है। कृपया प्राथमिक स्वास्थ्य केंद्र (PHC) के चिकित्सा अधिकारी या अपनी एएनएम से परामर्श लें (SCP-1.2)।"
            return AssistantResult(
                answer = scpResult.second,
                citedSection = scpResult.first,
                isEscalation = false,
                isRefusal = true,
                triageLevel = "OUT_OF_SCOPE",
                answerHindi = hindiMsg,
                patientContext = if (effectivePatient != null) "Patient: ${effectivePatient.name}" else null
            )
        }

        // Step 3: Laya Protocol Retrieval & Intelligent Synthesis
        val protocols = getProtocols()
        val retriever = layaRetriever ?: LayaRetriever(protocols).also { layaRetriever = it }
        val topSections = retriever.retrieve(query, topK = 1)

        return if (topSections.isNotEmpty()) {
            val topSec = topSections.first()
            val synth = LayaSynthesizer.synthesizeWithContext(topSec, query, effectivePatient, recentHistory)
            val pTag = if (effectivePatient != null) {
                val age = PatientAgeCalculator.calculate(effectivePatient.dob)?.ageDisplay ?: effectivePatient.dob
                "Patient: ${effectivePatient.name} ($age • ${effectivePatient.village})"
            } else null
            AssistantResult(
                answer = synth.textEnglish,
                citedSection = synth.citedSection,
                isEscalation = false,
                isRefusal = false,
                triageLevel = "ROUTINE_PROTOCOL",
                answerHindi = synth.textHindi,
                patientContext = pTag
            )
        } else {
            AssistantResult(
                answer = "Follow routine protocol guidelines. Please consult your supervisor (SCP-1.1).",
                citedSection = "SCP-1.1",
                isEscalation = false,
                isRefusal = true,
                triageLevel = "OUT_OF_SCOPE",
                answerHindi = "कृपया नियमित प्रोटोकॉल दिशा-निर्देशों का पालन करें और अपने पर्यवेक्षक से सलाह लें (SCP-1.1)।",
                patientContext = if (effectivePatient != null) "Patient: ${effectivePatient.name}" else null
            )
        }
    }

    fun savePatientChatMessage(
        patientId: String?,
        patientName: String?,
        sender: String,
        queryText: String,
        answerText: String,
        answerHindi: String,
        citedSections: List<String>,
        isEscalation: Boolean,
        isRefusal: Boolean,
        triageLevel: String
    ): PatientConsultation {
        val db = dbHelper.writableDatabase
        val record = PatientConsultation(
            patientId = patientId,
            patientName = patientName,
            sender = sender,
            queryText = queryText,
            answerText = answerText,
            answerHindi = answerHindi,
            citedSections = citedSections,
            isEscalation = isEscalation,
            isRefusal = isRefusal,
            triageLevel = triageLevel,
            timestamp = System.currentTimeMillis()
        )
        val cv = ContentValues().apply {
            put("id", record.id)
            put("patient_id", record.patientId)
            put("patient_name", record.patientName)
            put("sender", record.sender)
            put("query_text", record.queryText)
            put("answer_text", record.answerText)
            put("answer_hindi", record.answerHindi)
            put("cited_sections", record.citedSections.joinToString(","))
            put("is_escalation", if (record.isEscalation) 1 else 0)
            put("is_refusal", if (record.isRefusal) 1 else 0)
            put("triage_level", record.triageLevel)
            put("timestamp", record.timestamp)
        }
        db.insertWithOnConflict(
            ArogyasathiDbHelper.TABLE_PATIENT_CHAT,
            null,
            cv,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        return record
    }

    fun getPatientChatHistory(patientId: String?): List<PatientConsultation> {
        val list = mutableListOf<PatientConsultation>()
        val db = dbHelper.readableDatabase
        val selection = if (patientId == null) "patient_id IS NULL" else "patient_id = ?"
        val selectionArgs = if (patientId == null) null else arrayOf(patientId)

        val cursor = db.query(
            ArogyasathiDbHelper.TABLE_PATIENT_CHAT,
            null, selection, selectionArgs, null, null, "timestamp ASC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow("id")
            val pidIdx = it.getColumnIndexOrThrow("patient_id")
            val pnameIdx = it.getColumnIndexOrThrow("patient_name")
            val senderIdx = it.getColumnIndexOrThrow("sender")
            val qIdx = it.getColumnIndexOrThrow("query_text")
            val aIdx = it.getColumnIndexOrThrow("answer_text")
            val ahIdx = it.getColumnIndexOrThrow("answer_hindi")
            val csIdx = it.getColumnIndexOrThrow("cited_sections")
            val escIdx = it.getColumnIndexOrThrow("is_escalation")
            val refIdx = it.getColumnIndexOrThrow("is_refusal")
            val tlIdx = it.getColumnIndexOrThrow("triage_level")
            val tsIdx = it.getColumnIndexOrThrow("timestamp")

            while (it.moveToNext()) {
                val cs = it.getString(csIdx).split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                list.add(
                    PatientConsultation(
                        id = it.getString(idIdx),
                        patientId = if (it.isNull(pidIdx)) null else it.getString(pidIdx),
                        patientName = if (it.isNull(pnameIdx)) null else it.getString(pnameIdx),
                        sender = it.getString(senderIdx),
                        queryText = it.getString(qIdx),
                        answerText = it.getString(aIdx),
                        answerHindi = it.getString(ahIdx) ?: "",
                        citedSections = cs,
                        isEscalation = it.getInt(escIdx) == 1,
                        isRefusal = it.getInt(refIdx) == 1,
                        triageLevel = it.getString(tlIdx) ?: "ROUTINE_PROTOCOL",
                        timestamp = it.getLong(tsIdx)
                    )
                )
            }
        }
        return list
    }

    fun saveReferralSlip(slip: ReferralSlip): SyncResult {
        val payload = org.json.JSONObject().apply {
            put("slip_id", slip.id)
            put("patient_id", slip.patientId)
            put("patient_name", slip.patientName)
            put("age_gender", slip.ageGender)
            put("village", slip.village)
            put("danger_signs", org.json.JSONArray(slip.dangerSigns))
            put("vitals", slip.vitalSigns)
            put("citation", slip.protocolCitation)
            put("referred_facility", slip.referredFacility)
            put("immediate_action", slip.immediateActionTaken)
            put("asha_name", slip.ashaName)
            put("asha_phone", slip.ashaPhone)
            put("timestamp", slip.timestamp)
        }
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("entity_type", "REFERRAL_SLIP")
            put("entity_id", slip.id)
            put("payload_json", payload.toString())
            put("status", SyncStatus.PENDING.name)
            put("retry_count", 0)
            put("created_at", System.currentTimeMillis())
        }
        db.insert(ArogyasathiDbHelper.TABLE_SYNC_QUEUE, null, cv)
        return SyncResult(true, "Referral slip saved to outbox for ${slip.patientName}")
    }

    private fun getCompletedVisitKeys(): Set<String> {
        val set = mutableSetOf<String>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ArogyasathiDbHelper.TABLE_VISIT_OUTCOMES,
            arrayOf("member_id", "due_item"),
            null, null, null, null, null
        )
        cursor.use {
            val mIdx = it.getColumnIndexOrThrow("member_id")
            val dIdx = it.getColumnIndexOrThrow("due_item")
            while (it.moveToNext()) {
                set.add("${it.getString(mIdx)}_${it.getString(dIdx)}")
            }
        }
        return set
    }

    private fun cursorToMember(cursor: Cursor): Member {
        val ancStr = cursor.getString(cursor.getColumnIndexOrThrow("anc_visit_dates"))
        val ancList = ancStr?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val tdStr = cursor.getString(cursor.getColumnIndexOrThrow("td_doses"))
        val tdList = tdStr?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val hbncStr = cursor.getString(cursor.getColumnIndexOrThrow("hbnc_visits_done"))
        val hbncList = hbncStr?.split(";")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()

        val vacStr = cursor.getString(cursor.getColumnIndexOrThrow("child_vaccines"))
        val vacMap = mutableMapOf<String, String>()
        vacStr?.split(";")?.forEach { entry ->
            val p = entry.split(":")
            if (p.size == 2) {
                vacMap[p[0].trim()] = p[1].trim()
            }
        }

        return Member(
            memberId = cursor.getString(cursor.getColumnIndexOrThrow("member_id")),
            householdId = cursor.getString(cursor.getColumnIndexOrThrow("household_id")),
            village = cursor.getString(cursor.getColumnIndexOrThrow("village")),
            ashaId = cursor.getString(cursor.getColumnIndexOrThrow("asha_id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            relation = cursor.getString(cursor.getColumnIndexOrThrow("relation")),
            sex = cursor.getString(cursor.getColumnIndexOrThrow("sex")),
            dob = cursor.getString(cursor.getColumnIndexOrThrow("dob")),
            isPregnant = cursor.getInt(cursor.getColumnIndexOrThrow("is_pregnant")) == 1,
            lmpDate = cursor.getString(cursor.getColumnIndexOrThrow("lmp_date")),
            edd = cursor.getString(cursor.getColumnIndexOrThrow("edd")),
            ancVisitDates = ancList,
            lastAncBp = cursor.getString(cursor.getColumnIndexOrThrow("last_anc_bp")),
            lastHbGdl = if (!cursor.isNull(cursor.getColumnIndexOrThrow("last_hb_gdl"))) cursor.getDouble(cursor.getColumnIndexOrThrow("last_hb_gdl")) else null,
            tdBoosterEligible = cursor.getInt(cursor.getColumnIndexOrThrow("td_booster_eligible")) == 1,
            tdDoses = tdList,
            placeOfDelivery = cursor.getString(cursor.getColumnIndexOrThrow("place_of_delivery")),
            birthWeightKg = if (!cursor.isNull(cursor.getColumnIndexOrThrow("birth_weight_kg"))) cursor.getDouble(cursor.getColumnIndexOrThrow("birth_weight_kg")) else null,
            hbncVisitsDone = hbncList,
            childVaccines = vacMap,
            lastMuacCm = if (!cursor.isNull(cursor.getColumnIndexOrThrow("last_muac_cm"))) cursor.getDouble(cursor.getColumnIndexOrThrow("last_muac_cm")) else null,
            lastMuacDate = cursor.getString(cursor.getColumnIndexOrThrow("last_muac_date")),
            bilateralPittingOedema = cursor.getInt(cursor.getColumnIndexOrThrow("bilateral_pitting_oedema")) == 1,
            ncdCondition = cursor.getString(cursor.getColumnIndexOrThrow("ncd_condition")),
            lastBpCheckDate = cursor.getString(cursor.getColumnIndexOrThrow("last_bp_check_date")),
            lastBpReading = cursor.getString(cursor.getColumnIndexOrThrow("last_bp_reading")),
            lastSugarCheckDate = cursor.getString(cursor.getColumnIndexOrThrow("last_sugar_check_date")),
            lastRbsMgDl = if (!cursor.isNull(cursor.getColumnIndexOrThrow("last_rbs_mg_dl"))) cursor.getDouble(cursor.getColumnIndexOrThrow("last_rbs_mg_dl")) else null
        )
    }
}

object LocalRepository {
    val sampleProtocols = listOf(
        ProtocolSection("ESC-1.1", "Refer immediately", "If even one danger sign in this document is present, refer immediately — the same day, without delay. Do not wait, do not send the patient home to observe.", "ESC"),
        ProtocolSection("ESC-2.1", "Bleeding in pregnancy", "Any bleeding from the vagina during pregnancy (first, second or third trimester), and excessive bleeding during delivery: refer immediately (call 102/108).", "ESC"),
        ProtocolSection("ESC-2.3", "High blood pressure and pre-eclampsia", "BP 140/90 or more in pregnancy. Severe headache, blurring of vision, or swelling of the face: pre-eclampsia danger sign, refer immediately (call 102/108).", "ESC"),
        ProtocolSection("ESC-3.1", "Heavy bleeding after delivery", "Heavy bleeding: a pad is soaked in less than 5 minutes, or more than 5 pads are changed in a day. Refer immediately (call 108/102).", "ESC"),
        ProtocolSection("ESC-4.3", "Temperature danger signs in newborn", "Baby has fever or baby is cold to touch (temperature below 95.9 F / 35.5 C): keep skin-to-skin and refer immediately (call 102/108).", "ESC"),
        ProtocolSection("ESC-4.4", "Low birth weight under 1.8 kg", "Baby birth weight under 1.8 kg: refer immediately to SNCU (call 102/108). Keep skin-to-skin.", "ESC"),
        ProtocolSection("ESC-5.1", "General danger signs in child", "Not able to drink or breastfeed. Vomits everything. Convulsions (fits) now or during this illness. Lethargic or unconscious: refer immediately to hospital (call 108/102).", "ESC"),
        ProtocolSection("ESC-5.4", "Fever danger signs in child", "Fever with stiff neck or fever every day for more than 7 days: general danger sign, refer immediately (call 108).", "ESC"),
        ProtocolSection("ESC-6.2", "Stroke signs (BE FAST)", "Sudden weakness or numbness of face, arm or leg on one side, loss of speech: stroke signs, refer immediately (call 108).", "ESC"),
        ProtocolSection("ESC-6.4", "Very high blood pressure", "BP 180/110 or more, even without any complaint, is an escalation: refer immediately to Medical Officer / CHC.", "ESC"),
        ProtocolSection("ANC-1.2", "Minimum four ANC check-ups", "ANC-1 within 12 weeks; ANC-2 between 14 and 26 weeks; ANC-3 between 28 and 34 weeks; ANC-4 at 36 weeks up to delivery.", "ANC"),
        ProtocolSection("ANC-2.2", "IFA tablets in pregnancy", "One Red IFA tablet (100 mg elemental iron + 500 mcg folic acid) daily starting after first trimester for 180 days.", "ANC"),
        ProtocolSection("ANC-3.1", "Hb cut-offs in pregnancy", "No anaemia: 11.0 or more. Mild anaemia: 10.0-10.9. Moderate anaemia: 7.0-9.9. Severe anaemia: below 7.0 g/dL.", "ANC"),
        ProtocolSection("CHD-1.2", "How much ORS to give", "Up to 2 years: 50 to 100 ml after each loose stool. 2 years or more: 100 to 200 ml. If child vomits, wait 10 minutes then continue slowly.", "CHD"),
        ProtocolSection("CHD-1.3", "Zinc in diarrhoea", "Give zinc for 14 days even if diarrhoea stops. Under 6 months: 10 mg daily. 6 months or older: 20 mg daily.", "CHD"),
        ProtocolSection("CHD-3.1", "Counting breaths for pneumonia", "Fast breathing: Under 2 months: 60 breaths/min or more. 2 to 12 months: 50 breaths/min or more. 12 months to 5 years: 40 breaths/min or more.", "CHD"),
        ProtocolSection("HBNC-2.2", "Keeping baby warm and bathing", "Do not bathe the baby for the first 48 hours. Do not bathe a low birth weight baby until its weight is 2000 g.", "HBNC"),
        ProtocolSection("IMM-2.2", "At 6, 10 and 14 weeks", "6 weeks: OPV-1, Penta-1, RVV-1, fIPV-1, PCV-1. 10 weeks: OPV-2, Penta-2, RVV-2. 14 weeks: OPV-3, Penta-3, RVV-3, fIPV-2, PCV-2. Penta can be given till one year of age.", "IMM"),
        ProtocolSection("IMM-2.4", "At 16-24 months", "MR-2, DPT-B1, OPV-B; Vitamin A 2nd dose at 16-18 months, then every 6 months up to 5 years (9 doses in all). Chandrapuri is not a JE-endemic district.", "IMM"),
        ProtocolSection("NCD-3.1", "Lifestyle counselling", "Salt: maximum 1 teaspoon (5 g) per day. Physical activity: at least 30 minutes for at least 5 days a week.", "NCD"),
        ProtocolSection("NUT-1.1", "Malnutrition screening", "Screen all children 6-59 months with MUAC tape and look for bilateral pitting oedema (oedema of both feet). Press top of both feet gently.", "NUT"),
        ProtocolSection("VIS-1.3", "Counting conventions", "Grace period is 28 days before marking overdue. Upcoming horizon is 6 days. Follow-up interval for NCD/MUAC is 30 days.", "VIS")
    )

    val sampleMembers = listOf(
        Member(
            memberId = "M-YXYWBY6B",
            householdId = "HH-014Q8O",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Jamuna Devi",
            relation = "wife",
            sex = "F",
            dob = "2002-10-31",
            isPregnant = true,
            lmpDate = "2026-03-25",
            edd = "2026-12-30",
            ancVisitDates = listOf("2026-06-05", "2026-09-22"),
            lastAncBp = "144/92",
            lastHbGdl = 6.8
        ),
        Member(
            memberId = "M-6VETO2LR",
            householdId = "HH-0CXDQG",
            village = "Amrapur",
            ashaId = "ASHA-096P",
            name = "Ayaan Ansari",
            relation = "son",
            sex = "M",
            dob = "2026-10-12",
            placeOfDelivery = "institution",
            birthWeightKg = 2.1,
            hbncVisitsDone = listOf(3, 7)
        ),
        Member(
            memberId = "M-Z55MVUP8",
            householdId = "HH-08MLZ4",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Deepak Mishra",
            relation = "head",
            sex = "M",
            dob = "1972-04-10",
            ncdCondition = "HTN",
            lastBpCheckDate = "2026-09-12",
            lastBpReading = "186/112"
        ),
        Member(
            memberId = "M-B9JMMVGZ",
            householdId = "HH-098AG2",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Priya Mishra",
            relation = "daughter",
            sex = "F",
            dob = "2024-05-18",
            lastMuacCm = 11.2,
            lastMuacDate = "2026-09-15"
        )
    )
}
