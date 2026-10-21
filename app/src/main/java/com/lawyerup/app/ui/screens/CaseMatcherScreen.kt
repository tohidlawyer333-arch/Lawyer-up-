package com.lawyerup.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lawyerup.app.data.CaseMatcher
import com.lawyerup.app.data.LawRepository
import com.lawyerup.app.data.MatchResult
import com.lawyerup.app.data.Workspace
import com.lawyerup.app.data.local.ClaimEntity
import com.lawyerup.app.domain.normalize

private data class RelationshipFinding(val title:String,val detail:String,val sources:String)
private data class SourceText(val title:String,val text:String)

@Composable
fun CaseMatcherScreen(repository: LawRepository, workspace:Workspace) {
    var text by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<MatchResult>()) }
    var brief by remember { mutableStateOf<String?>(null) }
    var allClaims by remember { mutableStateOf(emptyList<ClaimEntity>()) }
    var findings by remember { mutableStateOf(emptyList<RelationshipFinding>()) }
    var analysisRan by remember { mutableStateOf(false) }
    var referenceSearchRan by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.allClaims().collect { allClaims = it }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("تحلیل پرونده",style=MaterialTheme.typography.headlineSmall)
        Text("منابع ذخیره‌شده: ${workspace.evidence.size} سند متنی • ${workspace.records.size} رکورد پرونده و ابلاغ",style=MaterialTheme.typography.bodySmall)
        Text("شرح پرونده را وارد کنید؛ اسناد OCR ذخیره‌شده هم بررسی می‌شوند. حتی اگر سند شماره پرونده نداشته باشد، می‌توان آن را از نظر پیوندهای متنی با سایر مدارک تحلیل کرد.",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value=text,
            onValueChange={text=it;findings=emptyList();analysisRan=false;brief=null},
            label={Text("شرح وقایع، ادعاها یا متن منتخب")},
            modifier=Modifier.fillMaxWidth().heightIn(min=150.dp),
            minLines=5
        )
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(enabled=text.isNotBlank() || workspace.evidence.size+workspace.records.size>=2,onClick={
                findings=analyzeRelationships(text,workspace)
                analysisRan=true
            }) {Text("تحلیل ارتباط‌ها")}
            OutlinedButton(enabled=text.isNotBlank(),onClick={
                matches=CaseMatcher.match(text,allClaims)
                referenceSearchRan=true
            }) {Text("جست‌وجوی مرجع دعاوی")}
        }
        OutlinedButton(enabled=text.isNotBlank(),onClick={brief=CaseMatcher.draftBrief(text,matches)}) {Text("ساخت پیش‌نویس لایحه")}
        Text("روش کار: شناسایی شناسه‌ها، تاریخ‌ها، ارجاع به مواد قانونی و واژگان مشترک. این موارد سرنخ قابل راستی‌آزمایی‌اند، نه اثبات رابطه حقوقی یا تعارض قطعی.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!analysisRan) Text("برای تحلیل متن، شرح را وارد کنید؛ برای مقایسه اسناد ذخیره‌شده، دست‌کم دو سند یا رکورد لازم است.",style=MaterialTheme.typography.bodySmall)
        if(analysisRan && findings.isEmpty()) Text("با الگوهای فعلی، پیوند قابل‌بررسی‌ای پیدا نشد. این نتیجه به معنای نبود ارتباط حقوقی نیست.",style=MaterialTheme.typography.bodySmall)
        findings.forEach {finding ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(finding.title,style=MaterialTheme.typography.titleSmall)
                    SelectionContainer {Text(finding.detail,style=MaterialTheme.typography.bodyMedium)}
                    Text("منابع: ${finding.sources}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(referenceSearchRan && matches.isEmpty()) {
            Text("در مرجع دعاوی موجود، تطبیق کلیدواژه‌ای پیدا نشد؛ کامل‌بودن مجموعه قوانین تأیید نشده است.",style=MaterialTheme.typography.bodySmall)
        }
        matches.take(5).forEach {m ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("${m.claim.title} • امتیاز کلیدواژه‌ای ${m.score}",style=MaterialTheme.typography.titleSmall)
                    Text(m.claim.articles,style=MaterialTheme.typography.bodySmall)
                    Text("مرجع: ${m.claim.authority}",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        brief?.let {
            Text("پیش‌نویس اولیه؛ ارسال‌نشده و نیازمند بازبینی وکیل",style=MaterialTheme.typography.titleSmall)
            Card(Modifier.fillMaxWidth()) {SelectionContainer {Text(it,modifier=Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)}}
        }
    }
}

private fun analyzeRelationships(query:String,workspace:Workspace):List<RelationshipFinding> {
    val sources=buildList {
        workspace.evidence.forEach { add(SourceText(it.title,it.text)) }
        workspace.records.forEach {record ->
            val kind=if(record.kind.name=="NOTICE") "ابلاغیه" else "پرونده"
            add(SourceText("$kind ${record.noticeNumber.ifBlank {record.caseNumber}}",record.text+"\n"+record.evidence.joinToString("\n")))
        }
    }
    val input=normalize(query)
    if(input.isBlank()) return analyzeSavedConnections(sources)
    val findings=linkedMapOf<String,RelationshipFinding>()
    val identifier=Regex("(?<![0-9])[0-9]{16,20}(?![0-9])")
    val identifiers=identifier.findAll(input).map {it.value}.toSet()
    for(id in identifiers) {
        val matches=sources.filter {identifier.containsMatchIn(normalize(it.text)) && normalize(it.text).contains(id)}
        if(matches.isNotEmpty()) findings["شناسه:$id"]=RelationshipFinding("شناسه مشترک: $id","شناسه در متن واردشده و منبع ذخیره‌شده تکرار شده است؛ نقش شناسه را با سند اصلی کنترل کنید.",matches.take(6).joinToString("، ") {it.title})
    }
    val datePattern=Regex("(?<![0-9])(?:13|14)[0-9]{2}[/-][0-9]{1,2}[/-][0-9]{1,2}(?![0-9])")
    val dates=datePattern.findAll(input).map {it.value}.toSet()
    for(date in dates) {
        val matches=sources.filter {datePattern.findAll(normalize(it.text)).any {hit->hit.value==date}}
        if(matches.isNotEmpty()) findings["تاریخ:$date"]=RelationshipFinding("تاریخ مشترک: $date","این تاریخ در متن واردشده و سند ذخیره‌شده تکرار شده است. نوع تاریخ (صدور، ابلاغ، جلسه یا قرارداد) را جداگانه بررسی کنید.",matches.take(6).joinToString("، ") {it.title})
    }
    val articlePattern=Regex("ماده\\s*[0-9]+(?:\\s*(?:مکرر|الحاقی))?")
    val articles=articlePattern.findAll(input).map {normalize(it.value)}.toSet()
    for(article in articles) {
        val matches=sources.filter {articlePattern.findAll(normalize(it.text)).any {hit->normalize(hit.value)==article}}
        if(matches.isNotEmpty()) findings["ماده:$article"]=RelationshipFinding("ارجاع قانونی تکرارشده: $article","ارجاع به ماده در متن و سند ذخیره‌شده تکرار شده است؛ متن حاکم و ارتباط حقوقی باید جداگانه ارزیابی شود.",matches.take(6).joinToString("، ") {it.title})
    }
    val queryWords=significantWords(input)
    for(source in sources) {
        val shared=(queryWords intersect significantWords(source.text)).take(8)
        if(shared.size>=4) findings["واژگان:${source.title}"]=RelationshipFinding("هم‌پوشانی موضوعی با «${source.title}»","واژگان مشترک: ${shared.joinToString("، ")}. این فقط قرابت متنی است و به‌تنهایی ارتباط پرونده‌ها را ثابت نمی‌کند.",source.title)
    }
    return findings.values.take(50)
}

private fun analyzeSavedConnections(sources:List<SourceText>):List<RelationshipFinding> {
    if(sources.size<2) return emptyList()
    val findings=mutableListOf<RelationshipFinding>()
    fun collect(pattern:Regex):Map<String,Set<String>> {
        val groups=linkedMapOf<String,MutableSet<String>>()
        sources.forEach {source->
            pattern.findAll(normalize(source.text)).map {normalize(it.value)}.toSet().forEach {value->
                groups.getOrPut(value){linkedSetOf()}.add(source.title)
            }
        }
        return groups.filterValues {it.size>=2}.mapValues {it.value.toSet()}
    }
    collect(Regex("(?<![0-9])[0-9]{16,20}(?![0-9])")).entries.take(20).forEach { (id,titles) ->
        findings.add(RelationshipFinding("شناسه مشترک: $id","یک شناسه دقیق در چند منبع ذخیره‌شده پیدا شد؛ پیشوند و نوع شناسه را با نسخه اصلی تطبیق دهید.",titles.take(6).joinToString("، ")))
    }
    collect(Regex("(?<![0-9])(?:13|14)[0-9]{2}[/-][0-9]{1,2}[/-][0-9]{1,2}(?![0-9])")).entries.take(20).forEach { (date,titles) ->
        findings.add(RelationshipFinding("تاریخ تکرارشده: $date","تاریخ مشترک می‌تواند نقطه اتصال زمانی باشد؛ نوع و رویداد مربوط به تاریخ را کنترل کنید.",titles.take(6).joinToString("، ")))
    }
    collect(Regex("ماده\\s*[0-9]+(?:\\s*(?:مکرر|الحاقی))?")).entries.take(20).forEach { (article,titles) ->
        findings.add(RelationshipFinding("ارجاع قانونی مشترک: $article","این ارجاع در چند منبع دیده شد؛ ارتباط حقوقی و نسخه معتبر قانون به‌صورت مستقل بررسی شود.",titles.take(6).joinToString("، ")))
    }
    val termGroups=linkedMapOf<String,MutableSet<String>>()
    sources.forEach {source->significantWords(source.text).forEach {word->termGroups.getOrPut(word){linkedSetOf()}.add(source.title)}}
    val commonTerms=termGroups.filterValues {it.size>=2}.entries.sortedByDescending {it.value.size}.take(12)
    if(commonTerms.isNotEmpty()) {
        val detail=commonTerms.joinToString("؛ ") { (term,titles)->"$term (${titles.size} منبع)" }
        val sourceNames=commonTerms.flatMap {it.value}.distinct().take(8).joinToString("، ")
        findings.add(RelationshipFinding("واژگان مشترک در اسناد","واژگان پرتکرار بین منابع: $detail. اشتراک واژگانی به‌تنهایی رابطه حقوقی را اثبات نمی‌کند.",sourceNames))
    }
    return findings.take(50)
}

private fun significantWords(value:String):Set<String> {
    val stop=setOf("این","آن","برای","از","به","با","در","را","که","و","یا","اگر","اما","باشد","بود","شده","ماده","شماره","ابلاغیه","پرونده","سند","خواهان","خوانده","اینجانب","مربوط","مطابق","بوده","نسبت","موضوع","خواسته","میباشد","باشد")
    return Regex("[\\p{L}]{4,}").findAll(normalize(value).lowercase()).map {it.value}.filter {it !in stop}.toSet()
}
