package com.github.kusa233.aliment.physiology;

import com.github.kusa233.aliment.physiology.model.ElectrolyteDefaults;
import com.github.kusa233.aliment.physiology.model.MediatorLevels;
import com.github.kusa233.aliment.physiology.model.MineralRanges;
import com.github.kusa233.aliment.physiology.model.ModelConstants;
import com.github.kusa233.aliment.physiology.model.ModelDrugs;
import com.github.kusa233.aliment.physiology.model.ModelElectrolytes;
import com.github.kusa233.aliment.physiology.model.ModelEnzymes;
import com.github.kusa233.aliment.physiology.model.ModelMediators;
import com.github.kusa233.aliment.physiology.model.ModelMineral;
import com.github.kusa233.aliment.physiology.model.ModelState;
import com.github.kusa233.aliment.physiology.model.ModelTraceElements;
import com.github.kusa233.aliment.physiology.model.Physiology;
import com.github.kusa233.aliment.physiology.model.TraceElementDefaults;

import java.util.Map;

/**
 * The seam between the mod and its numerical model.
 *
 * Every number and every calculation lives in Scala, in
 * {@code src/main/scala/.../physiology/model}. Kotlin cannot talk to it directly: K2 resolves a Scala
 * class by also loading its supertypes, so a Kotlin file that merely *names* a model type drags in
 * {@code scala.Product} and IntelliJ reports `Cannot access 'scala.Product' which is a supertype of
 * 'Mineral'` however the classpath is wired. Java references its dependencies by JVM descriptor and
 * has no such problem, so this class is the only thing in the project that is allowed to mention a
 * Scala class, and Kotlin only ever calls through it.
 *
 * The build order is what makes that possible: Scala compiles first, Kotlin second, Java last, so a
 * Java file can see both sides. Nothing here decides anything - it converts, it forwards, and it
 * re-exports the model's numbers under the names Kotlin already uses, so the model stays their single
 * source.
 *
 * <h2>Rules for this file</h2>
 *
 * <ul>
 *   <li>Nothing Scala-typed may appear in a public method, field or parameter. Kotlin resolves those
 *       eagerly, and a Scala type there puts the whole problem back. Scala types are confined to
 *       private fields and method bodies, which the Kotlin compiler never resolves.</li>
 *   <li>No arithmetic and no thresholds: every value below is read from the model or handed to it.
 *       If a number is needed on both sides it is added to the model and re-exported here.</li>
 *   <li>The model's objects are ordinary objects rather than companions, so their members are the
 *       static forwarders Scala generates for them: `ModelConstants.WATER_MAX()`, not
 *       `ModelConstants.WATER_MAX`. That is also why the names never collide with Kotlin's own
 *       storage types and every model type can be imported.</li>
 * </ul>
 */
public final class AlimentModelBridge {

    private AlimentModelBridge() {
    }

    // ================================================================== the numbers
    //
    // Re-exported from the model, which owns every one of them. They keep their old names and their
    // old home so that nothing else in the mod, or in the docs, had to move with them.

    public static final float MIN_INFLAMMATION = ModelConstants.MIN_INFLAMMATION();
    public static final float MAX_INFLAMMATION = ModelConstants.MAX_INFLAMMATION();

    /** The band a healthy player sits in. */
    public static final float SAFE_INFLAMMATION_LOW = ModelConstants.SAFE_INFLAMMATION_LOW();
    public static final float SAFE_INFLAMMATION_HIGH = ModelConstants.SAFE_INFLAMMATION_HIGH();

    /** The model's fixed point with no pathogen present, and the middle of the safe band. */
    public static final float BASELINE_INFLAMMATION = ModelConstants.BASELINE_INFLAMMATION();

    /** Below this the immune system is suppressed and the infection runs away. */
    public static final float IMMUNOSUPPRESSION_THRESHOLD = ModelConstants.IMMUNOSUPPRESSION_THRESHOLD();

    /** Above this the response itself is the disease. */
    public static final float IMMUNE_STORM_THRESHOLD = ModelConstants.IMMUNE_STORM_THRESHOLD();

    public static final float MAX_PATHOGEN = ModelConstants.MAX_PATHOGEN();

    /** Load at which the player starts showing symptoms. */
    public static final float SYMPTOM_THRESHOLD = ModelConstants.SYMPTOM_THRESHOLD();

    /** Pathogen load threshold above which immune response activates and starts suppression. */
    public static final float IMMUNITY_ACTIVATION_LOAD = ModelConstants.IMMUNITY_ACTIVATION_LOAD();

    /** Pathogen load threshold above which immune system enters stress and inflammation escalates. */
    public static final float IMMUNE_STRESS_LOAD = ModelConstants.IMMUNE_STRESS_LOAD();

    /** Load at which the symptoms are at full strength and the infection starts doing damage. */
    public static final float SEVERE_LOAD = ModelConstants.SEVERE_LOAD();

    /** Maximum core temperature under normal immune fever response before stress stage. */
    public static final float FEVER_NORMAL_IMMUNE_MAX = ModelConstants.FEVER_NORMAL_IMMUNE_MAX();

