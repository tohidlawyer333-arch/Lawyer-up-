package com.lawyerup.app.ui

import android.Manifest
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lawyerup.app.*
import com.lawyerup.app.data.*
import com.lawyerup.app.data.local.AppDatabase
import com.lawyerup.app.domain.*
import com.lawyerup.app.ui.screens.*
import com.lawyerup.app.ui.theme.*
import com.lawyerup.app.web.PortalController
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun WelcomeScreen(message:String,onEnter:()->Unit,onSecurity:()->Unit) {
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.Center) {
            Hero("دفتر شما،\nیک قدم جلوتر.","پایش • دستیار پرونده و پیگیری",null)
            Spacer(Modifier.height(28.dp))
            Text("به دفتر امن خود خوش آمدید",style=MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(message)
            Spacer(Modifier.height(24.dp))
            Button(onClick=onEnter,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Lock,null);Spacer(Modifier.width(8.dp));Text("ورود با قفل دستگاه") }
            TextButton(onClick=onSecurity,modifier=Modifier.fillMaxWidth()) {Text("تنظیم قفل دستگاه")}
            Text("بدون رمز نمایشی، بدون داده آزمایشی در کارتابل. ورود ثنا در نشست رسمی شما انجام می‌شود.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun Hero(title:String,subtitle:String,action:(@Composable ()->Unit)?) {
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.tertiary)),RoundedCornerShape(28.dp)).padding(24.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Outlined.Balance,null,tint=MaterialTheme.colorScheme.secondary);Spacer(Modifier.width(10.dp));Text("پایش / همراه دفتر",color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.82f),style=MaterialTheme.typography.labelMedium)}
        Spacer(Modifier.height(20.dp))
        Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.height(12.dp));Text(subtitle,color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.90f))
        action?.let {Spacer(Modifier.height(18.dp));it()}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceApp(model:WorkspaceModel,onLock:()->Unit,locked:Boolean=false,themeChoice:String="teal",onThemeChange:(String)->Unit={}) {
    val context=LocalContext.current
    val data by model.workspace.collectAsState()
    val error by model.storageError.collectAsState()
    val ready by model.ready.collectAsState()
    val busy by model.busy.collectAsState()
    val message by model.message.collectAsState()
    val scope=rememberCoroutineScope()
    var page by rememberSaveable {mutableStateOf("home")}
    var detail by remember {mutableStateOf<CourtRecord?>(null)}
    var task by remember {mutableStateOf<WorkItem?>(null)}
    var portal by remember {mutableStateOf<PortalController?>(null)}
    var claimId by remember {mutableStateOf<Long?>(null)}
    val law=remember {LawRepository(AppDatabase.getInstance(context))}
    val snack=remember {SnackbarHostState()}
    LaunchedEffect(message) {if(message.isNotEmpty()){snack.showSnackbar(message);model.message.value=""}}
    LaunchedEffect(locked) {if(locked) portal?.cancel()}
    DisposableEffect(Unit) {onDispose {portal?.close()}}
    fun openPortal() {
        if(portal==null) portal=PortalController(context,model.repository).also {it.open("https://eblagh1.adliran.ir/ENotice/Index")}
        page="portal"
    }
    Scaffold(topBar={TopAppBar(navigationIcon={if(page=="portal") {IconButton(onClick={page="home"}) {Icon(Icons.Outlined.ArrowBack,"بازگشت به دفتر")}}},title={Column {Text("پایش",fontWeight=FontWeight.Bold);Text("دفتر دیجیتال وکیل",style=MaterialTheme.typography.labelSmall)}},actions={
        IconButton(onClick={page="health"}) {Icon(Icons.Outlined.MonitorHeart,"سلامت و اتصال")}
        IconButton(onClick=onLock) {Icon(Icons.Outlined.Lock,"قفل دفتر")}
    })},snackbarHost={SnackbarHost(snack)},bottomBar={if(page!="portal") NavigationBar {
        listOf(Triple("home","دفتر",Icons.Outlined.SpaceDashboard),Triple("cases","پرونده‌ها",Icons.Outlined.FolderOpen),Triple("notices","ابلاغ‌ها",Icons.Outlined.NotificationsNone),Triple("tasks","پیگیری",Icons.Outlined.TaskAlt),Triple("tools","ابزارها",Icons.Outlined.Widgets)).forEach { (route,label,icon) ->
            NavigationBarItem(selected=page==route,onClick={page=route},icon={Icon(icon,null)},label={Text(label,style=MaterialTheme.typography.labelSmall)})
        }
    }}) {padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if(error) EmptyPanel("حافظه قابل خواندن نیست","داده‌ها حذف نشده‌اند. برنامه را متوقف و پشتیبان را بررسی کنید؛ عملیات نوشتن غیرفعال است.",Icons.Outlined.ErrorOutline)
            else if(!ready) LinearProgressIndicator(Modifier.fillMaxWidth())
            else when(page) {
                "home" -> Dashboard(data,{page=it},::openPortal,{detail=it})
                "cases" -> RecordsScreen(data,false,{detail=it})
                "notices" -> RecordsScreen(data,true,{detail=it})
                "tasks" -> TasksScreen(data,{task=it})
                "portal" -> portal?.let {PortalScreen(it,{all->scope.launch {it.sync(all)}},{page="health"})}
                "health" -> HealthScreen(data,portal,::openPortal)
                "library" -> if(claimId==null) ClaimsListScreen(law){claimId=it} else Column {TextButton(onClick={claimId=null}){Text("بازگشت به مرجع")};AuthorityDetailScreen(law,claimId!!)}
                "matcher" -> CaseMatcherScreen(law,data)
                else -> ToolsScreen(model,{page=it},::openPortal,themeChoice,onThemeChange)
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    detail?.let { initial ->
        val record=data.records.find {it.id==initial.id}?:initial
        RecordDialog(record,data,onDismiss={detail=null},onReview={model.review(record,!record.reviewed);detail=null})
    }
    task?.let { initial ->
        val selected=data.tasks.find {it.id==initial.id}?:initial
        TaskDialog(selected,data.records.find {it.id==selected.recordId},onDismiss={task=null},onSave={model.save(it);task=null})
    }
}

@Composable
fun Dashboard(data:Workspace,navigate:(String)->Unit,receive:()->Unit,detail:(CourtRecord)->Unit) {
    val today=LocalDate.now(ZoneId.of("Asia/Tehran"))
    val near=data.tasks.count {!it.done && it.dateConfirmed && runCatching {LocalDate.parse(it.due)<=today.plusDays(3)}.getOrDefault(false)}
    val exact=data.notices.count {Reconciler.match(it.copy(reviewed=false),data.cases).level in setOf(MatchLevel.EXACT,MatchLevel.ACTION_RECORDED)}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {Hero("پرونده‌ها روشن‌تر،\nپیگیری‌ها منظم‌تر.","${fa(PersianDates.display(today))} • اطلاعات دفتر، یک‌جا و امن") {
            Button(onClick=receive,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer)) {Icon(Icons.Outlined.Sync,null);Spacer(Modifier.width(8.dp));Text("دریافت و تطبیق ثنا")}
        }}
        item {SectionTitle("نمای کلی دفتر","همه اعداد از داده ذخیره‌شده")}
        item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {Metric("پرونده دریافت‌شده",data.cases.size,Icons.Outlined.FolderOpen,Modifier.weight(1f)){navigate("cases")};Metric("بررسی‌نشده در پایش",data.notices.count {!it.reviewed},Icons.Outlined.MarkEmailUnread,Modifier.weight(1f)){navigate("notices")}}}
        item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {Metric("موعد نزدیک یا گذشته",near,Icons.Outlined.Event,Modifier.weight(1f)){navigate("tasks")};Metric("تطبیق دقیق شناسه",exact,Icons.Outlined.JoinInner,Modifier.weight(1f)){navigate("notices")}}}
        item {SectionTitle("میز پیگیری","بررسی دستی از نتیجه تطبیق جداست")}
        if(data.notices.isEmpty()) item {EmptyPanel("هیچ ابلاغیه‌ای در پایش ثبت نشده","این وضعیت به‌هیچ‌وجه نشان نمی‌دهد کارتابل ثنا خالی است. تا وقتی گزارش دریافت موفق و تعداد رکوردهای واردشده را بررسی نکرده‌اید، ثنا را مستقلاً کنترل کنید.",Icons.Outlined.Inbox)}
        items(data.notices.filter {!it.reviewed}.take(3),key={it.id}) {record->RecordTile(record,data){detail(record)}}
        item {OutlinedButton(onClick={navigate("tasks")},modifier=Modifier.fillMaxWidth()){Text("همه پیگیری‌ها و پیش‌نویس‌ها")}}
        item {Text("تطبیق شناسه، اثبات کفایت حقوقی اقدام نیست. محاسبه موعد نیازمند تاریخ مبنا و تأیید تقویم وکیل است.",style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable
fun Metric(title:String,value:Int,icon:ImageVector,modifier:Modifier,onClick:()->Unit) {
    Card(modifier.clickable(onClick=onClick)) {Column(Modifier.padding(18.dp).heightIn(min=145.dp)) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp));Text(fa(value),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Text(title,style=MaterialTheme.typography.labelMedium)
    }}
}
@Composable fun SectionTitle(title:String,subtitle:String) {Column {Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(subtitle,style=MaterialTheme.typography.bodySmall)}}
@Composable fun EmptyPanel(title:String,message:String,icon:ImageVector) {Card(Modifier.fillMaxWidth().padding(12.dp)) {Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {Icon(icon,null,Modifier.size(42.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(16.dp));Text(title,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text(message)}}}
@Composable fun Badge(text:String) {Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(10.dp)) {Text(text,Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelSmall)}}

@Composable
fun RecordsScreen(data:Workspace,notices:Boolean,detail:(CourtRecord)->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var filter by rememberSaveable {mutableStateOf("همه")}
    val filtered=(if(notices)data.notices else data.cases).filter {record ->
        normalize(record.text+record.caseNumber+record.noticeNumber).contains(normalize(query)) && when(filter) {
            "بررسی‌نشده در پایش" -> !record.reviewed
            "بررسی‌شده در پایش" -> record.reviewed
            "تطبیق دقیق" -> Reconciler.match(record.copy(reviewed=false),data.cases).level in setOf(MatchLevel.EXACT,MatchLevel.ACTION_RECORDED)
            else -> true
        }
    }
    val records=if(notices && filter=="۲۰ مشاهده‌شده اخیر در ثنا") filtered.filter {record ->
        val origin=normalize(record.fields.entries.firstOrNull {it.key=="فهرست مبدأ ثنا"}?.value.orEmpty())
        origin.contains("مشاهده شده")
    }.sortedByDescending {record -> normalize(record.fields.entries.firstOrNull {it.key.contains("تاریخ صدور")}?.value.orEmpty()).replace('/','-') }.take(20) else filtered
    val subtitle=if(notices && filter=="۲۰ مشاهده‌شده اخیر در ثنا") "۲۰ مورد آخر از فهرست مشاهده‌شده‌های دریافت‌شده از ثنا؛ دریافت ناقص یا فیلتر تاریخ می‌تواند سابقه را محدود کند" else "جست‌وجو در داده‌های ذخیره‌شده؛ صفر به‌معنای خالی بودن کارتابل ثنا نیست"
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {SectionTitle(if(notices)"ابلاغیه‌ها" else "اطلاع‌رسانی پرونده‌ها","${fa(records.size)} مورد • $subtitle")}
        item {OutlinedTextField(value=query,onValueChange={query=it},label={Text("شماره، شعبه یا موضوع")},leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        if(notices) item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("همه","بررسی‌نشده در پایش","تطبیق دقیق","بررسی‌شده در پایش","۲۰ مشاهده‌شده اخیر در ثنا").forEach {label->FilterChip(selected=label==filter,onClick={filter=label},label={Text(label)})}}}
        if(records.isEmpty()) item {EmptyPanel("موردی پیدا نشد","بررسی کنید که فهرست درست را در ثنا باز کرده و دریافت موفق با تعداد رکوردهای معتبر ثبت شده باشد.",Icons.Outlined.ManageSearch)}
        items(records,key={it.id}) {record->RecordTile(record,data){detail(record)}}
    }
}
@Composable
fun RecordTile(record:CourtRecord,data:Workspace,onClick:()->Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick)) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {Icon(if(record.kind==RecordKind.NOTICE)Icons.Outlined.Description else Icons.Outlined.FolderOpen,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp));Text(record.subject,style=MaterialTheme.typography.titleSmall,maxLines=3)}
        SelectionContainer {Text(fa(record.noticeNumber.ifBlank {record.caseNumber}),style=MaterialTheme.typography.titleMedium)}
        Text(record.fields.entries.firstOrNull {it.key.contains("شعبه")||it.key.contains("صادر کننده")}?.value.orEmpty(),maxLines=2,style=MaterialTheme.typography.bodySmall)
        if(record.kind==RecordKind.NOTICE) {
            val portalList=record.fields.entries.firstOrNull {it.key=="فهرست مبدأ ثنا"}?.value.orEmpty()
            if(portalList.isNotBlank()) Badge("منبع ثنا: $portalList")
            Badge("وضعیت داخلی پایش: ${if(record.reviewed)"بررسی‌شده" else "بررسی‌نشده"}")
            Badge(Reconciler.match(record,data.cases).title)
        } else Badge("ردیف فرعی: ${fa(record.subNumber.ifBlank {"نامشخص"})}")
        Text("جزئیات و مستندات ←",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
    }}
}

