package com.github.kusa233.aliment.physiology.model

/**
 * The per-tick model, and every number it is built from.
 *
 * Every rate below is expressed per tick. The design goals, in the words of the feature spec:
 *
 *  - a healthy player drifts back to an inflammation of roughly 20..30;
 *  - too little inflammation means the infection runs away, too much means an immune storm that
 *    hurts the host without clearing the pathogen;
 *  - salicin and dexamethasone control the infection by damping the mediator response, while an
 *    overdose pushes inflammation below the safe band and therefore makes the infection worse;
 *  - a full bladder drains to one cell over one in-game day;
 *  - drinking far too much water dilutes and flushes the electrolytes;
 *  - every mineral is regulated towards its own normal value, so a player who ignores kelp and salt
 *    settles at a steady state rather than drifting off to an extreme;
 *  - core temperature is driven by prostaglandin (PGE2 is the fever mediator), by the environment
 *    the player is standing in, and by the thyroid.
 *
 * There is no Minecraft and no Kotlin in this file, and there never should be: it is a pure function
 * of [[ModelState]] and floats.
 */
object Physiology {

  // ------------------------------------------------------------------ mediators

  private final val HISTAMINE_BASE = 25f
  private final val PROSTAGLANDIN_BASE = 20f
  private final val LEUKOTRIENE_BASE = 20f
  private final val CYTOKINE_BASE = 20f
  private final val BRADYKININ_BASE = 25f

  /** Pathogen-driven response, at a full load, before any drug damping. */
  private final val CYTOKINE_RESPONSE = 100f
  private final val HISTAMINE_RESPONSE = 45f
  private final val BRADYKININ_RESPONSE = 60f
  private final val PROSTAGLANDIN_RESPONSE = 25f
  private final val LEUKOTRIENE_RESPONSE = 20f

  /** How much of the prostaglandin drive comes from induced COX-2 rather than the pathogen. */
  private final val PROSTAGLANDIN_FROM_CYTOKINE = 0.5f
  private final val LEUKOTRIENE_FROM_CYTOKINE = 0.5f

  /** How quickly a mediator moves towards its target. 1/0.002 = 500 ticks, about 25 seconds. */
  private final val MEDIATOR_APPROACH = 0.002f

  /** Drugs cut the whole pathogen-driven response by this fraction at full effect. */
  private final val DRUG_RESPONSE_DAMPING = 0.88f

  /** Extra, mediator-specific effects on top of the general damping. */
  private final val DEX_CYTOKINE_EXTRA = 0.4f
  private final val SALICIN_PROSTAGLANDIN_EXTRA = 0.5f
  private final val DEX_LEUKOTRIENE_EXTRA = 0.4f

  /**
   * A dose above the effective concentration scales the *resting* mediator levels down, which is
   * what turns an overdose into immunosuppression: at the drug cap this drives the resting
   * inflammation from 25 into single digits, low enough for the competence curve to collapse.
   */
  private final val OVERDOSE_BASELINE_DROP = 1.8f

  /** Drugs can overshoot their effective concentration by this factor. */
  private final val DRUG_MAX_SUPPRESSION = 1.5f

  // ------------------------------------------------------------------ pathogens

  /**
   * Logistic growth rate once a pathogen is present. Tuned so that an untreated infection plays out
   * over in-game minutes, not seconds.
   */
  private final val GROWTH_RATE = 0.0004f

  /**
   * Base clearance rate when active immunity is engaged.
   * Clears 20 load to 0 in exactly 2 in-game days (48,000 ticks) under normal immune competence.
   */
  private final val CLEARANCE_BASE_RATE = 20f / (2f * 24000f)

  /**
   * Base drug clearance rate when pathogen is suppressed (drug >= suppressThreshold).
   * Brings maximum pathogen load (100) down to 0 in 1.5 in-game days (36,000 ticks) at threshold.
   */
  private final val DRUG_SUPPRESS_CLEARANCE_RATE = ModelConstants.MAX_PATHOGEN / (1.5f * 24000f)

  private final val COMPETENCE_SIGMA = 15f

  // ------------------------------------------------------------------ water

  /** Extra water loss per point above the normal band, on top of the base loss. */
  private final val OVERHYDRATION_DIURESIS = 0.01f

  /**
   * How hard a full bladder flushes the electrolytes out, as a fraction of each mineral's normal
   * concentration per tick at the very top of the water scale.
   */
  private final val OVERHYDRATION_FLUSH_FRACTION = 0.000009f

  /**
   * Fever sweat parameters:
   * When deltaT > 1.25 C (core temperature above 38.25 C):
   *   sweatLoss = SWEAT_A * deltaT + SWEAT_B * deltaT^2
   * At 39 C (deltaT = 2): total loss is exactly 1/840 (3.5 game days to drain 100 water).
   * At 40 C (deltaT = 3): total loss is exactly 1/480 (2.0 game days to drain 100 water).
   */
  private final val SWEAT_A = -1.0f / 3360.0f
  private final val SWEAT_B = 1.0f / 4200.0f

  /** Sweat carries salt away too, as a fraction of normal per degree per tick. */
  private final val SWEAT_MINERAL_FRACTION_PER_DEGREE = 0.000002f

  /** The body slowly restores electrolytes on its own. 1/0.00005 = 20000 ticks, under a day. */
  private final val ELECTROLYTE_HOMEOSTASIS = 0.00005f

  /** Relative urinary loss per electrolyte; sodium and chloride go first. */
  private final val EXCRETION_SODIUM = 1.0f
  private final val EXCRETION_CHLORIDE = 1.0f
  private final val EXCRETION_POTASSIUM = 0.7f
  private final val EXCRETION_MAGNESIUM = 0.4f
  private final val EXCRETION_CALCIUM = 0.4f

  /** Iodine is lost the same way, and sweating is a real cause of iodine deficiency. */
  private final val EXCRETION_IODINE = 0.7f

  // ------------------------------------------------------------------ iodine

  /**
   * Iodine is the one mineral the diet has to supply, and the body does not make it: what is not
   * eaten is lost.
   *
   * The loss is a plain leak with nothing pulling the other way, sized so that a full store runs
   * down to the hard floor in [IODINE_DEPLETION_TICKS] - 0.50 umol/L to 0.05 in exactly three
   * in-game days - and then stays there. A proportional controller towards normal is deliberately
   * *not* modelled: it is exactly what would stop the store from ever running out, and iodine is
   * meant to be a dietary need rather than a bonus, so a player who lives on bread ends up severely
   * hypothyroid.
   *
   * Sweating carries iodine off on top of the leak, which is what pushes an otherwise adequate diet
   * under the reference range during a long fever.
   */
  private final val IODINE_DEPLETION_TICKS = 3 * 24000
  private final val IODINE_DRAIN_FRACTION =
    (MineralRanges.IODINE.normal - MineralRanges.IODINE.min) /
      IODINE_DEPLETION_TICKS / MineralRanges.IODINE.normal

  // ------------------------------------------------------------------ temperature

  /**
   * Fever per point of prostaglandin above the resting level. PGE2 in the hypothalamus is what
   * actually raises the set point, which is why salicin - a COX inhibitor - is an antipyretic here
   * and why a cytokine storm produces a high fever.
   */
  private final val FEVER_PER_PROSTAGLANDIN = 0.05f

  /** No infection can push the set point further than this on its own. */
  private final val FEVER_MAX = 4.0f

