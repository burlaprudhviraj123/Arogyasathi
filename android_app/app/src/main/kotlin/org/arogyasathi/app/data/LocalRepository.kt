package org.arogyasathi.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.runBlocking
import org.arogyasathi.app.core.*
import org.arogyasathi.app.data.local.ArogyasathiRoomDatabase
import org.arogyasathi.app.data.local.DangerEscalationEntity
import org.arogyasathi.app.data.local.PatientChatEntity
import org.arogyasathi.app.jev.JEVEscalate
import org.arogyasathi.app.jev.JEVScope
import org.arogyasathi.app.laya.LayaProtocolSources
import org.arogyasathi.app.laya.LayaV2Engine
import org.arogyasathi.app.sync.NeonPostgresSyncClient
import org.arogyasathi.app.visit.VisitEngine
import org.json.JSONObject

data class AssistantResult(
    val answer: String,
    val citedSection: String,
    val isEscalation: Boolean,
    val isRefusal: Boolean,
    val triageLevel: String, // "EMERGENCY", "OUT_OF_SCOPE", "ROUTINE_PROTOCOL"
    val answerHindi: String = "",
    val answerTelugu: String = "",
    val answerTamil: String = "",
    val answerKannada: String = "",
    val answerMalayalam: String = "",
    val patientContext: String? = null,
    val citedSections: List<String> = listOf(citedSection).filter { it.isNotBlank() },
    val latencyMs: Long = 0L
) {
    fun getAnswerForLanguage(langCode: String): String {
        return when (langCode.take(2).lowercase()) {
            "hi" -> if (answerHindi.isNotBlank()) answerHindi else "स्वीकृत प्रोटोकॉल नीचे अंग्रेज़ी में है। इसका अर्थ ANM या चिकित्सा अधिकारी से पुष्टि करें।\n\n$answer"
            "te" -> if (answerTelugu.isNotBlank()) answerTelugu else "ఆమోదించిన ప్రోటోకాల్ క్రింద ఆంగ్లంలో ఉంది. దయచేసి ANM లేదా వైద్య అధికారితో అర్థాన్ని నిర్ధారించండి.\n\n$answer"
            "ta" -> if (answerTamil.isNotBlank()) answerTamil else "Approved protocol content is shown in English. Please confirm its meaning with the ANM or Medical Officer.\n\n$answer"
            "kn" -> if (answerKannada.isNotBlank()) answerKannada else "Approved protocol content is shown in English. Please confirm its meaning with the ANM or Medical Officer.\n\n$answer"
            "ml" -> if (answerMalayalam.isNotBlank()) answerMalayalam else "Approved protocol content is shown in English. Please confirm its meaning with the ANM or Medical Officer.\n\n$answer"
            else -> answer
        }
    }
}

data class SyncResult(
    val success: Boolean,
    val message: String
)

class ArogyasathiRepository(private val context: Context) {

    private val dbHelper = ArogyasathiDbHelper.getInstance(context)
    val roomDb: ArogyasathiRoomDatabase = ArogyasathiRoomDatabase.getInstance(context)
    val neonSyncClient: NeonPostgresSyncClient = NeonPostgresSyncClient(roomDb)

    @Volatile
    private var cachedProtocols: List<ProtocolSection>? = null
    @Volatile
    private var layaV2Engine: LayaV2Engine? = null
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

    fun getAppLanguage(): org.arogyasathi.app.ui.AppLanguage {
        val prefs = context.getSharedPreferences("arogyasathi_prefs", Context.MODE_PRIVATE)
        val code = prefs.getString("app_language", org.arogyasathi.app.ui.AppLanguage.ENGLISH.code)
            ?: org.arogyasathi.app.ui.AppLanguage.ENGLISH.code
        return org.arogyasathi.app.ui.AppLanguage.fromCode(code)
    }

    fun setAppLanguage(language: org.arogyasathi.app.ui.AppLanguage) {
        val prefs = context.getSharedPreferences("arogyasathi_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_language", language.code).apply()
    }

