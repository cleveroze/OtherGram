# OtherGram: generates the built-in Vanilla Beige themes (light and dark) from Telegram's own colors.
#
# Every color key Telegram knows (ThemeColors.createDefaultColors + the base .attheme) is remapped
# into the Vanilla Beige palette, so no element keeps Telegram's blue just because it was missed.
#   neutral colors (whites, greys, blacks) -> warm ramp between the palette's dark text and backgrounds
#   blue accent family                     -> palette accent (#A68770) hue and saturation
#   other hues (red errors, green online)  -> same hue, calmer saturation
# Then a few surfaces get exact palette values (backgrounds, bubbles, action bar).
#
# Usage (from repo root):  powershell -ExecutionPolicy Bypass -File Tools/othergram/generate-themes.ps1

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$themeColors = Join-Path $root 'TMessagesProj\src\main\java\org\telegram\ui\ActionBar\ThemeColors.java'
$assets = Join-Path $root 'TMessagesProj\src\main\assets'

# Palette from OTHERGRAM_SPEC.md
$P = @{
    Main      = 0xF1E1CC
    Secondary = 0xE5CFB5
    Accent    = 0xA68770
    DarkText  = 0x30241C
    LightText = 0xFFF7ED
}

# PowerShell 5.1 reads 0xFF...... literals as negative Int32, so all ARGB values go through ToU
function ToU([int64]$x) { [uint32]($x -band 4294967295) }

function Get-Rgb([int]$rgb) { @((($rgb -shr 16) -band 255), (($rgb -shr 8) -band 255), ($rgb -band 255)) }

function Mix([int]$a, [int]$b, [double]$t) {
    $x = Get-Rgb $a; $y = Get-Rgb $b; $r = 0
    for ($i = 0; $i -lt 3; $i++) { $r = ($r -shl 8) -bor [int][Math]::Round($x[$i] + ($y[$i] - $x[$i]) * $t) }
    $r
}

function To-Hsl([int]$rgb) {
    $c = Get-Rgb $rgb | ForEach-Object { $_ / 255.0 }
    $max = ($c | Measure-Object -Maximum).Maximum; $min = ($c | Measure-Object -Minimum).Minimum
    $l = ($max + $min) / 2; $h = 0.0; $s = 0.0
    if ($max -ne $min) {
        $d = $max - $min
        $s = if ($l -gt 0.5) { $d / (2 - $max - $min) } else { $d / ($max + $min) }
        if ($max -eq $c[0]) { $h = (($c[1] - $c[2]) / $d) % 6 }
        elseif ($max -eq $c[1]) { $h = ($c[2] - $c[0]) / $d + 2 }
        else { $h = ($c[0] - $c[1]) / $d + 4 }
        $h = $h * 60; if ($h -lt 0) { $h += 360 }
    }
    @($h, $s, $l)
}

function From-Hsl([double]$h, [double]$s, [double]$l) {
    $s = [Math]::Max(0.0, [Math]::Min(1.0, $s)); $l = [Math]::Max(0.0, [Math]::Min(1.0, $l))
    $c = (1 - [Math]::Abs(2 * $l - 1)) * $s
    $x = $c * (1 - [Math]::Abs((($h / 60) % 2) - 1))
    $m = $l - $c / 2
    switch ([int][Math]::Floor($h / 60) % 6) {
        0 { $r = $c; $g = $x; $b = 0 } 1 { $r = $x; $g = $c; $b = 0 } 2 { $r = 0; $g = $c; $b = $x }
        3 { $r = 0; $g = $x; $b = $c } 4 { $r = $x; $g = 0; $b = $c } default { $r = $c; $g = 0; $b = $x }
    }
    $v = 0
    foreach ($ch in @($r, $g, $b)) { $v = ($v -shl 8) -bor [int][Math]::Round(($ch + $m) * 255) }
    $v
}

$accentHsl = To-Hsl $P.Accent

