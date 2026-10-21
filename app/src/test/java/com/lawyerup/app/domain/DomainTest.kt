package com.lawyerup.app.domain

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.time.DayOfWeek
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import com.lawyerup.app.data.BackupCipher

class DomainTest {
    private val number="1000000000000001"
    private val noticeNumber="200000000000000001"
    private fun case(fields:Map<String,String> = emptyMap(),caseNumber:String=number,sub:String="1")=CourtRecord(RecordKind.CASE,caseNumber=caseNumber,subNumber=sub,fields=fields)
    private fun notice(caseNumber:String=number)=CourtRecord(RecordKind.NOTICE,caseNumber=caseNumber,noticeNumber=noticeNumber)
    private fun evidence(status:String="")=mapOf("شماره پرونده" to number,"شماره ابلاغیه" to noticeNumber,"نوع اقدام" to "لایحه","وضعیت اقدام" to status)
    private fun page(value:String=number,paging:String="end")=PageRead("https://resultcase.adliran.ir/List",listOf(mapOf("شماره پرونده" to value)),paging,false,false,0,false,"test")

    @Test fun persianAndArabicDigitsNormalize() {assertEquals("1405 1405 ک ی",normalize("۱۴۰۵ ١٤٠٥ ك ي"))}
    @Test fun whitespaceAndJoinersNormalize() {assertEquals("شماره پرونده",normalize("  شماره\u200cپرونده\n"))}
    @Test fun officialHttpsOnly() {assertTrue(officialUrl("https://eblagh1.adliran.ir/ENotice/Index"));assertFalse(officialUrl("http://eblagh1.adliran.ir"))}
    @Test fun hostileHostsRejected() {listOf("https://adliran.ir.attacker.test","https://user@adliran.ir","file:///adliran.ir","https://adliran.ir:444").forEach {assertFalse(it,officialUrl(it))}}
    @Test fun sensitiveQueryIsNotStored() {assertEquals("https://resultcase.adliran.ir/List",safeSource("https://resultcase.adliran.ir/List?token=secret#secret"))}
    @Test fun cardFieldsParseWithoutCaseNumber() {val parsed=Extraction.parse(mapOf("شماره ابلاغیه" to noticeNumber),RecordKind.NOTICE,"https://eblagh1.adliran.ir");assertNotNull(parsed);assertEquals("",parsed!!.caseNumber)}
    @Test fun paginationNeverBecomesCase() {assertNull(Extraction.parse(mapOf("ردیف" to "26","مشاهده" to "25"),RecordKind.CASE,""))}
    @Test fun unlabeledNumberRejected() {assertNull(Extraction.parse(mapOf("متن" to number),RecordKind.CASE,""))}
    @Test fun numberLengthBoundaries() {for(length in listOf(15,21))assertNull(Extraction.parse(mapOf("شماره پرونده" to "1".repeat(length)),RecordKind.CASE,""));for(length in 16..20)assertNotNull(Extraction.parse(mapOf("شماره پرونده" to "1".repeat(length)),RecordKind.CASE,""))}
    @Test fun twoNumbersInOneCellNotGuessed() {assertNull(Extraction.parse(mapOf("شماره پرونده" to "$number 1000000000000002"),RecordKind.CASE,""))}
    @Test fun twoNoticesSameCaseHaveSeparateIdentity() {assertNotEquals(notice().id,notice().copy(noticeNumber="200000000000000002").id)}
    @Test fun caseSubNumbersHaveSeparateIdentity() {assertNotEquals(case(sub="1").id,case(sub="2").id)}
    @Test fun recordJsonRoundTrip() {val value=notice().copy(evidence=listOf("test"),reviewed=true,reviewedAt=5);assertEquals(value,CourtRecord.from(JSONObject(value.json().toString())))}
    @Test fun workJsonRoundTrip() {val task=WorkItem("id","record","title","why",draft="متن");assertEquals(task,WorkItem.from(JSONObject(task.json().toString())))}
    @Test fun missingCaseIsUnknownNotFailedAction() {assertEquals(MatchLevel.MISSING_CASE,Reconciler.match(notice(),emptyList()).level)}
    @Test fun matchingCaseAloneDoesNotProveAction() {assertEquals(MatchLevel.CASE_ONLY,Reconciler.match(notice(),listOf(case())).level)}
    @Test fun exactReferencePreservesEvidence() {val result=Reconciler.match(notice(),listOf(case(evidence())));assertEquals(MatchLevel.EXACT,result.level);assertEquals(1,result.evidence.size)}
    @Test fun observedNoticeIsNotAction() {assertEquals(MatchLevel.EXACT,Reconciler.match(notice(),listOf(case(evidence("مشاهده شده")))).level)}
    @Test fun explicitActionIsCandidateNotManualCompletion() {assertEquals(MatchLevel.ACTION_RECORDED,Reconciler.match(notice(),listOf(case(evidence("ثبت نهایی")))).level)}
    @Test fun negativeStatusPreventsCompletion() {assertEquals(MatchLevel.CONFLICT,Reconciler.match(notice(),listOf(case(evidence("انجام نشده")))).level)}
    @Test fun conflictingCaseStopsMatch() {assertEquals(MatchLevel.CONFLICT,Reconciler.match(notice(),listOf(case(evidence(),"1000000000000002"))).level)}
    @Test fun uniqueExactNoticeReferenceCanLinkMissingCase() {val result=Reconciler.match(notice(""),listOf(case(evidence())));assertEquals(MatchLevel.EXACT,result.level);assertEquals(number,result.caseNumber)}
    @Test fun ambiguousReferencesNotLinked() {assertEquals(MatchLevel.CONFLICT,Reconciler.match(notice(""),listOf(case(evidence()),case(evidence(),"1000000000000002"))).level)}
    @Test fun manualReviewDistinctFromMatching() {assertEquals(MatchLevel.MANUAL,Reconciler.match(notice().copy(reviewed=true),emptyList()).level)}
    @Test fun documentAmbiguousCaseNotGuessed() {val parsed=Extraction.fromDocument("شماره ابلاغیه: $noticeNumber شماره پرونده: $number شماره پرونده: 1000000000000002",RecordKind.NOTICE);assertEquals("",parsed!!.caseNumber)}
    @Test fun unsupportedPageRejected() {assertNull(Extraction.kind("https://sana.adliran.ir/ESana/Index"))}
    @Test fun fieldsReadFromDoubleEncodedWebViewResult() {val raw=JSONObject().put("url","https://eblagh1.adliran.ir").put("rows",JSONArray().put(JSONObject().put("headers",JSONArray(listOf("شماره ابلاغیه"))).put("cells",JSONArray(listOf(noticeNumber)))));assertEquals(noticeNumber,Extraction.read(JSONObject.quote(raw.toString())).rows.single()["شماره ابلاغیه"])}
    @Test fun successfulPagingRequiresEnd()=runTest {var index=0;val pages=listOf(page(paging="next"),page("1000000000000002"));val result=PaginationRunner().run({pages[index]},{ImportReport(1,1,0)},{index++;true},{false},{_,_->});assertTrue(result.success);assertEquals(2,result.pages)}
    @Test fun unknownPagingReportsPartial()=runTest {val result=PaginationRunner().run({page(paging="unknown")},{ImportReport(1,1,0)},{true},{false},{_,_->});assertFalse(result.success);assertEquals(1,result.changed)}
    @Test fun repeatedPageStopsLoop()=runTest {var saves=0;val result=PaginationRunner().run({page(paging="next")},{saves++;ImportReport(1,1,0)},{true},{false},{_,_->});assertFalse(result.success);assertEquals(1,saves)}
    @Test fun cancelledBatchDoesNotRead()=runTest {var reads=0;val result=PaginationRunner().run({reads++;page()},{ImportReport(1,1,0)},{true},{true},{_,_->});assertFalse(result.success);assertEquals(0,reads)}
    @Test fun loginNeverStored()=runTest {var writes=0;val result=PaginationRunner().run({page().copy(login=true)},{writes++;ImportReport(1,1,0)},{true},{false},{_,_->});assertFalse(result.success);assertEquals(0,writes)}
    @Test fun truncatedListNotComplete()=runTest {assertFalse(PaginationRunner().run({page().copy(truncated=true)},{ImportReport(1,1,0)},{true},{false},{_,_->}).success)}
    @Test fun unknownSanaViewNeverImportsRecords()=runTest {
        val unreadableList=PageRead("https://eblagh1.adliran.ir/ENotice/Index",
            listOf(mapOf("شماره ابلاغیه" to noticeNumber)),"end",false,false,0,false,"test")
        var writes=0
        val result=PaginationRunner().run({unreadableList},{writes++;ImportReport(1,1,0)},{true},{false},{_,_->})
        assertFalse(result.success)
        assertEquals(0,writes)
        assertEquals(0,result.pages)
        assertTrue(result.message.contains("عنوان فهرست ابلاغیه"))
        assertTrue(result.message.contains("دریافت کامل تأیید نشده است"))
    }
    @Test fun reportNamesSelectedViewAndDateRange()=runTest {
        val current=page(paging="end").copy(activeView="ابلاغیه‌های مشاهده شده",fromDate="1405/06/29",toDate="1405/07/28")
        val result=PaginationRunner().run({current},{ImportReport(1,1,0)},{true},{false},{_,_->})
        assertTrue(result.success)
        assertTrue(result.message.contains("ابلاغیه‌های مشاهده شده"))
        assertTrue(result.message.contains("1405/06/29 تا 1405/07/28"))
    }
    @Test fun zeroRowsNotSuccess()=runTest {
        val result=PaginationRunner().run({page()},{ImportReport(0,0,1)},{true},{false},{_,_->})
        assertFalse(result.success)
        assertEquals(1,result.pages)
        assertEquals(1,result.rejected)
        assertTrue(result.message.contains("ردشده: 1"))
    }
    @Test fun laterReadFailurePreservesPartialCounts()=runTest {
        var reads=0
        val result=PaginationRunner().run({
            if(reads++==0) page(paging="next") else throw IllegalStateException("synthetic fixture")
        },{ImportReport(1,1,0)},{true},{false},{_,_->})
        assertFalse(result.success)
        assertEquals(1,result.pages)
        assertEquals(1,result.accepted)
        assertEquals(1,result.changed)
        assertTrue(result.message.contains("ردیف معتبر: 1"))
        assertTrue(result.message.contains("IllegalStateException"))
        assertTrue(result.message.contains("دریافت کامل تأیید نشده است"))
    }

