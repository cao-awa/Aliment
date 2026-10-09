[English Version](PHYSIOLOGY.md) | [中文版本](PHYSIOLOGY_zh.md)

# Physiology

The core physiological simulation of **Aliment**: a continuous mathematical model tracking **inflammatory mediators, electrolytes, trace elements (iodine and vitamin C), hydration, core body temperature, pathogens, and pharmacological compounds** within each player.

The design objective is to make infection an evolving, organic biological process. Eating contaminated food does not deal instant damage; instead, it allows pathogens to grow, challenging and potentially overwhelming the immune system. Willow bark soup (salicin) and dexamethasone serve as clinical tools to modulate these responses, but excessive administration causes severe medical complications of its own.

Target Platform: **Minecraft 26.3** (Fabric, Scala 3.9 / Kotlin 2.4 / Java 25).

---

## Data Model

Each player possesses an `AlimentData` attachment, comprised of: inflammatory mediators, pathogen loads, hydration, serum electrolytes, trace elements (iodine and vitamin C), blood glucose and the insulin index, active drug concentrations (including naringin), the CYP3A4 enzyme index, and core body temperature.

### Inflammatory Mediators `Mediators`

Inflammation is not represented by a single arbitrary health bar, but as a **weighted composite** of five biological mediators:

```scala
inflammation = 0.15 * histamine + 0.20 * prostaglandin + 0.15 * leukotriene + 0.35 * cytokine + 0.15 * bradykinin
```

where:
* `histamine` (H): Histamine
* `prostaglandin` (P): Prostaglandin
* `leukotriene` (Lk): Leukotriene
* `cytokine` (C): Cytokine
* `bradykinin` (B): Bradykinin

| Mediator | Weight | Biological Role | Primary Therapeutic Inhibitor |
| --- | --- | --- | --- |
| Histamine `histamine` (H) | 0.15 | Vasodilation, pruritus, edema (mast cell degranulation) | Mildly reduced by both |
| Prostaglandin `prostaglandin` (P) | 0.20 | Pain, pyrogenesis / fever (COX pathway) | **Salicin** (aspirin mechanism) |
| Leukotriene `leukotriene` (Lk) | 0.15 | Bronchoconstriction, mucus secretion | **Dexamethasone** |
| Cytokine `cytokine` (C) | 0.35 | Systemic fever, primary driver of cytokine storms | **Dexamethasone** (most potent) |
| Bradykinin `bradykinin` (B) | 0.15 | Pain, vascular permeability | Salicin |

In a healthy resting state, the mediators sit at `(H, P, Lk, C, B) = (25.0, 30.0, 30.0, 20.0, 25.0)`, yielding a weighted sum of **exactly 25.0** (the center of the normal physiological safe zone):

```scala
// Resting baseline: (H, P, Lk, C, B) = (25.0, 30.0, 30.0, 20.0, 25.0)
val restingInflammation = 0.15 * 25.0 + 0.20 * 30.0 + 0.15 * 30.0 + 0.35 * 20.0 + 0.15 * 25.0 // == 25.0
```

Detailed formulas are located in `ModelMediators.getInflammation` (weights defined in `MediatorLevels`).

### Serum Electrolytes `ModelElectrolytes` and Trace Elements `ModelTraceElements`

**Values use real-world clinical units**: electrolytes in **mmol/L**, and trace elements in **µmol/L** (as plasma physiological concentrations are several orders of magnitude smaller).

Each mineral has an established **clinical reference range**, with **bilateral thresholds** where both deficiency and excess constitute pathological states:

```
   min    severeLow   safeLow   normal   safeHigh   severeHigh    max
   |    Severe Deficit  |  Mild Deficit  |    Healthy   |   Mild Excess   |    Severe Excess    |
```

| Mineral | Baseline Normal | Reference Range | Mild Boundary | Severe Boundary | Unit |
| --- | --- | --- | --- | --- | --- |
| Sodium `sodium` | 140 | **135 – 145** | 125 / 150 | 100 / 190 | mmol/L |
| Potassium `potassium` | 4.2 | **3.5 – 5.0** | 3.0 / 6.0 | 1.5 / 9.0 | mmol/L |
| Magnesium `magnesium` | 0.85 | **0.70 – 1.00** | 0.50 / 1.50 | 0.20 / 3.0 | mmol/L |
| Chloride `chloride` | 101 | **96 – 106** | 90 / 115 | 70 / 140 | mmol/L |
| Calcium `calcium` | 2.35 | **2.10 – 2.60** | 1.80 / 3.00 | 1.00 / 4.0 | mmol/L |
| **Iodine `iodine`** | 0.50 | **0.40 – 0.80** | 0.20 / 1.20 | 0.05 / 2.0 | **µmol/L** |
| **Vitamin C `vitamin_c`** | 60.0 | **40.0 – 80.0** | 40.0 / 80.0 | 15.0 / 120.0 | **µmol/L** |

**Electrolytes are regulated homeostatically back to baseline**: the endogenous restoration rate is `(normal - current) * 0.00005` (time constant 20,000 ticks, under one game day). **Trace elements (iodine and vitamin C) are exceptions**: they lack endogenous synthesis and must be maintained through dietary intake.

**Because units reflect reality, dietary salt intake produces realistic consequences**: one serving of crude salt or sea water food provides **+3.0 mmol/L sodium**, and refined salt provides **+3.5 mmol/L**. Since the upper normal threshold for sodium is 145:
* **Consuming 1 serving**: 140 → 143 or 143.5 mmol/L (**remains safely within normal range**);
* **Consuming 2 servings**: 140 → 146 or 147 mmol/L (**exceeds reference range**, triggering hypernatremic thirst).

**Cured meat is the one dietary salt that is not a spoonful of salt.** Bacon and ham are brined pork
in the real kitchen and brining is salt, so Farmer's Delight's seven cured foods carry sodium - but a
rasher is salty without being a serving of salt, so they sit deliberately **below** the 3.0 of a salt
serving: a cut is **+0.8**, a plate built on it - a bacon sandwich or bacon and eggs - is **+1.2**, and
a whole honey-glazed ham is **+2.0**. Even the ham is short of one salt serving, so no cured food can
push a body out of range on its own; two of the larger plates will. Nothing else in that mod
qualifies, because Farmer's Delight adds no salt item and **no recipe of its own calls for salt**, so
there is no hidden sodium anywhere else in its larder.

