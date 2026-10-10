package com.grandsphere.fiche.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PosterStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    fun fileFor(titleId: Long): File = File(File(context.filesDir, "posters"), "$titleId.jpg")

    suspend fun download(titleId: Long, url: String?, replace: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        val existing = fileFor(titleId)
        if (!replace && existing.exists() && existing.length() > 0) return@withContext existing.absolutePath
        if (replace && existing.exists()) existing.delete()
        runCatching {
            val request = Request.Builder().url(url).build()
            val bytes = client.newCall(request).execute().use { it.body?.bytes() } ?: return@runCatching null
            writeCompressed(titleId, bytes)
        }.getOrNull()
    }

    suspend fun importFromUri(titleId: Long, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            writeCompressed(titleId, bytes)
        }.getOrNull()
    }

    suspend fun delete(titleId: Long) = withContext(Dispatchers.IO) {
        fileFor(titleId).delete()
    }

    private fun writeCompressed(titleId: Long, bytes: ByteArray): String? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scaled = scale(bitmap, 400)
        val file = fileFor(titleId)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        if (scaled !== bitmap) scaled.recycle()
        return file.absolutePath
    }

    private fun scale(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val height = (source.height.toFloat() / source.width * maxWidth).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, maxWidth, height, true)
    }
}
