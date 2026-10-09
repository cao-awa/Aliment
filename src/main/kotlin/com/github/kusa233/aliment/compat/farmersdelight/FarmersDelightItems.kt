package com.github.kusa233.aliment.compat.farmersdelight

import net.minecraft.world.item.Item
import vectorwing.farmersdelight.common.registry.ModItems

/**
 * The Farmer's Delight items that are worth counting, grouped by what they are.
 *
 * **This file names Farmer's Delight types, and that is exactly why it is a file of its own.**
 * Farmer's Delight is optional: nothing in `fabric.mod.json` requires it, and a player without it
 * must be able to run Aliment as though this file did not exist. Every reference to `ModItems` below
 * lives in the static initialiser of this object, and the JVM runs that initialiser only when the
 * object is first touched - so as long as nothing touches it while Farmer's Delight is absent,
 * neither this object nor `ModItems` is ever loaded, and no `NoClassDefFoundError` can escape.
 * [FarmersDelightNutrition] is the only thing that touches it, and only behind an `isModLoaded`
 * guard; see its documentation.
 *
 * The alternative - naming items by their registry id as strings - would need no compile-time
 * dependency at all, but it would also let a typo or a renamed item survive compilation and then
 * vanish silently at runtime. Naming the fields is the reason the `compileOnly` dependency exists:
 * the compiler checks every one of the eighty, so a Farmer's Delight update that renames one fails
 * the build instead of quietly dropping a food from the model.
 *
 * The groups below follow Farmer's Delight's own item tags wherever it publishes one - `c:foods/
 * raw_meat`, `c:foods/cooked_meat`, `c:foods/soup`, `c:foods/vegetable`, `c:foods/pie`,
 * `c:foods/cookie`, `c:foods/food_poisoning`, `farmersdelight:drinks` - so that the classification
 * is the mod's own and not a guess at it.
 */
internal object FarmersDelightItems {

    // ---------------------------------------------------------------- raw meat and fish

    /**
     * Butcher's cuts, which are raw meat and nothing else.
     *
     * `c:foods/raw_meat` is built from `raw_beef`, `raw_pork`, `raw_chicken` and `raw_mutton`, and
     * those four files name exactly `minced_beef`, the bacon and pork chain, `chicken_cuts` and
     * `mutton_chops`. The fish slices are the same idea one step further along.
     */
    val RAW_MEAT: Set<Item> = setOf(
        ModItems.MINCED_BEEF.get(),
        ModItems.CHICKEN_CUTS.get(),
        ModItems.BACON.get(),
        ModItems.COD_SLICE.get(),
        ModItems.SALMON_SLICE.get(),
        ModItems.MUTTON_CHOPS.get(),
        ModItems.HAM.get(),
    )

    // ---------------------------------------------------------------- cooked meat and fish

    /** What the skillet and the campfire turn the cuts into, plus the fried egg. */
    val COOKED_MEAT: Set<Item> = setOf(
        ModItems.BEEF_PATTY.get(),
        ModItems.COOKED_CHICKEN_CUTS.get(),
        ModItems.COOKED_BACON.get(),
        ModItems.COOKED_COD_SLICE.get(),
        ModItems.COOKED_SALMON_SLICE.get(),
        ModItems.COOKED_MUTTON_CHOPS.get(),
        ModItems.SMOKED_HAM.get(),
        ModItems.FRIED_EGG.get(),
    )

    // ---------------------------------------------------------------- starch and sugar

    /**
     * Dough, pasta, crust, rice and everything built out of sugar.
     *
     * These are the foods that are starch or sugar and little else, and they are the ones a real
     * body absorbs fastest - which is why they are charged at the bread figure rather than the
     * plant one. `c:foods/pie` and `c:foods/cookie` are Farmer's Delight's own names for two thirds
     * of this set.
     */
    val STARCH_SWEET: Set<Item> = setOf(
        ModItems.WHEAT_DOUGH.get(),
        ModItems.RAW_PASTA.get(),
        ModItems.PIE_CRUST.get(),
        ModItems.COOKED_RICE.get(),
        ModItems.CAKE_SLICE.get(),
        ModItems.APPLE_PIE_SLICE.get(),
        ModItems.SWEET_BERRY_CHEESECAKE_SLICE.get(),
        ModItems.CHOCOLATE_PIE_SLICE.get(),
        ModItems.PUMPKIN_PIE_SLICE.get(),
        ModItems.SWEET_BERRY_COOKIE.get(),
        ModItems.HONEY_COOKIE.get(),
        ModItems.MELON_POPSICLE.get(),
        ModItems.GLOW_BERRY_CUSTARD.get(),
    )