### Iodine
The human body cannot synthesize iodine and **does not retain it permanently**:

| Food Item | Iodine Contribution | Nutritional Value |
| --- | --- | --- |
| Kelp `minecraft:kelp` | **+0.10 µmol/L** | Nutrition: 1 / Saturation: 0.6 |
| Dried Kelp `minecraft:dried_kelp` | **+0.20 µmol/L** | Nutrition: 1 / Saturation: 0.6 |
| Seaweed `aliment:seaweed` | **+0.20 µmol/L** | Nutrition: 1 / Saturation: 0.2; renewable in seabed farms |
| Cooked Seaweed `aliment:cooked_seaweed` | **+0.25 µmol/L** | Nutrition: 3 / Saturation: 0.6; high-density iodine source |
| Seaweed Iodized Salt `aliment:seaweed_iodized_salt` | **+0.40 µmol/L** | Adds **+1.5 mmol/L Na and Cl**; balances electrolytes and prevents hypothyroidism |
| Kelp Roll `farmersdelight:kelp_roll` | **+0.30 µmol/L** | *Farmer's Delight.* Three dried kelp wrapped around a bowl of rice; the rice is most of it, so it is charged as a dish containing kelp and not as three dried kelp |
| Kelp Roll Slice `farmersdelight:kelp_roll_slice` | **+0.10 µmol/L** | *Farmer's Delight.* A third of a roll, exactly as the cutting board divides it |

Without dietary iodine, plasma concentrations steadily drain at a fixed rate of 0.15 µmol/L per day, **depleting completely to the floor (0.05 µmol/L) in exactly 3 in-game days**:
* **Daily Maintenance**: 0.15 µmol/L is lost daily via basal metabolism (accelerated during fever and sweating).
* **Dietary Strategy**:
  * **Daily Upkeep**: 1 dried kelp (+0.20) or raw seaweed (+0.20) comfortably covers a day's basal decay.
  * **Acute Treatment**: 2 cooked seaweeds (+0.25 each) replenish a severely depleted thyroid reserve back toward the upper safe limit.
  * **Expedition Supply**: Seaweed iodized salt (+0.40) provides substantial iodine while concurrently replenishing sodium and chloride (+1.5 mmol/L each) to counteract dilutional hyponatremia from drinking water.

### Vitamin C (Ascorbic Acid)
Vitamin C cannot be synthesized endogenously by humans. Serum reference values are **40.0 – 80.0 µmol/L** (baseline healthy homeostasis is **60.0 µmol/L**).

* **First-Order Clearance Kinetics (Concentration-Dependent Excretion)**:

  ```scala
  // First-order excretion: half-life = 5 game days (120,000 ticks)
  // k = ln(2) / 120000 ≈ 5.776e-6 per tick
  val k = Math.log(2.0).toFloat / 120000f
  vitaminC -= vitaminC * k
  ```

  - **Excretion rate is directly proportional to current plasma concentration**: higher levels clear rapidly through the kidneys, while lower concentrations clear more slowly.
  - **Half-life is 5 in-game days (120,000 ticks)**: Starting from the upper safe boundary (**80.0 µmol/L**), it takes exactly **5 game days** of zero botanical intake to decay to the abnormal threshold line (**40.0 µmol/L**).
* **Clinical Symptoms**:
  - **Mild Deficiency (< 40.0 µmol/L, subclinical deficiency)**: Impaired collagen synthesis and muscular stamina impart **Mining Fatigue I**.
  - **Severe Deficiency (< 15.0 µmol/L, scurvy)**: Profound physical exhaustion and tissue breakdown add **Weakness I** (simultaneously inflicting Mining Fatigue I + Weakness I).
  - **Excess (> 80.0 µmol/L)**: Ascorbic acid is water-soluble; excess concentrations are safely excreted without adverse pathology.
* **Dietary Sources (Fruits, Root Vegetables, and Plant Foods)**:

| Food Item | Vitamin C Yield |
| --- | --- |
| Apple `minecraft:apple` | **+12.0 µmol/L** |
| Golden Apple `minecraft:golden_apple` | **+20.0 µmol/L** |
| Enchanted Golden Apple `minecraft:enchanted_golden_apple` | **+30.0 µmol/L** |
| Melon Slice `minecraft:melon_slice` | **+8.0 µmol/L** |
| Sweet Berries / Glow Berries | **+6.0 µmol/L** |
| Carrot `minecraft:carrot` | **+10.0 µmol/L** |
| Golden Carrot `minecraft:golden_carrot` | **+15.0 µmol/L** |
| Pumpkin Pie `minecraft:pumpkin_pie` | **+15.0 µmol/L** |
| Beetroot `minecraft:beetroot` | **+6.0 µmol/L** |
| Beetroot Soup `minecraft:beetroot_soup` | **+16.0 µmol/L** |
| Mandrake Fruit `aliment:mandrake_fruit` | **+10.0 µmol/L** |
| Seaweed `aliment:seaweed` | **+5.0 µmol/L** |
| Cooked Seaweed `aliment:cooked_seaweed` | **+3.0 µmol/L** |
| Grapefruit Slice `aliment:grapefruit_slice` | **+10.0 µmol/L** - a plant food, and a citrus besides, so the same as a carrot |

**Farmer's Delight's plants carry vitamin C on the same scale.** A vegetable is a root vegetable
wherever it comes from, so a tomato is worth a carrot (**+10.0**), a cabbage **+8.0**, an onion
**+6.0** and a tomato sauce **+8.0**. A composed dish earns what went into it and no more, which is
what makes the salads the richest things on the list - a gleaming salad is **+18.0** and a fruit salad
**+16.0** - while a sandwich that takes a slice of tomato as a garnish is **+4.0**. Meat, eggs, bread,
rice, milk and chocolate carry **none**, because the vitamin is in the plants; there is no vitamin C
in a grilled salmon however it is cooked.

### Hydration `water`

