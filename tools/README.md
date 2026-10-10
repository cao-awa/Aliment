# tools

Helper scripts used while developing the Aliment willow content. None of them are part
of the built mod — they only regenerate files under `src/main/resources`.

| script | what it does |
| --- | --- |
| `gen_data.ps1` / `gen_data.cmd` | Regenerates **all** data and asset JSON files (blockstates, block/item models, item definitions, worldgen, loot tables, recipes, tags, language files). Uses the vanilla JSON inside the Minecraft jar as the template, so the output always matches the exact format of the Minecraft version in `gradle.properties`. |
| `lang_zh_cn.json` | The Chinese block/item names, read by `gen_data.ps1`. Kept out of the script so the script itself stays pure ASCII and therefore safe to run with either Windows PowerShell 5.1 or PowerShell 7. |
| `lang_ja_jp.json` | The Japanese names, read by `gen_data.ps1`, for the same reason. |
| `gen_textures.ps1` / `gen_textures.cmd` | Regenerates all PNG textures (blocks, items, entity boats, bark and soup sprites, the cauldron liquid surfaces and the mod icon) from scratch with ImageMagick. |
| `gen_glucose_textures.ps1` | The five item sprites of the glucose chain (insulin injection, glucose meter, clean and bloodied test strips, microneedle). Pure `System.Drawing` and no ImageMagick, and it writes only those five files, so it can be re-run on its own after changing one of them. |
| `gen_glass_textures.ps1` | The fermentation tank and condenser pipe frames. Pure `System.Drawing`, writes only those two files. Keeps the copper frame pixel-for-pixel - including the tank's amber inner-corner bevel - and leaves everything inside it transparent, so the only thing it removes from the originals is the decoration that used to sit in the middle. |
| `gen_grapefruit_textures.ps1` | The eight textures of the grapefruit tree: the hanging fruit, the whole fruit and the slice as items, the log side and top, the leaves, the sapling and the **juice**. Pure `System.Drawing`, writes only those eight files. The hanging fruit is drawn in the top half of its canvas because it is rendered by a cross model, and the leaves are greyscale because they are tinted by the biome. The juice is the exception to the hand-drawn rule: it is vanilla's own `potion.png` bottle with vanilla's `potion_overlay.png` liquid tinted to the slice's flesh, which is why it reads `vanilla_potion*.png` from this directory. |
| `gen_grapefruit_wood.ps1` | The grapefruit **wood set's** JSON: every blockstate, block model, item model, item definition, recipe and block loot table, mirrored from the willow's by swapping `willow` for `grapefruit`. Also writes the `aliment:grapefruit_logs` tags and adds the grapefruit entries to the vanilla `planks` / `logs` / `wooden_*` / `signs` / `fence_gates` / `boats` tags. It owns the mirrored files only - the fruit and the tree's own models are hand-written and excluded. |
| `gen_grapefruit_wood_textures.ps1` | The sixteen **wood** textures, made by mapping the willow's palette onto the grapefruit's pixel for pixel rather than redrawing them, so the door panels, trapdoor slats and sign frames stay identical in form. It does not own the tree's bark (`gen_grapefruit_textures.ps1` does), the leaves or the sapling. |
| `gen_grape_textures.ps1` | The nine textures of the grape: the four vine stages, the grape and seed items, the grape-wine bottle, and the two tank liquids. Pure `System.Drawing`, writes only those nine files. Every sprite is stored as **ASCII art** at the top of the script and validated on the way in - `Add-GvArt` throws on a wrong row count, a wrong row length, or a legend character that is not in the map - so the shapes are editable as text and a typo is a startup error rather than a silently mangled sprite. The vine's last two stages read as **bunches**: stage 3 is a canopy with a ripe cluster hanging in it, and the item is the same tapering lattice, 6-6-4-4-2-2 berries wide, under a bare twig. The wine bottle follows `gen_grapefruit_textures.ps1`'s vanilla-potion approach - vanilla's `potion.png` bottle pixel for pixel with the greyscale overlay tinted deep red - and the tank liquids reuse the `channel = base + ((11x + 7y) mod 25)` weave every other `tank_liquid_*` sprite already uses, so the new liquids cannot be told apart from the old ones. |
| `verify-datapack/` | A dev-only data pack that proves the willow world generation actually runs. See below. |
| `gen_effect_textures.ps1` | The two **status effect** icons, `fever.png` and `pain.png`, at `assets/aliment/textures/mob_effect/`. Pure `System.Drawing`, 18x18 - which is not a style choice but the box `Hud` blits an effect sprite into - and both are stored as **ASCII art** at the top of the script, so the shapes are reviewable in a diff rather than having to be rendered. It uses a case-*sensitive* dictionary for the palette, because PowerShell's own `@{}` ignores case and the thermometer needs `R` and `r` to be mercury and its shadow; it throws on a row of the wrong length or a character that is not in the palette.