  /** 1/0.0004 = 2500 ticks, a little over two in-game minutes, to close most of the gap. */
  private final val TEMPERATURE_APPROACH = 0.0004f

  /**
   * How close to its set point the body has to get before an injected pyrogen starts to clear.
   *
   * An injected pyrogen *is* the set point, so if it cleared from the moment it was injected the set
   * point would fall away underneath a body that was still climbing towards it and the fever would
   * peak far short of the temperature asked for - asking for 39.5 used to peak at 38.8.
   */
  private final val PYROGEN_SETTLED = 0.2f

  /**
   * How much of the environment's pull the body cannot compensate for. Thermoregulation is good but
   * not perfect: a player in freezing water eventually loses the fight.
   */
  private final val AMBIENT_COUPLING = 0.6f

  /** The thyroid sets the metabolic rate, so iodine moves the set point in either direction. */
  private final val THYROID_SHIFT_PER_RELATIVE = 2f
  private final val THYROID_SHIFT_MAX = 0.8f

  // ------------------------------------------------------------------ derived thresholds

  /**
   * How well the current inflammation level actually fights pathogens, in `0..1`.
   *
   * A bell curve: the immune system works best inside the safe band. Below the immunosuppression
   * threshold it gives up entirely, and during a storm it is too dysregulated to be useful, so both
   * extremes let the infection grow.
   */
  def immuneCompetence(inflammation: Float): Float = {
    val delta =
      if (inflammation < 20f) inflammation - 20f
      else if (inflammation <= 40f) 0f
      else inflammation - 40f
    val sigma = if (inflammation < 20f) 10f else 15f
    val bell = Math.exp(-(delta * delta) / (2f * sigma * sigma)).toFloat
    val lowEnd = clamp(inflammation / ModelConstants.IMMUNOSUPPRESSION_THRESHOLD, 0f, 1f)
    bell * lowEnd
  }

  /** Drug concentration as a multiple of its effective concentration, capped. */
  def suppression(concentration: Float, effective: Float): Float =
    clamp(concentration / effective, 0f, DRUG_MAX_SUPPRESSION)

  def pathogenLoad(bacteria: Float, virus: Float): Float = bacteria + virus

  /** True once the load is high enough to actually make the player feel ill. */
  def isSymptomatic(load: Float): Boolean = load >= ModelConstants.SYMPTOM_THRESHOLD

  def isImmuneStorm(inflammation: Float): Boolean = inflammation >= ModelConstants.IMMUNE_STORM_THRESHOLD

  def isImmunosuppressed(inflammation: Float): Boolean =
    inflammation <= ModelConstants.IMMUNOSUPPRESSION_THRESHOLD

  /**
   * True once the infection is severe enough to hurt the host directly - sepsis, in the sense the
   * model uses. This is the only symptom in the mod that can kill without a monster being involved,
   * and it is reached through the infection, never through the immune system on its own.
   */
  def isSevereInfection(load: Float): Boolean = load >= ModelConstants.SEVERE_LOAD

  /** 0..1 severity used to scale symptoms; saturates at the severe load. */
  def severity(load: Float): Float = clamp(load / ModelConstants.SEVERE_LOAD, 0f, 1f)

  def isOverhydrated(water: Float): Boolean = water > ModelConstants.WATER_NORMAL

  def isDehydrated(water: Float): Boolean = water < ModelConstants.WATER_LOW

  /** How many of the ten thirst cells are full. */
  def thirstCells(water: Float): Int = clamp(water / 10f, 0f, ModelConstants.THIRST_CELLS.toFloat).toInt

  /**
   * How far outside the comfortable band the core temperature is, as a signed tier:
   *
   *  - `2` at or above 40.0 - super-high fever, which also blurs the screen;
   *  - `1` from 38.5 - fever, with the flushing and distortion at the edges;
   *  - `0` between 36.0 and 38.5 - comfortable;
   *  - `-1` at or below 36.0, `-2` at or below 35.0 - hypothermia.
   *
   * The fever side deliberately starts at 38.5 and not 38.0: 38 is what a hot biome, a fire or a
   * thyroid that runs hot produces on its own, and the screen effects it switched on were
   * indistinguishable from a bug.
   */
  def thermalTier(temperature: Float): Int =
    if (temperature >= ModelConstants.FEVER_SEVERE) 2
    else if (temperature >= ModelConstants.FEVER_MILD) 1
    else if (temperature <= ModelConstants.COLD_SEVERE) -2
    else if (temperature <= ModelConstants.COLD_MILD) -1
    else 0

  def isFebrile(temperature: Float): Boolean = temperature >= ModelConstants.FEVER_MILD

  def isHypothermic(temperature: Float): Boolean = temperature <= ModelConstants.COLD_MILD

  def hasThermalStress(temperature: Float): Boolean = thermalTier(temperature) != 0

  // ------------------------------------------------------------------ anticholinergics

  /** The two tropane alkaloids a mandrake carries, as one number. */
  def anticholinergicLoad(state: ModelState): Float = state.scopolamine + state.atropine

  /**
   * The temperature an anticholinergic overdose is driving the body towards.
   *
   * Atropine stops the body sweating - that is what it is given to a patient for - so past 1.5
   * combined the core temperature climbs, in three steps: 38, 39.5 and 41 degrees. It is a set point
   * shift like the thyroid's, not a fever: nothing here goes through prostaglandin, so salicin does
   * nothing about it.
   *
   * Deliberately independent of the infection fever, so the two add up: a mandrake eaten while ill
   * is worse than either on its own.
   */
  def anticholinergicFever(state: ModelState): Float = {
    val load = anticholinergicLoad(state)
    val target =
      if (load < ModelConstants.ANTICHOLINERGIC_FEVER_THRESHOLD) return 0f
      else if (load < ModelConstants.ANTICHOLINERGIC_FEVER_STEP) ModelConstants.ANTICHOLINERGIC_FEVER_MILD
      else if (load < ModelConstants.ANTICHOLINERGIC_FEVER_MAX) ModelConstants.ANTICHOLINERGIC_FEVER_SEVERE
      else ModelConstants.ANTICHOLINERGIC_FEVER_EXTREME
    target - ModelConstants.TEMPERATURE_NORMAL
  }

  /**
   * True when the alkaloids have blurred the player's sight.
   *
   * Either one on its own past 2.3, or the two together past 2.7 - which is the same kind of
   * mydriasis that makes a patient given atropine unable to read. It stacks with everything else:
   * a fever's haze and this are separate effects and both can be on the screen at once.
   */
  def isVisionBlurred(state: ModelState): Boolean =
    state.scopolamine >= ModelConstants.ANTICHOLINERGIC_BLUR_SINGLE ||
      state.atropine >= ModelConstants.ANTICHOLINERGIC_BLUR_SINGLE ||
      anticholinergicLoad(state) >= ModelConstants.ANTICHOLINERGIC_BLUR_TOTAL

  // ------------------------------------------------------------------ psilocybin

  /**
   * How far into the trip the player is, as one of the four stages the client has an effect for.
   *
   * `1` pulls coloured outlines out of every block edge, `2` starts dyeing the blocks themselves in
   * random bright colours, `3` bends the whole screen and `4` bends it hard. The stages are nested:
   * a stage 3 trip still has the outlines and the colour, because the client effect for it is the
   * whole thing at a higher intensity rather than a layer on top of the one below.
   */
  def psilocinTier(state: ModelState): Int =
    if (state.psilocin > ModelConstants.PSILOCIN_STORM) 4
    else if (state.psilocin > ModelConstants.PSILOCIN_WARP) 3
    else if (state.psilocin > ModelConstants.PSILOCIN_COLOUR) 2
    else if (state.psilocin > ModelConstants.PSILOCIN_OUTLINE) 1
    else 0