| Parameter | Value |
| --- | --- |
| Normal Range | **30 – 100** |
| Ceiling | 200 |
| Above 100 | **Overhydration**: Weakness + Reduced mining speed |
| Below 30 | **Dehydration** |
| **Basal Water Depletion** | **5 game days from full (100) to empty (0)** (without fever or sweating) |
| **Fever at 39 °C** | **3.5 game days to empty (100 → 0)** (accelerated sweating) |
| **Severe Hyperthermia at 40 °C** | **2 game days to empty (100 → 0)** (profuse sweating) |
| Hydration per Beverage | **+15** (water, potions, stew, milk, willow bark soups, and salted variants) |
| Hydration per Grapefruit Slice | **+5** - a slice is eaten rather than drunk, so it does not get a drink's worth |

Above 100, kidneys accelerate excretion (up to 2x normal rate), concurrently **diluting and washing out serum electrolytes**; high sodium and high calcium also intensify thirst. Temperatures exceeding 38.25 °C activate non-linear diaphoresis (sweating), multiplying water loss with higher fevers.

### Core Body Temperature `temperature`

| Parameter | Value |
| --- | --- |
| Baseline | **37.0 °C** |
| Comfort Zone | **36.0 – 38.5 °C** (no adverse symptoms) |
| Fever / Hyperthermia | ≥ **38.5 °C** / ≥ **40.0 °C** |
| Standard Immune Fever Ceiling | **39.5 °C** (during regular immune response, pathogen load ≤ 55) |
| Stress Storm Fever Ceiling | **42.0 °C** (during severe infection load > 55 or massive pyrogen insult) |
| Mild / Severe Hypothermia | ≤ **36.0 °C** / ≤ **35.0 °C** |
| Model Limits | 30.0 °C – 42.0 °C |
| Thermal Integration Rate | 0.0004 / tick (approx. 2500 ticks to traverse 63% of the delta; ~2 minutes) |

> Fever threshold begins at **38.5 °C** rather than 38.0 °C. 38.0 °C can be reached naturally in warm biomes with slight thyroid activation without requiring antipyretics. During standard immune response (loads 20–55), fever is capped at 39.5 °C; only when loads exceed 55 and trigger cytokine storms does temperature surge past 40.0 °C.

Core temperature is governed by four contributing factors: **prostaglandins** from immune stimulation, injected **pyrogens**, **thyroid** offsets from iodine, and **ambient environment**.

### Pharmacology and Anti-Inflammatory Therapeutics

| Drug | Active Threshold | Ceiling | Clearance Window | Pharmacological Mechanism |
| --- | --- | --- | --- | --- |
| Salicin `salicin` | 1.0 | 3.0 | **3 game days** (linear) | COX inhibitor suppressing prostaglandin synthesis; reduces fever, relieves pain, and dampens inflammation |
| Dexamethasone `dexamethasone` | 1.0 | 2.0 | **2 game days** (linear) | Glucocorticoid; strongly suppresses cytokine and leukotriene transcription; arrests cytokine storms |

* **Zero-Order Metabolic Elimination Rates**:

  ```scala
  // Zero-order linear clearance:
  salicin = Math.max(salicin - 3.0f / 72000f, 0f)       // -4.167e-5 per tick (clears 3.0 in 72,000 ticks / 3 game days)
  dexamethasone = Math.max(dexamethasone - 2.0f / 48000f, 0f) // -4.167e-5 per tick (clears 2.0 in 48,000 ticks / 2 game days)
  ```

> **Critical Distinction**: **Neither salicin nor dexamethasone directly kills or clears pathogens.** Their clinical role is strictly **anti-inflammatory and immunosuppressive**:
> - Proper dosage prevents fatal cytokine storms (inflammation >= 75 destroys host tissue and causes immune collapse);
> - **Overdosing** suppresses resting inflammation below the minimum immune clearance band (< 12), causing **iatrogenic immunosuppression**. Because pathogen clearance requires an active immune system, immunosuppressed players lose clearance capability, causing pathogens to grow unrestricted!

### Mandrake Alkaloids `scopolamine` / `atropine`

Mandrake fruit and seeds introduce two independent alkaloids, each capped at **5.0**, **clearing linearly over 1 game day**:

```scala
// Mandrake alkaloid linear clearance: 5.0 units clear in 1 game day (24,000 ticks)
scopolamine = Math.max(scopolamine - 5.0f / 24000f, 0f) // -2.083e-4 per tick
atropine = Math.max(atropine - 5.0f / 24000f, 0f)       // -2.083e-4 per tick
```

| Ingested Item | Scopolamine (S_scop) | Atropine (A_atro) |
| --- | --- | --- |
| `mandrake_fruit` | **+1.0** | **+0.1** |
| `mandrake_seeds` | **+0.75** | **+0.1** |

Their combined load `alkaloidSum = scopolamine + atropine` modulates **temperature** (elevates hypothalamic set point independently of prostaglandins, meaning **salicin cannot reduce mandrake fever**):

```scala
val deltaT =
  if (alkaloidSum >= 4.0f) 4.0f      // Core temp target -> 41.0 °C
  else if (alkaloidSum >= 2.5f) 2.5f // Core temp target -> 39.5 °C
  else if (alkaloidSum >= 1.5f) 1.0f // Core temp target -> 38.0 °C
  else 0.0f
```

while individual and combined levels dictate **visual blur**:

```scala
val visualBlurActive = scopolamine >= 2.3f || atropine >= 2.3f || alkaloidSum >= 2.7f
```

When active, render fog contracts down to **8 blocks**; distant blocks become blurred.

Drug fever and visual blur operate independently and stack with infection effects: eating multiple mandrakes causes both severe hyperthermia and blurred vision.

### Psilocybin and Psilocin `psilocybin` / `psilocin`

Ingesting raw *Gymnopilus* (cooked mushrooms destroy both alkaloids) introduces two compounds, capped at **10.0**:

| Compound | Biological Action | Clearance / Kinetics |
| --- | --- | --- |
| Psilocybin `psilocybin` | Inactive prodrug | Converted 1:1 into psilocin over **half a game day** (1.3 per half day) |
| Psilocin `psilocin` | Active psychedelic compound; visual distortions and drug fever | Fixed metabolic clearance rate of **1.3 per game day** (zero-order elimination) |