> **`gen_data.ps1` is stale - do not run it wholesale.** It predates the grapefruit tree, the glucose
> chain and the fermentation set, so a full run **deletes work that is not in its tables**:
>
> * the 26 `grapefruit*` language keys, from all three files it writes (`ko_kr.json` it does not write
>   at all, so that one is left alone);
> * the 14 keys of the glucose chain, the alcohol tooltip and `beer_cauldron` etc., which it does not
>   know about but overwrites the files of;
> * the `grapefruit` entries it would have to add to the vanilla `planks` / `logs` / `wooden_*` /
>   `signs` / `leaves` / `saplings` / `flower_pots` tags - `gen_grapefruit_wood.ps1` adds those, and
>   `gen_data.ps1` rewrites the same files without them.
>
> It also rewrites every JSON with LF, which on a CRLF checkout makes `git diff` show all ~400 files
> as modified and hides the real change. If you must change something it owns, either edit the small
> generated file directly and then fix the same string in the script, or back up `lang/` and every
> grapefruit-owned tag first and re-apply afterwards. The generators that are safe to re-run on their
> own are the ones listed here as owning a short, explicit file list.

Both generators are idempotent: running them twice produces byte-identical output.

> **Before running `gen_data.ps1`, read the diff.** It has fallen behind the hand-written content and
> will silently drop keys and files that were added to `src/main/resources` afterwards - the sleep
> refusal (`block.aliment.bed.too_stimulated`), the brewing names (`block.aliment.beer_cauldron`,
> `item.aliment.alcohol`, `item.aliment.beer`, `tooltip.aliment.alcohol.concentration`,
> `gui.aliment.jei.category.grindstone`), the `alcohol_cauldron` / `fermentation_tank` blockstate
> variants, the whole glucose chain, and everything the grapefruit added (`block.aliment.grapefruit*`,
> `item.aliment.grapefruit_slice`, the grapefruit wood set, and the `grapefruit`, `grapefruit_grove`
> and `block_state_provider/grapefruit` worldgen). Everything it does not know about has to be put
> back by hand. Its name sources `lang_zh_cn.json` and `lang_ja_jp.json` are stale in the same way:
> they still only carry the willow-era names, so they are not a complete list of what the mod adds.
>
> `gen_textures.ps1` still carries a hard-coded `$root` for the checkout it was written in; check that
> before running it. `gen_glucose_textures.ps1`, `gen_glass_textures.ps1`,
> `gen_grapefruit_textures.ps1`, `gen_grapefruit_wood.ps1`, `gen_grapefruit_wood_textures.ps1`,
> `gen_grape_textures.ps1` and `gen_effect_textures.ps1` derive their paths from `$PSScriptRoot`.

## Requirements

* **ImageMagick** at `.tools\imagemagick\magick.exe`. It is not committed to the repository
  (`.tools/` is git-ignored); download the portable Q16 x64 build from
  <https://imagemagick.org/download/> and unpack it there.
* The Minecraft jars that Loom downloads into `.gradle\loom-cache\minecraftMaven\...`
  (created automatically by any Gradle build).

## Running them

```
tools\gen_data.cmd
tools\gen_textures.cmd
pwsh -ExecutionPolicy Bypass -File tools\gen_glucose_textures.ps1
pwsh -ExecutionPolicy Bypass -File tools\gen_glass_textures.ps1
pwsh -ExecutionPolicy Bypass -File tools\gen_grapefruit_textures.ps1
pwsh -ExecutionPolicy Bypass -File tools\gen_grapefruit_wood_textures.ps1
pwsh -ExecutionPolicy Bypass -File tools\gen_grapefruit_wood.ps1
pwsh -ExecutionPolicy Bypass -File tools\gen_grape_textures.ps1
pwsh -ExecutionPolicy Bypass -File tools\gen_effect_textures.ps1
```

Both `.ps1` files are pure ASCII, so they also run fine when invoked directly:

```powershell
powershell -ExecutionPolicy Bypass -File tools\gen_data.ps1
```

## verify-datapack

`verify-datapack` is a small data pack that runs on world load and counts willow blocks in the
generated chunks, reporting its findings with `say`. It is how the world generation of this mod
was verified end to end.

To use it:

1. Copy it into a test world: `run/world/datapacks/aliment-verify/`.
2. Start the dev server with a superflat **river** world so every generated chunk goes through
   the river biome that Aliment injects the willow feature into. In `run/server.properties`:

   ```
   level-type=minecraft:flat
   generator-settings={"biome":"minecraft:river","features":true,"lakes":false,"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"structure_overrides":[]}
   ```

3. Run `gradlew runServer`. After a few seconds the log should contain:

   ```
   [Server] Aliment_RIVERWORLD_VINES_OK
   [Server] Aliment_RIVERWORLD_TREES_OK
   [Server] Aliment_RIVERWORLD_LEAVES_OK
   ```

Delete the world afterwards — the data pack clears the blocks it inspects.

Add `pause-when-empty-seconds=0` to `run/server.properties` while testing. Without it the
dedicated server stops ticking after 60 s with no players, and any tick-driven test stalls.

## Self test

There are two development-only entrypoints. Neither is referenced by `fabric.mod.json`, so both
are dead code in the shipped jar.

### `dev/AlimentSelfTest.kt` - content

Drives the player-facing willow features with Fabric's `FakePlayer` on a headless server: axe
stripping, grindstone grinding, filling the cauldron, the 60 second campfire cook, taking a serving
with a bottle and with a bowl, and growing a willow next to a pool to check that the trunk leans
towards the water. Then the plants: sowing a mandrake seed on dirt, grass, coarse dirt and farmland
(through the real item path), bone meal through all four stages, the loot table dropping 1-2 fruit
when ripe and nothing at all before that, and - for the grape vine - the right-click harvest itself,
counted by the `ItemEntity`s that actually land on the ground rather than by the returned
`InteractionResult`, so a pick that claimed success without dropping anything still fails. The grape
vine is picked twice in a row to prove the fall-back to `age=1` is a real re-ripening rather than a
constant, and bone meal is applied through the interaction path as well as directly, because a
harvest that forgot to fall through below `age=3` would swallow the click while the direct call kept
passing. The seeds recipe being in the recipe manager, the mandrake
patches being attached to the plains and the swamps and to nothing else, the gymnopilus' three
cooking recipes loading with the same timings raw beef has, and its patches being attached to the dark
forest and the taiga and to nothing else. It reads the worldgen answer out of the biome registry
rather than by scanning a world, which is what the patches are actually decided by, and verifies the advancement tree and triggers, and the fermentation tank and condenser pipe distillation machinery, and - where Farmer's Delight is installed - that its gated recipes parsed at all, which is the only thing that notices a malformed condition or container, since a recipe that fails to parse simply vanishes without an error. It then reads the cooking pot's grapefruit juice back out of the recipe manager and pins it down: one grapefruit slice rather than two, one sugar, and a glass bottle to draw into. 275 checks, or 243 when Farmer's Delight is not installed.

### `dev/AlimentPhysiologySelfTest.kt` - physiology