    // ---------------------------------------------------------------- mediators

    public static final float MEDIATORS_MAX = MediatorLevels.MAX();
    public static final float MEDIATORS_HISTAMINE_WEIGHT = MediatorLevels.HISTAMINE_WEIGHT();
    public static final float MEDIATORS_PROSTAGLANDIN_WEIGHT = MediatorLevels.PROSTAGLANDIN_WEIGHT();
    public static final float MEDIATORS_LEUKOTRIENE_WEIGHT = MediatorLevels.LEUKOTRIENE_WEIGHT();
    public static final float MEDIATORS_CYTOKINE_WEIGHT = MediatorLevels.CYTOKINE_WEIGHT();
    public static final float MEDIATORS_BRADYKININ_WEIGHT = MediatorLevels.BRADYKININ_WEIGHT();

    // ---------------------------------------------------------------- water

    public static final float WATER_MIN = ModelConstants.WATER_MIN();

    /** Below this the player is dehydrated. */
    public static final float WATER_LOW = ModelConstants.WATER_LOW();

    /** The top of the normal band. Above it the player is over-hydrated. */
    public static final float WATER_NORMAL = ModelConstants.WATER_NORMAL();

    /** Hard ceiling so drinking cannot run away. */
    public static final float WATER_MAX = ModelConstants.WATER_MAX();

    /** What a player starts with, and what `/aliment cure` restores. */
    public static final float WATER_START = ModelConstants.WATER_START();

    /** Where over-hydration becomes visible: the thirst bar is already full below this. */
    public static final float WATER_VISIBLY_OVERHYDRATED = ModelConstants.WATER_VISIBLY_OVERHYDRATED();

    /** Below this the player is not just thirsty: hunger sets in. */
    public static final float WATER_SEVERELY_DEHYDRATED = ModelConstants.WATER_SEVERELY_DEHYDRATED();

    public static final int THIRST_CELLS = ModelConstants.THIRST_CELLS();

    /** Water added by any drinkable. */
    public static final float WATER_PER_DRINK = ModelConstants.WATER_PER_DRINK();

    /** A full bladder drains to one cell over roughly one in-game day. */
    public static final float WATER_DECAY_PER_TICK = ModelConstants.WATER_DECAY_PER_TICK();

    // ---------------------------------------------------------------- drugs

    public static final float SALICIN_EFFECTIVE = ModelConstants.SALICIN_EFFECTIVE();
    public static final float SALICIN_CAP = ModelConstants.SALICIN_CAP();
    public static final int SALICIN_METABOLISM_TICKS = ModelConstants.SALICIN_METABOLISM_TICKS();
    public static final float SALICIN_DECAY_PER_TICK = ModelConstants.SALICIN_DECAY_PER_TICK();

    public static final float DEXAMETHASONE_EFFECTIVE = ModelConstants.DEXAMETHASONE_EFFECTIVE();
    public static final float DEXAMETHASONE_CAP = ModelConstants.DEXAMETHASONE_CAP();
    public static final int DEXAMETHASONE_METABOLISM_TICKS = ModelConstants.DEXAMETHASONE_METABOLISM_TICKS();
    public static final float DEXAMETHASONE_DECAY_PER_TICK = ModelConstants.DEXAMETHASONE_DECAY_PER_TICK();

    // ---------------------------------------------------------------- temperature

    /** Core temperature of a healthy player, and the set point thermoregulation defends. */
    public static final float TEMPERATURE_NORMAL = ModelConstants.TEMPERATURE_NORMAL();

    /**
     * The comfortable band, just above 36.0 up to just below 38.5. The fever side deliberately
     * starts at 38.5 rather than 38.0: 38 is what a hot biome, a fire or a thyroid that runs hot
     * produces on its own, and the screen effects it switched on looked exactly like a bug.
     */
    public static final float COLD_MILD = ModelConstants.COLD_MILD();
    public static final float FEVER_MILD = ModelConstants.FEVER_MILD();

    /** Past these the thermal symptoms get worse; 40 is where a fever turns dangerous. */
    public static final float COLD_SEVERE = ModelConstants.COLD_SEVERE();
    public static final float FEVER_SEVERE = ModelConstants.FEVER_SEVERE();

    /** Hard clamp for the model; 42 is where proteins start to denature. */
    public static final float TEMPERATURE_MIN = ModelConstants.TEMPERATURE_MIN();
    public static final float TEMPERATURE_MAX = ModelConstants.TEMPERATURE_MAX();

    /** The most pyrogen a body can carry, i.e. the largest fever the test command can induce. */
    public static final float PYROGEN_CAP = ModelConstants.PYROGEN_CAP();
    public static final int PYROGEN_METABOLISM_TICKS = ModelConstants.PYROGEN_METABOLISM_TICKS();
    public static final float PYROGEN_DECAY_PER_TICK = ModelConstants.PYROGEN_DECAY_PER_TICK();

