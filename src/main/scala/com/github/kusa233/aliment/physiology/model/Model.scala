package com.github.kusa233.aliment.physiology.model

import scala.beans.BeanProperty

/**
 * One mineral aliment tracks, with the unit and the thresholds a real blood test would report.
 *
 * Sodium, potassium, magnesium, chloride and calcium are serum electrolytes and are tracked in
 * **mmol/L**; iodine is a trace element and is tracked in **umol/L**, a thousandth of a mmol,
 * because its whole physiological range is a fraction of one - in mmol/L it would only ever print
 * as "0.0005".
 *
 * The `safeLow`..`safeHigh` band is the reference range used in clinical practice, and there are two
 * thresholds on each side because a deficit and an excess of the same mineral are both illnesses:
 *
 * {{{
 *   min    severeLow   safeLow   normal   safeHigh   severeHigh    max
 *   |  severe  |  mild   | healthy  |   mild   |  severe   |
 * }}}
 *
 * The named instances live in [[MineralRanges]] rather than in a companion object, because a
 * companion would have to be called `ModelMineral` too and the whole point of the `Model` names is
 * that Kotlin's storage types (`Mineral`, `Mediators`, ...) and the model's own types stay apart.
 */
final case class ModelMineral(
    @BeanProperty name: String,
    @BeanProperty unit: String,
    @BeanProperty normal: Float,
    @BeanProperty safeLow: Float,
    @BeanProperty safeHigh: Float,
    @BeanProperty severeLow: Float,
    @BeanProperty severeHigh: Float,
    @BeanProperty min: Float,
    @BeanProperty max: Float,
) {

  /** Clamped to the hard limits, so a runaway value can never reach nonsense. */
  def clamp(value: Float): Float =
    if (value < this.min) this.min else if (value > this.max) this.max else value

  /** Distance outside the reference range, in this mineral's own units; 0 while inside it. */
  def deviation(value: Float): Float =
    if (value < this.safeLow) this.safeLow - value
    else if (value > this.safeHigh) value - this.safeHigh
    else 0f

  /**
   * How far outside the reference range `value` is, as a fraction of `normal`.
   *
   * Deviations cannot be compared across minerals in their own units - 0.2 is nothing to sodium and
   * a catastrophe for iodine - so anything that has to rank or combine them uses this.
   */
  def relativeDeviation(value: Float): Float = this.deviation(value) / this.normal

  /** -1 for a deficit, 1 for an excess, 0 while `value` is inside the reference range. */
  def direction(value: Float): Int =
    if (value < this.safeLow) -1 else if (value > this.safeHigh) 1 else 0

  /** True past `severeLow` or `severeHigh`. */
  def isSevere(value: Float): Boolean = value < this.severeLow || value > this.severeHigh

  /** The value as `/aliment status` prints it: iodine needs more decimals than sodium. */
  def display(value: Float): String =
    String.format("%." + (if (this.normal < 1f) 2 else 1) + "f", java.lang.Float.valueOf(value))
}

/** The minerals the model knows, and the reference range of each. */
object MineralRanges {

  /** 135-145 mmol/L, the clinical reference range. */
  val SODIUM: ModelMineral = new ModelMineral("SODIUM", "mmol/L", 140f, 135f, 145f, 125f, 150f, 100f, 190f)

  /** 3.5-5.0 mmol/L. */
  val POTASSIUM: ModelMineral = new ModelMineral("POTASSIUM", "mmol/L", 4.2f, 3.5f, 5f, 3f, 6f, 1.5f, 9f)

  /** 0.70-1.00 mmol/L. */
  val MAGNESIUM: ModelMineral = new ModelMineral("MAGNESIUM", "mmol/L", 0.85f, 0.7f, 1f, 0.5f, 1.5f, 0.2f, 3f)

  /** 96-106 mmol/L. */
  val CHLORIDE: ModelMineral = new ModelMineral("CHLORIDE", "mmol/L", 101f, 96f, 106f, 90f, 115f, 70f, 140f)

  /** 2.10-2.60 mmol/L. */
  val CALCIUM: ModelMineral = new ModelMineral("CALCIUM", "mmol/L", 2.35f, 2.1f, 2.6f, 1.8f, 3f, 1f, 4f)

  /** 0.40-0.80 umol/L of serum iodine. */
  val IODINE: ModelMineral = new ModelMineral("IODINE", "umol/L", 0.5f, 0.4f, 0.8f, 0.2f, 1.2f, 0.05f, 2f)

  /** 40.0-80.0 umol/L of serum vitamin C (ascorbic acid). */
  val VITAMIN_C: ModelMineral = new ModelMineral("VITAMIN_C", "umol/L", 60.0f, 40.0f, 80.0f, 15.0f, 120.0f, 0.0f, 150.0f)
}

/**
 * The inflammatory mediators the immune response is broken down into.
 *
 * The single "inflammation" number the system used to carry is derived from these, and drugs act on
 * individual mediators rather than on inflammation as a whole - which is what makes salicin and
 * dexamethasone behave differently.
 *
 * The weights and the named sets live in [[MediatorLevels]], for the same reason
 * [[ModelMineral]]'s do.
 */
