package com.lawyerup.app.web

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.net.http.SslError
import android.webkit.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lawyerup.app.domain.*
import com.lawyerup.app.data.WorkspaceRepository
import com.lawyerup.app.security.SanaCredentialVault
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class PortalController(context: Context, private val repository: WorkspaceRepository) {
    var status by mutableStateOf("ورود به سامانه را خودتان تکمیل کنید؛ رمز موقت ذخیره نمی‌شود")
        private set
    var loading by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var url by mutableStateOf("")
        private set
    private var failed = false
    private var cancelled = false
    private var disposed = false
    private var epoch = 0
    private var lastPage: PageRead? = null
    var pendingDownload by mutableStateOf<String?>(null)
        private set
    private val snapshotScript = context.assets.open("sana/snapshot.js").bufferedReader().use { it.readText() }
    private val nextScript = context.assets.open("sana/next.js").bufferedReader().use { it.readText() }
    private val interactionGuardScript = context.assets.open("sana/notice_guard.js").bufferedReader().use { it.readText() }
    @SuppressLint("SetJavaScriptEnabled")
    val view = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val allowed = officialUrl(request.url.toString())
                if(!allowed) status = "نشانی خارج از دامنه امن عدل ایران باز نشد"
                return !allowed
            }
            override fun onPageStarted(view: WebView, location: String, favicon: android.graphics.Bitmap?) {
                if(disposed) return
                loading = true; failed = false; this@PortalController.url = location; epoch++
            }
            override fun onPageFinished(view: WebView, location: String) {
                if(disposed) return
                loading = false; this@PortalController.url = location
                if(!failed && !busy) status = "پس از بازکردن فهرست، دریافت و تطبیق را بزنید"
                if(officialUrl(location)) view.evaluateJavascript(interactionGuardScript, null)
                CookieManager.getInstance().flush()
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if(request.isForMainFrame) { failed = true; loading = false; status = "بارگذاری ناموفق؛ کد ${error.errorCode}" }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, error: WebResourceResponse) {
                if(request.isForMainFrame) { failed = true; loading = false; status = "خطای سرویس: ${error.statusCode}" }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); failed = true; loading = false; status = "گواهی امنیتی معتبر نیست؛ اتصال متوقف شد"
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                val current = result ?: return false
                val prefix = "PAYESH_CONFIRM_NOTICE:"
                if(message?.startsWith(prefix) != true) return false
                AlertDialog.Builder(context)
                    .setTitle("تأیید بازکردن ابلاغیه")
                    .setMessage(message.removePrefix(prefix).trim())
                    .setNegativeButton("انصراف") { _, _ -> current.cancel() }
                    .setPositiveButton("ادامه و بازکردن") { _, _ -> current.confirm() }
                    .setOnCancelListener { current.cancel() }
                    .show()
                return true
            }
        }
        setDownloadListener { location, _, _, _, _ ->
            if(officialUrl(location)) pendingDownload=location
            else status="فقط PDF از نشانی HTTPS رسمی قابل دریافت است؛ برای blob از امکانات خود سامانه استفاده کنید"
        }
    }
    fun open(location: String) {
        if(busy || !officialUrl(location)) return
        view.loadUrl(location)
    }
    fun fillSanaCredentials() {
        val credentials = SanaCredentialVault.load(context)
        if (credentials == null) {
            status = "مشخصات ورود رمزگذاری‌شده ذخیره نشده است؛ ابتدا از تنظیمات پایش ذخیره کنید."
            return
        }
        val encoded = org.json.JSONObject()
            .put("nationalCode", credentials.nationalCode)
            .put("personalPassword", credentials.personalPassword)
            .toString()
        val script = """
            (function () {
              const credentials = __CREDENTIALS_JSON__;
              let nationalCount = 0, passwordCount = 0;
              const normalize = value => String(value || '').replace(/[\u200c\u200e\u200f]/g,' ')
                .replace(/[يى]/g,'ی').replace(/ك/g,'ک').replace(/\s+/g,' ').trim().toLowerCase();
              function apply(doc) {
                if (!doc) return;
                const inputs = Array.from(doc.querySelectorAll('input')).filter(input => {
                  const style = doc.defaultView.getComputedStyle(input);
                  return style.display !== 'none' && style.visibility !== 'hidden' && !input.disabled && !input.readOnly;
                });
                function meta(input) {
                  const labels = input.labels ? Array.from(input.labels).map(node => node.innerText || node.textContent || '').join(' ') : '';
                  return normalize([
                    input.name,input.id,input.placeholder,input.getAttribute('aria-label'),
                    input.getAttribute('autocomplete'),labels,input.parentElement && (input.parentElement.innerText || input.parentElement.textContent)
                  ].filter(Boolean).join(' '));
                }
                const national = inputs.find(input => input.type !== 'password' && /کد ملی|کدملی|شناسه ملی|نام کاربری|username|national.?id|national.?code/.test(meta(input)));
                const password = inputs.find(input => input.type === 'password' &&
                  /password|رمز شخصی|رمز عبور/.test(meta(input)) &&
                  !/otp|one.?time|رمز موقت|یک.?بار.?مصرف|captcha|verification|تأیید/.test(meta(input)));
                function setValue(input,value) {
                  if(!input) return false;
                  const proto = input.tagName === 'TEXTAREA' ? doc.defaultView.HTMLTextAreaElement.prototype : doc.defaultView.HTMLInputElement.prototype;
                  const descriptor = Object.getOwnPropertyDescriptor(proto,'value');
                  if(descriptor && descriptor.set) descriptor.set.call(input,value); else input.value=value;
                  input.dispatchEvent(new doc.defaultView.Event('input',{bubbles:true}));
                  input.dispatchEvent(new doc.defaultView.Event('change',{bubbles:true}));
                  return true;
                }
                if(setValue(national,credentials.nationalCode)) nationalCount++;
                if(setValue(password,credentials.personalPassword)) passwordCount++;
                for(const frame of doc.querySelectorAll('iframe,frame')) {
                  try { if(frame.contentDocument) apply(frame.contentDocument); } catch(_) {}
                }
              }
              apply(document);
              return JSON.stringify({national:nationalCount,password:passwordCount});
            })()
        """.trimIndent().replace("__CREDENTIALS_JSON__", encoded)
        view.evaluateJavascript(script) { raw ->
            val result = runCatching {
                val returned = org.json.JSONTokener(raw).nextValue() as? String ?: return@runCatching null
                org.json.JSONObject(returned)
            }.getOrNull()
            val nationalFilled = (result?.optInt("national") ?: 0) > 0
            val passwordFilled = (result?.optInt("password") ?: 0) > 0
            status = when {
                nationalFilled && passwordFilled -> "کد ملی و رمز شخصی در فرم پیدا و پر شدند؛ مقدارها را بررسی کنید و خودتان ادامه ورود را انجام دهید. رمز موقت ذخیره یا ارسال نشده است."
                nationalFilled -> "کد ملی در فرم پر شد؛ فیلد رمز شخصی قابل‌تشخیص نبود. هیچ ورود یا ارسالی انجام نشد."
                passwordFilled -> "فیلد رمز شخصی پر شد؛ فیلد کد ملی قابل‌تشخیص نبود. هیچ ورود یا ارسالی انجام نشد."
                else -> "فیلدهای ورود قابل‌تشخیص نبودند؛ صفحه ورود رسمی را باز کنید. هیچ داده‌ای ارسال نشد."
            }
        }
    }

    fun cancel() { cancelled = true }
    fun dismissDownload() { pendingDownload=null }
    suspend fun download(): ByteArray {
        val location=requireNotNull(pendingDownload)
        return CourtDownload.fetch(location) { destination ->
            withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(destination).orEmpty() }
        }.also { pendingDownload=null }
    }
    fun close() { disposed = true; cancelled = true; view.stopLoading(); view.destroy() }
    private suspend fun evaluate(script: String): String = withTimeout(15000) {
        suspendCancellableCoroutine { continuation ->
            if(disposed) { continuation.cancel(); return@suspendCancellableCoroutine }
            view.evaluateJavascript(script) { raw -> if(continuation.isActive) continuation.resume(raw) }
        }
    }
    private suspend fun read(): PageRead {
        repeat(25) {
            if(cancelled || disposed) throw CancellationException()
            val start = epoch
            val raw = evaluate(snapshotScript)
            val page = Extraction.read(raw)
            if(start == epoch && page.login) return page
            if(start == epoch && !loading && !page.loading && page.rows.isNotEmpty()) { lastPage = page; return page }
            delay(400)
        }
        return Extraction.read(evaluate(snapshotScript)).also { lastPage = it }
    }
    suspend fun sync(allPages: Boolean) {
        if(busy || loading || failed) { status = "ابتدا بارگذاری صفحه را تکمیل یا خطای آن را رفع کنید"; return }
        busy = true; cancelled = false
        val report = try {
            if(allPages) PaginationRunner().run(::read, { page -> withContext(Dispatchers.IO) { repository.ingest(page) } }, {
                val before = lastPage?.rows.toString()
                val clicked = evaluate(nextScript)
                if(clicked != "true") false else {
                    var changed = false
                    for(attempt in 0 until 20) {
                        delay(500)
                        val page = read()
                        if(page.login || page.rows.toString() != before) { changed = true; break }
                    }
                    changed
                }
            }, { cancelled || disposed }) { pages, rows -> status = "$pages صفحه؛ $rows ردیف معتبر؛ در حال دریافت…" }
            else {
                val page = read()
                require(!page.login) { "ورود یا تمدید نشست لازم است" }
                val imported = withContext(Dispatchers.IO) { repository.ingest(page) }
                val complete = imported.accepted > 0 && imported.rejected == 0 && !page.truncated && page.blockedFrames == 0 && page.paging == "end"
                SyncReport(complete, 1, imported.accepted, imported.changed,
                    if(imported.accepted > 0) "${imported.accepted} ردیف معتبر؛ ${imported.rejected} ردیف ردشده؛ ${imported.changed} تغییر؛ فقط صفحه فعلی${if(!complete) "؛ دریافت کامل تأیید نشده است" else ""}" else "هیچ رکورد معتبری از صفحه فعلی استخراج نشد؛ این به معنی خالی بودن کارتابل نیست. گزارش ساختار و فیلترهای ثنا را بررسی کنید.", imported.rejected)
            }
        } catch(_: CancellationException) {
            SyncReport(false, 0, 0, 0, "دریافت متوقف شد؛ صفحات ذخیره‌شده باقی می‌مانند")
        } catch(error: Exception) {
            SyncReport(false, 0, 0, 0, "دریافت ناموفق: ${error.javaClass.simpleName}؛ ورود، ساختار صفحه یا حافظه را بررسی کنید")
        }
        try {
            withContext(NonCancellable + Dispatchers.IO) { repository.report(report, runCatching { java.net.URI(url).host }.getOrNull().orEmpty()) }
            status = report.message
        } catch(_: Exception) {
            status = "ذخیره گزارش ناموفق بود؛ دریافت کامل تأیید نشده است. سلامت حافظه را بررسی کنید"
        } finally { busy = false }
    }

    fun diagnostic(): String = org.json.JSONObject().put("version", "2.0.0").put("host", runCatching { java.net.URI(url).host }.getOrNull())
        .put("busy", busy).put("loading", loading).put("pageError", failed).put("adapter", lastPage?.adapter)
        .put("rows", lastPage?.rows?.size).put("pagination", lastPage?.paging).put("blockedFrames", lastPage?.blockedFrames)
        .put("headings", org.json.JSONArray(lastPage?.rows?.flatMap { it.keys }?.distinct().orEmpty())).toString(2)
}