    // ---------------------------------------------------------------- mandrake alkaloids

    /** The most of either tropane alkaloid a body can carry. */
    public static final float ANTICHOLINERGIC_CAP = ModelConstants.ANTICHOLINERGIC_CAP();

    /** How far a player whose sight the alkaloids have blurred can see, in blocks. */
    public static final float ANTICHOLINERGIC_BLUR_DISTANCE = ModelConstants.ANTICHOLINERGIC_BLUR_DISTANCE();

    /** Either alkaloid is cleared over one in-game day. */
    public static final int ANTICHOLINERGIC_METABOLISM_TICKS = ModelConstants.ANTICHOLINERGIC_METABOLISM_TICKS();
    public static final float ANTICHOLINERGIC_DECAY_PER_TICK = ModelConstants.ANTICHOLINERGIC_DECAY_PER_TICK();

    // ---------------------------------------------------------------- gymnopilus compounds

    /** The most of either of the mushroom's compounds a body can carry. */
    public static final float PSILOCYBIN_CAP = ModelConstants.PSILOCYBIN_CAP();
    public static final float PSILOCIN_CAP = ModelConstants.PSILOCIN_CAP();

    /** What one raw mushroom carries, which is the unit both rates below are written in. */
    public static final float PSILOCIN_DOSE = ModelConstants.PSILOCIN_DOSE();

    /** Psilocybin becomes psilocin over half a game day; psilocin leaves over a whole one. */
    public static final int PSILOCYBIN_METABOLISM_TICKS = ModelConstants.PSILOCYBIN_METABOLISM_TICKS();
    public static final int PSILOCIN_METABOLISM_TICKS = ModelConstants.PSILOCIN_METABOLISM_TICKS();

    // ---------------------------------------------------------------- ephedrine

    /** The most ephedrine a body can carry. */
    public static final float EPHEDRINE_CAP = ModelConstants.EPHEDRINE_CAP();

    /** Threshold for Haste I effect. */
    public static final float EPHEDRINE_HASTE_THRESHOLD = ModelConstants.EPHEDRINE_HASTE_THRESHOLD();

    /** Above this ephedrine level the player is too stimulated to fall asleep. */
    public static final float EPHEDRINE_SLEEP_BLOCK_THRESHOLD = ModelConstants.EPHEDRINE_SLEEP_BLOCK_THRESHOLD();

    /** Ephedrine is completely metabolised within one in-game day. */
    public static final int EPHEDRINE_METABOLISM_TICKS = ModelConstants.EPHEDRINE_METABOLISM_TICKS();
    public static final float EPHEDRINE_DECAY_PER_TICK = ModelConstants.EPHEDRINE_DECAY_PER_TICK();

    // ---------------------------------------------------------------- berberine & glycyrrhizin

    /** The maximum berberine concentration (0..7). */
    public static final float BERBERINE_CAP = ModelConstants.BERBERINE_CAP();

    /** Concentration threshold above which bacterial growth rate is reduced. */
    public static final float BERBERINE_SLOW_THRESHOLD = ModelConstants.BERBERINE_SLOW_THRESHOLD();

    /** Concentration threshold above which bacteria are suppressed (growth stops, count decays). */
    public static final float BERBERINE_SUPPRESS_THRESHOLD = ModelConstants.BERBERINE_SUPPRESS_THRESHOLD();

    public static final int BERBERINE_METABOLISM_TICKS = ModelConstants.BERBERINE_METABOLISM_TICKS();
    public static final float BERBERINE_DECAY_PER_TICK = ModelConstants.BERBERINE_DECAY_PER_TICK();

    /** The maximum glycyrrhizin concentration (0..7). */
    public static final float GLYCYRRHIZIN_CAP = ModelConstants.GLYCYRRHIZIN_CAP();

    /** Concentration threshold above which viral growth rate is reduced. */
    public static final float GLYCYRRHIZIN_SLOW_THRESHOLD = ModelConstants.GLYCYRRHIZIN_SLOW_THRESHOLD();

    /** Concentration threshold above which viruses are suppressed (growth stops, count decays). */
    public static final float GLYCYRRHIZIN_SUPPRESS_THRESHOLD = ModelConstants.GLYCYRRHIZIN_SUPPRESS_THRESHOLD();

    public static final int GLYCYRRHIZIN_METABOLISM_TICKS = ModelConstants.GLYCYRRHIZIN_METABOLISM_TICKS();
    public static final float GLYCYRRHIZIN_DECAY_PER_TICK = ModelConstants.GLYCYRRHIZIN_DECAY_PER_TICK();

    // ---------------------------------------------------------------- naringin & CYP3A4

    /** The maximum naringin a body can carry (0..10): ten grapefruit slices. */
    public static final float NARINGIN_CAP = ModelConstants.NARINGIN_CAP();

    public static final int NARINGIN_METABOLISM_TICKS = ModelConstants.NARINGIN_METABOLISM_TICKS();
    public static final float NARINGIN_DECAY_PER_TICK = ModelConstants.NARINGIN_DECAY_PER_TICK();

