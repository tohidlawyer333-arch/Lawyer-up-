package com.lawyerup.app.data

import com.lawyerup.app.data.local.ClaimEntity

data class MatchResult(val claim: ClaimEntity, val score: Int)

object CaseMatcher {
    private val keywordsByForum = mapOf(
        "حقوقی" to listOf("ملک", "سند", "بیع", "معامله", "اجاره", "اجاره نامه", "فروشنده", "خریدار", "اقاله", "فک رهن", "خیار"),
        "کیفری" to listOf("کلاهبرداری", "شکایت", "کیفری", "دادسرا", "جنایت", "سفته بازی", "اختلاس", "رشوه", "سرقت", "توهین", "تهدید"),
        "کار" to listOf("کارگر", "کارفرما", "حقوق", "مزایا", "سنوات", "عیدی", "اخراج", "مجوز کار", "دستمزد", "بیمه"),
        "مالیاتی" to listOf("مالیات", "مالیات بر درآمد", "هیأت حل اختلاف", "مودی", "بازرسی", "اظهارنامه", "مالیات بر ارزش افزوده"),
        "شهرداری" to listOf("شهرداری", "پروانه", "ساخت", "کمیسیون ماده ۱۰۰", "پارکینگ", "توقف", "جنس", "دیوار"),
        "ثبتی" to listOf("ثبت", "تحدید", "افراز", "سند معارض", "پلاک ثبتی", "املاک", "دفترخانه"),
        "تجاری" to listOf("چک", "سفته", "برات", "تجارت", "ورشکستگی", "وصول", "شرکت", "بورس", "بانک")
    )

    fun match(text: String, allClaims: List<ClaimEntity>): List<MatchResult> {
        val lower = text.lowercase()
        val results = mutableListOf<MatchResult>()
        for (claim in allClaims) {
            var score = 0
            keywordsByForum[claim.forum]?.forEach { kw ->
                if (kw in lower) score += 10
            }
            claim.title.split(" ", "،").forEach { t ->
                if (t.length > 2 && t in lower) score += 5
            }
            if (claim.forum in lower) score += 8
            if (score > 0) results.add(MatchResult(claim, score))
        }
        return results.sortedByDescending { it.score }
    }

    fun draftBrief(text: String, matches: List<MatchResult>): String {
        val sb = StringBuilder()
        sb.appendLine("ریاست محترم دادگاه")
        sb.appendLine("موضوع: تقدیم لایحه")
        sb.appendLine()
        sb.appendLine("شرح واقعه از منظر موکل:")
        sb.appendLine(text)
        sb.appendLine()
        sb.appendLine("با عنایت به شرح فوق و مطالعه پرونده محترم، مستنداً به مواد قانونی زیر تقاضای رسیدگی و صدور حکم موجه را دارد:")
        sb.appendLine()
        matches.take(3).forEachIndexed { i, m ->
            sb.appendLine("${i + 1}. ${m.claim.title} — مستند به: ${m.claim.articles}")
            sb.appendLine("   مأموریت: ${m.claim.authority}")
        }
        sb.appendLine()
        sb.appendLine("با تشکر - تنظیم‌کننده لایحه")
        return sb.toString()
    }
}
