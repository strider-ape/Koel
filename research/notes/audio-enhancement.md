# Audio enhancement — validated recipe

The call line is narrowband (cellular codec), so no recorder can add detail the network
never sent. But cleanup + leveling helps a lot. Validated on a real 38s recording with ffmpeg:

```
ffmpeg -i in.m4a -af "highpass=f=120, lowpass=f=7500, dynaudnorm=f=200:g=11, loudnorm=I=-16:TP=-1.5:LRA=11" -c:a aac -b:a 128k out.m4a
```

Measured: mean loudness -27 dB -> -18 dB, peak -7 dB -> -1.4 dB, no clipping.

The app reimplements this in pure Kotlin (no ffmpeg dependency) in `AudioEnhancer.kt`,
applied to the 16-bit PCM stream in real time before AAC encoding:
- Biquad high-pass @ 120 Hz, biquad low-pass @ 7500 Hz (RBJ cookbook)
- Per-buffer AGC toward -18 dBFS RMS, max gain 8x, with a soft limiter at 0.98 FS

Toggle: Settings "Enhance audio quality" (default on), stored in Prefs, passed through the
Shizuku service to AacRecorder.