    /** CYP3A4 activity in a body that has eaten no grapefruit, and the clamp on the index. */
    public static final float CYP3A4_NORMAL = ModelConstants.CYP3A4_NORMAL();
    public static final float CYP3A4_MIN = ModelConstants.CYP3A4_MIN();
    public static final float CYP3A4_MAX = ModelConstants.CYP3A4_MAX();

    /** The four naringin steps, and the CYP3A4 activity each one leaves behind. */
    public static final float NARINGIN_CYP_STEP_1 = ModelConstants.NARINGIN_CYP_STEP_1();
    public static final float NARINGIN_CYP_STEP_2 = ModelConstants.NARINGIN_CYP_STEP_2();
    public static final float NARINGIN_CYP_STEP_3 = ModelConstants.NARINGIN_CYP_STEP_3();
    public static final float NARINGIN_CYP_STEP_4 = ModelConstants.NARINGIN_CYP_STEP_4();

    public static final float CYP3A4_AT_STEP_1 = ModelConstants.CYP3A4_AT_STEP_1();
    public static final float CYP3A4_AT_STEP_2 = ModelConstants.CYP3A4_AT_STEP_2();
    public static final float CYP3A4_AT_STEP_3 = ModelConstants.CYP3A4_AT_STEP_3();
    public static final float CYP3A4_AT_STEP_4 = ModelConstants.CYP3A4_AT_STEP_4();

    // ---------------------------------------------------------------- ethanol

    /** The maximum ethanol index (0..1.0). */
    public static final float ETHANOL_CAP = ModelConstants.ETHANOL_CAP();

    public static final int ETHANOL_METABOLISM_TICKS = ModelConstants.ETHANOL_METABOLISM_TICKS();
    public static final float ETHANOL_DECAY_PER_TICK = ModelConstants.ETHANOL_DECAY_PER_TICK();

    // ---------------------------------------------------------------- blood glucose

    /** Blood glucose of a healthy fasting body, in mmol/L, and the middle of the reference range. */
    public static final float GLUCOSE_NORMAL = ModelConstants.GLUCOSE_NORMAL();

    /** The clinical reference range for blood glucose, in mmol/L. */
    public static final float GLUCOSE_SAFE_LOW = ModelConstants.GLUCOSE_SAFE_LOW();
    public static final float GLUCOSE_SAFE_HIGH = ModelConstants.GLUCOSE_SAFE_HIGH();

    /** Hard clamp for the model. */
    public static final float GLUCOSE_MIN = ModelConstants.GLUCOSE_MIN();
    public static final float GLUCOSE_MAX = ModelConstants.GLUCOSE_MAX();

    /** Where a fasted body levels off, two in-game days below the normal value. */
    public static final float GLUCOSE_FASTING_FLOOR = ModelConstants.GLUCOSE_FASTING_FLOOR();

    /** Above this the insulin index climbs steeply. */
    public static final float GLUCOSE_ELEVATED = ModelConstants.GLUCOSE_ELEVATED();

    /** The three hypoglycaemia thresholds: mining fatigue, then weakness, then magic damage. */
    public static final float GLUCOSE_HYPO_FATIGUE = ModelConstants.GLUCOSE_HYPO_FATIGUE();
    public static final float GLUCOSE_HYPO_WEAKNESS = ModelConstants.GLUCOSE_HYPO_WEAKNESS();
    public static final float GLUCOSE_HYPO_DAMAGE = ModelConstants.GLUCOSE_HYPO_DAMAGE();

    // ---------------------------------------------------------------- insulin

    /** The insulin index of a healthy fasting body. */
    public static final float INSULIN_NORMAL = ModelConstants.INSULIN_NORMAL();

    /** The most insulin a body can carry. */
    public static final float INSULIN_CAP = ModelConstants.INSULIN_CAP();

    /** The most injected insulin aspart a body can carry, and one injection's worth. */
    public static final float INSULIN_ASPART_CAP = ModelConstants.INSULIN_ASPART_CAP();
    public static final float INSULIN_ASPART_PER_INJECTION = ModelConstants.INSULIN_ASPART_PER_INJECTION();
    public static final int INSULIN_ASPART_METABOLISM_TICKS = ModelConstants.INSULIN_ASPART_METABOLISM_TICKS();
    public static final float INSULIN_ASPART_DECAY_PER_TICK = ModelConstants.INSULIN_ASPART_DECAY_PER_TICK();

    // ---------------------------------------------------------------- glucose from food

    /** What one serving of each kind of food adds to blood glucose, in mmol/L. */
    public static final float GLUCOSE_PER_PLANT_FOOD = ModelConstants.GLUCOSE_PER_PLANT_FOOD();
    public static final float GLUCOSE_PER_BREAD = ModelConstants.GLUCOSE_PER_BREAD();
    public static final float GLUCOSE_PER_RAW_MEAT = ModelConstants.GLUCOSE_PER_RAW_MEAT();
    public static final float GLUCOSE_PER_COOKED_MEAT = ModelConstants.GLUCOSE_PER_COOKED_MEAT();
    public static final float GLUCOSE_PER_SWEET_DRINK = ModelConstants.GLUCOSE_PER_SWEET_DRINK();

