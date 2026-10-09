package com.github.kusa233.aliment.physiology

import com.github.kusa233.aliment.registry.Registration
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * The inflammatory mediators the immune response is broken down into.
 *
 * The single "inflammation" number the system used to carry is now derived from these, and drugs act
 * on individual mediators rather than on inflammation as a whole - which is what makes salicin and
 * dexamethasone behave differently.
 *
 * The weights and the index itself are the model's, in Scala; this is only the shape Kotlin stores
 * and serialises.
 */
data class Mediators(
    val histamine: Float,
    val prostaglandin: Float,
    val leukotriene: Float,
    val cytokine: Float,
    val bradykinin: Float,
) {

    /** Inflammation index, 0..100, as a weighted sum of the mediators. */
    val inflammation: Float
        get() = AlimentModelBridge.inflammation(
            this.histamine, this.prostaglandin, this.leukotriene, this.cytokine, this.bradykinin,
        )

    fun withHistamine(value: Float): Mediators = this.copy(histamine = value)

    fun withProstaglandin(value: Float): Mediators = this.copy(prostaglandin = value)

    fun withLeukotriene(value: Float): Mediators = this.copy(leukotriene = value)

    fun withCytokine(value: Float): Mediators = this.copy(cytokine = value)

    fun withBradykinin(value: Float): Mediators = this.copy(bradykinin = value)

    companion object {

        @JvmField val MAX: Float = AlimentModelBridge.MEDIATORS_MAX

        @JvmField val HISTAMINE_WEIGHT: Float = AlimentModelBridge.MEDIATORS_HISTAMINE_WEIGHT
        @JvmField val PROSTAGLANDIN_WEIGHT: Float = AlimentModelBridge.MEDIATORS_PROSTAGLANDIN_WEIGHT
        @JvmField val LEUKOTRIENE_WEIGHT: Float = AlimentModelBridge.MEDIATORS_LEUKOTRIENE_WEIGHT

        /** Cytokines are the systemic driver, so they dominate the index. */
        @JvmField val CYTOKINE_WEIGHT: Float = AlimentModelBridge.MEDIATORS_CYTOKINE_WEIGHT
        @JvmField val BRADYKININ_WEIGHT: Float = AlimentModelBridge.MEDIATORS_BRADYKININ_WEIGHT

        /** No response at all: the fixed point a completely immunosuppressed body sits at. */
        @JvmField val CALM: Mediators = AlimentModelBridge.calmMediators()

        /**
         * The mediator levels a healthy player sits at. These are the fixed points of the model with
         * no pathogen present, and they give an inflammation of exactly
         * [AlimentData.BASELINE_INFLAMMATION]; the physiology self test asserts that.
         */
        @JvmField val RESTING: Mediators = AlimentModelBridge.restingMediators()

        val CODEC: Codec<Mediators> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("histamine").forGetter { it.histamine },
                Codec.FLOAT.fieldOf("prostaglandin").forGetter { it.prostaglandin },
                Codec.FLOAT.fieldOf("leukotriene").forGetter { it.leukotriene },
                Codec.FLOAT.fieldOf("cytokine").forGetter { it.cytokine },
                Codec.FLOAT.fieldOf("bradykinin").forGetter { it.bradykinin },
            ).apply(instance, ::Mediators)
        }
    }
}

/**
 * The five electrolytes that are tracked separately, in mmol/L.
 *
 * [Mineral.normal] is the healthy concentration and the safe band is [Mineral.safeLow]..
 * [Mineral.safeHigh]; both a deficit and an excess are modelled, because eating salt and drinking too
 * much water push the values in opposite directions.
 */
data class Electrolytes(
    val sodium: Float,
    val potassium: Float,
    val magnesium: Float,
    val chloride: Float,
    val calcium: Float,
) {

    /** The value of [mineral], for the table-driven parts. */
    fun of(mineral: Mineral): Float = when (mineral) {
        Mineral.SODIUM -> this.sodium
        Mineral.POTASSIUM -> this.potassium
        Mineral.MAGNESIUM -> this.magnesium
        Mineral.CHLORIDE -> this.chloride
        Mineral.CALCIUM -> this.calcium
        Mineral.IODINE -> throw IllegalArgumentException("iodine is a trace element, not an electrolyte")
        Mineral.VITAMIN_C -> throw IllegalArgumentException("vitamin C is a trace element/micronutrient, not an electrolyte")
    }

    /** How far the worst offender is outside its reference range, as a fraction of normal. */
    val worstImbalance: Float
        get() = AlimentModelBridge.worstImbalance(
            this.sodium, this.potassium, this.magnesium, this.chloride, this.calcium,
        )

    fun withSodium(value: Float): Electrolytes = this.copy(sodium = value)

    fun withPotassium(value: Float): Electrolytes = this.copy(potassium = value)

    fun withMagnesium(value: Float): Electrolytes = this.copy(magnesium = value)

    fun withChloride(value: Float): Electrolytes = this.copy(chloride = value)

    fun withCalcium(value: Float): Electrolytes = this.copy(calcium = value)

    companion object {

        /** The five electrolytes, in the order `/aliment status` prints them. */
        @JvmField val MINERALS: List<Mineral> =
            listOf(Mineral.SODIUM, Mineral.POTASSIUM, Mineral.MAGNESIUM, Mineral.CHLORIDE, Mineral.CALCIUM)

        /** Every electrolyte at its normal concentration. */
        @JvmField val HEALTHY: Electrolytes = AlimentModelBridge.healthyElectrolytes()

        val CODEC: Codec<Electrolytes> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("sodium").forGetter { it.sodium },
                Codec.FLOAT.fieldOf("potassium").forGetter { it.potassium },
                Codec.FLOAT.fieldOf("magnesium").forGetter { it.magnesium },
                Codec.FLOAT.fieldOf("chloride").forGetter { it.chloride },
                Codec.FLOAT.fieldOf("calcium").forGetter { it.calcium },
            ).apply(instance, ::Electrolytes)
        }
    }
}