Runs the whole model headlessly in a few milliseconds: homeostasis, infection clearance,
untreated immune storm, salicin and dexamethasone control, overdose, drug metabolism, the immune
competence curve, the mediator weights, thirst over a game day, over-hydration, electrolyte
dilution from heavy drinking, that each electrolyte's own leak grows with its own concentration, the iodine store draining to its floor in exactly five game days and
what a day's kelp does about it, and that a surplus of iodine - unlike a deficit - leaves faster than
that flat leak, blood glucose and the insulin index, the two mandrake alkaloids
(the three fever steps, the blur
thresholds, the cap and the metabolism, and that the drug fever stacks on an infection's), the two
gymnopilus compounds (that psilocybin is inert and converts one for one over half a day, that
psilocin leaves at a flat 1.3 a day so five doses are ten game days, all four trip stages either
side of their thresholds, and the two fever steps), the
temperature model (fever, hypothermia, the environment, the
thyroid) and the fever command. Then it exercises the mixins and the symptom layer end to end: it
really eats raw meat through `ItemStack.finishUsingItem` and counts the infection rate, eats a
mandrake fruit and its seeds and a raw and a cooked gymnopilus to check what each carries, reads the
two mushrooms' food values off their item components, drinks all
thirteen of the drinks to check the 15 water each, drinks salt water and sea water to check the
minerals, checks that swamp water is foul and sea water is not, probes every mineral on both sides of
both of its thresholds, asserts the camera-shake chance is zero for every state the player cannot
see, counts the two per-second contagion dice, checks which screen effects each fever tier asks for,
that the drug blur stacks with them and that exactly one trip stage is on the screen at a time,
drives a real `GrindstoneMenu` to prove the two grindstone
mixins applied, rolls the chest-loot pool
the mod actually adds, freezes a creative player and respawns a dead one, and checks the exhaustion
multiplier, the mining penalty and the synced client state. It also exercises the ephedra
and ephedrine system: eating ephedra (+0.5 ephedrine), purified ephedrine (+2.5 ephedrine),
capping at 5.0, granting Haste I when > 1.0, and 1-game-day (24000 ticks) linear metabolism decay.
It also places a real bed and calls `startSleepInBed` itself, to prove the stimulant sleep
restriction returns vanilla's own `BedSleepingProblem` carrying our message above 0.5 ephedrine and
nothing of ours at or below it. It also enumerates every item and entity
type the mod registers and fails, naming the key, if any of them has no name in `en_us.json` or
`zh_cn.json` or `ja_jp.json`, and asserts that all four shipped languages (`en_us`, `zh_cn`,
`ja_jp`, `ko_kr`) define exactly the same key set.

It also exercises the glucose chain end to end: fasting from 5.0 to 3.49 in exactly two game days and
the slower fall past the 3.5 floor, a meal of 6 to 30 mmol/L back inside the reference range within
half a game day with the higher ones falling faster, the insulin index flat while fasting and climbing
with a meal, what each of the four food classes is worth, the three hypoglycaemia tiers either side of
their thresholds, and the diagnostic chain through the real `UseItemCallback` - a microneedle starts
the fifteen-second bleed, a test strip on that finger becomes a bloodied one, and the meter prints the
reading.

It also exercises the two mechanisms that make a surplus leave faster than a deficit. Each
electrolyte's leak is scaled by its own concentration, and the check for that cannot simply compare
how much a loaded body lost against a healthy one - under any model the loaded body loses more,
because homeostasis is pulling it down either way. So the leak is isolated by differencing: the same
body is ticked once with a full bladder and once with a normal one, and the gap is the leak with the
homeostatic pull cancelled, which is then measured at two sodium levels and required to sit at the
concentration ratio (180/140). Restoring the flat leak drops that ratio to exactly 1.0 and fails
exactly those two checks. Iodine is the same idea on its own term: a surplus above normal is cleared
faster than the flat five-day leak, while a normal store is still required to lose exactly the flat
figure - the two claims pull against each other on one line of the model, so both are pinned. Zeroing
the surplus term fails the surplus half and leaves the depletion half green.

And it exercises the grapefruit: the CYP3A4 curve pinned to its six calibration points and swept
across the whole naringin range in steps of a hundredth of a slice, asserting that no single step
moves the index more than the steepest segment's own slope allows - which is what separates a curve
from a staircase, and which the step function this replaced fails by moving 25 points in one step -
and that the descent never turns back up or leaves the reported range, naringin filling to its
cap and clearing over exactly one game day, the tick reading the enzyme index off the naringin, and
berberine proved to fall at the written rate at the 85 baseline and at whatever fraction of it the
index reports below - then the same dose of coptis cleared with and without a body full of grapefruit,
which takes about 9,400 ticks alone against about 20,100 with the fruit. It eats a slice through the real item
path to check 2 hunger, 3 saturation, 5 water, 1 naringin, the plant-food glucose, carrot-grade
vitamin C and that it can be eaten on a full stomach, drinks a bucket of milk to check the standard
15 water, and eats eleven slices to prove the cap holds.

The grapefruit **wood set** is checked through the loaded block tags rather than through the files,
because tag membership is what a wood set actually is: each of the four log-shaped blocks is asserted
to be in `aliment:grapefruit_logs`, in `minecraft:logs` and axe-mineable; each shaped block in the one
vanilla tag that makes it craftable and mineable; a willow log is asserted *not* to be in the
grapefruit tag; and both boats are checked to be `BoatItem`s whose entity types are registered and
distinct from the willow's. 832 checks, all passing.

### Running either one

Temporarily add it to the `main` entrypoint in `src/main/resources/fabric.mod.json`:

```json
"main": [
  "com.github.kusa233.aliment.Aliment",
  "com.github.kusa233.aliment.dev.AlimentPhysiologySelfTest"
]
```

then `gradlew runServer` and look for the `SELFTEST` lines. The content test takes about 75 seconds
(most of it the 60 second cook); the physiology test finishes on the first server tick. Remove the
entrypoint again afterwards.

Note: a player-less dev server has no entity-ticking chunks, so dropped items never show up in
entity queries there. The bark drop is therefore asserted by the code path rather than by looking
for the dropped item.