    /** What one assembled dish - a stew, a sandwich, a plate of pasta - adds: a mixed plate. */
    public static final float GLUCOSE_PER_MIXED_DISH = ModelConstants.GLUCOSE_PER_MIXED_DISH();

    // ================================================================== the reference ranges

    /**
     * One tracked mineral and the thresholds a real blood test would report.
     *
     * The bands, the unit, the clamp and the formatting all come from the model; this only carries
     * them across. Kotlin hangs its own {@code Mineral} enum on one of these per case, which is what
     * lets `/aliment status` and the symptom table read the reference range without knowing that
     * Scala exists.
     */
    public static final class MineralSpec {

        private final ModelMineral mineral;

        private MineralSpec(ModelMineral mineral) {
            this.mineral = mineral;
        }

        /** The unit the value is measured in: mmol/L for the electrolytes, umol/L for iodine. */
        public String getUnit() {
            return this.mineral.getUnit();
        }

        /** The healthy concentration, and the set point the model regulates towards. */
        public float getNormal() {
            return this.mineral.getNormal();
        }

        /** Inside this band the mineral causes no symptoms at all. */
        public float getSafeLow() {
            return this.mineral.getSafeLow();
        }

        public float getSafeHigh() {
            return this.mineral.getSafeHigh();
        }

        /** Past these the symptoms become serious, and sometimes lethal. */
        public float getSevereLow() {
            return this.mineral.getSevereLow();
        }

        public float getSevereHigh() {
            return this.mineral.getSevereHigh();
        }

        /** Hard limits, so a runaway value can never reach nonsense. */
        public float getMin() {
            return this.mineral.getMin();
        }

        public float getMax() {
            return this.mineral.getMax();
        }

        public float clamp(float value) {
            return this.mineral.clamp(value);
        }

        /** Distance outside the reference range, in this mineral's own units; 0 while inside it. */
        public float deviation(float value) {
            return this.mineral.deviation(value);
        }

        /** The same distance as a fraction of normal, so minerals can be ranked against each other. */
        public float relativeDeviation(float value) {
            return this.mineral.relativeDeviation(value);
        }

        /** -1 for a deficit, 1 for an excess, 0 while the value is inside the reference range. */
        public int direction(float value) {
            return this.mineral.direction(value);
        }

        /** True past {@code severeLow} or {@code severeHigh}. */
        public boolean isSevere(float value) {
            return this.mineral.isSevere(value);
        }

        /** The value as `/aliment status` prints it: iodine needs more decimals than sodium. */
        public String display(float value) {
            return this.mineral.display(value);
        }
    }

    /**
     * The model's minerals, by name.
     *
     * Keyed by name rather than by ordinal so that reordering Kotlin's enum cannot silently pair a
     * mineral with another one's reference range.
     */
    private static final Map<String, MineralSpec> SPECS = Map.of(
            "SODIUM", new MineralSpec(MineralRanges.SODIUM()),
            "POTASSIUM", new MineralSpec(MineralRanges.POTASSIUM()),
            "MAGNESIUM", new MineralSpec(MineralRanges.MAGNESIUM()),
            "CHLORIDE", new MineralSpec(MineralRanges.CHLORIDE()),
            "CALCIUM", new MineralSpec(MineralRanges.CALCIUM()),
            "IODINE", new MineralSpec(MineralRanges.IODINE()),
            "VITAMIN_C", new MineralSpec(MineralRanges.VITAMIN_C()));

    /** The reference range of the mineral Kotlin calls {@code mineral}. */
    public static MineralSpec spec(Mineral mineral) {
        MineralSpec spec = SPECS.get(mineral.name());
        if (spec == null) {
            throw new IllegalArgumentException("the model tracks no mineral called " + mineral.name());
        }
        return spec;
    }

    // ================================================================== the fixed points

    /** The mediator levels a healthy player sits at, and the model's fixed point. */
    public static Mediators restingMediators() {
        return fromModel(MediatorLevels.RESTING());
    }

    /** A body with no inflammatory response at all, i.e. one that is immunosuppressed. */
    public static Mediators calmMediators() {
        return fromModel(MediatorLevels.CALM());
    }

    /** Every electrolyte at its normal concentration. */
    public static Electrolytes healthyElectrolytes() {
        return fromModel(ElectrolyteDefaults.HEALTHY());
    }

    /** Iodine at its normal concentration. */
    public static TraceElements healthyTraceElements() {
        return fromModel(TraceElementDefaults.HEALTHY());
    }

    /** What a healthy player looks like, and what a new attachment is initialised with. */
    public static AlimentData healthy() {
        return new AlimentData(
                restingMediators(),
                0f,
                0f,
                WATER_START,
                healthyElectrolytes(),
                healthyTraceElements(),
                0f,
                0f,
                TEMPERATURE_NORMAL,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                false,
                0f,
                0f,
                0f,
                0f,
                GLUCOSE_NORMAL,
                INSULIN_NORMAL,
                0f,
                CYP3A4_NORMAL);
    }

