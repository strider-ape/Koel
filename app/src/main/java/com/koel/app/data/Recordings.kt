package com.koel.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A finished recording on disk. */
data class Recording(
    val file: File,
    val displayName: String,
    val number: String,
    val incoming: Boolean,
    val startedAt: Long,
    val durationMs: Long,
) {
    val sizeBytes: Long get() = file.length()
}

/**
 * Where recordings live and how they are named. Files are kept in the app's own
 * external files dir (no storage permission needed, removed on uninstall).
 */
object Recordings {

    private val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }

    /**
     * Builds a pending file for a call. [contactName] may be null (unknown number).
     * Duration is appended by renaming on stop, so the name is provisional here.
     */
    fun newFile(context: Context, number: String?, contactName: String?, incoming: Boolean): File {
        val who = (contactName ?: number ?: "unknown").sanitize()
        val dir = if (incoming) "in" else "out"
        val name = "${stamp.format(Date())}__${dir}__${who}.m4a"
        return File(dir(context), name)
    }

    /**
     * Renames a provisional recording to its final name once the call is known.
     * Keeps the original timestamp prefix; returns the new file (or the old one on failure).
     */
    fun finalize(file: File, info: CallInfo?): File {
        if (info == null) return file
        val timePart = file.nameWithoutExtension.substringBefore("__")
            .ifBlank { stamp.format(Date(file.lastModified())) }
        val dir = if (info.incoming) "in" else "out"
        val who = (info.name ?: info.number ?: "unknown").sanitize()
        val target = File(file.parentFile, "${timePart}__${dir}__${who}.m4a")
        return if (file.renameTo(target)) target else file
    }

    fun list(context: Context): List<Recording> =
        dir(context).listFiles { f -> f.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.toRecording() }
            ?: emptyList()

    private fun File.toRecording(): Recording {
        // name: yyyy-MM-dd_HH-mm-ss__in__Name.m4a
        val parts = nameWithoutExtension.split("__")
        val incoming = parts.getOrNull(1) == "in"
        val who = parts.getOrNull(2)?.replace('_', ' ') ?: "Unknown"
        return Recording(
            file = this,
            displayName = who,
            number = who,
            incoming = incoming,
            startedAt = lastModified(),
            durationMs = 0,
        )
    }

    private fun String.sanitize(): String =
        replace(Regex("[^A-Za-z0-9+._-]"), "_").take(40).ifBlank { "unknown" }
}
