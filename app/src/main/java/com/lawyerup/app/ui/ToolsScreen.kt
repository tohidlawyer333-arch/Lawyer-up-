package com.lawyerup.app.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lawyerup.app.*
import com.lawyerup.app.data.BackupCipher
import com.lawyerup.app.documents.Documents
import com.lawyerup.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun ToolsScreen(model:WorkspaceModel,navigate:(String)->Unit,receive:()->Unit) {
    val context=LocalContext.current
    val documents=remember {Documents(context)}
    var tool by rememberSaveable {mutableStateOf("")}
    var importedText by remember {mutableStateOf("")}
    var documentStatus by remember {mutableStateOf("")}
    var kind by remember {mutableStateOf(RecordKind.NOTICE)}
    var password by remember {mutableStateOf("")}
    var pendingBytes by remember {mutableStateOf<ByteArray?>(null)}
    var exportText by remember {mutableStateOf("")}
    val busy by model.busy.collectAsState()
    val prefs=remember {context.getSharedPreferences("preferences",0)}
    var syncUrl by rememberSaveable {mutableStateOf(prefs.getString("sync_server_url","") ?: "")}
    var syncUser by rememberSaveable {mutableStateOf(prefs.getString("sync_user","") ?: "")}
    var syncToken by rememberSaveable {mutableStateOf("")}
    var syncPhrase by rememberSaveable {mutableStateOf("")}
    var syncStatus by remember {mutableStateOf("")}
    var reminders by remember {mutableStateOf(prefs.getBoolean("reminders",false))}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        if(!granted) model.message.value="اعلان‌ها مجاز نیستند؛ پیگیری‌ها در برنامه باقی می‌مانند"
    }
    val importDocument=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) model.perform {
        val result=documents.read(uri) {page,total->documentStatus="OCR: ${fa(page)} از ${fa(total)} صفحه"}
        importedText=result.text
        documentStatus="${fa(result.pages)} صفحه؛ ${if(result.usedOcr)"OCR؛ شناسه و تاریخ‌ها را حتماً بررسی کنید" else "متن PDF؛ پیش از ذخیره بازبینی کنید"}"
    }}
    val savePdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {uri->
        if(uri!=null && pendingBytes!=null) model.perform {context.contentResolver.openOutputStream(uri)!!.use {it.write(pendingBytes!!)};pendingBytes=null;model.message.value="PDF ذخیره شد؛ خوانایی را پیش از بارگذاری بررسی کنید"}
    }
    var limit by rememberSaveable {mutableStateOf("2000000")}
    val optimize=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) model.perform {
        val bytes=documents.optimize(uri,normalize(limit).toInt())
        pendingBytes=bytes
        withContext(Dispatchers.Main) {savePdf.launch("Payesh-attachment.pdf")}
    }}
    val saveBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->
        if(uri!=null) {
            val phrase=password.toCharArray();password=""
            model.perform {try {val encrypted=BackupCipher.encrypt(model.repository.export(),phrase);context.contentResolver.openOutputStream(uri)!!.use {it.write(encrypted)};model.message.value="پشتیبان رمزگذاری‌شده ذخیره شد"} finally {phrase.fill('\u0000')}}
        }
    }
    val restoreBackup=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null) {
            val phrase=password.toCharArray();password=""
            model.perform {
                try {
                    val bytes=context.contentResolver.openInputStream(uri)!!.use {stream->
                        val output=java.io.ByteArrayOutputStream()
                        val buffer=ByteArray(8192)
                        while(true) {val count=stream.read(buffer);if(count<0)break;require(output.size()+count<=25_000_000);output.write(buffer,0,count)}
                        output.toByteArray()
                    }
                    require(bytes.size<=25_000_000) {"فایل پشتیبان بزرگ‌تر از حد مجاز است"}
                    model.repository.restore(BackupCipher.decrypt(bytes,phrase))
                    model.message.value="پشتیبان با داده فعلی ادغام شد؛ رکوردهای موجود حذف نشدند"
                } finally {phrase.fill('\u0000')}
            }
        }
    }
    val saveText=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {uri->if(uri!=null) model.perform {
        context.contentResolver.openOutputStream(uri)!!.bufferedWriter(Charsets.UTF_8).use {it.write(exportText)}
        model.message.value="متن در محل انتخابی ذخیره شد؛ ارسال قضایی انجام نشده است"
    }}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        SectionTitle("ابزارهای دفتر","کارهای تکراری کمتر؛ کنترل نهایی با شما")
        if(tool.isNotEmpty()) TextButton(onClick={tool=""}){Text("بازگشت به ابزارها")}
        when(tool) {
            "document" -> {
                Text("ورود سند و OCR فارسی",style=MaterialTheme.typography.titleLarge)
                Text("فایل فقط روی دستگاه پردازش می‌شود. نتیجه OCR ممکن است اشتباه داشته باشد؛ شماره‌ها و تاریخ‌ها را پیش از ثبت اصلاح کنید.")
                Button(enabled=!busy,onClick={importDocument.launch(arrayOf("application/pdf","image/*"))}){Text("انتخاب PDF یا تصویر")}
                Text(documentStatus)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(kind==RecordKind.NOTICE,{kind=RecordKind.NOTICE},label={Text("ابلاغیه")});FilterChip(kind==RecordKind.CASE,{kind=RecordKind.CASE},label={Text("اطلاع‌رسانی پرونده")})}
                OutlinedTextField(importedText,{importedText=it},label={Text("متن بازبینی‌شده؛ شماره پرونده/ابلاغیه با عنوان صریح")},modifier=Modifier.fillMaxWidth().heightIn(min=260.dp))
                Button(enabled=!busy&&importedText.isNotBlank(),onClick={
                    val record=Extraction.fromDocument(importedText,kind)
                    if(record==null)model.message.value="شناسه یکتا با عنوان شماره پرونده/ابلاغیه پیدا نشد؛ متن را اصلاح کنید"
                    else model.perform {model.repository.importRecords(listOf(record));model.message.value="سند بازبینی‌شده ثبت شد";importedText=""}
                }){Text("متن و شناسه‌ها را تأیید و ثبت می‌کنم")}
            }
            "convert" -> {
                Text("آماده‌سازی پیوست",style=MaterialTheme.typography.titleLarge)
                Text("تصویر به PDF فشرده تبدیل می‌شود. اصل فایل تغییر نمی‌کند. PDF موجود با حفظ ساختار عبور می‌کند؛ فایل امضاشده خودکار دستکاری نمی‌شود.")
                OutlinedTextField(limit,{limit=it},label={Text("حداکثر حجم خروجی؛ بایت")},modifier=Modifier.fillMaxWidth())
                Button(enabled=!busy,onClick={optimize.launch(arrayOf("image/*","application/pdf"))}){Text("انتخاب سند و ذخیره PDF")}
            }
            "calendar" -> CalendarTool {task->model.save(task)}
            "powers" -> ContractTool {text->exportText=text;saveText.launch("LawyerUp-contract-checklist.txt")}
            "sync" -> {
                Text("همگام‌سازی رمزگذاری‌شده بین دستگاه‌ها",style=MaterialTheme.typography.titleLarge)
                Text("سرویس باید قبلاً روی میزبان امن با HTTPS راه‌اندازی شده باشد. محتوای دفتر قبل از ارسال با رمزگذاری سمت دستگاه محافظت می‌شود؛ عبارت رمزگذاری روی سرور ارسال نمی‌شود.")
                OutlinedTextField(syncUrl,{syncUrl=it},label={Text("نشانی سرویس HTTPS")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(syncUser,{syncUser=it},label={Text("شناسه کاربری تعریف‌شده روی سرویس")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(syncToken,{syncToken=it},label={Text("توکن دسترسی (ذخیره نمی‌شود)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(syncPhrase,{syncPhrase=it},label={Text("عبارت رمزگذاری مشترک با ویندوز")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true)
                Button(enabled=!busy&&syncUrl.isNotBlank()&&syncUser.isNotBlank()&&syncToken.length>=24&&syncPhrase.length>=12,onClick={
                    val token=syncToken.trim()
                    val phrase=syncPhrase.toCharArray()
                    prefs.edit().putString("sync_server_url",syncUrl.trim()).putString("sync_user",syncUser.trim()).apply()
                    syncStatus="در حال همگام‌سازی…"
                    model.perform {
                        try {
                            syncStatus=PayeshSyncClient.sync(context,model.repository,syncUrl.trim(),syncUser.trim(),token,phrase)
                            model.message.value=syncStatus
                        } catch(error:Exception) {
                            syncStatus=error.message?.take(180) ?: "همگام‌سازی ناموفق بود"
                            model.message.value=syncStatus
                        } finally {
                            phrase.fill('\\u0000')
                        }
                    }
                }) { Text("همگام‌سازی امن") }
                Text(syncStatus)
                Text("توکن و عبارت رمزگذاری در تنظیمات ذخیره نمی‌شوند. نسخه‌های متعارض به‌صورت خودکار بازنویسی نمی‌شوند؛ پیش از استفاده عملیاتی، سرویس باید ممیزی و آزمون بازیابی شود.",style=MaterialTheme.typography.bodySmall)
            }
            "backup" -> {
                Text("پشتیبان رمزگذاری‌شده",style=MaterialTheme.typography.titleLarge)
                Text("عبارت حداقل ۱۲ نویسه؛ فراموشی آن قابل بازیابی نیست. پشتیبان شامل پرونده، ابلاغ، پیگیری و پیش‌نویس است؛ رمز ثنا و نشست در آن نیست.")
                OutlinedTextField(password,{password=it},label={Text("عبارت محرمانه پشتیبان")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                Button(enabled=password.length>=12&&!busy,onClick={saveBackup.launch("Payesh-backup.lup")}){Text("انتخاب محل و ذخیره پشتیبان")}
                OutlinedButton(enabled=password.length>=12&&!busy,onClick={restoreBackup.launch(arrayOf("*/*"))}){Text("انتخاب پشتیبان و ادغام با دفتر")}
            }
            else -> {
                ToolButton("دریافت از ثنا","نشست رسمی؛ بدون ارسال خودکار",receive)
                ToolButton("ورود PDF و تصویر","OCR فارسی آفلاین و بازبینی متن"){tool="document"}
                ToolButton("تقویم و فرصت‌های پیگیری","موعد روزشمار، جلسه اول و ارزیابی شش‌ماهه"){tool="calendar"}
                ToolButton("مرجع دعاوی پایش","داده مرجع پیوست؛ نیازمند کنترل قانون جاری"){navigate("library")}
                ToolButton("تطبیق متن با مرجع","پیشنهاد کلیدواژه‌ای؛ نه نظر قطعی حقوقی"){navigate("matcher")}
                ToolButton("پشتیبان و بازیابی","خروجی رمزگذاری‌شده؛ بدون سرویس ابری"){tool="backup"}
                ToolButton("همگام‌سازی امن موبایل و ویندوز","رمزگذاری سمت دستگاه؛ نیازمند سرویس میزبانی‌شده"){tool="sync"}
                Row {Switch(reminders,{enabled->reminders=enabled;prefs.edit().putBoolean("reminders",enabled).apply();ReminderWorker.schedule(context,enabled);if(enabled&&Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)});Text("یادآور دوره‌ای دفتر (تقریباً هر ۱۲ ساعت)",Modifier.padding(8.dp))}
                Text("یادآور پس‌زمینه، داده ذخیره‌شده را بررسی می‌کند؛ بدون نشست معتبر، ورود ثنا یا دریافت جدید را جعل نمی‌کند.",style=MaterialTheme.typography.bodySmall)
                Text("نسخه ۲.۰.۰ • داده‌ها محلی و رمزگذاری‌شده • هیچ ارسال قضایی خودکار انجام نمی‌شود",style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ToolButton(title:String,subtitle:String,onClick:()->Unit) {OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(18.dp)) {Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.titleSmall);Text(subtitle,style=MaterialTheme.typography.bodySmall)};Icon(Icons.Outlined.ChevronLeft,null)}}

@Composable
private fun CalendarTool(save:(WorkItem)->Unit) {
    var mode by rememberSaveable {mutableStateOf("روزشمار")}
    var title by rememberSaveable {mutableStateOf("")}
    var date by rememberSaveable {mutableStateOf("")}
    var days by rememberSaveable {mutableStateOf("")}
    var holidays by rememberSaveable {mutableStateOf("")}
    var actionDay by rememberSaveable {mutableStateOf(false)}
    var confirmed by rememberSaveable {mutableStateOf(false)}
    var output by remember {mutableStateOf("")}
    var calculated by remember {mutableStateOf<LocalDate?>(null)}
    Text("تقویم و فرصت‌های مشروط",style=MaterialTheme.typography.titleLarge)
    Text("مدت‌ها از توضیحات ضمیمه به‌عنوان قانون قطعی وارد نشده‌اند. مدت و تاریخ ابلاغ را از متن معتبر پرونده تعیین کنید؛ تعطیلات کامل و شرایط خاص را خودتان تأیید کنید.")
    listOf("روزشمار","ارزیابی شش‌ماهه","جلسه اول / فرصت مشروط").forEach {label->FilterChip(mode==label,{mode=label;calculated=null;confirmed=false},label={Text(label)})}
    OutlinedTextField(title,{title=it},label={Text("عنوان و مستند / شرط فرصت")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(date,{date=it;calculated=null;confirmed=false},label={Text("تاریخ مبنا شمسی؛ ۱۴۰۵/۰۷/۲۳")},modifier=Modifier.fillMaxWidth())
    if(mode=="روزشمار") {
        OutlinedTextField(days,{days=it;calculated=null},label={Text("مدت معتبر؛ روز")},modifier=Modifier.fillMaxWidth())
        Row {Checkbox(actionDay,{actionDay=it;calculated=null});Text("روز اقدام هم جزو مدت حساب نشود",Modifier.padding(top=12.dp))}
        OutlinedTextField(holidays,{holidays=it;calculated=null;confirmed=false},label={Text("تعطیلات شمسی؛ هر خط یک تاریخ")},modifier=Modifier.fillMaxWidth())
    }
    Button(onClick={
        runCatching {
            val start=PersianDates.parse(date)
            when(mode) {
                "روزشمار" -> DeadlineEngine.calculate(start,normalize(days).toInt(),actionDay,holidays.lines().filter {it.isNotBlank()}.map(PersianDates::parse).toSet(),false).also {calculated=it.tentative;output=it.explanation}
                "ارزیابی شش‌ماهه" -> {calculated=PersianDates.months(start,6);output="شش ماه تقویمی شمسی؛ انطباق با نوع ارزیابی، مرحله پرونده و امکان درخواست باید بررسی شود. برای هشدار زودتر، موعد پیگیری جداگانه تعیین کنید."}
                else -> {calculated=start;output="یادآور تاریخ رویداد؛ تحقق شرط، پایان جلسه و وضعیت قطعیت باید توسط وکیل بررسی شود."}
            }
        }.onFailure {calculated=null;output="تاریخ، مدت یا فهرست تعطیلات معتبر نیست"}
    }){Text("محاسبه پیشنهادی")}
    Text(output)
    calculated?.let {result->
        Badge("تاریخ پیشنهادی: ${fa(PersianDates.display(result))}")
        Row {Checkbox(confirmed,{confirmed=it});Text("مبنا، شرایط و تعطیلات را تأیید می‌کنم",Modifier.padding(top=12.dp))}
        Button(enabled=confirmed&&title.isNotBlank(),onClick={save(WorkItem(identity("manual:${System.currentTimeMillis()}"),"",title,"$output\nمبنا: $date؛ نوع: $mode",result.toString(),true));output="پیگیری ذخیره شد"}){Text("ثبت موعد تأییدشده")}
    }
}

@Composable
private fun ContractTool(export:(String)->Unit) {
    var selected by rememberSaveable {mutableStateOf(listOf<String>())}
    var amount by rememberSaveable {mutableStateOf("")}
    var tax by rememberSaveable {mutableStateOf("")}
    var fund by rememberSaveable {mutableStateOf("")}
    var bar by rememberSaveable {mutableStateOf("")}
    var output by remember {mutableStateOf("")}
    Text("چک‌لیست قرارداد و نرخ‌ها",style=MaterialTheme.typography.titleLarge)
    Text("اختیارات را متناسب با رضایت موکل انتخاب کنید. این چک‌لیست هیچ اختیار یا قرارداد جدیدی را در سامانه ثبت نمی‌کند.")
    Row {TextButton(onClick={selected=FeeCalculator.powers}){Text("انتخاب همه برای بازبینی")};TextButton(onClick={selected=emptyList()}){Text("پاک‌کردن انتخاب")}}
    FeeCalculator.powers.forEach {power->Row {Checkbox(power in selected,{checked->selected=if(checked)selected+power else selected-power});Text(power,Modifier.padding(top=12.dp))}}
    OutlinedTextField(amount,{amount=it},label={Text("مبنای حق‌الوکاله؛ ریال")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(tax,{tax=it},label={Text("نرخ تمبر تأییدشده؛ درصد")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(fund,{fund=it},label={Text("نرخ صندوق تأییدشده؛ درصد")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(bar,{bar=it},label={Text("نرخ کانون/مرکز تأییدشده؛ درصد")},modifier=Modifier.fillMaxWidth())
    Button(onClick={output=runCatching {
        val base=BigDecimal(normalize(amount))
        "تمبر: ${FeeCalculator.percent(base,BigDecimal(normalize(tax)))} ریال\nصندوق: ${FeeCalculator.percent(base,BigDecimal(normalize(fund)))} ریال\nکانون/مرکز: ${FeeCalculator.percent(base,BigDecimal(normalize(bar)))} ریال"
    }.getOrElse {"مبلغ و نرخ‌ها باید عدد نامنفی و نرخ حداکثر ۱۰۰ باشد"}}){Text("محاسبه با نرخ‌های واردشده")}
    Text(output)
    OutlinedButton(onClick={export("چک‌لیست اختیارات؛ برای تأیید موکل و وکیل\n${selected.joinToString("\n")}\n$output\nهیچ قرارداد یا پرداختی ثبت نشده است.")}){Text("ذخیره چک‌لیست برای بازبینی")}
}
