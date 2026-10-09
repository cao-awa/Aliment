[English Version](SPOILER.md) | [中文版本](SPOILER_zh.md)

# Spoilers Guide

**Aliment** is a Minecraft mod about physiology, disease, pharmacology and contagion.

Everything below is a spoiler: the hidden thresholds, the crafting chains, the screen effects and the
commands that print the numbers. If you would rather work out for yourself why sea water does nothing
at first and then ruins you, or why raw willow broth makes you ill, stop reading here.

- The mathematical model: [`PHYSIOLOGY.md`](PHYSIOLOGY.md) · [中文](PHYSIOLOGY_zh.md)
- Architecture: [`TECHNICAL.md`](TECHNICAL.md) · [中文](TECHNICAL_zh.md)
- Asset generators and the dev self tests: [`tools/README.md`](../tools/README.md)

---

## The Thirty-Second Version

Spoiled food does not take hitpoints off you. Infection runs on a curve: pathogens multiply
logistically while your immune system works at full efficiency **only in a middle band**. Too little
inflammation and nothing is fighting; too much and the response itself is the disease.

So the game is keeping inflammation (salicin from willow broth, dexamethasone injections), water,
electrolytes, iodine, vitamin C and core temperature inside their windows - and not drinking untreated
swamp water or standing in a herd of cattle.

---

## 1. World Generation Overview

Ten plants and trees, all injected into the Overworld's `VEGETAL_DECORATION` step. Each has its own
section below covering growth, harvest and pharmacology; this table is where they grow and how often.

| Plant | Biomes | Frequency | Substrate | Found as | Yields |
| --- | --- | --- | --- | --- | --- |
| **Willow**<br>`aliment:willow` | every river biome (`#minecraft:is_river`) | rarity 1/2 | riverbank dirt or grass, never submerged (`max_water_depth: 0`), heightmap `OCEAN_FLOOR` | weeping or tall willow, with hanging vines | strip a log with an axe for willow bark (**salicin +1.1** once brewed) |
| **Mandrake**<br>`aliment:mandrake` | Plains, Sunflower Plains, Swamp, Mangrove Swamp | `count: 1`, heightmap `WORLD_SURFACE_WG` | air above a **grass block** | mature (`age: 3`), flowering with fruit | break for 1–2 fruit (**scopolamine +1.0, atropine +0.1**) |
| **Gymnopilus**<br>`aliment:gymnopilus` | Dark Forest, Swamp, Mangrove Swamp, Taiga, both Old Growth Taigas | `count: 3`, heightmap `WORLD_SURFACE_WG` | air above any Overworld substrate (`#minecraft:substrate_overworld`: dirt, grass, **and logs**) | mature solitary mushroom | break by hand; **raw is +1.3 psilocybin and +1.3 psilocin**, cooked is nothing |
| **Ephedra**<br>`aliment:ephedra` | Desert, Badlands and its variants, Windswept Hills, all three Savannas | rarity 1/8, heightmap `WORLD_SURFACE_WG` | air above sand, red sand, terracotta or grass | mature (`age: 3`) | right-click for 1–2 twigs, resetting to `age: 1` (**ephedrine +0.5**) |
| **Coptis**<br>`aliment:coptis` | every non-cold Overworld biome (`baseTemperature >= 0.2` and not `#minecraft:spawns_cold_variant_frogs`) | rarity 1/12, heightmap `WORLD_SURFACE_WG` | air above a **grass block** | mature (`age: 3`) | right-click or break (**berberine +1.1**) |
| **Phellodendron**<br>`aliment:phellodendron` | as coptis | rarity 1/12, heightmap `WORLD_SURFACE_WG` | air above a **grass block** | mature (`age: 3`) | right-click or break (**berberine +0.6**) |
| **Licorice**<br>`aliment:licorice` | as coptis | rarity 1/12, heightmap `WORLD_SURFACE_WG` | air above a **grass block** | mature (`age: 3`) | right-click or break (**glycyrrhizin +1.1**) |
| **Seaweed**<br>`aliment:seaweed` | every ocean biome (`#minecraft:is_ocean`) | `count: 4`, heightmap `OCEAN_FLOOR_WG` | **water** over sand or suspicious sand | submerged and mature (`age: 3, waterlogged: true`) | harvest underwater for 1–2, resetting to `age: 1` (**iodine +0.20**, +0.25 cooked) |
| **Grapefruit**<br>`aliment:grapefruit` | Jungle, Sparse Jungle, Bamboo Jungle and all three Savannas | rarity 1/6, heightmap `WORLD_SURFACE_WG` | would-survive test against a grapefruit sapling | trunk 4–6 with a blob canopy and fruit hung below | one fruit per hanging block, eight slices (**+1 naringin** each); see §14 |
| **Grape vine**<br>`aliment:grape_vine` | Plains, Sunflower Plains, Forest, Flower Forest, Birch Forest, Old Growth Birch Forest, Meadow | `count: 2`, heightmap `WORLD_SURFACE_WG` | air above grass, dirt, coarse dirt or podzol | mature (`age: 3`) | right-click for 1–3 grapes, resetting to `age: 1`; breaking pays the same; 2 more seeds per grape; see §18 |

Every plant that has growth stages can be advanced with bone meal, and every one of them is plantable
by hand on the substrates listed above. Planting by hand is also always at least as permissive as
world generation: the grape vine, for instance, is generated only over the four soils named above but
goes into **farmland** as well, because it is placed against vanilla's `minecraft:supports_vegetation`
tag rather than a list of its own.

---

## 2. Willow

**Where it grows.** Rivers only, on the bank and never in the water - see §1. Saplings grow on dirt,
grass, farmland, coarse dirt and mud, and take bone meal.

**Two shapes.** A sapling picks between the weeping willow (trunk 5–7) and the tall one (trunk 8–10)
at **65% / 35%**. Both have the same canopy, radius 3.