  /**
   * The temperature a heavy trip drives the body towards: nothing at all until the world is already
   * in pieces, then 39 degrees, then the same 41 the mandrake alkaloids reach at their worst.
   *
   * Like the alkaloid fever this is a set point shift rather than a fever - nothing here touches
   * prostaglandin, so salicin does not touch it either - and it stacks with whatever else is going
   * on, including the mandrake's own.
   */
  def psilocinFever(state: ModelState): Float =
    if (state.psilocin <= ModelConstants.PSILOCIN_STORM) 0f
    else if (state.psilocin < ModelConstants.PSILOCIN_FEVER_STEP) {
      ModelConstants.PSILOCIN_FEVER_MILD - ModelConstants.TEMPERATURE_NORMAL
    } else {
      ModelConstants.PSILOCIN_FEVER_EXTREME - ModelConstants.TEMPERATURE_NORMAL
    }

  // ------------------------------------------------------------------ the environment

  /** Vanilla's idea of a temperate biome; the neutral point of the ambient scale. */
  private final val TEMPERATE_BIOME = 0.8f

  /** Degrees of pull per unit of vanilla biome temperature, before thermoregulation. */
  private final val BIOME_PULL = 2.0f

  /**
   * The thermoneutral zone: this much pull costs the body nothing at all, which is what keeps an
   * ordinary walk through a taiga from being a medical event.
   */
  private final val THERMONEUTRAL_BAND = 2.0f

  private final val WET_PULL = 2.5f
  private final val POWDER_SNOW_PULL = 4.0f
  private final val FIRE_PULL = 4.0f
  private final val LAVA_PULL = 6.0f

  /**
   * The temperature the surroundings are dragging the player towards, on the body's own Celsius
   * scale, before thermoregulation.
   *
   * The world is almost never hot enough to overwhelm a healthy body - a desert is 2.4 degrees above
   * temperate, well inside the thermoneutral band - so the heat side of the model is mostly driven by
   * fever. The cold side is where the environment bites.
   */
  def environmentTemperature(
      biomeTemperature: Float,
      wet: Boolean,
      powderSnow: Boolean,
      lava: Boolean,
      fire: Boolean,
  ): Float = {
    var pull = (biomeTemperature - TEMPERATE_BIOME) * BIOME_PULL
    if (wet) pull -= WET_PULL
    if (powderSnow) pull -= POWDER_SNOW_PULL
    if (lava) pull += LAVA_PULL
    else if (fire) pull += FIRE_PULL

    // Shrug off the thermoneutral zone, and only then let the body's defences matter.
    val effective =
      if (pull > THERMONEUTRAL_BAND) pull - THERMONEUTRAL_BAND
      else if (pull < -THERMONEUTRAL_BAND) pull + THERMONEUTRAL_BAND
      else 0f

    clamp(ModelConstants.TEMPERATURE_NORMAL + effective, ModelConstants.TEMPERATURE_MIN, ModelConstants.TEMPERATURE_MAX)
  }

  // ------------------------------------------------------------------ symptom magnitudes

  /**
   * Magic damage a severe infection does in one two-second pass, and 0 while it is not severe.
   *
   * One point at the severe-load threshold and half a point more for every 20 points past it: a load
   * of 60 costs half a heart per ten seconds, a load of 100 a whole heart. Magic, so armour is no
   * help - this is the pathogen, not a blow.
   */
  def sepsisDamage(load: Float): Float =
    if (!isSevereInfection(load)) 0f
    else 1f + (load - ModelConstants.SEVERE_LOAD) / 40f

  /** No state, however bad, shakes the camera more often than this. */
  private final val MAX_SHAKE_CHANCE = 0.85f
  private final val SHAKE_CHANCE = 0.2f
  private final val STORM_SHAKE_CHANCE_MULTIPLIER = 2f
  private final val THERMAL_SHAKE_CHANCE = 0.15f

  /**
   * Chance that one shake roll actually shakes the camera, `0..MAX_SHAKE_CHANCE`.
   *
   * Every term has to come from something the player can notice, and every source that can produce a
   * tremor also puts an effect icon on the screen - otherwise the player has no way of telling why
   * their view is moving. That is why over-hydration only counts once it is past the point where the
   * nausea icon appears, and why 38 degrees does not count at all.
   */
  def shakeChance(state: ModelState): Float = {
    val load = pathogenLoad(state.bacteria, state.virus)
    val tier = thermalTier(state.temperature)
    var chance = if (isSymptomatic(load)) SHAKE_CHANCE else 0f
    if (isImmuneStorm(state.mediators.getInflammation)) chance *= STORM_SHAKE_CHANCE_MULTIPLIER
    if (state.water > ModelConstants.WATER_VISIBLY_OVERHYDRATED) chance += 0.1f
    if (state.electrolytes.magnesium < MineralRanges.MAGNESIUM.safeLow) chance += 0.15f
    if (state.electrolytes.calcium < MineralRanges.CALCIUM.severeLow) chance += 0.2f
    // Palpitations are a classic thyrotoxic symptom.
    if (state.traceElements.iodine > MineralRanges.IODINE.severeHigh) chance += 0.1f
    // Teeth chattering, or the rigors of a high fever.
    chance += THERMAL_SHAKE_CHANCE * Math.abs(tier)
    if (chance > MAX_SHAKE_CHANCE) MAX_SHAKE_CHANCE else chance
  }

  /** Extra food exhaustion while the body is unwell; a multiplier, so 1 is "nothing wrong". */
  def exhaustionMultiplier(state: ModelState): Float = {
    val load = pathogenLoad(state.bacteria, state.virus)
    var multiplier = 1f
    if (isSymptomatic(load)) multiplier += 0.5f * severity(load)
    if (isImmuneStorm(state.mediators.getInflammation)) multiplier += 0.5f
    if (isOverhydrated(state.water)) multiplier += 0.2f
    // Hyperchloraemia causes a mild metabolic acidosis with nausea and poor appetite.
    if (state.electrolytes.chloride > MineralRanges.CHLORIDE.safeHigh) multiplier += 0.2f
    if (state.electrolytes.magnesium < MineralRanges.MAGNESIUM.safeLow) multiplier += 0.15f
    // Shivering and a raised metabolic rate both cost calories.
    if (hasThermalStress(state.temperature)) multiplier += 0.25f
    multiplier
  }

  /** Extra food exhaustion from a shiver or a fever, per tier of thermal stress. */
  def thermalExhaustion(tier: Int): Float = 0.03f * Math.abs(tier)

  /** Extra food exhaustion from an immune storm, which burns energy fighting itself. */
  def stormExhaustion: Float = 0.05f

  private final val MINING_PENALTY_PER_SEVERITY = 0.06f
  private final val MINING_PENALTY_OVERHYDRATED = 0.08f

  /** How much slower this body mines, as a positive fraction; 0 when nothing is wrong. */
  def miningPenalty(state: ModelState): Float = {
    val load = pathogenLoad(state.bacteria, state.virus)
    var penalty = 0f
    if (isSymptomatic(load)) penalty += MINING_PENALTY_PER_SEVERITY * severity(load)
    if (isOverhydrated(state.water)) penalty += MINING_PENALTY_OVERHYDRATED
    // Hypokalaemia and hypocalcaemia both cause muscular weakness.
    if (state.electrolytes.potassium < MineralRanges.POTASSIUM.safeLow) penalty += 0.06f
    if (state.electrolytes.calcium < MineralRanges.CALCIUM.safeLow) penalty += 0.04f
    penalty
  }

