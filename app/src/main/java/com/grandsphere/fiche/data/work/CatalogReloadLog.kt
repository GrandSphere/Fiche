package com.grandsphere.fiche.data.work

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object CatalogReloadLog {
    fun write(context: Context, body: String): String {
        val date = LocalDate.now().toString()
        var name = "fiche-reload-$date.txt"
        if (exists(context, name)) {
            val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
            name = "fiche-reload-$date-$time.txt"
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                values
            )
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(body.toByteArray(Charsets.UTF_8))
                }
                return name
            }
        }
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (dir != null) {
            dir.mkdirs()
            File(dir, name).writeText(body, Charsets.UTF_8)
            return name
        }
        val fallback = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        fallback.mkdirs()
        File(fallback, name).writeText(body, Charsets.UTF_8)
        return name
    }

    private fun exists(context: Context, displayName: String): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(displayName),
                null
            )?.use { return it.moveToFirst() }
        }
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (dir != null && File(dir, displayName).exists()) return true
        val fallback = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        return fallback != null && File(fallback, displayName).exists()
    }
}