final case class ModelMediators(
    @BeanProperty histamine: Float,
    @BeanProperty prostaglandin: Float,
    @BeanProperty leukotriene: Float,
    @BeanProperty cytokine: Float,
    @BeanProperty bradykinin: Float,
) {

  /** Inflammation index, 0..100, as a weighted sum of the mediators. */
  def getInflammation: Float = {
    val sum = MediatorLevels.HISTAMINE_WEIGHT * this.histamine +
      MediatorLevels.PROSTAGLANDIN_WEIGHT * this.prostaglandin +
      MediatorLevels.LEUKOTRIENE_WEIGHT * this.leukotriene +
      MediatorLevels.CYTOKINE_WEIGHT * this.cytokine +
      MediatorLevels.BRADYKININ_WEIGHT * this.bradykinin
    if (sum < 0f) 0f else if (sum > MediatorLevels.MAX) MediatorLevels.MAX else sum
  }

  def withHistamine(value: Float): ModelMediators = copy(histamine = value)
  def withProstaglandin(value: Float): ModelMediators = copy(prostaglandin = value)
  def withLeukotriene(value: Float): ModelMediators = copy(leukotriene = value)
  def withCytokine(value: Float): ModelMediators = copy(cytokine = value)
  def withBradykinin(value: Float): ModelMediators = copy(bradykinin = value)
}

/** The weights of the inflammation index, and the mediator sets the model is stated against. */
object MediatorLevels {

  val MAX: Float = 100f

  val HISTAMINE_WEIGHT: Float = 0.15f
  val PROSTAGLANDIN_WEIGHT: Float = 0.20f
  val LEUKOTRIENE_WEIGHT: Float = 0.15f

  /** Cytokines are the systemic driver, so they dominate the index. */
  val CYTOKINE_WEIGHT: Float = 0.35f
  val BRADYKININ_WEIGHT: Float = 0.15f

  val CALM: ModelMediators = new ModelMediators(0f, 0f, 0f, 0f, 0f)

  /**
   * The mediator levels a healthy player sits at: the fixed points of the model with no pathogen
   * present, giving an inflammation of exactly [[ModelConstants.BASELINE_INFLAMMATION]].
   */
  val RESTING: ModelMediators = new ModelMediators(25f, 30f, 30f, 20f, 25f)
}

/** The five electrolytes that are tracked separately, in mmol/L. */
final case class ModelElectrolytes(
    @BeanProperty sodium: Float,
    @BeanProperty potassium: Float,
    @BeanProperty magnesium: Float,
    @BeanProperty chloride: Float,
    @BeanProperty calcium: Float,
) {

  /** The value of `mineral`, for the table-driven parts. */
  def of(mineral: ModelMineral): Float =
    if (mineral == MineralRanges.SODIUM) this.sodium
    else if (mineral == MineralRanges.POTASSIUM) this.potassium
    else if (mineral == MineralRanges.MAGNESIUM) this.magnesium
    else if (mineral == MineralRanges.CHLORIDE) this.chloride
    else if (mineral == MineralRanges.CALCIUM) this.calcium
    else throw new IllegalArgumentException("iodine is a trace element, not an electrolyte")

  /**
   * How far the worst offender is outside its reference range, as a fraction of normal. 0 when
   * everything is inside.
   */
  def getWorstImbalance: Float = {
    var worst = 0f
    var i = 0
    while (i < ElectrolyteDefaults.MINERALS.size()) {
      val mineral = ElectrolyteDefaults.MINERALS.get(i)
      val deviation = mineral.relativeDeviation(this.of(mineral))
      if (deviation > worst) worst = deviation
      i += 1
    }
    worst
  }

  def withSodium(value: Float): ModelElectrolytes = copy(sodium = value)
  def withPotassium(value: Float): ModelElectrolytes = copy(potassium = value)
  def withMagnesium(value: Float): ModelElectrolytes = copy(magnesium = value)
  def withChloride(value: Float): ModelElectrolytes = copy(chloride = value)
  def withCalcium(value: Float): ModelElectrolytes = copy(calcium = value)
}

/** The electrolyte set and the order `/aliment status` prints it in. */
object ElectrolyteDefaults {

  /** The five electrolytes, in the order `/aliment status` prints them. */
  val MINERALS: java.util.List[ModelMineral] = java.util.List.of(
    MineralRanges.SODIUM,
    MineralRanges.POTASSIUM,
    MineralRanges.MAGNESIUM,
    MineralRanges.CHLORIDE,
    MineralRanges.CALCIUM,
  )

  val HEALTHY: ModelElectrolytes = new ModelElectrolytes(
    MineralRanges.SODIUM.normal,
    MineralRanges.POTASSIUM.normal,
    MineralRanges.MAGNESIUM.normal,
    MineralRanges.CHLORIDE.normal,
    MineralRanges.CALCIUM.normal,
  )
}