  // ------------------------------------------------------------------ glucose and insulin

  /** What the index sits at on the boundary between the two ramps, so they meet without a step. */
  private final val ELEVATED_INSULIN_TARGET: Float =
    ModelConstants.INSULIN_NORMAL +
      (ModelConstants.GLUCOSE_ELEVATED - ModelConstants.GLUCOSE_NORMAL) * ModelConstants.INSULIN_PER_GLUCOSE

  /**
   * The insulin index the current glucose asks for.
   *
   * Two ramps, both starting at [ModelConstants.GLUCOSE_NORMAL]: a gentle one across the normal
   * band, which is what actually disposes of an ordinary meal, and a twice-as-steep one from
   * [ModelConstants.GLUCOSE_ELEVATED] upwards. So the higher the sugar the more insulin, and past 8
   * the pancreas is unmistakably responding.
   */
  private def insulinTarget(glucose: Float): Float = {
    if (glucose <= ModelConstants.GLUCOSE_NORMAL) {
      ModelConstants.INSULIN_NORMAL
    } else if (glucose <= ModelConstants.GLUCOSE_ELEVATED) {
      ModelConstants.INSULIN_NORMAL +
        (glucose - ModelConstants.GLUCOSE_NORMAL) * ModelConstants.INSULIN_PER_GLUCOSE
    } else {
      ELEVATED_INSULIN_TARGET +
        (glucose - ModelConstants.GLUCOSE_ELEVATED) * ModelConstants.INSULIN_PER_GLUCOSE_ELEVATED
    }
  }

  /** Moves the body's own insulin one tick towards what the current glucose asks for. */
  private def stepInsulin(state: ModelState): Float =
    clamp(
      state.insulin + (insulinTarget(state.glucose) - state.insulin) * ModelConstants.INSULIN_APPROACH,
      0f,
      ModelConstants.INSULIN_CAP,
    )

  /**
   * Moves blood glucose one tick, and everything that moves it is here.
   *
   * Three terms, and they are separate because they behave differently:
   *
   *  - the **basal drain**, a flat 1.5 mmol/L over two in-game days, which is the fasting path from
   *    5.0 down to 3.5. Past 3.5 it scales with what is left, so the fall slows down instead of
   *    running straight to zero;
   *  - the body's **own insulin**, which disposes of a glucose load and is switched off at the
   *    bottom of the reference range - the pancreas does not drive a body hypoglycaemic on its own;
   *  - **injected insulin aspart**, which has no such brake, which is exactly why an overdose is
   *    dangerous.
   */
  private def stepGlucose(state: ModelState, insulin: Float, insulinAspart: Float): Float = {
    val glucose = state.glucose

    val endogenousDrive =
      ModelConstants.GLUCOSE_UPTAKE_PER_INSULIN * Math.max(0f, insulin - ModelConstants.INSULIN_NORMAL)
    val endogenous =
      if (glucose <= ModelConstants.GLUCOSE_SAFE_LOW) 0f
      else Math.min(endogenousDrive, glucose - ModelConstants.GLUCOSE_SAFE_LOW)

    val injected = ModelConstants.GLUCOSE_UPTAKE_PER_INSULIN_ASPART * insulinAspart

    val basal =
      if (glucose > ModelConstants.GLUCOSE_FASTING_FLOOR) ModelConstants.GLUCOSE_BASAL_DECAY_PER_TICK
      else ModelConstants.GLUCOSE_BASAL_DECAY_PER_TICK * (glucose / ModelConstants.GLUCOSE_FASTING_FLOOR)

    clamp(
      glucose - basal - endogenous - injected,
      ModelConstants.GLUCOSE_MIN,
      ModelConstants.GLUCOSE_MAX,
    )
  }

  /**
   * How far into a hypoglycaemic crash the blood glucose is, as a tier:
   *
   * | tier | glucose | what the player gets |
   * | --- | --- | --- |
   * | 0 | ≥ 2.0 | nothing |
   * | 1 | < 2.0 | mining fatigue |
   * | 2 | < 1.7 | and weakness |
   * | 3 | < 1.3 | and magic damage, which is the one that can kill |
   */
  def hypoglycemiaTier(glucose: Float): Int =
    if (glucose < ModelConstants.GLUCOSE_HYPO_DAMAGE) 3
    else if (glucose < ModelConstants.GLUCOSE_HYPO_WEAKNESS) 2
    else if (glucose < ModelConstants.GLUCOSE_HYPO_FATIGUE) 1
    else 0

  /**
   * Magic damage a hypoglycaemic crash does in one two-second pass, and 0 above the damage
   * threshold. It scales with how far below the threshold the glucose is, so the last of it hurts
   * most - the brain has no other fuel.
   */
  def hypoglycemiaDamage(glucose: Float): Float =
    if (glucose >= ModelConstants.GLUCOSE_HYPO_DAMAGE) 0f
    else (ModelConstants.GLUCOSE_HYPO_DAMAGE - glucose) * ModelConstants.HYPOGLYCEMIA_DAMAGE_PER_MMOL

  /**
   * The blood glucose as the meter prints it: one decimal place, the way a real one reads.
   *
   * The format is the model's for the same reason [ModelMineral.display] is: it is a property of the
   * quantity, not of whatever happens to be drawing it.
   */
  def glucoseReading(glucose: Float): String =
    String.format("%.1f", java.lang.Float.valueOf(glucose))

  // ------------------------------------------------------------------ the tick

  /**
   * The calibration points of the CYP3A4 inhibition curve, as (naringin, activity) pairs in ascending
   * naringin order: a clean body first, then the four knots [ModelConstants] carries.
   */
  private final val CYP3A4_KNOTS: Array[(Float, Float)] = Array(
    (0f, ModelConstants.CYP3A4_NORMAL),
    (ModelConstants.NARINGIN_CYP_KNOT_1, ModelConstants.CYP3A4_AT_KNOT_1),
    (ModelConstants.NARINGIN_CYP_KNOT_2, ModelConstants.CYP3A4_AT_KNOT_2),
    (ModelConstants.NARINGIN_CYP_KNOT_3, ModelConstants.CYP3A4_AT_KNOT_3),
    (ModelConstants.NARINGIN_CYP_KNOT_4, ModelConstants.CYP3A4_AT_KNOT_4),
  )

