[English Version](TECHNICAL.md) | [中文版本](TECHNICAL_zh.md)

# Aliment Technical Architecture and Developer Guide

This document details the software architecture, engineering standards, cross-language interop seams, build system mechanics, and automated test harness of the **Aliment** mod.

Target Platform: **Minecraft 26.3**, built on **Fabric Loader 0.19.5** and **Fabric API 0.161.0+26.3**.

---

## 1. Multi-Language Tiered Architecture

To guarantee the pure mathematical integrity of physiological calculations and ensure decoupling from Minecraft platform logic, the project enforces a strict, unidirectional four-tier architecture:
`src/main/scala` -> `src/main/kotlin` -> `src/main/java` -> `src/client`

```
   src/main/scala (Scala 3.9)
   [Pure numerical model, zero Minecraft/Fabric dependencies]
             │
             ▼
   src/main/java (Java 25)
   [AlimentModelBridge: Cross-language seam and type barrier]
             │
             ▼
   src/main/kotlin (Kotlin 2.4)
   [Minecraft game logic: Registries, Data Attachments, Block Entities, Events & Symptoms]
             │
             ▼
   src/client (Kotlin + Java)
   [Client HUD, Camera oscillations, Fog convergence, Post-processing Shaders]
```

### 1.1 Scala 3 Model Layer (`src/main/scala/.../physiology/model`)
- **Responsibilities**: Houses all differential equations, physiological steady states, electrolyte clinical reference ranges, bell-shaped immune clearance curves, in vivo pharmacokinetics, and per-tick numerical state integration.
- **Pure Function Invariants**: This layer strictly prohibits importing any Minecraft, Kotlin, or Fabric packages. All inputs and outputs are pure numeric primitives and standard immutable case classes (`ModelState`, `ModelMineral`, `ModelMediators`, `ModelElectrolytes`, `ModelTraceElements`, `ModelDrugs`, `ModelEnzymes`).
- **Stateless Computation**: `Physiology.tick(...)` consumes the current state and environmental parameters, functionally returning the integrated next state; all active compounds and alkaloids are encapsulated in `ModelDrugs` and metabolically stepped by `Physiology.stepDrugs`, and the clearance pathways that act on them are encapsulated in `ModelEnzymes` - a drug is what the body is carrying, an enzyme index is how fast it is being cleared.

### 1.2 Java Interop Seam (`src/main/java/.../physiology/AlimentModelBridge.java`)
- **Root Justification**: When the Kotlin K2 compiler references a class mentioning a Scala type, it eagerly attempts to resolve all super-interfaces (including `scala.Product`). Even with a correctly configured classpath, Kotlin fails with `Cannot access 'scala.Product'`.
- **Architectural Constraints**:
  - `AlimentModelBridge.java` is the **only file in the entire repository permitted to mention Scala types**.
  - All Scala types must remain strictly encapsulated within `private` internal fields and method bodies, never exposed as `public` fields, parameters, or return types.
  - Exposes only Kotlin-side storage carriers (`AlimentData`) and Java primitive types to the Kotlin layer.
  - All constants are defined exclusively in the Scala model and re-exported via static methods on the Bridge to prevent dual-threshold drift.

### 1.3 Kotlin Domain Layer (`src/main/kotlin/...`)
- **Responsibilities**: Implements all engine integration and game mechanics.
  - **Data Persistence and Codec Extension**: Binds `AlimentData` to Player entities using the Fabric Data Attachment API. To bypass the Mojang DataFixerUpper `RecordCodecBuilder.instance.group(...)` limit of 16 fields (`Products.P16`), a flattened auxiliary `Compounds` record and nested `MapCodec` are inlined into the root Codec, maintaining flat NBT backward compatibility while supporting extensive biomarkers.
  - **Symptom Tick Engine**: `AlimentSymptoms` evaluates per-tick core temperature, dehydration penalties, electrolyte imbalance effects (slowness, nausea, blindness), ephedrine digging haste, etc.
  - **Ingestion Hooks**: `AlimentIngestion` intercepts food, drink, and medicine consumption to update hydration, electrolyte pools, and pathogen seeds.
  - **Interactions**: `AlimentInteractions` manages sneak-grinding, cauldron brewing, injection syringes, and related interactions.