/**
 * The trace elements, which is iodine for now, in umol/L.
 *
 * Kept apart from the electrolytes because the body cannot make iodine at all, so the only way in is
 * food - kelp, in this mod - and the regulation is one-sided: a deficit is corrected by hanging on
 * to what little there is rather than by manufacturing more.
 */
final case class ModelTraceElements(
    @BeanProperty iodine: Float,
    @BeanProperty vitaminC: Float = MineralRanges.VITAMIN_C.normal,
) {
  def getWorstImbalance: Float =
    Math.max(MineralRanges.IODINE.relativeDeviation(this.iodine), MineralRanges.VITAMIN_C.relativeDeviation(this.vitaminC))

  /** -1 for a deficit, 1 for an excess, 0 while trace elements are inside the reference range. */
  def getDirection: Int =
    if (MineralRanges.IODINE.relativeDeviation(this.iodine) >= MineralRanges.VITAMIN_C.relativeDeviation(this.vitaminC))
      MineralRanges.IODINE.direction(this.iodine)
    else
      MineralRanges.VITAMIN_C.direction(this.vitaminC)

  def withIodine(value: Float): ModelTraceElements = copy(iodine = value)
  def withVitaminC(value: Float): ModelTraceElements = copy(vitaminC = value)
}

/** The trace element set a healthy player sits at. */
object TraceElementDefaults {
  val HEALTHY: ModelTraceElements = new ModelTraceElements(MineralRanges.IODINE.normal, MineralRanges.VITAMIN_C.normal)
}

/**
 * Pharmacological compounds and alkaloids carried in the body.
 *
 * Abstracted into its own structure so that ModelState remains clean and focused on core physiology,
 * while drug pharmacokinetics, clearance, and dosing can be evaluated together.
 */
final case class ModelDrugs(
    @BeanProperty salicin: Float = 0f,
    @BeanProperty dexamethasone: Float = 0f,
    @BeanProperty scopolamine: Float = 0f,
    @BeanProperty atropine: Float = 0f,
    @BeanProperty psilocybin: Float = 0f,
    @BeanProperty psilocin: Float = 0f,
    @BeanProperty ephedrine: Float = 0f,
    @BeanProperty berberine: Float = 0f,
    @BeanProperty glycyrrhizin: Float = 0f,
    /**
     * Naringin, the bitter flavanone glycoside that makes a grapefruit a grapefruit,
     * 0..[ModelConstants.NARINGIN_CAP].
     *
     * Nothing about it is therapeutic. It is here because it shuts down [ModelState.cyp3a4], the
     * liver enzyme that clears berberine, and that interaction - eat grapefruit, and coptis stops
     * leaving the body - is the whole point of tracking it.
     */
    @BeanProperty naringin: Float = 0f,
    @BeanProperty ethanol: Float = 0f,
    /**
     * Insulin aspart, the injected fast-acting analogue, 0..[ModelConstants.INSULIN_ASPART_CAP].
     *
     * Deliberately separate from [ModelState.insulin]: that one is the body's own, which the
     * pancreas switches off as glucose falls, and this one is not switched off by anything.
     */
    @BeanProperty insulinAspart: Float = 0f,
) {
  def withSalicin(value: Float): ModelDrugs = copy(salicin = value)
  def withDexamethasone(value: Float): ModelDrugs = copy(dexamethasone = value)
  def withScopolamine(value: Float): ModelDrugs = copy(scopolamine = value)
  def withAtropine(value: Float): ModelDrugs = copy(atropine = value)
  def withPsilocybin(value: Float): ModelDrugs = copy(psilocybin = value)
  def withPsilocin(value: Float): ModelDrugs = copy(psilocin = value)
  def withEphedrine(value: Float): ModelDrugs = copy(ephedrine = value)
  def withBerberine(value: Float): ModelDrugs = copy(berberine = value)
  def withGlycyrrhizin(value: Float): ModelDrugs = copy(glycyrrhizin = value)
  def withNaringin(value: Float): ModelDrugs = copy(naringin = value)
  def withEthanol(value: Float): ModelDrugs = copy(ethanol = value)
  def withInsulinAspart(value: Float): ModelDrugs = copy(insulinAspart = value)
}