  /**
   * How active the liver's CYP3A4 is at a given naringin load, on the 0..100 scale the index is
   * reported in.
   *
   * A curve, not a step. The activity is exactly each calibrated value at the naringin it was
   * calibrated for and slides from one to the next in between, so one more slice always takes a
   * little more off the liver instead of everything changing the moment a threshold is crossed.
   *
   * The slide is a smoothstep - `3t^2 - 2t^3`, whose value and slope are both 0 at `t = 0` and both
   * settled at `t = 1` - so each knot is entered and left flat and the curve has no kink where two
   * segments meet either. Past the last knot it holds, which is what makes eight slices the deepest
   * the inhibition goes.
   *
   * Nothing else in the body moves it, and everything the enzyme does downstream - today that is
   * only the berberine metabolism - is scaled by how far below [ModelConstants.CYP3A4_NORMAL] it
   * sits.
   */
  def cyp3a4For(naringin: Float): Float = {
    val load = Math.max(naringin, 0f)
    var segment = 0
    while (segment < CYP3A4_KNOTS.length - 1 && load > CYP3A4_KNOTS(segment + 1)._1) segment += 1
    if (segment >= CYP3A4_KNOTS.length - 1) {
      CYP3A4_KNOTS(CYP3A4_KNOTS.length - 1)._2
    } else {
      val (fromNaringin, fromActivity) = CYP3A4_KNOTS(segment)
      val (toNaringin, toActivity) = CYP3A4_KNOTS(segment + 1)
      val span = toNaringin - fromNaringin
      val t = if (span > 0f) clamp((load - fromNaringin) / span, 0f, 1f) else 1f
      fromActivity + (toActivity - fromActivity) * (t * t * (3f - 2f * t))
    }
  }

  /** Metabolises and decays all pharmacological compounds carried in the body by one tick. */
  def stepDrugs(drugs: ModelDrugs): ModelDrugs = {
    val salicin = Math.max(drugs.salicin - ModelConstants.SALICIN_DECAY_PER_TICK, 0f)
    val dexamethasone = Math.max(drugs.dexamethasone - ModelConstants.DEXAMETHASONE_DECAY_PER_TICK, 0f)
    val scopolamine = Math.max(drugs.scopolamine - ModelConstants.ANTICHOLINERGIC_DECAY_PER_TICK, 0f)
    val atropine = Math.max(drugs.atropine - ModelConstants.ANTICHOLINERGIC_DECAY_PER_TICK, 0f)

    val converted = Math.min(drugs.psilocybin, ModelConstants.PSILOCYBIN_DECAY_PER_TICK)
    val psilocybin = drugs.psilocybin - converted
    val psilocin = clamp(
      drugs.psilocin + converted - ModelConstants.PSILOCIN_DECAY_PER_TICK,
      0f,
      ModelConstants.PSILOCIN_CAP,
    )
    val ephedrine = Math.max(drugs.ephedrine - ModelConstants.EPHEDRINE_DECAY_PER_TICK, 0f)

    // Naringin decays first, and the enzyme index is then read off what is left: a tick is the
    // smallest step the model has, so "the liver clears berberine at the rate this much grapefruit
    // allows" is decided by the naringin the body actually still carries at the end of the tick.
    val naringin = Math.max(drugs.naringin - ModelConstants.NARINGIN_DECAY_PER_TICK, 0f)
    val enzymes = new ModelEnzymes(cyp3a4For(naringin))

    // Berberine is cleared by CYP3A4 and by nothing else, so the rate the constant was written at is
    // scaled by how fast the liver is currently running. At the 85 baseline the fraction is exactly
    // 1 and the metabolism is the one the model has always had; at 10 it takes eight and a half
    // times as long. The division lives in `ModelEnzymes.cyp3a4Fraction`, not here.
    val berberine = Math.max(
      drugs.berberine - ModelConstants.BERBERINE_DECAY_PER_TICK * enzymes.cyp3a4Fraction,
      0f,
    )
    val glycyrrhizin = Math.max(drugs.glycyrrhizin - ModelConstants.GLYCYRRHIZIN_DECAY_PER_TICK, 0f)
    val ethanol = Math.max(drugs.ethanol - ModelConstants.ETHANOL_DECAY_PER_TICK, 0f)
    val insulinAspart = Math.max(drugs.insulinAspart - ModelConstants.INSULIN_ASPART_DECAY_PER_TICK, 0f)

    new ModelDrugs(
      salicin,
      dexamethasone,
      scopolamine,
      atropine,
      psilocybin,
      psilocin,
      ephedrine,
      berberine,
      glycyrrhizin,
      naringin,
      ethanol,
      insulinAspart,
    )
  }

  /**
   * Advances a player's physiology by a single tick.
   *
   * `ambient` is the temperature the environment is trying to drag the body towards, on the same
   * Celsius scale as the body itself; the normal temperature means "indoors, no wind, nothing to
   * fight". It defaults to neutral so the model can be driven from a test without a world.
   */
  def tick(state: ModelState): ModelState = tick(state, ModelConstants.TEMPERATURE_NORMAL)

  def tick(state: ModelState, ambient: Float): ModelState = {
    // 1. Drugs and injected pyrogen are metabolised first so the rest of the tick sees the current
    //    concentrations. A negative pyrogen is an antipyretic offset and clears the same way.
    val drugs = stepDrugs(state.drugs)
    // The liver's enzyme indices are read off the compounds the tick has just left behind, so the
    // state and the metabolism that used them agree by construction rather than by two call sites
    // happening to ask the same question.
    val enzymes = new ModelEnzymes(cyp3a4For(drugs.naringin))
    val pyrogen = stepPyrogen(state, ambient)

    // 1b. Glucose and insulin, which the drugs above feed into: injected insulin aspart is what
    //     makes the injected term of the uptake possible.
    val insulin = stepInsulin(state)
    val glucose = stepGlucose(state, insulin, drugs.insulinAspart)

    // 2. Pathogens grow logistically and are cleared in proportion to immune competence and targeted drugs.
    val competence = immuneCompetence(state.mediators.getInflammation)
    val bacteria = stepBacteria(state.bacteria, competence, state.immuneActive, state.drugs.berberine)
    val virus = stepVirus(state.virus, competence, state.immuneActive, state.drugs.glycyrrhizin)

    val currentLoad = pathogenLoad(bacteria, virus)
    val nextImmuneActive = if (state.immuneActive) {
      currentLoad > 0.0001f
    } else {
      currentLoad > ModelConstants.IMMUNITY_ACTIVATION_LOAD
    }

    val next = state
      .withDrugs(drugs)
      .withPyrogen(pyrogen)
      .withBacteria(bacteria)
      .withVirus(virus)
      .withImmuneActive(nextImmuneActive)
      .withGlucose(glucose)
      .withInsulin(insulin)
      .withCyp3a4(enzymes.cyp3a4)

    // 3. The immune response, mediator by mediator.
    val inflamed = next.withMediators(stepMediators(next))

    // 4. Temperature follows the mediators and the environment. Everything the body *does* about a
    //    temperature - sweating water and salt away - is driven by the temperature it started the
    //    tick at, so each step reads the previous state and the new temperature is written once.
    val temperature = stepTemperature(inflamed, ambient)

    val hydrated = inflamed.withWater(stepWater(inflamed))

    hydrated
      .withTemperature(temperature)
      .withElectrolytes(stepElectrolytes(hydrated))
      .withTraceElements(stepTraceElements(hydrated))
  }

  // ------------------------------------------------------------------ mediators

