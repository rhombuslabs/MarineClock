# Renders app/src/main/res/raw/bells_1.ogg … bells_8.ogg from tools/audio/ships_bell.wav.
# Timing: strike i at (i / 2) * 1.2 s + (i % 2) * 0.4 s; the 2.0 s strike sample is the ring-out.
# Channel sounds are fixed once created: after changing the audio, bump SOUND_VERSION in Channels.kt.
param(
    [string]$Sox = "C:\Program Files (x86)\sox-14-4-2\sox.exe"
)
$ErrorActionPreference = "Stop"
$inv = [Globalization.CultureInfo]::InvariantCulture
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $PSScriptRoot "audio\ships_bell.wav"
$outDir = Join-Path $root "app\src\main\res\raw"
$work = Join-Path ([IO.Path]::GetTempPath()) "ships-bell-render"
New-Item -ItemType Directory -Force $work | Out-Null

$pairGapS = 0.4
$pairPeriodS = 1.2

function Invoke-Sox {
    & $Sox @args
    if ($LASTEXITCODE -ne 0) { throw "sox failed: $args" }
}

$mono = Join-Path $work "strike_mono.wav"
Invoke-Sox $source $mono channels 1

foreach ($bells in 1..8) {
    $out = Join-Path $outDir "bells_$bells.ogg"
    if ($bells -eq 1) {
        Invoke-Sox $mono $out
    } else {
        $mixArgs = @("-m")
        for ($i = 0; $i -lt $bells; $i++) {
            $offset = [math]::Floor($i / 2) * $pairPeriodS + ($i % 2) * $pairGapS
            $strike = Join-Path $work "strike_$i.wav"
            Invoke-Sox $mono $strike pad $offset.ToString($inv)
            $mixArgs += @("-v", "1", $strike)
        }
        Invoke-Sox @mixArgs $out
    }
    $duration = & $Sox --i -D $out
    "bells_$bells.ogg  $duration s"
}
Remove-Item -Recurse -Force $work