/** The default clean drug state with zero concentration for all substances. */
object DrugDefaults {
  val CLEAN: ModelDrugs = new ModelDrugs(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
}

/**
 * The liver's enzyme indices, each on its own `0..100` activity scale.
 *
 * These are not the concentration of anything the player took. They are how fast a set of clearance
 * pathways is currently running, which is a property of the body rather than of the last meal - so
 * they belong beside [ModelState.drugs] rather than inside it, and they outlive the thing that moved
 * them only as long as that thing is still there.
 *
 * [cyp3a4] is the first of them, and it is the shape every drug interaction in this mod is built on:
 * something eaten changes an enzyme, and the enzyme changes how long something else lasts. A second
 * enzyme is a second field here and a second clearance rate that reads [cyp3a4Fraction]'s sibling.
 */
final case class ModelEnzymes(
    /**
     * CYP3A4 activity, 0..[ModelConstants.CYP3A4_MAX], and [ModelConstants.CYP3A4_NORMAL] in a body
     * that has eaten no grapefruit.
     *
     * It is a step function of [ModelDrugs.naringin] and of nothing else, so it is not a quantity
     * with a life of its own: it is read off the naringin each tick and never disagrees with it by
     * more than one tick.
     */
    @BeanProperty cyp3a4: Float = ModelConstants.CYP3A4_NORMAL,
) {

  /**
   * How fast this body clears what CYP3A4 clears, as a fraction of a clean body's rate.
   *
   * Exactly `1.0` at [ModelConstants.CYP3A4_NORMAL] and `0.12` at the deepest naringin step. It is
   * the form a clearance rate actually wants, so the division happens here once rather than at each
   * call site - and a rate that reads it cannot accidentally forget to.
   */
  def cyp3a4Fraction: Float = this.cyp3a4 / ModelConstants.CYP3A4_NORMAL

  def withCyp3a4(value: Float): ModelEnzymes = copy(cyp3a4 = value)
}

/** The enzyme indices of a body that has taken nothing: the model's starting point. */
object EnzymeDefaults {
  val NORMAL: ModelEnzymes = new ModelEnzymes()
}

/**
 * Every scalar the model reads or writes, in one place.
 *
 * `AlimentModelBridge` re-exports these names to Kotlin, so the numbers exist exactly once and the
 * Kotlin side, the docs and the self test all keep reading them from where they always did.
 */
object ModelConstants {
  val MIN_INFLAMMATION: Float = 0f
  val MAX_INFLAMMATION: Float = 100f
  val SAFE_INFLAMMATION_LOW: Float = 20f
  val SAFE_INFLAMMATION_HIGH: Float = 30f
  val BASELINE_INFLAMMATION: Float = 25f
  val IMMUNOSUPPRESSION_THRESHOLD: Float = 12f
  val IMMUNE_STORM_THRESHOLD: Float = 75f

  val MAX_PATHOGEN: Float = 100f
  val SYMPTOM_THRESHOLD: Float = 8f
  val IMMUNITY_ACTIVATION_LOAD: Float = 20f
  val IMMUNE_STRESS_LOAD: Float = 55f
  val SEVERE_LOAD: Float = 60f

  val WATER_MIN: Float = 0f
  val WATER_LOW: Float = 30f
  val WATER_NORMAL: Float = 100f
  val WATER_MAX: Float = 200f

  /** What a player starts with and what `/aliment cure` restores: hydrated, but not full. */
  val WATER_START: Float = 80f

  val WATER_VISIBLY_OVERHYDRATED: Float = 150f

  /** Below this the player is not just thirsty: hunger sets in. */
  val WATER_SEVERELY_DEHYDRATED: Float = 15f
  val THIRST_CELLS: Int = 10
  val WATER_PER_DRINK: Float = 15f
  /** Normal water loss: full water (100) is depleted in exactly 5 in-game days without sweating. */
  val WATER_DECAY_PER_TICK: Float = 100f / (5f * 24000f)

  val SALICIN_EFFECTIVE: Float = 1f
  val SALICIN_CAP: Float = 3f
  val SALICIN_METABOLISM_TICKS: Int = 72000
  val SALICIN_DECAY_PER_TICK: Float = SALICIN_CAP / SALICIN_METABOLISM_TICKS

  val DEXAMETHASONE_EFFECTIVE: Float = 1f
  val DEXAMETHASONE_CAP: Float = 2f
  val DEXAMETHASONE_METABOLISM_TICKS: Int = 48000
  val DEXAMETHASONE_DECAY_PER_TICK: Float = DEXAMETHASONE_CAP / DEXAMETHASONE_METABOLISM_TICKS

  val TEMPERATURE_NORMAL: Float = 37f
  val TEMPERATURE_MIN: Float = 30f
  val TEMPERATURE_MAX: Float = 42f
  val COLD_MILD: Float = 36f
  val COLD_SEVERE: Float = 35f
  val FEVER_MILD: Float = 38.5f
  val FEVER_NORMAL_IMMUNE_MAX: Float = 39.5f
  val FEVER_SEVERE: Float = 40f

  val PYROGEN_CAP: Float = 6f
  val PYROGEN_METABOLISM_TICKS: Int = 24000
  val PYROGEN_DECAY_PER_TICK: Float = PYROGEN_CAP / PYROGEN_METABOLISM_TICKS

  // ---------------------------------------------------------------- anticholinergics

  /** The most of either tropane alkaloid a body can carry. */
  val ANTICHOLINERGIC_CAP: Float = 5f

  /**
   * Total alkaloid load at which the body starts running a temperature.
   *
   * Unlike a fever from an infection this is not the immune system: it is the drug shutting down
   * sweating, which is exactly what atropine does to a real patient.
   */
  val ANTICHOLINERGIC_FEVER_THRESHOLD: Float = 1.5f

