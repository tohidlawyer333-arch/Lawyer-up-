package com.lawyerup.app.ui

import android.icu.util.Calendar
import android.icu.util.ULocale
import java.time.LocalDate
import java.time.ZoneId

object PersianDates {
    private val zone=ZoneId.of("Asia/Tehran")
    private fun calendar()=Calendar.getInstance(android.icu.util.TimeZone.getTimeZone("Asia/Tehran"), ULocale("fa_IR@calendar=persian")).apply { isLenient=false }
    fun display(date:LocalDate):String {
        val value=calendar().apply {timeInMillis=date.atTime(12,0).atZone(zone).toInstant().toEpochMilli()}
        return "%04d/%02d/%02d".format(value.get(Calendar.YEAR),value.get(Calendar.MONTH)+1,value.get(Calendar.DAY_OF_MONTH))
    }
    fun parse(text:String):LocalDate {
        val parts=com.lawyerup.app.domain.normalize(text).split('/').map(String::toInt)
        require(parts.size==3 && parts[0] in 1300..1600)
        val value=calendar().apply {clear();set(parts[0],parts[1]-1,parts[2],12,0,0)}
        return java.time.Instant.ofEpochMilli(value.timeInMillis).atZone(zone).toLocalDate()
    }
    fun months(date:LocalDate,count:Int):LocalDate {
        val value=calendar().apply {timeInMillis=date.atTime(12,0).atZone(zone).toInstant().toEpochMilli();add(Calendar.MONTH,count)}
        return java.time.Instant.ofEpochMilli(value.timeInMillis).atZone(zone).toLocalDate()
    }
}

fun fa(value:Any):String=value.toString().map {if(it in '0'..'9') '۰'+(it-'0') else it}.joinToString("")
