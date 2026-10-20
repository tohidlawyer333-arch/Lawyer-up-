package com.lawyerup.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lawyerup.app.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class RepositoryTest {
    private lateinit var database:VaultDatabase
    private lateinit var repository:WorkspaceRepository
    private val notice=CourtRecord(RecordKind.NOTICE,"1000000000000001","200000000000000001",fields=mapOf("موضوع" to "رفع نقص"))
    @Before fun setup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        database=Room.inMemoryDatabaseBuilder(context,VaultDatabase::class.java).allowMainThreadQueries().build()
        repository=WorkspaceRepository(context,database,object:TextCipher {override fun seal(value:String,id:String)=value;override fun open(value:String,id:String)=value})
    }
    @After fun close() {database.close()}
    @Test fun duplicateImportIsUnchangedAndTaskIdempotent()=runTest {
        assertEquals(1,repository.importRecords(listOf(notice)).changed)
        assertEquals(0,repository.importRecords(listOf(notice)).changed)
        assertEquals(1,repository.snapshot().notices.size)
        assertEquals(1,repository.snapshot().tasks.size)
    }
    @Test fun updatedImportPreservesManualReview()=runTest {
        repository.importRecords(listOf(notice));repository.review(notice.id,true)
        repository.importRecords(listOf(notice.copy(fields=mapOf("موضوع" to "رفع نقص جدید"))))
        assertTrue(repository.snapshot().notices.single().reviewed)
    }
    @Test fun separateSubNumbersAndHistoryAreRetained()=runTest {
        val base=CourtRecord(RecordKind.CASE,"1000000000000001",subNumber="1",fields=mapOf("شماره ابلاغیه" to notice.noticeNumber))
        repository.importRecords(listOf(base,base.copy(fields=base.fields+mapOf("شرح اقدام" to "لایحه")),base.copy(subNumber="2")))
        val cases=repository.snapshot().cases
        assertEquals(2,cases.size);assertEquals(2,cases.single {it.subNumber=="1"}.evidence.size)
    }
    @Test fun taskCompletionRequiresReceipt()=runTest {
        assertTrue(runCatching {repository.saveTask(WorkItem("id","","title","why",done=true))}.isFailure)
    }
    @Test fun restoreMergesWithoutErasingExisting()=runTest {
        repository.importRecords(listOf(notice));val backup=repository.export()
        repository.importRecords(listOf(notice.copy(noticeNumber="200000000000000002")))
        repository.restore(backup);assertEquals(2,repository.snapshot().notices.size)
    }
    @Test fun failedRestoreRollsBackAllWrites()=runTest {
        repository.importRecords(listOf(notice))
        val json=org.json.JSONObject(repository.export())
        json.getJSONArray("records").getJSONObject(0).put("notice","200000000000000003")
        json.put("tasks",org.json.JSONArray().put(WorkItem("bad","","title","why",done=true).json()))
        assertTrue(runCatching {repository.restore(json.toString())}.isFailure)
        assertEquals(listOf(notice.noticeNumber),repository.snapshot().notices.map {it.noticeNumber})
    }
}
