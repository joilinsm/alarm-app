$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$raw = Join-Path $root "app\src\main\res\raw"
New-Item -ItemType Directory -Force -Path $raw | Out-Null

function Write-Wav([string]$path, [int]$sampleRate, [int16[]]$samples) {
    $dataSize = $samples.Length * 2
    $stream = [System.IO.File]::Open($path, [System.IO.FileMode]::Create)
    $writer = New-Object System.IO.BinaryWriter($stream)
    $writer.Write([System.Text.Encoding]::ASCII.GetBytes("RIFF"))
    $writer.Write([int](36 + $dataSize))
    $writer.Write([System.Text.Encoding]::ASCII.GetBytes("WAVE"))
    $writer.Write([System.Text.Encoding]::ASCII.GetBytes("fmt "))
    $writer.Write([int]16)
    $writer.Write([int16]1)
    $writer.Write([int16]1)
    $writer.Write([int]$sampleRate)
    $writer.Write([int]($sampleRate * 2))
    $writer.Write([int16]2)
    $writer.Write([int16]16)
    $writer.Write([System.Text.Encoding]::ASCII.GetBytes("data"))
    $writer.Write([int]$dataSize)
    foreach ($s in $samples) { $writer.Write([int16]$s) }
    $writer.Flush()
    $writer.Close()
}

function Tone([double]$seconds, [scriptblock]$fn) {
    $sr = 22050
    $n = [int]($seconds * $sr)
    $samples = New-Object int16[] $n
    for ($i = 0; $i -lt $n; $i++) {
        $t = $i / $sr
        $v = & $fn $t $i $sr
        if ($v -gt 1) { $v = 1 }
        if ($v -lt -1) { $v = -1 }
        $samples[$i] = [int16]([math]::Round($v * 28000))
    }
    return $samples
}

$tones = @{
    "tone_alert_beacon.wav" = Tone 4 {
        param($t)
        $env = if (($t % 1.0) -lt 0.35) { 1 } else { 0 }
        return $env * 0.55 * ([math]::Sin(2 * [math]::PI * 880 * $t) + 0.5 * [math]::Sin(2 * [math]::PI * 1100 * $t))
    }
    "tone_dawn_pulse.wav" = Tone 4 {
        param($t)
        $f = 520 + 40 * $t
        $env = [math]::Min(1, $t / 0.4) * (0.4 + 0.6 * [math]::Abs([math]::Sin(2 * [math]::PI * 2 * $t)))
        return $env * 0.5 * [math]::Sin(2 * [math]::PI * $f * $t)
    }
    "tone_crystal_chime.wav" = Tone 4 {
        param($t)
        $d1 = [math]::Exp(-2.2 * ($t % 2))
        $d2 = [math]::Exp(-2.8 * (($t + 0.15) % 2))
        return 0.45 * $d1 * [math]::Sin(2 * [math]::PI * 784 * $t) + 0.3 * $d2 * [math]::Sin(2 * [math]::PI * 1174 * $t)
    }
    "tone_radar_ping.wav" = Tone 4 {
        param($t)
        $cycle = $t % 1.4
        $env = [math]::Exp(-4.5 * $cycle)
        return $env * 0.6 * [math]::Sin(2 * [math]::PI * 980 * $t) * [math]::Sin(2 * [math]::PI * 6 * $t)
    }
    "tone_gentle_wave.wav" = Tone 4 {
        param($t)
        $tri = 2 * [math]::Abs(2 * (($t * 220) % 1) - 1) - 1
        return 0.35 * $tri * (0.5 + 0.5 * [math]::Sin(2 * [math]::PI * 0.5 * $t))
    }
    "tone_night_watch.wav" = Tone 4 {
        param($t)
        $pulse = if (($t % 0.9) -lt 0.18) { 1 } else { 0 }
        return $pulse * 0.5 * [math]::Sin(2 * [math]::PI * 180 * $t)
    }
    "tone_sunrise_bell.wav" = Tone 4 {
        param($t)
        $env = [math]::Exp(-1.4 * ($t % 2))
        return $env * (0.4 * [math]::Sin(2 * [math]::PI * 523 * $t) + 0.22 * [math]::Sin(2 * [math]::PI * 784 * $t) + 0.12 * [math]::Sin(2 * [math]::PI * 1046 * $t))
    }
    "tone_digital_tick.wav" = Tone 3 {
        param($t)
        $on = if (($t % 0.5) -lt 0.06) { 1 } else { 0 }
        return $on * 0.45 * [math]::Sign([math]::Sin(2 * [math]::PI * 1400 * $t))
    }
}

foreach ($name in $tones.Keys) {
    $path = Join-Path $raw $name
    Write-Wav $path 22050 $tones[$name]
    Write-Host "Wrote $path"
}
Write-Host "Original alarm tones generated."
