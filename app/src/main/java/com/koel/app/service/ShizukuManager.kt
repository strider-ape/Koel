package com.koel.app.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.koel.app.BuildConfig
import com.koel.app.IRecorderService
import com.koel.app.recorder.RecorderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

/**
 * Owns the Shizuku permission handshake and the bound [IRecorderService].
 *
 * State machine: SHIZUKU_MISSING -> NEEDS_PERMISSION -> BINDING -> READY
 * (or FAILED with a message).
 */
object ShizukuManager {

    enum class State { SHIZUKU_MISSING, NEEDS_PERMISSION, BINDING, READY, FAILED }

    private val _state = MutableStateFlow(State.SHIZUKU_MISSING)
    val state: StateFlow<State> = _state

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    @Volatile var service: IRecorderService? = null
        private set

    private const val PERMISSION_REQUEST_CODE = 4711

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, RecorderService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("recorder")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                service = IRecorderService.Stub.asInterface(binder)
                _state.value = State.READY
                _message.value = null
                Log.i(TAG, "user service connected")
            } else {
                _state.value = State.FAILED
                _message.value = "Service returned a dead binder"
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            if (_state.value == State.READY) _state.value = State.BINDING
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == PERMISSION_REQUEST_CODE) {
            if (grant == PackageManager.PERMISSION_GRANTED) bind()
            else {
                _state.value = State.NEEDS_PERMISSION
                _message.value = "Shizuku permission was denied"
            }
        }
    }

    fun init() {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            _state.value = State.SHIZUKU_MISSING
        }
    }

    /** Re-evaluates current availability. Safe to call from the UI whenever it resumes. */
    fun refresh() {
        if (!Shizuku.pingBinder()) {
            _state.value = State.SHIZUKU_MISSING
            return
        }
        if (Shizuku.isPreV11()) {
            _state.value = State.FAILED
            _message.value = "Shizuku is too old; please update it"
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            if (service == null) bind()
        } else {
            _state.value = State.NEEDS_PERMISSION
        }
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            _state.value = State.SHIZUKU_MISSING
            return
        }
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    private fun bind() {
        _state.value = State.BINDING
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (t: Throwable) {
            Log.e(TAG, "bindUserService failed", t)
            _state.value = State.FAILED
            _message.value = t.message
        }
    }

    private const val TAG = "ShizukuManager"
}
