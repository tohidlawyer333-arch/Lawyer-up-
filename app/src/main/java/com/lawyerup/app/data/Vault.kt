package com.lawyerup.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.*
import com.lawyerup.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "vault")
data class VaultRow(@PrimaryKey val id: String, val kind: String, val payload: String, val updated: Long)

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault ORDER BY updated DESC") fun watch(): Flow<List<VaultRow>>
    @Query("SELECT * FROM vault ORDER BY updated DESC") suspend fun all(): List<VaultRow>
    @Query("SELECT * FROM vault WHERE id = :id") suspend fun get(id: String): VaultRow?
    @Upsert suspend fun put(rows: List<VaultRow>)
    @Query("DELETE FROM vault WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [VaultRow::class], version = 1, exportSchema = true)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun dao(): VaultDao
    companion object {
        @Volatile private var instance: VaultDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, VaultDatabase::class.java, "workspace.db").build().also { instance = it }
        }
    }
}

interface TextCipher {
    fun seal(value: String, id: String): String
    fun open(value: String, id: String): String
}

class CryptoBox : TextCipher {
    private val alias = "lawyerup.workspace.v1"
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    override fun seal(value: String, id: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(id.toByteArray())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    override fun open(value: String, id: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(id.toByteArray())
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}

data class Workspace(val records: List<CourtRecord> = emptyList(), val tasks: List<WorkItem> = emptyList(), val reports: List<JSONObject> = emptyList()) {
    val cases get() = records.filter { it.kind == RecordKind.CASE }
    val notices get() = records.filter { it.kind == RecordKind.NOTICE }
}

class WorkspaceRepository(context: Context, private val database: VaultDatabase = VaultDatabase.get(context), private val crypto: TextCipher = CryptoBox()) {
    private val dao = database.dao()
    private fun decode(rows: List<VaultRow>): Workspace {
        val records = mutableListOf<CourtRecord>()
        val tasks = mutableListOf<WorkItem>()
        val reports = mutableListOf<JSONObject>()
        rows.forEach { row ->
            val json = JSONObject(crypto.open(row.payload, row.id))
            when(row.kind) {
                "record" -> records.add(CourtRecord.from(json))
                "task" -> tasks.add(WorkItem.from(json))
                "report" -> reports.add(json)
            }
        }
        return Workspace(records, tasks, reports)
    }
    fun watch(): Flow<Workspace> = dao.watch().map(::decode)
    suspend fun snapshot(): Workspace = decode(dao.all())
    private fun row(id: String, kind: String, json: JSONObject) = VaultRow(id, kind, crypto.seal(json.toString(), id), System.currentTimeMillis())

    suspend fun ingest(page: PageRead): ImportReport {
        require(officialUrl(page.url) && !page.login && !page.loading)
        val kind = Extraction.kind(page.url) ?: error("این بخش فهرست پرونده یا ابلاغ نیست")
        val parsed = page.rows.mapNotNull { Extraction.parse(it, kind, page.url) }
        return importRecords(parsed, page.rows.size - parsed.size)
    }

    suspend fun importRecords(records: List<CourtRecord>, rejected: Int = 0): ImportReport = database.withTransaction {
        var changed = 0
        records.forEach { incoming ->
            val previous = dao.get(incoming.id)?.let { CourtRecord.from(JSONObject(crypto.open(it.payload, it.id))) }
            val mergedFields = previous?.fields.orEmpty().toMutableMap().apply { incoming.fields.forEach { (key, value) -> if(value.isNotBlank()) put(key, value) } }
            val evidence = (previous?.evidence.orEmpty() + incoming.evidence + if(incoming.kind == RecordKind.CASE) listOf(incoming.text) else emptyList()).distinct()
            require(evidence.size <= 2000) { "سقف گردش پرونده رسید؛ بسته را تقسیم کنید" }
            val merged = incoming.copy(caseNumber = incoming.caseNumber.ifBlank { previous?.caseNumber.orEmpty() },
                fields = mergedFields, evidence = evidence, reviewed = previous?.reviewed ?: incoming.reviewed,
                reviewedAt = previous?.reviewedAt ?: incoming.reviewedAt)
            if(previous == null || previous.copy(importedAt = merged.importedAt) != merged) {
                dao.put(listOf(row(merged.id, "record", merged.json())))
                changed++
            }
            for(task in ActionEngine.suggest(merged)) {
                if(dao.get(task.id) == null) dao.put(listOf(row(task.id, "task", task.json())))
            }
        }
        ImportReport(records.map { it.id }.distinct().size, changed, rejected)
    }

    private fun bufferId(key: String): String {
        require(key in setOf("ocr", "ocr-undo", "internal-clipboard")) { "شناسه حافظه متنی معتبر نیست" }
        return "buffer:$key"
    }

    suspend fun readTextBuffer(key: String): String {
        val id = bufferId(key)
        val stored = dao.get(id) ?: return ""
        if (stored.kind != "text-buffer") return ""
        return JSONObject(crypto.open(stored.payload, id)).optString("value", "")
    }

    suspend fun writeTextBuffer(key: String, value: String) = database.withTransaction {
        val id = bufferId(key)
        dao.put(listOf(row(id, "text-buffer", JSONObject().put("value", value))))
    }

    suspend fun deleteTextBuffer(key: String) = database.withTransaction {
        dao.delete(bufferId(key))
    }

    suspend fun review(id: String, reviewed: Boolean) = database.withTransaction {
        val stored = dao.get(id) ?: error("رکورد پیدا نشد")
        val record = CourtRecord.from(JSONObject(crypto.open(stored.payload, id)))
        dao.put(listOf(row(id, "record", record.copy(reviewed = reviewed, reviewedAt = if(reviewed) System.currentTimeMillis() else null).json())))
    }
    suspend fun saveTask(task: WorkItem) = database.withTransaction {
        require(task.id.matches(Regex("[a-f0-9]{64}"))) { "شناسه پیگیری معتبر نیست" }
        require(dao.get(task.id)?.kind in listOf(null, "task"))
        require(dao.get(task.recordId)?.kind == "record") { "پرونده یا ابلاغ مرتبط پیدا نشد" }
        require(task.due.isEmpty() || runCatching { java.time.LocalDate.parse(task.due) }.isSuccess)
        require(!task.dateConfirmed || task.due.isNotBlank())
        require(!task.done || task.receipt.isNotBlank()) { "برای ثبت انجام اقدام، یادداشت یا رسید لازم است" }
        dao.put(listOf(row(task.id, "task", task.copy(updatedAt = System.currentTimeMillis()).json())))
    }
    suspend fun report(report: SyncReport, host: String) {
        val json = JSONObject().put("at", System.currentTimeMillis()).put("ok", report.success)
            .put("pages", report.pages).put("accepted", report.accepted).put("changed", report.changed)
            .put("rejected", report.rejected).put("message", report.message).put("host", host)
        dao.put(listOf(row("sync-report", "report", json)))
    }
    suspend fun export(): String {
        val data = snapshot()
        return JSONObject().put("format", 1).put("records", JSONArray(data.records.map { it.json() }))
            .put("tasks", JSONArray(data.tasks.map { it.json() })).toString()
    }
    suspend fun restore(text: String) = database.withTransaction {
        require(text.length <= 20_000_000)
        val json = JSONObject(text)
        require(json.getInt("format") == 1)
        val records = json.getJSONArray("records")
        val tasks = json.getJSONArray("tasks")
        require(records.length() <= 20000 && tasks.length() <= 50000)
        val parsed = (0 until records.length()).map { CourtRecord.from(records.getJSONObject(it)) }
        parsed.forEach { require((if(it.kind == RecordKind.NOTICE) it.noticeNumber else it.caseNumber).matches(Regex("[0-9]{16,20}"))) }
        val newer = parsed.filter { record ->
            val existing = dao.get(record.id)?.let { CourtRecord.from(JSONObject(crypto.open(it.payload, it.id))) }
            existing == null || existing.importedAt < record.importedAt
        }
        importRecords(newer)
        (0 until tasks.length()).map { WorkItem.from(tasks.getJSONObject(it)) }.forEach { task ->
            val current = dao.get(task.id)?.let { WorkItem.from(JSONObject(crypto.open(it.payload, it.id))) }
            if(current == null || current.updatedAt < task.updatedAt) saveTask(task)
        }
    }
}
