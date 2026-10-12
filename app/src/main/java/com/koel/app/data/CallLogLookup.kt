package com.koel.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** What we learned about a call from the system call log. */
data class CallInfo(
    val number: String?,
    val name: String?,
    val incoming: Boolean,
)

/**
 * Reads the most recent call-log entry to label a finished recording.
 * The log row is written by the system when a call ends, so this is called at stop time.
 */
object CallLogLookup {

    private fun has(context: Context, perm: String) =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    /** Latest call on or after [sinceMs] (minus a small slack). Null if unavailable. */
    fun latest(context: Context, sinceMs: Long): CallInfo? {
        if (!has(context, Manifest.permission.READ_CALL_LOG)) return null
        val proj = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
        )
        val sel = "${CallLog.Calls.DATE} >= ?"
        val args = arrayOf((sinceMs - 10_000).toString())
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, proj, sel, args, "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                if (!c.moveToFirst()) return null
                val number = c.getString(0)?.takeIf { it.isNotBlank() }
                val type = c.getInt(1)
                val cached = c.getString(2)?.takeIf { it.isNotBlank() }
                val incoming = type == CallLog.Calls.INCOMING_TYPE ||
                    type == CallLog.Calls.MISSED_TYPE ||
                    type == CallLog.Calls.REJECTED_TYPE
                val name = cached ?: contactName(context, number)
                CallInfo(number, name, incoming)
            }
        }.getOrNull()
    }

    private fun contactName(context: Context, number: String?): String? {
        if (number == null || !has(context, Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        return runCatching {
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
    }
}