    // ================================================================== derived values

    /** Inflammation index, 0..100, as the model's weighted sum of the mediators. */
    public static float inflammation(
            float histamine, float prostaglandin, float leukotriene, float cytokine, float bradykinin) {
        return new ModelMediators(histamine, prostaglandin, leukotriene, cytokine, bradykinin)
                .getInflammation();
    }

    /** How far the worst electrolyte is outside its reference range, as a fraction of normal. */
    public static float worstImbalance(
            float sodium, float potassium, float magnesium, float chloride, float calcium) {
        return new ModelElectrolytes(sodium, potassium, magnesium, chloride, calcium).getWorstImbalance();
    }

    /** Combined pathogen load. */
    public static float pathogenLoad(float bacteria, float virus) {
        return Physiology.pathogenLoad(bacteria, virus);
    }

    public static boolean isSymptomatic(float load) {
        return Physiology.isSymptomatic(load);
    }

    public static boolean isImmuneStorm(float inflammation) {
        return Physiology.isImmuneStorm(inflammation);
    }

    public static boolean isImmunosuppressed(float inflammation) {
        return Physiology.isImmunosuppressed(inflammation);
    }

    public static boolean isSevereInfection(float load) {
        return Physiology.isSevereInfection(load);
    }

    public static float severity(float load) {
        return Physiology.severity(load);
    }

    public static boolean isOverhydrated(float water) {
        return Physiology.isOverhydrated(water);
    }

    public static boolean isDehydrated(float water) {
        return Physiology.isDehydrated(water);
    }

    public static int thirstCells(float water) {
        return Physiology.thirstCells(water);
    }

    public static boolean isFebrile(float temperature) {
        return Physiology.isFebrile(temperature);
    }

    public static boolean isHypothermic(float temperature) {
        return Physiology.isHypothermic(temperature);
    }

    public static boolean hasThermalStress(float temperature) {
        return Physiology.hasThermalStress(temperature);
    }

    public static int thermalTier(float temperature) {
        return Physiology.thermalTier(temperature);
    }

    /** The two mandrake alkaloids, as one number. */
    public static float anticholinergicLoad(AlimentData data) {
        return Physiology.anticholinergicLoad(toModel(data));
    }

    /** The temperature the alkaloids are driving the body towards, as an offset from normal. */
    public static float anticholinergicFever(AlimentData data) {
        return Physiology.anticholinergicFever(toModel(data));
    }

    /** True when the alkaloids have blurred the player's sight. */
    public static boolean isVisionBlurred(AlimentData data) {
        return Physiology.isVisionBlurred(toModel(data));
    }

    /** How far into the trip the player is: 0 nothing, 1 outlines, 2 colour, 3 warp, 4 hard warp. */
    public static int psilocinTier(AlimentData data) {
        return Physiology.psilocinTier(toModel(data));
    }

    /** The temperature a heavy trip is driving the body towards, as an offset from normal. */
    public static float psilocinFever(AlimentData data) {
        return Physiology.psilocinFever(toModel(data));
    }

    // ================================================================== symptom magnitudes

    /** Magic damage a severe infection does in one two-second pass, and 0 while it is not severe. */
    public static float sepsisDamage(float load) {
        return Physiology.sepsisDamage(load);
    }

    /** Chance that one shake roll actually shakes the camera. */
    public static float shakeChance(AlimentData data) {
        return Physiology.shakeChance(toModel(data));
    }

    /** Extra food exhaustion while the body is unwell; a multiplier, so 1 is "nothing wrong". */
    public static float exhaustionMultiplier(AlimentData data) {
        return Physiology.exhaustionMultiplier(toModel(data));
    }

    /** How much slower this body mines, as a positive fraction; 0 when nothing is wrong. */
    public static float miningPenalty(AlimentData data) {
        return Physiology.miningPenalty(toModel(data));
    }

    /** The temperature the surroundings are dragging the body towards, before thermoregulation. */
    public static float environmentTemperature(
            float biomeTemperature, boolean wet, boolean powderSnow, boolean lava, boolean fire) {
        return Physiology.environmentTemperature(biomeTemperature, wet, powderSnow, lava, fire);
    }

    /** Extra food exhaustion from a shiver or a fever, per tier of thermal stress. */
    public static float thermalExhaustion(int tier) {
        return Physiology.thermalExhaustion(tier);
    }

    /** Extra food exhaustion from an immune storm, which burns energy fighting itself. */
    public static float stormExhaustion() {
        return Physiology.stormExhaustion();
    }

    // ================================================================== the model

    /** Advances a player's physiology by a single tick, in neutral surroundings. */
    public static AlimentData tick(AlimentData data) {
        return fromModel(Physiology.tick(toModel(data)));
    }