### 1.4 Client Rendering Layer (`src/client`)
- **HUD Extensions**: `AlimentThirstHud` renders an independent 10-pip thirst bar above the player's health indicators (supporting empty, half, and full states).
- **Post-Processing Shaders**: Screen distortion, chromatic aberration, and fever blur shaders triggered during psilocin intoxication, hyperthermia, or mandrake delirium.
- **Dynamic Fog Shaders**: During anticholinergic intoxication, client Mixins contract render distance fog down to 8 blocks to simulate pupil dilation (mydriasis) and loss of visual accommodation.

---

## 2. Core Build System and Compiler Protections

### 2.1 Independent `compileModelScala` Task
The standard Gradle Scala plugin's `compileScala` task unconditionally depends on `compileJava`, regardless of whether Java sources exist in that source set. This creates an unresolvable cyclic dependency:

```
compileJava -> compileKotlin -> compileScala -> compileJava
```

**Solution**:
A custom `ScalaCompile` task named `compileModelScala` is explicitly configured in `build.gradle.kts` with four underlying conventions:
1. `incrementalOptions.analysisFile`
2. `incrementalOptions.classfileBackupDir`
3. `targetCompatibility`
4. `javaLauncher`
The default `compileScala` task is emptied of sources. Compiled model classes are injected into downstream compilation classpaths as plain file dependencies (`files(compileModelScala)`).

### 2.2 IntelliJ IDEA Resource Shadow Copy Protection (`dropIdeResourceCopies`)
When IntelliJ IDEA is configured with "Build and run using: IntelliJ IDEA", the IDE automatically copies `src/main/resources` directly into `build/classes/java/main`. Because this directory precedes resources on the classpath, unprocessed `${version}` tokens in `fabric.mod.json` crash Fabric at runtime, and subsequent Gradle `jar` tasks fail on duplicate entries.

**Solution**:
`build.gradle.kts` injects the dedicated `dropIdeResourceCopies` task, which scrubs all non-`.class` copies from `build/classes/java/main` immediately before executing any `runClient`, `runServer`, or `jar` task.

---

## 3. Mixin Registry

| Mixin Class | Target Class | Implementation Hook |
| --- | --- | --- |
| `ItemMixin.java` | `net.minecraft.world.item.ItemStack` | Intercepts completed item consumption to trigger `AlimentIngestion` metabolism |
| `PlayerMixin.java` | `net.minecraft.world.entity.player.Player` | Modulates exhaustion rates and severe dehydration damage |
| `GrindstoneInputSlotMixin.java` | `net.minecraft.world.inventory.GrindstoneMenu` (Input Slot) | Lifts vanilla damaged/enchanted item restrictions, allowing botanical herbs, rock salt, and bark |
| `GrindstoneMenuMixin.java` | `net.minecraft.world.inventory.GrindstoneMenu` | Hooks into `AlimentGrinding` recipe maps to produce milled powders and botanical pieces |
| `CameraMixin.java` (Client) | `net.minecraft.client.Camera` | Applies damped oscillatory camera roll during hypothermic shivering without displacing entity physics |
| `FogRendererMixin.java` (Client) | `net.minecraft.client.renderer.FogRenderer` | Contracts view fog distance down to 8 blocks during anticholinergic intoxication |
| `HudMixin.java` (Client) | `net.minecraft.client.gui.Gui` | Injects thirst HUD rendering hooks above player health hearts |

---

## 4. Automated Asset and Data Generation (`tools/`)

The mod adheres strictly to a **data-driven, code-generation-first** design. All JSON descriptors and sprite textures can be idempotently generated from scratch:

### 4.1 Data Generator (`tools/gen_data.ps1`)
- Scrapes the latest data schemas directly from the cached Minecraft 26.3 client JAR.
- Generates 400+ JSON descriptor files:
  - `blockstates/` and `models/block/`: Willow sets, cauldron variations (raw/cooked broth, brine), fermentation tanks, condenser pipes, and 4 growth stages each for ephedra, coptis, phellodendron, and licorice.
  - `items/` and `models/item/`: Definitions for all custom items (botanical raw parts, crushed herbs, potions, and tools).
  - `recipes/`: Carpentry, brewing yeast, glassware, stirring rods, shearing recipes, and medicinal infusions.
  - `loot_tables/`: Block destruction and crop harvesting tables with Fortune scaling and maturation stages.
  - `worldgen/`: Riparian willow river placements, subterranean rock salt veins, and arid ephedra vegetation features.
  - `lang/`: Aligns and synchronizes `en_us.json`, `zh_cn.json`, and `ja_jp.json`.

### 4.2 Procedural Texture Engine (`tools/gen_textures.ps1`, `tools/gen_ephedra_textures.ps1`, `tools/gen_herbs_textures.ps1`)
- All textures are procedurally synthesized using ImageMagick scripting, eliminating manual drawing.
- **Vanilla Potion Composite Algorithm**: Extracts the vanilla `potion.png` bottle mask and `potion_overlay.png` liquid overlay, blending custom chromatic matrices (e.g. amber gold for ephedrine, limpid bitter yellow for coptis, golden-brown for phellodendron, dark brown for licorice) using multiply blend modes to match vanilla pixel aesthetics.
- `tools/gen_grape_textures.ps1` follows the same masking approach for the grape vine stages and the grape-wine bottle, and synthesizes the two new tank liquids from the closed-form weave `channel = base + ((11x + 7y) mod 25)` that every other `tank_liquid_*` sprite already uses, so the new liquids are indistinguishable in style from the old ones.

### 4.3 Optional Cross-Mod Integration (`fabric:load_conditions`)

Farmer's Delight support is **optional at every level**. No `fabric.mod.json` entry, and the mod must
boot, pass its own self tests, and be fully playable with that mod absent.

#### The recipes: data-only, gated on a resource condition

The recipe and tag half of the integration is pure JSON, gated by Fabric API's resource-condition API,
which is already on the classpath through `fabric-api`. Every integration JSON carries a leading
condition:

```json
"fabric:load_conditions": [
  { "condition": "fabric:all_mods_loaded", "values": ["farmersdelight"] }
]
```

The failure mode is what makes this worth a test rather than a comment. A condition that fails to parse,
or one whose *codec field is misspelled*, does not raise an error anywhere - the resource is dropped
with a log line and the recipe simply does not exist. So `AlimentSelfTest.testGrapeTags` asserts both
directions: each gated recipe must be present **when** `FabricLoader.isModLoaded("farmersdelight")` and
absent when it is not.

#### The nutrition: a `compileOnly` dependency and three layered classes

Farmer's Delight's *foods* also carry Aliment values - glucose, vitamin C, iodine, sodium - and that
half cannot be JSON, because it has to run inside the ingestion hook. It is a real compile-time
dependency, but a **narrow one**:

```kotlin
compileOnly("maven.modrinth:farmers-delight-refabricated:${project.property("farmersdelight_version")}")
localRuntime("maven.modrinth:farmers-delight-refabricated:${project.property("farmersdelight_version")}")
```

`compileOnly` puts the mod on the compiler's classpath and *not* in the shipped jar, so the release
neither bundles Farmer's Delight nor demands it; `localRuntime` installs it into dev runs only, so the
self test can enumerate its items. It resolves from Modrinth's own maven, which is not one of the
usual mod mavens - see the comment on the repository block in `build.gradle.kts`.

**No remapping is needed**, and that is specific to this Minecraft version: 26.3 ships unmapped, and
Farmer's Delight's jar names `net/minecraft/world/item/Item` directly and contains no intermediary
names at all, so a plain `compileOnly` is correct where older versions would have needed a remapping
configuration.

Naming the items is a deliberate choice over naming them as registry-id strings. A string table needs
no dependency but suffers a typo or a renamed item **silently**; a named-field table makes the
compiler check all eighty, so a Farmer's Delight update that renames one fails the build instead of
quietly dropping a food from the model.

The load-time hazard is real, though: `compileOnly` means the classes are *not there* at runtime, so
a class that names `ModItems` must never be loaded while Farmer's Delight is absent, or the JVM throws
`NoClassDefFoundError`. The integration is therefore spread over four files, so that the guard the
rest of the mod touches names nothing of that mod at all:

| File | Names Farmer's Delight? | Loaded when? |
| --- | --- | --- |
| `FarmersDelightNutrition.kt` | No | Always. Reads `isModLoaded` once and returns `0f`/`false` early when it is false. |
| `FarmersDelightValues.kt` | No - only the classes below | Only when `loaded` is true; no Farmer's Delight type appears in any of its signatures. |
| `FarmersDelightItems.kt` | Yes - `ModItems`, in its static initialiser | Only when the object is first touched, which only happens when `loaded` is true. |
| `FarmersDelightRecipes.kt` | Yes - `CookingPotRecipe` | Only from the self test, under its `loaded` guard. Ingredients arrive through vanilla's `PlacementInfo` since 26.3; the cooking pot's container did not move anywhere and is that mod's own field, so reading it means naming the recipe class. |

The mechanism is the JVM's own lazy class loading: a class is initialised the first time it is
*used*, so a player without Farmer's Delight never causes `FarmersDelightItems` to initialise and
`ModItems` is never asked for. The middle file exists so that the guard class does not have to
resolve a Farmer's Delight type merely to be verified - keeping that type out of every signature means
the guard can be loaded and run with the mod absent. The `FarmersDelightItems` classification follows
the mod's own item tags (`c:foods/raw_meat`, `c:foods/cooked_meat`, `c:foods/soup`, `c:foods/vegetable`,
`c:foods/pie`, `c:foods/cookie`, `c:foods/food_poisoning`, `farmersdelight:drinks`) wherever one
exists, so the grouping is Farmer's Delight's answer and not a guess at it.

Two further traps, both avoided by construction:

* **`farmersdelight:knives` does not exist** as an item tag. Knife-gated recipes must use the
  conventional `#c:tools/knife`, which is what Farmer's Delight itself ships and reads.
* **Conventional tags merge rather than replace.** Aliment's own `data/c/tags/item/*` files contain
  only its own entries and no `replace: true`, so they add to Farmer's Delight's tags instead of
  clobbering them. These tags are unconditional - they are Aliment's half of the contract, useful to
  any mod that reads `c:`, whether or not Farmer's Delight is present.

---

## 5. Automated Headless Test Suite

Because standard JUnit runners cannot emulate world generation checks, chunk boundaries, player inventory interactions, and network synchronization, the mod incorporates a headless test suite built on Fabric's `FakePlayer`:

### 5.1 Block and Interaction Tests (`AlimentSelfTest.kt`, 275 assertions)
- **Arboreal Growth**: Verifies riparian riverbank detection, directional trunk angling toward open water, and vine draping.
- **Block Mechanics**: Axe stripping drops bark; grindstone slots accept botanical herbs, rock salt, and bark; shear crafting degrades tool durability by 1; cauldrons brew broth after 60 seconds over active campfires; condenser pipes validate directional connections.
- **No mid-process water**: Asserts that a vessel holding a finished or in-progress batch refuses to be topped up. The fermentation tank stores ethanol as a *concentration*, so water after fermentation would refill it for unlimited bottling; the brine cauldron would otherwise be **replaced outright by a full water cauldron**, because it is the one Aliment cauldron built on vanilla's `EMPTY` dispatcher rather than overriding `useItemOn` - so the assertion is that the block is still brine afterwards, not merely that a level did not rise. The lava bucket is checked from the same dispatcher, and the stirring rod is checked to still work. All four Aliment cauldrons are then checked by name against both a water and a lava bucket, so a future omission of the `useItemOn` override fails loudly instead of silently reopening the hole. The tank is also driven through the exploit end to end the way a player would try it - bottle, refill, bottle again - asserting that the batch still yields exactly one bottle per water level and nothing more; with the guard removed that loop produces 8 bottles from 3 levels, which is what the assertion exists to catch.
- **Crops**: Sows the mandrake, ephedra and grape vine through the real `BlockItem.useOn` path against each substrate the block claims to accept, walks them through every bone-meal stage, and asserts ripe-versus-unripe drops - including that the grape vine grows wild in the biomes its placed feature is attached to, since the seeds come from the fruit and an unpatched vine would be unobtainable. The grape vine's **right-click harvest** is covered from the player's side: the test counts the `ItemEntity`s that actually land on the ground rather than trusting the returned `InteractionResult`, so a harvest that reported success without dropping anything would still fail; it then re-ripens the picked vine and picks it a second time, which is what makes the fall-back to `age=1` mean something rather than being a constant that happens to match. Because `BlockBehaviour.useItemOn` returns `TRY_WITH_EMPTY_HAND` and `BlockBehaviour.useWithoutItem` returns `PASS`, a harvest that forgot to fall through below `age=3` would silently eat every right click on a growing vine, so bone meal is driven through the interaction path as well as through `BoneMealItem` directly - the direct call alone would keep passing while the click was being swallowed. Removing the fall-through fails 4 assertions; that is the guard.
- **Recipes and Loot**: Validates that all `RecipeSerializer` and `LootTable` entries parse cleanly on reload.
- **Optional integrations**: Asserts that every Farmer's Delight-gated recipe is present *exactly when* that mod is loaded, because `fabric:load_conditions` drops a file silently - a typo in the condition codec would otherwise look like a working build. Where the mod *is* loaded it then reads the cooking pot's grapefruit juice back out of the recipe manager and pins its shape: one grapefruit slice rather than two, one sugar, and a glass bottle to draw into. The ingredients come through vanilla's `PlacementInfo`, which 26.3 moved them onto; the container is Farmer's Delight's own field, so that half goes through `FarmersDelightRecipes`, the fourth and last file allowed to name one of its types. Reverting the recipe to two slices and no container fails exactly three assertions, so this is a shape that is held rather than merely written down.
- **Cross-mod nutrition without an omission**: Farmer's Delight's foods carry Aliment values, and the assertion that matters is that *none was missed*. A hand-written list of foods would agree with itself, so `testFarmersDelight` asks the **item registry** for every `farmersdelight` item carrying a `FOOD` or `CONSUMABLE` component (80 of them) and requires each to carry at least one value - so a food added to Farmer's Delight later, or a line deleted from Aliment's table, fails the suite rather than going unnoticed. Deletion of one dish produces exactly `1 unmodelled` and a failure. It then **eats** several of them through the same `finishUsingItem` path the mixin hooks and reads the body afterwards, because a perfect lookup table wired to nothing would pass every check above: a tomato takes vitamin C from 30 to 40, a kelp roll adds its iodine and its mixed-dish glucose, bacon moves sodium and chloride by its curing salt, a bowl of bone broth hydrates, and a fried egg - which carries no vitamin C, iodine or sodium at all - changes nothing but its cooked-meat glucose, which is the control against a default charged to every Farmer's Delight item. Disabling the ingestion wiring fails exactly those six assertions. Beside them sit the negative controls that catch a *double* count - a vanilla apple must not collect Farmer's Delight's vitamin C, vanilla bread must not be charged twice, and vanilla's milk *bucket* must not become a Farmer's Delight drink - plus per-item spot checks that pin each tier and nutrient to an unmistakable food (a raw cut as raw meat, cooked rice as bread, a kelp roll slice as exactly a third of a roll, bacon as cured but below a spoonful of salt).

### 5.2 Physiological Model and Localization Suite (`AlimentPhysiologySelfTest.kt`, 800+ assertions)
- **Equilibrium Values**: Verifies that healthy baseline states remain centered in normal clinical reference ranges.
- **Immune Bell Curve**: Confirms pathogen growth suppression across low, optimal, and cytokine-storm inflammation bands, as well as reactive stress storms above load 55.
- **Electrolyte Pathophysiology**: Validates hypernatremia, hyponatremia, and hyperkalemia symptom onset and lethal collapse.
- **Targeted Pharmacokinetics**:
  - Salicin fever reduction and dexamethasone cytokine storm arrest;
  - Ephedrine per-tick decay (clearing within 1 game day) and haste activation;
  - Berberine antibacterial efficacy: normal growth at <= 1.5, deceleration at > 1.5, complete replication block and suppression at >= 3.0, eradicating full infection within 1.5 game days with a 2.5-day clearance window;
  - Glycyrrhizin antiviral efficacy: normal replication at <= 1.5, deceleration at > 1.5, complete block at >= 3.0, clearing full viral load within 1.5 game days with a 2.0-day clearance window.
