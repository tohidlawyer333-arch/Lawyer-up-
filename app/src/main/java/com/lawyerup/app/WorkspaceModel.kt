package com.lawyerup.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lawyerup.app.data.Workspace
import com.lawyerup.app.data.WorkspaceRepository
import com.lawyerup.app.domain.*
import com.lawyerup.app.notifications.DeadlineReminderScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class WorkspaceModel(application: Application): AndroidViewModel(application) {
    val repository=WorkspaceRepository(application)
    val message=MutableStateFlow("")
    val storageError=MutableStateFlow(false)
    val ready=MutableStateFlow(false)
    val busy=MutableStateFlow(false)
    val workspace=repository.watch().onEach { ready.value=true }.catch { storageError.value=true; message.value="حافظه امن قابل خواندن نیست؛ داده‌ها حذف نشده‌اند. پیش از هر اقدام پشتیبان را بررسی کنید." }
        .stateIn(viewModelScope,SharingStarted.Eagerly,Workspace())
    fun perform(action: suspend ()->Unit) {
        if(busy.value || storageError.value) return
        viewModelScope.launch {
            busy.value=true
            try { withContext(Dispatchers.IO) { action() } }
            catch(error:CancellationException){throw error}
            catch(error:Exception){message.value=error.message?.takeIf { it.length<180 && !it.contains("file:") } ?: "عملیات ناموفق بود؛ دوباره بررسی کنید"}
            finally {busy.value=false}
        }
    }
    fun review(record: CourtRecord,value: Boolean)=perform { repository.review(record.id,value);message.value="وضعیت بررسی ذخیره شد؛ اقدامی در ثنا ثبت نشده است" }
    fun save(task: WorkItem)=perform { repository.saveTask(task);DeadlineReminderScheduler.schedule(getApplication(),task);message.value="پیگیری ذخیره شد" }
    fun draft(task: WorkItem)=perform {
        val record=workspace.value.records.find {it.id==task.recordId}
        repository.saveTask(task.copy(draft=ActionEngine.draft(task,record)))
        message.value="پیش‌نویس در پیگیری ذخیره شد؛ برای ارسال باید بازبینی شود"
    }
}
