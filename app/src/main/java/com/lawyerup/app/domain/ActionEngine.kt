package com.lawyerup.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

data class DeadlineCalculation(val tentative: LocalDate, val shiftedDays: Int, val verified: Boolean, val explanation: String)

object DeadlineEngine {
    fun calculate(start: LocalDate, days: Int, excludeActionDay: Boolean, holidays: Set<LocalDate>,
        calendarVerified: Boolean, weekends: Set<DayOfWeek> = setOf(DayOfWeek.FRIDAY)): DeadlineCalculation {
        require(days in 1..3650)
        var result = start.plusDays(days.toLong() + if(excludeActionDay) 1 else 0)
        var shifted = 0
        while(result.dayOfWeek in weekends || result in holidays) {
            result = result.plusDays(1)
            shifted++
            require(shifted <= 366) { "تقویم تعطیلات معتبر نیست" }
        }
        return DeadlineCalculation(result, shifted, calendarVerified,
            "روز مبنا حساب نشده؛ روز اقدام ${if(excludeActionDay) "حساب نشده" else "در مدت حساب شده"}؛ $shifted روز جابه‌جایی پایان. ${if(calendarVerified) "تقویم با تأیید شما" else "تقویم تأیید نشده؛ نتیجه قطعی نیست"}")
    }
}