**It leans.** A custom trunk placer looks for water within 7 blocks and curves the trunk 1–4 blocks
towards the densest water it can see, so willows overhang the river they grew beside. With no water
nearby it grows straight up.

**It trails.** Hanging vines (`willow_vines` tips, `willow_vines_plant` stems) suspend 1–3 blocks
below the canopy and grow downward over time; bone meal hastens them.

**Willow leaves never drop apples.**

### The wood set

Everything below is in the mod's own creative tab (`itemGroup.aliment.main`).

| Id | Notes |
| --- | --- |
| `willow_log`, `willow_wood`, `stripped_willow_log`, `stripped_willow_wood` | `axis` property; strip with an axe |
| `willow_planks`, `_stairs`, `_slab`, `_fence`, `_fence_gate`, `_door`, `_trapdoor`, `_pressure_plate`, `_button`, `_shelf` | an ordinary wood set; the shelf is backed by the vanilla `SHELF` block entity |
| `willow_sign`, `_wall_sign`, `willow_hanging_sign`, `_wall_hanging_sign` | signage |
| `willow_boat`, `willow_chest_boat` | each with its own entity type and textures |
| `willow_leaves`, `willow_sapling`, `potted_willow_sapling`, `willow_vines`, `willow_vines_plant` | foliage |
| `willow_bark`, `willow_bark_pieces` | the broth chain, §3 |
| `willow_soup_cauldron`, `raw_willow_bark_soup_bottle`/`_bowl`, `willow_bark_soup_bottle`/`_bowl` | the broth itself, §3 |

---

## 3. Willow Bark and Broth

```
log or wood ──right-click with an axe──> stripped log/wood + willow bark ×1
willow bark ──grindstone──> willow bark pieces ×2
willow bark pieces ──right-click a water cauldron──> raw broth cauldron
                                                          │ campfire or soul campfire below, 60 s
                                                          ▼
                                                     boiled broth cauldron
                                                          │ glass bottle ──> broth bottle
                                                          │ bowl         ──> broth bowl
```

* **The cauldron's water level is its servings.** Ladling one out drops the level by one; emptying it
  leaves an empty cauldron. Breaking a cauldron of broth drops an empty cauldron and nothing else.
* **The brew pauses, it does not reset.** Take the fire away and the 60-second timer stops where it
  is; put it back and it carries on.
* Raw broth is cloudy and pale; boiled broth is dark, clear and fragrant.

| Broth | Hunger | Saturation | What it does |
| --- | --- | --- | --- |
| **Raw** (bottle, bowl or salted) | 1 | 1 | **25% nausea** and **15% hunger** for 20 s, **30% bacterial infection**, +1.1 salicin, +15 water |
| **Boiled** (bottle, bowl or salted) | 1 | 2 | **safe**, +1.1 salicin, +15 water |

---

## 4. Salt

```
rock salt ore (y=20–90 underground, stone pickaxe, drops itself)
   └─grindstone─> crude salt ×9 ──grindstone──> crude salt powder
                                                   └─right-click a water cauldron─> brine cauldron
                                                                                       │ campfire below
                                                                                       │ one stage per 20 s
                                                                                       ▼ boiled dry
                                                                                   salt powder ×1
```

* **Stirring rod** (two sticks, vertical) right-clicks a brine cauldron to skip it forward one
  evaporation stage, at the cost of one durability (16 uses).
* **Brine takes no water, at any stage.** Pouring water in would not dilute it - the concentration is
  the stage, not the level - so it would only throw the batch away. The stirring rod is the one thing
  a brine cauldron accepts.
* **Crude salt carries the rock with it**: besides sodium and chloride it adds a little magnesium and
  calcium. Refined salt is nearly pure sodium chloride, and pushes sodium higher for it.
* **Every salted drink hydrates (+15) and salts you.** The crude versions also carry the extra
  minerals.
* Salted variants exist for water, mushroom stew, willow broth and raw willow broth.
* **Salting plain water asks for a water bottle specifically.** Salt stirred into a potion is not salt
  water, so the recipe will not take one - a potion of healing goes in and does not come back out as
  salt water. The swamp- and sea-water versions name their own bottles and never take a potion at all.

---

## 5. Grindstone

| In | Out |
| --- | --- |
| Willow bark ×1 | Willow bark pieces ×2 |
| Rock salt ore ×1 | Crude salt ×9 |
| Crude salt ×1 | Crude salt powder ×1 |
| Ephedra ×1 | Crushed ephedra ×1 |
| Coptis ×1 | Crushed coptis ×1 |
| Phellodendron ×1 | Crushed phellodendron ×1 |
| Licorice ×1 | Crushed licorice ×1 |
| Seaweed ×1 | Crushed seaweed ×1 |

The vanilla grindstone GUI accepts these, but **one item at a time** - a stack is refused, so that a
mis-click cannot destroy the rest of it. **Sneak + right-click** grinds the held item directly,
without opening the GUI.

---

## 6. Water and Thirst

* A **ten-pip thirst bar** sits above the health bar; each pip is 10 water.
* The healthy band is **30 – 100**, the ceiling is 200, and a resting body sits at 80.
* **+15 water per drink** - water bottles, potions, stew, milk, both broths, grapefruit juice, and the
  salted versions. Grapefruit slices are the exception, at **+5**: a slice is eaten rather than drunk,
  so it does not get a drink's worth.
* **Full to empty (100 → 0) takes 5 game days** at rest in a temperate place.
* **Fever drinks it faster**: 39.0 °C empties it in 3.5 days, 40.0 °C in 2.0.
* **Above 100 is overhydration**: weakness, slower mining, faster exhaustion and diluted
  electrolytes. Above 150 it adds nausea.

Where you fill the bottle from decides what is in it.

| Water | What it does |
| --- | --- |
| Swamp water (and salted) | 30% bacterial infection, 35% nausea, 5% poisoning, 30 s each |
| **Sea water** (and salted) | **nothing at first**. It is simply hypertonic: one bottle takes sodium from 140 to about 143.5 mmol/L, still inside 135–145, and a second takes it past the limit - thirst and rapid fluid loss follow |

