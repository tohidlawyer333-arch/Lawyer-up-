package com.lawyerup.app.data

/** Local-only checks before the user chooses an attachment for a portal upload. */
object AttachmentPolicy {
    private val allowed = setOf("pdf", "tif", "tiff", "jpg", "jpeg", "png")
    fun validate(fileName: String, sizeBytes: Long, maxBytes: Long = 2_000_000L): Result {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext !in allowed -> Result(false, "فرمت مجاز: PDF، TIFF، JPG یا PNG")
            sizeBytes <= 0 -> Result(false, "حجم فایل قابل تشخیص نیست")
            sizeBytes > maxBytes -> Result(false, "حجم فایل از سقف تنظیم‌شده بیشتر است؛ فشرده‌سازی یا انتخاب فایل دیگر لازم است")
            else -> Result(true, "فایل برای بررسی آماده است؛ پذیرش نهایی را خود سامانه تعیین می‌کند")
        }
    }
    data class Result(val accepted: Boolean, val message: String)
}
