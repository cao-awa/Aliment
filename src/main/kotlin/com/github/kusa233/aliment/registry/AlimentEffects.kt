package com.github.kusa233.aliment.registry

import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory

/**
 * The two effects that tell the player something instead of doing something to them.
 *
 * A fever and the ache that goes with it are already modelled - the temperature, the thresholds it
 * crosses and the symptoms it produces all live in `Physiology` - but nothing on the HUD said so, so
 * the only way to know was the debug command. These two are that line on the HUD and nothing else:
 * neither carries an attribute modifier, neither overrides a single tick, and nothing anywhere reads
 * them back. Deleting both would change exactly one thing, which is that a player would have to ask
 * the command how ill they were.
 *
 * Being effects rather than a custom HUD element is what makes them free: the icon, the name, the
 * tooltip, the ordering among vanilla's effects and the "does this player have it" query all come
 * from vanilla. The icon is looked up by the effect's own registry id - `Hud.getMobEffectSprite`
 * builds `mob_effect/<path>` and the vanilla `gui` atlas collects that directory from every
 * namespace - so they live at `assets/aliment/textures/mob_effect/<path>.png` and are drawn by
 * `tools/gen_effect_textures.ps1`.
 *
 * [MobEffect]'s constructor is protected, so both are the smallest possible subclass rather than
 * instances of it.
 */
object AlimentEffects {

    /**
     * The fever indicator, whose amplifier is the grade: I from 38.5, II from 39.5, III from 40.
     *
     * The grade is [com.github.kusa233.aliment.physiology.AlimentData.feverGrade], which is the
     * model's own answer and has three steps where the symptom scale has two.
     */
    val FEVER: Holder<MobEffect> = register("fever", 0xB03A2E)

    /** The ache of a high fever, shown as a single level from [com.github.kusa233.aliment.physiology.AlimentData.PAIN_THRESHOLD]. */
    val PAIN: Holder<MobEffect> = register("pain", 0x7A2E4A)

    fun initialize() {
        // Classloading triggers registration
    }

    /**
     * Registers one indicator.
     *
     * The category is [MobEffectCategory.HARMFUL] for both, which is what draws them with a red
     * frame among the player's effects and is the honest labelling: neither is a buff. The colour is
     * the constructor's only other argument and matters only to a particle, which these never have -
     * see [com.github.kusa233.aliment.physiology.AlimentSymptoms], which adds them with particles
     * switched off.
     */
    private fun register(path: String, color: Int): Holder<MobEffect> = Registry.registerForHolder(
        BuiltInRegistries.MOB_EFFECT,
        Registration.id(path),
        Indicator(color),
    )

    /**
     * An effect with no behaviour at all: no attribute modifier, no per-tick work, no instant effect.
     * Everything it is, is its name, its category and its icon.
     */
    private class Indicator(color: Int) : MobEffect(MobEffectCategory.HARMFUL, color)
}
