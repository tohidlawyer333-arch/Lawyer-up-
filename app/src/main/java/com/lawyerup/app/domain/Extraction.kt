package com.lawyerup.app.domain

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import kotlinx.coroutines.CancellationException

data class PageRead(val url: String, val rows: List<Map<String, String>>, val paging: String,
    val login: Boolean, val loading: Boolean, val blockedFrames: Int, val truncated: Boolean, val adapter: String,
    val activeView: String = "", val fromDate: String = "", val toDate: String = "")

object Extraction {
    private val number = Regex("(?<![0-9])[0-9]{16,20}(?![0-9])")
    fun read(raw: String): PageRead {
        require(raw.length <= 4_000_000) { "حجم صفحه بیش از حد مجاز است" }
        val decoded = org.json.JSONTokener(raw).nextValue()
        val json = if (decoded is String) JSONObject(decoded) else decoded as JSONObject
        val array = json.optJSONArray("rows") ?: JSONArray()
        val rows = (0 until array.length()).take(1000).map { index ->
            val row = array.getJSONObject(index)
            val headers = row.optJSONArray("headers") ?: JSONArray()
            val cells = row.optJSONArray("cells") ?: JSONArray()
            buildMap {
                for (column in 0 until cells.length()) {
                    val value = normalize(cells.optString(column)).take(20000)
                    val heading = normalize(headers.optString(column)).trimEnd(':', '：').trim()
                    if (heading in listOf("ردیف", "عملیات", "مشاهده") || value == "مشاهده") continue
                    if(heading.isNotBlank()) {
                        val existing = get(heading)
                        put(heading, if(existing != null && existing != value) "$existing | $value" else value)
                    }
                    else {
                        val split = value.indexOf(':')
                        if(split in 1..80) put(value.take(split).trim(), value.drop(split+1).trim())
                    }
                }
            }
        }
        val url = json.optString("url")
        val pagination = json.optJSONObject("pagination")
        val login = json.optBoolean("login") || runCatching { URI(url).host == "iehraz.adliran.ir" }.getOrDefault(false)
        return PageRead(
            url, rows, pagination?.optString("state") ?: "unknown",
            login, json.optBoolean("loading"), json.optInt("blockedFrames"), json.optBoolean("truncated"),
            json.optString("adapter"), json.optString("viewTitle"),
            pagination?.optString("fromDate").orEmpty(), pagination?.optString("toDate").orEmpty()
        )
    }

    fun parse(fields: Map<String, String>, kind: RecordKind, source: String, origin: String = "web"): CourtRecord? {
        fun identifier(label: String) = fields.entries.filter { it.key.contains("شماره") && it.key.contains(label) }
            .flatMap { entry -> number.findAll(normalize(entry.value)).map { it.value }.toList() }.distinct().singleOrNull().orEmpty()
        val caseNumber = identifier("پرونده")
        val noticeNumber = identifier("ابلاغ")
        if (kind == RecordKind.NOTICE && noticeNumber.isEmpty()) return null
        if (kind == RecordKind.CASE && caseNumber.isEmpty()) return null
        val sub = fields.entries.firstOrNull { it.key.contains("ردیف فرعی") }?.value.orEmpty()
        return CourtRecord(kind, caseNumber, noticeNumber, sub, fields, source = safeSource(source), origin = origin)
    }

    fun kind(url: String): RecordKind? {
        if(!officialUrl(url)) return null
        val host = URI(url).host
        return when {
            Regex("eblagh[0-9]*\\.adliran\\.ir").matches(host) -> RecordKind.NOTICE
            host in listOf("resultcase.adliran.ir", "lawyer.adliran.ir") -> RecordKind.CASE
            else -> null
        }
    }

    fun fromDocument(text: String, kind: RecordKind): CourtRecord? {
        val fields = linkedMapOf<String, String>()
        val normalized = normalize(text)
        for((label, expression) in listOf("شماره پرونده" to "شماره\\s*پرونده", "شماره ابلاغیه" to "شماره\\s*ابلاغ(?:یه)?")) {
            val matches = Regex("$expression\\s*[:：]?\\s*([0-9]{16,20})(?![0-9])").findAll(normalized).map { it.groupValues[1] }.distinct().toList()
            if(matches.size == 1) fields[label] = matches.single()
        }
        fields["متن سند؛ نیازمند بازبینی"] = text.take(100000)
        return parse(fields, kind, "local-document", "document-review")
    }
}

data class ImportReport(val accepted: Int, val changed: Int, val rejected: Int)
data class SyncReport(val success: Boolean, val pages: Int, val accepted: Int, val changed: Int, val message: String, val rejected: Int = 0)