object ActionEngine {
    private val opportunities = linkedMapOf(
        "نظریه" to "بررسی نظریه و امکان اعتراض کارشناسی",
        "دستمزد" to "بررسی پرداخت دستمزد و ثبت رسید",
        "رفع نقص" to "بررسی اخطار و تکمیل نواقص",
        "دادنامه" to "بررسی قابلیت و مهلت اعتراض به رأی",
        "وقت رسیدگی" to "آماده‌سازی جلسه؛ بررسی جلب ثالث، تقابل و ایرادات",
        "حضور در جلسه" to "آماده‌سازی جلسه و فرصت‌های قبل از جلسه اول",
        "ارزیابی" to "بررسی اعتبار ارزیابی و ضرورت ارزیابی مجدد",
        "اجراییه" to "بررسی اجرای حکم و پیگیری عملیات اجرایی",
        "تمبر" to "بررسی تمبر و سهم‌های مربوط به مرحله رسیدگی",
        "استعلام" to "پیگیری پاسخ استعلام",
        "تبادل لوایح" to "آماده‌سازی پاسخ و کنترل مهلت تبادل لوایح"
    )
    fun suggest(record: CourtRecord): List<WorkItem> {
        val text = normalize(record.text)
        val suggestions = opportunities.filterKeys { text.contains(it) }
        val titles = if(suggestions.isEmpty() && record.kind == RecordKind.NOTICE) mapOf("review" to "بررسی ابلاغیه و تعیین اقدام لازم") else suggestions
        return titles.map { (key, title) -> WorkItem(identity("task:${record.id}:$key"), record.id, title,
            "پیشنهاد بر اساس متن دریافت‌شده؛ تاریخ ابلاغ، مرحله دادرسی، صلاحیت، وضعیت موکل و قانون جاری باید بررسی شود. هیچ مهلت یا انجام اقدامی از روی عنوان فرض نشده است.") }
    }
    fun draft(task: WorkItem, record: CourtRecord?): String = buildString {
        appendLine("پیش‌نویس برای بازبینی وکیل — ارسال نشده")
        appendLine("ریاست محترم ${record?.fields?.entries?.firstOrNull { it.key.contains("شعبه") || it.key.contains("صادر کننده") }?.value ?: "[مرجع رسیدگی]"}")
        appendLine("پرونده: ${record?.caseNumber?.ifBlank { "[شماره پرونده]" } ?: "[شماره پرونده]"}")
        appendLine("ردیف فرعی: ${record?.subNumber?.ifBlank { "[ردیف فرعی]" } ?: "[ردیف فرعی]"}")
        appendLine("ابلاغیه: ${record?.noticeNumber ?: "[شماره ابلاغیه]"}")
        appendLine("موضوع: ${task.title}")
        appendLine("با سلام و احترام، به وکالت از [نام و سمت موکل]، در خصوص موضوع فوق به استحضار می‌رساند:")
        val body = when {
            task.title.contains("نظریه") -> "در خصوص نظریه کارشناسی مورخ [تاریخ]، موارد نیازمند بررسی عبارت‌اند از: [ایرادات مشخص، روش ارزیابی، مستندات و اثر هر ایراد]. تقاضای رسیدگی به اعتراض و اتخاذ تصمیم مقتضی درباره تکمیل یا ارجاع کارشناسی، با لحاظ شرایط پرونده، مطرح می‌شود."
            task.title.contains("دستمزد") -> "مدارک مربوط به تودیع دستمزد کارشناسی: [مبلغ، تاریخ پرداخت، شناسه واریز و شماره رسید]. پس از احراز پرداخت، تقاضای ثبت وصول وجه و ادامه فرایند کارشناسی تکمیل شود."
            task.title.contains("رفع نقص") || task.title.contains("نواقص") -> "در پاسخ به اخطار رفع نقص، هر بند اخطار و نحوه رفع آن به ترتیب درج می‌شود: [بند اول و سند پیوست]، [بند دوم و سند پیوست]. تقاضای بررسی تکمیل نواقص و ادامه رسیدگی ارائه می‌شود."
            task.title.contains("ارزیابی") -> "در خصوص ارزیابی مورخ [تاریخ] و با بیان [دلایل تغییر ارزش یا جهات قانونی تجدید ارزیابی]، تقاضای بررسی ضرورت ارزیابی مجدد مطرح است. انطباق مدت اعتبار، موضوع ارزیابی و مرحله رسیدگی باید پیش از ثبت کنترل شود."
            task.title.contains("اعتراض به رأی") -> "رأی مورد اعتراض: [شماره، تاریخ، مرجع]. جهات اعتراض: [بندهای مستدل و مستند]. قابلیت اعتراض، سمت، مهلت و مرجع صالح پیش از تنظیم خواسته نهایی کنترل شود."
            else -> "شرح درخواست و ترتیب اقدامات پیشنهادی: [جزئیات مستند متناسب با مرحله پرونده تکمیل شود]."
        }
        appendLine(body)
        appendLine("[شرح مستند وقایع، تاریخ‌ها و جهات درخواست با بررسی پرونده تکمیل شود]")
        appendLine("[مستندات قانونی جاری و قابل انطباق پس از بررسی وکیل درج شود]")
        appendLine("درخواست: [خواسته دقیق و متناسب با وضعیت پرونده]")
        appendLine("پیوست‌ها: [فهرست اسناد/رسید واریز/نظریه مرتبط]")
        appendLine("با احترام — [نام وکیل، تاریخ و امضا]")
    }
    fun explain(text: String): String = when {
        normalize(text).contains("وقت نظارت") -> "وقت نظارت به‌تنهایی نوع دستور یا انجام اقدام را مشخص نمی‌کند؛ متن دستور و گردش مرتبط را بررسی کنید."
        normalize(text).contains("مکاتبه") -> "مکاتبه ثبت شده است؛ صدور مکاتبه را با وصول پاسخ اشتباه نگیرید. برای پیگیری یادآور تعیین کنید."
        else -> "از این عبارت به‌تنهایی نمی‌توان نتیجه قطعی حقوقی گرفت؛ متن اصلی و تاریخ و مرجع آن را بررسی کنید."
    }
}

object FeeCalculator {
    fun percent(amount: BigDecimal, rate: BigDecimal): BigDecimal {
        require(amount >= BigDecimal.ZERO && rate >= BigDecimal.ZERO && rate <= BigDecimal("100"))
        return amount.multiply(rate).divide(BigDecimal("100"), 0, RoundingMode.HALF_UP)
    }
    val powers = listOf("تجدیدنظرخواهی", "واخواهی", "فرجام‌خواهی", "اعاده دادرسی", "ادعای جعل", "انکار و تردید", "ارجاع به داوری", "صلح و سازش", "توکیل", "دعوای تقابل", "جلب ثالث", "ورود ثالث", "دریافت محکوم‌به", "درخواست اجراییه")
}
