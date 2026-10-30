package org.arogyasathi.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import org.arogyasathi.app.core.ReferralSlip
import org.arogyasathi.app.ui.AppLanguage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-fidelity, vector PDF generator for official National Health Mission (NHM)
 * emergency referral slips. Generates clean on-device referral documentation
 * complete with attending MO and ANM emergency contact numbers, danger sign triage,
 * and immediate interim relief guidance in the active regional language.
 */
object ReferralSlipPdfGenerator {

    /**
     * Immediate interim normal relief guidelines formatted for rural frontline care
     * while emergency transport (108 Ambulance) is en route.
     */
    fun getInterimReliefGuidance(language: AppLanguage): Pair<String, String> {
        return when (language) {
            AppLanguage.TELUGU -> Pair(
                "తక్షణ ఉపశమనం & రవాణా జాగ్రత్తలు (First-Aid / Immediate Relief):",
                "1. రోగిని ప్రశాంతంగా ఉంచండి; గర్భిణీ స్త్రీలను ఎడమవైపునకు ఒరిగించి (Left lateral tilt) పడుకోబెట్టండి.\n" +
                "2. శ్వాస మార్గాన్ని నిరంతరం శుభ్రంగా ఉంచండి; దుస్తులను వదులుగా చేయండి.\n" +
                "3. అపస్మారక స్థితి లేదా ఫిట్స్ ఉంటే ఎటువంటి ఆహారం లేదా ద్రవాలు నోటి ద్వారా ఇవ్వవద్దు.\n" +
                "4. రోగి శరీరాన్ని దుప్పటితో వెచ్చగా ఉంచండి; రవాణా సమయంలో ఒత్తిడి తగ్గించండి.\n" +
                "5. MCP కార్డు మరియు ఈ రిఫరల్ పత్రాన్ని ఆసుపత్రి ఎమర్జెన్సీ డెస్క్ వద్ద వెంటనే అందజేయండి."
            )
            AppLanguage.HINDI -> Pair(
                "तत्काल राहत एवं परिवहन सावधानियां (Immediate Relief Instructions):",
                "1. मरीज को शांत रखें; गर्भवती महिला को बाईं करवट (Left lateral position) लिटाएं।\n" +
                "2. श्वसन मार्ग खुला रखें; तंग कपड़ों को ढीला करें।\n" +
                "3. बेहोशी या दौरे की स्थिति में मुंह से कोई भी तरल या ठोस पदार्थ कदापि न दें।\n" +
                "4. कंबल से शरीर को गर्म रखें और शांत वातावरण बनाए रखें।\n" +
                "5. एमसीपी कार्ड और यह रेफरल पर्ची अस्पताल इमरजेंसी डेस्क पर तुरंत प्रस्तुत करें।"
            )
            AppLanguage.TAMIL -> Pair(
                "அவசர முதலுதவி மற்றும் உடனடி வழிகாட்டுதல் (Immediate Relief):",
                "1. நோயாளியை அமைதியாக வைக்கவும்; கர்ப்பிணிப் பெண்களை இடதுபுறமாக படுக்க வைக்கவும்.\n" +
                "2. சுவாசப் பாதையை சீராக பராமரிக்கவும்; இறுக்கமான ஆடைகளைத் தளர்த்தவும்.\n" +
                "3. மயக்கம் அல்லது வலிப்பு ஏற்பட்டால் வாய்வழியாக திரவமோ உணவோ கொடுக்க வேண்டாம்.\n" +
                "4. நோயாளியை கதகதப்பாக வைக்கவும்.\n" +
                "5. தாய் சேய் பாதுகாப்பு அட்டை மற்றும் இந்த பரிந்துரை சீட்டை மருத்துவமனையில் சமர்ப்பிக்கவும்."
            )
            AppLanguage.KANNADA -> Pair(
                "ತುರ್ತು ಪ್ರಥಮ ಚಿಕಿತ್ಸೆ ಮತ್ತು ಪರಿಹಾರ (Immediate Relief):",
                "1. ರೋಗಿಯನ್ನು ಶಾಂತವಾಗಿರಿಸಿ; ಗರ್ಭಿಣಿ ಮಹಿಳೆಯನ್ನು ಎಡಭಾಗಕ್ಕೆ ತಿರುಗಿಸಿ ಮಲಗಿಸಿ.\n" +
                "2. ಉಸಿರಾಟದ ಮಾರ್ಗವನ್ನು ಮುಕ್ತವಾಗಿರಿಸಿ; ಸಡಿಲವಾದ ಉಡುಪುಗಳನ್ನು ಧರಿಸಿ.\n" +
                "3. ಪ್ರಜ್ಞಾಹೀನ ಸ್ಥಿತಿಯಲ್ಲಿ ಯಾವುದೇ ಆಹಾರ ಅಥವಾ ನೀರನ್ನು ಬಾಯಿಗೆ ಹಾಕಬೇಡಿ.\n" +
                "4. ರೋಗಿಯ ದೇಹವನ್ನು ಬೆಚ್ಚಗಿರಿಸಿ.\n" +
                "5. ಎಂಸಿಪಿ ಕಾರ್ಡ್ ಮತ್ತು ಈ ರೆಫರಲ್ ಪತ್ರವನ್ನು ಆಸ್ಪತ್ರೆಯಲ್ಲಿ ನೀಡಿ."
            )
            AppLanguage.MALAYALAM -> Pair(
                "അടിയന്തര ആശ്വാസ നിർദ്ദേശങ്ങൾ (Immediate Relief):",
                "1. രോഗിയെ ശാന്തമായി കിടത്തുക; ഗർഭിണികളെ ഇടത് വശം ചരിഞ്ഞ് കിടത്തുക.\n" +
                "2. ശ്വാസതടസ്സമില്ലെന്ന് ഉറപ്പുവരുത്തുക; അയഞ്ഞ വസ്ത്രങ്ങൾ നൽകുക.\n" +
                "3. അബോധാവസ്ഥയിലാണെങ്കിൽ യാതൊരു കാരണവശാലും വെള്ളമോ ഭക്ഷണമോ നൽകരുത്.\n" +
                "4. ശരീരം ചൂടോടെ സൂക്ഷിക്കുക.\n" +
                "5. എം.സി.പി കാർഡും ഈ റഫറൽ സ്ലിപ്പും ആശുപത്രിയിൽ ഉടൻ കാണിക്കുക."
            )
            AppLanguage.ENGLISH -> Pair(
                "Immediate Relief & En-Route Care Instructions:",
                "1. Keep patient calm and rested; position on left lateral side (especially in pregnancy).\n" +
                "2. Ensure airway remains clear; loosen restrictive clothing around chest and neck.\n" +
                "3. Do NOT administer fluids or food orally if consciousness or swallowing is impaired.\n" +
                "4. Keep patient comfortable and warm; avoid physical agitation during transit.\n" +
                "5. Present MCP Card and this official referral slip at the hospital emergency triage desk."
            )
        }
    }

