package com.lawyerup.app.domain

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

fun normalize(value: String): String = value.map { character ->
    when (character) {
        in '۰'..'۹' -> '0' + (character - '۰')
        in '٠'..'٩' -> '0' + (character - '٠')
        'ي', 'ى' -> 'ی'
        'ك' -> 'ک'
        '\u200c', '\u200e', '\u200f' -> ' '
        else -> character
    }
}.joinToString("").replace(Regex("\\s+"), " ").trim()

fun identity(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

fun officialUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
        (uri.host == "adliran.ir" || uri.host?.endsWith(".adliran.ir") == true)
}.getOrDefault(false)

fun safeSource(value: String): String = runCatching {
    val uri = URI(value)
    URI(uri.scheme, null, uri.host, -1, uri.path, null, null).toString()
}.getOrDefault("local-document")

enum class RecordKind { CASE, NOTICE }

data class CourtRecord(
    val kind: RecordKind,
    val caseNumber: String = "",
    val noticeNumber: String = "",
    val subNumber: String = "",
    val fields: Map<String, String> = emptyMap(),
    val evidence: List<String> = emptyList(),
    val source: String = "",
    val importedAt: Long = System.currentTimeMillis(),
    val reviewed: Boolean = false,
    val reviewedAt: Long? = null,
    val origin: String = "web"
) {
    val id: String get() = identity(if (kind == RecordKind.NOTICE) "notice:$noticeNumber" else "case:$caseNumber:$subNumber")
    val text: String get() = fields.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    val subject: String get() = fields.entries.firstOrNull { it.key.contains("موضوع") }?.value ?: if (kind == RecordKind.CASE) "اطلاع‌رسانی پرونده" else "ابلاغیه"
    fun json(): JSONObject = JSONObject().put("kind", kind.name).put("case", caseNumber)
        .put("notice", noticeNumber).put("sub", subNumber).put("fields", JSONObject(fields))
        .put("evidence", JSONArray(evidence)).put("source", source).put("at", importedAt)
        .put("reviewed", reviewed).put("reviewedAt", reviewedAt).put("origin", origin)
    companion object {
        fun from(json: JSONObject): CourtRecord {
            val fields = json.getJSONObject("fields")
            val evidence = json.optJSONArray("evidence") ?: JSONArray()
            return CourtRecord(RecordKind.valueOf(json.getString("kind")), json.optString("case"),
                json.optString("notice"), json.optString("sub"), fields.keys().asSequence().associateWith { fields.getString(it) },
                (0 until evidence.length()).map(evidence::getString), json.optString("source"), json.getLong("at"),
                json.optBoolean("reviewed"), if(json.isNull("reviewedAt")) null else json.getLong("reviewedAt"), json.optString("origin", "web"))
        }
    }
}

data class WorkItem(
    val id: String,
    val recordId: String,
    val title: String,
    val rationale: String,
    val due: String = "",
    val dateConfirmed: Boolean = false,
    val done: Boolean = false,
    val draft: String = "",
    val receipt: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("record", recordId).put("title", title)
        .put("rationale", rationale).put("due", due).put("confirmed", dateConfirmed).put("done", done)
        .put("draft", draft).put("receipt", receipt).put("at", updatedAt)
    companion object {
        fun from(json: JSONObject) = WorkItem(json.getString("id"), json.getString("record"), json.getString("title"),
            json.getString("rationale"), json.optString("due"), json.optBoolean("confirmed"), json.optBoolean("done"),
            json.optString("draft"), json.optString("receipt"), json.optLong("at"))
    }
}

data class EvidenceDocument(
    val id: String,
    val title: String,
    val text: String,
    val source: String = "local-document",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun json(): JSONObject = JSONObject().put("id",id).put("title",title).put("text",text).put("source",source).put("createdAt",createdAt)
    companion object {
        fun create(title:String,text:String,source:String="local-document"):EvidenceDocument {
            val at=System.currentTimeMillis()
            return EvidenceDocument(identity("evidence:$at:$title:$text"),title.ifBlank { "سند بارگذاری‌شده" },text,source,at)
        }
        fun from(json:JSONObject) = EvidenceDocument(json.getString("id"),json.optString("title","سند بارگذاری‌شده"),json.optString("text"),json.optString("source","local-document"),json.optLong("createdAt",System.currentTimeMillis()))
    }
}
enum class MatchLevel { MISSING_IDENTITY, MISSING_CASE, CASE_ONLY, EXACT, ACTION_RECORDED, CONFLICT, MANUAL }
data class Reconciliation(val level: MatchLevel, val title: String, val reason: String, val evidence: List<String> = emptyList(), val caseNumber: String = "")

