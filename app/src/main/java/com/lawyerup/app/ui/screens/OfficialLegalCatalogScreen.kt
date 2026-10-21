package com.lawyerup.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.Html
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lawyerup.app.LegalUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private data class OfficialLaw(val title:String,val category:String,val id:String) {
    val key:String get() = if(id.isNotBlank()) id else title
    fun url():String = if(id.isBlank()) "https://qavanin.ir/?Report_ID=1" else "https://qavanin.ir/Law/TreeText/?IDS="+id
}
private data class RelatedOfficialDocument(val id:String,val title:String,val kind:String,val url:String)

private fun relatedKind(title:String):String = when {
    title.contains("آیین‌نامه") || title.contains("آیین نامه") -> "آیین‌نامه"
    title.contains("رأی وحدت رویه") || title.contains("رای وحدت رویه") -> "رأی وحدت رویه"
    title.contains("نظریه مشورتی") -> "نظریه مشورتی"
    title.contains("مصوبه") || title.contains("تصویب‌نامه") || title.contains("تصویب نامه") -> "مصوبه"
    title.contains("بخشنامه") || title.contains("بخش‌نامه") -> "بخش‌نامه"
    else -> "سند مرتبط رسمی"
}

private val officialLaws = listOf(
    OfficialLaw("قانون مدنی","حقوقی","12021850837713548188"),
    OfficialLaw("قانون آیین دادرسی دادگاه‌های عمومی و انقلاب در امور مدنی","حقوقی","502404499976366661"),
    OfficialLaw("قانون تجارت","تجاری","12145533825531226090"),
    OfficialLaw("قانون صدور چک","تجاری","11400475274213876131"),
    OfficialLaw("قانون روابط موجر و مستأجر","حقوقی","13448814470140205835"),
    OfficialLaw("قانون الزام به ثبت رسمی معاملات اموال غیرمنقول","ثبتی","8323921457009018779"),
    OfficialLaw("قانون کار","کار و تأمین اجتماعی","3983654531606411392"),
    OfficialLaw("قانون تشکیلات و آیین دادرسی دیوان عدالت اداری","اداری","13656175697806347054"),
    OfficialLaw("قانون اجرای احکام مدنی","اجرای احکام","12257143710724326112"),
    OfficialLaw("قانون دیوان عالی کشور؛ شناسه رسمی نیازمند تطبیق","قضایی",""),
    OfficialLaw("قانون بودجه سال ۱۴۰۵ کل کشور","اقتصادی و عمومی","17235897393895510166"),
    OfficialLaw("قانون برنامه پنج‌ساله هفتم پیشرفت جمهوری اسلامی ایران","اقتصادی و عمومی","15033449572675229448"),
    OfficialLaw("قانون شوراهای حل اختلاف","قضایی","7564968649549447308"),
    OfficialLaw("قانون مالیات بر ارزش افزوده","مالیاتی","17874811672232780652"),
    OfficialLaw("قانون احکام دائمی برنامه‌های توسعه کشور","اقتصادی و عمومی","11124862449016338596"),
    OfficialLaw("قانون بیمه اجباری خسارات واردشده به شخص ثالث","بیمه","4997560313236073514"),
    OfficialLaw("قانون الحاق برخی مواد به قانون تنظیم بخشی از مقررات مالی دولت (۲)","مالی و اداری","6795408435639159550"),
    OfficialLaw("قانون نحوه اجرای محکومیت‌های مالی","اجرای احکام","4687546238878663724"),
    OfficialLaw("قانون آیین دادرسی کیفری","کیفری","2433638803151753757"),
    OfficialLaw("قانون مبارزه با قاچاق کالا و ارز","کیفری و اقتصادی","12813502521337657905"),
    OfficialLaw("قانون حمایت از کودکان و نوجوانان بی‌سرپرست و بدسرپرست؛ شناسه رسمی نیازمند تطبیق","خانواده",""),
    OfficialLaw("قانون مجازات اسلامی","کیفری","4693937366938194803"),
    OfficialLaw("قانون حمایت خانواده","خانواده","5917848193987091276"),
    OfficialLaw("قانون بهبود مستمر محیط کسب‌وکار","اقتصادی و عمومی","1082234023675631014"),
    OfficialLaw("قانون امور گمرکی","اقتصادی و عمومی","7778005441520314589"),
    OfficialLaw("قانون مدیریت خدمات کشوری","اداری","1204996686065044702"),
    OfficialLaw("قانون الحاق موادی به قانون تنظیم بخشی از مقررات مالی دولت (۱)","مالی و اداری","15361588074584818347"),
    OfficialLaw("قانون برگزاری مناقصات","اقتصادی و عمومی","15356697504862195697"),
    OfficialLaw("قانون تنظیم بخشی از مقررات مالی دولت","مالی و اداری","10444344590630024339"),
    OfficialLaw("آیین‌نامه داخلی مجلس شورای اسلامی","عمومی","5626585734073301464"),
    OfficialLaw("قانون وصول برخی از درآمدهای دولت و مصرف آن در موارد معین","مالی و عمومی","5943420824727771948"),
    OfficialLaw("قانون مقررات صادرات و واردات","اقتصادی و عمومی","2981512097583997698"),
    OfficialLaw("آیین‌نامه داخلی هیأت دولت","اداری","7112151676786926217"),
    OfficialLaw("قانون مالیات‌های مستقیم","مالیاتی","892583829653829785"),
    OfficialLaw("قانون محاسبات عمومی کشور","مالی و عمومی","1743801659624223197"),
    OfficialLaw("قانون تشدید مجازات مرتکبین ارتشاء و اختلاس و کلاهبرداری","کیفری","10842884964381905461"),
    OfficialLaw("قانون اساسی جمهوری اسلامی ایران","عمومی","6623702055317218729"),
    OfficialLaw("قانون تأمین اجتماعی","کار و تأمین اجتماعی","15217005284570186428"),
    OfficialLaw("قانون تملک آپارتمان‌ها","ثبتی","5370249204644142241"),
    OfficialLaw("قانون امور حسبی","حقوقی","5855336716322644674"),
    OfficialLaw("قانون ثبت اسناد و املاک","ثبتی","10904323794258146747")
)

