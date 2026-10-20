package com.lawyerup.app.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun ToolsScreen(model:WorkspaceModel,navigate:(String)->Unit,receive:()->Unit,themeChoice:String="teal",onThemeChange:(String)->Unit={}) {
    val context=LocalContext.current
    val documents=remember {Documents(context)}
    var tool by rememberSaveable {mutableStateOf("")}
    var importedText by remember {mutableStateOf("")}
    var deletedText by remember {mutableStateOf("")}
    var clipboardText by remember {mutableStateOf("")}
    var textBuffersLoaded by remember {mutableStateOf(false)}
    var includeEnglish by rememberSaveable {mutableStateOf(false)}
    var showDeleteClipboardConfirm by remember {mutableStateOf(false)}
    var documentStatus by remember {mutableStateOf("")}
    var kind by remember {mutableStateOf(RecordKind.NOTICE)}
    var password by remember {mutableStateOf("")}
    var pendingBytes by remember {mutableStateOf<ByteArray?>(null)}
    var exportText by remember {mutableStateOf("")}
    val busy by model.busy.collectAsState()
    LaunchedEffect(Unit) {
        val stored = withContext(Dispatchers.IO) {
            listOf(model.repository.readTextBuffer("ocr"), model.repository.readTextBuffer("ocr-undo"), model.repository.readTextBuffer("internal-clipboard"))
        }
        importedText=stored[0]
        deletedText=stored[1]
        clipboardText=stored[2]
        textBuffersLoaded=true
    }
    LaunchedEffect(importedText,textBuffersLoaded) {
        if(textBuffersLoaded) { delay(350); runCatching { withContext(Dispatchers.IO) { model.repository.writeTextBuffer("ocr",importedText) } } }
    }
    LaunchedEffect(deletedText,textBuffersLoaded) {
        if(textBuffersLoaded) { delay(350); runCatching { withContext(Dispatchers.IO) { model.repository.writeTextBuffer("ocr-undo",deletedText) } } }
    }
    LaunchedEffect(clipboardText,textBuffersLoaded) {
        if(textBuffersLoaded) { delay(350); runCatching { withContext(Dispatchers.IO) { model.repository.writeTextBuffer("internal-clipboard",clipboardText) } } }
    }
    DisposableEffect(Unit) {
        onDispose {
            if(textBuffersLoaded) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+Dispatchers.IO).launch {
                    runCatching {
                        model.repository.writeTextBuffer("ocr",importedText)
                        model.repository.writeTextBuffer("ocr-undo",deletedText)
                        model.repository.writeTextBuffer("internal-clipboard",clipboardText)
                    }
                }
            }
        }
    }
    val prefs=remember {context.getSharedPreferences("preferences",0)}
    var reminders by remember {mutableStateOf(prefs.getBoolean("reminders",false))}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        if(!granted) model.message.value="اعلان‌ها مجاز نیستند؛ پیگیری‌ها در برنامه باقی می‌مانند"
    }
    val importDocument=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) model.perform {
        val result=documents.read(uri, {page,total->documentStatus="OCR: ${fa(page)} از ${fa(total)} صفحه"}, includeEnglish)
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
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked=includeEnglish,onCheckedChange={includeEnglish=it})
                    Text("سند شامل واژه‌های انگلیسی است؛ تشخیص دوزبانه",style=MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(kind==RecordKind.NOTICE,{kind=RecordKind.NOTICE},label={Text("ابلاغیه")});FilterChip(kind==RecordKind.CASE,{kind=RecordKind.CASE},label={Text("اطلاع‌رسانی پرونده")})}
                OutlinedTextField(importedText,{importedText=it},label={Text("متن استخراج‌شده و قابل ویرایش")},modifier=Modifier.fillMaxWidth().heightIn(min=260.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={if(importedText.isNotEmpty()) deletedText=importedText;importedText=""},modifier=Modifier.weight(1f)) {Text("پاک‌کردن تمام متن استخراج‌شده")}
                    OutlinedButton(enabled=deletedText.isNotEmpty(),onClick={importedText=deletedText},modifier=Modifier.weight(1f)) {Text("بازگرداندن متن پاک‌شده")}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(enabled=importedText.isNotBlank(),onClick={
                        clipboardText=if(clipboardText.isBlank()) importedText else clipboardText+"\n\n"+importedText
                        model.message.value="متن به کلیپ‌بورد داخلی افزوده شد؛ فقط از تنظیمات می‌توان محتویات آن را حذف کرد"
                    },modifier=Modifier.weight(1f)) {Text("ذخیره متن در کلیپ‌بورد")}
                    OutlinedButton(onClick={tool="clipboard"},modifier=Modifier.weight(1f)) {Text("کلیپ‌بورد")}
                }
                Text("حتی اگر شماره پرونده یا ابلاغیه در سند وجود نداشته باشد، متن به مخزن اسناد برای تحلیل اضافه می‌شود؛ تطبیق با پرونده مشخص فقط با شناسه مستند ممکن است.",style=MaterialTheme.typography.bodySmall)
                Button(enabled=!busy&&importedText.isNotBlank(),onClick={
                    val record=Extraction.fromDocument(importedText,kind)
                    val evidence=EvidenceDocument.create("سند OCR",importedText,"local-ocr")
                    model.perform {
                        if(record!=null) model.repository.importRecords(listOf(record))
                        model.repository.saveEvidence(evidence)
                        model.message.value=if(record==null) "متن سند برای تحلیل ذخیره شد؛ شناسه یکتا از متن احراز نشد" else "متن سند و شناسه مستند ذخیره شد؛ OCR حفظ شد"
                    }
                }){Text("ذخیره سند برای تحلیل پرونده")}
            }
            "clipboard" -> {
                Text("کلیپ‌بورد داخلی",style=MaterialTheme.typography.titleLarge)
                Text("محتوا در حافظه رمزگذاری‌شده دستگاه نگهداری می‌شود و با جابه‌جایی بین بخش‌ها یا بستن برنامه از بین نمی‌رود. برای حذف محتوا فقط از تنظیمات استفاده کنید.")
                OutlinedTextField(value=clipboardText,onValueChange={},readOnly=true,label={Text("متن‌های ذخیره‌شده")},modifier=Modifier.fillMaxWidth().heightIn(min=320.dp))
                OutlinedButton(onClick={tool="document"}) {Text("بازگشت به OCR")}
            }
            "settings" -> {
                Text("تنظیمات، حریم خصوصی و ظاهر",style=MaterialTheme.typography.titleLarge)
                Text("متن OCR، متن قابل‌بازیابی و کلیپ‌بورد در حافظه رمزگذاری‌شده محلی نگهداری می‌شوند. این اطلاعات به سرویس ابری ارسال نمی‌شوند.")
                Text("تم سراسری برنامه؛ رنگ پس‌زمینه، کارت‌ها، دکمه‌ها، نوار پیمایش و اجزای اصلی تغییر می‌کند.",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("teal" to "سبزآبی","navy" to "سرمه‌ای","pink" to "صورتی").forEach {(value,label)->FilterChip(selected=themeChoice==value,onClick={onThemeChange(value)},label={Text(label)})}
                }
                OutlinedButton(onClick={showDeleteClipboardConfirm=true}) {Text("حذف محتویات کلیپ‌بورد داخلی")}
                if(showDeleteClipboardConfirm) AlertDialog(
                    onDismissRequest={showDeleteClipboardConfirm=false},
                    title={Text("حذف کلیپ‌بورد داخلی؟")},
                    text={Text("این کار فقط محتویات کلیپ‌بورد داخلی را حذف می‌کند و متن OCR و متن قابل‌بازیابی باقی می‌مانند.")},
                    confirmButton={Button(onClick={clipboardText="";showDeleteClipboardConfirm=false;model.message.value="محتویات کلیپ‌بورد داخلی حذف شد"}) {Text("حذف محتویات")}},
                    dismissButton={TextButton(onClick={showDeleteClipboardConfirm=false}) {Text("انصراف")}}
                )
            }
            "convert" -> {
                Text("آماده‌سازی پیوست",style=MaterialTheme.typography.titleLarge)
                Text("تصویر به PDF فشرده تبدیل می‌شود. اصل فایل تغییر نمی‌کند. PDF موجود با حفظ ساختار عبور می‌کند؛ فایل امضاشده خودکار دستکاری نمی‌شود.")
                OutlinedTextField(limit,{limit=it},label={Text("حداکثر حجم خروجی؛ بایت")},modifier=Modifier.fillMaxWidth())
                Button(enabled=!busy,onClick={optimize.launch(arrayOf("image/*","application/pdf"))}){Text("انتخاب سند و ذخیره PDF")}
            }
            "calendar" -> CalendarTool {task->model.save(task)}
            "powers" -> ContractTool {text->exportText=text;saveText.launch("LawyerUp-contract-checklist.txt")}
            "backup" -> {
                Text("پشتیبان رمزگذاری‌شده",style=MaterialTheme.typography.titleLarge)
                Text("عبارت حداقل ۱۲ نویسه؛ فراموشی آن قابل بازیابی نیست. پشتیبان شامل پرونده، ابلاغ، پیگیری و پیش‌نویس است؛ رمز ثنا و نشست در آن نیست.")
                OutlinedTextField(password,{password=it},label={Text("عبارت محرمانه پشتیبان")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                Button(enabled=password.length>=12&&!busy,onClick={saveBackup.launch("Payesh-backup.lup")}){Text("انتخاب محل و ذخیره پشتیبان")}
                OutlinedButton(enabled=password.length>=12&&!busy,onClick={restoreBackup.launch(arrayOf("*/*"))}){Text("انتخاب پشتیبان و ادغام با دفتر")}
            }
            else -> {
                ToolButton("دریافت از ثنا","نشست رسمی؛ بدون ارسال خودکار",receive)
                ToolButton("ورود PDF و تصویر","OCR فارسی آفلاین؛ تشخیص انگلیسی اختیاری"){tool="document"}
                ToolButton("کلیپ‌بورد داخلی","ذخیره رمزگذاری‌شده روی دستگاه"){tool="clipboard"}
                ToolButton("تنظیمات، حریم خصوصی و تم","انتخاب تم سراسری و مدیریت کلیپ‌بورد"){tool="settings"}
                ToolButton("تقویم و فرصت‌های پیگیری","موعد روزشمار، جلسه اول و ارزیابی شش‌ماهه"){tool="calendar"}
                ToolButton("قرارداد و محاسبات","چک‌لیست اختیارات و نرخ‌های تأییدشده"){tool="powers"}
                ToolButton("آماده‌سازی ضمائم","تبدیل تصویر به PDF با سقف حجم"){tool="convert"}
                ToolButton("مرجع دعاوی و مستندات","داده مرجع پیوست؛ نیازمند کنترل قانون جاری"){navigate("library")}
                ToolButton("تطبیق متن با مرجع","پیشنهاد کلیدواژه‌ای؛ نه نظر قطعی حقوقی"){navigate("matcher")}
                ToolButton("پشتیبان و بازیابی","خروجی رمزگذاری‌شده؛ بدون سرویس ابری"){tool="backup"}
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