object Reconciler {
    private fun sourceRows(record: CourtRecord): List<String> = record.evidence.ifEmpty { listOf(record.text) }

    fun match(notice: CourtRecord, cases: List<CourtRecord>): Reconciliation {
        if (notice.reviewed) {
            val automatic = match(notice.copy(reviewed = false), cases)
            return Reconciliation(MatchLevel.MANUAL, "بررسی با تأیید شما", "تأیید دستی، نه ثبت اقدام در ثنا. نتیجه تطبیق: ${automatic.title}", automatic.evidence, automatic.caseNumber)
        }
        if (notice.noticeNumber.isBlank()) return Reconciliation(MatchLevel.MISSING_IDENTITY, "شماره ابلاغیه نامشخص", "شناسه معتبر برای تطبیق لازم است")
        val reference = Regex("شماره\\s*ابلاغ(?:یه)?\\s*[:：]?\\s*${Regex.escape(notice.noticeNumber)}(?![0-9])")
        val referenced = cases.filter { record -> sourceRows(record).any { reference.containsMatchIn(normalize(it)) } }
        val referencedNumbers = referenced.map { it.caseNumber }.distinct()
        if (referencedNumbers.size > 1 || (notice.caseNumber.isNotBlank() && referencedNumbers.any { it != notice.caseNumber })) {
            return Reconciliation(MatchLevel.CONFLICT, "تعارض شناسه‌ها", "ارجاع به ابلاغیه در بیش از یک پرونده یا پرونده متفاوت دیده شد")
        }
        val number = notice.caseNumber.ifBlank { referencedNumbers.singleOrNull().orEmpty() }
        if(number.isBlank()) return Reconciliation(MatchLevel.MISSING_IDENTITY, "نیازمند جزئیات یا گردش پرونده", "فهرست ابلاغ ممکن است شماره پرونده نداشته باشد؛ شماره از حدس متن ساخته نمی‌شود")
        val related = cases.filter { it.caseNumber == number }
        if(related.isEmpty()) return Reconciliation(MatchLevel.MISSING_CASE, "اطلاع‌رسانی دریافت نشده", "داده پرونده مرتبط را دریافت کنید؛ نبود داده به معنی انجام‌نشدن اقدام نیست", caseNumber = number)
        val evidence = related.flatMap(::sourceRows).distinct().filter { reference.containsMatchIn(normalize(it)) }
        if(evidence.isEmpty()) return Reconciliation(MatchLevel.CASE_ONLY, "پرونده مرتبط", "شماره پرونده منطبق است، اما ارجاع دقیق این ابلاغیه هنوز پیدا نشده", caseNumber = number)
        val negative = evidence.any { text -> text.lines().any { Regex("^وضعیت اقدام\\s*[:：]\\s*(لغو شده|رد شده|انجام نشده|ابطال شده)$").matches(normalize(it)) } }
        val positive = evidence.any { text ->
            text.lines().any { Regex("^وضعیت اقدام\\s*[:：]\\s*(ثبت نهایی|انجام شده|تکمیل شده)$").matches(normalize(it)) } &&
                text.lines().any { Regex("^(نوع اقدام|شرح اقدام|اقدام)\\s*[:：]\\s*.+").matches(normalize(it)) }
        }
        return when {
            negative -> Reconciliation(MatchLevel.CONFLICT, "وضعیت اقدام نیازمند بررسی", "ارجاع دقیق با وضعیت منفی یا متعارض یافت شد", evidence, number)
            positive -> Reconciliation(MatchLevel.ACTION_RECORDED, "ثبت اقدام مرتبط شناسایی شد", "کفایت حقوقی و زمان اقدام هنوز باید توسط وکیل تأیید شود", evidence, number)
            else -> Reconciliation(MatchLevel.EXACT, "تطبیق دقیق ابلاغ و پرونده", "ارجاع دقیق پیدا شد؛ مشاهده ابلاغیه یا تولید پیش‌نویس اثبات انجام اقدام نیست", evidence, number)
        }
    }
}