* **Conversion and Elimination Dynamics**:

  ```scala
  // Inactive prodrug conversion: 1.3 units convert over half a game day (12,000 ticks)
  val converted = Math.min(psilocybin, 1.3f / 12000f) // 1.083e-4 per tick
  psilocybin -= converted

  // Active psilocin elimination: fixed zero-order rate of 1.3 units per game day (24,000 ticks)
  psilocin = clamp(psilocin + converted - (1.3f / 24000f), 0f, 10.0f) // -5.417e-5 per tick
  ```

One raw mushroom yields **1.3 psilocybin and 1.3 psilocin** (total 2.6 units), clearing in **exactly two game days**. Because clearance is zero-order rather than exponential, consuming five mushrooms (13 units total) extends the trip linearly to **ten game days**.

Four visual stages are rendered via client post-processing shaders according to active `psilocin` levels:

| Threshold | Visual Effect |
| --- | --- |
| `psilocin > 1.2` | **Edge Ray Projection**: Directional prismatic chromatic streaks emit outwards from block edges. |
| `psilocin > 1.7` | **Chromatic Color Shifts**: Blocks blend with oscillating bright hues; subtle full-screen chromatic noise overlays the scene. |
| `psilocin > 2.5` | **Spatial Warping**: Mild full-screen sinusoidal distortion waves bend viewport geometry. |
| `psilocin > 5.0` | **Intense Hallucinatory Warping**: Violent spatial distortion accompanied by rising core body temperature (up to **39.0 °C**). |
| `psilocin >= 7.0` | Drug fever reaches up to **41.0 °C**. |

### Data Persistence Architecture

Backed by Fabric's **Data Attachment API**:

| Attachment Key | Persisted | Network Synced | Purpose |
| --- | --- | --- | --- |
| `aliment:physiology` | Yes (**Resets on Death**) | No | Authoritative full state on the server. Does not use `copyOnDeath`: a respawned body is fresh and infection-free. |
| `aliment:client_state` | Yes | Yes (to client) | Shiver sequence, oscillation amplitude, and integer hydration for HUD and camera rendering. |
| `aliment:runtime` | No | No | Ephemeral tick counters, bat contact cooldowns, and last-synced caches. |

Hydration packets are dispatched only when **integer values change**, reducing network traffic to ~1 packet per 270 ticks during steady dehydration.

### Creative Mode Immunity

`AlimentSymptoms.isFrozen(player)` activates in Creative mode:
* **Physiology ticks freeze**: pathogens do not grow or clear, temperature does not adjust, and drugs do not metabolize.
* **Symptoms are suppressed**: no effect icons, no sepsis damage, no mining fatigue, and exhaustion multipliers stay at 1.0.
* **Shaders and HUD clear**: active post-processing effects and camera shivering are dismissed immediately.
* **Ingestion is blocked**: eating or drinking does not modify internal physiology values.
Entering Creative mode freezes state rather than resetting it; returning to Survival resumes the exact physiological state.

---

## Infection Pathways

Pathogens enter the body through three deterministic infection pathways:

| Source | Pathogen | Probability | Inoculum Size |
| --- | --- | --- | --- |
| Raw meat (beef, pork, chicken, mutton, rabbit, fish), rotten flesh, poisonous potato | Bacteria | **30%** | +6 load |
| Raw willow bark soup (including salted variants) | Bacteria | **30%** | +6 load |
| Proximity within 2 blocks of **any living mob** | Virus | **5% / second** | +5 load |
| **Severe Immunosuppression** (inflammation ≤ 12) | Bacteria | **15% / second** | **+4 to +12 (random)** |

* Food transmission triggers on `Item.finishUsingItem`.
* Contact transmission evaluates against all nearby `Mob` entities (cows, wolves, villagers, bats). Standing near dense livestock for 10 seconds almost guarantees viral inoculation.
* **Opportunistic Infection**: When inflammation drops to ≤ 12, opportunistic bacterial colonization occurs without external exposure, making excessive dosing of salicin or dexamethasone genuinely dangerous.

---

## Mathematical Model

The physiological model integrates per tick (`AlimentPhysiology.tick(data)`), decoupled from Minecraft engine classes.

### Infection Dynamics and Immune Competence

The baseline logistic proliferation rate of pathogens per tick:

```scala
// Logistic proliferation rate: r = 0.0004 per tick, carrying capacity K = 100.0
val baseGrowth = 0.0004f * load * (1.0f - load / 100.0f)
```

The active immune clearance rate:

```scala
// Active immune clearance: base clearance rate = 20.0 load / 48,000 ticks (2 game days)
val immuneClearance = if (immuneActive || load > 20.0f) {
  baseGrowth * competence + (20.0f / 48000f) * competence
} else {
  0.0f
}
```

where `competence` is the immune competence function of composite inflammation.

- **Covert Growth Phase (`load <= 20.0`)**:
  - Pathogens reproduce logistically unchecked by the immune system (`immuneActive == false` implies `immuneClearance == 0.0`).
  - Inflammation remains resting (`inflammation == 25.0`).
  - Core body temperature remains normal (`temperature == 37.0 °C`).
- **Immune Engagement Phase (`20.0 < load <= 55.0`)**:
  - Exceeding load 20.0 flags `immuneActive = true`, initiating mediator release.
  - Elevated prostaglandins raise core temperature up to 39.5 °C.
  - At optimal competence (`competence == 1.0`), active clearance eradicates the infection within **2 in-game days (48,000 ticks)**.
  - Upon reaching load 0, `immuneActive` resets to `false`, and mediators return to baseline.
- **Cytokine Storm and Septic Shock (`load > 55.0`)**:
  - If immune clearance fails (due to drug-induced immunosuppression or deficiency), infection surpasses load 55.0.
  - Severe biological stress triggers a 2.5x cytokine surge, driving inflammation into storm territory (`inflammation >= 75.0`).
  - Temperature ceiling unlocks to 42.0 °C, and septic magic damage is inflicted directly on the player.

The immune competence follows an asymmetric Gaussian bell curve centered at optimal inflammation = 25.0:

```scala
// Asymmetric Gaussian bell curve: sigma = 10.0 (below 25) / 15.0 (above 25)
val delta = inflammation - 25.0f
val sigma = if (inflammation < 25.0f) 10.0f else 15.0f
val bell = Math.exp(-(delta * delta) / (2.0 * sigma * sigma)).toFloat
val lowEndSuppression = clamp(inflammation / 12.0f, 0.0f, 1.0f)
val competence = bell * lowEndSuppression
```

| Inflammation | 0 | 6 | 12 | 25 | 40 | 50 | 75 | 100 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| Competence | 0.00 | 0.22 | 0.69 | **1.00** | 0.61 | 0.25 | 0.004 | ~0 |

**Too little inflammation paralyzes immune response; excessive inflammation causes immune storm collapse while harming the host.**

### Mediator Kinetics

Biological inflammatory stimulus as a function of total pathogen load:

```scala
val stimulus = if (!immuneActive && load <= 20.0f) {
  0.0f
} else if (load <= 55.0f) {
  (load - 20.0f) / (55.0f - 20.0f)
} else {
  1.0f + 2.5f * clamp((load - 55.0f) / (100.0f - 55.0f), 0.0f, 1.0f)
}
```

Pharmacological suppression and damping (Salicin & Dexamethasone):

```scala
val salicinEffect = Math.min(salicin / 1.0f, 1.0f)
val dexEffect = Math.min(dexamethasone / 1.0f, 1.0f)
val maxDrugEffect = Math.max(salicinEffect, dexEffect)

val damping = Math.max(1.0f - 0.88f * maxDrugEffect, 0.05f)
val overdose = Math.max(salicin / 1.0f - 1.0f, 0.0f) + Math.max(dexamethasone / 1.0f - 1.0f, 0.0f)
val baselineScale = Math.max(1.0f - overdose * 1.8f, 0.0f)
```

Target mediator asymptotic values:

```scala
// Resting baselines: (H, P, Lk, C, B) = (25.0, 30.0, 30.0, 20.0, 25.0)
val cytokineTarget = 20.0f * baselineScale + 100.0f * stimulus * damping * (1.0f - 0.4f * dexEffect)
val histamineTarget = 25.0f * baselineScale + 45.0f * stimulus * damping
val bradykininTarget = 25.0f * baselineScale + 60.0f * stimulus * damping
val prostaglandinTarget = 20.0f * baselineScale +
  (cytokine * 0.5f + 25.0f * stimulus) * damping * (1.0f - 0.5f * salicinEffect)
val leukotrieneTarget = 20.0f * baselineScale +
  (cytokine * 0.5f + 20.0f * stimulus) * damping * (1.0f - 0.4f * dexEffect)

// Each mediator approaches its target value via exponential relaxation (k_m = 0.002 / tick, ~25s)
mediator += (target - mediator) * 0.002f
```

Prostaglandins (P) and leukotrienes (Lk) are **downstream products induced by cytokines (C)**, explaining why dexamethasone (acting upstream) and salicin (acting downstream) possess distinct clinical profiles.

### Targeted Antimicrobial Pharmacokinetics (Berberine & Glycyrrhizin)

Unlike symptomatic anti-inflammatories, **Berberine** and **Glycyrrhizin** directly attack and clear pathogens:

For targeted drug concentration in `[0.0, 7.0]`, with deceleration threshold 1.5 and suppression threshold 3.0:

```scala
if (drugConc >= 3.0f) {
  // Complete suppression of replication; drug clearance scales with concentration
  val drugClearance = (100.0f / (1.5f * 24000f)) * (drugConc / 3.0f)
  load = Math.max(load - immuneClearance - drugClearance, 0.0f)
} else if (drugConc > 1.5f) {
  // Reduced pathogen replication rate allowing immune system to overpower it
  val slowRatio = (drugConc - 1.5f) / (3.0f - 1.5f)
  val reducedGrowth = baseGrowth * (1.0f - 0.75f * slowRatio)
  load = Math.max(load + reducedGrowth - immuneClearance, 0.0f)
} else {
  // Normal pathogen replication minus immune clearance
  load = Math.max(load + baseGrowth - immuneClearance, 0.0f)
}
```