  /** The two steps above it: 1.5..2.5 runs to 38, 2.5..4 to 39.5, and 4 or more to 41. */
  val ANTICHOLINERGIC_FEVER_STEP: Float = 2.5f
  val ANTICHOLINERGIC_FEVER_MAX: Float = 4f

  /** The ceiling each step drives the core temperature towards. */
  val ANTICHOLINERGIC_FEVER_MILD: Float = 38f
  val ANTICHOLINERGIC_FEVER_SEVERE: Float = 39.5f
  val ANTICHOLINERGIC_FEVER_EXTREME: Float = 41f

  /** Either alkaloid alone this high blurs the vision... */
  val ANTICHOLINERGIC_BLUR_SINGLE: Float = 2.3f

  /** ...or the two of them together this high. */
  val ANTICHOLINERGIC_BLUR_TOTAL: Float = 2.7f

  /** How far a blurred player can see, in blocks. */
  val ANTICHOLINERGIC_BLUR_DISTANCE: Float = 8f

  /** Both alkaloids are cleared over one in-game day. */
  val ANTICHOLINERGIC_METABOLISM_TICKS: Int = 24000
  val ANTICHOLINERGIC_DECAY_PER_TICK: Float = ANTICHOLINERGIC_CAP / ANTICHOLINERGIC_METABOLISM_TICKS

  // ---------------------------------------------------------------- psilocybin and psilocin

  /** The most of either of the mushroom's compounds a body can carry. */
  val PSILOCYBIN_CAP: Float = 10f
  val PSILOCIN_CAP: Float = 10f

  /** One mushroom's worth, which is the unit both of the rates below are written in. */
  val PSILOCIN_DOSE: Float = 1.3f

  /** A dose of psilocybin becomes psilocin over half a game day, one for one. */
  val PSILOCYBIN_METABOLISM_TICKS: Int = 12000
  val PSILOCYBIN_DECAY_PER_TICK: Float = PSILOCIN_DOSE / PSILOCYBIN_METABOLISM_TICKS

  /**
   * Psilocin leaves at a flat 1.3 a game day whatever the level, rather than as a fraction of what
   * is there - so a single mushroom is gone in a day and ten of them take eight.
   */
  val PSILOCIN_METABOLISM_TICKS: Int = 24000
  val PSILOCIN_DECAY_PER_TICK: Float = PSILOCIN_DOSE / PSILOCIN_METABOLISM_TICKS

  /** Where the four stages of the trip start: outlines, colour, a mild warp, a hard one. */
  val PSILOCIN_OUTLINE: Float = 1.2f
  val PSILOCIN_COLOUR: Float = 1.7f
  val PSILOCIN_WARP: Float = 2.5f
  val PSILOCIN_STORM: Float = 5f

  /** Past a hard warp the body runs hot, and at 7 it is as bad as either of them gets. */
  val PSILOCIN_FEVER_STEP: Float = 7f
  val PSILOCIN_FEVER_MILD: Float = 39f
  val PSILOCIN_FEVER_EXTREME: Float = 41f

  // ---------------------------------------------------------------- ephedrine

  /** The most ephedrine a body can carry (0..5). */
  val EPHEDRINE_CAP: Float = 5f

  /** Ephedrine threshold above which Haste I is granted. */
  val EPHEDRINE_HASTE_THRESHOLD: Float = 1f

  /**
   * Ephedrine threshold above which the nervous system is too aroused to settle into sleep.
   *
   * One raw ephedra carries exactly this much, so a single twig is survivable but a second dose -
   * or the purified injection - keeps a body awake.
   */
  val EPHEDRINE_SLEEP_BLOCK_THRESHOLD: Float = 0.5f

  /** Ephedrine is completely metabolised within one in-game day (24000 ticks). */
  val EPHEDRINE_METABOLISM_TICKS: Int = 24000
  val EPHEDRINE_DECAY_PER_TICK: Float = EPHEDRINE_CAP / EPHEDRINE_METABOLISM_TICKS

  // ---------------------------------------------------------------- berberine & glycyrrhizin

  /** The maximum berberine concentration, 0..7. */
  val BERBERINE_CAP: Float = 7.0f

  /** Concentration threshold above which bacterial growth rate is reduced. */
  val BERBERINE_SLOW_THRESHOLD: Float = 1.5f

  /** Concentration threshold above which bacteria are suppressed (growth stops, count decays). */
  val BERBERINE_SUPPRESS_THRESHOLD: Float = 3.0f

  /** Berberine is completely metabolised within 2.5 in-game days (60000 ticks) from cap. */
  val BERBERINE_METABOLISM_TICKS: Int = 60000
  val BERBERINE_DECAY_PER_TICK: Float = BERBERINE_CAP / BERBERINE_METABOLISM_TICKS

  /** The maximum glycyrrhizin concentration, 0..7. */
  val GLYCYRRHIZIN_CAP: Float = 7.0f

  /** Concentration threshold above which viral growth rate is reduced. */
  val GLYCYRRHIZIN_SLOW_THRESHOLD: Float = 1.5f