/**
 * The trace elements, which is iodine for now, in umol/L.
 *
 * Kept apart from the electrolytes because they behave differently: the body cannot make iodine at
 * all, so the only way in is food - kelp, in this mod. It is still regulated, but the regulation is
 * one-sided: a deficit is corrected by hanging on to what little there is (renal conservation)
 * rather than by manufacturing more, so the diet sets how high the equilibrium sits.
 */
data class TraceElements(
    val iodine: Float,
    val vitaminC: Float = Mineral.VITAMIN_C.normal,
) {

    /** How far outside its reference range the worst element is, as a fraction of normal. */
    val worstImbalance: Float
        get() = maxOf(
            Mineral.IODINE.relativeDeviation(this.iodine),
            Mineral.VITAMIN_C.relativeDeviation(this.vitaminC),
        )

    /** -1 for a deficit, 1 for an excess, 0 while trace elements are inside the reference range. */
    val direction: Int
        get() = if (Mineral.IODINE.relativeDeviation(this.iodine) >= Mineral.VITAMIN_C.relativeDeviation(this.vitaminC)) {
            Mineral.IODINE.direction(this.iodine)
        } else {
            Mineral.VITAMIN_C.direction(this.vitaminC)
        }

    fun withIodine(value: Float): TraceElements = this.copy(iodine = value)

    fun withVitaminC(value: Float): TraceElements = this.copy(vitaminC = value)

    companion object {

        @JvmField val HEALTHY: TraceElements = AlimentModelBridge.healthyTraceElements()

        val CODEC: Codec<TraceElements> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("iodine").forGetter { it.iodine },
                Codec.FLOAT.optionalFieldOf("vitamin_c", Mineral.VITAMIN_C.normal).forGetter { it.vitaminC },
            ).apply(instance, ::TraceElements)
        }
    }
}

/**
 * A player's body, as far as this mod is concerned.
 *
 * This class is the **storage and serialisation** layer: it is what the Fabric attachment holds, what
 * the codecs read, and what the rest of the mod reads its thresholds from. Every number and every
 * steady state lives in Scala, in `src/main/scala/.../physiology/model`, and reaches this file
 * through [AlimentModelBridge] - the one place that knows both representations.
 *
 * The derived values below ([inflammation], [thermalTier], [isSevereInfection] and friends) are
 * delegations to that model rather than a second copy of its rules, so a threshold is defined in
 * exactly one place.
 */
