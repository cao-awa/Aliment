<#
    gen_apothecary_villager_texture.ps1 - the apothecary's robe overlay.

    A villager's profession texture is not an entity texture. It is a 64x64 *overlay* drawn on top of
    the shared `villager.png` base, and `VillagerProfessionLayer` resolves it from the profession's
    own registry id:

        textures/entity/villager/profession/<profession path>.png

    with the *namespace taken from the profession*. That last part is the trap, and it is why the
    apothecary rendered as the magenta missing-texture square: the profession is `aliment:apothecary`,
    so the layer looks for `aliment:textures/entity/villager/profession/apothecary.png` - inside this
    mod's own assets, not in `minecraft`. Nothing in the profession's registration or its lang entry
    mentions a texture at all, so a missing one is invisible to the compiler, to the datapack loader
    and to the server-side self test; it appears only when a client renders a villager that has
    traded up to the job.

    The layer skips the overlay entirely for `minecraft:none` and for babies, then draws it with
    `renderColoredCutoutModel`, so this file must be 64x64 and everything not painted must be fully
    transparent.

    ## The painted region

    The silhouette is the robe: columns 0-27, rows 38-63. Those bounds are not a guess - they are the
    region every vanilla profession overlay agrees on. `cleric.png`, `librarian.png` and `armorer.png`
    all paint inside exactly that rectangle and nothing outside it (measured pixel by pixel), which is
    the villager model's lower-body UV island. Filling the whole rectangle rather than tracing one
    vanilla file's ragged edge keeps the apron legible after the model's own UV seams split it across
    the front, sides and back of the legs.

    The head is deliberately left untouched. `farmer.png` paints rows 0-11 and ships an `.mcmeta`
    declaring `"hat": "full"`, which replaces the head wholesale; doing that without the matching
    metadata hides the villager's face and nose. A plain apron is enough to tell an apothecary from an
    unemployed villager, and it cannot break the head.

    ## Colours

    Pharmacist's green and an off-white linen apron, with the amber the mod's own tinctures and the
    cabinet's bottles already use (`gen_medicine_cabinet_textures.ps1` draws its amber from the same
    tone). The green is dark enough to read against `villager.png`'s brown robe, which is what the
    overlay is composited over.

    Run with (from anywhere):
        powershell -ExecutionPolicy Bypass -File tools\gen_apothecary_villager_texture.ps1

    Idempotent: the target is rewritten from the grid on each run.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'src\main\resources\assets\aliment\textures\entity\villager\profession'
New-Item -ItemType Directory -Force -Path $out | Out-Null

# Keyed by the single character written into the grid. 'FFRRGGBB', alpha first.
$palette = @{
    '.' = '00000000'   # transparent: the base villager texture shows through
    '1' = 'FF3F5B4A'   # robe, base
    '2' = 'FF52705C'   # robe, lit edge
    '3' = 'FF2C4338'   # robe, deep shade and the hem
    '4' = 'FFE4DAC4'   # apron, linen
    '5' = 'FFC6BAA0'   # apron, shade
    '6' = 'FFC98A32'   # amber, the cabinet's bottle tone
    '7' = 'FFE0B45C'   # amber, lit
}

$size = 64
$grid = New-Object 'string[][]' $size
for ($y = 0; $y -lt $size; $y++) {
    $grid[$y] = New-Object 'char[]' $size
    for ($x = 0; $x -lt $size; $x++) { $grid[$y][$x] = '.' }
}

# Fills an inclusive rectangle, clipped to the grid so an over-wide apron is a bug in the numbers
# above rather than a silent index error.
function Set-Rect([int]$x0, [int]$y0, [int]$x1, [int]$y1, [char]$c) {
    for ($y = $y0; $y -le $y1; $y++) {
        for ($x = $x0; $x -le $x1; $x++) {
            $grid[$y][$x] = $c
        }
    }
}

# --- the robe: the whole lower-body island, so the model's UV seams cannot cut a hole in it
Set-Rect 0 38 27 63 '1'

# --- the belt, at the top of the island
Set-Rect 0 38 27 38 '6'
Set-Rect 0 39 27 39 '7'
Set-Rect 0 40 27 40 '1'

# --- the apron: inset, with the robe visible as a border on both sides
Set-Rect 4 41 23 56 '4'
Set-Rect 4 57 23 57 '5'

# --- apron shading: a shade column down each side of the panel
Set-Rect 4 41 5 57 '5'
Set-Rect 22 41 23 57 '5'

# --- the amber cross, the one mark that says "this is the one who sells the medicine".
#     Vertical stroke first, then the arms, so the join is solid amber.
Set-Rect 12 43 13 52 '6'
Set-Rect 8 46 17 47 '6'
#     A lit top edge on the vertical stroke only, so the cross is not a flat silhouette.
Set-Rect 12 43 12 52 '7'

# --- the hem
Set-Rect 0 58 27 63 '3'
Set-Rect 0 58 27 58 '2'

# --- write it
$bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $size; $y++) {
    for ($x = 0; $x -lt $size; $x++) {
        $key = [string]$grid[$y][$x]
        if (-not $palette.ContainsKey($key)) { throw "the grid uses '$key', which is not in the palette" }
        $hex = $palette[$key]
        $a = [Convert]::ToInt32($hex.Substring(0, 2), 16)
        $r = [Convert]::ToInt32($hex.Substring(2, 2), 16)
        $g = [Convert]::ToInt32($hex.Substring(4, 2), 16)
        $b = [Convert]::ToInt32($hex.Substring(6, 2), 16)
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($a, $r, $g, $b))
    }
}

$target = Join-Path $out 'apothecary.png'
$bmp.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Output "  apothecary.png  ${size}x${size}  -> $target"