  /** Concentration threshold above which viruses are suppressed (growth stops, count decays). */
  val GLYCYRRHIZIN_SUPPRESS_THRESHOLD: Float = 3.0f

  /** Glycyrrhizin is completely metabolised within 2 in-game days (48000 ticks) from cap. */
  val GLYCYRRHIZIN_METABOLISM_TICKS: Int = 48000
  val GLYCYRRHIZIN_DECAY_PER_TICK: Float = GLYCYRRHIZIN_CAP / GLYCYRRHIZIN_METABOLISM_TICKS

  // ---------------------------------------------------------------- naringin and CYP3A4

  /** The most naringin a body can carry, 0..10: ten grapefruit slices. */
  val NARINGIN_CAP: Float = 10f

  /**
   * Naringin is cleared within 1 in-game day (24000 ticks) from the cap.
   *
   * Half the lifetime of the mod's other two-day compounds, which is what keeps grapefruit a
   * decision about the next few hours rather than about the next few days: the enzyme is fully
   * suppressed for most of a day and back to normal by the next one.
   */
  val NARINGIN_METABOLISM_TICKS: Int = 24000
  val NARINGIN_DECAY_PER_TICK: Float = NARINGIN_CAP / NARINGIN_METABOLISM_TICKS

  /**
   * CYP3A4 activity in a body that has eaten no grapefruit: the baseline the berberine metabolism
   * was written against, and the value the index returns to once the naringin is gone.
   */
  val CYP3A4_NORMAL: Float = 85f

  /** The hard clamp on the index. */
  val CYP3A4_MIN: Float = 0f
  val CYP3A4_MAX: Float = 100f

  /**
   * The four naringin thresholds, and the CYP3A4 activity each one leaves behind.
   *
   * The first three are "above this", the last is "at or above": eight slices is already as bad as
   * it gets, and 8.5 is where that starts. Read together the two lists are one step function - see
   * `Physiology.cyp3a4For` - so the index only ever holds one of five values.
   */
  val NARINGIN_CYP_STEP_1: Float = 2f
  val NARINGIN_CYP_STEP_2: Float = 4f
  val NARINGIN_CYP_STEP_3: Float = 7f
  val NARINGIN_CYP_STEP_4: Float = 8.5f

  val CYP3A4_AT_STEP_1: Float = 60f
  val CYP3A4_AT_STEP_2: Float = 45f
  val CYP3A4_AT_STEP_3: Float = 25f
  val CYP3A4_AT_STEP_4: Float = 10f

  // ---------------------------------------------------------------- ethanol

  /** The maximum ethanol index a body can carry, 0..1.0. */
  val ETHANOL_CAP: Float = 1.0f

  /** Ethanol is metabolised over one in-game day (24000 ticks) from cap. */
  val ETHANOL_METABOLISM_TICKS: Int = 24000
  val ETHANOL_DECAY_PER_TICK: Float = ETHANOL_CAP / ETHANOL_METABOLISM_TICKS

  // ---------------------------------------------------------------- vitamin C

  /** Vitamin C excretion half-life: 5 in-game days (120,000 ticks) from safeHigh (80) to safeLow (40). */
  val VITAMIN_C_HALF_LIFE_TICKS: Int = 5 * 24000
  val VITAMIN_C_DECAY_RATE: Float = (Math.log(2.0) / (5.0 * 24000.0)).toFloat

  // ---------------------------------------------------------------- blood glucose

  /**
   * Blood glucose of a healthy fasting body, in **mmol/L**, and the middle of the reference range.
   *
   * Glucose is the one quantity in the model that is *spent* rather than regulated to a set point:
   * the body burns it continuously and only food puts it back, exactly like water. A player who
   * never eats therefore runs it down and hypoglycaemia is what stops them.
   */
  val GLUCOSE_NORMAL: Float = 5f

  /** The clinical reference range for blood glucose, in mmol/L. */
  val GLUCOSE_SAFE_LOW: Float = 4f
  val GLUCOSE_SAFE_HIGH: Float = 5.5f

  /** Hard clamp, so a runaway value can never reach nonsense. */
  val GLUCOSE_MIN: Float = 0f
  val GLUCOSE_MAX: Float = 30f

  /**
   * Where a fasted body levels off: from [GLUCOSE_NORMAL] to here takes exactly two in-game days
   * (48,000 ticks), and past it the fall slows down because the body is running on its stores.
   */
  val GLUCOSE_FASTING_FLOOR: Float = 3.5f

  /** The flat drain that makes that two-day fall exact. 1.5 mmol/L over 48,000 ticks. */
  val GLUCOSE_BASAL_DECAY_PER_TICK: Float = (GLUCOSE_NORMAL - GLUCOSE_FASTING_FLOOR) / (2f * 24000f)

  /** Above this the pancreas is clearly responding: the insulin index climbs steeply from here. */
  val GLUCOSE_ELEVATED: Float = 8f

