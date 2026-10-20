package com.lawyerup.app.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
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
    private fun input(uri: Uri): ByteArray = context.contentResolver.openInputStream(uri)!!.use { stream ->
        val bytes = stream.readBytes()
        require(bytes.isNotEmpty()) { "فایل خالی است" }
        bytes
    }
    private fun image(bytes: ByteArray): Bitmap {
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
        require(dimensions.outWidth > 0 && dimensions.outHeight > 0) { "تصویر قابل خواندن نیست" }
        var sample = 1
        while(dimensions.outWidth/sample > 2400 || dimensions.outHeight/sample > 2400) sample *= 2
        return requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize = sample }))
    }
    private fun ocr(bitmap: Bitmap, includeEnglish: Boolean): String {
        val directory = File(context.filesDir, "ocr/tessdata").apply { mkdirs() }
        // Persian-only is the default: loading an unnecessary English model can add false Latin tokens to Persian scans.
        val languages = if(includeEnglish) listOf("fas", "eng") else listOf("fas")
        for(language in languages) {
            val file = File(directory,"$language.traineddata")
            if(!file.exists()) context.assets.open("ocr/$language.traineddata").use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        val engine = TessBaseAPI()
        try {
            check(engine.init(directory.parent, languages.joinToString("+"))) { "موتور OCR آماده نشد" }
            engine.setImage(bitmap)
            return engine.utF8Text.orEmpty()
        } finally { engine.recycle() }
    }
    suspend fun read(uri: Uri, progress: (Int, Int) -> Unit, includeEnglish: Boolean = false): DocumentText = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        val bytes=input(uri)
        if(!bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray())) {
            val bitmap=image(bytes)
            try { return@withContext DocumentText(ocr(bitmap, includeEnglish),true,1) } finally { bitmap.recycle() }
        }
        PDDocument.load(bytes).use { document ->
            require(!document.isEncrypted) { "فایل رمزدار را ابتدا خودتان باز کنید" }
            
            val text = PDFTextStripper().getText(document)
            if(text.count { it.isLetterOrDigit() } > document.numberOfPages * 20) return@withContext DocumentText(text,false,document.numberOfPages)
        }
        val file = File.createTempFile("ocr-", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    
                    val result=StringBuilder()
                    for(index in 0 until renderer.pageCount) {
                        coroutineContext.ensureActive()
                        renderer.openPage(index).use { page ->
                            val ratio = minOf(2.5f, 2400f / maxOf(page.width,page.height))
                            val bitmap=Bitmap.createBitmap((page.width*ratio).toInt(),(page.height*ratio).toInt(),Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            try { page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); result.appendLine(ocr(bitmap, includeEnglish)) } finally { bitmap.recycle() }
                        }
                        progress(index+1,renderer.pageCount)
                    }
                    DocumentText(result.toString(),true,renderer.pageCount)
                }
            }
        } finally { file.delete() }
    }
    suspend fun optimize(uri: Uri, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        require(limit in 100_000..25_000_000)
        PDFBoxResourceLoader.init(context)
        val bytes=input(uri)
        if(bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray())) {
            if(bytes.size<=limit) return@withContext bytes
            error("برای حفظ متن و امضای احتمالی، PDF بزرگ خودکار بازنویسی نمی‌شود؛ فایل را تقسیم یا نسخه تصویری تأییدشده وارد کنید")
        }
        var bitmap=image(bytes)
        try {
            for(attempt in 0..4) {
                coroutineContext.ensureActive()
                val output=ByteArrayOutputStream()
                PDDocument().use { document ->
                    val page=PDPage(PDRectangle(bitmap.width.toFloat(),bitmap.height.toFloat()))
                    document.addPage(page)
                    val jpeg=JPEGFactory.createFromImage(document,bitmap,0.8f-attempt*0.1f)
                    PDPageContentStream(document,page).use { it.drawImage(jpeg,0f,0f,bitmap.width.toFloat(),bitmap.height.toFloat()) }
                    document.save(output)
                }
                if(output.size()<=limit) return@withContext output.toByteArray()
                val scaled=Bitmap.createScaledBitmap(bitmap,(bitmap.width*.8).toInt().coerceAtLeast(1),(bitmap.height*.8).toInt().coerceAtLeast(1),true)
                if(scaled !== bitmap) { bitmap.recycle(); bitmap=scaled }
            }
            error("با کیفیت مجاز به سقف انتخاب‌شده نرسید؛ سقف یا سند را تغییر دهید")
        } finally { bitmap.recycle() }
    }
}
