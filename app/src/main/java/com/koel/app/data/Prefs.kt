package com.koel.app.data

import android.content.Context

/** Tiny SharedPreferences wrapper for Koel's settings. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("koel", Context.MODE_PRIVATE)

    var enhanceAudio: Boolean
        get() = sp.getBoolean(KEY_ENHANCE, true)
        set(v) = sp.edit().putBoolean(KEY_ENHANCE, v).apply()

    var autoRecord: Boolean
        get() = sp.getBoolean(KEY_AUTO, false)
        set(v) = sp.edit().putBoolean(KEY_AUTO, v).apply()

    var exportToDownloads: Boolean
        get() = sp.getBoolean(KEY_EXPORT, true)
        set(v) = sp.edit().putBoolean(KEY_EXPORT, v).apply()

    companion object {
        private const val KEY_ENHANCE = "enhance_audio"
        private const val KEY_AUTO = "auto_record"
        private const val KEY_EXPORT = "export_downloads"
    }
}
