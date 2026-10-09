package com.github.kusa233.aliment.compat.farmersdelight

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe

/**
 * The one thing about a Farmer's Delight cooking recipe that vanilla will not hand over: the
 * container it draws its result into.
 *
 * Minecraft 26.3 moved a recipe's ingredients off `Recipe` and onto `PlacementInfo`, which is
 * vanilla, so a cooking recipe's inputs are readable without naming anything of that mod's. The
 * container did not move anywhere - it is Farmer's Delight's own field, and a pot that asks for a
 * bowl will not give you a stew without one - so reading it means naming [CookingPotRecipe]. That is
 * the whole reason this object exists rather than the check living in the test.
 *
 * Like [FarmersDelightItems], this names a Farmer's Delight type and must never be touched unless
 * [FarmersDelightNutrition.loaded] is true. It is loaded lazily, so the reference is only resolved on
 * first use, and a game without the mod never resolves it at all.
 */
internal object FarmersDelightRecipes {

    /**
     * The item a cooking recipe insists on drawing into, or an empty stack when it asks for nothing.
     *
     * [recipe] is accepted as anything because the caller has only a `Recipe<*>` from the recipe
     * manager, and a star projection cannot be cast to the recipe's own input type.
     */
    fun cookingPotContainer(recipe: Recipe<*>?): ItemStack =
        (recipe as? CookingPotRecipe)?.container()?.create() ?: ItemStack.EMPTY
}