private object OfficialLawCache {

    fun fetchRelated(law:OfficialLaw):List<RelatedOfficialDocument> {
        require(law.id.isNotBlank()) { "شناسه رسمی این عنوان نیازمند تطبیق است." }
        val endpoints=listOf(
            "https://qavanin.ir/Law/Attribute/?IDS="+law.id,
            "https://qavanin.ir/Law/StatusIndex/?IDS="+law.id
        )
        val results=linkedMapOf<String,RelatedOfficialDocument>()
        val anchors=Regex("(?is)<a\\b[^>]*href\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>(.*?)</a>")
        for(endpoint in endpoints) {
            val html=try {
                val connection=URL(endpoint).openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects=true
                    connection.connectTimeout=15000
                    connection.readTimeout=20000
                    connection.setRequestProperty("User-Agent","Payesh-Official-Relations/2.0")
                    val code=connection.responseCode
                    if(code !in 200..299) continue
                    val host=connection.url.host.lowercase()
                    if(host!="qavanin.ir" && !host.endsWith(".qavanin.ir")) continue
                    val output=ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer=ByteArray(8192)
                        while(true) {
                            val count=input.read(buffer)
                            if(count<0) break
                            if(output.size()+count>6000000) break
                            output.write(buffer,0,count)
                        }
                    }
                    String(output.toByteArray(),Charsets.UTF_8)
                } finally { connection.disconnect() }
            } catch(_:Exception) { continue }
            for(match in anchors.findAll(html)) {
                val raw=match.groupValues[1].replace("&amp;","&")
                if(!raw.contains("TreeText",ignoreCase=true)) continue
                val absolute=try { URL(URL(endpoint),raw) } catch(_:Exception) { continue }
                val host=absolute.host.lowercase()
                if(host!="qavanin.ir" && !host.endsWith(".qavanin.ir")) continue
                val id=Regex("[?&]IDS=(\\d+)",RegexOption.IGNORE_CASE).find(absolute.toString())?.groupValues?.get(1) ?: continue
                if(id==law.id) continue
                val title=Html.fromHtml(match.groupValues[2],Html.FROM_HTML_MODE_LEGACY).toString()
                    .replace(Regex("\\s+")," ").trim().take(180)
                if(title.length<4) continue
                results[id]=RelatedOfficialDocument(id,title,relatedKind(title),absolute.toString())
            }
        }
        return results.values.toList().take(300)
    }

    private fun file(context:Context,law:OfficialLaw):File {
        val directory=File(context.filesDir,"official-laws").apply { mkdirs() }
        val name=law.id.ifBlank { "search-"+law.title.hashCode().toString().replace("-","n") }
        return File(directory,name+".txt")
    }
    fun read(context:Context,law:OfficialLaw):String = runCatching {
        val item=file(context,law)
        if(item.isFile) item.readText(Charsets.UTF_8) else ""
    }.getOrDefault("")
    fun has(context:Context,law:OfficialLaw):Boolean = file(context,law).isFile
    fun updatedAt(context:Context,law:OfficialLaw):Long = file(context,law).lastModified()

    fun fetchAndCache(context:Context,law:OfficialLaw):String {
        require(law.id.isNotBlank()) { "شناسه رسمی این عنوان هنوز تأیید نشده؛ آن را از مرجع رسمی جست‌وجو کنید." }
        val connection=URL(law.url()).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=true
            connection.connectTimeout=20000
            connection.readTimeout=30000
            connection.setRequestProperty("User-Agent","Payesh-Android-Legal-Library/2.0")
            connection.setRequestProperty("Accept","text/html,application/xhtml+xml")
            val code=connection.responseCode
            require(code in 200..299) { "مرجع رسمی پاسخ HTTP "+code+" داد." }
            val host=connection.url.host.lowercase()
            require(host=="qavanin.ir" || host.endsWith(".qavanin.ir")) { "هدایت به دامنه غیررسمی متوقف شد." }
            val bytes=ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer=ByteArray(8192)
                while(true) {
                    val n=input.read(buffer)
                    if(n<0) break
                    require(bytes.size()+n<=12000000) { "پاسخ مرجع رسمی برای ذخیره بسیار بزرگ است." }
                    bytes.write(buffer,0,n)
                }
            }
            var html=String(bytes.toByteArray(),Charsets.UTF_8)
            html=Regex("(?is)<script\\b[^>]*>.*?</script>").replace(html," ")
            html=Regex("(?is)<style\\b[^>]*>.*?</style>").replace(html," ")
            html=Regex("(?is)<nav\\b[^>]*>.*?</nav>").replace(html," ")
            html=Regex("(?is)<header\\b[^>]*>.*?</header>").replace(html," ")
            html=Regex("(?is)<footer\\b[^>]*>.*?</footer>").replace(html," ")
            val article=Regex("(?is)<main\\b[^>]*>(.*?)</main>").find(html)?.groupValues?.get(1) ?: html
            val plain=Html.fromHtml(article,Html.FROM_HTML_MODE_LEGACY).toString()
                .replace('\u00a0',' ')
                .replace(Regex("[ \\t]+")," ")
                .replace(Regex("\\n{3,}"),"\n\n")
                .trim()
            require(plain.length>150 && (plain.contains("ماده") || plain.contains("مصوب") || plain.contains("رأی") || plain.contains("قانون"))) {
                "متن قانون از پاسخ دریافت‌شده قابل تأیید نبود؛ چیزی ذخیره نشد."
            }
            val target=file(context,law)
            target.writeText(plain,Charsets.UTF_8)
            return plain
        } finally {
            connection.disconnect()
        }
    }
}

