package com.github.kusa233.aliment.physiology

import com.github.kusa233.aliment.advancement.AlimentAdvancements
import com.github.kusa233.aliment.registry.AlimentEffects
import com.github.kusa233.aliment.registry.Registration
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.Holder
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import kotlin.math.abs

/** One symptom set: what a mineral imbalance does to a player for the next `ticks` ticks. */
private typealias Symptom = (ServerPlayer, Int) -> Unit

/**
 * Turns [AlimentData] into things the player can feel.
 *
 * All symptoms are deliberately mild; the point is that an untreated infection is a slow drain
 * rather than an instant death sentence.
 *
 * Everything mineral goes through one table ([MINERALS]) with four thresholds per entry, so an
 * excess and a deficit of the same substance are always described side by side and neither can be
 * added without the other being considered. Body temperature gets its own block at the end.
 */
object AlimentSymptoms {

    private val MINING_MODIFIER_ID = Registration.id("infection_mining")

    /**
     * The screen effects that go with a fever and with hypothermia. The assets live in
     * `assets/aliment/post_effect/<name>.json`, and the client only ever applies what the server
     * asks it to through `ServerPlayer.addPostEffect`, so there is nothing to do client-side.
     *
     * The fever has two of them, and they stack: [HEAT_HAZE] from [AlimentData.FEVER_MILD], and
     * the motion blur on top of it from [AlimentData.FEVER_SEVERE].
     */
    val HEAT_HAZE: Identifier = Registration.id("heat_haze")
    val HEAT_BLUR: Identifier = Registration.id("heat_blur")
    val COLD_SHIVER: Identifier = Registration.id("cold_shiver")

    /**
     * The mandrake blur, which is a heavier version of [HEAT_BLUR] and has its own id so that a
     * fever and an alkaloid overdose can both be on the screen at once.
     */
    val ANTICHOLINERGIC_BLUR: Identifier = Registration.id("anticholinergic_blur")

    /**
     * The four stages of a psilocin trip: `psilocinTier` is the index into [PSILOCIN_EFFECTS] plus
     * one, and exactly one of them is ever on the screen. They are stages rather than layers because
     * each one is the whole effect at a higher intensity - the outlines and the colour are still
     * there at stage four, just bent along with everything else - and they stack with the fever and
     * the mandrake blur like any other screen effect.
     */
    val PSILOCIN_OUTLINE: Identifier = Registration.id("psilocin_outline")
    val PSILOCIN_COLOUR: Identifier = Registration.id("psilocin_colour")
    val PSILOCIN_WARP: Identifier = Registration.id("psilocin_warp")
    val PSILOCIN_STORM: Identifier = Registration.id("psilocin_storm")

    private val PSILOCIN_EFFECTS: List<Identifier> = listOf(
        PSILOCIN_OUTLINE,
        PSILOCIN_COLOUR,
        PSILOCIN_WARP,
        PSILOCIN_STORM,
    )

    /**
     * How often the game rolls for a brief camera shake, and how long one lasts once it triggers.
     *
     * These are Minecraft's cadence rather than the model's numbers - *when* to poke the client, not
     * *how likely* the shiver is - so they stay here. The probability itself is in Scala.
     */
    const val SHAKE_INTERVAL_TICKS = 300
    const val SHAKE_DURATION_TICKS = 16

    /** How often the storm, mineral and thermal effects are refreshed. */
    private const val EFFECT_INTERVAL_TICKS = 40

    /**
     * True while the physiology leaves this player completely alone.
     *
     * Creative mode is not playing the game, so nothing is simulated, nothing is applied and the body
     * is left exactly as it was: a player who goes creative while ill stops being ill *visibly* on the
     * same tick, and the infection is still there if they go back to survival. Freezing rather than
     * resetting is deliberate - `creative` should not be a cure the player can use mid-fight, and it
     * is the only reading of "the state stops updating" that does not silently throw data away.
     */
    @JvmStatic
    fun isFrozen(player: Player): Boolean = player.isCreative