---

## 7. Electrolytes and Trace Elements

Electrolytes are in **mmol/L** and trace elements in **µmol/L**, with the reference ranges a blood
test would print.

| Mineral | Healthy | Reference range | Unit |
| --- | --- | --- | --- |
| Sodium | 140 | **135 – 145** | mmol/L |
| Potassium | 4.2 | **3.5 – 5.0** | mmol/L |
| Magnesium | 0.85 | **0.70 – 1.00** | mmol/L |
| Chloride | 101 | **96 – 106** | mmol/L |
| Calcium | 2.35 | **2.10 – 2.60** | mmol/L |
| Iodine | 0.50 | **0.40 – 0.80** | µmol/L |
| Vitamin C | 60.0 | **40.0 – 80.0** | µmol/L |

The five electrolytes find their own way home. **Iodine and vitamin C do not** - see below and §15.

* **Iodine only ever leaves.** From a normal 0.50 it drains 0.15 a day and is gone in exactly
  **3 game days**: one kelp a day is not quite enough, two is comfortable. The thyroid reads it, so
  the set point starts moving as soon as iodine drops below 0.40 and is 0.8 °C lower by the 0.05
  floor - which is the severe hypothyroidism behind slowness II, weakness II and fatigue.
* **Drinking a lot of water washes out sodium first**, then chloride; magnesium and calcium go last.
* **A crude salted serving is +3.0 mmol/L of sodium, a refined one +3.5.** Two salted servings take
  a healthy 140 to 146–147 and the thirst that comes with it.

| Mineral | Severe deficit | Mild deficit | Mild excess | Severe excess |
| --- | --- | --- | --- | --- |
| Sodium | nausea + slowness | weakness | hunger (thirst) | hunger + weakness |
| Potassium | weakness II + mining fatigue | weakness | weakness | slowness II + periodic magic damage (arrhythmia) |
| Magnesium | weakness + slowness (tremor) | weakness | slowness | slowness + weakness |
| Chloride | nausea | weakness | hunger | hunger + nausea |
| Calcium | slowness + weakness (tetany) | weakness | slowness | slowness II + weakness |
| Iodine | slowness II + weakness II + fatigue | weakness + slowness + hunger | hunger + nausea | adds weakness |
| Vitamin C | mining fatigue + weakness | mining fatigue | none - excreted | none - excreted |

---

## 8. Immune System

Inflammation is a weighted composite of five mediators and centres on **25.0** in health.

| Mediator | Does | Held down by |
| --- | --- | --- |
| Histamine | vasodilation, itching, swelling | mildly by both |
| Prostaglandin | pain and **fever** | **salicin** |
| Leukotriene | bronchial constriction, mucus | **dexamethasone** |
| Cytokine | systemic fever, the storm's driver | **dexamethasone** (strongest) |
| Bradykinin | pain, vascular permeability | salicin |

| Band | What happens |
| --- | --- |
| **< 12** | immune paralysis: infection grows unchecked and opportunistic bacteria appear on their own |
| **20 – 40** | peak clearance |
| **> 75** | cytokine storm: clearance fails and your own tissue takes the damage |

Which is the whole tension in one sentence: **overdosing anti-inflammatories is as dangerous as
infection**, because suppressing the response below 12 lets the pathogen run.

---

## 9. Infection

| Vector | Pathogen | Chance | Load |
| --- | --- | --- | --- |
| Raw meat (all of it), rotten flesh, poisonous potato | bacteria | **30%** | +6 |
| Raw willow broth, including salted | bacteria | **30%** | +6 |
| Within 2 blocks of **any living mob** | virus | **5% per second** | +5 |
| **Inflammation ≤ 12** | bacteria | **15% per second** | **+4 to +12**, random |

* The proximity roll is against every living `Mob` - cows, wolves, villagers, bats. Standing in a herd
  is the fastest way to catch something.
* **Immunosuppression colonises you from your own flora**, with no vector at all.
* **Sepsis: from a load of 60.0**, unblockable magic damage every two seconds:

  ```scala
  val damage = 1.0f + (load - 60.0f) / 40.0f
  ```

  At a load of 100 that is 2 damage - a full heart - every ten seconds.

---

## 10. Pharmacology

| Drug | From | Dose | Lasts | Rate |
| --- | --- | --- | --- | --- |
| Salicin | willow broth, raw or boiled | +1.1 | **3 game days** | `salicin -= 3.0f / 72000f` (−4.167e-5 / tick) |
| Dexamethasone | injection, right-click | +1.2 | **2 game days** | `dexamethasone -= 2.0f / 48000f` (−4.167e-5 / tick) |
| Insulin aspart | injection, right-click | +10.0 | **1 game day** | `insulinAspart -= 60.0f / 24000f` (−2.5e-3 / tick) |
| Berberine | coptis, phellodendron, their potions | +0.6 to +2.5 | 2.5 game days at the 7.0 cap | see §20, and §14 for what slows it down |
| Glycyrrhizin | licorice, licorice potion | +1.1 to +2.5 | 2.0 game days at the 7.0 cap | `glycyrrhizin -= 7.0f / 48000f` |
| Ephedrine | ephedra, ephedrine potion | +0.5 to +2.5 | 1 game day at the 5.0 cap | see §19 |
| Scopolamine, atropine | mandrake | see §16 | 1 game day at the 5.0 cap | see §16 |
| Naringin | grapefruit slices | +1 each | 1 game day at the 10 cap | see §14 |

Salicin is a COX inhibitor: it is an antipyretic because it blocks prostaglandin synthesis.
Dexamethasone halts cytokine transcription and aborts storms. **Neither kills anything directly** -
they buy the immune system room. Berberine and glycyrrhizin are the ones that do the killing, in §20.

Insulin aspart is the opposite of a remedy: it is the one drug that makes a body **worse** on purpose.
See §13.