    /** Advances a player's physiology by a single tick, towards {@code ambient} degrees. */
    public static AlimentData tick(AlimentData data, float ambient) {
        return fromModel(Physiology.tick(toModel(data), ambient));
    }

    /** How well an inflammation level fights pathogens, in 0..1. */
    public static float immuneCompetence(float inflammation) {
        return Physiology.immuneCompetence(inflammation);
    }

    /** Drug concentration as a multiple of its effective concentration, capped. */
    public static float suppression(float concentration, float effective) {
        return Physiology.suppression(concentration, effective);
    }

    /** The core temperature the body is currently aiming for. */
    public static float targetTemperature(AlimentData data, float ambient) {
        return Physiology.targetTemperature(toModel(data), ambient);
    }

    /**
     * Raises or lowers the fever so that the body *peaks* at {@code degrees} Celsius, whatever the
     * infection is already doing.
     */
    public static AlimentData induceFever(AlimentData data, float degrees, float ambient) {
        return fromModel(Physiology.induceFever(toModel(data), degrees, ambient));
    }

    /** Adds a pathogen seed, used by the infection sources. */
    public static AlimentData seed(AlimentData data, float bacteria, float virus) {
        return fromModel(Physiology.seed(toModel(data), bacteria, virus));
    }

    /** Adds salicin, capped. */
    public static AlimentData dose(AlimentData data, float amount) {
        return fromModel(Physiology.dose(toModel(data), amount));
    }

    /** Adds dexamethasone, capped. */
    public static AlimentData inject(AlimentData data, float amount) {
        return fromModel(Physiology.inject(toModel(data), amount));
    }

    /** Adds water from a drink. */
    public static AlimentData drink(AlimentData data, float amount) {
        return fromModel(Physiology.drink(toModel(data), amount));
    }

    /** Adds salt, in mmol/L of serum sodium and chloride. */
    public static AlimentData salt(
            AlimentData data, float sodium, float chloride, float magnesium, float calcium) {
        return fromModel(Physiology.salt(toModel(data), sodium, chloride, magnesium, calcium));
    }

    /** Adds iodine, in umol/L, which the body only gets from food - kelp, in this mod. */
    public static AlimentData iodine(AlimentData data, float amount) {
        return fromModel(Physiology.iodine(toModel(data), amount));
    }

    /** Adds vitamin C, in umol/L, from plant foods. */
    public static AlimentData vitaminC(AlimentData data, float amount) {
        return fromModel(Physiology.vitaminC(toModel(data), amount));
    }

    /** Adds the two tropane alkaloids a mandrake carries, capped. */
    public static AlimentData anticholinergic(AlimentData data, float scopolamine, float atropine) {
        return fromModel(Physiology.anticholinergic(toModel(data), scopolamine, atropine));
    }

    /** Adds what a raw gymnopilus carries: a dose of psilocybin and a dose of psilocin. */
    public static AlimentData mushroom(AlimentData data, float psilocybin, float psilocin) {
        return fromModel(Physiology.mushroom(toModel(data), psilocybin, psilocin));
    }

    /** Adds ephedrine, capped. */
    public static AlimentData addEphedrine(AlimentData data, float amount) {
        return fromModel(Physiology.addEphedrine(toModel(data), amount));
    }

    /** True if ephedrine is above the threshold for Haste I. */
    public static boolean hasHasteFromEphedrine(AlimentData data) {
        return Physiology.hasHasteFromEphedrine(toModel(data));
    }

    /** True if ephedrine is high enough to prevent the player from sleeping. */
    public static boolean isTooStimulatedToSleep(AlimentData data) {
        return Physiology.isTooStimulatedToSleep(toModel(data));
    }

    /** Adds berberine, capped. */
    public static AlimentData addBerberine(AlimentData data, float amount) {
        return fromModel(Physiology.addBerberine(toModel(data), amount));
    }

    /** Adds glycyrrhizin, capped. */
    public static AlimentData addGlycyrrhizin(AlimentData data, float amount) {
        return fromModel(Physiology.addGlycyrrhizin(toModel(data), amount));
    }

    /**
     * Adds naringin from grapefruit, capped.
     *
     * The CYP3A4 index follows on the next tick rather than here, so that the step function stays in
     * one place.
     */
    public static AlimentData addNaringin(AlimentData data, float amount) {
        return fromModel(Physiology.addNaringin(toModel(data), amount));
    }

    /** The CYP3A4 activity a given naringin load leaves the liver at, 0..100. */
    public static float cyp3a4For(float naringin) {
        return Physiology.cyp3a4For(naringin);
    }

    /** Adds ethanol, capped. */
    public static AlimentData addEthanol(AlimentData data, float amount) {
        return fromModel(Physiology.addEthanol(toModel(data), amount));
    }

    /** Adds what one serving of food does to blood glucose, in mmol/L, capped. */
    public static AlimentData addGlucose(AlimentData data, float amount) {
        return fromModel(Physiology.addGlucose(toModel(data), amount));
    }

