package com.koel.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.koel.app.R
import com.koel.app.data.Recordings
import java.io.File

/**
 * Foreground service that stays alive to watch call state. When a call is connected
 * it asks the Shizuku-backed [com.koel.app.IRecorderService] to record the call
 * audio, and finalizes the file when the call ends.
 */
class CallRecorderService : Service() {

    private lateinit var tm: TelephonyManager
    private var callback: TelephonyCallback? = null
    private var currentFile: File? = null
    @Volatile private var recording = false
    private var lastNumber: String? = null
    private var callStartedAt = 0L

    override fun onCreate() {
        super.onCreate()
        tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        startForeground(NOTIF_ID, buildNotification("Watching for calls"))
        registerCallback()
    }

    private fun registerCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleState(state)
            }
            callback = cb
            tm.registerTelephonyCallback(mainExecutor, cb)
        }
    }

    private fun handleState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> if (!recording) startRecording()
            TelephonyManager.CALL_STATE_IDLE -> if (recording) stopRecording()
            // RINGING: we wait for OFFHOOK (call actually connected) before recording.
        }
    }

    private fun startRecording() {
        val svc = ShizukuManager.service
        if (svc == null) {
            Log.w(TAG, "no recorder service bound; cannot record this call")
            notify("Call not recorded - Shizuku not ready")
            return
        }
        val incoming = true // refined later via call log; OFFHOOK alone can't tell direction
        val file = Recordings.newFile(this, lastNumber, null, incoming)
        currentFile = file
        try {
            val pfd = ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY
            )
            val enhance = com.koel.app.data.Prefs(this).enhanceAudio
            val err = svc.start(pfd, VOICE_CALL_SOURCE, enhance)
            if (err != null) {
                Log.e(TAG, "recorder start error: $err")
                notify("Recording failed: $err")
                file.delete()
                currentFile = null
            } else {
                recording = true
                callStartedAt = System.currentTimeMillis()
                notify("Recording call")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startRecording failed", t)
            currentFile = null
        }
    }

    private fun stopRecording() {
        val svc = ShizukuManager.service
        recording = false
        val result = runCatching { svc?.stop() }.getOrNull()
        Log.i(TAG, "stop result: $result")
        currentFile?.let { f ->
            if (!f.exists() || f.length() == 0L) {
                f.delete()
            } else {
                // The call-log row exists now the call has ended; label the file from it.
                val info = com.koel.app.data.CallLogLookup.latest(this, callStartedAt)
                val finalFile = com.koel.app.data.Recordings.finalize(f, info)
                Log.i(TAG, "saved ${finalFile.name}")
            }
        }
        currentFile = null
        notify("Watching for calls")
    }

    private fun buildNotification(text: String): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Call recording", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Koel")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
    }

    private fun notify(text: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        callback?.let { tm.unregisterTelephonyCallback(it) }
        if (recording) stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CallRecorderService"
        private const val CHANNEL = "koel.watch"
        private const val NOTIF_ID = 1
        // MediaRecorder.AudioSource.VOICE_CALL == 4. Proven to carry both sides on this device.
        const val VOICE_CALL_SOURCE = MediaRecorder.AudioSource.VOICE_CALL

        fun start(context: Context) {
            val i = Intent(context, CallRecorderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallRecorderService::class.java))
        }
    }
}
