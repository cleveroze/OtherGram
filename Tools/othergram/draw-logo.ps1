# OtherGram: draws the logo (beige waves + three dots) as Tools/othergram/icon-source.png, 1024x1024.
# Geometry is in a 512 grid and scaled, so the source stays sharp at any icon size.
#
# Usage (from repo root):  powershell -ExecutionPolicy Bypass -File Tools/othergram/draw-logo.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$size = 1024
$k = $size / 512.0
$bmp = New-Object System.Drawing.Bitmap $size, $size
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

function C([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }
function P([double]$x, [double]$y) { New-Object System.Drawing.PointF ([float]($x * $k)), ([float]($y * $k)) }

function Fill-Shape([string]$color, [scriptblock]$build) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    & $build $path
    $path.CloseFigure()
    $brush = New-Object System.Drawing.SolidBrush (C $color)
    $g.FillPath($brush, $path)
    $brush.Dispose(); $path.Dispose()
}

$g.Clear((C '#E5CFB5'))

# top-left wave
Fill-Shape '#A08570' {
    param($p)
    $p.AddLine((P 0 0), (P 345 0))
    $p.AddBezier((P 345 0), (P 338 70), (P 300 112), (P 225 138))
    $p.AddBezier((P 225 138), (P 140 168), (P 55 178), (P 0 252))
}

# bottom light wave (under the right one)
Fill-Shape '#C0A588' {
    param($p)
    $p.AddLine((P 150 512), (P 150 512))
    $p.AddBezier((P 150 512), (P 200 478), (P 268 462), (P 352 456))
    $p.AddLine((P 352 456), (P 420 512))
}

# bottom-right wave
Fill-Shape '#A08570' {
    param($p)
    $p.AddLine((P 512 264), (P 512 512))
    $p.AddLine((P 512 512), (P 326 512))
    $p.AddBezier((P 326 512), (P 330 470), (P 352 438), (P 382 392))
    $p.AddBezier((P 382 392), (P 420 330), (P 455 282), (P 512 264))
}

# three dots
$dots = @(@(168, '#C2A68A'), @(256, '#A08570'), @(344, '#7E5E46'))
foreach ($d in $dots) {
    $brush = New-Object System.Drawing.SolidBrush (C $d[1])
    $r = 30 * $k
    $g.FillEllipse($brush, [float]($d[0] * $k - $r), [float](256 * $k - $r), [float](2 * $r), [float](2 * $r))
    $brush.Dispose()
}

$out = Join-Path $PSScriptRoot 'icon-source.png'
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Host "Wrote $out"
