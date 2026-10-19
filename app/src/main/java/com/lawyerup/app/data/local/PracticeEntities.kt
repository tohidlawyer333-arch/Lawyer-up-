package com.lawyerup.app.data.local
import androidx.room.*
@Entity(tableName="cases") data class PracticeCase(@PrimaryKey(autoGenerate=true) val id:Long=0,val title:String,val client:String="",val court:String="",val caseNumber:String="",val notes:String="",val updatedAt:Long=System.currentTimeMillis())
@Entity(tableName="notices") data class Notice(@PrimaryKey(autoGenerate=true) val id:Long=0,val caseId:Long,val type:String,val receivedDate:String,val body:String)
@Entity(tableName="deadlines") data class Deadline(@PrimaryKey(autoGenerate=true) val id:Long=0,val caseId:Long,val title:String,val triggerDate:String,val dueDate:String,val completed:Boolean=false,val reminder:Boolean=true,val note:String="")
@Entity(tableName="drafts") data class Draft(@PrimaryKey(autoGenerate=true) val id:Long=0,val caseId:Long?,val title:String,val body:String,val updatedAt:Long=System.currentTimeMillis())
@Dao interface PracticeDao {
 @Query("SELECT * FROM cases ORDER BY updatedAt DESC") fun cases(): kotlinx.coroutines.flow.Flow<List<PracticeCase>>
 @Insert suspend fun addCase(x:PracticeCase):Long; @Update suspend fun updateCase(x:PracticeCase); @Delete suspend fun deleteCase(x:PracticeCase)
 @Query("SELECT * FROM notices WHERE caseId=:id ORDER BY receivedDate DESC") fun notices(id:Long):kotlinx.coroutines.flow.Flow<List<Notice>>
 @Insert suspend fun addNotice(x:Notice); @Delete suspend fun deleteNotice(x:Notice)
 @Query("SELECT * FROM deadlines ORDER BY dueDate") fun deadlines():kotlinx.coroutines.flow.Flow<List<Deadline>>
 @Insert suspend fun addDeadline(x:Deadline); @Update suspend fun updateDeadline(x:Deadline); @Delete suspend fun deleteDeadline(x:Deadline)
 @Query("SELECT * FROM drafts ORDER BY updatedAt DESC") fun drafts():kotlinx.coroutines.flow.Flow<List<Draft>>
 @Insert suspend fun addDraft(x:Draft); @Update suspend fun updateDraft(x:Draft); @Delete suspend fun deleteDraft(x:Draft)
}