  private def stepMediators(state: ModelState): ModelMediators = {
    val load = pathogenLoad(state.bacteria, state.virus)
    val stimulus = if (!state.immuneActive && load <= ModelConstants.IMMUNITY_ACTIVATION_LOAD) {
      0f
    } else if (load <= ModelConstants.IMMUNE_STRESS_LOAD) {
      if (state.immuneActive && load <= ModelConstants.IMMUNITY_ACTIVATION_LOAD) {
        (load / ModelConstants.IMMUNITY_ACTIVATION_LOAD) * 0.18f
      } else {
        clamp((load - ModelConstants.IMMUNITY_ACTIVATION_LOAD) / (ModelConstants.IMMUNE_STRESS_LOAD - ModelConstants.IMMUNITY_ACTIVATION_LOAD), 0f, 1f)
      }
    } else {
      val stressRatio = (load - ModelConstants.IMMUNE_STRESS_LOAD) / (ModelConstants.MAX_PATHOGEN - ModelConstants.IMMUNE_STRESS_LOAD)
      1.0f + 2.5f * clamp(stressRatio, 0f, 1f)
    }

    val salicin = suppression(state.salicin, ModelConstants.SALICIN_EFFECTIVE)
    val dex = suppression(state.dexamethasone, ModelConstants.DEXAMETHASONE_EFFECTIVE)
    val salFight = Math.min(salicin, 1f)
    val dexFight = Math.min(dex, 1f)

    // A combination of drugs damps the response more than either alone.
    val fight = Math.max(salFight, dexFight)
    val damping = Math.max(1f - DRUG_RESPONSE_DAMPING * fight, 0.05f)

    // Anything above the effective concentration also suppresses the resting level.
    val overdose = Math.max(salicin - 1f, 0f) + Math.max(dex - 1f, 0f)
    val baselineScale = Math.max(1f - overdose * OVERDOSE_BASELINE_DROP, 0f)

    val mediators = state.mediators
    val cytokine = approach(
      mediators.cytokine,
      CYTOKINE_BASE * baselineScale + CYTOKINE_RESPONSE * stimulus * damping * (1f - DEX_CYTOKINE_EXTRA * dexFight),
    )
    val histamine = approach(
      mediators.histamine,
      HISTAMINE_BASE * baselineScale + HISTAMINE_RESPONSE * stimulus * damping,
    )
    val bradykinin = approach(
      mediators.bradykinin,
      BRADYKININ_BASE * baselineScale + BRADYKININ_RESPONSE * stimulus * damping,
    )
    val prostaglandin = approach(
      mediators.prostaglandin,
      PROSTAGLANDIN_BASE * baselineScale +
        (cytokine * PROSTAGLANDIN_FROM_CYTOKINE + PROSTAGLANDIN_RESPONSE * stimulus) *
        damping * (1f - SALICIN_PROSTAGLANDIN_EXTRA * salFight),
    )
    val leukotriene = approach(
      mediators.leukotriene,
      LEUKOTRIENE_BASE * baselineScale +
        (cytokine * LEUKOTRIENE_FROM_CYTOKINE + LEUKOTRIENE_RESPONSE * stimulus) *
        damping * (1f - DEX_LEUKOTRIENE_EXTRA * dexFight),
    )

    new ModelMediators(histamine, prostaglandin, leukotriene, cytokine, bradykinin)
  }

  private def approach(current: Float, target: Float): Float =
    clamp(current + (target - current) * MEDIATOR_APPROACH, 0f, MediatorLevels.MAX)

  // ------------------------------------------------------------------ pathogens

  private def stepBacteria(load: Float, competence: Float, immuneActive: Boolean, berberine: Float): Float =
    stepTargetedPathogen(
      load,
      competence,
      immuneActive,
      berberine,
      ModelConstants.BERBERINE_SLOW_THRESHOLD,
      ModelConstants.BERBERINE_SUPPRESS_THRESHOLD,
    )

  private def stepVirus(load: Float, competence: Float, immuneActive: Boolean, glycyrrhizin: Float): Float =
    stepTargetedPathogen(
      load,
      competence,
      immuneActive,
      glycyrrhizin,
      ModelConstants.GLYCYRRHIZIN_SLOW_THRESHOLD,
      ModelConstants.GLYCYRRHIZIN_SUPPRESS_THRESHOLD,
    )

  private def stepTargetedPathogen(
      load: Float,
      competence: Float,
      immuneActive: Boolean,
      drugConc: Float,
      slowThreshold: Float,
      suppressThreshold: Float,
  ): Float = {
    if (load <= 0f) {
      0f
    } else {
      val baseGrowth = GROWTH_RATE * load * (1f - load / ModelConstants.MAX_PATHOGEN)
      val immuneClearance = if (immuneActive || load > ModelConstants.IMMUNITY_ACTIVATION_LOAD) {
        baseGrowth * competence + CLEARANCE_BASE_RATE * competence
      } else {
        0f
      }

      if (drugConc >= suppressThreshold) {
        // Complete suppression of pathogen replication; clearance rate scales with drug concentration.
        val drugClearance = DRUG_SUPPRESS_CLEARANCE_RATE * (drugConc / suppressThreshold)
        clamp(load - immuneClearance - drugClearance, 0f, ModelConstants.MAX_PATHOGEN)
      } else if (drugConc > slowThreshold) {
        // Reduced pathogen replication rate allowing the immune system to overpower it.
        val slowRatio = clamp((drugConc - slowThreshold) / (suppressThreshold - slowThreshold), 0f, 1f)
        val reducedGrowth = baseGrowth * (1f - 0.75f * slowRatio)
        clamp(load + reducedGrowth - immuneClearance, 0f, ModelConstants.MAX_PATHOGEN)
      } else {
        clamp(load + baseGrowth - immuneClearance, 0f, ModelConstants.MAX_PATHOGEN)
      }
    }
  }

  private def stepPathogen(load: Float, competence: Float, immuneActive: Boolean): Float = {
    if (load <= 0f) {
      0f
    } else {
      val growth = GROWTH_RATE * load * (1f - load / ModelConstants.MAX_PATHOGEN)
      val clearance = if (immuneActive || load > ModelConstants.IMMUNITY_ACTIVATION_LOAD) {
        growth * competence + CLEARANCE_BASE_RATE * competence
      } else {
        0f
      }
      clamp(load + growth - clearance, 0f, ModelConstants.MAX_PATHOGEN)
    }
  }

  // ------------------------------------------------------------------ temperature

  /**
   * The core temperature the body is currently aiming for.
   *
   * Four things move it, and they are all separate on purpose: prostaglandin (the fever of an
   * infection, and the term salicin takes away), the injected pyrogen, the thyroid, and the
   * environment - of which only `AMBIENT_COUPLING` gets past thermoregulation.
   */
  def targetTemperature(state: ModelState): Float = targetTemperature(state, ModelConstants.TEMPERATURE_NORMAL)

  def targetTemperature(state: ModelState, ambient: Float): Float = {
    val prostaglandin = Math.max(state.mediators.prostaglandin - MediatorLevels.RESTING.prostaglandin, 0f)
    val feverRaw = FEVER_PER_PROSTAGLANDIN * prostaglandin
    val load = pathogenLoad(state.bacteria, state.virus)
    val fever = if (load <= ModelConstants.IMMUNE_STRESS_LOAD) {
      Math.min(feverRaw, ModelConstants.FEVER_NORMAL_IMMUNE_MAX - ModelConstants.TEMPERATURE_NORMAL)
    } else {
      Math.min(feverRaw, FEVER_MAX)
    }
    val environmental = (ambient - ModelConstants.TEMPERATURE_NORMAL) * AMBIENT_COUPLING
    val target = ModelConstants.TEMPERATURE_NORMAL +
      fever + state.pyrogen + thyroidShift(state.traceElements.iodine) +
      anticholinergicFever(state) + psilocinFever(state) + environmental
    clamp(target, ModelConstants.TEMPERATURE_MIN, ModelConstants.TEMPERATURE_MAX)
  }

