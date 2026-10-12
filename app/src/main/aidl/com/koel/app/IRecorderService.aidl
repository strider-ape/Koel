package com.koel.app;

import android.os.ParcelFileDescriptor;

// Runs inside a Shizuku user service process as the shell user (uid 2000),
// which holds CAPTURE_AUDIO_OUTPUT and can therefore record the call line.
interface IRecorderService {
    // Called by Shizuku when the service is unbound/removed. Code is fixed by Shizuku.
    void destroy() = 16777114;

    int getUid() = 1;

    // Starts recording into `out` (an .m4a container). Returns null on success, or an error message.
    String start(in ParcelFileDescriptor outFd, int audioSource, boolean enhance) = 2;

    // Stops and finalizes the file. Returns "durationMs;peak" or an error message prefixed with "ERR:".
    String stop() = 3;

    boolean isRecording() = 4;
}