### Where remedies come from

| Chest | Dexamethasone | Willow broth bowl | Insulin | Meter | Test strips | Microneedle |
| --- | --- | --- | --- | --- | --- | --- |
| Every vanilla village chest (all 14 types) | **3%** | **35%** | **10%** | **35%** | **35%**, stacks of 5–9 | **35%** |
| Pillager outpost | **3%** | **35%** | **10%** | **35%** | **35%**, stacks of 5–9 | **35%** |

---

## 11. Thermoregulation

| State | Core temperature |
| --- | --- |
| Normal | **37.0 °C** |
| Comfortable | 36.0 – 38.5 °C, no symptoms |
| Fever / hyperthermia | **≥ 38.5 °C** / **≥ 40.0 °C** |
| Mild / severe hypothermia | ≤ 36.0 °C / ≤ 35.0 °C |

Temperature is what infection pyrogen, injected pyrogen, thyroid activity and the environment add up
to.

| Exposure | Where it settles |
| --- | --- |
| Temperate biomes | no effect |
| Desert | ~37.4 °C |
| Snowy Plains | ~36.6 °C, no symptoms |
| Submerged in a cold biome | ~35.1 °C, **mild hypothermia** |
| Buried in powder snow while wet | ~32.8 °C, **severe hypothermia** |
| On fire | ~38.2 °C, sub-fever |
| In lava | ~39.4 °C, **fever**, with heat haze |

A hot biome plus a working thyroid can reach 38.0 °C without any illness; fever symptoms start
strictly at **38.5 °C**.

---

## 12. Screen Effects and Camera Tremors

Full-screen shaders distort **the periphery only** (`smoothstep(0.45, 1.0, ...)`), so the crosshair
and the middle of the screen stay clean.

| Effect | Trigger | Looks like |
| --- | --- | --- |
| Heat haze | ≥ 38.5 °C | reddish waviness at the edges |
| Heat blur | ≥ 40.0 °C | haze plus motion-blur trails |
| Cold shiver | ≤ 36.0 °C | slower vertical shivering, blue tint |

**Camera tremors** are rolled every **15 seconds** and shake for 16 ticks when they fire. Every cause
on that table puts an icon in the effect bar, so a shake is never unexplained.

| Cause | Chance |
| --- | --- |
| Symptomatic infection | 20%, doubled during a cytokine storm |
| Each temperature tier away from comfortable | +15%, with a larger shake |
| Low magnesium | +15% |
| Severe low calcium | +20% |
| Iodine excess (palpitations) | +10% |
| Severe overhydration, with the nausea icon | +10% |

Below 38.0 °C, and between 100 and 149 water, nothing tremors.

---

## 13. Blood Glucose and the Glucose Chain

Glucose is the second resource - after water - that the body **spends** rather than regulates.
Nothing stores a surplus and nothing makes it: eating is the only thing that puts it back, so an
unfed player runs out however healthy the rest of the body is.

| Quantity | Value |
| --- | --- |
| Normal, fasting | **5.0 mmol/L** |
| Reference range | **4.0 – 5.5 mmol/L** |
| Fasting floor, reached in 2 game days | **3.5 mmol/L** |
| Steep insulin response | **> 8.0 mmol/L** |
| Baseline insulin index | **1.0** |

**What food is worth**

| Food | Glucose |
| --- | --- |
| Bread, and grapefruit juice | **+0.7** |
| Cooked meat and fish | **+0.5** |
| Raw meat and fish | **+0.4** |
| Plant food - fruit, vegetables, kelp, seaweed, mushrooms, willow broth, grapefruit | **+0.4** |

One loaf of bread is enough to leave the reference range from a normal 5.0 - a body reads 5.7 after
one - and one glass of grapefruit juice does the same, because the sugar stirred into it is the fast
carbohydrate. Everything else edible that is not meat counts as plant food, so living on berries does
not avoid the glucose cost of eating.

* **A meal is gone within half a game day, and a bigger one falls faster.** The insulin index climbs
  across the reference range and then twice as steeply past 8.0, so a 20 mmol/L spike comes down
  further in the same time than a 9 does. Nothing overshoots into hypoglycaemia, because the body's
  own insulin switches off at the bottom of the range.
* **Fasting costs 1.5 mmol/L over the first two days** (5.0 → 3.5) and less after that: past the
  floor the fall scales with what is left instead of running straight to zero.
* **Hypoglycaemia:**

  | Glucose | Effect |
  | --- | --- |
  | < 2.0 mmol/L | **mining fatigue** |
  | < 1.7 mmol/L | mining fatigue **+ weakness** |
  | < 1.3 mmol/L | the above, plus **magic damage** every two seconds: `(1.3 - glucose) * 1.0` |

  The damage ignores armour - the brain has no fuel but glucose. Eight game days without food leaves
  a body at **0.97 mmol/L**, which is a crisis.

### The four items

| Item | Chests | Notes |
| --- | --- | --- |
| `aliment:insulin_injection` | **10%** | right-click; **+10.0** insulin aspart, ceiling 60.0, cleared over 1 game day |
| `aliment:glucose_meter` | **35%** | reads a bloodied strip from the other hand |
| `aliment:glucose_test_strip` | **35%**, in 5–9s | becomes a bloodied strip on a bleeding finger |
| `aliment:microneedle` | **35%** | pricks a finger, which bleeds for **15 seconds** (300 ticks) |

All four generate in every vanilla village chest and in the pillager outpost's. The bloodied strip is
never found - it is what the player makes.

**The diagnostic chain.** Prick a finger with the microneedle, right-click with a test strip while it
is still bleeding (a strip used after the drop has dried is refused), then hold the meter in the
**main hand** and the bloodied strip in the **off hand** and right-click. The reading is printed in
chat - `Blood glucose: 5.1 mmol/L` - and the strip is used up.

