package com.lawyerup.app.validation
object Validation { fun required(value:String,label:String):String?=if(value.trim().isEmpty()) "$label الزامی است" else null; fun date(value:String):String?=try { java.time.LocalDate.parse(value); null } catch(_:Exception){"تاریخ باید به شکل ۱۴۰۳-۰۱-۰۱ میلادی (YYYY-MM-DD) باشد"} }
