package com.bixbyx.assistant.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

enum class AttKind { Image, Text, Pdf, Other }

/** What a chat message remembers about an attachment. The content itself is only used for the turn it was sent in. */
data class AttachmentRef(val name: String, val kind: AttKind)

/** A file the user attached. Images and PDF pages go to the model as pictures, text files go in the prompt. */
data class Attachment(
    val id: String, val name: String, val kind: AttKind, val path: String,
    val text: String? = null, val images: List<String> = emptyList(),
)

object Attachments {
    private val textExt = setOf("txt", "md", "json", "csv", "xml", "html", "htm", "log", "kt", "java", "py", "js", "ts", "c", "cpp", "h", "rs", "go",
        "sh", "yaml", "yml", "toml", "ini", "sql", "gradle", "properties", "tsv", "srt")

    /** Copies the file into the workspace (so the AI can read it again later) and prepares it for the model. Blocking. */
    fun load(ctx: Context, uri: Uri): Attachment {
        val cr = ctx.contentResolver
        var name = "file"
        var size = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) { name = c.getString(0) ?: name; size = c.getLong(1) }
        }
        require(size < 0 || size <= 25_000_000) { "the file is larger than 25 MB" }
        val mime = cr.getType(uri).orEmpty()
        val id = System.nanoTime().toString(36)
        val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).ifBlank { "file" }
        val dir = File(Tools.root, "attachments").apply { mkdirs() }
        val dest = File(dir, "$id-$safe")
        (cr.openInputStream(uri) ?: error("cannot open the file")).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        val ext = safe.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> Attachment(id, safe, AttKind.Image, dest.path, images = listOfNotNull(jpeg(dest)))
            mime == "application/pdf" || ext == "pdf" -> Attachment(id, safe, AttKind.Pdf, dest.path, images = pdfPages(dest))
            mime.startsWith("text/") || ext in textExt || mime == "application/json" || mime == "application/xml" ->
                Attachment(id, safe, AttKind.Text, dest.path, text = runCatching { dest.readText().take(30_000) }.getOrNull())
            else -> Attachment(id, safe, AttKind.Other, dest.path)
        }
    }

    private fun b64(bmp: Bitmap, quality: Int): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun jpeg(f: File, maxEdge: Int = 1280): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
        var bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rot = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f }
        if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        val s = maxEdge.toFloat() / max(bmp.width, bmp.height)
        if (s < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        b64(bmp, 75)
    }.getOrNull()

    /** The first pages of a PDF as pictures. */
    private fun pdfPages(f: File, max: Int = 3): List<String> = runCatching {
        val out = ArrayList<String>()
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                for (i in 0 until minOf(r.pageCount, max)) {
                    r.openPage(i).use { p ->
                        val w = 900
                        val h = (w.toFloat() * p.height / p.width).toInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out += b64(bmp, 70)
                    }
                }
            }
        }
        out
    }.getOrDefault(emptyList())
}