**A bloodied strip is a sample, not a sensor.** It stores the glucose the player had *when the blood
was taken* and reports that value however much later it is read - so a strip can be pricked before a
meal, read after it, put in a chest, or handed to someone else, and still mean what it meant. Only a
strip with no sample on it at all falls back to the body's current glucose.

**Insulin aspart is not a treatment.** Unlike the body's own insulin it is not switched off at the
bottom of the range, so one dose from a normal body is a survivable dip to about 2.7 mmol/L and two
are a crisis. Eating is the only way out.

---

## 14. Grapefruit and CYP3A4

Grapefruit is the mod's first **food that changes how long another substance lasts**. Naringin has no
effect of its own: everything it does, it does by holding down the liver enzyme that clears
berberine.

### The tree

| | |
| --- | --- |
| Where | the jungles and the savannas, rarity 1/6 - see §1 |
| Trunk | `aliment:grapefruit_log`, 4–6 blocks, blob canopy radius 2 |
| Fruit | `aliment:grapefruit`, hung under 30% of the eligible canopy leaves |
| Sapling | `aliment:grapefruit_sapling`, grows the same tree |

The fruit is a **hanging block**: vanilla's `minecraft:attached_to_leaves` decorator puts it in the
cell directly below a canopy leaf, and it exists only while the block above it is still a leaf or a
log. Felling the canopy drops the whole crop at once. Breaking one drops the fruit, and one fruit
crafts into **eight slices**.

### The slice

| | Per slice |
| --- | --- |
| Hunger | **2** |
| Saturation | **3 points** |
| Water | **5** |
| Naringin | **1** |
| Vitamin C | **10 µmol/L**, the same as a carrot |
| Always edible | **yes** - a dose should not have to wait for a full stomach |

A slice is the only food in the mod that also hydrates - a drink's job done by a snack, at a third of
a drink's worth. Ten slices fill the body's naringin. It is also one of the few foods that can be
eaten on a full stomach, because what a player eats it *for* is the naringin rather than the hunger.

### The juice

Sugar, a slice, and a **water bottle** - not any potion - craft one **grapefruit juice**. It is the
slice pressed into a bottle: same naringin, same vitamin C, but drunk rather than eaten, so it also
collects a full drink's water. The sugar is the only thing it adds.

| | Grapefruit slice | Grapefruit juice |
| --- | --- | --- |
| Hunger | 2 | **0** - a drink, not a meal |
| Water | 5 | **15**, the standard drink |
| Naringin | 1 | **1** |
| Vitamin C | 10 µmol/L | **10 µmol/L** |
| Glucose | 0.4, plant food | **0.7**, the sugar's |
| Always edible | yes | **yes** |

So a glass is what to drink when the water matters too, and it is charged for its sugar as bread is
charged rather than as the fruit it came from: **5.0 → 5.7** for one, the same step a loaf makes. Ten
glasses is still the naringin cap, exactly as ten slices is - what the glass changes is water and
glucose, not the dose.

### The wood

The tree is a full **second wood set**, not just a fruit tree:

| | |
| --- | --- |
| Log-shaped | `grapefruit_log`, `grapefruit_wood`, `stripped_grapefruit_log`, `stripped_grapefruit_wood` |
| Planks | `grapefruit_planks` |
| Shaped | stairs, slab, fence, fence gate, door, trapdoor, pressure plate, button, shelf |
| Signs | standing, wall, hanging, wall hanging |
| Boats | `grapefruit_boat` and `grapefruit_chest_boat`, each with its own entity type |
| Decorative | `potted_grapefruit_sapling` |

The four log-shaped blocks are in their own `aliment:grapefruit_logs` tag, which is what the planks
recipe takes - so a grapefruit plank recipe cannot be satisfied with willow logs, and the two woods
stay apart all the way down the crafting tree. Everything else joins the vanilla `planks`, `logs`,
`wooden_*`, `signs` and `fence_gates` tags, which is what makes the set axe-mineable and recognisable
to vanilla recipes.

### The enzyme

CYP3A4 sits at **85** in a body that has eaten no grapefruit, and naringin pushes it down in **steps**
rather than along a curve, so the index only ever holds one of five values:

| Naringin | CYP3A4 | Berberine cleared at | so a dose lasts |
| --- | --- | --- | --- |
| ≤ 2 | **85** | 1.00x | 1.0x |
| > 2 | **60** | 0.71x | 1.4x |
| > 4 | **45** | 0.53x | 1.9x |
| > 7 | **25** | 0.29x | 3.4x |
| ≥ 8.5 | **10** | 0.12x | **8.5x** |

Naringin clears linearly over **one game day** from the cap, so the effect goes away on its own - and
the last column is only reached for part of a dose's life.

### What it is for

Berberine - from coptis and phellodendron - is the mod's antibacterial, and it is cleared by CYP3A4
and by nothing else. A single coptis herb clears in about **9,400 ticks** on its own; eaten after
nine slices of grapefruit the same herb takes about **17,800**. That cuts both ways: grapefruit is
how a player stretches a herb they are short of, and how a player commits to one they only meant to
take once. No grapefruit, no interaction - a clean body clears berberine at exactly the rate it
always did.

---

## 15. Vitamin C and Plant Nutrition

| | |
| --- | --- |
| Normal | **60.0 µmol/L** |
| Reference range | **40.0 – 80.0 µmol/L** |
| Scurvy | **15.0 µmol/L** |
| Half-life | **5 game days** (120,000 ticks); `k = ln(2) / 120000 ≈ 5.776e-6` per tick |

Excretion is first-order, so it decays proportionally: 80.0 falls to 40.0 in exactly **5 game days**
with no plant food at all.

* **Mild deficiency** (< 40.0): mining fatigue.
* **Severe deficiency** (< 15.0): mining fatigue + weakness.
* Above 80.0 there is nothing to worry about - it is water-soluble and simply excreted.

**What is worth what**