@Composable
fun RecordDialog(record:CourtRecord,data:Workspace,onDismiss:()->Unit,onReview:()->Unit) {
    val match=Reconciler.match(record,data.cases)
    var confirm by remember {mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(record.subject)},text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(record.kind==RecordKind.NOTICE) {Badge(match.title);Text(match.reason)}
        SelectionContainer {Text(record.text)}
        Text("منبع: ${record.source}",style=MaterialTheme.typography.bodySmall)
        Text("دریافت: ${fa(java.time.Instant.ofEpochMilli(record.importedAt).atZone(ZoneId.of("Asia/Tehran")).toLocalDateTime())}",style=MaterialTheme.typography.labelSmall)
        if(match.evidence.isNotEmpty()) {Text("مدرک تطبیق",fontWeight=FontWeight.Bold);match.evidence.forEach {SelectionContainer {Text(it)}}}
        if(record.kind==RecordKind.CASE) {
            Text(ActionEngine.explain(record.text))
            Text("گردش دریافت‌شده (${fa(record.evidence.size)})",fontWeight=FontWeight.Bold)
            record.evidence.forEach {SelectionContainer {Text(it)}}
            Text("ابلاغ‌های مرتبط: ${fa(data.notices.count {Reconciler.match(it,listOf(record)).caseNumber==record.caseNumber})}")
        }
    }},confirmButton={if(record.kind==RecordKind.NOTICE) TextButton(onClick={confirm=true}) {Text(if(record.reviewed)"بازگرداندن به بررسی" else "علامت‌گذاری به‌عنوان بررسی‌شده در پایش")}},dismissButton={TextButton(onClick=onDismiss){Text("بستن")}})
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("تغییر وضعیت بررسی در پایش؟")},text={Text("این گزینه فقط یادداشت داخلی شما در پایش است و وضعیت مشاهده‌شدن ابلاغیه در ثنا را تغییر نمی‌دهد؛ هیچ اقدامی در سامانه رسمی انجام نمی‌شود.")},confirmButton={Button(onClick=onReview){Text("تأیید")}},dismissButton={TextButton(onClick={confirm=false}){Text("انصراف")}})
}