- **Berberine (0.0 ~ 7.0)**: Specifically targets **Bacteria**
  - At the CYP3A4 baseline it clears from peak 7.0 over **2.5 game days (60,000 ticks)**, and the
    enzyme index scales that rate - see [Naringin and CYP3A4](#naringin-and-cyp3a4):
    ```scala
    // CYP3A4_NORMAL is 85, so at a clean baseline the factor is exactly 1 and this is the
    // 7.0f / 60000f = -1.167e-4 / tick the model has always cleared berberine at.
    berberine = Math.max(
      berberine - (7.0f / 60000f) * (cyp3a4 / 85.0f),
      0.0f)
    ```

- **Glycyrrhizin (0.0 ~ 7.0)**: Specifically targets **Viruses**
  - Clears from peak 7.0 over **2.0 game days (48,000 ticks)**:
    ```scala
    glycyrrhizin = Math.max(glycyrrhizin - 7.0f / 48000f, 0.0f) // -1.458e-4 / tick
    ```

### Naringin and CYP3A4

Grapefruit is not a drug in this model and naringin has no effect of its own. What it has is a
*consequence*: it holds down **CYP3A4**, the liver enzyme that clears berberine, and berberine is
cleared by nothing else. So the three numbers form a chain - a slice raises the naringin, the
naringin picks an enzyme activity, and the enzyme activity scales a metabolism.

Naringin runs **0.0 ~ 10.0** - ten grapefruit slices - and clears linearly from the cap over **one
game day (24,000 ticks)**, half the lifetime of glycyrrhizin:

```scala
naringin = Math.max(naringin - 10.0f / 24000f, 0.0f) // -4.167e-4 / tick
```

CYP3A4 runs **0.0 ~ 100.0** and sits at **85** in a body that has eaten no grapefruit. It is a
**curve** over the naringin rather than a step, so the index slides from one level to the next instead
of snapping, and one more slice always takes a little more off the liver. The calibration points are
the whole of it:

| Naringin | CYP3A4 | Berberine cleared at |
| --- | --- | --- |
| 0 | **85** | 1.00x - the rate the model was written with |
| 2 | **60** | 0.71x |
| 4 | **45** | 0.53x |
| 7 | **25** | 0.29x |
| 8.5 or more | **10** | 0.12x |

Between two of those the activity is interpolated with a smoothstep, whose value and slope are both
zero at each end, so the curve is flat *at* every calibration point and has no kink where two segments
meet. Past 8.5 it holds at 10, which is what makes eight slices the deepest the inhibition goes:

```scala
// The knots, in ascending naringin order.
val knots = Array((0f, 85f), (2f, 60f), (4f, 45f), (7f, 25f), (8.5f, 10f))

def cyp3a4For(naringin: Float): Float = {
  val load = Math.max(naringin, 0f)
  var segment = 0
  while (segment < knots.length - 1 && load > knots(segment + 1)._1) segment += 1
  if (segment >= knots.length - 1) knots(knots.length - 1)._2
  else {
    val (fromNaringin, fromActivity) = knots(segment)
    val (toNaringin, toActivity) = knots(segment + 1)
    val t = clamp((load - fromNaringin) / (toNaringin - fromNaringin), 0f, 1f)
    fromActivity + (toActivity - fromActivity) * (t * t * (3f - 2f * t))
  }
}
```

The enzyme index is written by the tick, not by eating: a slice moves `naringin` and the next tick
reads `cyp3a4` off it, so the two can disagree for exactly one tick and never longer. That is also
what makes the index recover - once the naringin is gone the curve returns 85 on its own, with no
separate recovery term to keep in step.

The size of the effect is worth stating plainly, because it is the first interaction in the mod where
one thing a player eats changes how long another lasts. A single coptis herb (**1.1 berberine**)
clears in about **9,400 ticks** on its own. Eat nine grapefruit slices first and the same herb takes
about **20,100** - more than twice as long, because for the first part of that the liver is crawling
back up from 10 towards 85 rather than running at full rate. The day-long lifetime of the naringin is
what bounds it: a shorter naringin would leave the deepest knot doing nothing for most of the dose.

### Hydration and Sweating Model

Total hydration depletion per tick:

```scala
// Total water loss per tick = renalLoss + sweatLoss
var renalLoss = (100.0f / 120000f) * (1.0f + 0.01f * Math.max(water - 100.0f, 0.0f))
if (sodium > 150.0f) renalLoss *= 1.3f
if (calcium > 3.0f)  renalLoss *= 1.2f

val deltaT = Math.max(temperature - 37.0f, 0.0f)
val sweatLoss = if (deltaT > 1.25f) {
  Math.max((-1.0f / 3360.0f) * deltaT + (1.0f / 4200.0f) * deltaT * deltaT, 0.0f)
} else {
  0.0f
}

water = clamp(water - renalLoss - sweatLoss, 0.0f, 200.0f)
```

Total Water Depletion Benchmarks:
- 37.0 °C (Basal): `waterLoss = 1 / 1200 / tick` (100 units consumed in exactly 5 game days).
- 39.0 °C (Fever): `waterLoss = 1 / 840 / tick` (100 units consumed in 3.5 game days).
- 40.0 °C (Severe Hyperthermia): `waterLoss = 1 / 480 / tick` (100 units consumed in 2.0 game days).

### Electrolyte Clearance and Homeostasis

The dynamics of each serum electrolyte:

```scala
// Dilution flush from overhydration (water > 100)
val flushFraction = clamp((water - 100.0f) / 100.0f, 0.0f, 1.0f) * 0.000009f

// Sweating loss from hyperthermia (temp > 38.25 °C)
val sweatFraction = if (temperature > 38.25f) Math.max(temperature - 37.0f, 0.0f) * 0.000002f else 0.0f

// Excretion multiplier: (Na: 1.0, Cl: 1.0, K: 0.7, Mg: 0.4, Ca: 0.4)
val totalLossRate = (flushFraction + sweatFraction) * excretionMultiplier * normalConcentration
val restorationRate = 0.00005f * (normalConcentration - currentConcentration)

electrolyte += restorationRate - totalLossRate
```

Sodium and chloride wash out fastest; over-drinking fresh water quickly triggers dilutional hyponatremia.

### Thermal Balance

Target core temperature calculation:

```scala
val prostaglandinExcess = Math.max(prostaglandin - 20.0f, 0.0f)
val feverMax = if (pathogenLoad <= 55.0f) 2.5f else 4.0f
val fever = Math.min(0.05f * prostaglandinExcess, feverMax)

val thyroidDelta = clamp(2.0f * (iodine - 0.50f) / 0.50f, -0.8f, 0.8f)
val ambientOffset = 0.6f * (ambientTemperature - 37.0f)

val targetTemperature = clamp(
  37.0f + fever + pyrogen + thyroidDelta + anticholinergicFever + psilocinFever + ambientOffset,
  30.0f, 42.0f
)

// Thermal relaxation rate toward target (k_T = 0.0004 / tick, ~2500 ticks to close 63% gap)
temperature += (targetTemperature - temperature) * 0.0004f
```

| Biome Ambient (per 1.0 biome temperature) | ×2.0 relative to temperate 0.8 |
| --- | --- |
| Neutral Comfort Zone | **Ignored if within ±2.0 °C** |
| Submerged in Water / Rain | −2.5 °C |
| Buried in Powder Snow | −4.0 °C (stacks with wetness) |
| On Fire | +4.0 °C |
| In Lava | +6.0 °C |

---

## Clinical Symptoms

### Pathogen Symptoms (Load ≥ 8)
- Mining speed reduced up to **-6%**.
- Food exhaustion multiplier increased up to `1 + 0.5 * severity`.
- Camera tremors: rolled every **15 seconds** at a **20% chance** for 16 ticks.
- **Septic Magic Damage (Load ≥ 60)**: `1 + (load - 60) / 40` damage every two seconds, ignoring armor.

### Hydration Symptoms
- Water > 100: Weakness, mining speed -8%, increased food exhaustion.
- Water > 150: **Nausea** (dilutional hyponatremia). Camera shivering only unlocks after this threshold.
- Water < 15: Hunger.

### Mineral Disorders

| Mineral | Severe Deficit | Mild Deficit | Mild Excess | Severe Excess |
| --- | --- | --- | --- | --- |
| Sodium | Nausea + Slowness (Confusion) | Weakness | Hunger (Thirst) | Hunger + Weakness |
| Potassium | Weakness II + Mining Fatigue | Weakness | Weakness | Slowness II + Periodic Magic Damage (Arrhythmia) |
| Magnesium | Weakness + Slowness (Tremor) | Weakness | Slowness | Slowness + Weakness (Lethargy) |
| Chloride | Nausea (Alkalosis) | Weakness | Hunger (Acidosis) | Hunger + Nausea |
| Calcium | Slowness + Weakness (Tetany) | Weakness | Slowness | Slowness II + Weakness (Lethargy) |
| Iodine | Slowness II + Weakness II + Fatigue | Weakness + Slowness + Hunger | Hunger + Nausea | Adds Weakness |
| Vitamin C | Mining Fatigue + Weakness | Mining Fatigue | None (Safely Excreted) | None (Safely Excreted) |

### Core Temperature Symptoms
- **Fever (38.5 – 40.0 °C)**: Weakness + Mining Fatigue, perimeter heat shimmer (`aliment:heat_haze`).
- **Severe Hyperthermia (≥ 40.0 °C)**: Adds post-processing dynamic motion blur (`aliment:heat_blur`).
- **Hypothermia (≤ 36.0 °C)**: Weakness + Mining Fatigue + Slowness, perimeter cold shivering tint (`aliment:cold_shiver`).

### Glycaemic Symptoms

| Glucose | Effect |
| --- | --- |
| **< 2.0 mmol/L** | Mining Fatigue |
| **< 1.7 mmol/L** | Mining Fatigue + Weakness |
| **< 1.3 mmol/L** | The above, plus magic damage every two seconds: `(1.3 - glucose) * 1.0` |

Glucose is spent continuously and only food restores it, so an unfed player eventually reaches these
thresholds however healthy the rest of the body is. The damage is magic, so armour is no help - the
brain has no fuel but glucose.

---

## Blood Glucose and Insulin

Glucose is the second quantity in the model - after water - that is **spent rather than regulated to a
set point**. There is no gluconeogenesis: the body burns glucose every tick and only eating puts it
back, so `glucose` is a resource the player manages rather than a value the model defends.

### Reference Values

| Quantity | Value | Unit |
| --- | --- | --- |
| Normal / fasting | **5.0** | mmol/L |
| Reference range | **4.0 – 5.5** | mmol/L |
| Fasting floor (2 game days) | **3.5** | mmol/L |
| Elevated (steep insulin response) | **> 8.0** | mmol/L |
| Model limits | 0.0 – 30.0 | mmol/L |
| Baseline insulin index | **1.0** | index |

### What Food Is Worth

| Food class | Glucose | Examples |
| --- | --- | --- |
| Starch, sugar and bread | **+0.7** | `minecraft:bread`, sweet drinks, a bowl of cooked rice |
| Mixed dish | **+0.6** | an assembled plate: a stew, a sandwich, a pasta |
| Cooked meat and fish | **+0.5** | cooked beef, porkchop, chicken, mutton, rabbit, cod, salmon |
| Raw meat and fish | **+0.4** | beef, porkchop, chicken, mutton, rabbit, cod, salmon, tropical fish, rotten flesh |
| Plant food | **+0.4** | fruit, vegetables, kelp, seaweed, mushrooms, every willow bark soup |

Bread is the only food that leaves the reference range on its own from a normal 5.0 (5.0 + 0.7 =
5.7), which is why it is separated from the rest of the plant food. Everything edible that is not meat
or bread counts as plant food, so a player cannot dodge the glucose cost of eating by living on
berries.

**The mixed dish band exists for assembled food.** A plate built from starch, meat and vegetable at
once - a stew, a sandwich, a pasta - is charged **+0.6**, between plant food and bread, because the
meat and the fat in it slow the starch down. Charging it as bread would make every cooked meal a
sugar spike, and charging it as plant food would make it cheaper than the rice inside it. Farmer's
Delight's thirty-six dishes are the foods that use this band; the mod's own plain ingredients do not.

### Per-Tick Dynamics

Three terms move glucose, and they are separate because they behave differently:

```scala
// 1. The basal drain: a flat 1.5 mmol/L over two in-game days, which is the fasting path from
//    5.0 down to 3.5. Past 3.5 it scales with what is left, so the fall slows down rather than
//    running straight to zero.
val basal =
  if (glucose > 3.5f) 1.5f / 48000f          // 3.125e-5 per tick
  else (1.5f / 48000f) * (glucose / 3.5f)

// 2. The body's own insulin. It is switched off at the bottom of the reference range: the pancreas
//    does not drive a body hypoglycaemic on its own.
val endogenousDrive = 0.0001f * Math.max(0f, insulin - 1.0f)
val endogenous =
  if (glucose <= 4.0f) 0f
  else Math.min(endogenousDrive, glucose - 4.0f)

// 3. Injected insulin aspart, which has no such brake.
val injected = 0.00008f * insulinAspart

glucose = clamp(glucose - basal - endogenous - injected, 0f, 30f)
```

The insulin index itself relaxes towards what the current glucose asks for, at 0.0005 per tick (a
2,000-tick time constant):

```scala
// Two ramps, both starting at the normal 5.0: a gentle one across the reference range, which is
// what actually disposes of an ordinary meal, and a twice-as-steep one past 8.
val target =
  if (glucose <= 5.0f) 1.0f
  else if (glucose <= 8.0f) 1.0f + (glucose - 5.0f) * 2.0f
  else 7.0f + (glucose - 8.0f) * 4.0f

insulin = clamp(insulin + (target - insulin) * 0.0005f, 0f, 60f)
```

> **Why the gentle ramp exists.** A literal "insulin only above 8" threshold cannot bring a 9 or a 10
> back into the reference range inside half a game day: the index switches off the moment glucose
> crosses 8, and the residual insulin is gone before the job is done. The ramp across the reference
> range is what makes the half-day promise true, and the steep segment past 8 is what "the higher the
> sugar, the more insulin and the faster it comes down" means.

### Verified Behaviour

| Scenario | Result |
| --- | --- |
| Fasting from 5.0 | Reaches **3.49** in exactly 2 game days (48,000 ticks) |
| Fasting from 3.5 | The next 2 days cost **1.22** rather than 1.5 - the fall slows down |
| A meal of 8.0 | Back to **4.87** within half a game day |
| A meal of 10.0 | Back to **4.77** within half a game day |
| A meal of 20.0 | Back to **3.90** within half a game day |
| A meal of 30.0 | Back to **3.85** within half a game day |
| After 2,400 ticks | A 20 falls further than a 9, at every point on the curve |
| One injection of insulin aspart | 5.0 -> **2.74**: a dip, not yet a crash |
| Two injections | A hypoglycaemic crash, because nothing switches the injected insulin off |
| Eight game days without food | **0.97** mmol/L: a hypoglycaemic crisis |

### Insulin Aspart `insulinAspart`

| Property | Value |
| --- | --- |
| Delivered by | `aliment:insulin_injection` (right-click), **+10.0** a dose |
| Ceiling | **60.0** |
| Clearance window | **1 game day** (linear) |

```scala
insulinAspart = Math.max(insulinAspart - 60.0f / 24000f, 0f) // -2.5e-3 per tick
```

The one drug in the mod that makes a body worse on purpose. It is deliberately a **separate quantity**
from the body's own `insulin`: the pancreas switches its own secretion off as glucose falls, and
injected aspart is not switched off by anything. One dose from a normal body is survivable; two
stacked inside the cooldown are a crisis, and eating is the only way out.

---

## The Glucose Meter

The meter is the one part of the mod a player cannot discover by experiment - a meter is useless
without a strip, and a strip is useless without a lancet - so the three are found together in chests.

| Item | Loot chance |
| --- | --- |
| `aliment:insulin_injection` | **10%** |
| `aliment:glucose_meter` | **35%** |
| `aliment:glucose_test_strip` | **35%**, as a stack of **5 – 9** |
| `aliment:microneedle` | **35%** |

All four generate in every vanilla village chest and in the pillager outpost's.

**The diagnostic chain:**

1. Right-click with the **microneedle**. The finger bleeds for **15 seconds** (300 ticks) and the
   lancet takes one point of damage.
2. Right-click with a **test strip** while the finger is still bleeding. It becomes a **bloodied test
   strip**; a strip used after the drop has dried is refused, with an action-bar hint. The drop is
   spent on that strip, so a second strip needs a second prick.
3. Hold the **meter in the main hand** and the **bloodied test strip in the off hand**, and
   right-click. The reading is printed in chat - `Blood glucose: 5.1 mmol/L`, localised as
   `当前血糖：5.1 mmol/L` in `zh_cn` - and the strip is used up.

The strip is read from the off hand and the meter from the main hand specifically, so there is exactly
one arrangement that works and no ambiguity about which item is doing what.

### A strip is a sample, not a sensor

The reading a bloodied strip reports is **the glucose the player had when the blood was taken**, stored
on the strip itself as a data component in mmol/L. It does not track the body afterwards.

That is the point of taking a sample. A reading that followed the player's blood sugar would say
nothing about the moment it was taken, and would make it impossible to prick a finger, eat, and then
compare - or to take a strip to another player and have it still mean what it meant.

Because the value rides on the stack, it survives being put in a chest, dropped on the ground, or
handed over, exactly like the concentration on a bottle of wine. A strip with no sample on it at all -
one left over from before this data existed, or one spawned by a command - falls back to the body's
current glucose, which is how every strip behaved before.

The meter reads the sample **before** it consumes the strip: emptying a stack takes its components
with it, so reading afterwards would find no sample at all.

---

## Applied Mixins

Implemented in **Java** to allow Loom annotation processing and compile-time verification:

| Mixin | Target | Technical Requirement |
| --- | --- | --- |
| `ItemMixin` | `Item.finishUsingItem` | Hooks finished ingestion before server tick inventory mutation |
| `PlayerMixin` | `Player.causeFoodExhaustion` | Scales all exhaustion sources uniformly |
| `GrindstoneInputSlotMixin` | `GrindstoneMenu$2` / `GrindstoneMenu$3.mayPlace` | Allows botanical herbs, salt ore, and bark into vanilla grindstone slots |
| `GrindstoneMenuMixin` | `GrindstoneMenu.computeResult` | Integrates `AlimentGrinding` recipe translation |
| `CameraMixin` (Client) | `Camera.alignWithEntity` | Injects rotational camera shake without moving physical entity hitboxes |
| `FogRendererMixin` (Client) | `FogRenderer.setupFog` | Contracts visual fog distance to 8 blocks during anticholinergic intoxication |
| `HudMixin` (Client) | `Hud.extractPlayerHealth` | Positions thirst HUD directly above player health hearts |

---

## Salt Manufacture and Processing

```
Rock Salt Ore ──Grindstone──> Crude Salt ×9 ──Grindstone──> Crude Salt Powder
                                                    │
                                                    ├─ Use on Water Cauldron ──> Brine Cauldron
                                                    │                                │
                                                    │                         Campfire underneath
                                                    │                                │ Evaporates every 30s
                                                    │                                ▼
                                                    │                       Boiled Dry ──> Salt Powder ×1
                                                    │
Crude Salt + Water/Soup/Stew ──> Crude Salt Water / Crude Salt Broth
Salt Powder + Water/Soup/Stew ──> Salt Water / Refined Salt Broth
```

* **Stirring Rod**: Right-click a brine cauldron to advance evaporation by one stage, consuming 1 durability (16 max). Crafted with **two sticks vertically aligned**.
* Brine cauldrons feature 3 visual concentration stages (`stage 0/1/2`).
* Removing the campfire pauses evaporation without resetting progress.
* Rock salt ore drops itself and requires a stone pickaxe or better, generating between y=20–90 at 6 veins per chunk.

---

## Diagnostic Commands

```
/aliment status                      Inspect full physiological metrics
/aliment fever [temperature]         Induce calibrated fever (or hypothermia) via pyrogens (OP only)
/aliment cure                        Reset body to perfect health and clear screen shaders (OP only)
/aliment set <field> <value>         Directly configure physiological parameters (OP only)
```
