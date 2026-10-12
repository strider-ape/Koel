# Koel

A personal call recorder for Android that records **both sides** of a call, silently,
**without root and without unlocking the bootloader**. Built for a Poco M4 Pro 4G
(codename `miel`, HyperOS 1.0 / Android 13) but should work on most Xiaomi/Poco devices.

## How it works

Android blocks ordinary apps from recording call audio (`VOICE_CALL` source). But the
**ADB shell user** (uid 2000, `com.android.shell`) is allowed to — that is how scrcpy
records call audio. [Shizuku](https://shizuku.rikka.app/) starts a small service as that
shell user, and Koel records through it.

```
MainActivity (UI)
  └─ ShizukuManager ── binds ──▶ RecorderService  (runs as uid 2000 via Shizuku)
                                   └─ AacRecorder  (AudioRecord VOICE_CALL → AAC/.m4a)
  └─ CallRecorderService (foreground, watches TelephonyCallback call state)
```

- `ShellContext` makes the recording process present itself as `com.android.shell` so the
  audio system accepts the capture (same trick as scrcpy's `FakeContext`).
- Recordings are saved to the app's external files dir as `.m4a`, named by time/direction/number.

## Feasibility — proven on 2026-10-12

Recorded a real call over scrcpy on the actual device. All three sources carried audio:
`voice-call` (both sides), `voice-call-downlink` (other party), `voice-call-uplink` (self).
See `research/calltest/` (wav files git-ignored). The two sides differ in level; a future
version can auto-balance them.

## Build

Requirements: JDK 21, Android SDK (platform 37, build-tools 36), Gradle wrapper included.

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## On-device setup

1. Install **Shizuku** from the Play Store and start it (via wireless debugging, or ADB).
2. Open Koel, grant the Shizuku permission, grant phone + notification permissions.
3. Turn on **Auto-record calls**.

## Status

- [x] Feasibility proven (call audio capturable as shell user)
- [x] Core recorder (Shizuku service + AAC encoder)
- [x] Call-state watcher + foreground service
- [x] Minimal UI (status, auto-record toggle, recordings list)
- [x] First debug APK builds
- [ ] Install + first real recording test on device
- [ ] Caller number/name from call log; correct in/out direction
- [ ] Reboot reminder (Shizuku needs restart after reboot)
- [ ] HyperOS battery/autostart hardening
- [ ] Playback with waveform, auto-balance channels
- [ ] Later: on-device transcription (Hindi/English), search, encryption, backup

## Legal

Recording your own calls is generally lawful in India. Do not share recordings of others
without consent. Recordings stay on-device.
