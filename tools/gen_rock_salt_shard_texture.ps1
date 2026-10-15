<#
    gen_rock_salt_shard_texture.ps1 - the crushed rock salt shard.

    `AlimentItems.CRUSHED_ROCK_SALT` is what `AlimentBlocks.ROCK_SALT_ORE` drops when it is mined,
    and it grinds into nine crude salt. It is the only sprite of the salt chain that is a *piece*
    rather than a powder or a heap, so it is the one that has to read as solid mineral at inventory
    size.

    Drawn in the style of vanilla's raw ore items (`raw_iron.png` and friends), which is the
    reference this item is asking to be read against: a single lumpy chunk filling most of the tile,
    a deep tonal ramp rather than a handful of flat facets, a dark rim around the whole silhouette
    that is itself shaded - lighter along the top-left, darkest along the bottom-right - and a
    jagged lower edge with a gap in it, so the thing looks broken off rather than cut. The reading
    is "a lump of something you dug out of the ground", which is exactly what this is.

    The ramp is the ore block's own crystal palette, interpolated up to the depth the raw ores use.
    `rock_salt_ore.png` draws its halite crystals in five tones, and all five are here - `FFF0F3`,
    `F2B8C2`, `D47A88`, `9C4856` and `632B34` - with the midpoint between each adjacent pair filled
    in, giving nine steps where raw iron has eight. So the shard is not merely *pink like* the ore,
    it is drawn in the ore's exact crystal colours, which is what stops it being mistaken for the
    beige `crude_salt.png` heap it grinds into. The two sit next to each other in the creative tab
    and in the grindstone, so "pink chunk in, beige pile out" is the reading the pair has to carry.

    That palette choice is not an analogy, and it is worth recording why, because the chain is
    longer than it looks. `gen_textures.ps1` builds `rock_salt_ore.png` by recolouring vanilla
    `iron_ore.png` (`New-RockSaltOreBlock`), and the five tones it maps to are exactly the five
    above. Three of the tones it reads *out* of the vanilla ore - `D8AF93`, `AF8E77` and `887455` -
    are tones `raw_iron.png` still carries today, because the raw item and the ore are drawn from
    the one rock palette. So recolouring raw iron through that same table lands on this ramp:
    the shard is vanilla's raw-iron sprite rendered in the very translation the ore block already
    went through, which is as close to "the ore item that vanilla never drew" as can be had.

    Kept as its own script rather than added to `gen_textures.ps1` for the reason
    `gen_glucose_textures.ps1` and `gen_glass_textures.ps1` are separate: this writes one file and
    can be re-run on its own after changing the art. It also has no ImageMagick dependency, and
    `gen_textures.ps1` still carries a hard-coded `$root` for the checkout it was written in.

    The grid below *is* the image. It is ASCII art rather than rectangle calls so a change to the
    shape is reviewable in a diff, and the palette is validated on the way in - a character that is
    not in the map fails the run instead of writing a transparent hole.

    Run with (from the repository root):
        powershell -ExecutionPolicy Bypass -File tools\gen_rock_salt_shard_texture.ps1

    Idempotent: the target is rewritten from the grid on every run.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$sroot = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$sdir = Join-Path $sroot 'src\main\resources\assets\aliment\textures\item'
if (-not (Test-Path $sdir)) { New-Item -ItemType Directory -Path $sdir -Force | Out-Null }

<#
    The nine-step pink halite ramp, lightest first. The odd numbers are the ore block's own crystal
    colours, read out of `rock_salt_ore.png`; the even ones are the midpoint between their
    neighbours, which is what turns five flat facets into a ramp the way the raw ores are shaded.
    `9` is the ore's deepest tone and doubles as the crevice colour. `.` is fully transparent, so
    the empty cells around the chunk need no special case.

    An ordinal dictionary, because PowerShell's own `@{}` ignores case and art grids are easier to
    read when case can carry meaning.
#>
$shardPalette = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
$shardPalette.Add('.', '#00000000')   # transparent
$shardPalette.Add('1', '#FFFFF0F3')   # the ore crystal's own highlight
$shardPalette.Add('2', '#FFF8D4DA')   # midpoint of 1 and 3
$shardPalette.Add('3', '#FFF2B8C2')   # the ore crystal's light tone
$shardPalette.Add('4', '#FFE399A5')   # midpoint of 3 and 5
$shardPalette.Add('5', '#FFD47A88')   # the ore crystal's mid tone
$shardPalette.Add('6', '#FFB8616F')   # midpoint of 5 and 7
$shardPalette.Add('7', '#FF9C4856')   # the ore crystal's dark tone
$shardPalette.Add('8', '#FF803A45')   # midpoint of 7 and 9, and the lit rim
$shardPalette.Add('9', '#FF632B34')   # the ore's deepest tone: the shadow rim and the crevices

<#
    One chunky lump, lit from the upper-left the way the heap and the stirring rod are, sitting
    slightly off centre and reaching the right-hand edge so the mass does not read as a neat oval.
    The body runs from the bright `1`-`3` of the upper-left down through the mid tones to the
    `6`-`7` of the lower-right. The rim is `8` where it faces up or left and `9` where it faces
    down or right, which is what raw iron does - even the outline is shaded.

    The shading is **dithered**, and that is the single most important thing about this grid. The
    first attempt here was a clean diagonal ramp, and rendered at 16x16 it read as a smoothly lit
    sphere rather than as rock. Raw iron does not shade that way: look along any of its rows and the
    tone alternates constantly - `721212324777`, `846545556776557` - which is what makes its surface
    read as crystalline and irregular. So the tone of each interior pixel is its diagonal position
    nudged one step up or down by a fixed hash of its coordinates: neighbouring pixels land on
    different steps, the overall shading still runs light-to-dark across the lump, and the scatter
    is identical on every run, so the script stays idempotent and this grid is still the image.

    The lower edge is jagged on purpose: a foot at the bottom-left is cut off from the mass beside
    it by a one-pixel gap, and the body to its right hangs a row lower again. Every raw ore item has
    a break somewhere along its lower edge for the same reason - it is what says the piece was
    broken off rather than cut and polished.
#>
$shardGrid = @(
    '................'
    '..88888.........'
    '.812113888......'
    '.82112233388....'
    '8111312243438...'
    '81131144344458..'
    '813224334546568.'
    '832122533466579.'
    '812245344655669.'
    '824445346555669.'
    '8243455456577778'
    '.843369976677779'
    '..8999..87677779'
    '.........877779.'
    '..........8999..'
    '................'
)

function Save-ShardPng([string]$path, [string[]]$grid, $palette) {
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

Write-Output 'rock salt shard:'
Save-ShardPng (Join-Path $sdir 'crushed_rock_salt.png') $shardGrid $shardPalette
