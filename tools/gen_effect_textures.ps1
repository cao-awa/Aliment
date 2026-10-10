<#
    gen_effect_textures.ps1 - the two icons of the status effects.

    `AlimentEffects` registers two effects that do nothing: a fever and the ache that comes with
    it. They exist so the HUD can say what the debug command would otherwise have to be asked, and
    an effect's icon is found by the effect's own registry id - `Hud.getMobEffectSprite` builds
    `mob_effect/<path>` and the vanilla `gui` atlas collects that whole directory from every
    namespace - so a mod's effect icon is a file at
    `assets/<namespace>/textures/mob_effect/<path>.png` and needs no code, no atlas entry and no
    client class at all.

    Both are 18x18, which is the size vanilla's own effect icons are, and that is not a style
    choice: `Hud` blits the sprite into an 18x18 box, so anything else is scaled to it and a
    16x16 icon would come out soft.

    The two are drawn rather than copied, and they are separate glyphs rather than one tinted
    twice, because the two effects can be on the HUD at the same time and a player reading the row
    has to be able to tell which is which at a glance:

      fever - a thermometer with the mercury high up the bore, which says "how hot" rather than
              "hot", matching the three grades the effect has.
      pain  - a four-point flare, the shape of a sudden impact. It is one level only, so it is one
              shape only: there is no pain-II to draw.

    Both are drawn as ASCII art rather than with the rectangle and box helpers the item sprites
    use. At 18x18 a shape is small enough to write down, and written down it can be read - the
    grid below *is* the image, so a change to it is reviewable in a diff instead of having to be
    rendered.

    Run with (from the repository root):
        powershell -ExecutionPolicy Bypass -File tools\gen_effect_textures.ps1

    Idempotent: every target is rewritten from the grids below on every run, and a grid that uses
    a character not in the palette fails the run rather than writing a hole.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$eroot = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$edir = Join-Path $eroot 'src\main\resources\assets\aliment\textures\mob_effect'
if (-not (Test-Path $edir)) { New-Item -ItemType Directory -Force -Path $edir | Out-Null }

# --------------------------------------------------------------------- palettes

<#
    PowerShell's own `@{}` ignores case, so `R` and `r` would be one key - and those two are
    exactly the pair the art needs to keep apart, because a thermometer is mercury and its shadow.
    An ordinal dictionary keeps the grids in mixed case, which is what makes them readable. Colours
    are eight-digit `#AARRGGBB`, so transparency is an ordinary entry and there is no null to
    special-case; that is also the form the mod's other generators write.
