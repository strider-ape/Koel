$scrcpy = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Genymobile.scrcpy_Microsoft.Winget.Source_8wekyb3d8bbwe\scrcpy-win64-v4.1\scrcpy.exe"
# Make scrcpy reuse our adb instead of its bundled one (version mismatch kills the server)
$env:ADB = (Get-Command adb).Source
$out = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Output "Waiting for an active call (up to 10 min)..."
$deadline = (Get-Date).AddMinutes(10)
while ((Get-Date) -lt $deadline) {
    $state = ((adb shell dumpsys telephony.registry) -match 'mCallState=' | Select-Object -First 1) -join ''
    if ($state -match 'mCallState=2') { break }
    Start-Sleep -Milliseconds 700
}
if ($state -notmatch 'mCallState=2') { Write-Output "No call detected."; exit 1 }

Write-Output "Call active at $(Get-Date -Format HH:mm:ss). Waiting 3s for audio to settle..."
Start-Sleep -Seconds 3

$tests = @(
    @{ src = 'voice-call';          secs = 20 },
    @{ src = 'voice-call-downlink'; secs = 12 },
    @{ src = 'voice-call-uplink';   secs = 12 }
)
foreach ($t in $tests) {
    $file = Join-Path $out "$($t.src).wav"
    Write-Output "Recording $($t.src) for $($t.secs)s -> $file"
    & $scrcpy --no-video --no-window --no-audio-playback --no-control `
        --audio-source=$($t.src) --audio-codec=raw --record=$file `
        --time-limit=$($t.secs) --require-audio 2>&1 | Select-String -Pattern 'ERROR|WARN|Recording' | ForEach-Object { "  $_" }
}
Write-Output "Done."