    /**
     * Generates a single-page vector A4 PDF document for the given [ReferralSlip].
     */
    fun generatePdf(context: Context, slip: ReferralSlip, language: AppLanguage): File {
        val document = PdfDocument()
        val pageWidth = 595 // Standard A4 width in PostScript points
        val pageHeight = 842 // Standard A4 height in PostScript points
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Header Bar: Deep Teal (National Health Mission standard)
        paint.color = Color.parseColor("#0F766E")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 72f, paint)

        // Header Titles
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        canvas.drawText("NATIONAL HEALTH MISSION • GOVERNMENT OF INDIA", 24f, 26f, paint)

        paint.textSize = 16f
        canvas.drawText("EMERGENCY CLINICAL REFERRAL SLIP", 24f, 48f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = Color.parseColor("#CCFBF1")
        canvas.drawText("Ayushman Arogya Mandir (HWC) • Offline Frontline Triage", 24f, 62f, paint)

        // 2. High-Visibility Emergency Priority Banner
        paint.color = Color.parseColor("#DC2626") // Emergency Red
        canvas.drawRect(0f, 72f, pageWidth.toFloat(), 102f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("CRITICAL DANGER SIGN DETECTED — PRIORITY FACILITY TRANSFER", 24f, 92f, paint)

        val dateStr = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()).format(Date(slip.timestamp))
        val dateWidth = paint.measureText(dateStr)
        canvas.drawText(dateStr, pageWidth - 24f - dateWidth, 92f, paint)

        var yPos = 122f

        // Helper box drawer
        fun drawSectionCard(top: Float, height: Float, title: String, fillColor: Int, strokeColor: Int): Float {
            val rect = RectF(24f, top, pageWidth - 24f, top + height)
            paint.style = Paint.Style.FILL
            paint.color = fillColor
            canvas.drawRoundRect(rect, 8f, 8f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = strokeColor
            canvas.drawRoundRect(rect, 8f, 8f, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 10f
            canvas.drawText(title.uppercase(), 36f, top + 18f, paint)

            return top + 26f
        }

        // 3. Beneficiary & Location Details Card
        val pCardY = drawSectionCard(yPos, 90f, "1. Patient & Household Information", Color.parseColor("#F8FAFC"), Color.parseColor("#CBD5E1"))
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        paint.color = Color.parseColor("#334155")

        canvas.drawText("Patient Name: ${slip.patientName}", 36f, pCardY + 12f, paint)
        canvas.drawText("Age / Sex: ${slip.ageGender}", 300f, pCardY + 12f, paint)

        canvas.drawText("Household ID: ${slip.householdId}", 36f, pCardY + 28f, paint)
        canvas.drawText("Village: ${slip.village}", 300f, pCardY + 28f, paint)

        canvas.drawText("Patient ID: ${slip.patientId}", 36f, pCardY + 44f, paint)
        canvas.drawText("Referral ID: ${slip.id.take(12)}", 300f, pCardY + 44f, paint)

        yPos += 100f

        // 4. Clinical Findings & Danger Signs Card
        val cCardY = drawSectionCard(yPos, 102f, "2. Clinical Findings & Protocol Triage", Color.parseColor("#FEF2F2"), Color.parseColor("#FCA5A5"))

        paint.color = Color.parseColor("#B91C1C")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10.5f
        canvas.drawText("Suspected Condition: ${slip.suspectedCondition}", 36f, cCardY + 14f, paint)

        paint.color = Color.parseColor("#0F766E")
        paint.textSize = 9.5f
        canvas.drawText("Protocol Citation: ${slip.protocolCitation}", 36f, cCardY + 30f, paint)

        paint.color = Color.parseColor("#1E293B")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Observed Vitals / Findings: ${slip.vitalSigns}", 36f, cCardY + 46f, paint)

        val dangerList = if (slip.dangerSigns.isNotEmpty()) slip.dangerSigns.joinToString(", ") else slip.suspectedCondition
        canvas.drawText("Danger Signs Identified: $dangerList", 36f, cCardY + 62f, paint)

        yPos += 112f

        // 5. Emergency Contacts Box (Both MO and ANM numbers prominently highlighted)
        val contactCardY = drawSectionCard(yPos, 128f, "3. Emergency Health Contacts (Immediate Dispatch)", Color.parseColor("#F0FDF4"), Color.parseColor("#86EFAC"))

        // Call MO Box (Left)
        val moBox = RectF(36f, contactCardY + 6f, 280f, contactCardY + 68f)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(moBox, 6f, 6f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#10B981")
        canvas.drawRoundRect(moBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f
        paint.color = Color.parseColor("#065F46")
        canvas.drawText("ATTENDING MEDICAL OFFICER (MO-IC)", 46f, contactCardY + 22f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = Color.parseColor("#1F2937")
        canvas.drawText(slip.medicalOfficerName, 46f, contactCardY + 37f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        paint.color = Color.parseColor("#047857")
        canvas.drawText("📞 ${slip.medicalOfficerPhone}", 46f, contactCardY + 54f, paint)

        // Call ANM Box (Right)
        val anmBox = RectF(295f, contactCardY + 6f, pageWidth - 36f, contactCardY + 68f)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(anmBox, 6f, 6f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#10B981")
        canvas.drawRoundRect(anmBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f
        paint.color = Color.parseColor("#065F46")
        canvas.drawText("SECTOR AUXILIARY NURSE MIDWIFE (ANM)", 305f, contactCardY + 22f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = Color.parseColor("#1F2937")
        canvas.drawText(slip.anmName, 305f, contactCardY + 37f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        paint.color = Color.parseColor("#047857")
        canvas.drawText("📞 ${slip.anmPhone}", 305f, contactCardY + 54f, paint)

        // Facilities & 108 Footer inside contacts
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = Color.parseColor("#374151")
        canvas.drawText("Ambulance Service: 108 (Dispatched) • Receiving Facility: ${slip.referredFacility}", 36f, contactCardY + 84f, paint)

        yPos += 138f

        // 6. Normal Interim Relief Instructions (Localized for User's Active Language)
        val (reliefTitle, reliefContent) = getInterimReliefGuidance(language)
        val reliefCardY = drawSectionCard(yPos, 160f, "4. Interim Relief & Patient Stabilization", Color.parseColor("#EFF6FF"), Color.parseColor("#93C5FD"))

        paint.color = Color.parseColor("#1E40AF")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText(reliefTitle, 36f, reliefCardY + 12f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        paint.color = Color.parseColor("#1E3A8A")

        var lineY = reliefCardY + 28f
        for (line in reliefContent.split("\n")) {
            canvas.drawText(line, 36f, lineY, paint)
            lineY += 15f
        }

        // Disclaimer
        paint.color = Color.parseColor("#DC2626")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8f
        canvas.drawText("⚠️ NOTICE: Temporary comfort & interim stabilization only. Do not delay facility transport.", 36f, lineY + 10f, paint)

        yPos += 170f

        // 7. Attending Personnel & Official Verification Stamp
        val signCardY = drawSectionCard(yPos, 94f, "5. ASHA Field Verification & Triage Desk Sign-Off", Color.parseColor("#F8FAFC"), Color.parseColor("#E2E8F0"))

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = Color.parseColor("#334155")
        canvas.drawText("Attending ASHA Worker: ${slip.ashaName} (${slip.ashaPhone})", 36f, signCardY + 14f, paint)
        canvas.drawText("Primary Action Taken: ${slip.immediateActionTaken}", 36f, signCardY + 30f, paint)

        paint.color = Color.parseColor("#64748B")
        paint.textSize = 8f
        canvas.drawText("ASHA Signature: ___________________________", 36f, signCardY + 54f, paint)
        canvas.drawText("Receiving Medical Officer Stamp: ___________________________", 280f, signCardY + 54f, paint)

        // Bottom Security Stamp
        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 7.5f
        val stamp = "Generated by Arogyasathi Frontline Companion (100% Offline Secured) • ID: ${slip.id}"
        canvas.drawText(stamp, (pageWidth - paint.measureText(stamp)) / 2f, pageHeight - 16f, paint)

        document.finishPage(page)

        // Write to application cache
        val outDir = File(context.cacheDir, "referrals").apply { if (!exists()) mkdirs() }
        val sanitizedId = slip.patientId.replace(Regex("[^a-zA-Z0-9-]"), "_")
        val outFile = File(outDir, "Referral_${sanitizedId}_${System.currentTimeMillis()}.pdf")

        FileOutputStream(outFile).use { fos ->
            document.writeTo(fos)
        }
        document.close()

        return outFile
    }

    /**
     * Open generated PDF in the device's default or chosen PDF viewing application.
     */
    fun openPdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open Emergency Referral Slip"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No PDF viewer app found. You can share or print it.", Toast.LENGTH_LONG).show()
            sharePdf(context, pdfFile)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Share the PDF document via WhatsApp, SMS, or other frontline sharing channels.
     */
    fun sharePdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Arogyasathi Emergency Referral Slip")
                putExtra(Intent.EXTRA_TEXT, "Emergency clinical referral slip generated via Arogyasathi.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Referral Slip PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
