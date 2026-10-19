package com.lawyerup.app.data

/** Heuristic explanations only; it never asserts what happened in a court file. */
object CmsPhraseInterpreter {
    data class Explanation(val phrase: String, val possibleMeaning: String, val suggestedCheck: String)
    private val rules = listOf(
        Triple("وقت نظارت", "ثبت یک پیگیری یا انتظار برای وصول پاسخ ممکن است", "آخرین گردش، ابلاغیه و دفتر شعبه بررسی شود"),
        Triple("مکاتبه مکانیزه", "مکاتبه‌ای با یک مرجع ممکن است صادر شده باشد", "مرجع، تاریخ و پاسخ مکاتبه دستی کنترل شود"),
        Triple("نظریه کارشناسی", "پرونده ممکن است به نظر کارشناس مرتبط باشد", "متن نظریه، تاریخ ابلاغ و مهلت اعتراض بررسی شود")
    )
    fun explain(text: String): Explanation? = rules.firstOrNull { text.contains(it.first) }?.let {
        Explanation(it.first, it.second, it.third)
    }
}
