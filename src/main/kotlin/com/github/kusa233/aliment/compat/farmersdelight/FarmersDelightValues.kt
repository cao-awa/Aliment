package com.github.kusa233.aliment.compat.farmersdelight

import com.github.kusa233.aliment.physiology.AlimentData
import net.minecraft.world.item.Item

/**
 * The answers for one Farmer's Delight item, as plain values.
 *
 * **Nothing may call into this file unless Farmer's Delight is loaded.** It names
 * [FarmersDelightItems], which names `ModItems`, so touching it without the mod on the classpath
 * ends in `NoClassDefFoundError`. [FarmersDelightNutrition] is the guard that stands in front of
 * it, and it is the only caller.
 *
 * Note what is *not* here: no field of an `Item`, no `Set<Item>` returned across the boundary, no
 * `FarmersDelightItems` in any signature. Every function takes and returns Minecraft types the rest
 * of the mod already has, so that the guard class never has to resolve a Farmer's Delight type in
 * order to be verified - only to be *run*, which it never is without the mod.
 */
internal object FarmersDelightValues {

    /**
     * What one serving adds to blood glucose, in mmol/L, or 0 for anything that is not food.
     *
     * The tiers are the model's own, so a Farmer's Delight stew is charged as a mixed dish and its
     * bread as bread.
     */
    fun glucoseFor(item: Item): Float = when {
        item in FarmersDelightItems.STARCH_SWEET -> AlimentData.GLUCOSE_PER_BREAD
        item in FarmersDelightItems.DISH -> AlimentData.GLUCOSE_PER_MIXED_DISH
        item in FarmersDelightItems.COOKED_MEAT -> AlimentData.GLUCOSE_PER_COOKED_MEAT
        item in FarmersDelightItems.RAW_MEAT -> AlimentData.GLUCOSE_PER_RAW_MEAT
        item in FarmersDelightItems.PLANT -> AlimentData.GLUCOSE_PER_PLANT_FOOD
        item in FarmersDelightItems.DRINK_SWEET -> AlimentData.GLUCOSE_PER_SWEET_DRINK
        else -> 0f
    }

    /** Dietary vitamin C one serving delivers, in umol/L, or 0. */
    fun vitaminCFor(item: Item): Float = FarmersDelightItems.VITAMIN_C[item] ?: 0f

    /** Iodine one serving delivers, in umol/L, or 0. The kelp rolls are the only source. */
    fun iodineFor(item: Item): Float = FarmersDelightItems.IODINE[item] ?: 0f

    /** Sodium one serving delivers, in mmol/L of serum, or 0. Only the cured pork carries any. */
    fun sodiumFor(item: Item): Float = FarmersDelightItems.CURED_SODIUM[item] ?: 0f

    /**
     * Whether the item counts towards the water index: the drinks, and the dishes that arrive as a
     * bowl of liquid. Milk is on the list because it is a drink like any other.
     */
    fun isDrink(item: Item): Boolean = item in FarmersDelightItems.DRINK_MILK ||
        item in FarmersDelightItems.DRINK_SWEET ||
        item in FarmersDelightItems.SOUP

    /**
     * Whether swallowing it raw should roll for a bacterial infection.
     *
     * This is Farmer's Delight's own answer, not an invented one: every item in
     * `c:foods/food_poisoning` - the raw dough, the raw pasta, the raw chicken cuts and the nether
     * salad - plus every raw cut, because a chicken cut is raw chicken.
     */
    fun isRisky(item: Item): Boolean =
        item in FarmersDelightItems.RISKY || item in FarmersDelightItems.RAW_MEAT
}
