<#
    gen_medicine_cabinet_textures.ps1 - the medicine cabinet's three faces.

    `AlimentBlocks.MEDICINE_CABINET` is the apothecary's job site and the mod's first piece of
    furniture, so it is the one block here whose art has to survive being looked at rather than
    merely recognised in a hotbar. It is drawn as vanilla draws its own storage blocks: a plank
    carcase with a recessed opening, and the front is the only face that says what the block is for.

    Three files, because the model is `minecraft:block/orientable`:

      medicine_cabinet_front.png  the open front: two shelves of bottles behind a plank frame
      medicine_cabinet_side.png   a closed plank side, vertical boards
      medicine_cabinet_top.png    the same boards run the other way, so a stack of cabinets does not
                                  read as one extruded texture

    The front face is the point of the sprite. The recipe is planks around glass bottles, so the
    front shows exactly that: a wooden frame, two shelves, and six bottles in the three liquids the
    cabinet's own contents are drawn from - a green, an amber and a blue. The three colours are not
    arbitrary: `coptis_potion` and `licorice_potion` are the two level-1 trades and are drawn in
    `gen_potion_textures.ps1` from the same green, the amber is the tincture tone the injection
    sprites use, and the blue is the clear-glass tone of `salt_water.png`. A cabinet stocked with
    recognisable bottles is what connects the block to the trades it unlocks.

    The palette is the mod's wood, shared by both board faces: `gen_textures.ps1` draws the willow
    and grapefruit plank sets from `B8945F`/`9E7B4C`/`6B5334`, and this reuses that ramp so a cabinet
    standing beside a willow shelf does not look like a different mod. The glass and liquid tones are
    sampled from the existing item sprites rather than invented.

    Kept as its own script for the reason `gen_rock_salt_shard_texture.ps1` is: it writes three files,
    has no ImageMagick dependency, and can be re-run alone after an art change. `gen_textures.ps1`
    still carries a hard-coded `$root` for the checkout it was written in.

    The grids below *are* the images. ASCII art rather than rectangle calls so a change to the shape
    is reviewable in a diff, and `Save-CabinetPng` validates both the row width and the palette on
    the way in - a character that is not in the map fails the run instead of writing a transparent
    hole.

    Run with (from the repository root):
        powershell -ExecutionPolicy Bypass -File tools\gen_medicine_cabinet_textures.ps1

    Idempotent: every target is rewritten from its grid on each run.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$croot = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$cdir = Join-Path $croot 'src\main\resources\assets\aliment\textures\block'
if (-not (Test-Path $cdir)) { New-Item -ItemType Directory -Path $cdir -Force | Out-Null }

<#
    One palette for all three faces, so the wood is literally the same wood. An ordinal dictionary
    because PowerShell's own `@{}` ignores case and art grids are easier to read when case can carry
    meaning - the liquids are the lowercase keys.

    `1`-`3` are the plank ramp from `gen_textures.ps1` with the seam tone dropped in; `4` is the
    shelf board, a shade off the seam so a shelf reads as a separate piece of timber; `5` is the
    inside of the cabinet, which has to be dark enough that the bottles are the bright thing.
#>
$cabinetPalette = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
$cabinetPalette.Add('1', '#FFC9A97A')   # plank highlight: a lit board edge
$cabinetPalette.Add('2', '#FFAE8B5C')   # plank face
$cabinetPalette.Add('3', '#FF6E5433')   # plank seam, and the shaded outer edge
$cabinetPalette.Add('4', '#FF7A5C38')   # the shelf board
$cabinetPalette.Add('5', '#FF3E2E1B')   # the shadowed inside of the cabinet
$cabinetPalette.Add('6', '#FFDCEFF2')   # glass: the lit shoulder of a bottle
$cabinetPalette.Add('7', '#FFA9CBD1')   # glass: the body of an empty part of a bottle
$cabinetPalette.Add('8', '#FFD79A3C')   # amber contents
$cabinetPalette.Add('9', '#FF7FA85C')   # green contents
$cabinetPalette.Add('a', '#FF5F9BD0')   # blue contents

<#
    The front. Reading it top to bottom: a lit top edge, two board rows, then the recessed opening.

    The frame is two pixels wide on the left and right, which at 16x16 is the thinnest that still
    reads as a frame once the block is on screen - a one-pixel border disappears into the neighbouring
    faces. The opening is therefore columns 2-13.

    Six bottles, three per shelf, each three pixels wide and three tall in columns 3-5, 7-9 and 11-13
    with a dark column between them. Each bottle is a lit rim (`676`), then two rows of glass edges
    around its contents (`696`), which is the smallest shape that still reads as a bottle rather than
    as a block of colour. The two shelves carry the same six bottles so the front reads as *stocked*
    rather than as arranged; the tint is what distinguishes them, and every bottle on the top shelf
    repeats on the bottom.

    Rows 0-2 and 13-15 are the carcase, and the middle row of each band is the seam tone rather than
    the face tone, so the cabinet has a visible top and bottom lip.
#>
$cabinetFront = @(
    '1111111111111111'
    '1222222222222221'
    '1233333333333321'
    '1255555555555521'
    '1256765676567621'
    '125696568656a621'
    '125696568656a621'
    '1244444444444421'
    '1255555555555521'
    '1256765676567621'
    '125696568656a621'
    '125696568656a621'
    '1244444444444421'
    '1233333333333321'
    '1222222222222221'
    '1111111111111111'
)

<#
    A closed side: vertical boards, seams down columns 0, 5, 10 and 15.

    The four grain rows cycle rather than repeating in a period of one, because a single grain row
    tiled sixteen times reads as a repeating pattern at block scale. The topmost row is the face tone
    and the bottom row the seam tone, so the light still comes from above on a face that has no other
    shading to say so.
#>
$cabinetSide = @(
    '2222222222222222'
    '3122232122322123'
    '3212231221321223'
    '3122132212312213'
    '3221231222321223'
    '3122232122322123'
    '3212231221321223'
    '3122132212312213'
    '3221231222321223'
    '3122232122322123'
    '3212231221321223'
    '3122132212312213'
    '3221231222321223'
    '3122232122322123'
    '3212231221321223'
    '3333333333333333'
)

<#
    The top: the same boards run the other way, seams across rows 0, 5, 10 and 15.

    This is the one face a player looks straight down at, and it is the reason the side and the top
    are not the same file: with both faces showing vertical boards, a column of cabinets reads as a
    single extruded texture with no corners. Running the grain across the top gives every cabinet a
    visible lid.
#>
$cabinetTop = @(
    '3333333333333333'
    '1222122212221222'
    '2112221222122212'
    '1222122212221222'
    '2211222122212221'
    '3333333333333333'
    '1222122212221222'
    '2112221222122212'
    '1222122212221222'
    '2211222122212221'
    '3333333333333333'
    '1222122212221222'
    '2112221222122212'
    '1222122212221222'
    '2211222122212221'
    '3333333333333333'
)

function Save-CabinetPng([string]$path, [string[]]$grid, $palette) {
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

Write-Output 'medicine cabinet:'
Save-CabinetPng (Join-Path $cdir 'medicine_cabinet_front.png') $cabinetFront $cabinetPalette
Save-CabinetPng (Join-Path $cdir 'medicine_cabinet_side.png') $cabinetSide $cabinetPalette
Save-CabinetPng (Join-Path $cdir 'medicine_cabinet_top.png') $cabinetTop $cabinetPalette