    @Test fun calendarRequiresConfirmation() {assertFalse(DeadlineEngine.calculate(LocalDate.of(2026,10,19),7,false,emptySet(),false).verified)}
    @Test fun holidayChainMovesEndOnly() {val start=LocalDate.of(2026,10,19);val result=DeadlineEngine.calculate(start,4,false,setOf(LocalDate.of(2026,10,24)),true);assertEquals(LocalDate.of(2026,10,25),result.tentative);assertEquals(2,result.shiftedDays)}
    @Test fun actionDayExclusionExplicit() {val start=LocalDate.of(2026,10,19);assertEquals(start.plusDays(8),DeadlineEngine.calculate(start,7,true,emptySet(),true,emptySet()).tentative)}
    @Test(expected=IllegalArgumentException::class) fun invalidDurationRejected() {DeadlineEngine.calculate(LocalDate.now(),0,false,emptySet(),true)}
    @Test fun suggestionDoesNotAssignInventedDeadline() {val tasks=ActionEngine.suggest(notice().copy(fields=mapOf("موضوع" to "اخطار رفع نقص")));assertTrue(tasks.isNotEmpty());assertTrue(tasks.all {!it.dateConfirmed&&it.due.isEmpty()})}
    @Test fun draftNotReceiptOrCompletedTask() {val task=WorkItem("a","b","اعتراض","why");assertTrue(ActionEngine.draft(task,null).contains("ارسال نشده"));assertFalse(task.done)}
    @Test fun feesUseEnteredRateAndExactMoney() {assertEquals(BigDecimal("50000"),FeeCalculator.percent(BigDecimal("1000000"),BigDecimal("5")))}
    @Test(expected=IllegalArgumentException::class) fun invalidFeeRejected() {FeeCalculator.percent(BigDecimal("-1"),BigDecimal("5"))}
    @Test fun backupRoundTrip() {val phrase="a very long phrase".toCharArray();val bytes=BackupCipher.encrypt("محرمانه",phrase);assertEquals("محرمانه",BackupCipher.decrypt(bytes,phrase))}
    @Test fun wrongBackupPasswordFails() {val bytes=BackupCipher.encrypt("secret","long password one".toCharArray());assertTrue(runCatching {BackupCipher.decrypt(bytes,"long password two".toCharArray())}.isFailure)}
    @Test fun backupTamperFails() {val bytes=BackupCipher.encrypt("secret","long password one".toCharArray());bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();assertTrue(runCatching {BackupCipher.decrypt(bytes,"long password one".toCharArray())}.isFailure)}
}