  private def stepTemperature(state: ModelState, ambient: Float): Float = {
    val target = targetTemperature(state, ambient)
    clamp(
      state.temperature + (target - state.temperature) * TEMPERATURE_APPROACH,
      ModelConstants.TEMPERATURE_MIN,
      ModelConstants.TEMPERATURE_MAX,
    )
  }

  /**
   * How much the thyroid moves the set point, in degrees. 0 while iodine is inside its reference
   * range. Measured relative to normal rather than in umol/L, because the same shift has to come out
   * of a number like 0.5.
   */
  private def thyroidShift(iodine: Float): Float = {
    val mineral = MineralRanges.IODINE
    val delta =
      if (iodine < mineral.safeLow) (iodine - mineral.safeLow) / mineral.normal
      else if (iodine > mineral.safeHigh) (iodine - mineral.safeHigh) / mineral.normal
      else 0f
    clamp(delta * THYROID_SHIFT_PER_RELATIVE, -THYROID_SHIFT_MAX, THYROID_SHIFT_MAX)
  }

  /** Degrees of core temperature above normal; the drive behind sweating. 0 when not hot. */
  private def heatStress(state: ModelState): Float =
    Math.max(state.temperature - ModelConstants.TEMPERATURE_NORMAL, 0f)

  /**
   * Clears an injected pyrogen, one step per tick - but only once the fever it drives has actually
   * developed. See `PYROGEN_SETTLED` for why it waits. An infection never goes through here: its
   * fever comes from prostaglandin, so pyrogen is zero and this returns immediately.
   */
  private def stepPyrogen(state: ModelState, ambient: Float): Float = {
    val pyrogen = state.pyrogen
    if (pyrogen == 0f) {
      0f
    } else if (Math.abs(state.temperature - targetTemperature(state, ambient)) >= PYROGEN_SETTLED) {
      pyrogen
    } else if (pyrogen > 0f) {
      Math.max(pyrogen - ModelConstants.PYROGEN_DECAY_PER_TICK, 0f)
    } else {
      Math.min(pyrogen + ModelConstants.PYROGEN_DECAY_PER_TICK, 0f)
    }
  }

  // ------------------------------------------------------------------ water and minerals

  private def stepWater(state: ModelState): Float = {
    val excess = Math.max(state.water - ModelConstants.WATER_NORMAL, 0f)

    // The kidneys dump a water load: up to twice the base rate at the ceiling.
    var loss = ModelConstants.WATER_DECAY_PER_TICK * (1f + excess * OVERHYDRATION_DIURESIS)

    // Hypernatraemia and hypercalcaemia both cause thirst and increased urine output.
    if (state.electrolytes.sodium > MineralRanges.SODIUM.safeHigh) loss *= 1.3f
    if (state.electrolytes.calcium > MineralRanges.CALCIUM.safeHigh) loss *= 1.2f

    // Sweat water loss:
    // When deltaT > 1.25 C (core temperature above 38.25 C):
    //   sweatLoss = SWEAT_A * deltaT + SWEAT_B * deltaT^2
    // At 39 C (deltaT = 2): total loss is exactly 1/840 (3.5 game days to drain 100 water).
    // At 40 C (deltaT = 3): total loss is exactly 1/480 (2.0 game days to drain 100 water).
    val deltaT = heatStress(state)
    if (deltaT > 1.25f) {
      loss += Math.max(SWEAT_A * deltaT + SWEAT_B * deltaT * deltaT, 0f)
    }

    clamp(state.water - loss, ModelConstants.WATER_MIN, ModelConstants.WATER_MAX)
  }

  /**
   * The water a full bladder is dumping, as a fraction of a mineral's normal concentration per tick.
   *
   * Everything here is unit-free on purpose: the same physiology has to work for sodium at 140 mmol/L
   * and iodine at 0.5 umol/L, so a loss is always expressed relative to normal.
   */
  private def mineralFlush(state: ModelState): Float = {
    val excess = Math.max(state.water - ModelConstants.WATER_NORMAL, 0f)
    val full = ModelConstants.WATER_MAX - ModelConstants.WATER_NORMAL
    clamp(excess / full, 0f, 1f) * OVERHYDRATION_FLUSH_FRACTION
  }

  /** Sweat: the same, driven by how far the core temperature is above normal. */
  private def mineralSweat(state: ModelState): Float = {
    val deltaT = heatStress(state)
    if (deltaT > 1.25f) deltaT * SWEAT_MINERAL_FRACTION_PER_DEGREE else 0f
  }

  private def stepMineral(value: Float, mineral: ModelMineral, loss: Float, excretion: Float): Float = {
    val homeostasis = (mineral.normal - value) * ELECTROLYTE_HOMEOSTASIS
    mineral.clamp(value + homeostasis - loss * excretion * mineral.normal)
  }

  private def stepElectrolytes(state: ModelState): ModelElectrolytes = {
    val loss = mineralFlush(state) + mineralSweat(state)
    val current = state.electrolytes
    new ModelElectrolytes(
      sodium = stepMineral(current.sodium, MineralRanges.SODIUM, loss, EXCRETION_SODIUM),
      potassium = stepMineral(current.potassium, MineralRanges.POTASSIUM, loss, EXCRETION_POTASSIUM),
      magnesium = stepMineral(current.magnesium, MineralRanges.MAGNESIUM, loss, EXCRETION_MAGNESIUM),
      chloride = stepMineral(current.chloride, MineralRanges.CHLORIDE, loss, EXCRETION_CHLORIDE),
      calcium = stepMineral(current.calcium, MineralRanges.CALCIUM, loss, EXCRETION_CALCIUM),
    )
  }

  /**
   * The store drains at a fixed rate with nothing adding to it, so a player who never eats kelp runs
   * it down in three in-game days and then sits at the floor. A fever, or a drinking binge, takes it
   * away faster.
   */
  private def stepTraceElements(state: ModelState): ModelTraceElements = {
    val iodineMineral = MineralRanges.IODINE
    val flush = mineralFlush(state) + mineralSweat(state)

    val iodineLoss = (IODINE_DRAIN_FRACTION + flush * EXCRETION_IODINE) * iodineMineral.normal
    val newIodine = iodineMineral.clamp(state.traceElements.iodine - iodineLoss)

    // Vitamin C excretion: first-order elimination (rate proportional to concentration).
    // Starting at safeHigh (80 umol/L), reaches safeLow (40 umol/L) in exactly 5 in-game days.
    val vitCMineral = MineralRanges.VITAMIN_C
    val vitCLoss = state.traceElements.vitaminC * ModelConstants.VITAMIN_C_DECAY_RATE
    val newVitaminC = vitCMineral.clamp(state.traceElements.vitaminC - vitCLoss)

    new ModelTraceElements(newIodine, newVitaminC)
  }

  // ------------------------------------------------------------------ external inputs

  /** Adds a pathogen seed, used by the infection sources. */
  def seed(state: ModelState): ModelState = state

  def seed(state: ModelState, bacteria: Float): ModelState = seed(state, bacteria, 0f)

  def seed(state: ModelState, bacteria: Float, virus: Float): ModelState =
    state
      .withBacteria(clamp(state.bacteria + bacteria, 0f, ModelConstants.MAX_PATHOGEN))
      .withVirus(clamp(state.virus + virus, 0f, ModelConstants.MAX_PATHOGEN))

  /** Adds salicin, capped. */
  def dose(state: ModelState, amount: Float): ModelState =
    state.withSalicin(clamp(state.salicin + amount, 0f, ModelConstants.SALICIN_CAP))