data class AlimentData(
    val mediators: Mediators,
    val bacteria: Float,
    val virus: Float,
    /** Total body water, 0..[WATER_MAX]. The normal band is [WATER_LOW]..[WATER_NORMAL]. */
    val water: Float,
    val electrolytes: Electrolytes,
    val traceElements: TraceElements,
    /** Salicin, the willow bark soup drug. */
    val salicin: Float,
    /** Dexamethasone, the injected corticosteroid. */
    val dexamethasone: Float,
    /** Core temperature in degrees Celsius, normally [TEMPERATURE_NORMAL]. */
    val temperature: Float = TEMPERATURE_NORMAL,
    /** Circulating pyrogen, the exogenous fever driver, cleared over a game day. */
    val pyrogen: Float = 0f,
    /**
     * Scopolamine and atropine: the two tropane alkaloids a mandrake carries, 0..[ANTICHOLINERGIC_CAP]
     * each. Past 1.5 of the two together the body runs a temperature, and past 2.3 of either (or 2.7
     * of the two) the player's sight blurs down to [ANTICHOLINERGIC_BLUR_DISTANCE] blocks.
     */
    val scopolamine: Float = 0f,
    val atropine: Float = 0f,
    /**
     * What a raw gymnopilus carries, 0..[PSILOCYBIN_CAP] and 0..[PSILOCIN_CAP].
     *
     * Psilocybin has no effect of its own: it converts into psilocin one for one over half a game
     * day, and psilocin is what the trip and the fever come from. Psilocin itself is cleared at a
     * flat [PSILOCIN_DOSE] a game day, so a big dose lasts proportionally longer.
     */
    val psilocybin: Float = 0f,
    val psilocin: Float = 0f,
    /** Ephedrine, the stimulant alkaloid, 0..[EPHEDRINE_CAP]. */
    val ephedrine: Float = 0f,
    /** Whether the active immune response has been triggered (once pathogen load > 20). */
    val immuneActive: Boolean = false,
    /** Berberine, antimicrobial alkaloid targeting bacteria, 0..[BERBERINE_CAP]. */
    val berberine: Float = 0f,
    /** Glycyrrhizin, antiviral saponin targeting viruses, 0..[GLYCYRRHIZIN_CAP]. */
    val glycyrrhizin: Float = 0f,
    /**
     * Naringin, the bitter flavanone glycoside of grapefruit, 0..[NARINGIN_CAP].
     *
     * It has no effect of its own. What it does is hold [cyp3a4] down, and [cyp3a4] is what clears
     * berberine - so eating grapefruit is what makes a dose of coptis last.
     */
    val naringin: Float = 0f,
    /** Ethanol, alcohol index from drinking wine, 0..[ETHANOL_CAP]. */
    val ethanol: Float = 0f,
    /**
     * Blood glucose, in mmol/L. [GLUCOSE_NORMAL] in a healthy fasting body, and the middle of the
     * [GLUCOSE_SAFE_LOW]..[GLUCOSE_SAFE_HIGH] reference range.
     *
     * Nothing in the body stores a surplus: glucose is *spent* continuously and only food puts it
     * back, so it is the second quantity in the mod - after water - that a player has to keep
     * topping up. Below [GLUCOSE_HYPO_FATIGUE] the body starts to fail.
     */
    val glucose: Float = GLUCOSE_NORMAL,
    /**
     * The body's own insulin index, [INSULIN_NORMAL] while fasting and climbing with glucose.
     *
     * It is what disposes of a meal, and past [GLUCOSE_ELEVATED] it climbs twice as steeply - which
     * is why a big meal comes down faster than a small one.
     */
    val insulin: Float = INSULIN_NORMAL,
    /**
     * Injected insulin aspart, 0..[INSULIN_ASPART_CAP].
     *
     * Deliberately separate from [insulin]: the body's own is switched off at the bottom of the
     * reference range, and this one is not, so an overdose takes the player hypoglycaemic.
     */
    val insulinAspart: Float = 0f,
    /**
     * CYP3A4 activity, 0..[CYP3A4_MAX], and [CYP3A4_NORMAL] in a body that has eaten no grapefruit.
     *
     * The liver enzyme that clears berberine. It is a step function of [naringin] - one of five
     * values and nothing in between - so it is not a quantity the player can nudge, only one they
     * can walk down by eating grapefruit and back up by waiting.
     */
    val cyp3a4: Float = CYP3A4_NORMAL,
) {

    val inflammation: Float
        get() = this.mediators.inflammation

    /** Combined pathogen load, 0..200 in theory, 0..100 in practice. */
    val pathogenLoad: Float
        get() = AlimentModelBridge.pathogenLoad(this.bacteria, this.virus)

    /** True once the load is high enough to actually make the player feel ill. */
    val isSymptomatic: Boolean
        get() = AlimentModelBridge.isSymptomatic(this.pathogenLoad)

    val isImmuneStorm: Boolean
        get() = AlimentModelBridge.isImmuneStorm(this.inflammation)

    val isImmunosuppressed: Boolean
        get() = AlimentModelBridge.isImmunosuppressed(this.inflammation)

    /** 0..1 severity used to scale symptoms; saturates at [SEVERE_LOAD]. */
    val severity: Float
        get() = AlimentModelBridge.severity(this.pathogenLoad)

    /**
     * True once the infection is severe enough to hurt the host directly - sepsis, in the sense the
     * model uses. Reached through the infection and never through the immune system on its own: a low
     * inflammation does no damage itself, it only lets an infection climb until *this* is true.
     */
    val isSevereInfection: Boolean
        get() = AlimentModelBridge.isSevereInfection(this.pathogenLoad)

    /** True above [WATER_NORMAL]; causes weakness and slower mining. */
    val isOverhydrated: Boolean
        get() = AlimentModelBridge.isOverhydrated(this.water)

    /** True below [WATER_LOW]; the thirst bar is nearly empty. */
    val isDehydrated: Boolean
        get() = AlimentModelBridge.isDehydrated(this.water)

    /** The thirst bar, 0..10 cells. Each 10 points is one cell, and anything above 100 is full. */
    val thirstCells: Int
        get() = AlimentModelBridge.thirstCells(this.water)

    /** True when any electrolyte is outside its reference range. */
    val hasElectrolyteImbalance: Boolean
        get() = this.electrolytes.worstImbalance > 0f

    /** True when any trace element is outside its reference range. */
    val hasTraceElementImbalance: Boolean
        get() = this.traceElements.worstImbalance > 0f

    // ------------------------------------------------------------------ temperature

    /** True at or above [FEVER_MILD]; the player is running a fever. */
    val isFebrile: Boolean
        get() = AlimentModelBridge.isFebrile(this.temperature)

    /** True at or below [COLD_MILD]; the player is hypothermic. */
    val isHypothermic: Boolean
        get() = AlimentModelBridge.isHypothermic(this.temperature)

    /** True while the core temperature is outside the comfortable band. */
    val hasThermalStress: Boolean
        get() = AlimentModelBridge.hasThermalStress(this.temperature)

    /**
     * How far outside the comfortable band the core temperature is, as a signed tier:
     *
     * | tier | range | meaning |
     * | --- | --- | --- |
     * | 2 | ≥ 40.0 | super-high fever: everything tier 1 does, plus motion blur |
     * | 1 | 38.5 – 40.0 | fever: flushing and distortion at the screen edges, weakness, mining fatigue |
     * | 0 | 36.0 < T < 38.5 | comfortable |
     * | -1 | 35.0 – 36.0 | mild hypothermia |
     * | -2 | ≤ 35.0 | severe hypothermia |
     */
    val thermalTier: Int
        get() = AlimentModelBridge.thermalTier(this.temperature)

    // ------------------------------------------------------------------ mandrake alkaloids

    /** Scopolamine plus atropine, which is what the fever and the blur are both judged on. */
    val anticholinergicLoad: Float
        get() = AlimentModelBridge.anticholinergicLoad(this)

    /**
     * True when the alkaloids have blurred the player's sight: either one past 2.3, or the two
     * together past 2.7. Independent of any fever, so the two can be on the screen at once.
     */
    val isVisionBlurred: Boolean
        get() = AlimentModelBridge.isVisionBlurred(this)

    /**
     * How far into the trip the player is, as the stage the client has a screen effect for:
     *
     * | tier | psilocin | what the screen does |
     * | --- | --- | --- |
     * | 0 | ≤ 1.2 | nothing |
     * | 1 | > 1.2 | coloured outlines along every block edge |
     * | 2 | > 1.7 | the blocks themselves start taking random bright colours |
     * | 3 | > 2.5 | and the whole screen starts to bend |
     * | 4 | > 5 | it bends hard, and the body starts to run hot |
     */
    val psilocinTier: Int
        get() = AlimentModelBridge.psilocinTier(this)

    /** True when ephedrine exceeds the threshold for Haste I. */
    val hasHasteFromEphedrine: Boolean
        get() = AlimentModelBridge.hasHasteFromEphedrine(this)

    /** True when ephedrine is high enough that this body cannot fall asleep. */
    val isTooStimulatedToSleep: Boolean
        get() = AlimentModelBridge.isTooStimulatedToSleep(this)

    // ------------------------------------------------------------------ glucose

    /** True while the blood glucose is inside the clinical reference range. */
    val isGlucoseNormal: Boolean
        get() = this.glucose in GLUCOSE_SAFE_LOW..GLUCOSE_SAFE_HIGH

    /** True above the reference range: the meal is still being disposed of. */
    val isHyperglycemic: Boolean
        get() = this.glucose > GLUCOSE_SAFE_HIGH

    /**
     * How far into a hypoglycaemic crash this body is:
     *
     * | tier | glucose | what the player gets |
     * | --- | --- | --- |
     * | 0 | ≥ 2.0 | nothing |
     * | 1 | < 2.0 | mining fatigue |
     * | 2 | < 1.7 | and weakness |
     * | 3 | < 1.3 | and magic damage |
     */
    val hypoglycemiaTier: Int
        get() = AlimentModelBridge.hypoglycemiaTier(this)

    /** True once the glucose is low enough to start slowing the body down. */
    val isHypoglycemic: Boolean
        get() = this.hypoglycemiaTier > 0

    /** Magic damage a hypoglycaemic crash does in one two-second pass; 0 while it is not one. */
    val hypoglycemiaDamage: Float
        get() = AlimentModelBridge.hypoglycemiaDamage(this)

    /** The blood glucose as the meter and the chat line print it: one decimal, like a real one. */
    val glucoseReading: String
        get() = AlimentModelBridge.glucoseReading(this)

    fun withMediators(value: Mediators): AlimentData = this.copy(mediators = value)

    fun withElectrolytes(value: Electrolytes): AlimentData = this.copy(electrolytes = value)

    fun withTraceElements(value: TraceElements): AlimentData = this.copy(traceElements = value)

    fun withWater(value: Float): AlimentData = this.copy(water = value)

    fun withBacteria(value: Float): AlimentData = this.copy(bacteria = value)

    fun withVirus(value: Float): AlimentData = this.copy(virus = value)

    fun withSalicin(value: Float): AlimentData = this.copy(salicin = value)

    fun withDexamethasone(value: Float): AlimentData = this.copy(dexamethasone = value)

    fun withTemperature(value: Float): AlimentData = this.copy(temperature = value)

    fun withPyrogen(value: Float): AlimentData = this.copy(pyrogen = value)

    fun withScopolamine(value: Float): AlimentData = this.copy(scopolamine = value)

    fun withAtropine(value: Float): AlimentData = this.copy(atropine = value)

    fun withPsilocybin(value: Float): AlimentData = this.copy(psilocybin = value)

    fun withPsilocin(value: Float): AlimentData = this.copy(psilocin = value)

    fun withEphedrine(value: Float): AlimentData = this.copy(ephedrine = value)

    fun withImmuneActive(value: Boolean): AlimentData = this.copy(immuneActive = value)

    fun withBerberine(value: Float): AlimentData = this.copy(berberine = value)

    fun withGlycyrrhizin(value: Float): AlimentData = this.copy(glycyrrhizin = value)

    fun withEthanol(value: Float): AlimentData = this.copy(ethanol = value)

    companion object {

        // ---------------------------------------------------------------- the numbers
        //
        // Re-exported from the Scala model through the bridge, which owns every one of them. They keep
        // their old names and their old home so that nothing else in the mod, or in the docs, had to
        // move with them.

        @JvmField val MIN_INFLAMMATION: Float = AlimentModelBridge.MIN_INFLAMMATION
        @JvmField val MAX_INFLAMMATION: Float = AlimentModelBridge.MAX_INFLAMMATION

        /** The band a healthy player sits in. */
        @JvmField val SAFE_INFLAMMATION_LOW: Float = AlimentModelBridge.SAFE_INFLAMMATION_LOW
        @JvmField val SAFE_INFLAMMATION_HIGH: Float = AlimentModelBridge.SAFE_INFLAMMATION_HIGH

        /** The model's fixed point with no pathogen present, and the middle of the safe band. */
        @JvmField val BASELINE_INFLAMMATION: Float = AlimentModelBridge.BASELINE_INFLAMMATION

        /** Below this the immune system is suppressed and the infection runs away. */
        @JvmField val IMMUNOSUPPRESSION_THRESHOLD: Float = AlimentModelBridge.IMMUNOSUPPRESSION_THRESHOLD

        /** Above this the response itself is the disease. */
        @JvmField val IMMUNE_STORM_THRESHOLD: Float = AlimentModelBridge.IMMUNE_STORM_THRESHOLD

        @JvmField val MAX_PATHOGEN: Float = AlimentModelBridge.MAX_PATHOGEN

        /** Load at which the player starts showing symptoms. */
        @JvmField val SYMPTOM_THRESHOLD: Float = AlimentModelBridge.SYMPTOM_THRESHOLD

        /** Pathogen load threshold above which immune response activates and starts suppression. */
        @JvmField val IMMUNITY_ACTIVATION_LOAD: Float = AlimentModelBridge.IMMUNITY_ACTIVATION_LOAD

        /** Pathogen load threshold above which immune system enters stress and inflammation escalates. */
        @JvmField val IMMUNE_STRESS_LOAD: Float = AlimentModelBridge.IMMUNE_STRESS_LOAD

        /** Load at which the symptoms are at full strength and the infection starts doing damage. */
        @JvmField val SEVERE_LOAD: Float = AlimentModelBridge.SEVERE_LOAD

        /** Maximum core temperature under normal immune fever response before stress stage. */
        @JvmField val FEVER_NORMAL_IMMUNE_MAX: Float = AlimentModelBridge.FEVER_NORMAL_IMMUNE_MAX

        // ---------------------------------------------------------------- water

        @JvmField val WATER_MIN: Float = AlimentModelBridge.WATER_MIN

        /** Below this the player is dehydrated. */
        @JvmField val WATER_LOW: Float = AlimentModelBridge.WATER_LOW

        /** The top of the normal band. Above it the player is over-hydrated. */
        @JvmField val WATER_NORMAL: Float = AlimentModelBridge.WATER_NORMAL

        /** Hard ceiling so drinking cannot run away. */
        @JvmField val WATER_MAX: Float = AlimentModelBridge.WATER_MAX

        /** What a player starts with, and what `/aliment cure` restores. */
        @JvmField val WATER_START: Float = AlimentModelBridge.WATER_START

        /**
         * Where over-hydration becomes something the player can see for themselves: the thirst bar is
         * [THIRST_CELLS] cells and anything from [WATER_NORMAL] upwards draws as a full bar, so the
         * bar alone cannot tell 101 from 200. From here on the player also gets nausea, which is the
         * visible marker; effects that are only confusing when they come out of nowhere - the camera
         * tremor - wait for it.
         */
        @JvmField val WATER_VISIBLY_OVERHYDRATED: Float = AlimentModelBridge.WATER_VISIBLY_OVERHYDRATED

        /** Below this the player is not just thirsty: hunger sets in. */
        @JvmField val WATER_SEVERELY_DEHYDRATED: Float = AlimentModelBridge.WATER_SEVERELY_DEHYDRATED

        @JvmField val THIRST_CELLS: Int = AlimentModelBridge.THIRST_CELLS

        /** Water added by any drinkable. */
        @JvmField val WATER_PER_DRINK: Float = AlimentModelBridge.WATER_PER_DRINK

        /** A full bladder drains to one cell over roughly one in-game day. */
        @JvmField val WATER_DECAY_PER_TICK: Float = AlimentModelBridge.WATER_DECAY_PER_TICK

        // ---------------------------------------------------------------- drugs

        @JvmField val SALICIN_EFFECTIVE: Float = AlimentModelBridge.SALICIN_EFFECTIVE
        @JvmField val SALICIN_CAP: Float = AlimentModelBridge.SALICIN_CAP
        @JvmField val SALICIN_METABOLISM_TICKS: Int = AlimentModelBridge.SALICIN_METABOLISM_TICKS
        @JvmField val SALICIN_DECAY_PER_TICK: Float = AlimentModelBridge.SALICIN_DECAY_PER_TICK

        @JvmField val DEXAMETHASONE_EFFECTIVE: Float = AlimentModelBridge.DEXAMETHASONE_EFFECTIVE
        @JvmField val DEXAMETHASONE_CAP: Float = AlimentModelBridge.DEXAMETHASONE_CAP
        @JvmField val DEXAMETHASONE_METABOLISM_TICKS: Int = AlimentModelBridge.DEXAMETHASONE_METABOLISM_TICKS
        @JvmField val DEXAMETHASONE_DECAY_PER_TICK: Float = AlimentModelBridge.DEXAMETHASONE_DECAY_PER_TICK

        // ---------------------------------------------------------------- temperature

        /** Core temperature of a healthy player, and the set point thermoregulation defends. */
        @JvmField val TEMPERATURE_NORMAL: Float = AlimentModelBridge.TEMPERATURE_NORMAL

        /**
         * The comfortable band, just above 36.0 up to just below 38.5. The fever side deliberately
         * starts at 38.5 rather than 38.0: 38 is what a hot biome, a fire or a thyroid that runs hot
         * produces on its own, and the screen effects it switched on looked exactly like a bug.
         */
        @JvmField val COLD_MILD: Float = AlimentModelBridge.COLD_MILD
        @JvmField val FEVER_MILD: Float = AlimentModelBridge.FEVER_MILD

        /** Past these the thermal symptoms get worse; 40 is where a fever turns dangerous. */
        @JvmField val COLD_SEVERE: Float = AlimentModelBridge.COLD_SEVERE
        @JvmField val FEVER_SEVERE: Float = AlimentModelBridge.FEVER_SEVERE

        /** Hard clamp for the model; 42 is where proteins start to denature. */
        @JvmField val TEMPERATURE_MIN: Float = AlimentModelBridge.TEMPERATURE_MIN
        @JvmField val TEMPERATURE_MAX: Float = AlimentModelBridge.TEMPERATURE_MAX

        /** The most pyrogen a body can carry, i.e. the largest fever the test command can induce. */
        @JvmField val PYROGEN_CAP: Float = AlimentModelBridge.PYROGEN_CAP
        @JvmField val PYROGEN_METABOLISM_TICKS: Int = AlimentModelBridge.PYROGEN_METABOLISM_TICKS
        @JvmField val PYROGEN_DECAY_PER_TICK: Float = AlimentModelBridge.PYROGEN_DECAY_PER_TICK

        // ---------------------------------------------------------------- mandrake alkaloids

        /** The most of either tropane alkaloid a body can carry. */
        @JvmField val ANTICHOLINERGIC_CAP: Float = AlimentModelBridge.ANTICHOLINERGIC_CAP

        /** How far a blurred player can see, in blocks. */
        @JvmField val ANTICHOLINERGIC_BLUR_DISTANCE: Float = AlimentModelBridge.ANTICHOLINERGIC_BLUR_DISTANCE

        /** Either alkaloid is cleared over one in-game day. */
        @JvmField val ANTICHOLINERGIC_METABOLISM_TICKS: Int = AlimentModelBridge.ANTICHOLINERGIC_METABOLISM_TICKS

        // ---------------------------------------------------------------- gymnopilus compounds

        /** The most of either of the mushroom's compounds a body can carry. */
        @JvmField val PSILOCYBIN_CAP: Float = AlimentModelBridge.PSILOCYBIN_CAP
        @JvmField val PSILOCIN_CAP: Float = AlimentModelBridge.PSILOCIN_CAP

        /** What one raw mushroom carries, which is the unit both metabolism rates are written in. */
        @JvmField val PSILOCIN_DOSE: Float = AlimentModelBridge.PSILOCIN_DOSE

        /** Psilocybin becomes psilocin over half a game day; psilocin leaves over a whole one. */
        @JvmField val PSILOCYBIN_METABOLISM_TICKS: Int = AlimentModelBridge.PSILOCYBIN_METABOLISM_TICKS
        @JvmField val PSILOCIN_METABOLISM_TICKS: Int = AlimentModelBridge.PSILOCIN_METABOLISM_TICKS

        // ---------------------------------------------------------------- ephedrine

        /** The most ephedrine a body can carry, 0..5. */
        @JvmField val EPHEDRINE_CAP: Float = AlimentModelBridge.EPHEDRINE_CAP
        @JvmField val EPHEDRINE_HASTE_THRESHOLD: Float = AlimentModelBridge.EPHEDRINE_HASTE_THRESHOLD

        /** Above this ephedrine level the player cannot fall asleep. */
        @JvmField val EPHEDRINE_SLEEP_BLOCK_THRESHOLD: Float = AlimentModelBridge.EPHEDRINE_SLEEP_BLOCK_THRESHOLD

        @JvmField val EPHEDRINE_METABOLISM_TICKS: Int = AlimentModelBridge.EPHEDRINE_METABOLISM_TICKS
        @JvmField val EPHEDRINE_DECAY_PER_TICK: Float = AlimentModelBridge.EPHEDRINE_DECAY_PER_TICK

        // ---------------------------------------------------------------- berberine & glycyrrhizin

        /** The most berberine a body can carry, 0..7. */
        @JvmField val BERBERINE_CAP: Float = AlimentModelBridge.BERBERINE_CAP
        @JvmField val BERBERINE_SLOW_THRESHOLD: Float = AlimentModelBridge.BERBERINE_SLOW_THRESHOLD
        @JvmField val BERBERINE_SUPPRESS_THRESHOLD: Float = AlimentModelBridge.BERBERINE_SUPPRESS_THRESHOLD
        @JvmField val BERBERINE_METABOLISM_TICKS: Int = AlimentModelBridge.BERBERINE_METABOLISM_TICKS
        @JvmField val BERBERINE_DECAY_PER_TICK: Float = AlimentModelBridge.BERBERINE_DECAY_PER_TICK

        /** The most glycyrrhizin a body can carry, 0..7. */
        @JvmField val GLYCYRRHIZIN_CAP: Float = AlimentModelBridge.GLYCYRRHIZIN_CAP
        @JvmField val GLYCYRRHIZIN_SLOW_THRESHOLD: Float = AlimentModelBridge.GLYCYRRHIZIN_SLOW_THRESHOLD
        @JvmField val GLYCYRRHIZIN_SUPPRESS_THRESHOLD: Float = AlimentModelBridge.GLYCYRRHIZIN_SUPPRESS_THRESHOLD
        @JvmField val GLYCYRRHIZIN_METABOLISM_TICKS: Int = AlimentModelBridge.GLYCYRRHIZIN_METABOLISM_TICKS
        @JvmField val GLYCYRRHIZIN_DECAY_PER_TICK: Float = AlimentModelBridge.GLYCYRRHIZIN_DECAY_PER_TICK

        /** The most ethanol a body can carry, 0..1.0. */
        @JvmField val ETHANOL_CAP: Float = AlimentModelBridge.ETHANOL_CAP
        @JvmField val ETHANOL_METABOLISM_TICKS: Int = AlimentModelBridge.ETHANOL_METABOLISM_TICKS
        @JvmField val ETHANOL_DECAY_PER_TICK: Float = AlimentModelBridge.ETHANOL_DECAY_PER_TICK

        // ---------------------------------------------------------------- blood glucose

        /** Blood glucose of a healthy fasting body, in mmol/L, and the middle of the safe band. */
        @JvmField val GLUCOSE_NORMAL: Float = AlimentModelBridge.GLUCOSE_NORMAL

        /**
         * The clinical reference range, in mmol/L. A meal is disposed of back into this band within
         * half a game day; the fasting path leaves the bottom of it after two.
         */
        @JvmField val GLUCOSE_SAFE_LOW: Float = AlimentModelBridge.GLUCOSE_SAFE_LOW
        @JvmField val GLUCOSE_SAFE_HIGH: Float = AlimentModelBridge.GLUCOSE_SAFE_HIGH

        /** Hard clamp for the model. */
        @JvmField val GLUCOSE_MIN: Float = AlimentModelBridge.GLUCOSE_MIN
        @JvmField val GLUCOSE_MAX: Float = AlimentModelBridge.GLUCOSE_MAX

        /** Where a body that never eats levels off, two in-game days below the normal value. */
        @JvmField val GLUCOSE_FASTING_FLOOR: Float = AlimentModelBridge.GLUCOSE_FASTING_FLOOR

        /** Above this the insulin index climbs twice as steeply. */
        @JvmField val GLUCOSE_ELEVATED: Float = AlimentModelBridge.GLUCOSE_ELEVATED

        /** Below these the player is dragging, then weak, then taking magic damage. */
        @JvmField val GLUCOSE_HYPO_FATIGUE: Float = AlimentModelBridge.GLUCOSE_HYPO_FATIGUE
        @JvmField val GLUCOSE_HYPO_WEAKNESS: Float = AlimentModelBridge.GLUCOSE_HYPO_WEAKNESS
        @JvmField val GLUCOSE_HYPO_DAMAGE: Float = AlimentModelBridge.GLUCOSE_HYPO_DAMAGE

        // ---------------------------------------------------------------- insulin

        /** The insulin index of a healthy fasting body. */
        @JvmField val INSULIN_NORMAL: Float = AlimentModelBridge.INSULIN_NORMAL

        /** The most insulin a body can carry. */
        @JvmField val INSULIN_CAP: Float = AlimentModelBridge.INSULIN_CAP

        /** What one injection of insulin aspart delivers, and the most a body can carry. */
        @JvmField val INSULIN_ASPART_PER_INJECTION: Float = AlimentModelBridge.INSULIN_ASPART_PER_INJECTION
        @JvmField val INSULIN_ASPART_CAP: Float = AlimentModelBridge.INSULIN_ASPART_CAP
        @JvmField val INSULIN_ASPART_METABOLISM_TICKS: Int = AlimentModelBridge.INSULIN_ASPART_METABOLISM_TICKS

        // ---------------------------------------------------------------- glucose from food

        /** What one serving of each kind of food adds to blood glucose, in mmol/L. */
        @JvmField val GLUCOSE_PER_PLANT_FOOD: Float = AlimentModelBridge.GLUCOSE_PER_PLANT_FOOD
        @JvmField val GLUCOSE_PER_BREAD: Float = AlimentModelBridge.GLUCOSE_PER_BREAD
        @JvmField val GLUCOSE_PER_RAW_MEAT: Float = AlimentModelBridge.GLUCOSE_PER_RAW_MEAT
        @JvmField val GLUCOSE_PER_COOKED_MEAT: Float = AlimentModelBridge.GLUCOSE_PER_COOKED_MEAT
        @JvmField val GLUCOSE_PER_SWEET_DRINK: Float = AlimentModelBridge.GLUCOSE_PER_SWEET_DRINK

        /** What one assembled dish - a stew, a sandwich, a plate of pasta - adds: a mixed plate. */
        @JvmField val GLUCOSE_PER_MIXED_DISH: Float = AlimentModelBridge.GLUCOSE_PER_MIXED_DISH

        // ---------------------------------------------------------------- naringin & CYP3A4

        /** The most naringin a body can carry (0..10): ten grapefruit slices. */
        @JvmField val NARINGIN_CAP: Float = AlimentModelBridge.NARINGIN_CAP
        @JvmField val NARINGIN_METABOLISM_TICKS: Int = AlimentModelBridge.NARINGIN_METABOLISM_TICKS
        @JvmField val NARINGIN_DECAY_PER_TICK: Float = AlimentModelBridge.NARINGIN_DECAY_PER_TICK

        /** CYP3A4 activity with no grapefruit in the body, and the clamp the index lives in. */
        @JvmField val CYP3A4_NORMAL: Float = AlimentModelBridge.CYP3A4_NORMAL
        @JvmField val CYP3A4_MIN: Float = AlimentModelBridge.CYP3A4_MIN
        @JvmField val CYP3A4_MAX: Float = AlimentModelBridge.CYP3A4_MAX

        /** The four naringin steps, and the CYP3A4 activity each one leaves behind. */
        @JvmField val NARINGIN_CYP_STEP_1: Float = AlimentModelBridge.NARINGIN_CYP_STEP_1
        @JvmField val NARINGIN_CYP_STEP_2: Float = AlimentModelBridge.NARINGIN_CYP_STEP_2
        @JvmField val NARINGIN_CYP_STEP_3: Float = AlimentModelBridge.NARINGIN_CYP_STEP_3
        @JvmField val NARINGIN_CYP_STEP_4: Float = AlimentModelBridge.NARINGIN_CYP_STEP_4

        @JvmField val CYP3A4_AT_STEP_1: Float = AlimentModelBridge.CYP3A4_AT_STEP_1
        @JvmField val CYP3A4_AT_STEP_2: Float = AlimentModelBridge.CYP3A4_AT_STEP_2
        @JvmField val CYP3A4_AT_STEP_3: Float = AlimentModelBridge.CYP3A4_AT_STEP_3
        @JvmField val CYP3A4_AT_STEP_4: Float = AlimentModelBridge.CYP3A4_AT_STEP_4

        /** What a healthy player looks like. */
        @JvmField val HEALTHY: AlimentData = AlimentModelBridge.healthy()

        private data class Compounds(
            val salicin: Float,
            val dexamethasone: Float,
            val scopolamine: Float,
            val atropine: Float,
            val psilocybin: Float,
            val psilocin: Float,
            val ephedrine: Float,
            val berberine: Float,
            val glycyrrhizin: Float,
            val naringin: Float,
            val ethanol: Float,
            val insulinAspart: Float,
        ) {
            companion object {
                val MAP_CODEC: MapCodec<Compounds> = RecordCodecBuilder.mapCodec { instance ->
                    instance.group(
                        Codec.FLOAT.optionalFieldOf("salicin", 0f).forGetter { it.salicin },
                        Codec.FLOAT.optionalFieldOf("dexamethasone", 0f).forGetter { it.dexamethasone },
                        Codec.FLOAT.optionalFieldOf("scopolamine", 0f).forGetter { it.scopolamine },
                        Codec.FLOAT.optionalFieldOf("atropine", 0f).forGetter { it.atropine },
                        Codec.FLOAT.optionalFieldOf("psilocybin", 0f).forGetter { it.psilocybin },
                        Codec.FLOAT.optionalFieldOf("psilocin", 0f).forGetter { it.psilocin },
                        Codec.FLOAT.optionalFieldOf("ephedrine", 0f).forGetter { it.ephedrine },
                        Codec.FLOAT.optionalFieldOf("berberine", 0f).forGetter { it.berberine },
                        Codec.FLOAT.optionalFieldOf("glycyrrhizin", 0f).forGetter { it.glycyrrhizin },
                        Codec.FLOAT.optionalFieldOf("naringin", 0f).forGetter { it.naringin },
                        Codec.FLOAT.optionalFieldOf("ethanol", 0f).forGetter { it.ethanol },
                        Codec.FLOAT.optionalFieldOf("insulin_aspart", 0f).forGetter { it.insulinAspart },
                    ).apply(instance, ::Compounds)
                }
            }
        }

        val CODEC: Codec<AlimentData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Mediators.CODEC.fieldOf("mediators").forGetter { it.mediators },
                Codec.FLOAT.fieldOf("bacteria").forGetter { it.bacteria },
                Codec.FLOAT.fieldOf("virus").forGetter { it.virus },
                Codec.FLOAT.fieldOf("water").forGetter { it.water },
                Electrolytes.CODEC.fieldOf("electrolytes").forGetter { it.electrolytes },
                TraceElements.CODEC.fieldOf("trace_elements").forGetter { it.traceElements },
                Codec.FLOAT.optionalFieldOf("temperature", TEMPERATURE_NORMAL).forGetter { it.temperature },
                Codec.FLOAT.optionalFieldOf("pyrogen", 0f).forGetter { it.pyrogen },
                Codec.BOOL.optionalFieldOf("immune_active", false).forGetter { it.immuneActive },
                Codec.FLOAT.optionalFieldOf("glucose", GLUCOSE_NORMAL).forGetter { it.glucose },
                Codec.FLOAT.optionalFieldOf("insulin", INSULIN_NORMAL).forGetter { it.insulin },
                Codec.FLOAT.optionalFieldOf("cyp3a4", CYP3A4_NORMAL).forGetter { it.cyp3a4 },
                Compounds.MAP_CODEC.forGetter {
                    Compounds(
                        it.salicin,
                        it.dexamethasone,
                        it.scopolamine,
                        it.atropine,
                        it.psilocybin,
                        it.psilocin,
                        it.ephedrine,
                        it.berberine,
                        it.glycyrrhizin,
                        it.naringin,
                        it.ethanol,
                        it.insulinAspart,
                    )
                },
            ).apply(instance) { mediators, bacteria, virus, water, electrolytes, traceElements, temperature, pyrogen, immuneActive, glucose, insulin, cyp3a4, compounds ->
                AlimentData(
                    mediators = mediators,
                    bacteria = bacteria,
                    virus = virus,
                    water = water,
                    electrolytes = electrolytes,
                    traceElements = traceElements,
                    salicin = compounds.salicin,
                    dexamethasone = compounds.dexamethasone,
                    temperature = temperature,
                    pyrogen = pyrogen,
                    scopolamine = compounds.scopolamine,
                    atropine = compounds.atropine,
                    psilocybin = compounds.psilocybin,
                    psilocin = compounds.psilocin,
                    ephedrine = compounds.ephedrine,
                    immuneActive = immuneActive,
                    berberine = compounds.berberine,
                    glycyrrhizin = compounds.glycyrrhizin,
                    naringin = compounds.naringin,
                    ethanol = compounds.ethanol,
                    glucose = glucose,
                    insulin = insulin,
                    insulinAspart = compounds.insulinAspart,
                    cyp3a4 = cyp3a4,
                )
            }
        }
    }
}