| Food | Vitamin C |
| --- | --- |
| Apple | +12.0 |
| Golden apple | +20.0 |
| Enchanted golden apple | +30.0 |
| Melon slice | +8.0 |
| Sweet berries / glow berries | +6.0 |
| Carrot | +10.0 |
| Golden carrot | +15.0 |
| Pumpkin pie | +15.0 |
| Beetroot | +6.0 |
| Beetroot soup | +16.0 |
| Mandrake fruit | +10.0 |
| Grapefruit slice | +10.0 |
| Grapefruit juice | +10.0 |
| Seaweed | +5.0 |
| Cooked seaweed | +3.0 |

---

## 16. Mandrake

A nightshade with four growth stages, sown on **soil rather than in a tilled field** - it is a
`BushBlock`, not a crop, so it does not need farmland (though farmland works). See §1 for where it
grows wild.

* **Growing it:** on grass, dirt, coarse dirt, rooted dirt, mud, moss or farmland. Random ticks
  advance it at light level 9 or above, one step in eight; bone meal advances one stage per use, three
  to mature.
* **Harvesting:** only a **mature** plant drops anything - 1–2 fruit, with Fortune. Immature plants
  drop nothing.
* **Seeds:** one fruit crafts into **2 seeds**, so a single find becomes a farm.

Both alkaloids cap at **5.0** and clear linearly over **1 game day**:

```scala
scopolamine = Math.max(scopolamine - 5.0f / 24000f, 0f) // -2.083e-4 / tick
atropine    = Math.max(atropine    - 5.0f / 24000f, 0f) // -2.083e-4 / tick
```

| Eaten | Scopolamine | Atropine |
| --- | --- | --- |
| Mandrake fruit | **+1.0** | **+0.1** |
| Mandrake seeds | **+0.75** | **+0.1** |

The two add up, and the sum drives a fever of its own that **salicin cannot touch** - it has nothing
to do with prostaglandins:

```scala
val deltaT =
  if (alkaloidSum >= 4.0f) 4.0f      // core temperature -> 41.0 °C
  else if (alkaloidSum >= 2.5f) 2.5f // -> 39.5 °C
  else if (alkaloidSum >= 1.5f) 1.0f // -> 38.0 °C
  else 0.0f
```

Sight blurs - fog pulled in to 8 blocks - once `scopolamine >= 2.3` or `atropine >= 2.3` or the sum
reaches `2.7`. Drug fever and blur stack on top of whatever an infection is already doing.

---

## 17. Gymnopilus

A rust-coloured wood-decay mushroom of damp, shaded woodland - see §1. Because it decays wood and not
soil, it **ignores vanilla's mushroom darkness rule** entirely and grows in open daylight and on tree
trunks.

* **Raw:** 1.3 psilocybin + 1.3 psilocin, and 3 hunger / 4 saturation.
* **Cooked** (furnace, smoker or campfire): 4 hunger / 5 saturation and **no psychedelics at all** -
  heat destroys them.
* **Kinetics:** psilocybin does nothing on its own; it converts 1:1 into psilocin over half a game
  day, and psilocin is cleared at a flat rate.

  ```scala
  val converted = Math.min(psilocybin, 1.3f / 12000f)          // 1.083e-4 / tick
  psilocybin -= converted
  psilocin = clamp(psilocin + converted - (1.3f / 24000f), 0f, 10.0f) // -5.417e-5 / tick
  ```

  Five raw mushrooms is a trip lasting ten game days, linearly.

| Psilocin | What you see |
| --- | --- |
| > 1.2 | chromatic rays off block edges |
| > 1.7 | colour shifts on blocks and full-screen colour noise |
| > 2.5 | mild full-screen spatial warping |
| > 5.0 | heavy warping, and a drug fever climbing to 39.0 °C |
| ≥ 7.0 | hyperthermia, 41.0 °C |

---

## 18. Fermentation and Distillation

**The tank.** Glass around any wood planks in the top centre. Three water levels (0–3); add **sugar
×1** and **brewer's yeast ×1** and it bubbles for 45 s (900 ticks) into **7% wine**, which a glass
bottle ladles out. A bottle carries its own strength with it, and at **70% or more it is named
Alcohol rather than Wine**.

**The grape must.** The same tank makes a second, weaker drink. Add **grapes ×1** and **sugar ×1** to
the water in either order - the liquid turns purple - then yeast. The sugar completes the must rather
than replacing it: **yeast is refused until both are in**, because a grape is mostly water and the
sugar is the part that actually ferments. 45 s later the tank holds **grape wine at 5%**, which bottles
straight out with no still needed. Grapes and sugar are consumed; the tank is empty again.

Order does not matter. Grapes first leaves the tank a grape must waiting for sugar; sugar first makes
plain wine and the grapes then promote it to a grape must. Either way the yeast only takes once both
are in, and either way the product is 5% grape wine rather than 7% wine.

**A tank that has fermented takes no more water.** Top it up and the alcohol does not dilute, because
the tank stores a *concentration* and not a total - it simply comes back out at full strength. Bottle
one bottle, refill to three, and one dose of sugar and yeast would fill the world. The water the tank
held when fermentation finished is the whole batch, and once it is bottled out the tank is empty and
free to refill.

| In the water | Product | Ethanol | Needs a still? |
| --- | --- | --- | --- |
| sugar | wine | 7% | to reach 40% |
| wheat | beer | 7% mash | **yes** - the mash cannot be bottled |
| grapes + sugar | grape wine | **5%** | no - bottled as it is |