@Composable
fun TasksScreen(data:Workspace,onEdit:(WorkItem)->Unit) {
    var done by rememberSaveable {mutableStateOf(false)}
    val tasks=data.tasks.filter {it.done==done}.sortedWith(compareBy<WorkItem>{if(it.dateConfirmed)it.due else "9999"}.thenBy {it.title})
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {SectionTitle("میز پیگیری","اقدام، موعد و پیش‌نویس؛ با تأیید شما")}
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(!done,{done=false},label={Text("در انتظار")});FilterChip(done,{done=true},label={Text("انجام‌شده با مستند")})}}
        if(tasks.isEmpty()) item {EmptyPanel("پیگیری‌ای در این بخش نیست","بعد از ورود اطلاعات، پیشنهادهای مرتبط ساخته می‌شوند؛ از جزئیات، موعد و یادداشت اقدام را ثبت کنید.",Icons.Outlined.TaskAlt)}
        items(tasks,key={it.id}) {task->Card(Modifier.fillMaxWidth().clickable {onEdit(task)}) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(task.title,fontWeight=FontWeight.Bold);Text(task.rationale,style=MaterialTheme.typography.bodySmall,maxLines=3)
            Badge(if(task.dateConfirmed) "موعد تأییدشده: ${fa(PersianDates.display(LocalDate.parse(task.due)))}" else "موعد هنوز تأیید نشده")
            Text(if(task.draft.isEmpty())"تنظیم پیگیری و ساخت پیش‌نویس ←" else "پیش‌نویس ذخیره‌شده • ویرایش ←",color=MaterialTheme.colorScheme.primary)
        }}}
    }
}