- **Localization Completeness**: Uses runtime reflection over all registered items and blocks to assert 100% dictionary key coverage across English, Chinese, and Japanese without missing keys.
- **The reference bands themselves are pinned, separately from the rules about them**: every symptom check in the suite is written *relative* to the band it is testing - "just inside the floor is silent, just outside it is not" - which is what lets the suite survive a retune, and is also why none of those checks can notice the band moving. Iodine's floor was retuned to **0.25** so that a deficiency stays silent until a store has lost half of itself, and that value is asserted three ways: as the literal `safeLow` in `homeostasis`, as the three-day weight check, and through the symptom layer as a player meets it - a body at **0.30** must be silent while one at **0.24** must not be. Moving the floor back to 0.40 fails those, which the rule-shaped checks alone would not have done.
- **Concentration-Dependent Excretion**: The electrolyte leak scales with the value itself - what a kidney filters is the concentration in the blood - so the suite asserts the *proportionality* rather than a table of losses. The obvious shape of that check is worthless and is deliberately not used: a body carrying too much sodium loses more of it in absolute terms under any model, because homeostasis is pulling it down either way, so "the higher one lost more" cannot fail. Instead the leak is isolated by **differencing** - the same body is ticked once with a full bladder and once with a normal one, and the gap between them is the leak alone, with the homeostatic pull cancelled because it is identical in both - and then the same measurement is taken at two sodium levels. Under the old flat model the two leaks are equal, so their ratio is exactly what changed; the check requires it to match the concentration ratio (180/140 = 1.2857, measured 1.2771) rather than merely to exceed 1. A single tick is used on purpose, because homeostasis is nonlinear and a longer run would let the two trajectories drift until the subtraction stopped being a clean isolation. Restoring the flat leak drops the ratio to exactly 1.0 and fails exactly those two checks. Iodine carries the same idea as a term of its own, and there the two claims genuinely pull against each other on one line: a surplus must clear faster than the flat leak, *and* a normal store must still lose exactly the flat figure, because "five days from normal to the floor" is a number the docs state. Zeroing the surplus term fails the surplus half while leaving the depletion half green, which is the evidence that the term was written against the excess above normal rather than against the whole value.
- **Diagnostic Readout Completeness**: `/aliment status` is documented as *every* physiological metric, and the suite holds it to that - the same reflection trick as the localization check above, aimed at the state rather than the registry. It reads the fields `AlimentData` actually declares, pairs each of the 24 with a fragment of text that only its own line can produce, and then requires every one of those fragments to be **present in the output**. Deleting the liver line reports `unprinted: [naringin, cyp3a4]` and fails two assertions. The pairing is the whole point and the first version got it wrong: comparing the declared fields against a second list of field *names* is a comparison between two lists, and no edit to the readout can ever fail it - deleting the liver line left it green. It is the fragment, matched against the text the command really returns, that makes this a claim about the readout rather than a claim about itself. The line building is split out of the command handler as `statusLines(data)` for the same reason the glucose meter's message is, so the test asserts that text rather than one it built for itself. Running it is also what found the drift it now guards against: the readout had been claiming completeness while the whole glucose chain and the CYP3A4 index were absent from it.
- **Global state a test touches**: exactly one thing in the suite mutates something outside its own player. The peaceful-difficulty checks call `MinecraftServer.setDifficulty` and put the world's difficulty back in a `finally`, because the glucose hold is a property of the level rather than of the body and there is no other way to reach it. Both halves are asserted - that the flag reads `false` on normal and `true` on peaceful - since a hold that were never wired to `Level.getDifficulty()` would otherwise pass on the model's behalf. Two negative controls fix what that is worth: removing the gate from the tick fails **8** assertions, removing it from `addGlucose` fails **4**, and the two sets do not overlap, which is the evidence that the four ways into the blood glucose are covered separately rather than by one check that happens to notice.