**Where the grapes come from.** A wild vine in the plains and temperate forests - see §1 - or seeds
from the fruit: one grape in a crafting grid gives two seeds, sown into dirt, grass, farmland or
coarse dirt like the mandrake, and grown through four stages with bone meal. A ripe vine is
**right-clicked** for its grapes and falls back to `age=1` rather than dying, so a vine is a plant
you keep and walk back to; it is exactly the shape of vanilla's sweet berry bush. A pick yields a flat
**1-3** and takes no fortune, which is again what vanilla does - its harvest table for the sweet berry
bush carries no bonus while its block table does. Breaking a ripe vine still pays 1-3 grapes *and*
still applies fortune, because there is no item for the vine itself; so a fortune tool is worth more
on the break, and the pick is what you do when you would rather keep the plant. Anything picked green
is a wasted seed: before `age=3` the vine drops nothing to either harvest, and a right click on it is
passed through rather than swallowed, so bone meal still lands.
A bunch is itself edible:
2 hunger, 1.0 saturation, always edible. Cutting it on a board and boiling grapefruit into juice are
recipes that need **Farmer's Delight** - see §22, which is also where that mod's own larder is priced.

**The yeast.** Shapeless: wheat + sugar + brown mushroom.

**The still.** Two horizontal rows of glass with the middle row left open. Place it above a tank
heated by a lit **campfire or soul campfire**, and every 30 seconds (600 ticks) one water level of
wine evaporates:

* A lone vertical condenser pipe's outlet faces **up**. Connect it to a horizontal neighbour and the
  outlet bends **down** - which is what lets it drip into a cauldron and collect as **40% spirit**.
* A pipe that does not line up vents the vapour into the air with a hiss, wasting the level.

**Drinking.** Wine restores **10 water** rather than a drink's 15. It raises the ethanol index on a
0.0–1.0 scale: 7% wine is +0.07, **5% grape wine is +0.05**, 40% spirit is +0.40. Elimination is
zero-order, 1.0 per game day:

```scala
ethanol = Math.max(ethanol - 1.0f / 24000f, 0f) // -4.167e-5 / tick
```

| Ethanol | Effect |
| --- | --- |
| ≥ 0.35 | nausea I, slowness I |
| ≥ 0.70 | nausea II, slowness II |

One glass of 7% wine is +0.07, which is about **1.4 minutes** to sober off.

---

## 19. Ephedra and Ephedrine

A desert and steppe shrub - see §1 for where it grows and how to harvest it. **Right-clicking a
mature one gives 1–2 twigs and resets it to stage 1 without uprooting it**, so a patch is a
renewable source; breaking it gives more twigs but costs the plant.

* **Crushed ephedra:** grindstone, or shears in a crafting grid (one durability).
* **Ephedrine potion:** glass bottle + crushed ephedra, shapeless. The bottle comes back.
* **Dose:** a raw twig is +0.5, a potion +2.5, capped at 5.0.

```scala
ephedrine = Math.max(ephedrine - 5.0f / 24000f, 0f) // -2.083e-4 / tick
```

* **Haste** from `ephedrine > 1.0`, so one twig is not enough and a potion is. A full 5.0 is over
  sixteen continuous minutes of it.
* **It keeps you awake.** Above **0.5** the nervous system will not settle and a bed refuses you -
  *"You cannot sleep while stimulated"*. One twig lands exactly on the threshold and still sleeps; a
  second twig, or any potion, does not. The restriction lifts itself as the drug is metabolised.

---

## 20. Traditional Medicinal Herbs

Coptis, phellodendron and licorice grow together across every non-cold biome - see §1.

| Herb | Raw | Grind | Brew with a water bottle |
| --- | --- | --- | --- |
| Coptis `aliment:coptis` | **+1.1 berberine** | crushed coptis | **coptis potion**, +2.5 berberine |
| Phellodendron `aliment:phellodendron` | **+0.6 berberine** | crushed phellodendron | **phellodendron potion**, +1.5 berberine |
| Licorice `aliment:licorice` | **+1.1 glycyrrhizin** | crushed licorice | **licorice potion**, +2.5 glycyrrhizin |

These are the only things in the mod that **kill pathogens directly**. For a concentration in
`0.0 .. 7.0`, with a deceleration threshold at 1.5 and full suppression at 3.0:

```scala
if (drugConc >= 3.0f) {
  // replication stops outright, and the drug's own clearance scales with concentration
  val drugClearance = (100.0f / (1.5f * 24000f)) * (drugConc / 3.0f)
  load = Math.max(load - immuneClearance - drugClearance, 0.0f)
} else if (drugConc > 1.5f) {
  // replication slowed enough for the immune system to win
  val slowRatio = (drugConc - 1.5f) / (3.0f - 1.5f)
  val reducedGrowth = baseGrowth * (1.0f - 0.75f * slowRatio)
  load = Math.max(load + reducedGrowth - immuneClearance, 0.0f)
} else {
  load = Math.max(load + baseGrowth - immuneClearance, 0.0f)
}
```

where the suppression clearance constant is `c_suppress = 100.0 / (1.5 * 24000) ≈ 2.778e-3` per tick.

* **Berberine targets bacteria**: `berberine -= 7.0f / 60000f` (−1.167e-4 / tick), 2.5 game days from
  the cap - **unless grapefruit is in the body**, which is what §14 is about.
* **Glycyrrhizin targets viruses**: `glycyrrhizin -= 7.0f / 48000f` (−1.458e-4 / tick), 2.0 game days
  from the cap.

---

## 21. Seaweed and Iodine

An underwater crop of every ocean, harvested by right-click **while submerged**, which resets the
plant to stage 1 instead of uprooting it. Bone meal works underwater. See §1 for where it generates.

| Form | Iodine | Also |
| --- | --- | --- |
| Raw seaweed | **+0.20 µmol/L** | food |
| Cooked seaweed | **+0.25 µmol/L** | 3 hunger / 0.6 saturation |
| Crushed seaweed | - | grindstone, an ingredient |
| Seaweed iodized salt | **+0.40 µmol/L** | crushed seaweed + salt powder; **+1.5 mmol/L** sodium and chloride |

Dried kelp is the vanilla alternative at +0.20. See §7 for what a deficit does.

---

## 22. Farmer's Delight

Aliment does not require that mod and does not bundle it: the dependency is **compile-time only**.
Aliment is built against its foods so it can price them, and a game without it boots, plays and passes
the same tests - the two recipes below simply do not exist. It is an integration, not a requirement.