/**
 * The bits of the physiology the client needs.
 *
 * Synced rather than simulated: the client only draws the thirst bar and the camera shake, so it is
 * told the water level and the shake counter instead of running the model.
 */
data class AlimentClientState(
    /** Bumped every time the server wants the camera to shake. */
    val shakeSequence: Int,
    val shakeAmplitude: Float,
    /** Whole water points, for the thirst bar. */
    val water: Int,
    /**
     * True while the mandrake alkaloids have blurred the player's sight. The client turns it into
     * fog out at [AlimentData.ANTICHOLINERGIC_BLUR_DISTANCE] blocks, which is the one part of the
     * blur the server cannot draw for the player.
     */
    val blurred: Boolean = false,
) {
    companion object {
        val INACTIVE = AlimentClientState(0, 0f, 0)

        val CODEC: Codec<AlimentClientState> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("shake_sequence").forGetter { it.shakeSequence },
                Codec.FLOAT.fieldOf("shake_amplitude").forGetter { it.shakeAmplitude },
                Codec.INT.fieldOf("water").forGetter { it.water },
                Codec.BOOL.optionalFieldOf("blurred", false).forGetter { it.blurred },
            ).apply(instance, ::AlimentClientState)
        }

        val STREAM_CODEC: StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, AlimentClientState> =
            StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AlimentClientState::shakeSequence,
                ByteBufCodecs.FLOAT, AlimentClientState::shakeAmplitude,
                ByteBufCodecs.VAR_INT, AlimentClientState::water,
                ByteBufCodecs.BOOL, AlimentClientState::blurred,
                ::AlimentClientState,
            )
    }
}

