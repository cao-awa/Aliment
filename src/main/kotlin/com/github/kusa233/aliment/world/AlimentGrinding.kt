package com.github.kusa233.aliment.world

import com.github.kusa233.aliment.registry.AlimentBlocks
import com.github.kusa233.aliment.registry.AlimentItems
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * What the grindstone does with aliment's items.
 *
 * Vanilla's grindstone only accepts damageable or enchanted items, and its result logic knows
 * nothing about our conversions, so both the input slots and [net.minecraft.world.inventory.GrindstoneMenu]
 * are widened by mixins that forward here. Keeping the table in one place means the mixins stay
 * one-liners and the behaviour is testable without a menu.
 */
object AlimentGrinding {

    /** One input item to (output item, how many it yields). */
    val RECIPES: Map<Item, Pair<Item, Int>> = mapOf(
        AlimentItems.WILLOW_BARK to (AlimentItems.WILLOW_BARK_PIECES to 2),
        AlimentBlocks.ROCK_SALT_ORE.asItem() to (AlimentItems.CRUSHED_ROCK_SALT to 1),
        AlimentItems.CRUSHED_ROCK_SALT to (AlimentItems.CRUDE_SALT to 9),
        AlimentItems.CRUDE_SALT to (AlimentItems.CRUDE_SALT_POWDER to 1),
        AlimentItems.EPHEDRA to (AlimentItems.CRUSHED_EPHEDRA to 1),
        AlimentItems.COPTIS to (AlimentItems.CRUSHED_COPTIS to 1),
        AlimentItems.PHELLODENDRON to (AlimentItems.CRUSHED_PHELLODENDRON to 1),
        AlimentItems.LICORICE to (AlimentItems.CRUSHED_LICORICE to 1),
        AlimentItems.SEAWEED to (AlimentItems.CRUSHED_SEAWEED to 1),
    )

    @JvmStatic
    fun allRecipes(): Map<Item, Pair<Item, Int>> = RECIPES

    /** True when the grindstone has something to do with this item. */
    @JvmStatic
    fun isGrindable(stack: ItemStack): Boolean = RECIPES.containsKey(stack.item)

    /** How many items the given input yields, or 0. Used by the sneak-right-click shortcut. */
    @JvmStatic
    fun outputFor(stack: ItemStack): Pair<Item, Int>? = RECIPES[stack.item]

    /**
     * The grindstone result for a pair of input slots.
     *
     * Deliberately limited to a single item in a single slot: the result slot's `onTake` clears
     * both input slots, so anything larger would silently swallow the rest of the stack.
     */
    @JvmStatic
    fun resultFor(input: ItemStack, additional: ItemStack): ItemStack {
        val single = when {
            !input.isEmpty && additional.isEmpty -> input
            !additional.isEmpty && input.isEmpty -> additional
            else -> return ItemStack.EMPTY
        }
        if (single.count != 1) {
            return ItemStack.EMPTY
        }
        val recipe = RECIPES[single.item] ?: return ItemStack.EMPTY
        return ItemStack(recipe.first, recipe.second)
    }

    /** True when the item may sit in a grindstone input slot at all. */
    @JvmStatic
    fun mayPlace(stack: ItemStack): Boolean = RECIPES.containsKey(stack.item) && stack.count == 1
}