private fun legalSearchSnippets(text:String,query:String):String {
    if(query.isBlank()) return text
    val output=StringBuilder()
    var from=0
    var count=0
    while(count<20) {
        val at=text.indexOf(query,from,ignoreCase=true)
        if(at<0) break
        val start=(at-160).coerceAtLeast(0)
        val end=(at+query.length+420).coerceAtMost(text.length)
        if(output.isNotEmpty()) output.append("\n\n…\n\n")
        output.append(text.substring(start,end))
        from=(at+query.length).coerceAtLeast(at+1)
        count++
    }
    return if(output.isEmpty()) "عبارت در متن ذخیره‌شده پیدا نشد." else output.toString()
}

@Composable
fun OfficialLegalCatalogScreen(onBack:()->Unit) {
    val context=LocalContext.current
    val uriHandler=LocalUriHandler.current
    val scope=rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("همه") }
    var selectedKey by rememberSaveable { mutableStateOf("") }
    var selectedLaw by remember { mutableStateOf<OfficialLaw?>(null) }
    var localText by remember { mutableStateOf("") }
    var searchWithin by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var monitorEnabled by remember { mutableStateOf(LegalUpdateWorker.isEnabled(context)) }
    var monitorMessage by remember { mutableStateOf("") }
    var exportText by remember { mutableStateOf("") }
    var relatedDocs by remember { mutableStateOf<List<RelatedOfficialDocument>>(emptyList()) }
    var loadingRelated by remember { mutableStateOf(false) }
    val legalNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) {
            monitorEnabled=true
            LegalUpdateWorker.setEnabled(context,true)
            monitorMessage="اعلان‌ها مجاز شد؛ بررسی فهرست رسمی فعال شد."
        } else {
            monitorEnabled=false
            LegalUpdateWorker.setEnabled(context,false)
            monitorMessage="بدون مجوز اعلان، پایش خودکار متوقف ماند."
        }
    }
    val saveText=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if(uri!=null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(exportText) }
                ?: error("مسیر ذخیره فایل باز نشد")
        }.onSuccess { message="متن ذخیره‌شده صادر شد." }.onFailure { message="ذخیره متن ناموفق بود." }
    }
    val preferences=remember { context.getSharedPreferences("preferences",Context.MODE_PRIVATE) }
    val lastCheck=preferences.getLong("legal_watch_last_check",0L)
    val recentChanges=runCatching { org.json.JSONArray(preferences.getString("legal_watch_updates","[]") ?: "[]") }.getOrNull()

    fun open(url:String) { runCatching { uriHandler.openUri(url) }.onFailure { message="بازکردن پیوند رسمی ناموفق بود." } }
    fun select(law:OfficialLaw) {
        selectedLaw=law
        selectedKey=law.key
        localText=""
        searchWithin=""
        message=""
        relatedDocs=runCatching {
            val json=org.json.JSONArray(preferences.getString("official_related_"+law.id,"[]") ?: "[]")
            (0 until json.length()).map { index ->
                val item=json.getJSONObject(index)
                RelatedOfficialDocument(item.optString("id"),item.optString("title"),item.optString("kind"),item.optString("url"))
            }
        }.getOrDefault(emptyList())
        scope.launch {
            val saved=withContext(Dispatchers.IO) { OfficialLawCache.read(context,law) }
            if(selectedKey==law.key) {
                localText=saved
                message=if(saved.isBlank()) "متن هنوز در حافظه آفلاین ذخیره نشده است." else "نسخه محلی موجود است؛ آخرین ذخیره: "+java.text.SimpleDateFormat("yyyy/MM/dd HH:mm",java.util.Locale.ROOT).format(java.util.Date(OfficialLawCache.updatedAt(context,law)))
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            IconButton(onClick={
                if(selectedLaw!=null) { selectedLaw=null; selectedKey=""; localText=""; message="" } else onBack()
            }) { Icon(Icons.Outlined.ArrowBack,"بازگشت") }
            Column(Modifier.weight(1f)) {
                Text(if(selectedLaw==null) "کتابخانه قوانین و مقررات" else selectedLaw!!.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("منبع متن رسمی: Qavanin.ir",style=MaterialTheme.typography.bodySmall)
            }
        }

        if(selectedLaw==null) {
            Card {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Gavel,null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("پایش فهرست رسمی قوانین",fontWeight=FontWeight.SemiBold)
                            Text("هر ۱۲ ساعت فهرست قابل‌خواندن مرجع بررسی می‌شود؛ این روش همه اصلاحات متن قوانین یا همه آرای قضایی را تضمین نمی‌کند.",style=MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked=monitorEnabled,onCheckedChange={enabled->
                            if(!enabled) {
                                monitorEnabled=false
                                LegalUpdateWorker.setEnabled(context,false)
                                monitorMessage="بررسی خودکار متوقف شد."
                            } else if(Build.VERSION.SDK_INT>=33 &&
                                ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                                legalNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                monitorEnabled=true
                                LegalUpdateWorker.setEnabled(context,true)
                                monitorMessage="بررسی دوره‌ای فعال شد؛ اجرای اول فقط خط مبنا را ثبت می‌کند."
                            }
                        })
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(if(lastCheck>0) "آخرین بررسی: "+java.text.SimpleDateFormat("yyyy/MM/dd HH:mm",java.util.Locale.ROOT).format(java.util.Date(lastCheck)) else "هنوز بررسی رسمی ثبت نشده است",style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
                        OutlinedButton(onClick={LegalUpdateWorker.checkNow(context);monitorMessage="درخواست بررسی دستی ارسال شد؛ نتیجه پس از اجرای کار در این صفحه به‌روز می‌شود."}) { Text("بررسی اکنون") }
                    }
                    if(monitorMessage.isNotBlank()) Text(monitorMessage,style=MaterialTheme.typography.bodySmall)
                    if(recentChanges!=null && recentChanges.length()>0) {
                        Text("آخرین اسناد شناسایی‌شده:",fontWeight=FontWeight.SemiBold)
                        for(i in 0 until minOf(3,recentChanges.length())) {
                            val item=recentChanges.optJSONObject(i)
                            if(item!=null) Text("• "+item.optString("title"),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("جست‌وجو در نام قانون یا دسته‌بندی")},leadingIcon={Icon(Icons.Outlined.Search,null)})
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("همه","حقوقی","ثبتی","تجاری","کیفری","خانواده","اداری","اقتصادی و عمومی","مالیاتی","کار و تأمین اجتماعی","اجرای احکام").forEach { value->
                    FilterChip(selected=category==value,onClick={category=value},label={Text(value)})
                }
            }
            Text("فهرست قابل جست‌وجوی منابع رسمی: "+officialLaws.size+" عنوان. برای استفاده آفلاین، متن هر قانون را از صفحه جزئیات دریافت و ذخیره کنید.",style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(officialLaws.filter { law ->
                    (category=="همه" || law.category==category) &&
                    (law.title.contains(query,ignoreCase=true) || law.category.contains(query,ignoreCase=true))
                },key={it.key}) { law->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(law.title,fontWeight=FontWeight.SemiBold)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                AssistChip(onClick={category=law.category},label={Text(law.category)})
                                Spacer(Modifier.weight(1f))
                                if(OfficialLawCache.has(context,law)) Text("متن آفلاین ذخیره شده",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall)
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick={select(law)}) { Text("مطالعه و جست‌وجو") }
                                OutlinedButton(onClick={open(law.url())}) { Text("متن رسمی آنلاین") }
                            }
                        }
                    }
                }
            }
        } else {
            val law=selectedLaw!!
            Text("نسخه ذخیره‌شده برای مطالعه آفلاین است؛ پس از اصلاح قانون، نسخه را دوباره دریافت کنید.",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={open(law.url())}) { Text("متن رسمی آنلاین") }
                if(law.id.isNotBlank()) {
                    OutlinedButton(onClick={open("https://qavanin.ir/Law/Attribute/?IDS="+law.id)}) { Text("مشخصات و روابط") }
                    OutlinedButton(onClick={open("https://qavanin.ir/Law/StatusIndex/?IDS="+law.id)}) { Text("قوانین اثرگذار/پذیر") }
                    Button(enabled=!loading,onClick={
                        loading=true
                        message="در حال دریافت متن از منبع رسمی..."
                        scope.launch {
                            try {
                                val received=withContext(Dispatchers.IO) { OfficialLawCache.fetchAndCache(context,law) }
                                localText=received
                                message="متن دریافت و برای مطالعه آفلاین ذخیره شد؛ اعتبار و کامل‌بودن نسخه را در مرجع رسمی کنترل کنید."
                            } catch(error:Exception) {
                                message=error.message?.take(180) ?: "دریافت متن رسمی ناموفق بود."
                            } finally { loading=false }
                        }
                    }) { Icon(Icons.Outlined.Download,null);Spacer(Modifier.width(4.dp));Text(if(loading) "در حال دریافت..." else "دریافت/به‌روزرسانی آفلاین") }
                }
                OutlinedButton(
                    enabled=!loadingRelated && law.id.isNotBlank(),
                    onClick={
                        loadingRelated=true
                        message="در حال بررسی پیوندهای رسمی این قانون..."
                        scope.launch {
                            try {
                                val found=withContext(Dispatchers.IO) { OfficialLawCache.fetchRelated(law) }
                                relatedDocs=found
                                val json=org.json.JSONArray()
                                found.forEach { item ->
                                    json.put(org.json.JSONObject().put("id",item.id).put("title",item.title).put("kind",item.kind).put("url",item.url))
                                }
                                preferences.edit().putString("official_related_"+law.id,json.toString()).apply()
                                message=if(found.isEmpty()) "در صفحات ارتباطی قابل‌دسترسی، سند مرتبطی استخراج نشد؛ این به معنی نبود مقررات یا آرا نیست. صفحات رسمی را جداگانه بررسی کنید."
                                    else "از صفحات ارتباطی رسمی "+found.size+" عنوان مرتبط استخراج شد؛ کامل‌بودن فهرست باید در مرجع رسمی بررسی شود."
                            } catch(error:Exception) {
                                message="دریافت روابط رسمی ناموفق بود."
                            } finally { loadingRelated=false }
                        }
                    }
                ) { Text(if(loadingRelated) "در حال بررسی..." else "استخراج فهرست اسناد مرتبط") }
            }
            if(relatedDocs.isNotEmpty()) {
                Text("روابط استخراج‌شده از صفحات رسمی (نه تضمین فهرست کامل):",fontWeight=FontWeight.SemiBold)
                LazyColumn(Modifier.heightIn(max=190.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    items(relatedDocs,key={it.id}) { item ->
                        Card {
                            Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.title,style=MaterialTheme.typography.bodyMedium)
                                    Text(item.kind,style=MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick={open(item.url)}) { Text("مرجع") }
                                TextButton(onClick={select(OfficialLaw(item.title,item.kind,item.id))}) { Text("مطالعه") }
                            }
                        }
                    }
                }
            }
            if(localText.isNotBlank()) {
                OutlinedTextField(value=searchWithin,onValueChange={searchWithin=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("جست‌وجو داخل همین قانون")})
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={exportText=localText;saveText.launch("متن-قانون-"+law.key+".txt")}) { Text("ذخیره متن TXT") }
                    Text("ذخیره‌شده روی همین دستگاه",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=12.dp))
                }
                Text("منبع: "+law.url(),style=MaterialTheme.typography.labelSmall)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    SelectionContainer { Text(legalSearchSnippets(localText,searchWithin)) }
                }
            } else {
                Spacer(Modifier.weight(1f))
                EmptyPanel("متن آفلاین هنوز ذخیره نشده","برای ذخیره متن، اینترنت را روشن و «دریافت/به‌روزرسانی آفلاین» را انتخاب کنید. برای عنوان‌های فاقد شناسه قطعی، فقط پیوند مرجع رسمی باز می‌شود.",Icons.Outlined.Download)
            }
            if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall)
        }
    }
}