function Read-ThemeColors {
    $src = Get-Content $themeColors -Raw
    $consts = @{
        'TELEGRAM_COLOR'      = ToU 0xFF229AF0
        'TELEGRAM_COLOR_TEXT' = ToU 0xFF298ACF
        'DEFAULT_BLACK_TEXT'  = ToU 0xFF1A1D21
        'Color.WHITE'         = ToU 0xFFFFFFFF
        'ColorUtils.setAlphaComponent(Color.WHITE, 90)' = ToU 0x5AFFFFFF
    }
    $names = @{}
    foreach ($m in [regex]::Matches($src, 'colorKeysMap\.put\((key_\w+),\s*"([^"]+)"\)')) { $names[$m.Groups[1].Value] = $m.Groups[2].Value }
    $colors = @{}
    foreach ($m in [regex]::Matches($src, 'defaultColors\[(key_\w+)\]\s*=\s*([^;]+);')) {
        $expr = $m.Groups[2].Value.Trim()
        if ($consts.ContainsKey($expr)) { $v = $consts[$expr] }
        elseif ($expr -match '^0x([0-9a-fA-F]{1,8})$') { $v = [Convert]::ToUInt32($Matches[1], 16) }
        elseif ($expr -match '^-?\d+$') { $v = ToU ([int64]$expr) }
        else { throw "Unsupported default color expression for $($m.Groups[1].Value): $expr" }
        if (-not $names.ContainsKey($m.Groups[1].Value)) { continue }
        $colors[$names[$m.Groups[1].Value]] = [uint32]$v
    }
    # Keys with a fallback in Theme.java (e.g. glass_tabUnselected -> windowBackgroundWhiteBlackText) must stay
    # out of the theme unless the base theme sets them: writing their light defaults disables the fallback and
    # made the bottom tab icons dark-on-dark in the dark theme.
    $themeSrc = Get-Content (Join-Path (Split-Path $themeColors) 'Theme.java') -Raw
    foreach ($m in [regex]::Matches($themeSrc, 'fallbackKeys\.put\((key_\w+),')) {
        if ($names.ContainsKey($m.Groups[1].Value)) { $colors.Remove($names[$m.Groups[1].Value]) }
    }
    $colors
}

function Read-Attheme([string]$file) {
    $colors = @{}
    foreach ($line in Get-Content $file) {
        if ($line -eq 'WPS') { break }
        if ($line -match '^(\w+)=(-?\d+)$') { $colors[$Matches[1]] = ToU ([int64]$Matches[2]) }
    }
    $colors
}

function Is-Foreground([string]$key) {
    # many text keys live under a background prefix (windowBackgroundWhiteBlueText4), so only the tail decides
    $tail = $key -replace '^(windowBackgroundWhite|windowBackgroundGray|windowBackground|dialogBackground)', ''
    $tail -match '(Text|Icon|Title|Name|Link|Check|Tick|Clock|Views)' -and $tail -notmatch 'Background'
}

function Map-Color([string]$key, [uint32]$argb, [bool]$dark) {
    $a = $argb -shr 24; $rgb = [int]($argb -band 0xFFFFFF)
    $hsl = To-Hsl $rgb; $h = $hsl[0]; $s = $hsl[1]; $l = $hsl[2]
    $neutral = $s -lt 0.18 -or $l -gt 0.93 -or $l -lt 0.08
    if ($neutral) {
        if ($dark) {
            # dark ramp: near-black warm brown -> light text
            $new = Mix 0x17110D $P.LightText $l
        } elseif ($l -ge 0.995) {
            $new = if (Is-Foreground $key) { $P.LightText } else { $P.Main }
        } elseif ($l -ge 0.93) {
            $new = Mix $P.Secondary $P.Main (($l - 0.93) / 0.07)
        } else {
            # gamma keeps dividers light but pulls secondary text darker for readable contrast
            $new = Mix $P.DarkText $P.Secondary ([Math]::Pow($l / 0.93, 1.8))
        }
    } else {
        $blueFamily = $h -ge 170 -and $h -le 260
        if ($blueFamily -or $key -like 'chat_out*') {
            $newL = if ($dark) { [Math]::Max($l, 0.62) } elseif (Is-Foreground $key) { [Math]::Min($l * 0.92, 0.40) } else { [Math]::Min($l * 0.92, 0.52) }
            $new = From-Hsl $accentHsl[0] ([Math]::Min($s, $accentHsl[1] * 1.25)) $newL
        } else {
            $fgL = if (-not $dark -and (Is-Foreground $key)) { [Math]::Min($l, 0.42) } else { $l }
            $new = From-Hsl $h ($s * 0.8) $fgL
        }
    }
    ToU ([int64]$a * 16777216 + $new)
}

