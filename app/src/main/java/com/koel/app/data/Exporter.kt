package com.koel.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Copies a finished recording into the public Download/Koel folder so it shows up in
 * Files / music players. Uses MediaStore on Android 10+ (no storage permission needed).
 */
object Exporter {

    private const val TAG = "Exporter"
    private const val SUBDIR = "Koel"
    private const val MIME = "audio/mp4"

    fun export(context: Context, file: File): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) exportMediaStore(context, file)
        else exportLegacy(file)
    }.onFailure { Log.e(TAG, "export failed", it) }.getOrDefault(false)

    private fun exportMediaStore(context: Context, file: File): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    @Suppress("DEPRECATION")
    private fun exportLegacy(file: File): Boolean {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUBDIR)
        dir.mkdirs()
        file.copyTo(File(dir, file.name), overwrite = true)
        return true
    }
}
