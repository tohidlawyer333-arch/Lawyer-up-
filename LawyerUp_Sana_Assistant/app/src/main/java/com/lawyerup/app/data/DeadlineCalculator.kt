package com.lawyerup.app.data
import java.time.LocalDate
import java.time.format.DateTimeFormatter
object DeadlineCalculator { private val f=DateTimeFormatter.ISO_LOCAL_DATE
 /** Assumption: day zero is the triggering date; counting starts on the following calendar day. Holidays and court rules are not inferred. */
 fun calculate(trigger:String,days:Int):String { require(days>=0); return LocalDate.parse(trigger,f).plusDays(days.toLong()).format(f) }
}