#>
function New-EffectPalette {
    return [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
}

$feverPalette = New-EffectPalette
$feverPalette.Add('.', '#00000000')   # transparent
$feverPalette.Add('#', '#FF3A1414')   # the outline, a warm near-black rather than a neutral one
$feverPalette.Add('G', '#FFEEF2F6')   # lit glass
$feverPalette.Add('g', '#FFB4BEC8')   # glass in shadow
$feverPalette.Add('R', '#FFE8453C')   # mercury, the bright side
$feverPalette.Add('r', '#FF9E1B16')   # mercury, the shaded side
$feverPalette.Add('p', '#FFFF7A6E')   # the specular blip on the bulb

<#
    The fever is clinical glass and hot mercury, and the mercury carries the whole signal: it is the
    only saturated thing in the icon, so at HUD size the eye finds it first and the reading is "how
    far up the red goes".

    The pain is the same silhouette logic in a bruise - a magenta-red rather than the fever's
    orange-red, so the two are separable even for a player who cannot tell the shapes apart.
#>
$painPalette = New-EffectPalette
$painPalette.Add('.', '#00000000')
$painPalette.Add('#', '#FF3A1020')   # outline
$painPalette.Add('P', '#FFC2446C')   # the body of the flare
$painPalette.Add('p', '#FF8E2A50')   # its shaded flank
$painPalette.Add('q', '#FF5C1734')   # the shade under the arms
$painPalette.Add('w', '#FFF0A8C4')   # the lit top-left

# --------------------------------------------------------------------- the grids

<#
    The thermometer. Rows top to bottom, one character per pixel, eighteen each.

    The stem is four wide and the bulb flares 4-6-8-10-10-8-6-4, so the silhouette is a glass tube
    with a ball on the end and nothing else. The mercury runs from row 5 all the way into the bulb
    without a break at the neck, which is the one thing the shape has to get right: a bulb that
    lights up separately from its bore is a broken thermometer, and the whole point of the icon is
    that the level is readable.
#>
$feverGrid = @(
    '..................'
    '.......####.......'   # the cap on the end of the bore
    '.......#Gg#.......'   # empty glass above the mercury
    '.......#Gg#.......'
    '.......#Gg#.......'
    '.......#Rr#.......'   # the column starts here, five rows below the top of the stem
    '.......#Rr#.......'
    '.......#Rr#.......'
    '.......#Rr#.......'
    '.......#Rr#.......'
    '.......#Rr#.......'
    '......#pRRr#......'   # the shoulders of the bulb
    '.....#pRRRRr#.....'
    '....#pRRRRRRr#....'
    '....#pRRRRRRr#....'
    '.....#rRRRRr#.....'
    '......#rRRr#......'
    '.......####.......'   # and its base
)

<#
    The flare. Four arms out of a narrow waist; the arms are four wide for four rows and then the
    waist pinches to eight before the horizontal pair opens out to the full sixteen, and that pinch
    is the whole shape - take it out and the cross becomes a disc.

    The vertical pair runs sixteen rows and the horizontal pair only two, which is the asymmetry
    that stops it reading as a compass rose: the eye takes it as a flare thrown off by something
    rather than a direction.
#>
$painGrid = @(
    '..................'
    '.......####.......'   # the tip of the top arm
    '.......#wP#.......'
    '.......#wP#.......'
    '.......#wP#.......'
    '......#wPPP#......'
    '......#wPPP#......'
    '.....#wPPPPp#.....'
    '.#wPPPPPPPPPPPPp#.'   # the horizontal pair, and the pinch it opens out of
    '.#qpppPPPPPPpppq#.'   # its underside, held lit through the middle where the stem crosses
    '.....#qPPPPp#.....'
    '......#qPPp#......'
    '......#qPPp#......'
    '.......#Pp#.......'
    '.......#Pp#.......'
    '.......#qp#.......'
    '.......####.......'   # the tip of the bottom arm
    '..................'
)

# --------------------------------------------------------------------- helpers

function Save-EffectPng([string]$path, [string[]]$grid, $palette) {
    $height = $grid.Count
    $width = $grid[0].Length
    foreach ($row in $grid) {
        if ($row.Length -ne $width) { throw "$path has a row of $($row.Length) pixels, not $width" }
    }

    $bmp = New-Object System.Drawing.Bitmap($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $height; $y++) {
        for ($x = 0; $x -lt $width; $x++) {
            $key = [string]$grid[$y][$x]
            if (-not $palette.ContainsKey($key)) { throw "$path uses '$key', which is not in its palette" }
            $hex = $palette[$key]
            $a = [Convert]::ToInt32($hex.Substring(1, 2), 16)
            $r = [Convert]::ToInt32($hex.Substring(3, 2), 16)
            $g = [Convert]::ToInt32($hex.Substring(5, 2), 16)
            $b = [Convert]::ToInt32($hex.Substring(7, 2), 16)
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($a, $r, $g, $b))
        }
    }

    $full = [System.IO.Path]::GetFullPath($path)
    $bmp.Save($full, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output "  $([System.IO.Path]::GetFileName($path))  ${width}x${height}"
}

# --------------------------------------------------------------------- write

Write-Output 'effect icons:'
Save-EffectPng (Join-Path $edir 'fever.png') $feverGrid $feverPalette
Save-EffectPng (Join-Path $edir 'pain.png') $painGrid $painPalette