    /**
     * True while the player's blood glucose is left exactly where it is: peaceful difficulty.
     *
     * Peaceful is the one difficulty that promises nothing can kill the player, and the sugar curve
     * is the only thing in this mod that takes hitpoints away on its own - a hypoglycaemic crash
     * hurts you with magic damage, which no difficulty setting intercepts. Rather than soften the
     * crash, the whole curve is switched off there: the fasting drain stops, the body's own insulin
     * and any injected aspart stop disposing, and food stops adding.
     *
     * It *holds* the level rather than resetting it, for the same reason [isFrozen] freezes rather
     * than clears - Peaceful should not be a cure the player can flick on to top themselves up, and
     * the sugar they had is the sugar they find again on the way back out. What follows from that,
     * and is worth knowing before it surprises someone, is that a player who enters Peaceful already
     * crashing stays crashing and cannot eat their way out of it: both the fix and the cause are
     * turned off together.
     */
    @JvmStatic
    fun isGlucoseHeld(player: Player): Boolean = player.level().difficulty == Difficulty.PEACEFUL

    /**
     * Runs the whole system for every online player, once per server tick.
     *
     * The physiology only advances while the player is online, which keeps the model predictable.
     */
    fun initialize() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            for (player in server.playerList.players) {
                this.tick(player)
            }
        }
    }

    /**
     * Runs one tick of the whole system for [player], or nothing at all while [isFrozen].
     */
    fun tick(player: ServerPlayer) {
        // The finger a microneedle pricked only bleeds for a few seconds, and nothing else counts it
        // down. It runs for a frozen body too: it is a property of the player's hand, not of the
        // model, so going creative must not leave a player bleeding forever.
        val runtime = player.getAttachedOrCreate(AlimentAttachments.RUNTIME) { AlimentRuntime() }
        if (runtime.bleedingTicks > 0) {
            runtime.bleedingTicks--
        }

        if (isFrozen(player)) {
            // Anything already on the screen has to come off: a frozen body must not keep a fever
            // shimmer, a motion blur or a shiver running. The shake stops by itself, because the
            // client only ever shakes when the counter below moves.
            clearPostEffects(player)
            return
        }

        val before = player.getAttachedOrCreate(AlimentAttachments.DATA)

        var data = AlimentPhysiology.tick(before, ambientTemperature(player), isGlucoseHeld(player))
        // Both of these roll once a second, and both are contagion: something touched the player,
        // or the player's own immune system stopped keeping its own flora in check.
        data = AlimentInfection.rollContact(player, data, runtime)
        data = AlimentInfection.rollImmunosuppression(player, data, runtime)
        player.setAttached(AlimentAttachments.DATA, data)

        this.applyMiningPenalty(player, data)
        this.applyOngoingEffects(player, data, runtime)
        this.rollShake(player, data, runtime)
        this.applyPostEffects(player, data)
        this.syncClient(player, data, runtime)
    }

    // ------------------------------------------------------------------ environment

    /**
     * The temperature the surroundings are dragging the player towards, on the body's own Celsius
     * scale, before thermoregulation. This reads the world; the arithmetic is the model's.
     */
    fun ambientTemperature(player: ServerPlayer): Float = environmentTemperature(
        biomeTemperature = player.level().getBiome(player.blockPosition()).value().baseTemperature,
        wet = player.isInWaterOrRain,
        powderSnow = player.isInPowderSnow,
        lava = player.isInLava,
        fire = player.isOnFire,
    )

    /** The pure half of [ambientTemperature], so the environment can be tested without a world. */
    fun environmentTemperature(
        biomeTemperature: Float,
        wet: Boolean = false,
        powderSnow: Boolean = false,
        lava: Boolean = false,
        fire: Boolean = false,
    ): Float = AlimentModelBridge.environmentTemperature(biomeTemperature, wet, powderSnow, lava, fire)

    // ------------------------------------------------------------------ symptoms

    private fun applyMiningPenalty(player: ServerPlayer, data: AlimentData) {
        val instance = player.getAttribute(Attributes.BLOCK_BREAK_SPEED) ?: return

        // How much slower this body mines is a model number; see src/main/scala.
        val penalty = AlimentModelBridge.miningPenalty(data).toDouble()

        if (penalty <= 0.0) {
            if (instance.hasModifier(MINING_MODIFIER_ID)) {
                instance.removeModifier(MINING_MODIFIER_ID)
            }
            return
        }
        instance.addOrUpdateTransientModifier(
            AttributeModifier(MINING_MODIFIER_ID, -penalty, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
        )
    }

    /**
     * The multiplier applied to every source of food exhaustion while the player is unwell, so
     * that hunger drains a little faster. Used by `PlayerMixin`. The numbers are the model's.
     */
    @JvmStatic
    fun exhaustionMultiplier(player: Player): Float {
        if (isFrozen(player)) {
            return 1f
        }
        val data = player.getAttachedOrElse(AlimentAttachments.DATA, AlimentData.HEALTHY)
        return AlimentModelBridge.exhaustionMultiplier(data)
    }

    /**
     * True while the player's stimulant load is too high for sleep. Used by `PlayerSleepMixin`.
     *
     * A frozen (creative) body is left alone like everything else, so a player cannot dodge the
     * restriction by switching game mode.
     */
    @JvmStatic
    fun isTooStimulatedToSleep(player: Player): Boolean {
        if (isFrozen(player)) {
            return false
        }
        val data = player.getAttachedOrElse(AlimentAttachments.DATA, AlimentData.HEALTHY)
        return AlimentModelBridge.isTooStimulatedToSleep(data)
    }

    /**
     * Everything that has to be refreshed rather than set once: sepsis, the storm, the water, the
     * six minerals, and the body temperature.
     *
     * It runs on the [EFFECT_INTERVAL_TICKS] cadence - one pass every two seconds - and re-applies
     * each effect for three intervals, so an effect that stops being warranted simply lapses instead
     * of having to be removed.
     *
     * Every effect here is a vanilla one except the two the last block hands out: the condition
     * indicators are the mod's own, they are the only things in the mod that have an icon drawn for
     * them, and they are the one case that does *not* lapse on the three-interval rule - see
     * [indicator] for why.
     */
    private fun applyOngoingEffects(player: ServerPlayer, data: AlimentData, runtime: AlimentRuntime) {
        if (runtime.shakeCooldown % EFFECT_INTERVAL_TICKS != 0) {
            return
        }
        val ticks = EFFECT_INTERVAL_TICKS * 3

        // --- a severe infection hurts the host directly, and this is the one symptom that can
        //     kill on its own.
        val sepsis = sepsisDamage(data)
        if (sepsis > 0f) {
            player.hurtServer(player.level(), player.damageSources().magic(), sepsis)
        }

        // --- immune storm: the response itself hurts the host
        if (data.isImmuneStorm) {
            player.addEffect(MobEffectInstance(MobEffects.WEAKNESS, ticks, 0))
            player.causeFoodExhaustion(AlimentModelBridge.stormExhaustion())
        }

        // --- hypoglycaemia: the brain has no fuel but glucose, so this is the one thing the body
        //     does about it that the player cannot ignore. The tier is the model's, and it is the
        //     three thresholds - dragging, weak, and finally disoriented enough to be hurt by it.
        val hypo = data.hypoglycemiaTier
        if (hypo >= 1) {
            player.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, ticks, 0))
        }
        if (hypo >= 2) {
            player.addEffect(MobEffectInstance(MobEffects.WEAKNESS, ticks, 0))
        }
        val hypoDamage = data.hypoglycemiaDamage
        if (hypoDamage > 0f) {
            player.hurtServer(player.level(), player.damageSources().magic(), hypoDamage)
        }

        // --- water
        if (data.isOverhydrated) {
            // Too much water: weakness, and at the extreme, confusion.
            player.addEffect(MobEffectInstance(MobEffects.WEAKNESS, ticks, 0))
            if (data.water > AlimentData.WATER_VISIBLY_OVERHYDRATED) {
                player.addEffect(MobEffectInstance(MobEffects.NAUSEA, ticks, 0))
            }
        } else if (data.isDehydrated && data.water < AlimentData.WATER_SEVERELY_DEHYDRATED) {
            player.addEffect(MobEffectInstance(MobEffects.HUNGER, ticks, 0))
        }

        // --- the six minerals, each judged on both sides of its reference range
        for (rule in MINERALS) {
            rule.symptomFor(data)?.invoke(player, ticks)
        }

        // --- ephedrine stimulant: provides Haste I when ephedrine > 1
        if (data.ephedrine > AlimentData.EPHEDRINE_HASTE_THRESHOLD) {
            player.addEffect(MobEffectInstance(MobEffects.HASTE, ticks, 0))
        }

        // --- ethanol: intoxication / drunkenness
        if (data.ethanol >= 0.70f) {
            player.addEffect(MobEffectInstance(MobEffects.NAUSEA, ticks, 1))
            player.addEffect(MobEffectInstance(MobEffects.SLOWNESS, ticks, 1))
        } else if (data.ethanol >= 0.35f) {
            player.addEffect(MobEffectInstance(MobEffects.NAUSEA, ticks, 0))
            player.addEffect(MobEffectInstance(MobEffects.SLOWNESS, ticks, 0))
        }

        // --- body temperature
        applyThermalEffects(player, data, ticks)
    }

    /**
     * Magic damage a severe infection does in one two-second pass, and 0 while it is not severe.
     *
     * Public because a `FakePlayer` is deliberately invulnerable, so this is the half of "severe
     * infections hurt" that can be tested headlessly; applying it is the one `hurtServer` call below.
     */
    fun sepsisDamage(data: AlimentData): Float = AlimentModelBridge.sepsisDamage(data.pathogenLoad)

    /**
     * Fever and hypothermia both produce weakness and mining fatigue, with the strength stepping
     * up at tier 2. Hypothermia adds slowness on top; a cold body is a slow body, and the shiver
     * itself is handled by [rollShake].
     *
     * The visual half - the shimmer at the edges of the screen - is a post effect, applied by
     * [applyPostEffects] rather than here.
     */
    private fun applyThermalEffects(player: ServerPlayer, data: AlimentData, ticks: Int) {
        if (data.temperature > 40.0f) {
            AlimentAdvancements.award(player, AlimentAdvancements.EXTREME_FEVER)
        }

        // The two indicators go before the early return below, because switching them off is one of
        // the things this has to do and `tier == 0` is exactly the case where they must come off.
        // They are not symptoms: they are the HUD reading the same thresholds the symptoms use.
        indicator(player, AlimentEffects.FEVER, data.feverGrade)
        indicator(player, AlimentEffects.PAIN, if (data.hasPain) 1 else 0)

        val tier = data.thermalTier
        if (tier == 0) {
            return
        }
        val amplifier = abs(tier) - 1

        player.addEffect(MobEffectInstance(MobEffects.WEAKNESS, ticks, amplifier))
        player.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, ticks, amplifier))
        if (tier < 0) {
            player.addEffect(MobEffectInstance(MobEffects.SLOWNESS, ticks, amplifier))
        }

        // A fever is a furnace and a shiver is a workout; both burn food.
        player.causeFoodExhaustion(AlimentModelBridge.thermalExhaustion(tier))
    }

    /**
     * Shows, changes or clears one of the two indicator effects, from its grade: 0 means "not this",
     * anything above is the amplifier plus one, which is the roman numeral the player reads.
     *
     * They are given an [MobEffectInstance.INFINITE_DURATION] and taken off by hand rather than left
     * to run out. A duration would put a countdown on the HUD, and a countdown answers the wrong
     * question: what ends a fever is the body cooling down, not a timer, and a player watching `0:06`
     * tick down would be told their temperature is about to settle when it is not. So the icon lasts
     * exactly as long as the condition does, and `visible = false` keeps the swirl off the world -
     * this is a line on the HUD, not a cloud around the player.
     *
     * Re-applying every pass would be the other way to do it, and it flickers: vanilla restarts the
     * blend-in each time an effect is added, so a two-second cadence makes the icon pulse.
     */
    private fun indicator(player: ServerPlayer, effect: Holder<MobEffect>, grade: Int) {
        val current = player.getEffect(effect)
        if (grade <= 0) {
            if (current != null) {
                player.removeEffect(effect)
            }
            return
        }
        if (current != null && current.amplifier == grade - 1) {
            return
        }
        // Removed before the new grade goes on: vanilla's update keeps the stronger of two
        // amplifiers, so adding a milder fever over a worse one would leave the icon reading high.
        if (current != null) {
            player.removeEffect(effect)
        }
        player.addEffect(
            MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, grade - 1, false, false),
        )
    }

    /**
     * Applies and clears the screen effects.
     *
     * A post effect has to be requested by the server for the client to load it, and the request is
     * a plain id - the client resolves `assets/aliment/post_effect/<id>.json` itself and logs (and
     * skips) anything it cannot load. This runs every tick so the effect clears the instant the
     * temperature is back inside the comfortable band.
     *
     * The tiers line up with the bands in [AlimentData.thermalTier]: the edge distortion is a
     * fever thing (38.5 and up), and the motion blur only joins it once the fever is dangerous.
     *
     * The mandrake blur is a fourth, independent effect: it has nothing to do with the temperature,
     * so it stacks with any of the three - the player can be feverish *and* half blind.
     */
    private fun applyPostEffects(player: ServerPlayer, data: AlimentData) {
        val tier = data.thermalTier
        syncPostEffect(player, HEAT_HAZE, tier >= 1)
        syncPostEffect(player, HEAT_BLUR, tier >= 2)
        syncPostEffect(player, COLD_SHIVER, tier <= -1)
        syncPostEffect(player, ANTICHOLINERGIC_BLUR, data.isVisionBlurred)
        // The trip, one stage at a time. The fever that comes with the last stage is the model's.
        val trip = data.psilocinTier
        PSILOCIN_EFFECTS.forEachIndexed { index, id -> syncPostEffect(player, id, trip == index + 1) }
    }

    /** Removes every screen effect, for `/aliment cure` and anything else that resets the body. */
    fun clearPostEffects(player: ServerPlayer) {
        syncPostEffect(player, HEAT_HAZE, false)
        syncPostEffect(player, HEAT_BLUR, false)
        syncPostEffect(player, COLD_SHIVER, false)
        syncPostEffect(player, ANTICHOLINERGIC_BLUR, false)
        for (id in PSILOCIN_EFFECTS) {
            syncPostEffect(player, id, false)
        }
    }

    private fun syncPostEffect(player: ServerPlayer, id: Identifier, wanted: Boolean) {
        val present = player.getPostEffects().contains(id)
        when {
            // addPostEffect is a no-op when the id is already there, so this never spams packets.
            wanted && !present -> player.addPostEffect(id)
            !wanted && present -> player.removePostEffect(id)
        }
    }

    /**
     * Chance that this tick's shake roll actually shakes the camera, `0..MAX_SHAKE_CHANCE`.
     *
     * The probability is the model's, in Scala, reached through `AlimentModelBridge`; this only
     * hands it the body. Every term there has to come from something the player can notice, which is
     * why a fever below 38.5 and over-hydration behind a full thirst bar do not count.
     */
    fun shakeChance(data: AlimentData): Float = AlimentModelBridge.shakeChance(data)

    /**
     * Rolls for the camera shake. Low magnesium and calcium both make the tremor more likely,
     * because both cause real neuromuscular hyperexcitability, and so does a fever or a shiver.
     */
    private fun rollShake(player: ServerPlayer, data: AlimentData, runtime: AlimentRuntime) {
        if (runtime.shakeCooldown > 0) {
            runtime.shakeCooldown--
            return
        }
        runtime.shakeCooldown = SHAKE_INTERVAL_TICKS

        val chance = shakeChance(data)
        if (chance <= 0f || player.level().random.nextFloat() >= chance) {
            return
        }

        val amplitude = 0.6f + 0.8f * data.severity +
            (if (data.isImmuneStorm) 0.6f else 0f) +
            (if (data.electrolytes.calcium < Mineral.CALCIUM.severeLow) 0.4f else 0f) +
            (if (data.hasThermalStress) 0.4f else 0f)
        pushShake(player, amplitude)
    }

    /** Bumps the synced shake counter, which is what the client watches. */
    private fun pushShake(player: ServerPlayer, amplitude: Float) {
        val current = player.getAttachedOrElse(AlimentAttachments.CLIENT, AlimentClientState.INACTIVE)
        player.setAttached(
            AlimentAttachments.CLIENT,
            current.copy(shakeSequence = current.shakeSequence + 1, shakeAmplitude = amplitude),
        )
    }

    /**
     * Publishes the bits the client needs.
     *
     * The water level is only resent when its whole number changes, so a draining thirst bar costs
     * roughly one packet every 270 ticks rather than one per tick.
     */
    private fun syncClient(player: ServerPlayer, data: AlimentData, runtime: AlimentRuntime) {
        val water = data.water.toInt()
        val blurred = data.isVisionBlurred
        val current = player.getAttachedOrElse(AlimentAttachments.CLIENT, AlimentClientState.INACTIVE)

        if (water == runtime.syncedWater &&
            current.shakeSequence == runtime.syncedShake &&
            current.blurred == blurred
        ) {
            return
        }
        runtime.syncedWater = water
        runtime.syncedShake = current.shakeSequence
        player.setAttached(AlimentAttachments.CLIENT, current.copy(water = water, blurred = blurred))
    }

    // ------------------------------------------------------------------ the mineral table

    /**
     * One mineral, and what happens when it falls below or past its reference range.
     *
     * [deficit] and [excess] are the extremes, [low] and [high] the mild ends of the same sides, so
     * every entry reads as a two-sided illness rather than as a list of one-way checks. The
     * thresholds themselves come from [mineral], so a row is only the symptoms.
     */
    private class MineralRule(
        val mineral: Mineral,
        val of: (AlimentData) -> Float,
        val deficit: Symptom,
        val low: Symptom,
        val high: Symptom,
        val excess: Symptom,
    ) {
        /** The symptom for [value], or null while it is inside the reference range. */
        fun symptomFor(value: Float): Symptom? = when {
            value < this.mineral.severeLow -> this.deficit
            value < this.mineral.safeLow -> this.low
            value > this.mineral.severeHigh -> this.excess
            value > this.mineral.safeHigh -> this.high
            else -> null
        }

        /** The symptom for the player's current value of this mineral. */
        fun symptomFor(data: AlimentData): Symptom? = this.symptomFor(this.of(data))
    }

    /** Builds a [Symptom] out of vanilla effects, with optional damage for the lethal extremes. */
    private fun symptom(vararg effects: Pair<Holder<MobEffect>, Int>, damage: Float = 0f): Symptom =
        { player, ticks ->
            for ((effect, amplifier) in effects) {
                player.addEffect(MobEffectInstance(effect, ticks, amplifier))
            }
            if (damage > 0f) {
                player.hurtServer(player.level(), player.damageSources().magic(), damage)
            }
        }

    /**
     * Every mineral, in the order `/aliment status` prints them.
     *
     * The thresholds are the clinical reference ranges in [Mineral]; only the symptoms differ
     * between rows.
     */
    private val MINERALS: List<MineralRule> = listOf(
        // Sodium: hyponatraemia is confusion and nausea, hypernatraemia is intense thirst.
        MineralRule(
            mineral = Mineral.SODIUM,
            of = { it.electrolytes.sodium },
            deficit = symptom(MobEffects.NAUSEA to 0, MobEffects.SLOWNESS to 0),
            low = symptom(MobEffects.WEAKNESS to 0),
            high = symptom(MobEffects.HUNGER to 0),
            excess = symptom(MobEffects.HUNGER to 0, MobEffects.WEAKNESS to 0),
        ),
        // Potassium: both ends upset the heart, which is why the excess one does damage.
        MineralRule(
            mineral = Mineral.POTASSIUM,
            of = { it.electrolytes.potassium },
            deficit = symptom(MobEffects.WEAKNESS to 1, MobEffects.MINING_FATIGUE to 0),
            low = symptom(MobEffects.WEAKNESS to 0),
            high = symptom(MobEffects.WEAKNESS to 0),
            excess = symptom(MobEffects.SLOWNESS to 1, damage = 1f),
        ),
        // Magnesium: too little is tremor and cramps, too much is lethargy.
        MineralRule(
            mineral = Mineral.MAGNESIUM,
            of = { it.electrolytes.magnesium },
            deficit = symptom(MobEffects.WEAKNESS to 0, MobEffects.SLOWNESS to 0),
            low = symptom(MobEffects.WEAKNESS to 0),
            high = symptom(MobEffects.SLOWNESS to 0),
            excess = symptom(MobEffects.SLOWNESS to 0, MobEffects.WEAKNESS to 0),
        ),
        // Chloride follows sodium, but the acid-base side of it shows up as nausea and appetite.
        MineralRule(
            mineral = Mineral.CHLORIDE,
            of = { it.electrolytes.chloride },
            deficit = symptom(MobEffects.NAUSEA to 0),
            low = symptom(MobEffects.WEAKNESS to 0),
            high = symptom(MobEffects.HUNGER to 0),
            excess = symptom(MobEffects.HUNGER to 0, MobEffects.NAUSEA to 0),
        ),
        // Calcium: hypocalcaemia is tetany, hypercalcaemia is lethargy.
        MineralRule(
            mineral = Mineral.CALCIUM,
            of = { it.electrolytes.calcium },
            deficit = symptom(MobEffects.SLOWNESS to 0, MobEffects.WEAKNESS to 0),
            low = symptom(MobEffects.WEAKNESS to 0),
            high = symptom(MobEffects.SLOWNESS to 0),
            excess = symptom(MobEffects.SLOWNESS to 1, MobEffects.WEAKNESS to 0),
        ),
        // Iodine: the thyroid. Deficiency is hypothyroidism (slow, weak, cold, hungry); excess is
        // thyrotoxicosis (appetite without weight gain, nausea, muscle wasting).
        MineralRule(
            mineral = Mineral.IODINE,
            of = { it.traceElements.iodine },
            deficit = symptom(MobEffects.SLOWNESS to 1, MobEffects.WEAKNESS to 1, MobEffects.MINING_FATIGUE to 0),
            low = symptom(MobEffects.WEAKNESS to 0, MobEffects.SLOWNESS to 0, MobEffects.HUNGER to 0),
            high = symptom(MobEffects.HUNGER to 0, MobEffects.NAUSEA to 0),
            excess = symptom(MobEffects.HUNGER to 0, MobEffects.NAUSEA to 0, MobEffects.WEAKNESS to 0),
        ),
        // Vitamin C: deficiency (hypovitaminosis C / scurvy).
        // Low: mining fatigue I; Severe deficit: mining fatigue I + weakness I.
        MineralRule(
            mineral = Mineral.VITAMIN_C,
            of = { it.traceElements.vitaminC },
            deficit = symptom(MobEffects.MINING_FATIGUE to 0, MobEffects.WEAKNESS to 0),
            low = symptom(MobEffects.MINING_FATIGUE to 0),
            high = symptom(),
            excess = symptom(),
        ),
    )
}