    fun isVoiceModelDownloaded(): Boolean {
        val prefs = context.getSharedPreferences("arogyasathi_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("voice_model_downloaded", false)
    }

    fun setVoiceModelDownloaded(downloaded: Boolean) {
        val prefs = context.getSharedPreferences("arogyasathi_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("voice_model_downloaded", downloaded).apply()
    }

    fun verifyPhoneNumber(rawPhone: String): AshaProfile? {
        val clean = rawPhone.replace(Regex("[^0-9]"), "")
        return allProfiles.find {
            it.phone.replace(Regex("[^0-9]"), "").endsWith(clean.takeLast(10))
        }
    }

    fun getProtocols(): List<ProtocolSection> {
        cachedProtocols?.let { return it }
        val list = roomDb.protocolDao().getAllProtocols().map { entity ->
            val protocol = entity.toDomain()
            protocol.copy(sourceFilename = LayaProtocolSources.filenameFor(protocol))
        }
        val result = if (list.isNotEmpty()) list else LocalRepository.sampleProtocols
        cachedProtocols = result
        return result
    }

    suspend fun refreshFromNeonPostgres(): NeonPostgresSyncClient.BootstrapSyncReport {
        val report = neonSyncClient.pullAndCacheFromNeon(activeAshaId)
        if (report.syncedFromRemote) {
            cachedProtocols = null
            layaV2Engine = null
            cachedMembers = null
        }
        return report
    }

    fun getHouseholds(searchQuery: String? = null, villageFilter: String? = null): List<Household> {
        val list = mutableListOf<Household>()
        val db = dbHelper.readableDatabase

        val selection = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (!villageFilter.isNullOrBlank() && villageFilter != "All") {
            if (villageFilter == "My Villages" || villageFilter == "My Beat") {
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
        return roomDb.memberDao().getMembersByHousehold(householdId).map { it.toDomain() }
    }

    fun getMemberById(memberId: String): Member? {
        return roomDb.memberDao().getMemberById(memberId)?.toDomain()
    }

    fun getAllMembers(): List<Member> {
        cachedMembers?.let { return it }
        val list = roomDb.memberDao().getAllMembers().map { it.toDomain() }
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
                    villageFilter == "My Villages" || villageFilter == "My Beat" -> it.village in activeVillages
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

            // 2. Queue for deferred sync to central Neon Postgres
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
            roomDb.notifyDataChanged()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            db.endTransaction()
        }
    }

    fun getCompletedVisits(): List<VisitOutcome> {
        return roomDb.visitOutcomeDao().getAllOutcomes().map { it.toDomain() }
    }

    fun getPendingSyncCount(): Int {
        return roomDb.syncQueueDao().getPendingCount()
    }

    fun getSyncQueueItems(): List<SyncItem> {
        return roomDb.syncQueueDao().getAllSyncItems().map { it.toDomain() }
    }

    fun triggerSync(forceOfflineMark: Boolean = false): SyncResult {
        return runBlocking {
            neonSyncClient.pushOutboxToNeon(activeAshaId, forceOfflineMark = forceOfflineMark)
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

    private fun getDangerTeluguMessage(citation: String): String {
        return when (citation) {
            "ESC-6.3" -> "రోగి స్పృహ కోల్పోయారు: ఇది తీవ్రమైన అత్యవసర పరిస్థితి! వెంటనే 108 కు కాల్ చేసి అంబులెన్స్ పిలవండి. నోటి ద్వారా ఏమీ ఇవ్వవద్దు (ESC-6.3)."
            "ESC-5.1" -> "ఫిట్స్ లేదా మూర్ఛ (Convulsions) ప్రమాదకర లక్షణం. వెంటనే 108/102 కు కాల్ చేసి సమీప ఆసుపత్రికి తరలించండి. నోటిలో ఏమీ పెట్టవద్దు (ESC-5.1)."
            "ESC-3.1" -> "ప్రసవం తర్వాత తీవ్రమైన రక్తస్రావం (5 నిమిషాల్లో ప్యాడ్ తడిసిపోవడం): వెంటనే 108 కి కాల్ చేయండి (ESC-3.1)."
            "ESC-2.1" -> "గర్భధారణ సమయంలో ఏ విధమైన యోని రక్తస్రావమైనా ప్రమాద సంకేతం: వెంటనే 102/108 అంబులెన్స్ ద్వారా ఆసుపత్రికి పంపండి (ESC-2.1)."
            "ESC-2.3" -> "గర్భధారణలో ముఖం వాపు, తీవ్రమైన తలనొప్పి ప్రీ-ఎక్లాంప్సియా లక్షణాలు. వెంటనే ఆసుపత్రికి తరలించండి (ESC-2.3)."
            "ESC-2.5" -> "గర్భధారణలో తీవ్రమైన పాలిపోవడం మరియు శ్వాస తీసుకోవడంలో ఇబ్బంది తీవ్ర రక్తహీనత, వెంటనే CHC కి తరలించండి (ESC-2.5)."
            "ESC-5.2" -> "పిల్లలలో ఛాతీ లోపలికి లాగడం లేదా శ్వాసలో పిల్లికూతలు తీవ్రమైన న్యుమోనియా, వెంటనే ఆసుపత్రికి తరలించండి (ESC-5.2)."
            "ESC-5.4" -> "7 రోజుల కంటే ఎక్కువ తీవ్రమైన జ్వరం లేదా మెడ పట్టేయడం మెనింజైటిస్ కావచ్చు, వెంటనే తరలించండి (ESC-5.4)."
            else -> "అత్యవసర ప్రమాద లక్షణం గుర్తించబడింది! వెంటనే 108/102 అంబులెన్స్ పిలిచి రోగిని కమ్యూనిటీ హెల్త్ సెంటర్ (CHC) కి తరలించండి ($citation)."
        }
    }

    private fun getDangerTamilMessage(citation: String): String {
        return when (citation) {
            "ESC-6.3" -> "நோயாளி மயக்கமடைந்துள்ளார்: இது அவசர நிலை! உடனடியாக 108 ஆம்புலன்ஸை அழைக்கவும். வாயில் எதுவும் கொடுக்க வேண்டாம் (ESC-6.3)."
            "ESC-5.1" -> "வலிப்பு (Convulsions) ஒரு ஆபத்தான அறிகுறி. உடனடியாக 108/102 அழைத்து அருகில் உள்ள மருத்துவமனைக்கு கொண்டு செல்லவும் (ESC-5.1)."
            "ESC-3.1" -> "பிரசவத்திற்குப் பின் அதிக ரத்தப்போக்கு (5 நிமிடத்தில் பேட் நனைவது): உடனடியாக 108 அழைக்கவும் (ESC-3.1)."
            "ESC-2.1" -> "கர்ப்ப காலத்தில் ஏற்படும் யோனி ரத்தப்போக்கு ஆபத்து அறிகுறி: உடனடியாக 102/108 ஆம்புலன்ஸ் மூலம் மருத்துவமனைக்கு அனுப்பவும் (ESC-2.1)."
            "ESC-2.3" -> "கர்ப்ப காலத்தில் முக வீக்கம், கடுமையான தலைவலி ப்ரீ-எக்லாம்ப்சியாவின் அறிகுறிகள். உடனே மருத்துவமனைக்கு அனுப்பவும் (ESC-2.3)."
            "ESC-2.5" -> "கர்ப்ப காலத்தில் அதிக வெளிறிய நிறம் கடுமையான ரத்தசோகையின் அறிகுறி, உடனே CHC க்கு அனுப்பவும் (ESC-2.5)."
            "ESC-5.2" -> "குழந்தையின் விலா எலும்பு உள்வாங்குதல் கடுமையான நிமோனியாவின் அறிகுறி, உடனே மருத்துவமனைக்கு அனுப்பவும் (ESC-5.2)."
            "ESC-5.4" -> "7 நாட்களுக்கு மேல் காய்ச்சல் அல்லது கழுத்து விறைப்பு ஆபத்து, உடனே மருத்துவமனைக்கு அனுப்பவும் (ESC-5.4)."
            else -> "அவசர ஆபத்து அறிகுறி கண்டறியப்பட்டுள்ளது! உடனே 108/102 ஆம்புலன்ஸ் மூலம் சமூக சுகாதார மையத்திற்கு (CHC) அனுப்பவும் ($citation)."
        }
    }

    private fun getDangerKannadaMessage(citation: String): String {
        return when (citation) {
            "ESC-6.3" -> "ರೋಗಿ ಪ್ರಜ್ಞಾಹೀನರಾಗಿದ್ದಾರೆ: ಇದು ತೀವ್ರ ತುರ್ತು ಪರಿಸ್ಥಿತಿ! ತಕ್ಷಣ 108 ಆಂಬ್ಯುಲೆನ್ಸ್ ಕರೆ ಮಾಡಿ. ಬಾಯಿಗೆ ಏನನ್ನೂ ನೀಡಬೇಡಿ (ESC-6.3)."
            "ESC-5.1" -> "ಫಿಟ್ಸ್ ಅಥವಾ ಸೆಳೆತ (Convulsions) ಗಂಭೀರ ಅಪಾಯದ ಲಕ್ಷಣ. ತಕ್ಷಣ 108/102 ಕರೆ ಮಾಡಿ ಆಸ್ಪತ್ರೆಗೆ ಕರೆದೊಯ್ಯಿರಿ (ESC-5.1)."
            "ESC-3.1" -> "ಹೆರಿಗೆಯ ನಂತರ ಅತಿಯಾದ ರಕ್ತಸ್ರಾವ: ತಕ್ಷಣ 108 ಕರೆ ಮಾಡಿ (ESC-3.1)."
            "ESC-2.1" -> "ಗರ್ಭಾವಸ್ಥೆಯಲ್ಲಿ ಯೋನಿ ರಕ್ತಸ್ರಾವ ಗಂಭೀರ ಅಪಾಯದ ಸಂಕೇತ: ತಕ್ಷಣ 102/108 ಆಂಬ್ಯುಲೆನ್ಸ್ ಮೂಲಕ ಆಸ್ಪತ್ರೆಗೆ ಕಳುಹಿಸಿ (ESC-2.1)."
            "ESC-2.3" -> "ಗರ್ಭಾವಸ್ಥೆಯಲ್ಲಿ ಮುಖದ ಊತ, ತೀವ್ರ ತಲೆನೋವು ಪ್ರಿಕ್ಲಾಂಪ್ಸಿಯಾದ ಲಕ್ಷಣ. ತಕ್ಷಣ ಆಸ್ಪತ್ರೆಗೆ ಕಳುಹಿಸಿ (ESC-2.3)."
            "ESC-2.5" -> "ಗರ್ಭಾವಸ್ಥೆಯಲ್ಲಿ ತೀವ್ರ ರಕ್ತಹೀನತೆ, ತಕ್ಷಣ CHC ಗೆ ಕಳುಹಿಸಿ (ESC-2.5)."
            "ESC-5.2" -> "ಮಗುವಿನ ಎದೆ ಎಳೆಯುವುದು ತೀವ್ರ ನ್ಯುಮೋನಿಯಾ, ತಕ್ಷಣ ಆಸ್ಪತ್ರೆಗೆ ಕರೆದೊಯ್ಯಿರಿ (ESC-5.2)."
            "ESC-5.4" -> "7 ದಿನಗಳಿಗಿಂತ ಹೆಚ್ಚು ಜ್ವರ ಅಪಾಯ, ತಕ್ಷಣ ಆಸ್ಪತ್ರೆಗೆ ಕಳುಹಿಸಿ (ESC-5.4)."
            else -> "ತುರ್ತು ಅಪಾಯದ ಲಕ್ಷಣ ಕಂಡುಬಂದಿದೆ! ತಕ್ಷಣ 108/102 ಆಂಬ್ಯುಲೆನ್ಸ್ ಕರೆ ಮಾಡಿ ಸಮುದಾಯ ಆರೋಗ್ಯ ಕೇಂದ್ರಕ್ಕೆ (CHC) ಕಳುಹಿಸಿ ($citation)."
        }
    }

    private fun getDangerMalayalamMessage(citation: String): String {
        return when (citation) {
            "ESC-6.3" -> "രോഗി അബോധാവസ്ഥയിലാണ്: ഇത് അടിയന്തര സാഹചര്യമാണ്! ഉടൻ 108 ആംബുലൻസ് വിളിക്കുക. വായിലൂടെ ഒന്നും നൽകരുത് (ESC-6.3)."
            "ESC-5.1" -> "ഫിറ്റ്സ് അല്ലെങ്കിൽ അപസ്മാരം (Convulsions) ഗുരുതര അപകട ലക്ഷണമാണ്. ഉടൻ 108/102 വിളിച്ച് ആശുപത്രിയിലെത്തിക്കുക (ESC-5.1)."
            "ESC-3.1" -> "പ്രസവശേഷം അമിത രಕ್ತസ്രാവം: ഉടൻ 108 വിളിക്കുക (ESC-3.1)."
            "ESC-2.1" -> "ഗർഭാവസ്ഥയിലെ യോനി രക്തസ്രാവം അപകട ലക്ഷണമാണ്: ഉടൻ 102/108 ആംബുലൻസിൽ ആശുപത്രിയിലെത്തിക്കുക (ESC-2.1)."
            "ESC-2.3" -> "ഗർഭാവസ്ഥയിലെ മുഖവീക്കം, കഠിനമായ തലവേദന പ്രീ-എക്ലാംസിയ ലക്ഷണങ്ങളാണ്. ഉടൻ ആശുപത്രിയിലെത്തിക്കുക (ESC-2.3)."
            "ESC-2.5" -> "ഗർഭാവസ്ഥയിലെ കഠിനമായ വിളർച്ച ഗുരുതരമാണ്, ഉടൻ CHC-ലേക്ക് റഫർ ചെയ്യുക (ESC-2.5)."
            "ESC-5.2" -> "കുട്ടിയുടെ നെഞ്ച് ഉള്ളിലേക്ക് വലിയുന്നത് കഠിന ന്യുമോണിയയാണ്, ഉടൻ ആശുപത്രിയിലെത്തിക്കുക (ESC-5.2)."
            "ESC-5.4" -> "7 ദിവസത്തിലധികം കടുത്ത പനി മസ്തിഷ്കജ്വര സാധ്യതയാണ്, ഉടൻ റഫർ ചെയ്യുക (ESC-5.4)."
            else -> "അടിയന്തര അപകട ലക്ഷണം കണ്ടെത്തി! ഉടൻ 108/102 ആംബുലൻസ് വിളിച്ച് CHC-ലേക്ക് റഫർ ചെയ്യുക ($citation)."
        }
    }

    private fun isAnatomicallyIncompatible(query: String, sectionId: String): Boolean {
        val q = query.lowercase()

        // Legitimate pediatric cold signs permitted by protocols (CHD-3.1 non-severe cold)
        if ("runny nose" in q || "running nose" in q) {
            // Permitted for respiratory protocol
        } else if (Regex("\\b(ble{1,3}d|ble{1,3}ding|blood|raktham|khoon).*from.*(nose|naak|mookku|mukku|mooku)\\b|\\bnosebleed\\b|\\bepistaxis\\b|\\bnaak se khoon\\b|\\bnose\\b|\\bnaak\\b|\\bmookku\\b|\\bmukku\\b|\\bmooku\\b|\\bnaasi\\b").containsMatchIn(q)) {
            return true
        }

        // Non-protocol anatomical systems
        val nonProtocolOrgans = listOf(
            Regex("\\bear\\b|\\bears\\b|\\bkaan\\b|\\bchevi\\b|\\bchevu\\b|\\bchevulu\\b|\\bcevu\\b|\\bcevi\\b|\\bkaadhu\\b|\\bkevi\\b|\\bkivi\\b|చెవు|చెవి"),
            Regex("\\btooth\\b|\\bteeth\\b|\\bdaant\\b|\\bdant\\b|\\bpal\\b|\\bpallu\\b|\\bhallu\\b|\\bgum\\b|\\bdentist\\b|పళ్ళు|పన్ను"),
            Regex("\\bthroat\\b|\\bgala\\b|\\bthonda\\b|\\bgontu\\b|\\bgonthu\\b|\\btonsil\\b|గొంతు"),
            Regex("\\burine\\b|\\bpeshab\\b|\\bmutram\\b|\\bmootram\\b|\\bmoothram\\b|\\bkidney\\b|\\bstone\\b|\\bpathri\\b|మూత్రం"),
            Regex("\\bfracture\\b|\\bbone\\b|\\bhaddi\\b|\\belumbu\\b|\\bjoint\\b|\\bghutn[a-z]*\\b|\\bknee\\b|\\byemuka\\b|\\bemuka\\b|\\bvirigindi\\b|విరిగింది"),
            Regex("\\bskin rash\\b|\\bkhujli\\b|\\bitching\\b|\\bringworm\\b|\\bfungal\\b|\\babscess\\b|\\bgajji\\b|\\bduradha\\b|దురద|గజ్జి"),
            Regex("\\bburn\\b|\\bjal gaya\\b|\\btheekayam\\b|\\bkaalindhi\\b|కాలింది"),
            Regex("\\beye\\b|\\beyes\\b|\\baankh\\b|\\bankh\\b|\\baankhon\\b|\\bkallu\\b|\\bkannu\\b|\\bkanla\\b|\\bkannil\\b|కళ్ళు|కన్ను")
        )
        if (nonProtocolOrgans.any { it.containsMatchIn(q) }) {
            return true
        }

        // Maternal vaginal bleeding protocol (ESC-2.1 / ESC-3.1) without any pregnancy/postpartum context
        if (sectionId.startsWith("ESC-2.1") || sectionId.startsWith("ESC-3.1")) {
            val maternalWords = listOf(
                Regex("\\bpregnan[a-z]*\\b"), Regex("\\bgarbh[a-z]*\\b"), Regex("\\btrimester\\b"), Regex("\\bvagina[a-z]*\\b"),
                Regex("\\bmahila\\b"), Regex("\\baurat\\b"), Regex("\\bprasav\\b"), Regex("\\bfoetal\\b"), Regex("\\bfetal\\b"),
                Regex("\\bdelivery\\b"), Regex("\\bpad\\b"), Regex("\\bpostpartum\\b"), Regex("\\bpregnancy\\b"),
                Regex("\\bnela\\b"), Regex("\\bnelalu\\b"), Regex("\\bthalli\\b"), Regex("\\bbidda\\b")
            )
            if (!maternalWords.any { it.containsMatchIn(q) }) {
                return true
            }
        }

        return false
    }

    private fun askAssistantV2(query: String, patient: Member?): AssistantResult {
        val startedAt = System.nanoTime()
        val patientContext = patient?.let { "Patient: ${it.name} (${it.village})" }
        fun elapsedMs() = (System.nanoTime() - startedAt) / 1_000_000L

        fun sourceFor(sectionIds: String, protocols: List<ProtocolSection>): Pair<List<String>, String> {
            val requestedIds = Regex("[A-Z]{2,4}-\\d+(?:\\.\\d+)?")
                .findAll(sectionIds.uppercase())
                .map { it.value }
                .distinct()
                .toList()
            val cited = requestedIds.mapNotNull { id ->
                protocols.firstOrNull { it.sectionId.equals(id, ignoreCase = true) }
            }
            val formatted = cited.mapNotNull { section ->
                val filename = LayaProtocolSources.filenameFor(section)
                if (filename.isBlank()) return@mapNotNull null
                val heading = section.body.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("#") }
                    ?.replace(Regex("^#{1,6}\\s*"), "")
                    ?.replaceFirst(Regex("^[A-Z]{2,4}-\\d+(?:\\.\\d+)?\\s*"), "")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: section.title
                "$filename — $heading (${section.sectionId})"
            }
            return cited.map { it.sectionId } to formatted.joinToString("\n") { "Source: $it" }
        }

        val emergency = JEVEscalate.evaluate(query)
        if (emergency != null) {
            val source = sourceFor("${emergency.first} ${emergency.second}", getProtocols())
            val citation = source.second.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()
            return AssistantResult(
                answer = emergency.second + citation,
                citedSection = source.first.joinToString(", "),
                isEscalation = true,
                isRefusal = false,
                triageLevel = "EMERGENCY",
                answerHindi = getDangerHindiMessage(emergency.first) + citation,
                answerTelugu = getDangerTeluguMessage(emergency.first) + citation,
                answerTamil = getDangerTamilMessage(emergency.first) + citation,
                answerKannada = getDangerKannadaMessage(emergency.first) + citation,
                answerMalayalam = getDangerMalayalamMessage(emergency.first) + citation,
                patientContext = patientContext,
                citedSections = source.first,
                latencyMs = elapsedMs()
            )
        }

        val outOfScope = JEVScope.evaluate(query)
        if (outOfScope != null) {
            val source = sourceFor("${outOfScope.first} ${outOfScope.second}", getProtocols())
            val citation = source.second.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()
            return AssistantResult(
                answer = outOfScope.second + citation,
                citedSection = source.first.joinToString(", "),
                isEscalation = false,
                isRefusal = true,
                triageLevel = "OUT_OF_SCOPE",
                answerHindi = "यह विषय मेरे प्रोटोकॉल से बाहर है। कृपया PHC के चिकित्सा अधिकारी या अपनी ANM से परामर्श लें।$citation",
                answerTelugu = "ఈ విషయం నా ప్రోటోకాల్ పరిధికి మించినది. దయచేసి PHC వైద్య అధికారి లేదా మీ ANM ను సంప్రదించండి.$citation",
                answerTamil = "இந்த கேள்வி நெறிமுறைக்கு அப்பாற்பட்டது. PHC மருத்துவ அதிகாரி அல்லது ANM-ஐ அணுகவும்.$citation",
                answerKannada = "ಈ ವಿಷಯವು ಪ್ರೋಟೋಕಾಲ್ ವ್ಯಾಪ್ತಿಯಿಂದ ಹೊರಗಿದೆ. PHC ವೈದ್ಯಾಧಿಕಾರಿ ಅಥವಾ ANM ಅವರನ್ನು ಸಂಪರ್ಕಿಸಿ.$citation",
                answerMalayalam = "ഈ വിഷയം പ്രോട്ടോക്കോൾ പരിധിക്ക് പുറത്താണ്. PHC മെഡിക്കൽ ഓഫീസറെയോ ANM-നെയോ സമീപിക്കുക.$citation",
                patientContext = patientContext,
                citedSections = source.first,
                latencyMs = elapsedMs()
            )
        }

        val effectivePatient = resolveMemberFromQuery(query) ?: patient
        val resolvedPatientContext = effectivePatient?.let { "Patient: ${it.name} (${it.village})" }
        val protocols = getProtocols()
        val engine = layaV2Engine ?: LayaV2Engine(protocols).also { layaV2Engine = it }
        val response = engine.answer(query)
        val topSection = response.sections.firstOrNull()
        if (topSection != null && isAnatomicallyIncompatible(query, topSection.sectionId)) {
            val source = sourceFor("SCP-1.2", protocols)
            val citation = source.second.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()
            return AssistantResult(
                answer = "This condition is outside frontline community protocols. Please refer the beneficiary to the Medical Officer at the PHC.$citation",
                citedSection = source.first.joinToString(", "),
                isEscalation = false,
                isRefusal = true,
                triageLevel = "OUT_OF_SCOPE",
                answerHindi = "यह लक्षण सामुदायिक प्रोटोकॉल से बाहर है। कृपया PHC के चिकित्सा अधिकारी या ANM से संपर्क करें।$citation",
                answerTelugu = "ఈ లక్షణం కమ్యూనిటీ ప్రోటోకాల్ పరిధికి మించినది. దయచేసి PHC వైద్య అధికారి లేదా ANM ను సంప్రదించండి.$citation",
                answerTamil = "இந்த அறிகுறி நெறிமுறைக்கு அப்பாற்பட்டது. PHC மருத்துவ அதிகாரி அல்லது ANM-ஐ அணுகவும்.$citation",
                answerKannada = "ಈ ಲಕ್ಷಣವು ಪ್ರೋಟೋಕಾಲ್ ವ್ಯಾಪ್ತಿಗೆ ಮೀರಿದೆ. PHC ವೈದ್ಯಾಧಿಕಾರಿ ಅಥವಾ ANM ಅವರನ್ನು ಸಂಪರ್ಕಿಸಿ.$citation",
                answerMalayalam = "ഈ ലക്ഷണം പ്രോട്ടോക്കോൾ പരിധിക്ക് പുറത്താണ്. PHC മെഡിക്കൽ ഓഫീസറെയോ ANM-നെയോ സമീപിക്കുക.$citation",
                patientContext = resolvedPatientContext,
                citedSections = source.first,
                latencyMs = elapsedMs()
            )
        }

        if (response.abstained) {
            return AssistantResult(
                answer = response.answer,
                citedSection = "",
                isEscalation = false,
                isRefusal = true,
                triageLevel = "INSUFFICIENT_EVIDENCE",
                answerHindi = "स्वीकृत प्रोटोकॉल में इस प्रश्न का सुरक्षित उत्तर देने के लिए पर्याप्त जानकारी नहीं है। कृपया ANM या PHC के चिकित्सा अधिकारी से सलाह लें। आपात स्थिति में तुरंत रेफर करें।",
                answerTelugu = "ఈ ప్రశ్నకు సురక్షితంగా సమాధానం ఇవ్వడానికి ఆమోదించిన ప్రోటోకాల్‌లో తగిన సమాచారం లేదు. దయచేసి ANM లేదా PHC వైద్య అధికారిని సంప్రదించండి. అత్యవసరమైతే వెంటనే రిఫర్ చేయండి.",
                patientContext = resolvedPatientContext,
                citedSections = emptyList(),
                latencyMs = elapsedMs()
            )
        }

        val citedIds = response.citations.map { it.sectionId }
        return AssistantResult(
            answer = response.answer,
            citedSection = citedIds.joinToString(", "),
            isEscalation = false,
            isRefusal = false,
            triageLevel = "ROUTINE_PROTOCOL",
            patientContext = resolvedPatientContext,
            citedSections = citedIds,
            latencyMs = elapsedMs()
        )
    }

    fun askAssistant(query: String, patient: Member? = null): AssistantResult =
        askAssistantV2(query, patient)

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
        ProtocolSection("CHD-1.1", "Preparing ORS", "Mix 1 packet of ORS in 1 litre of clean drinking water (boiled and cooled), in a clean container. Wash hands with soap and water before preparing. Do not boil prepared ORS. Use within 24 hours.", "CHD"),
        ProtocolSection("CHD-1.2", "How much ORS to give", "Up to 2 years: 50 to 100 ml after each loose stool. 2 years or more: 100 to 200 ml. If child vomits, wait 10 minutes then continue slowly.", "CHD"),
        ProtocolSection("CHD-1.3", "Zinc in diarrhoea", "Give zinc for 14 days even if diarrhoea stops. Under 6 months: 10 mg daily. 6 months or older: 20 mg daily.", "CHD"),
        ProtocolSection("CHD-3.1", "Counting breaths for pneumonia", "Fast breathing: Under 2 months: 60 breaths/min or more. 2 to 12 months: 50 breaths/min or more. 12 months to 5 years: 40 breaths/min or more.", "CHD"),
        ProtocolSection("HBNC-2.2", "Keeping baby warm and bathing", "Do not bathe the baby for the first 48 hours. Do not bathe a low birth weight baby until its weight is 2000 g.", "HBNC"),
        ProtocolSection("IMM-2.2", "At 6, 10 and 14 weeks", "6 weeks: OPV-1, Penta-1, RVV-1, fIPV-1, PCV-1. 10 weeks: OPV-2, Penta-2, RVV-2. 14 weeks: OPV-3, Penta-3, RVV-3, fIPV-2, PCV-2. Penta can be given till one year of age.", "IMM"),
        ProtocolSection("IMM-2.3", "At 9–12 months", "At 9–12 months: MR-1 (measles rubella 0.5 ml SC), PCV booster, JE-1 (if endemic), Vitamin A 1st dose (100,000 IU = 1 ml).", "IMM"),
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
