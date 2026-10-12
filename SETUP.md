# Koel — Setup Guide

How to install Koel and get it recording calls on a Xiaomi / POCO / Redmi phone
(HyperOS or MIUI) **without root and without unlocking the bootloader**.

Tested on: POCO M4 Pro 4G (`miel`), HyperOS 1.0 / Android 13.
Should work on most Xiaomi/POCO/Redmi phones on Android 11+ whose audio driver
exposes the `VOICE_CALL` source (most MediaTek/Snapdragon Xiaomi devices do).

---

## How it works (30 seconds)

Android forbids normal apps from recording call audio. The **ADB shell user**
(`com.android.shell`, uid 2000) is allowed to — that's how scrcpy records calls.
[Shizuku](https://shizuku.rikka.app/) runs a tiny service as that shell user, and
Koel records through it. No system modification, nothing permanent.

---

## What you need

- The phone (Android 11 or newer).
- The **Shizuku** app (free, Play Store).
- The **Koel** APK (build it yourself — see [README](README.md) — or grab it from Releases).
- A computer with `adb` **once**, OR use the on-device "Wireless debugging" method (no PC).

---

## Step 1 — Developer options and the TWO USB debugging switches

1. **Settings → About phone →** tap **OS version / MIUI version** 7 times until it says
   "You are now a developer".
2. **Settings → Additional settings → Developer options**, and enable:
   - **USB debugging**
   - **USB debugging (Security settings)** ← *separate switch, easy to miss.* Without
     this, Shizuku shows **"your device manufacturer has limited…"** when you try to grant
     permission. Enabling it usually needs you **signed into a Mi account with a SIM inserted**;
     accept Xiaomi's warning.
   - **Install via USB** (only needed if you install the APK over USB).

> ⚠️ Do **not** run the scanner in Xiaomi's **Security** app — it silently turns
> Developer options back off.

---

## Step 2 — Install Shizuku and Koel

- Install **Shizuku** from the Play Store.
- Install **Koel**:
  - Over USB: `adb install -r app-debug.apk`
  - Or copy the APK to the phone and tap it (needs "Install via USB" / unknown-sources).

---

## Step 3 — Start the Shizuku service

Shizuku must be started **once per boot** (it stops on reboot — see [Step 6](#step-6--after-every-reboot)).
Pick whichever is easier:

### Option A — Wireless debugging (no computer)

1. **Developer options → Wireless debugging → On.**
2. Open **Shizuku → Start via wireless debugging**, follow its pairing prompt
   (tap "Pair device with pairing code" in Wireless debugging, enter the code in Shizuku's
   notification). Pairing is needed only the first time.
3. Tap **Start**. Shizuku shows "running".

MIUI note: if pairing fails instantly, set **Settings → Notifications → Notification shade**
style to **"Android"**.

### Option B — Connect to a computer (what this repo used)

With the phone on USB and `adb devices` listing it, run the Shizuku starter as the shell user:

```bash
# Find Shizuku's install path
adb shell pm path moe.shizuku.privileged.api
# -> package:/data/app/~~XXXX==/moe.shizuku.privileged.api-YYYY==/base.apk

# Run the starter from that same folder's lib/arm64 directory:
adb shell /data/app/~~XXXX==/moe.shizuku.privileged.api-YYYY==/lib/arm64/libshizuku.so
```

Success looks like:

```
info: starting server...
info: shizuku_server pid is NNNNN
info: shizuku_starter exit with 0
```

(The classic `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
only exists after you've opened Shizuku's "Start via computer" screen once; the
`libshizuku.so` command above always works.)

---

## Step 4 — Grant Koel the Shizuku permission

1. Open **Koel**. The status card shows **Shizuku: NEEDS_PERMISSION**.
2. Tap **Grant Shizuku permission**.
3. In the Shizuku popup, tap **Allow** (or **Always allow**).
4. Status flips to **READY**.

If you still see "manufacturer has limited", **USB debugging (Security settings)** isn't
active — recheck Step 1, then **replug USB / restart Shizuku** and try again.

---

## Step 5 — Turn on recording

- Flip **Auto-record calls** on. Koel now runs a small foreground service that records
  every call (both sides) and saves `.m4a` files, listed on the main screen.

---

## Step 6 — After every reboot

Shizuku stops when the phone restarts, so after a reboot:

- **Wireless debugging method:** open Shizuku → Start.
- **Computer method:** re-run the `libshizuku.so` command from Step 3.

Until Shizuku is restarted, Koel can't record. (A future Koel version will remind you.)

---

## Step 7 — Keep HyperOS from killing it

HyperOS aggressively stops background apps. Do all of these for **Koel** (and Shizuku):

1. **Recents screen →** swipe down on the Koel card → tap the **lock** icon.
2. **Settings → Apps → Koel → Battery saver → No restrictions.**
3. **Settings → Apps → Koel → Autostart → On.**
4. Same three for **Shizuku**.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Your device manufacturer has limited…" on grant | Enable **USB debugging (Security settings)**, replug USB, restart Shizuku. |
| Koel stuck on **SHIZUKU_MISSING** | Shizuku service isn't running — do Step 3. |
| Recording file is empty / silent | Your ROM may not expose `VOICE_CALL`; try while **not** on speakerphone. Report your model. |
| Recording stops after a while | Do Step 7 (battery/autostart). |
| `INSTALL_FAILED_USER_RESTRICTED` | Enable **Install via USB** in Developer options. |

---

## Privacy

Recordings are stored only in Koel's own app storage on the phone and are removed if you
uninstall. Nothing is uploaded. Know and follow the call-recording laws where you live.