| Gated recipe | Station | Yields |
| --- | --- | --- |
| A bunch of grapes cut with a **knife** | cutting board | 3 grape seeds |
| One grapefruit slice and sugar, drawn into a **glass bottle** | **cooking pot** | grapefruit juice |

**Its larder feeds the model.** Eating is eating, so a Farmer's Delight meal moves the same numbers a
vanilla one does, and all **80** of its edible foods and drinks are priced.

| What | Charge | Examples |
| --- | --- | --- |
| Cured meat | **sodium and chloride** | the bacon and ham cuts +0.8, a bacon sandwich or bacon and eggs +1.2, honey-glazed ham +2.0 mmol/L |
| Vegetables and salads | **vitamin C** | a tomato +10, worth a carrot; cabbage +8, onion and pumpkin slice +6; the salads are the richest at +14 to +18 |
| Kelp rolls | **iodine** | a roll +0.30 µmol/L, a slice exactly a third of one |
| Everything edible | **blood glucose** | by what it is made of: plant food 0.4, raw meat 0.4, cooked meat 0.5, an assembled plate **0.6**, bread and sweets 0.7 |
| Bottled milk, three sweetened drinks, nine soups | **water** | one drink's worth, as vanilla's milk bucket and mushroom stew |

Its cured meat is the **only** salt in that mod - it adds no salt item and no recipe that uses one -
so a rasher is salty without being a spoonful of salt, and every cured food sits below a serving of
Aliment's own. Its raw dough, raw pasta, chicken cuts and nether salad carry the food-poisoning risk
raw meat does. See §13 for the glucose bands, §15 for vitamin C, §21 for iodine and §7 for what a
deficit does.

---

## 23. Creative Mode and Death

**Creative mode freezes the whole system.** Nothing advances - pathogens neither grow nor clear,
temperature does not move, drugs are not metabolised - no effects are applied, no damage is dealt, no
mining penalty is charged and no exhaustion multiplier is applied. Any screen effect already up is
taken down on the next tick, and the tremors stop. Eating, drinking and injecting do nothing either.
It is a **freeze, not a cure**: walk into creative with an infection and you walk back into survival
with it.

**Death resets the body.** You respawn with a new one: inflammation back at rest, no pathogens, core
temperature 37, water 80, iodine normal. Dying is the only way to throw the model away.

---

## 24. Advancements

The mod has its own advancement tab, rooted at the first piece of willow bark.

| Advancement | Description | Frame | For |
| --- | --- | --- | --- |
| **Ancient Anti-inflammatory** | Obtain a piece of willow bark | task, root | getting willow bark, by stripping a log or picking it up |
| **Just Crude Salt** | Crush a piece of rock salt ore | task | grinding rock salt ore |
| **Crushed and Crushed Again** | Crush a piece of crude salt | task | grinding crude salt into powder |
| **Refined Salt** | High-purity refined table salt | task | getting salt powder out of a boiled-dry cauldron |
| **Even If Dangerous** | Taste the mandrake | task | eating a mandrake fruit or seed |
| **Psychedelic World** | Eat a bite of Gymnopilus | task | eating a gymnopilus |
| **Hyperpyrexia** | Core body temperature exceeds 40 C | **challenge** | a core temperature above 40.0 °C - severe infection, mandrake, or a large dose of psilocin |

---

## 25. Diagnostic Commands

```
/aliment status                 every physiological metric, in one readout
/aliment fever [temperature]    induce a calibrated fever (default 39.5, range 31–42; OP only)
/aliment cure                   reset the body to perfect health and clear the screen shaders (OP only)
/aliment set <field> <value>    set any field directly (OP only, tab-completed)
```

`/aliment fever` injects a calculated dose of pyrogen so that the peak is reached within two minutes
and metabolised within one game day.

---

## 26. Common Pitfalls

1. **Sea water is not dirty water.** Nothing happens at the time; the price is the sodium, and it
   takes two bottles to leave the reference range.
2. **The thirst bar cannot show overhydration.** 100 and 200 both draw as ten full pips - watch for
   the weakness icon instead.
3. **Raw willow broth is an infection source and boiled broth is not.** Those 60 seconds are worth
   waiting for.
4. **Iodine is gone in three days and the body will not hold on to it.** One kelp a day is not
   enough, two is comfortable; without it you walk into severe hypothyroidism and a lower temperature
   set point. The surplus does not store either - it clears in the same three days.
5. **Salicin is an anti-inflammatory and an antipyretic at once**, because it blocks prostaglandins,
   which is what fever runs on.
6. **Overdosing is more dangerous than the infection.** Below 12 inflammation you are
   immunosuppressed, and immunosuppression gives you a **random bacterial infection at 15% a second**
   out of nothing; once the load passes 60 the sepsis damage starts. It is the only way to kill
   yourself with medicine.
7. **Do not stand in a herd.** Any mob within 2 blocks is a 5% viral roll every second.
8. **Willow leaves never drop apples.**
9. **Every tremor has a cause** and every cause has an icon in the effect bar.
10. **Blood glucose only ever falls on its own.** Nothing synthesises it, so an unfed player drifts
    from 5.0 to 3.5 in two game days and into a crisis by the eighth. Bread is worth 0.7 and
    everything else 0.4–0.5.
11. **An insulin injection is not a treatment.** It lowers blood glucose and nothing switches it off;
    two doses inside the cooldown are a crisis, and eating is the only way out.
12. **Grapefruit changes how long your other medicine lasts.** Nine slices make a dose of coptis last
    nearly twice as long - a way to stretch a herb you are short of, and a way to commit to one you
    did not mean to take. It fades on its own over a game day.
13. **A hanging grapefruit is not a fruit you can pick and forget.** It exists only while the leaf or
    log above it does, so felling the tree drops the whole crop - convenient, but it also means the
    fruit will not survive you building through the canopy.
14. **Creative mode does not cure you.** It freezes the model; the infection is still there when you
    go back to survival.