    /** Adds insulin aspart, the injected fast-acting analogue, capped. */
    public static AlimentData injectInsulin(AlimentData data, float amount) {
        return fromModel(Physiology.injectInsulin(toModel(data), amount));
    }

    /** How far into a hypoglycaemic crash this blood glucose is: 0 nothing, 1, 2, 3. */
    public static int hypoglycemiaTier(AlimentData data) {
        return Physiology.hypoglycemiaTier(data.getGlucose());
    }

    /** Magic damage a hypoglycaemic crash does in one two-second pass, and 0 above the threshold. */
    public static float hypoglycemiaDamage(AlimentData data) {
        return Physiology.hypoglycemiaDamage(data.getGlucose());
    }

    /** The blood glucose as the meter prints it: one decimal place, the way a real one reads. */
    public static String glucoseReading(AlimentData data) {
        return Physiology.glucoseReading(data.getGlucose());
    }

    /**
     * The same format for a value that is not the body's current glucose, which is what a bloodied
     * test strip carries: the reading it took when the drop was caught.
     */
    public static String glucoseReading(float glucose) {
        return Physiology.glucoseReading(glucose);
    }

    // ================================================================== the conversion
    //
    // The only place a Kotlin `AlimentData` becomes the model's `ModelState` or back. Everything
    // above funnels through these pairs, so there is exactly one definition of what the two
    // representations mean.

    private static ModelState toModel(AlimentData data) {
        ModelDrugs drugs = new ModelDrugs(
                data.getSalicin(),
                data.getDexamethasone(),
                data.getScopolamine(),
                data.getAtropine(),
                data.getPsilocybin(),
                data.getPsilocin(),
                data.getEphedrine(),
                data.getBerberine(),
                data.getGlycyrrhizin(),
                data.getNaringin(),
                data.getEthanol(),
                data.getInsulinAspart());

        // `AlimentData` keeps the enzyme indices as flat scalars, the same way it keeps the drugs;
        // the grouping into a model-side type happens here and nowhere else. `ModelEnzymes` is a
        // Scala type, so this has to stay private - a public signature naming it would put the
        // `scala.Product` problem back into Kotlin.
        ModelEnzymes enzymes = new ModelEnzymes(data.getCyp3a4());

        return new ModelState(
                toModel(data.getMediators()),
                data.getBacteria(),
                data.getVirus(),
                data.getWater(),
                toModel(data.getElectrolytes()),
                toModel(data.getTraceElements()),
                data.getTemperature(),
                data.getPyrogen(),
                data.getImmuneActive(),
                drugs,
                enzymes,
                data.getGlucose(),
                data.getInsulin());
    }

    private static AlimentData fromModel(ModelState state) {
        ModelDrugs drugs = state.getDrugs();
        return new AlimentData(
                fromModel(state.getMediators()),
                state.getBacteria(),
                state.getVirus(),
                state.getWater(),
                fromModel(state.getElectrolytes()),
                fromModel(state.getTraceElements()),
                drugs.getSalicin(),
                drugs.getDexamethasone(),
                state.getTemperature(),
                state.getPyrogen(),
                drugs.getScopolamine(),
                drugs.getAtropine(),
                drugs.getPsilocybin(),
                drugs.getPsilocin(),
                drugs.getEphedrine(),
                state.getImmuneActive(),
                drugs.getBerberine(),
                drugs.getGlycyrrhizin(),
                drugs.getNaringin(),
                drugs.getEthanol(),
                state.getGlucose(),
                state.getInsulin(),
                drugs.getInsulinAspart(),
                state.getEnzymes().getCyp3a4());
    }

    private static ModelMediators toModel(Mediators mediators) {
        return new ModelMediators(
                mediators.getHistamine(),
                mediators.getProstaglandin(),
                mediators.getLeukotriene(),
                mediators.getCytokine(),
                mediators.getBradykinin());
    }

    private static Mediators fromModel(ModelMediators mediators) {
        return new Mediators(
                mediators.getHistamine(),
                mediators.getProstaglandin(),
                mediators.getLeukotriene(),
                mediators.getCytokine(),
                mediators.getBradykinin());
    }

    private static ModelElectrolytes toModel(Electrolytes electrolytes) {
        return new ModelElectrolytes(
                electrolytes.getSodium(),
                electrolytes.getPotassium(),
                electrolytes.getMagnesium(),
                electrolytes.getChloride(),
                electrolytes.getCalcium());
    }

    private static Electrolytes fromModel(ModelElectrolytes electrolytes) {
        return new Electrolytes(
                electrolytes.getSodium(),
                electrolytes.getPotassium(),
                electrolytes.getMagnesium(),
                electrolytes.getChloride(),
                electrolytes.getCalcium());
    }

    private static ModelTraceElements toModel(TraceElements traceElements) {
        return new ModelTraceElements(traceElements.getIodine(), traceElements.getVitaminC());
    }

    private static TraceElements fromModel(ModelTraceElements traceElements) {
        return new TraceElements(traceElements.getIodine(), traceElements.getVitaminC());
    }
}
