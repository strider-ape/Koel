Add-Type -TypeDefinition @"
using System; using System.IO; using System.Text;
public static class Wav {
    public static string Analyze(string path) {
        byte[] b = File.ReadAllBytes(path);
        int ch = 0, rate = 0, bits = 0, pos = 12, dataOff = -1, dataLen = 0;
        while (pos + 8 <= b.Length) {
            string id = Encoding.ASCII.GetString(b, pos, 4);
            int len = BitConverter.ToInt32(b, pos + 4);
            if (id == "fmt ") { ch = BitConverter.ToInt16(b, pos + 10); rate = BitConverter.ToInt32(b, pos + 12); bits = BitConverter.ToInt16(b, pos + 22); }
            if (id == "data") { dataOff = pos + 8; dataLen = Math.Min(len <= 0 ? b.Length : len, b.Length - dataOff); break; }
            pos += 8 + len + (len & 1);
        }
        var sb = new StringBuilder();
        sb.AppendFormat("{0} ch, {1} Hz, {2}-bit\n", ch, rate, bits);
        if (bits != 16 || dataOff < 0) return sb.Append("unsupported\n").ToString();
        int frame = ch * 2, perSec = rate * frame; int n = dataLen / frame;
        double totSq = 0; int peak = 0; long cnt = 0;
        for (int s = 0; s * perSec < dataLen; s++) {
            double sq = 0; int pk = 0, c = 0;
            int end = Math.Min(dataLen, (s + 1) * perSec);
            for (int i = s * perSec; i + 1 < end; i += 2) {
                int v = BitConverter.ToInt16(b, dataOff + i); int a = Math.Abs(v);
                sq += (double)v * v; if (a > pk) pk = a; c++;
            }
            double rms = Math.Sqrt(sq / Math.Max(1, c));
            double db = rms > 0 ? 20 * Math.Log10(rms / 32768.0) : -999;
            sb.AppendFormat("  sec {0,2}: rms {1,6:F1} dBFS  peak {2,5}  {3}\n", s, db, pk, new string('#', Math.Max(0, (int)((db + 70) / 2))));
            totSq += sq; cnt += c; if (pk > peak) peak = pk;
        }
        double trms = Math.Sqrt(totSq / Math.Max(1, cnt));
        sb.AppendFormat("  OVERALL: {0:F1} s, rms {1:F1} dBFS, peak {2}\n", (double)n / rate, trms > 0 ? 20 * Math.Log10(trms / 32768.0) : -999, peak);
        return sb.ToString();
    }
}
"@
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
foreach ($f in 'voice-call','voice-call-downlink','voice-call-uplink','pretest-mic') {
    $p = Join-Path $dir "$f.wav"
    if (Test-Path $p) { "== $f ($((Get-Item $p).Length) bytes)"; [Wav]::Analyze($p) } else { "== $f MISSING" }
}