  /** Adds dexamethasone, capped. */
  def inject(state: ModelState, amount: Float): ModelState =
    state.withDexamethasone(clamp(state.dexamethasone + amount, 0f, ModelConstants.DEXAMETHASONE_CAP))

  /** Adds water from a drink. */
  def drink(state: ModelState, amount: Float): ModelState =
    state.withWater(clamp(state.water + amount, ModelConstants.WATER_MIN, ModelConstants.WATER_MAX))

  /** Adds salt, in mmol/L of serum sodium and chloride. */
  def salt(state: ModelState, sodium: Float, chloride: Float, magnesium: Float, calcium: Float): ModelState = {
    val e = state.electrolytes
    state.withElectrolytes(
      e
        .withSodium(MineralRanges.SODIUM.clamp(e.sodium + sodium))
        .withMagnesium(MineralRanges.MAGNESIUM.clamp(e.magnesium + magnesium))
        .withChloride(MineralRanges.CHLORIDE.clamp(e.chloride + chloride))
        .withCalcium(MineralRanges.CALCIUM.clamp(e.calcium + calcium)),
    )
  }

  /** Adds iodine, in umol/L, which the body only gets from food - kelp, in this mod. */
  def iodine(state: ModelState, amount: Float): ModelState =
    state.withTraceElements(
      state.traceElements.withIodine(MineralRanges.IODINE.clamp(state.traceElements.iodine + amount)),
    )

  /** Adds vitamin C (ascorbic acid), in umol/L, from plant foods (fruits, carrots, pumpkins, etc.). */
  def vitaminC(state: ModelState, amount: Float): ModelState =
    state.withTraceElements(
      state.traceElements.withVitaminC(MineralRanges.VITAMIN_C.clamp(state.traceElements.vitaminC + amount)),
    )

  /**
   * Adds the two tropane alkaloids a mandrake carries, in dose units, both capped.
   *
   * Scopolamine is the one that crosses into the brain and does the hallucinating; atropine is the
   * one that dries the body out and stops it sweating. A fruit (1.0 / 0.1) is a trip; the seeds
   * (0.75 / 0.1) are the same trip with less of the delirium, which is why nobody eats them.
   */
  def anticholinergic(state: ModelState, scopolamine: Float, atropine: Float): ModelState =
    state
      .withScopolamine(clamp(state.scopolamine + scopolamine, 0f, ModelConstants.ANTICHOLINERGIC_CAP))
      .withAtropine(clamp(state.atropine + atropine, 0f, ModelConstants.ANTICHOLINERGIC_CAP))

  /**
   * Adds what one raw gymnopilus carries, in dose units, both capped.
   *
   * A raw mushroom is a dose of each at once, which is why it comes on fast: the psilocin is there
   * immediately and the psilocybin behind it keeps topping it up for the next half a day. A cooked
   * one carries neither - heat destroys both compounds - so it is food and nothing more.
   */
  def mushroom(state: ModelState, psilocybin: Float, psilocin: Float): ModelState =
    state
      .withPsilocybin(clamp(state.psilocybin + psilocybin, 0f, ModelConstants.PSILOCYBIN_CAP))
      .withPsilocin(clamp(state.psilocin + psilocin, 0f, ModelConstants.PSILOCIN_CAP))

  /**
   * Adds ephedrine, the stimulant alkaloid, capped at [ModelConstants.EPHEDRINE_CAP].
   */
  def addEphedrine(state: ModelState, amount: Float): ModelState =
    state.withEphedrine(clamp(state.ephedrine + amount, 0f, ModelConstants.EPHEDRINE_CAP))

  def hasHasteFromEphedrine(state: ModelState): Boolean =
    state.ephedrine > ModelConstants.EPHEDRINE_HASTE_THRESHOLD

  /**
   * True while the stimulant load is high enough to keep the body from falling asleep.
   */
  def isTooStimulatedToSleep(state: ModelState): Boolean =
    state.ephedrine > ModelConstants.EPHEDRINE_SLEEP_BLOCK_THRESHOLD

  /** Adds berberine, capped at [ModelConstants.BERBERINE_CAP]. */
  def addBerberine(state: ModelState, amount: Float): ModelState =
    state.withBerberine(clamp(state.berberine + amount, 0f, ModelConstants.BERBERINE_CAP))

  /** Adds glycyrrhizin, capped at [ModelConstants.GLYCYRRHIZIN_CAP]. */
  def addGlycyrrhizin(state: ModelState, amount: Float): ModelState =
    state.withGlycyrrhizin(clamp(state.glycyrrhizin + amount, 0f, ModelConstants.GLYCYRRHIZIN_CAP))

  /**
   * Adds naringin from grapefruit, capped at [ModelConstants.NARINGIN_CAP].
   *
   * Eating it does not move [ModelState.cyp3a4] directly: the index is read off the naringin at the
   * start of the next tick, so the two can disagree for exactly one tick and never longer.
   */
  def addNaringin(state: ModelState, amount: Float): ModelState =
    state.withNaringin(clamp(state.naringin + amount, 0f, ModelConstants.NARINGIN_CAP))

  /** Adds ethanol, capped at [ModelConstants.ETHANOL_CAP]. */
  def addEthanol(state: ModelState, amount: Float): ModelState =
    state.withEthanol(clamp(state.ethanol + amount, 0f, ModelConstants.ETHANOL_CAP))

  /**
   * Adds what one serving of food does to blood glucose, in mmol/L, capped.
   *
   * The body cannot store a surplus, so this is the only thing that ever puts glucose back - which
   * makes eating a glucose *dose* the player has to think about, not just a hunger bar.
   */
  def addGlucose(state: ModelState, amount: Float): ModelState =
    state.withGlucose(
      clamp(state.glucose + amount, ModelConstants.GLUCOSE_MIN, ModelConstants.GLUCOSE_MAX),
    )

  /**
   * Adds insulin aspart, the injected fast-acting analogue, capped.
   *
   * Unlike the body's own insulin this is not switched off when glucose reaches the bottom of the
   * reference range, so a dose deep enough will take a player hypoglycaemic.
   */
  def injectInsulin(state: ModelState, amount: Float): ModelState =
    state.withInsulinAspart(
      clamp(state.insulinAspart + amount, 0f, ModelConstants.INSULIN_ASPART_CAP),
    )

  /**
   * Raises or lowers the fever so that the body *peaks* at `degrees` Celsius.
   *
   * This works out how much pyrogen is needed given whatever the infection is already doing, so the
   * answer is the requested temperature whether or not the player is ill. Something below normal
   * gives a negative offset, which is how hypothermia is tested. The extra settled tolerance pays for
   * the same tolerance `stepPyrogen` waits for, so the peak lands on the request exactly.
   */
  def induceFever(state: ModelState, degrees: Float): ModelState =
    induceFever(state, degrees, ModelConstants.TEMPERATURE_NORMAL)

  def induceFever(state: ModelState, degrees: Float, ambient: Float): ModelState = {
    val withoutPyrogen = targetTemperature(state.withPyrogen(0f), ambient)
    val naive = degrees - withoutPyrogen
    val needed = if (naive >= 0f) naive + PYROGEN_SETTLED else naive - PYROGEN_SETTLED
    state.withPyrogen(clamp(needed, -ModelConstants.PYROGEN_CAP, ModelConstants.PYROGEN_CAP))
  }

  // ------------------------------------------------------------------ helpers

  private def clamp(value: Float, min: Float, max: Float): Float =
    if (value < min) min else if (value > max) max else value
}

