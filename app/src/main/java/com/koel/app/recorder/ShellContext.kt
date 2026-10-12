package com.koel.app.recorder

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper

/**
 * Presents this process to the audio system as `com.android.shell` (uid 2000).
 *
 * The process really does run as uid 2000 (started by Shizuku), but the Context
 * Shizuku hands us still carries our own package name. AudioFlinger checks that the
 * attribution's package belongs to the calling uid, so we report the shell package.
 * Same idea as scrcpy's FakeContext (Apache-2.0, Genymobile).
 */
class ShellContext(base: Context) : ContextWrapper(base) {

    override fun getPackageName(): String = SHELL_PACKAGE

    override fun getOpPackageName(): String = SHELL_PACKAGE

    override fun getAttributionSource(): AttributionSource =
        AttributionSource.Builder(SHELL_UID).setPackageName(SHELL_PACKAGE).build()

    override fun getApplicationContext(): Context = this

    companion object {
        const val SHELL_PACKAGE = "com.android.shell"
        const val SHELL_UID = 2000
    }
}
