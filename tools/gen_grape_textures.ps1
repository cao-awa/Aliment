<#
    gen_grape_textures.ps1 - the nine textures of the grape vine.

    A focused companion to gen_textures.ps1 and gen_grapefruit_textures.ps1, in the same spirit as
    gen_glucose_textures.ps1: same 16x16 canvases, same palette discipline, same PNG writer, but it
    only ever writes the nine files listed at the bottom, so it can be re-run on its own after
    changing one of them.

    Three things are worth knowing before reading the rest:

    1. The four growth stages are drawn from **ASCII art** rather than from coordinate lists. At
       16x16 a stage is short enough to read as a picture in the source, which is exactly what
       matters when the only question anyone ever asks about a crop sprite is "does it look like a
       vine?". `Add-GvArt` turns one 16-row block into pixels and throws if a row is the wrong
       length, so a typo is a loud failure and not a one-pixel hole.

    2. `item/grape_wine.png` is drawn in the **vanilla potion style**, the way every other drink in
       this mod is: vanilla's own `potion.png` bottle and `potion_overlay.png` liquid, with the
       liquid tinted to a deep wine red. The bottle is copied pixel for pixel - only the tint is
       ours. `tools/vanilla_potion.png` and `tools/vanilla_potion_overlay.png` must be present.

    3. The two fermentation-tank liquids are *not* drawn from art. `tank_liquid_water`,
       `tank_liquid_sugar` and `tank_liquid_wine` are all the same 16x16 fully-opaque weave, and
       the weave has a closed form: channel = base + ((11*x + 7*y) mod 25). Reproducing the formula
       rather than eyeballing the pixels is what makes the new liquids tile against the old ones.

    The legend is one *distinct letter* per material, never two letters differing only in case:
    PowerShell's `@{}` hashtable is case-insensitive as well as its variables, so a legend with both
    'M' and 'm' is a parse error, and the error is the good outcome - the silent one is a key that
    overwrites the tone meant for a different material.

    Run with (from the repository root):
        powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen_grape_textures.ps1

    Idempotent: every target is rewritten from scratch and nothing here is random, so two runs
    produce byte-identical files.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$gvRoot = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$gvBlockDir = Join-Path $gvRoot "src\main\resources\assets\aliment\textures\block"
$gvItemDir = Join-Path $gvRoot "src\main\resources\assets\aliment\textures\item"
foreach ($gvDir in @($gvBlockDir, $gvItemDir)) {
    if (-not (Test-Path $gvDir)) { New-Item -ItemType Directory -Force -Path $gvDir | Out-Null }
}

# --------------------------------------------------------------------- palette
# Two to three shading steps per material and nothing else: at 16x16 a fourth step is noise.
$gvClear = '#00000000'

# The vine: stem and foliage share one green ramp, so a stage reads as one plant.
$gvLeafDark = '#3E6B2A'
$gvLeafMid = '#4F7F35'
$gvLeafLight = '#5A8F3C'

# Ripe grapes: dark / mid / highlight.
$gvGrapeDark = '#582C68'
$gvGrapeMid = '#7B3F8F'
$gvGrapeHi = '#A66BB8'

# Unripe grapes: the same greens as the leaves, one step lighter, which is what makes a stage-2
# cluster read as "not ready" rather than as a second kind of leaf. Three steps, because the bunch
# is built from individual berries and each one needs a lit corner, a body and a shaded corner.
$gvUnripeDark = '#3C5F28'
$gvUnripeMid = '#4F7F35'
$gvUnripeLight = '#6FA04A'

# Seeds: pale tan over an olive shadow.
$gvSeedDark = '#A89868'
$gvSeedLight = '#C8B98A'

# The twig a bunch hangs from.
$gvTwig = '#6A4831'

# --------------------------------------------------------------------- helpers
$script:gvWritten = 0

