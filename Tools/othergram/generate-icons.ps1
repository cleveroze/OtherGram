# OtherGram: generates launcher icons from Tools/othergram/icon-source.png (square artwork, any size).
#
#   mipmap-*/og_ic_launcher_foreground.png  adaptive-icon foreground (Android 8+): the whole artwork sits
#                                           in the 72dp visible zone of the 108dp layer, so no mask clips it
#   mipmap-*/ic_launcher.png                legacy icon (Android 6-7), rounded square
#   mipmap-*/ic_launcher_round.png          legacy round icon
#
# Usage (from repo root):  powershell -ExecutionPolicy Bypass -File Tools/othergram/generate-icons.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$res = Join-Path $root 'TMessagesProj\src\main\res'
$source = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot 'icon-source.png'))

$densities = [ordered]@{ mdpi = 1.0; hdpi = 1.5; xhdpi = 2.0; xxhdpi = 3.0; xxxhdpi = 4.0 }

function New-Canvas([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    @($bmp, $g)
}

function Save-Png($bmp, [string]$dir, [string]$name) {
    $path = Join-Path $res "$dir\$name"
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host "Wrote $path"
}

function Rounded-Path([float]$size, [float]$radius) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $radius * 2
    $p.AddArc(0, 0, $d, $d, 180, 90)
    $p.AddArc($size - $d, 0, $d, $d, 270, 90)
    $p.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
    $p.AddArc(0, $size - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    $p
}

function Draw-Clipped([int]$size, $path) {
    $c = New-Canvas $size; $bmp = $c[0]; $g = $c[1]
    # draw through a texture brush so the clip edge is anti-aliased (SetClip is not)
    $art = New-Object System.Drawing.Bitmap $size, $size
    $ag = [System.Drawing.Graphics]::FromImage($art)
    $ag.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $ag.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $ag.DrawImage($source, 0, 0, $size, $size)
    $ag.Dispose()
    $brush = New-Object System.Drawing.TextureBrush $art
    $g.FillPath($brush, $path)
    $brush.Dispose(); $art.Dispose(); $g.Dispose()
    $bmp
}

foreach ($d in $densities.Keys) {
    $scale = $densities[$d]
    $dir = "mipmap-$d"

    # adaptive foreground: 108dp layer, artwork in the central 72dp
    $layer = [int](108 * $scale); $inner = [int](72 * $scale); $off = [int](($layer - $inner) / 2)
    $c = New-Canvas $layer; $bmp = $c[0]; $g = $c[1]
    $g.DrawImage($source, $off, $off, $inner, $inner)
    $g.Dispose(); Save-Png $bmp $dir 'og_ic_launcher_foreground.png'; $bmp.Dispose()

    # legacy icons: 48dp
    $size = [int](48 * $scale)
    $bmp = Draw-Clipped $size (Rounded-Path $size ($size * 0.2))
    Save-Png $bmp $dir 'ic_launcher.png'; $bmp.Dispose()
    $circle = New-Object System.Drawing.Drawing2D.GraphicsPath
    $circle.AddEllipse(0, 0, $size, $size)
    $bmp = Draw-Clipped $size $circle
    Save-Png $bmp $dir 'ic_launcher_round.png'; $bmp.Dispose()
}

$source.Dispose()