/**
 * The attachments every player carries.
 *
 * * `physiology` is the whole body, persisted but deliberately **not** carried across death;
 * * `client_state` is the small subset the client needs, synced to everyone;
 * * `runtime` is per-session counters that are deliberately neither saved nor synced.
 */
object AlimentAttachments {

    /**
     * The whole body.
     *
     * There is no `copyOnDeath()` on purpose: a respawned player is a new player, with a healthy body
     * and no infection, so death is the one cure that always works. Without that line Fabric starts
     * the new player from [AlimentData.HEALTHY], which is also what a fresh player gets.
     */
    val DATA: AttachmentType<AlimentData> = AttachmentRegistry.create(Registration.id("physiology")) { builder ->
        builder
            .initializer { AlimentData.HEALTHY }
            .persistent(AlimentData.CODEC)
    }

    /** The bits the client needs, pushed to everyone who can see the player. */
    val CLIENT: AttachmentType<AlimentClientState> =
        AttachmentRegistry.create(Registration.id("client_state")) { builder ->
            builder
                .initializer { AlimentClientState.INACTIVE }
                .persistent(AlimentClientState.CODEC)
                .syncWith(AlimentClientState.STREAM_CODEC, AttachmentSyncPredicate.all())
        }

    /**
     * Per-player timers that only matter while the player is online, so they are neither saved nor
     * synced.
     */
    val RUNTIME: AttachmentType<AlimentRuntime> = AttachmentRegistry.create(Registration.id("runtime"))

    fun initialize() {
        // Registration happens in the property initialisers above.
    }
}

/** Short lived counters; deliberately mutable and never serialised. */
class AlimentRuntime {
    /** Ticks until the next "will the view shake?" roll. */
    var shakeCooldown: Int = 0

    /** Ticks until the next "did something next to me pass on a virus?" roll. */
    var contactCooldown: Int = 0

    /** Ticks until the next "am I suppressed enough to catch something random?" roll. */
    var immunosuppressionCooldown: Int = 0

    /** Last water value that was pushed to the client, so the bar is only resent when it moves. */
    var syncedWater: Int = Int.MIN_VALUE

    /** Last shake counter that was pushed to the client. */
    var syncedShake: Int = Int.MIN_VALUE

    /**
     * How long the finger is still bleeding after a microneedle, in ticks.
     *
     * A test strip used while this is positive comes away bloodied; once it reaches zero the drop
     * has dried and the strip is wasted. See [AlimentInteractions.MICRONEEDLE_BLEEDING_TICKS].
     */
    var bleedingTicks: Int = 0
}