class PaginationRunner(private val limit: Int = 100) {
    suspend fun run(
        read: suspend () -> PageRead,
        save: suspend (PageRead) -> ImportReport,
        next: suspend () -> Boolean,
        cancelled: () -> Boolean,
        progress: (Int, Int) -> Unit
    ): SyncReport {
        var pages = 0
        var accepted = 0
        var changed = 0
        var rejected = 0
        val seen = mutableSetOf<String>()
        var source = ""
        var activeView = ""
        var fromDate = ""
        var toDate = ""

        fun scopeSummary(): String {
            val parts = mutableListOf<String>()
            if (activeView.isNotBlank()) parts += "فهرست ثنا: «$activeView»"
            if (fromDate.isNotBlank() || toDate.isNotBlank()) {
                val start = fromDate.ifBlank { "نامشخص" }
                val end = toDate.ifBlank { "نامشخص" }
                parts += "بازه تاریخی: $start تا $end"
            } else {
                parts += "بازه تاریخی: بدون فیلتر تاریخ قابل‌تشخیص"
            }
            return "؛ " + parts.joinToString("؛ ")
        }

        fun stop(reason: String) = SyncReport(
            false, pages, accepted, changed,
            "$reason${scopeSummary()}؛ صفحه: $pages؛ ردیف معتبر: $accepted؛ ردشده: $rejected؛ تغییر: $changed. دریافت کامل تأیید نشده است",
            rejected
        )

        while (pages < limit) {
            if (cancelled()) return stop("دریافت متوقف شد")

            val page = try {
                read()
            } catch (_: CancellationException) {
                return stop("خواندن فهرست متوقف شد")
            } catch (error: Exception) {
                return stop("خواندن صفحه ناموفق بود (${error.javaClass.simpleName})")
            }

            if (page.activeView.isNotBlank()) activeView = page.activeView
            if (page.fromDate.isNotBlank()) fromDate = page.fromDate
            if (page.toDate.isNotBlank()) toDate = page.toDate

            if (cancelled()) return stop("دریافت متوقف شد")
            if (!officialUrl(page.url)) return stop("نشانی غیرمجاز")
            if (page.login) return stop("ورود یا تمدید نشست لازم است")
            if (page.loading) return stop("بارگذاری کامل نشده است")

            val noticeHost = runCatching {
                URI(page.url).host.orEmpty().matches(Regex("eblagh[0-9]*\\.adliran\\.ir"))
            }.getOrDefault(false)
            if (noticeHost) {
                val view = normalize(page.activeView)
                val isRecognizedNoticeList = view.contains("ابلاغیه") &&
                    Regex("مشاهده\\s*(?:شده|نشده)|باز\\s*نشده|جدید").containsMatchIn(view)
                if (!isRecognizedNoticeList) {
                    return stop("عنوان فهرست ابلاغیه‌های ثنا تشخیص داده نشد؛ برای جلوگیری از طبقه‌بندی اشتباه، دریافت انجام نشد")
                }
            }

            if (source.isEmpty()) source = safeSource(page.url)
            if (source != safeSource(page.url)) return stop("مسیر فهرست تغییر کرد")

            val signature = identity(page.rows.toString())
            if (!seen.add(signature)) return stop("صفحه تکراری است")

            val report = try {
                save(page)
            } catch (_: CancellationException) {
                return stop("ذخیره صفحه متوقف شد")
            } catch (error: Exception) {
                return stop("ذخیره ردیف‌های این صفحه ناموفق بود (${error.javaClass.simpleName})")
            }

            // Count the attempted page and its rejected rows even when no valid identity was parsed.
            pages++
            accepted += report.accepted
            changed += report.changed
            rejected += report.rejected
            try {
                progress(pages, accepted)
            } catch (error: Exception) {
                return stop("گزارش پیشرفت ناموفق بود (${error.javaClass.simpleName})")
            }

            if (report.accepted == 0) {
                return stop("هیچ رکورد معتبری خوانده نشد؛ این به معنی خالی بودن کارتابل نیست")
            }
            if (page.blockedFrames > 0 || page.truncated) return stop("بخشی از محتوا قابل خواندن نیست")

            if (page.paging == "filtered" || page.paging == "filtered-end") {
                return stop(
                    if (page.paging == "filtered-end")
                        "پایان نتایج همین بازه تاریخی؛ سوابق خارج از این بازه دریافت نشده‌اند"
                    else
                        "بازه تاریخی فعال است و صفحه‌بندی قابل تشخیص نیست؛ فقط همین بازه بررسی شد"
                )
            }

            if (page.paging == "end") {
                val complete = rejected == 0
                val message = "$pages صفحه؛ $accepted ردیف معتبر؛ $changed تغییر؛ $rejected ردیف ردشده" +
                    "${scopeSummary()}؛ " +
                    if (complete) "پایان فهرست فعلی با فیلترهای سامانه شناسایی شد"
                    else "پایان فهرست فعلی شناسایی شد، اما دریافت به علت ردیف‌های ردشده کامل تأیید نمی‌شود"
                return SyncReport(complete, pages, accepted, changed, message, rejected)
            }

            if (page.paging != "next") return stop("کنترل صفحه‌بندی قابل تشخیص نیست")
            if (pages >= limit) return stop("سقف ایمنی صفحات رسید")

            val moved = try {
                next()
            } catch (_: CancellationException) {
                return stop("رفتن به صفحه بعد متوقف شد")
            } catch (error: Exception) {
                return stop("رفتن به صفحه بعد ناموفق بود (${error.javaClass.simpleName})")
            }

            if (cancelled() || !moved) return stop("صفحه بعد دریافت نشد")
        }

        return stop("سقف ایمنی صفحات رسید")
    }
}
