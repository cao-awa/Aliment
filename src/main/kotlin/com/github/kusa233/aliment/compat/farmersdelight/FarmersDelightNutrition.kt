package com.github.kusa233.aliment.compat.farmersdelight

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.world.item.Item

/**
 * What Farmer's Delight's food does to the body, or nothing at all if Farmer's Delight is absent.
 *
 * This is the **only** part of the integration the rest of Aliment talks to, and it is written so
 * that the mod runs, unchanged, without Farmer's Delight installed:
 *
 * * [loaded] is read once, from the loader, when this object initialises;
 * * every function returns its empty answer - `0f`, `false` - when [loaded] is `false`, *before* it
 *   reaches anything else;
 * * the item lists themselves live two classes further down: [FarmersDelightValues] names
 *   [FarmersDelightItems], which names `ModItems`. The JVM loads a class the first time it is
 *   actually used, so as long as nothing here calls down that chain while [loaded] is `false`, a
 *   player without Farmer's Delight never loads `ModItems` and never sees a
 *   `NoClassDefFoundError`.
 *
 * That layering is the whole reason there are three small files instead of one. A single object
 * that both checked for the mod and named its items would resolve `ModItems` while initialising
 * itself, and the error would arrive before the check could stop it.
 *
 * **The numbers reached this way are doses, not model parameters.** They are the same kind of
 * constant as the `APPLE_VITAMIN_C` and `KELP_IODINE` in `AlimentIngestion`: how much one swallowed
 * item delivers. The figures that describe how the *body* handles what it is given - decay rates,
 * healthy ranges, how much glucose a class of food adds - stay in the Scala model. The one that this
 * integration needed, `GLUCOSE_PER_MIXED_DISH`, was added there and re-exported the usual way.
 */
object FarmersDelightNutrition {

    /** Whether Farmer's Delight is loaded. The loader's answer does not change; read it once. */
    val loaded: Boolean = FabricLoader.getInstance().isModLoaded("farmersdelight")

    /** What one serving adds to blood glucose, in mmol/L, or 0 without Farmer's Delight. */
    fun glucoseFor(item: Item): Float =
        if (loaded) FarmersDelightValues.glucoseFor(item) else 0f

    /** Dietary vitamin C one serving delivers, in umol/L, or 0. */
    fun vitaminCFor(item: Item): Float =
        if (loaded) FarmersDelightValues.vitaminCFor(item) else 0f

    /** Iodine one serving delivers, in umol/L, or 0. The kelp rolls are the only source. */
    fun iodineFor(item: Item): Float =
        if (loaded) FarmersDelightValues.iodineFor(item) else 0f

    /**
     * Sodium one serving delivers, in mmol/L of serum, or 0. Only the cured pork carries any; see
     * `CURED_SODIUM`.
     */
    fun sodiumFor(item: Item): Float =
        if (loaded) FarmersDelightValues.sodiumFor(item) else 0f

    /**
     * Whether the item counts towards the water index: the drinks, and the dishes that arrive as a
     * bowl of liquid. Milk is on the list because it is a drink like any other, exactly as vanilla's
     * milk bucket is.
     */
    fun isDrink(item: Item): Boolean =
        loaded && FarmersDelightValues.isDrink(item)

    /**
     * Whether swallowing it raw should roll for a bacterial infection.
     *
     * This is Farmer's Delight's own answer and not an invented one: the items in
     * `c:foods/food_poisoning`, plus every raw cut, because raw meat is already risky in vanilla and
     * a Farmer's Delight cut is raw meat.
     */
    fun isRisky(item: Item): Boolean =
        loaded && FarmersDelightValues.isRisky(item)
}