  /**
   * The three hypoglycaemia thresholds, on the way down. Below the first the body is dragging and
   * gets mining fatigue; below the second it is also weak; below the third the brain is starved
   * enough that it starts taking magic damage.
   */
  val GLUCOSE_HYPO_FATIGUE: Float = 2f
  val GLUCOSE_HYPO_WEAKNESS: Float = 1.7f
  val GLUCOSE_HYPO_DAMAGE: Float = 1.3f

  /** Magic damage per two-second pass, per mmol/L of glucose below [GLUCOSE_HYPO_DAMAGE]. */
  val HYPOGLYCEMIA_DAMAGE_PER_MMOL: Float = 1f

  // ---------------------------------------------------------------- insulin

  /** The insulin index of a healthy fasting body. */
  val INSULIN_NORMAL: Float = 1f

  /** The most insulin a body can carry; a dose past it adds nothing. */
  val INSULIN_CAP: Float = 60f

  /**
   * Insulin secreted per mmol/L of glucose above normal, and above [GLUCOSE_ELEVATED].
   *
   * The index is already climbing through the normal band - which is what actually disposes of a
   * meal - and the second, twice-as-steep slope is what "past 8 the pancreas responds" means: the
   * higher the sugar, the more insulin, and the faster it comes down.
   */
  val INSULIN_PER_GLUCOSE: Float = 2f
  val INSULIN_PER_GLUCOSE_ELEVATED: Float = 4f

  /** How quickly the index moves towards what the current glucose asks for. 1/0.0005 = 2,000 ticks. */
  val INSULIN_APPROACH: Float = 0.0005f

  /** Glucose the body's own insulin disposes of, per tick and per unit of insulin above normal. */
  val GLUCOSE_UPTAKE_PER_INSULIN: Float = 0.0001f

  // ---------------------------------------------------------------- insulin aspart

  /**
   * Insulin aspart, the fast-acting analogue a player injects.
   *
   * Deliberately unlike the body's own insulin: an injection is not switched off when glucose
   * reaches the bottom of the reference range, which is the whole reason an insulin overdose is
   * dangerous. One dose from a normal 5.0 takes the player to about 2.7; two take them under 2.
   */
  val INSULIN_ASPART_CAP: Float = 60f
  val INSULIN_ASPART_PER_INJECTION: Float = 10f
  val INSULIN_ASPART_METABOLISM_TICKS: Int = 24000
  val INSULIN_ASPART_DECAY_PER_TICK: Float = INSULIN_ASPART_CAP / INSULIN_ASPART_METABOLISM_TICKS

  /** Glucose one unit of injected insulin disposes of, per tick. */
  val GLUCOSE_UPTAKE_PER_INSULIN_ASPART: Float = 0.00008f

  // ---------------------------------------------------------------- glucose from food

  /**
   * What one serving of each kind of food adds to blood glucose, in mmol/L.
   *
   * Bread is the worst of them and cooked meat the mildest of the animal foods; plant food and raw
   * meat sit together in the middle. A single serving is never enough to reach
   * [GLUCOSE_ELEVATED] on its own from a healthy 5.0, so it takes a real meal to provoke the
   * insulin response.
   */
  val GLUCOSE_PER_PLANT_FOOD: Float = 0.4f
  val GLUCOSE_PER_BREAD: Float = 0.7f
  val GLUCOSE_PER_RAW_MEAT: Float = 0.4f
  val GLUCOSE_PER_COOKED_MEAT: Float = 0.5f

  /**
   * A fruit drink that has had sugar stirred into it: grapefruit juice.
   *
   * It lands on the same figure as bread, and for the same reason - sugar is the fast carbohydrate,
   * and a glass of juice is mostly water with a spoonful of it. The fruit it is pressed from is only
   * worth [GLUCOSE_PER_PLANT_FOOD] on its own, so sweetening it is what makes it a meal's worth of
   * glucose rather than a snack's.
   */
  val GLUCOSE_PER_SWEET_DRINK: Float = 0.7f