function Save-GPng([string]$gpPath, $gpGrid, [int]$gpW, [int]$gpH) {
    $gpBmp = New-Object System.Drawing.Bitmap($gpW, $gpH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($gpY = 0; $gpY -lt $gpH; $gpY++) {
        for ($gpX = 0; $gpX -lt $gpW; $gpX++) {
            $gpHx = $gpGrid[$gpX, $gpY]
            $gpR = 0; $gpG = 0; $gpB = 0; $gpA = 0
            if ($gpHx -and $gpHx.StartsWith('#')) {
                $gpR = [Convert]::ToInt32($gpHx.Substring(1, 2), 16)
                $gpG = [Convert]::ToInt32($gpHx.Substring(3, 2), 16)
                $gpB = [Convert]::ToInt32($gpHx.Substring(5, 2), 16)
                $gpA = if ($gpHx.Length -ge 9) { [Convert]::ToInt32($gpHx.Substring(7, 2), 16) } else { 255 }
            }
            $gpBmp.SetPixel($gpX, $gpY, [System.Drawing.Color]::FromArgb($gpA, $gpR, $gpG, $gpB))
        }
    }
    $gpFull = [System.IO.Path]::GetFullPath($gpPath)
    $gpBmp.Save($gpFull, [System.Drawing.Imaging.ImageFormat]::Png)
    $gpBmp.Dispose()
    $script:gvWritten = $script:gvWritten + 1
}

# The leading comma matters: without it PowerShell unrolls the 2D array into a list of rows and every
# later read of $gg[$x, $y] comes back empty.
function New-GGrid([int]$gw, [int]$gh) {
    $gg = New-Object 'string[,]' $gw, $gh
    for ($gy = 0; $gy -lt $gh; $gy++) { for ($gx = 0; $gx -lt $gw; $gx++) { $gg[$gx, $gy] = $gvClear } }
    return , $gg
}

function Set-GPx($gg, [int]$gx, [int]$gy, [string]$gc) {
    if ($gx -ge 0 -and $gy -ge 0 -and $gx -lt $gg.GetLength(0) -and $gy -lt $gg.GetLength(1)) {
        $gg[$gx, $gy] = $gc
    }
}

# One ASCII block -> one grid. '.' is transparent; every other character must be in $gvMap, and every
# row must be exactly $gw characters, so a miscount is an exception rather than a hole.
function Add-GvArt($gg, [string[]]$gvArt, $gvMap, [string]$gvWhat) {
    $gvW = $gg.GetLength(0)
    if ($gvArt.Count -ne $gg.GetLength(1)) {
        throw "$gvWhat has $($gvArt.Count) rows, expected $($gg.GetLength(1))."
    }
    for ($gvY = 0; $gvY -lt $gvArt.Count; $gvY++) {
        $gvRow = $gvArt[$gvY]
        if ($gvRow.Length -ne $gvW) {
            throw "$gvWhat row $gvY is $($gvRow.Length) characters, expected $gvW."
        }
        for ($gvX = 0; $gvX -lt $gvW; $gvX++) {
            $gvCh = $gvRow.Substring($gvX, 1)
            if ($gvCh -eq '.') { continue }
            if (-not $gvMap.ContainsKey($gvCh)) { throw "$gvWhat uses '$gvCh' at ($gvX,$gvY), which is not in the legend." }
            Set-GPx $gg $gvX $gvY $gvMap[$gvCh]
        }
    }
}

# d/m/l leaves, p/q/r ripe grapes, k/n/u unripe grapes, s/w seeds, t twig.
# Every legend key is a distinct letter for a reason: PowerShell compares hashtable keys
# case-insensitively, so 'k' and 'K' would be the *same* entry and the second would win.
$gvMap = @{
    'd' = $gvLeafDark
    'm' = $gvLeafMid
    'l' = $gvLeafLight
    'p' = $gvGrapeDark
    'q' = $gvGrapeMid
    'r' = $gvGrapeHi
    'k' = $gvUnripeDark
    'n' = $gvUnripeMid
    'u' = $gvUnripeLight
    's' = $gvSeedDark
    'w' = $gvSeedLight
    't' = $gvTwig
}

function New-GvArt([string]$gvOut, [string[]]$gvArt, [string]$gvWhat) {
    $gg = New-GGrid 16 16
    Add-GvArt $gg $gvArt $gvMap $gvWhat
    Save-GPng $gvOut $gg 16 16
}

# ---------------------------------------------------------------------
# 1. block/grape_vine_stage0.png
#    A sprout: a one-pixel stem with a single pair of cotyledons, sitting
#    low on the canvas because the block it grows in is mostly air.
# ---------------------------------------------------------------------
$gvStage0 = @(
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '.......l........',
    '.....lmmml......',
    '.......m........',
    '.......d........',
    '.......d........',
    '.......d........',
    '.......d........',
    '.......d........'
)

# ---------------------------------------------------------------------
# 2. block/grape_vine_stage1.png
#    Taller: the stem reaches the middle of the cell and carries two
#    asymmetric leaf clusters, so it reads as a shoot and not a shrub.
# ---------------------------------------------------------------------
$gvStage1 = @(
    '................',
    '................',
    '................',
    '................',
    '.......l........',
    '......lmm.......',
    '.....lmmml......',
    '......mmm.......',
    '.......m........',
    '....llmm........',
    '...lmmmm........',
    '....lmm.........',
    '.......d........',
    '.......d........',
    '......dd........',
    '......dd........'
)

# ---------------------------------------------------------------------
# 3. block/grape_vine_stage2.png
#    A full canopy with the first, still-green cluster hanging in it.
# ---------------------------------------------------------------------
$gvStage2 = @(
    '................',
    '................',
    '.....llll.......',
    '...llmmmll......',
    '..lmmmmmml......',
    '..lmmmmmml......',
    '...lmmmmml......',
    '....lmmml.......',
    '.....lml........',
    '.....unun.......',
    '.....nknk.......',
    '......un........',
    '......nk........',
    '.......d........',
    '.......d........',
    '......dd........'
)

# ---------------------------------------------------------------------
# 4. block/grape_vine_stage3.png
#    The bearing vine: the same canopy, and a ripe purple bunch hanging
#    from the middle of it. The berries are laid out as a tapering
#    lattice - 6, 6, 4, 4, 2, 2 - so the bunch narrows to a point the way
#    a real cluster does, and each berry is lit top-left with its body and
#    a shaded bottom-right corner (`r` lit, `q` body, `p` shaded).
# ---------------------------------------------------------------------
$gvStage3 = @(
    '................',
    '....lll.lll.....',
    '...lmmlmlmml....',
    '..lmmllmllmml...',
    '...lmmmmmmml....',
    '....lmmmmml.....',
    '.....lmmmml.....',
    '.....rqrqrq.....',
    '.....qpqpqp.....',
    '......rqrq......',
    '......qpqp......',
    '.......rq.......',
    '.......qp.......',
    '.......d........',
    '.......d........',
    '......dd........'
)

# ---------------------------------------------------------------------
# 5. item/grape.png
#    A bunch in the inventory: the same tapering lattice as the hanging
#    one, but wider (a full 3 berries across at the shoulders) and with a
#    bare twig standing up out of the top, so the item reads as a bunch
#    that has been picked rather than one still on the vine.
# ---------------------------------------------------------------------
$gvGrapeItem = @(
    '................',
    '........tm......',
    '........t.......',
    '....rrqrrqrrq...',
    '....rqprqprqp...',
    '....qppqppqpp...',
    '....rrqrrqrrq...',
    '....rqprqprqp...',
    '....qppqppqpp...',
    '.....rrqrrq.....',
    '.....rqprqp.....',
    '.....qppqpp.....',
    '......rrq.......',
    '......rqp.......',
    '......qpp.......',
    '................'
)

# ---------------------------------------------------------------------
# 6. item/grape_seeds.png
#    Three seeds, dropped diagonally so they do not read as one object.
# ---------------------------------------------------------------------
$gvSeeds = @(
    '................',
    '................',
    '...ww...........',
    '..wssw....ww....',
    '..wssw...wssw...',
    '...ss.....ss....',
    '................',
    '................',
    '........ww......',
    '.......wssw.....',
    '.......wssw.....',
    '........ss......',
    '................',
    '................',
    '................',
    '................'
)

# ---------------------------------------------------------------------
# 7. item/grape_wine.png
#    Drawn in the **vanilla potion style**, the way every other drink in
#    this mod is: vanilla's own potion.png bottle and potion_overlay.png
#    liquid, with the liquid tinted deep wine red. The shape is vanilla's
#    to the pixel; the only thing that is ours is the tint.
# ---------------------------------------------------------------------
$gvPotionPath = Join-Path $PSScriptRoot 'vanilla_potion.png'
$gvOverlayPath = Join-Path $PSScriptRoot 'vanilla_potion_overlay.png'

if (!(Test-Path $gvPotionPath) -or !(Test-Path $gvOverlayPath)) {
    throw "Vanilla potion reference textures not found in tools/: expected vanilla_potion.png and vanilla_potion_overlay.png next to this script. They are vanilla's own assets/minecraft/textures/item/potion.png and potion_overlay.png, extracted from the client jar; see tools/README.md."
}

function Get-GvVanillaPotionTint([string]$gvTint) {
    $gvBottle = New-Object System.Drawing.Bitmap($gvPotionPath)
    $gvOverlay = New-Object System.Drawing.Bitmap($gvOverlayPath)
    $gv = New-GGrid 16 16

    # Named -TintR-/-OutR- on purpose: PowerShell compares variable names case-insensitively, so a
    # computed `$gvb` would *be* `$gvB` and would clobber the tint's blue channel on the first pixel,
    # leaving the liquid fading to black down the bottle.
    $gvTintR = [Convert]::ToInt32($gvTint.Substring(1, 2), 16)
    $gvTintG = [Convert]::ToInt32($gvTint.Substring(3, 2), 16)
    $gvTintB = [Convert]::ToInt32($gvTint.Substring(5, 2), 16)

    for ($gvY = 0; $gvY -lt 16; $gvY++) {
        for ($gvX = 0; $gvX -lt 16; $gvX++) {
            $gvP = $gvBottle.GetPixel($gvX, $gvY)
            if ($gvP.A -gt 0) {
                # The bottle itself: vanilla's pixel, byte for byte.
                $gv[$gvX, $gvY] = '#{0:X2}{1:X2}{2:X2}' -f $gvP.R, $gvP.G, $gvP.B
                continue
            }
            $gvO = $gvOverlay.GetPixel($gvX, $gvY)
            if ($gvO.A -gt 0) {
                # The liquid: vanilla's greyscale, scaled by the tint - which is how vanilla itself
                # colours a potion, so the shading bands survive.
                $gvOutR = [int][Math]::Round(($gvO.R / 255.0) * $gvTintR)
                $gvOutG = [int][Math]::Round(($gvO.G / 255.0) * $gvTintG)
                $gvOutB = [int][Math]::Round(($gvO.B / 255.0) * $gvTintB)
                $gv[$gvX, $gvY] = '#{0:X2}{1:X2}{2:X2}' -f $gvOutR, $gvOutG, $gvOutB
            }
        }
    }

    $gvBottle.Dispose()
    $gvOverlay.Dispose()
    return , $gv
}

function New-GvGrapeWine([string]$gvOut) {
    $gw = Get-GvVanillaPotionTint '#7B1E3C'
    Save-GPng $gvOut $gw 16 16
}

# The unfermented half of the same drink: the same bottle, the same liquid mask, and a tint that is
# the wine's red-violet lifted towards the purple of the ripe grapes it is pressed from. Drawing it
# from the same two vanilla files as the wine is what makes the pair read as one drink at two stages,
# which is exactly what they are.
function New-GvGrapeJuice([string]$gvOut) {
    $gj = Get-GvVanillaPotionTint '#8B4FA8'
    Save-GPng $gvOut $gj 16 16
}

# ---------------------------------------------------------------------
# 8/9. block/tank_liquid_grape.png and tank_liquid_grape_wine.png
#      These are not art: they are the tank's liquid surface, and the
#      existing ones (water/sugar/wine) share one 16x16 fully-opaque
#      weave whose channel is base + ((11*x + 7*y) mod 25). Matching the
#      formula - not an eyeballed copy - is what lets the new liquids sit
#      next to the old ones without a seam, and it keeps every pixel
#      opaque, which is the whole of their "structure".
# ---------------------------------------------------------------------
function New-GvTankLiquid([string]$gvOut, [string]$gvBase) {
    $gvGrid = New-GGrid 16 16
    $gvBaseR = [Convert]::ToInt32($gvBase.Substring(1, 2), 16)
    $gvBaseG = [Convert]::ToInt32($gvBase.Substring(3, 2), 16)
    $gvBaseB = [Convert]::ToInt32($gvBase.Substring(5, 2), 16)
    for ($gvY = 0; $gvY -lt 16; $gvY++) {
        for ($gvX = 0; $gvX -lt 16; $gvX++) {
            $gvStep = ((11 * $gvX) + (7 * $gvY)) % 25
            $gvOutR = [int][Math]::Min(255, $gvBaseR + $gvStep)
            $gvOutG = [int][Math]::Min(255, $gvBaseG + $gvStep)
            $gvOutB = [int][Math]::Min(255, $gvBaseB + $gvStep)
            Set-GPx $gvGrid $gvX $gvY ('#{0:X2}{1:X2}{2:X2}' -f $gvOutR, $gvOutG, $gvOutB)
        }
    }
    Save-GPng $gvOut $gvGrid 16 16
}

# --------------------------------------------------------------------- targets
New-GvArt (Join-Path $gvBlockDir 'grape_vine_stage0.png') $gvStage0 'grape_vine_stage0'
New-GvArt (Join-Path $gvBlockDir 'grape_vine_stage1.png') $gvStage1 'grape_vine_stage1'
New-GvArt (Join-Path $gvBlockDir 'grape_vine_stage2.png') $gvStage2 'grape_vine_stage2'
New-GvArt (Join-Path $gvBlockDir 'grape_vine_stage3.png') $gvStage3 'grape_vine_stage3'

New-GvArt (Join-Path $gvItemDir 'grape.png') $gvGrapeItem 'grape'
New-GvArt (Join-Path $gvItemDir 'grape_seeds.png') $gvSeeds 'grape_seeds'
New-GvGrapeWine (Join-Path $gvItemDir 'grape_wine.png')
New-GvGrapeJuice (Join-Path $gvItemDir 'grape_juice.png')

New-GvTankLiquid (Join-Path $gvBlockDir 'tank_liquid_grape.png') '#4A2C63'
New-GvTankLiquid (Join-Path $gvBlockDir 'tank_liquid_grape_wine.png') '#5A1620'

Write-Host "wrote $script:gvWritten grape textures to $gvBlockDir and $gvItemDir"