    // ---------------------------------------------------------------- vegetables and fruit

    /**
     * Raw produce and the salads assembled from it: the food that carries the vitamin C.
     *
     * `c:foods/vegetable` names the onion and the tomato, and `c:foods/cabbage` names the cabbage
     * and its leaf, so the group is largely the mod's own again.
     */
    val PLANT: Set<Item> = setOf(
        ModItems.CABBAGE.get(),
        ModItems.CABBAGE_LEAF.get(),
        ModItems.TOMATO.get(),
        ModItems.ONION.get(),
        ModItems.TOMATO_SAUCE.get(),
        ModItems.PUMPKIN_SLICE.get(),
        ModItems.MIXED_SALAD.get(),
        ModItems.NETHER_SALAD.get(),
        ModItems.FRUIT_SALAD.get(),
        ModItems.RATATOUILLE.get(),
        ModItems.GLEAMING_SALAD.get(),
        ModItems.STUFFED_PUMPKIN.get(),
    )

    // ---------------------------------------------------------------- assembled dishes

    /**
     * Everything the cooking pot, the skillet and the cutting board assemble: sandwiches, rolls,
     * dumplings, pasta, rice dishes, glazed hams and the rest of the feast portions.
     *
     * They are mixed food - starch, meat and vegetable at once - which is why they are charged
     * between the two, at `GLUCOSE_PER_MIXED_DISH`.
     */
    val DISH: Set<Item> = setOf(
        ModItems.BARBECUE_STICK.get(),
        ModItems.EGG_SANDWICH.get(),
        ModItems.CHICKEN_SANDWICH.get(),
        ModItems.HAMBURGER.get(),
        ModItems.BACON_SANDWICH.get(),
        ModItems.MUTTON_WRAP.get(),
        ModItems.DUMPLINGS.get(),
        ModItems.STUFFED_POTATO.get(),
        ModItems.CABBAGE_ROLLS.get(),
        ModItems.SALMON_ROLL.get(),
        ModItems.COD_ROLL.get(),
        ModItems.BONE_BROTH.get(),
        ModItems.BEEF_STEW.get(),
        ModItems.CHICKEN_SOUP.get(),
        ModItems.VEGETABLE_SOUP.get(),
        ModItems.FISH_STEW.get(),
        ModItems.FRIED_RICE.get(),
        ModItems.PUMPKIN_SOUP.get(),
        ModItems.BAKED_COD_STEW.get(),
        ModItems.NOODLE_SOUP.get(),
        ModItems.ONION_SOUP.get(),
        ModItems.BACON_AND_EGGS.get(),
        ModItems.PASTA_WITH_MEATBALLS.get(),
        ModItems.PASTA_WITH_MUTTON_CHOP.get(),
        ModItems.MUSHROOM_RICE.get(),
        ModItems.ROASTED_MUTTON_CHOPS.get(),
        ModItems.VEGETABLE_NOODLES.get(),
        ModItems.STEAK_AND_POTATOES.get(),
        ModItems.SQUID_INK_PASTA.get(),
        ModItems.GRILLED_SALMON.get(),
        ModItems.ROAST_CHICKEN.get(),
        ModItems.HONEY_GLAZED_HAM.get(),
        ModItems.SHEPHERDS_PIE.get(),
        // Pot-cooked from rotten flesh, bone meal, raw meat and rice. It is a cooked dish like the
        // rest of this set and not a butcher's cut, which is why it is here rather than above.
        ModItems.DOG_FOOD.get(),
        // Rice wrapped in dried kelp: a dish assembled at a crafting table, charged as a dish. What
        // the kelp adds on top - the iodine - is in [IODINE], because it is a nutrient and not a
        // tier of carbohydrate.
        ModItems.KELP_ROLL.get(),
        ModItems.KELP_ROLL_SLICE.get(),
    )