  /**
   * A dish assembled out of several foods at once: a stew, a sandwich, a plate of pasta, a rice
   * bowl.
   *
   * It sits between plant food and cooked meat, and deliberately below bread. A dish is starch and
   * meat and vegetable together, and the meat and the fat in it slow the starch down - which is the
   * whole reason a meal of several things is gentler on blood glucose than the same weight of bread.
   * Treating it as bread would make every Farmer's Delight meal a sugar spike, which is the opposite
   * of what a mixed plate does.
   */
  val GLUCOSE_PER_MIXED_DISH: Float = 0.6f
}

/**
 * The model's whole input and output: everything `Physiology.tick` reads and everything it writes.
 *
 * Kotlin's `AlimentData` is the storage and serialisation layer (it owns the codecs and the Fabric
 * attachment), and `AlimentModelBridge` is the only thing that converts between the two. Keeping
 * the model on its own state type is what lets it stay free of Minecraft and of Kotlin: it is a pure
 * function of numbers.
 *
 * There is no companion object, so this is built with `new` and copied with `withX`; the scalars it
 * is stated against are in [[ModelConstants]].
 */
final case class ModelState(
    @BeanProperty mediators: ModelMediators,
    @BeanProperty bacteria: Float,
    @BeanProperty virus: Float,
    @BeanProperty water: Float,
    @BeanProperty electrolytes: ModelElectrolytes,
    @BeanProperty traceElements: ModelTraceElements,
    @BeanProperty temperature: Float,
    @BeanProperty pyrogen: Float,
    /** Whether the active immune response has been triggered (once pathogen load > 20). */
    @BeanProperty immuneActive: Boolean = false,
    /** Pharmacological compounds and alkaloids carried in the body. */
    @BeanProperty drugs: ModelDrugs = DrugDefaults.CLEAN,
    /**
     * The liver's enzyme indices, which is what clears [drugs] and at what rate.
     *
     * Deliberately a sibling of [drugs] rather than a member of it: a drug is something the body is
     * carrying and is clearing, an enzyme index is how fast one of its clearance pathways is
     * running. See [ModelEnzymes].
     */
    @BeanProperty enzymes: ModelEnzymes = EnzymeDefaults.NORMAL,
    /**
     * Blood glucose, in mmol/L. [ModelConstants.GLUCOSE_NORMAL] in a healthy fasting body; food is
     * the only thing that puts it back.
     */
    @BeanProperty glucose: Float = ModelConstants.GLUCOSE_NORMAL,
    /**
     * The body's own insulin index. [ModelConstants.INSULIN_NORMAL] while fasting and climbing with
     * glucose; it is what disposes of a meal. Injected insulin is separate, in
     * [ModelDrugs.insulinAspart].
     */
    @BeanProperty insulin: Float = ModelConstants.INSULIN_NORMAL,
) {
  def withMediators(value: ModelMediators): ModelState = copy(mediators = value)
  def withBacteria(value: Float): ModelState = copy(bacteria = value)
  def withVirus(value: Float): ModelState = copy(virus = value)
  def withWater(value: Float): ModelState = copy(water = value)
  def withElectrolytes(value: ModelElectrolytes): ModelState = copy(electrolytes = value)
  def withTraceElements(value: ModelTraceElements): ModelState = copy(traceElements = value)
  def withTemperature(value: Float): ModelState = copy(temperature = value)
  def withPyrogen(value: Float): ModelState = copy(pyrogen = value)
  def withImmuneActive(value: Boolean): ModelState = copy(immuneActive = value)
  def withDrugs(value: ModelDrugs): ModelState = copy(drugs = value)
  def withGlucose(value: Float): ModelState = copy(glucose = value)
  def withInsulin(value: Float): ModelState = copy(insulin = value)
  def withEnzymes(value: ModelEnzymes): ModelState = copy(enzymes = value)

  /**
   * CYP3A4 activity, read through [enzymes].
   *
   * The model reads the index as a bare number in the places that only need the value, so the
   * grouping in [ModelEnzymes] costs those call sites nothing.
   */
  def cyp3a4: Float = enzymes.cyp3a4

  /** How fast this body clears what CYP3A4 clears, as a fraction of a clean body's rate. */
  def cyp3a4Fraction: Float = enzymes.cyp3a4Fraction

  def withCyp3a4(value: Float): ModelState = copy(enzymes = enzymes.withCyp3a4(value))

  // Convenience accessors delegating to drugs
  def salicin: Float = drugs.salicin
  def dexamethasone: Float = drugs.dexamethasone
  def scopolamine: Float = drugs.scopolamine
  def atropine: Float = drugs.atropine
  def psilocybin: Float = drugs.psilocybin
  def psilocin: Float = drugs.psilocin
  def ephedrine: Float = drugs.ephedrine
  def berberine: Float = drugs.berberine
  def glycyrrhizin: Float = drugs.glycyrrhizin
  def naringin: Float = drugs.naringin
  def ethanol: Float = drugs.ethanol
  def insulinAspart: Float = drugs.insulinAspart

  def withSalicin(value: Float): ModelState = copy(drugs = drugs.withSalicin(value))
  def withDexamethasone(value: Float): ModelState = copy(drugs = drugs.withDexamethasone(value))
  def withScopolamine(value: Float): ModelState = copy(drugs = drugs.withScopolamine(value))
  def withAtropine(value: Float): ModelState = copy(drugs = drugs.withAtropine(value))
  def withPsilocybin(value: Float): ModelState = copy(drugs = drugs.withPsilocybin(value))
  def withPsilocin(value: Float): ModelState = copy(drugs = drugs.withPsilocin(value))
  def withEphedrine(value: Float): ModelState = copy(drugs = drugs.withEphedrine(value))
  def withBerberine(value: Float): ModelState = copy(drugs = drugs.withBerberine(value))
  def withGlycyrrhizin(value: Float): ModelState = copy(drugs = drugs.withGlycyrrhizin(value))
  def withNaringin(value: Float): ModelState = copy(drugs = drugs.withNaringin(value))
  def withEthanol(value: Float): ModelState = copy(drugs = drugs.withEthanol(value))
  def withInsulinAspart(value: Float): ModelState = copy(drugs = drugs.withInsulinAspart(value))
}
