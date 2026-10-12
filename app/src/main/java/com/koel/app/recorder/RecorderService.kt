package com.koel.app.recorder

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import com.koel.app.IRecorderService
import java.io.FileDescriptor

/**
 * The body of the Shizuku user service. This class is instantiated INSIDE a process
 * that Shizuku starts as the shell user (uid 2000). From here on, the audio system
 * sees us as the shell user, which is what lets us open the call audio source.
 *
 * It must have a constructor taking a Context (Shizuku calls it reflectively).
 */
class RecorderService(context: Context) : IRecorderService.Stub() {

    // Wrap the handed Context so the attribution package matches uid 2000.
    private val shellContext = ShellContext(context.applicationContext ?: context)

    @Volatile private var recorder: AacRecorder? = null

    override fun getUid(): Int = Process.myUid()

    override fun start(out: ParcelFileDescriptor, audioSource: Int): String? {
        if (recorder?.isRecording() == true) return "already recording"
        return try {
            val fd: FileDescriptor = out.fileDescriptor
            val r = AacRecorder(shellContext, audioSource, fd)
            r.start()
            recorder = r
            Log.i(TAG, "recording started (uid=${Process.myUid()}, source=$audioSource)")
            null
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            recorder = null
            t.message ?: t.javaClass.simpleName
        } finally {
            runCatching { out.close() }
        }
    }

    override fun stop(): String {
        val r = recorder ?: return "ERR:not recording"
        recorder = null
        return r.stop()
    }

    override fun isRecording(): Boolean = recorder?.isRecording() == true

    override fun destroy() {
        runCatching { recorder?.stop() }
        recorder = null
    }

    companion object {
        private const val TAG = "RecorderService"
    }
}