@Composable
fun TaskDialog(task:WorkItem,record:CourtRecord?,onDismiss:()->Unit,onSave:(WorkItem)->Unit) {
    var draft by remember(task.id) {mutableStateOf(task.draft)}
    var date by remember(task.id) {mutableStateOf(if(task.due.isEmpty())"" else PersianDates.display(LocalDate.parse(task.due)))}
    var confirmed by remember {mutableStateOf(task.dateConfirmed)}
    var done by remember {mutableStateOf(task.done)}
    var receipt by remember {mutableStateOf(task.receipt)}
    var error by remember {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(task.title)},text={Column(Modifier.heightIn(max=540.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(task.rationale,style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(date,{date=it;confirmed=false},label={Text("موعد شمسی؛ مثال ۱۴۰۵/۰۸/۰۱")},modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(confirmed,{confirmed=it});Text("تاریخ مبنا، قانون و تعطیلات را بررسی کردم",style=MaterialTheme.typography.bodySmall)}
        OutlinedButton(onClick={draft=ActionEngine.draft(task,record)}){Text("ساخت پیش‌نویس قابل ویرایش")}
        OutlinedTextField(draft,{draft=it},label={Text("پیش‌نویس؛ ارسال نشده")},modifier=Modifier.fillMaxWidth().heightIn(min=160.dp))
        OutlinedTextField(receipt,{receipt=it},label={Text("یادداشت اقدام / شماره رسید ثبت")},modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(done,{done=it});Text("انجام اقدام را با مستند تأیید می‌کنم",style=MaterialTheme.typography.bodySmall)}
        if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={Button(onClick={
        val parsed=if(date.isBlank())null else runCatching {PersianDates.parse(date)}.getOrNull()
        when {date.isNotBlank()&&parsed==null->error="تاریخ شمسی معتبر نیست";confirmed&&parsed==null->error="موعد را وارد کنید";done&&receipt.isBlank()->error="یادداشت یا رسید اقدام لازم است";else->onSave(task.copy(draft=draft,due=parsed?.toString().orEmpty(),dateConfirmed=confirmed,done=done,receipt=receipt))}
    }){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("بستن")}})
}

@Composable
fun PortalScreen(controller:PortalController,sync:(Boolean)->Unit,health:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var downloadBytes by remember {mutableStateOf<ByteArray?>(null)}
    var downloadError by remember {mutableStateOf("")}
    var downloading by remember {mutableStateOf(false)}
    val saveDocument=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {uri->
        if(uri!=null) scope.launch {
            try {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){context.contentResolver.openOutputStream(uri)!!.use {it.write(requireNotNull(downloadBytes))}}}
            catch(_:Exception){downloadError="ذخیره فایل ناموفق بود"}
            finally {downloadBytes=null}
        } else downloadBytes=null
    }
    var all by rememberSaveable {mutableStateOf(true)}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("ابلاغیه‌ها" to "https://eblagh1.adliran.ir/ENotice/Index","اطلاع‌رسانی پرونده‌ها" to "https://resultcase.adliran.ir/ResultCase/CaseListForLawyer","پنل وکیل" to "https://lawyer.adliran.ir/Lawyer/Index").forEach {(label,url)->OutlinedButton(enabled=!controller.busy,onClick={controller.open(url)}){Text(label)}}
        }
        Text(controller.status,Modifier.padding(horizontal=16.dp,vertical=6.dp),style=MaterialTheme.typography.bodySmall)
        if(downloadError.isNotEmpty()) Text(downloadError,color=MaterialTheme.colorScheme.error)
        Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {Switch(all,{all=it},enabled=!controller.busy);Text("صفحات پیوسته از صفحه فعلی",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall);TextButton(onClick=health){Text("گزارش")}}
        Row(Modifier.padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={sync(all)},enabled=!controller.busy&&!controller.loading,modifier=Modifier.weight(1f)){Text("دریافت و تطبیق")}
            if(controller.busy) OutlinedButton(onClick=controller::cancel){Text("توقف")}
        }
        Text("بازکردن ابلاغ جدید می‌تواند آثار حقوقی داشته باشد؛ برنامه متن ابلاغ‌ها را خودکار باز نمی‌کند.",Modifier.padding(12.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error)
        if(controller.busy||controller.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        AndroidView(factory={(controller.view.parent as? ViewGroup)?.removeView(controller.view);controller.view},modifier=Modifier.fillMaxWidth().weight(1f),update={it.setOnTouchListener {_,_->controller.busy}})
    }
    if(controller.pendingDownload!=null) AlertDialog(onDismissRequest=controller::dismissDownload,title={Text("دریافت PDF از سامانه رسمی؟")},text={Text("فایل با نشست فعلی شما دریافت و در محل انتخابی ذخیره می‌شود. لینک و کوکی در گزارش ثبت نمی‌شوند.")},confirmButton={Button(enabled=!downloading,onClick={scope.launch {
        downloading=true
        try {downloadBytes=controller.download();saveDocument.launch("LawyerUp-court-document.pdf")}
        catch(_:Exception){downloadError="دریافت PDF ناموفق بود؛ نشست، حجم و قالب فایل را بررسی کنید";controller.dismissDownload()}
        finally {downloading=false}
    }}){Text(if(downloading)"در حال دریافت…" else "دریافت و انتخاب محل")}},dismissButton={TextButton(enabled=!downloading,onClick=controller::dismissDownload){Text("انصراف")}})
}

@Composable
fun HealthScreen(data:Workspace,controller:PortalController?,receive:()->Unit) {
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {SectionTitle("سلامت و اتصال","موفقیت دریافت با پاسخ HTTP یکی نیست")}
        item {Card {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(controller==null)"نشست باز نشده" else if(controller.loading)"در حال بارگذاری" else "نشست مرورگر آماده بررسی")
            Text(controller?.status?:"برای ورود، دریافت از ثنا را باز کنید")
            Button(onClick=receive){Text("بازکردن دریافت ثنا")}
        }}}
        item {Text("آخرین دریافت",style=MaterialTheme.typography.titleMedium)}
        if(data.reports.isEmpty()) item {Text("هنوز دریافت ثبت نشده است")}
        items(data.reports) {report ->
            Card {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Badge(if(report.optBoolean("ok"))"دریافت محدوده انتخابی موفق" else "دریافت ناقص یا ناموفق")
                Text(report.optString("message"))
                Text("صفحات: ${fa(report.optInt("pages"))} • ردیف معتبر: ${fa(report.optInt("accepted"))} • تغییر: ${fa(report.optInt("changed"))} • ردشده: ${fa(report.optInt("rejected"))}",style=MaterialTheme.typography.bodySmall)
            }}
        }
        item {Text("گزارش بدون رمز، کوکی و مقدار ردیف‌ها",style=MaterialTheme.typography.titleMedium)}
        item {SelectionContainer {Text(controller?.diagnostic()?:"نشستی وجود ندارد",style=MaterialTheme.typography.bodySmall)}}
    }
}