function Write-Attheme([hashtable]$colors, [string]$file) {
    $lines = foreach ($k in ($colors.Keys | Sort-Object)) {
        $v = [int64]$colors[$k]; if ($v -ge 2147483648) { $v -= 4294967296 }
        "$k=$v"
    }
    [IO.File]::WriteAllText($file, (($lines -join "`n") + "`n"), (New-Object Text.UTF8Encoding($false)))
    Write-Host "Wrote $($colors.Count) colors to $file"
}

$defaults = Read-ThemeColors

# Light: Telegram defaults + Day theme, but own-message bubbles keep the classic light scheme with dark text
$light = @{} + $defaults
$day = Read-Attheme (Join-Path $assets 'day.attheme')
foreach ($k in $day.Keys) { if ($k -notlike 'chat_out*' -and $k -notlike 'chat_wallpaper*') { $light[$k] = $day[$k] } }
$lightOut = @{}
foreach ($k in $light.Keys) { $lightOut[$k] = Map-Color $k $light[$k] $false }
$exactLight = @{
    windowBackgroundWhite = $P.Main; windowBackgroundGray = $P.Secondary
    actionBarDefault = $P.Main; actionBarDefaultTitle = $P.DarkText; actionBarDefaultIcon = $P.DarkText
    chats_menuBackground = $P.Main; dialogBackground = $P.Main; dialogBackgroundGray = $P.Secondary
    chat_wallpaper = $P.Secondary; chat_messagePanelBackground = $P.Main
    chat_inBubble = $P.LightText; chat_outBubble = (Mix $P.Main $P.Accent 0.22)
    chat_outBubbleSelected = (Mix $P.Main $P.Accent 0.32); chat_inBubbleSelected = $P.Main
    chat_messageTextIn = $P.DarkText; chat_messageTextOut = $P.DarkText
    windowBackgroundWhiteBlackText = $P.DarkText; chats_name = $P.DarkText
    featuredStickers_addButton = $P.Accent; featuredStickers_buttonText = $P.LightText
}
foreach ($k in $exactLight.Keys) { $lightOut[$k] = ToU (4278190080 + $exactLight[$k]) }
Write-Attheme $lightOut (Join-Path $assets 'og_vanilla_beige.attheme')

# Dark: Telegram defaults + Night theme mapped onto a warm dark ramp with the same accent
$dark = @{} + $defaults
$night = Read-Attheme (Join-Path $assets 'night.attheme')
foreach ($k in $night.Keys) { $dark[$k] = $night[$k] }
$darkOut = @{}
foreach ($k in $dark.Keys) { $darkOut[$k] = Map-Color $k $dark[$k] $true }
$exactDark = @{
    windowBackgroundWhite = 0x221A15; windowBackgroundGray = 0x17110D
    actionBarDefault = 0x221A15; actionBarDefaultTitle = $P.LightText; actionBarDefaultIcon = $P.LightText
    chats_menuBackground = 0x221A15; dialogBackground = 0x221A15; dialogBackgroundGray = 0x17110D
    chat_wallpaper = 0x17110D; chat_messagePanelBackground = 0x221A15
    chat_inBubble = 0x2E241D; chat_outBubble = 0x6E5644; chat_inBubbleSelected = 0x3A2E25; chat_outBubbleSelected = 0x7C6250
    chat_messageTextIn = $P.LightText; chat_messageTextOut = $P.LightText
    windowBackgroundWhiteBlackText = $P.LightText; chats_name = $P.LightText
    featuredStickers_addButton = $P.Accent; featuredStickers_buttonText = $P.LightText
}
foreach ($k in $exactDark.Keys) { $darkOut[$k] = ToU (4278190080 + $exactDark[$k]) }
Write-Attheme $darkOut (Join-Path $assets 'og_vanilla_beige_dark.attheme')