    /**
     * The dishes that arrive as liquid in a bowl.
     *
     * They are a subset of [DISH] and are listed again because they are the ones that also count as
     * a drink. That is not a Farmer's Delight detail but a vanilla one: a mushroom stew is drunk as
     * much as eaten, and vanilla counts it as a drink too.
     *
     * Farmer's Delight's own `c:foods/soup` tag names eight of these nine; `onion_soup` is the one
     * it leaves out, and it is a bowl of soup by any other measure, so it is included.
     */
    val SOUP: Set<Item> = setOf(
        ModItems.BONE_BROTH.get(),
        ModItems.BEEF_STEW.get(),
        ModItems.CHICKEN_SOUP.get(),
        ModItems.VEGETABLE_SOUP.get(),
        ModItems.FISH_STEW.get(),
        ModItems.PUMPKIN_SOUP.get(),
        ModItems.BAKED_COD_STEW.get(),
        ModItems.NOODLE_SOUP.get(),
        ModItems.ONION_SOUP.get(),
    )

    // ---------------------------------------------------------------- drinks

    /**
     * Milk in a bottle.
     *
     * It is charged exactly as vanilla's milk bucket is charged - water and nothing else - because
     * the model does not count lactose as a glucose load, and inventing a different answer for the
     * bottled version of the same drink would be a lie about the same liquid.
     */
    val DRINK_MILK: Set<Item> = setOf(ModItems.MILK_BOTTLE.get())

    /** The sweetened drinks: cider, fruit juice and cocoa, all at the sweet-drink figure. */
    val DRINK_SWEET: Set<Item> = setOf(
        ModItems.APPLE_CIDER.get(),
        ModItems.MELON_JUICE.get(),
        ModItems.HOT_COCOA.get(),
    )

    // ---------------------------------------------------------------- risk

    /**
     * The four items Farmer's Delight itself marks `c:foods/food_poisoning` - the ones it is
     * telling you not to eat raw.
     *
     * Three of them are here because they are not meat: the dough and the pasta are raw flour, and
     * the nether salad is a bowl of things that were never food. The fourth, `chicken_cuts`, is
     * already in [RAW_MEAT], and every other cut is raw meat too, so the raw-meat rule covers the
     * rest of that tag on its own.
     */
    val RISKY: Set<Item> = setOf(
        ModItems.WHEAT_DOUGH.get(),
        ModItems.RAW_PASTA.get(),
        ModItems.CHICKEN_CUTS.get(),
        ModItems.NETHER_SALAD.get(),
    )

    // ---------------------------------------------------------------- curing salt

    /**
     * What each cured item carries in sodium, in mmol/L of serum.
     *
     * Bacon and ham are brined pork in the real kitchen, and brining is salt: this is the only
     * sodium any Farmer's Delight food carries, and it is deliberately a fraction of a salt
     * *serving* (compare `CRUDE_SODIUM`, which is 3.0), because a rasher of bacon is salty without
     * being a spoonful of salt. A plate built around the cut carries what went into the pork, which
     * is why the two bacon dishes are above the cuts and the whole glazed ham is above them both.
     *
     * Nothing else in the mod qualifies. Farmer's Delight adds no salt item and no recipe of its own
     * calls for one, so no other dish has sodium added to it out of nowhere.
     */
    val CURED_SODIUM: Map<Item, Float> = mapOf(
        ModItems.BACON.get() to 0.8f,
        ModItems.COOKED_BACON.get() to 0.8f,
        ModItems.HAM.get() to 0.8f,
        ModItems.SMOKED_HAM.get() to 0.8f,
        ModItems.BACON_SANDWICH.get() to 1.2f,
        ModItems.BACON_AND_EGGS.get() to 1.2f,
        ModItems.HONEY_GLAZED_HAM.get() to 2.0f,
    )

    // ---------------------------------------------------------------- iodine

