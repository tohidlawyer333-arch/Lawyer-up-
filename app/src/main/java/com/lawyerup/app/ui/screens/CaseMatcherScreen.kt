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

    LaunchedEffect(Unit) {
        repository.allClaims().collect { allClaims = it }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("تحلیل پرونده",style=MaterialTheme.typography.headlineSmall)
        Text("منابع ذخیره‌شده: ${workspace.evidence.size} سند متنی • ${workspace.records.size} رکورد پرونده و ابلاغ",style=MaterialTheme.typography.bodySmall)
        Text("متن شرح پرونده را وارد کنید. اسناد OCR که ذخیره کرده‌اید نیز بررسی می‌شوند؛ نیازی نیست شماره پرونده حتماً در متن سند وجود داشته باشد.",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value=text,
            onValueChange={text=it;findings=emptyList();brief=null},
            label={Text("شرح وقایع، ادعاها یا متن انتخابی")},
            modifier=Modifier.fillMaxWidth().heightIn(min=150.dp),
            minLines=5
        )
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(enabled=text.isNotBlank(),onClick={findings=analyzeRelationships(text,workspace)}) {Text("تحلیل ارتباط‌ها")}
            OutlinedButton(enabled=text.isNotBlank(),onClick={matches=CaseMatcher.match(text,allClaims)}) {Text("جست‌وجوی مرجع دعاوی")}
        }
        OutlinedButton(enabled=text.isNotBlank(),onClick={brief=CaseMatcher.draftBrief(text,matches)}) {Text("ساخت پیش‌نویس لایحه")}
        Text("روش کار: شناسایی شناسه‌ها، تاریخ‌ها، ارجاع به مواد قانونی و واژگان مشترک. این‌ها سرنخ قابل راستی‌آزمایی‌اند، نه اثبات رابطه حقوقی یا تعارض قطعی.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(findings.isEmpty() && text.isNotBlank()) Text("برای دیدن نتایج، «تحلیل ارتباط‌ها» را بزنید.",style=MaterialTheme.typography.bodySmall)
        findings.forEach {finding ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(finding.title,style=MaterialTheme.typography.titleSmall)
                    SelectionContainer {Text(finding.detail,style=MaterialTheme.typography.bodyMedium)}
                    Text("منابع: ${finding.sources}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(text.isNotBlank() && findings.isEmpty() && workspace.evidence.isEmpty() && workspace.records.isEmpty()) {
            Text("هنوز سند ذخیره‌شده‌ای برای مقایسه وجود ندارد. از ابزار OCR، متن را ذخیره کنید یا اطلاعات پرونده/ابلاغ را از فهرست ثنا دریافت کنید.",style=MaterialTheme.typography.bodySmall)
        }
        if(matches.isEmpty()) {
            Text("از مرجع دعاویِ موجود، تطبیق کلیدواژه‌ای نمایش داده نشده است؛ کامل‌بودن مجموعه قوانین هنوز تأیید نشده.",style=MaterialTheme.typography.bodySmall)
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
    val input=normalize(query)
    if(input.isBlank()) return emptyList()
    val sources=buildList {
        workspace.evidence.forEach { add(SourceText(it.title,it.text)) }
        workspace.records.forEach {record ->
            val kind=if(record.kind.name=="NOTICE") "ابلاغیه" else "پرونده"
            add(SourceText("$kind ${record.noticeNumber.ifBlank {record.caseNumber}}",record.text+"\n"+record.evidence.joinToString("\n")))
        }
    }
    val findings=linkedMapOf<String,RelationshipFinding>()
    val identifiers=Regex("(?<![0-9])[0-9]{16,20}(?![0-9])").findAll(input).map {it.value}.toSet()
    for(id in identifiers) {
        val matches=sources.filter {normalize(it.text).contains(id)}
        if(matches.isNotEmpty()) {
            val key="شناسه:$id"
            findings[key]=RelationshipFinding("شناسه مشترک: $id","شناسه در متن واردشده و منبع ذخیره‌شده تکرار شده است؛ نقش شناسه را با سند اصلی کنترل کنید.",matches.take(5).joinToString("، ") {it.title})
        }
    }
    val dates=Regex("(?<![0-9])(?:13|14)[0-9]{2}[/-][0-9]{1,2}[/-][0-9]{1,2}(?![0-9])").findAll(input).map {it.value}.toSet()
    for(date in dates) {
        val matches=sources.filter {normalize(it.text).contains(date)}
        if(matches.isNotEmpty()) {
            findings["تاریخ:$date"]=RelationshipFinding("تاریخ مشترک: $date","این تاریخ هم در متن واردشده و هم در سند ذخیره‌شده دیده می‌شود. نوع تاریخ (صدور، ابلاغ، جلسه یا قرارداد) را جداگانه بررسی کنید.",matches.take(5).joinToString("، ") {it.title})
        }
    }
    val articles=Regex("ماده\\s*[0-9]+(?:\\s*(?:مکرر|الحاقی))?").findAll(input).map {normalize(it.value)}.toSet()
    for(article in articles) {
        val matches=sources.filter {Regex(Regex.escape(article),RegexOption.IGNORE_CASE).containsMatchIn(normalize(it.text))}
        if(matches.isNotEmpty()) {
            findings["ماده:$article"]=RelationshipFinding("ارجاع قانونی تکرارشده: $article","تکرار ارجاع به ماده شناسایی شد؛ ارتباط حقوقی و متن حاکم باید جداگانه ارزیابی شود.",matches.take(5).joinToString("، ") {it.title})
        }
    }
    val stop=setOf("این","آن","برای","از","به","با","در","را","که","و","یا","اگر","اما","باشد","بود","شده","می‌شود","ماده","شماره","ابلاغیه","پرونده","سند","خواهان","خوانده","اینجانب","مربوط","مطابق","بوده","نسبت","موضوع","خواسته")
    fun keywords(value:String)=Regex("[\\p{L}]{3,}").findAll(normalize(value).lowercase()).map {it.value}.filter {it !in stop}.toSet()
    val queryWords=keywords(input)
    for(source in sources) {
        val shared=(queryWords intersect keywords(source.text)).take(8)
        if(shared.size>=4) {
            findings["واژگان:${source.title}"]=RelationshipFinding("هم‌پوشانی موضوعی با «${source.title}»","واژگان مشترک: ${shared.joinToString("، ")}. این فقط قرابت متنی است و به‌تنهایی ارتباط پرونده‌ها را ثابت نمی‌کند.",source.title)
        }
    }
    return findings.values.take(50)
}
