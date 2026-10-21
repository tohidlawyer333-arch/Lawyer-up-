package com.lawyerup.app.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

data class DocumentText(val text: String, val usedOcr: Boolean, val pages: Int)

class Documents(private val context: Context) {
    // Stage the source incrementally in app-private cache; no arbitrary source-size or page-count ceiling.
    private fun input(uri: Uri): File {
        val file = File.createTempFile("document-import-", ".bin", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)!!.use { source ->
                file.outputStream().buffered().use { target -> source.copyTo(target, 64 * 1024) }
            }
            require(file.length() > 0L) { "فایل خالی است" }
            return file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    private fun isPdf(file: File): Boolean = file.inputStream().use { source ->
        val header = ByteArray(5)
        source.read(header) == 5 && header.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))
    }

    private fun image(file: File): Bitmap {
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, dimensions)
        require(dimensions.outWidth > 0 && dimensions.outHeight > 0) { "تصویر قابل خواندن نیست" }
        var sample = 1
        while(dimensions.outWidth/sample > 2400 || dimensions.outHeight/sample > 2400) sample *= 2
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }))
    }

    private fun ocr(bitmap: Bitmap, includeEnglish: Boolean): String {
        val directory = File(context.filesDir, "ocr/tessdata").apply { mkdirs() }
        // Persian-only is the default: avoid inventing Latin tokens from an unused English language model.
        val languages = if(includeEnglish) listOf("fas", "eng") else listOf("fas")
        for(language in languages) {
            val trainedData = File(directory, "$language.traineddata")
            if(!trainedData.exists()) context.assets.open("ocr/$language.traineddata").use { input -> trainedData.outputStream().use { input.copyTo(it) } }
        }
        val engine = TessBaseAPI()
        try {
            check(engine.init(directory.parent, languages.joinToString("+"))) { "موتور OCR آماده نشد" }
            engine.setImage(bitmap)
            val recognized = engine.utF8Text.orEmpty()
            return if(includeEnglish) recognized else recognized
                .replace(Regex("[A-Za-z]+"), "")
                .replace(Regex("[ \\t]{2,}"), " ")
        } finally { engine.recycle() }
    }

    suspend fun read(uri: Uri, progress: (Int, Int) -> Unit, includeEnglish: Boolean = false): DocumentText = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        val file = input(uri)
        try {
            if(!isPdf(file)) {
                val bitmap = image(file)
                try { return@withContext DocumentText(ocr(bitmap, includeEnglish), true, 1) } finally { bitmap.recycle() }
            }
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                require(!document.isEncrypted) { "فایل رمزدار را ابتدا خودتان باز کنید" }
                val text = PDFTextStripper().getText(document)
                if(text.count { it.isLetterOrDigit() } > document.numberOfPages * 20) {
                    return@withContext DocumentText(text, false, document.numberOfPages)
                }
            }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val result = StringBuilder()
                    for(index in 0 until renderer.pageCount) {
                        coroutineContext.ensureActive()
                        renderer.openPage(index).use { page ->
                            val ratio = minOf(2.5f, 2400f / maxOf(page.width, page.height))
                            val bitmap = Bitmap.createBitmap((page.width*ratio).toInt(), (page.height*ratio).toInt(), Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                result.appendLine(ocr(bitmap, includeEnglish))
                            } finally { bitmap.recycle() }
                        }
                        progress(index+1, renderer.pageCount)
                    }
                    DocumentText(result.toString(), true, renderer.pageCount)
                }
            }
        } finally { file.delete() }
    }

    suspend fun optimize(uri: Uri, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        require(limit in 100_000..25_000_000)
        PDFBoxResourceLoader.init(context)
        val file = input(uri)
        try {
            if(isPdf(file)) {
                if(file.length() <= limit) return@withContext file.readBytes()
                error("برای حفظ متن و امضای احتمالی، PDF بزرگ خودکار بازنویسی نمی‌شود؛ فایل را تقسیم یا نسخه تصویری تأییدشده وارد کنید")
            }
            var bitmap = image(file)
            try {
                for(attempt in 0..4) {
                    coroutineContext.ensureActive()
                    val output = ByteArrayOutputStream()
                    PDDocument().use { document ->
                        val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                        document.addPage(page)
                        val jpeg = JPEGFactory.createFromImage(document, bitmap, 0.8f-attempt*0.1f)
                        PDPageContentStream(document, page).use { it.drawImage(jpeg, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()) }
                        document.save(output)
                    }
                    if(output.size() <= limit) return@withContext output.toByteArray()
                    val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width*.8).toInt().coerceAtLeast(1), (bitmap.height*.8).toInt().coerceAtLeast(1), true)
                    if(scaled !== bitmap) { bitmap.recycle(); bitmap = scaled }
                }
                error("با کیفیت مجاز به سقف انتخاب‌شده نرسید؛ سقف یا سند را تغییر دهید")
            } finally { bitmap.recycle() }
        } finally { file.delete() }
    }
}