    /**
     * What each kelp roll carries in iodine, in umol/L.
     *
     * A roll is three dried kelp, and dried kelp is worth 0.20 each - but a roll is also a bowl of
     * rice, and the rice is most of it. So a roll is charged as a serving of food that contains
     * kelp, not as three dried kelp: 0.30, between one kelp and two. A slice is a third of a roll,
     * exactly as the cutting board divides it.
     */
    val IODINE: Map<Item, Float> = mapOf(
        ModItems.KELP_ROLL.get() to 0.30f,
        ModItems.KELP_ROLL_SLICE.get() to 0.10f,
    )

    /**
     * What each item carries in vitamin C, in umol/L.
     *
     * Anything absent from this map carries none, which is the honest answer for the meat, the
     * eggs, the bread, the rice, the milk and the chocolate. The scale is the one the rest of the
     * mod already uses: an apple is 12, a carrot 10, a melon slice 8, sweet berries 6 and seaweed 5,
     * so a tomato is 10 and a garnished sandwich is 4. A dish earns its vitamin C from the plants
     * that went into it, which is what makes the salads the richest things on the list.
     */
    val VITAMIN_C: Map<Item, Float> = mapOf(
        // Raw produce, eaten as it is.
        ModItems.TOMATO.get() to 10.0f,
        ModItems.CABBAGE.get() to 8.0f,
        ModItems.ONION.get() to 6.0f,
        ModItems.PUMPKIN_SLICE.get() to 6.0f,
        ModItems.CABBAGE_LEAF.get() to 4.0f,
        ModItems.TOMATO_SAUCE.get() to 8.0f,

        // Salads and plates of vegetables, where the plants are most of the dish.
        ModItems.GLEAMING_SALAD.get() to 18.0f,
        ModItems.FRUIT_SALAD.get() to 16.0f,
        ModItems.RATATOUILLE.get() to 16.0f,
        ModItems.MIXED_SALAD.get() to 14.0f,
        ModItems.STUFFED_PUMPKIN.get() to 12.0f,
        ModItems.VEGETABLE_SOUP.get() to 12.0f,
        ModItems.PUMPKIN_SOUP.get() to 10.0f,
        ModItems.VEGETABLE_NOODLES.get() to 10.0f,
        ModItems.CABBAGE_ROLLS.get() to 8.0f,
        ModItems.ONION_SOUP.get() to 6.0f,
        ModItems.NETHER_SALAD.get() to 4.0f,

        // Dishes that take a vegetable or two as a garnish.
        ModItems.HAMBURGER.get() to 4.0f,
        ModItems.CHICKEN_SANDWICH.get() to 4.0f,
        ModItems.BACON_SANDWICH.get() to 4.0f,
        ModItems.BARBECUE_STICK.get() to 4.0f,
        ModItems.FRIED_RICE.get() to 4.0f,
        ModItems.SQUID_INK_PASTA.get() to 4.0f,
        ModItems.PUMPKIN_PIE_SLICE.get() to 4.0f,
        ModItems.MUTTON_WRAP.get() to 3.0f,
        ModItems.DUMPLINGS.get() to 3.0f,
        ModItems.STEAK_AND_POTATOES.get() to 3.0f,
        ModItems.APPLE_PIE_SLICE.get() to 3.0f,
        ModItems.SWEET_BERRY_CHEESECAKE_SLICE.get() to 3.0f,
        ModItems.KELP_ROLL.get() to 3.0f,
        ModItems.SALMON_ROLL.get() to 2.0f,
        ModItems.COD_ROLL.get() to 2.0f,
        ModItems.SWEET_BERRY_COOKIE.get() to 2.0f,
        ModItems.HONEY_COOKIE.get() to 1.0f,
        ModItems.KELP_ROLL_SLICE.get() to 1.0f,

        // Sweet things that are also fruit.
        ModItems.MELON_POPSICLE.get() to 6.0f,
        ModItems.GLOW_BERRY_CUSTARD.get() to 6.0f,

        // Drinks, pressed from fruit.
        ModItems.APPLE_CIDER.get() to 8.0f,
        ModItems.MELON_JUICE.get() to 8.0f,
    )
}
