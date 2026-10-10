package com.github.kusa233.aliment.physiology

import com.github.kusa233.aliment.advancement.AlimentAdvancements
import com.github.kusa233.aliment.compat.farmersdelight.FarmersDelightNutrition
import com.github.kusa233.aliment.registry.AlimentItems
import com.github.kusa233.aliment.world.item.BeerItem
import com.github.kusa233.aliment.world.item.GrapeWineItem
import com.github.kusa233.aliment.world.item.WineItem
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Everything that happens when a player swallows something.
 *
 * One hook covers all of it, because vanilla only gives us one moment: `Item.finishUsingItem`
 * (see `ItemMixin`). From here the effects fan out:
 *
 * * risky food rolls for a bacterial infection;
 * * drinks add water;
 * * salted food adds sodium and chloride (crude salt carries the other minerals too);
 * * willow bark soup adds salicin.
 */
object AlimentIngestion {

    /** Water added by any drinkable: a water bottle, a potion, stew, or soup. */
    val WATER_PER_DRINK = AlimentData.WATER_PER_DRINK

    /** Water added by drinking wine (10 units). */
    const val WINE_WATER = 10f

    /**
     * Salt from one serving made with crude rock salt, in mmol/L of serum. The impurities that rock
     * salt carries come along.
     *
     * These numbers are chosen against the reference range: sodium runs 135..145 mmol/L, so one
     * serving of 3.0 moves a healthy player from 140 to 143 - still inside - and it takes **two**
     * servings to go past 145. That is the whole point of the scale: "drink two cups of salt water
     * and you are hypernatraemic" is a sentence that means something.
     */
    private const val CRUDE_SODIUM = 3.0f
    private const val CRUDE_CHLORIDE = 3.0f
    private const val CRUDE_MAGNESIUM = 0.04f
    private const val CRUDE_CALCIUM = 0.07f

    /** Refined salt is almost pure sodium chloride. 140 -> 143.5, two of them -> 147. */
    private const val REFINED_SODIUM = 3.5f
    private const val REFINED_CHLORIDE = 3.5f

    /**
     * A bottle of sea water is about 3.5% salt, so it is a sodium load in its own right, and it
     * arrives with the magnesium and calcium sea water actually contains. Two of them also tip
     * sodium over the reference range; one does not.
     */
    private const val SEA_WATER_SODIUM = 3.0f
    private const val SEA_WATER_CHLORIDE = 3.0f
    private const val SEA_WATER_MAGNESIUM = 0.05f
    private const val SEA_WATER_CALCIUM = 0.05f

    /**
     * Iodine from kelp, in umol/L. Dried kelp is the concentrated form.
     *
     * The body loses 0.09 umol/L a day on its own - the whole store is gone in five days - so a
     * regular diet of kelp is what keeps a player out of hypothyroidism: one a day is not quite
     * enough, two a day is comfortable, and dried kelp is worth two wet ones.
     */
    private const val KELP_IODINE = 0.05f
    private const val DRIED_KELP_IODINE = 0.10f
    private const val SEAWEED_IODINE = 0.10f
    private const val COOKED_SEAWEED_IODINE = 0.125f
    private const val IODIZED_SALT_IODINE = 0.20f
    private const val IODIZED_SALT_SODIUM = 1.5f
    private const val IODIZED_SALT_CHLORIDE = 1.5f

    // ---------------------------------------------------------------- vitamin C
    private const val APPLE_VITAMIN_C = 12.0f
    private const val GOLDEN_APPLE_VITAMIN_C = 20.0f
    private const val ENCHANTED_GOLDEN_APPLE_VITAMIN_C = 30.0f
    private const val MELON_SLICE_VITAMIN_C = 8.0f
    private const val BERRIES_VITAMIN_C = 6.0f
    private const val CARROT_VITAMIN_C = 10.0f
    private const val GOLDEN_CARROT_VITAMIN_C = 15.0f
    private const val PUMPKIN_PIE_VITAMIN_C = 15.0f
    private const val BEETROOT_VITAMIN_C = 6.0f
    private const val BEETROOT_SOUP_VITAMIN_C = 16.0f
    private const val MANDRAKE_FRUIT_VITAMIN_C = 10.0f
    private const val SEAWEED_VITAMIN_C = 5.0f
    private const val COOKED_SEAWEED_VITAMIN_C = 3.0f

    /** What one mandrake fruit carries, in dose units; the seeds are the same plant, watered down. */
    private const val FRUIT_SCOPOLAMINE = 1.0f
    private const val FRUIT_ATROPINE = 0.1f
    private const val SEED_SCOPOLAMINE = 0.75f
    private const val SEED_ATROPINE = 0.1f

    /** One raw gymnopilus: a dose of the prodrug and a dose of what it becomes. */
    private const val MUSHROOM_PSILOCYBIN = 1.3f
    private const val MUSHROOM_PSILOCIN = 1.3f

    /** Ephedrine delivered by consuming raw ephedra herb twigs. */
    private const val EPHEDRA_EPHEDRINE = 0.5f

    /** Ephedrine delivered by consuming purified ephedrine extract. */
    private const val EPHEDRINE_PURIFIED = 2.5f

    /** Berberine delivered by consuming raw coptis herb. */
    private const val COPTIS_BERBERINE = 1.1f

    /** Berberine delivered by consuming raw phellodendron bark. */
    private const val PHELLODENDRON_BERBERINE = 0.6f

    /** Glycyrrhizin delivered by consuming raw licorice herb. */
    private const val LICORICE_GLYCYRRHIZIN = 1.1f

    /** Berberine delivered by coptis potion. */
    private const val COPTIS_POTION_BERBERINE = 2.5f

    /** Berberine delivered by phellodendron potion. */
    private const val PHELLODENDRON_POTION_BERBERINE = 1.5f

    /** Glycyrrhizin delivered by licorice potion. */
    private const val LICORICE_POTION_GLYCYRRHIZIN = 2.5f

    /**
     * Water a grapefruit slice adds.
     *
     * A slice is *eaten*, not drunk, so it does not go through [isDrink] and does not get the
     * standard drink's worth: this is a third of a bottle, which is what a slice of fruit is.
     */
    private const val GRAPEFRUIT_SLICE_WATER = 5f

    /** Naringin one grapefruit slice delivers. Ten slices fill the body; see `NARINGIN_CAP`. */
    private const val GRAPEFRUIT_SLICE_NARINGIN = 1f

    /**
     * Vitamin C one grapefruit slice delivers.
     *
     * A citrus is the obvious source of it, and a slice is a plant food like any other, so it is
     * worth exactly what the other plant foods are: the same 10 as a carrot.
     */
    private const val GRAPEFRUIT_SLICE_VITAMIN_C = 10.0f

    /**
     * Naringin one glass of grapefruit juice delivers - one slice's worth, in one drink.
     *
     * The glass is a slice, pressed: same naringin, same vitamin C, but drunk rather than eaten, so
     * it also collects the full drink's water. What it adds that the slice does not is the sugar
     * (see [SWEET_DRINK]).
     */
    private const val GRAPEFRUIT_JUICE_NARINGIN = 1f

    /** Vitamin C one glass of grapefruit juice delivers: the slice's 10, pressed into a bottle. */
    private const val GRAPEFRUIT_JUICE_VITAMIN_C = 10.0f

    /** How much pathogen a single successful roll adds. */
    private const val BACTERIA_SEED = 6f

    // ---------------------------------------------------------------- foul water

    /** 30 seconds, shared by every foul-water effect. */
    private const val FOUL_EFFECT_TICKS = 600

    private const val FOUL_BACTERIA_CHANCE = 0.30f

    private const val FOUL_NAUSEA_CHANCE = 0.35f

    private const val FOUL_POISON_CHANCE = 0.05f

    /** Salicin delivered by one serving of willow bark soup, raw or cooked. */
    private const val SALICIN_PER_SERVING = 1.1f

    /** Dexamethasone delivered by one injection. */
    const val DEXAMETHASONE_PER_INJECTION = 1.2f

    /**
     * Called from `ItemMixin` whenever a living entity finishes using an item, which is the
     * moment the food or drink is actually consumed.
     *
     * A creative player's body is frozen (`AlimentSymptoms.isFrozen`), so nothing eaten or drunk
     * reaches it - otherwise the state would keep moving through the kitchen door while the model
     * stood still. The vanilla side of eating, hunger and saturation, is untouched either way.
     */
    @JvmStatic
    fun onItemConsumed(player: ServerPlayer, stack: ItemStack) {
        if (AlimentSymptoms.isFrozen(player)) {
            return
        }
        val before = player.getAttachedOrCreate(AlimentAttachments.DATA)
        var data = before

        data = AlimentInfection.rollRiskyFood(player, data, stack)

        if (isDrink(stack.item)) {
            val water = if (stack.item === AlimentItems.WINE ||
                stack.item === AlimentItems.BEER ||
                stack.item === AlimentItems.GRAPE_WINE
            ) {
                WINE_WATER
            } else {
                WATER_PER_DRINK
            }
            data = AlimentPhysiology.drink(data, water)
        }

        if (stack.item === AlimentItems.WINE) {
            val concentration = WineItem.getConcentration(stack)
            data = AlimentPhysiology.addEthanol(data, concentration)
        } else if (stack.item === AlimentItems.BEER) {
            val concentration = BeerItem.getConcentration(stack)
            data = AlimentPhysiology.addEthanol(data, concentration)
        } else if (stack.item === AlimentItems.GRAPE_WINE) {
            val concentration = GrapeWineItem.getConcentration(stack)
            data = AlimentPhysiology.addEthanol(data, concentration)
        }

        when (stack.item) {
            AlimentItems.CRUDE_SALT_WATER,
            AlimentItems.CRUDE_SALT_MUSHROOM_STEW,
            AlimentItems.CRUDE_SALT_WILLOW_BARK_SOUP,
            AlimentItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP,
            AlimentItems.CRUDE_SALT_SWAMP_WATER,
            AlimentItems.CRUDE_SALT_SEA_WATER,
            -> data = AlimentPhysiology.salt(data, CRUDE_SODIUM, CRUDE_CHLORIDE, CRUDE_MAGNESIUM, CRUDE_CALCIUM)

            AlimentItems.SALT_WATER,
            AlimentItems.SALT_MUSHROOM_STEW,
            AlimentItems.SALT_WILLOW_BARK_SOUP,
            AlimentItems.SALT_RAW_WILLOW_BARK_SOUP,
            AlimentItems.SALT_SWAMP_WATER,
            AlimentItems.SALT_SEA_WATER,
            -> data = AlimentPhysiology.salt(data, REFINED_SODIUM, REFINED_CHLORIDE)

            // Plain sea water carries no salt *serving*, only the salt the sea already has.
            AlimentItems.SEA_WATER_BOTTLE,
            -> data = AlimentPhysiology.salt(
                data,
                SEA_WATER_SODIUM,
                SEA_WATER_CHLORIDE,
                SEA_WATER_MAGNESIUM,
                SEA_WATER_CALCIUM,
            )

            else -> Unit
        }

        // Farmer's Delight's own foods, if it is installed. This is a no-op - a 0 and a skipped
        // branch - when it is not, and the class behind it is never even loaded in that case; see
        // [FarmersDelightNutrition].
        val fdSodium = FarmersDelightNutrition.sodiumFor(stack.item)
        if (fdSodium > 0f) {
            data = AlimentPhysiology.salt(data, fdSodium, fdSodium)
        }

        if (isWillowSoup(stack.item)) {
            data = AlimentPhysiology.dose(data, SALICIN_PER_SERVING)
        }

        // Kelp and seaweed are dietary sources of iodine, stopping the slow drain.
        when (stack.item) {
            Items.KELP -> data = AlimentPhysiology.iodine(data, KELP_IODINE)
            Items.DRIED_KELP -> data = AlimentPhysiology.iodine(data, DRIED_KELP_IODINE)
            AlimentItems.SEAWEED -> data = AlimentPhysiology.iodine(data, SEAWEED_IODINE)
            AlimentItems.COOKED_SEAWEED -> data = AlimentPhysiology.iodine(data, COOKED_SEAWEED_IODINE)
            AlimentItems.SEAWEED_IODIZED_SALT -> {
                data = AlimentPhysiology.iodine(data, IODIZED_SALT_IODINE)
                data = AlimentPhysiology.salt(data, IODIZED_SALT_SODIUM, IODIZED_SALT_CHLORIDE)
            }

            // The mandrake: scopolamine for the delirium, atropine for the dry mouth and the fever
            // that comes with it. A fruit is a full dose of the first, the seeds three quarters.
            AlimentItems.MANDRAKE_FRUIT -> {
                data = AlimentPhysiology.anticholinergic(data, FRUIT_SCOPOLAMINE, FRUIT_ATROPINE)
                AlimentAdvancements.award(player, AlimentAdvancements.EVEN_IF_DANGEROUS)
            }
            AlimentItems.MANDRAKE_SEEDS -> {
                data = AlimentPhysiology.anticholinergic(data, SEED_SCOPOLAMINE, SEED_ATROPINE)
                AlimentAdvancements.award(player, AlimentAdvancements.EVEN_IF_DANGEROUS)
            }

            // The gymnopilus. Raw it hands over both compounds at once; cooked it hands over nothing,
            // because the heat that makes it food is what destroys them.
            AlimentItems.GYMNOPILUS -> {
                data = AlimentPhysiology.mushroom(data, MUSHROOM_PSILOCYBIN, MUSHROOM_PSILOCIN)
                AlimentAdvancements.award(player, AlimentAdvancements.PSYCHEDELIC_WORLD)
            }
            AlimentItems.COOKED_GYMNOPILUS -> {
                AlimentAdvancements.award(player, AlimentAdvancements.PSYCHEDELIC_WORLD)
            }
            // Ephedra & ephedrine: oral intake adds ephedrine to the body
            AlimentItems.EPHEDRA -> {
                data = AlimentPhysiology.addEphedrine(data, EPHEDRA_EPHEDRINE)
            }
            AlimentItems.EPHEDRINE -> {
                data = AlimentPhysiology.addEphedrine(data, EPHEDRINE_PURIFIED)
            }

            // Traditional herbs: coptis, phellodendron, licorice
            AlimentItems.COPTIS -> {
                data = AlimentPhysiology.addBerberine(data, COPTIS_BERBERINE)
            }
            AlimentItems.COPTIS_POTION -> {
                data = AlimentPhysiology.addBerberine(data, COPTIS_POTION_BERBERINE)
            }
            AlimentItems.PHELLODENDRON -> {
                data = AlimentPhysiology.addBerberine(data, PHELLODENDRON_BERBERINE)
            }
            AlimentItems.PHELLODENDRON_POTION -> {
                data = AlimentPhysiology.addBerberine(data, PHELLODENDRON_POTION_BERBERINE)
            }
            AlimentItems.LICORICE -> {
                data = AlimentPhysiology.addGlycyrrhizin(data, LICORICE_GLYCYRRHIZIN)
            }
            AlimentItems.LICORICE_POTION -> {
                data = AlimentPhysiology.addGlycyrrhizin(data, LICORICE_POTION_GLYCYRRHIZIN)
            }

            // The grapefruit: a snack that is also a drink, and the only source of naringin. What it
            // does is hold CYP3A4 down, which is what makes the berberine above last - so a slice
            // after a dose of coptis is not a dessert, it is a decision.
            AlimentItems.GRAPEFRUIT_SLICE -> {
                data = AlimentPhysiology.drink(data, GRAPEFRUIT_SLICE_WATER)
                data = AlimentPhysiology.addNaringin(data, GRAPEFRUIT_SLICE_NARINGIN)
            }
            // The juice is the slice pressed into a bottle: the same naringin, and it takes its
            // water from [isDrink] instead of from a constant of its own.
            AlimentItems.GRAPEFRUIT_JUICE -> {
                data = AlimentPhysiology.addNaringin(data, GRAPEFRUIT_JUICE_NARINGIN)
            }
            else -> Unit
        }

        // Plant foods (fruits, carrots, pumpkins, etc.) provide dietary vitamin C.
        when (stack.item) {
            Items.APPLE -> data = AlimentPhysiology.vitaminC(data, APPLE_VITAMIN_C)
            Items.GOLDEN_APPLE -> data = AlimentPhysiology.vitaminC(data, GOLDEN_APPLE_VITAMIN_C)
            Items.ENCHANTED_GOLDEN_APPLE -> data = AlimentPhysiology.vitaminC(data, ENCHANTED_GOLDEN_APPLE_VITAMIN_C)
            Items.MELON_SLICE -> data = AlimentPhysiology.vitaminC(data, MELON_SLICE_VITAMIN_C)
            Items.SWEET_BERRIES, Items.GLOW_BERRIES -> data = AlimentPhysiology.vitaminC(data, BERRIES_VITAMIN_C)
            Items.CARROT -> data = AlimentPhysiology.vitaminC(data, CARROT_VITAMIN_C)
            Items.GOLDEN_CARROT -> data = AlimentPhysiology.vitaminC(data, GOLDEN_CARROT_VITAMIN_C)
            Items.PUMPKIN_PIE -> data = AlimentPhysiology.vitaminC(data, PUMPKIN_PIE_VITAMIN_C)
            Items.BEETROOT -> data = AlimentPhysiology.vitaminC(data, BEETROOT_VITAMIN_C)
            Items.BEETROOT_SOUP -> data = AlimentPhysiology.vitaminC(data, BEETROOT_SOUP_VITAMIN_C)
            AlimentItems.GRAPEFRUIT_SLICE -> data = AlimentPhysiology.vitaminC(data, GRAPEFRUIT_SLICE_VITAMIN_C)
            AlimentItems.GRAPEFRUIT_JUICE -> data = AlimentPhysiology.vitaminC(data, GRAPEFRUIT_JUICE_VITAMIN_C)
            AlimentItems.MANDRAKE_FRUIT -> data = AlimentPhysiology.vitaminC(data, MANDRAKE_FRUIT_VITAMIN_C)
            AlimentItems.SEAWEED -> data = AlimentPhysiology.vitaminC(data, SEAWEED_VITAMIN_C)
            AlimentItems.COOKED_SEAWEED -> data = AlimentPhysiology.vitaminC(data, COOKED_SEAWEED_VITAMIN_C)
            else -> Unit
        }

        // Farmer's Delight's foods carry vitamin C and iodine the same way the mod's own plants do:
        // a tomato and a plate of ratatouille are plant food, and a kelp roll is seaweed and rice.
        // Both lookups are 0 without the mod, so nothing happens for a player who does not have it.
        val fdVitaminC = FarmersDelightNutrition.vitaminCFor(stack.item)
        if (fdVitaminC > 0f) {
            data = AlimentPhysiology.vitaminC(data, fdVitaminC)
        }
        val fdIodine = FarmersDelightNutrition.iodineFor(stack.item)
        if (fdIodine > 0f) {
            data = AlimentPhysiology.iodine(data, fdIodine)
        }

        // Everything edible is carbohydrate or becomes it, and the body stores none of it: this is
        // what makes a meal a glucose dose rather than just a hunger bar. On peaceful the dose is
        // declined by the model rather than here, so that every way into the blood sugar is shut off
        // in one place and none of them is a special case at a call site.
        val glucose = glucoseFor(stack.item)
        if (glucose > 0f) {
            data = AlimentPhysiology.addGlucose(data, glucose, AlimentSymptoms.isGlucoseHeld(player))
        }

        if (data !== before) {
            player.setAttached(AlimentAttachments.DATA, data)
        }

        if (isFoulWater(stack.item)) {
            applyFoulWater(player)
        }
    }

    // ---------------------------------------------------------------- glucose from food

    /**
     * Bread, the one food that spikes blood glucose hardest: +0.7 mmol/L a serving.
     *
     * It is separated from the rest of the plant food because it is the only thing in the game that
     * is pure starch with nothing to slow it down, which is exactly why a real one is worth
     * counting.
     */
    private val BREAD: Set<Item> = setOf(Items.BREAD)

    /**
     * Sweetened fruit drinks, at the same +0.7 as bread.
     *
     * Grapefruit juice is pressed from a plant food, which would earn it the plant figure of +0.4,
     * but a glass has had sugar stirred into it - that is what the recipe is - and sugar is the fast
     * carbohydrate. So it is charged as bread is charged, not as the fruit it came from is.
     */
    private val SWEET_DRINK: Set<Item> = setOf(AlimentItems.GRAPEFRUIT_JUICE)

    /** Raw meat and fish, at the same +0.4 as plant food. */
    private val RAW_MEAT: Set<Item> = setOf(
        Items.BEEF,
        Items.PORKCHOP,
        Items.CHICKEN,
        Items.MUTTON,
        Items.RABBIT,
        Items.COD,
        Items.SALMON,
        Items.TROPICAL_FISH,
        Items.ROTTEN_FLESH,
    )

    /** Cooked meat and fish, at +0.5: the heat has already done part of the digesting. */
    private val COOKED_MEAT: Set<Item> = setOf(
        Items.COOKED_BEEF,
        Items.COOKED_PORKCHOP,
        Items.COOKED_CHICKEN,
        Items.COOKED_MUTTON,
        Items.COOKED_RABBIT,
        Items.COOKED_COD,
        Items.COOKED_SALMON,
    )

    /**
     * Fruit, vegetables, fungi and anything made out of them: +0.4 a serving.
     *
     * The mod's own plant foods are on the list too - a mandrake, a gymnopilus, seaweed and every
     * willow bark soup - because they are plants, and a player who lives off them is living off
     * carbohydrate.
     */
    private val PLANT_FOOD: Set<Item> = setOf(
        Items.APPLE,
        Items.GOLDEN_APPLE,
        Items.ENCHANTED_GOLDEN_APPLE,
        Items.MELON_SLICE,
        Items.SWEET_BERRIES,
        Items.GLOW_BERRIES,
        Items.CARROT,
        Items.GOLDEN_CARROT,
        Items.POTATO,
        Items.BAKED_POTATO,
        Items.POISONOUS_POTATO,
        Items.BEETROOT,
        Items.BEETROOT_SOUP,
        Items.PUMPKIN_PIE,
        Items.COOKIE,
        Items.KELP,
        Items.DRIED_KELP,
        Items.CHORUS_FRUIT,
        Items.MUSHROOM_STEW,
        Items.SUSPICIOUS_STEW,
        Items.RABBIT_STEW,
        AlimentItems.MANDRAKE_FRUIT,
        AlimentItems.MANDRAKE_SEEDS,
        AlimentItems.GYMNOPILUS,
        AlimentItems.COOKED_GYMNOPILUS,
        AlimentItems.SEAWEED,
        AlimentItems.COOKED_SEAWEED,
        AlimentItems.RAW_WILLOW_BARK_SOUP_BOTTLE,
        AlimentItems.RAW_WILLOW_BARK_SOUP_BOWL,
        AlimentItems.WILLOW_BARK_SOUP_BOTTLE,
        AlimentItems.WILLOW_BARK_SOUP_BOWL,
        AlimentItems.CRUDE_SALT_MUSHROOM_STEW,
        AlimentItems.SALT_MUSHROOM_STEW,
        AlimentItems.CRUDE_SALT_WILLOW_BARK_SOUP,
        AlimentItems.SALT_WILLOW_BARK_SOUP,
        AlimentItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP,
        AlimentItems.SALT_RAW_WILLOW_BARK_SOUP,
        AlimentItems.GRAPEFRUIT_SLICE,
        AlimentItems.GRAPE,
    )

    /**
     * What one serving of [item] adds to blood glucose, in mmol/L, and 0 for anything that is not
     * food.
     *
     * The four amounts are the model's; this only says which food is which. Everything that is
     * edible and not meat or bread counts as plant food, so a player cannot dodge the glucose cost
     * of eating by living on berries.
     */
    fun glucoseFor(item: Item): Float = when {
        item in SWEET_DRINK -> AlimentData.GLUCOSE_PER_SWEET_DRINK
        item in BREAD -> AlimentData.GLUCOSE_PER_BREAD
        item in COOKED_MEAT -> AlimentData.GLUCOSE_PER_COOKED_MEAT
        item in RAW_MEAT -> AlimentData.GLUCOSE_PER_RAW_MEAT
        item in PLANT_FOOD -> AlimentData.GLUCOSE_PER_PLANT_FOOD
        // Farmer's Delight's food is charged by the same tiers, so one of its stews costs the body
        // what a vanilla stew costs, and its bread what vanilla bread costs. 0 without the mod.
        else -> FarmersDelightNutrition.glucoseFor(item)
    }

    /**
     * Anything the player drinks, which counts towards the water index.
     *
     * Milk is on the list: it is a drink like any other, so it is worth exactly
     * [AlimentData.WATER_PER_DRINK] - fifteen - and it is the one vanilla drink that arrives with
     * its own `CONSUMABLE` component rather than a `BucketItem` override, which is why it reaches
     * [onItemConsumed] at all. Drinking it still clears the player's status effects, which is
     * vanilla's business and not the model's.
     */
    fun isDrink(item: Item): Boolean = item === Items.POTION ||
        item === Items.MUSHROOM_STEW ||
        item === Items.MILK_BUCKET ||
        item === AlimentItems.WINE ||
        item === AlimentItems.BEER ||
        item === AlimentItems.GRAPE_WINE ||
        item === AlimentItems.GRAPEFRUIT_JUICE ||
        item === AlimentItems.EPHEDRINE ||
        item === AlimentItems.COPTIS_POTION ||
        item === AlimentItems.PHELLODENDRON_POTION ||
        item === AlimentItems.LICORICE_POTION ||
        item === AlimentItems.RAW_WILLOW_BARK_SOUP_BOTTLE ||
        item === AlimentItems.RAW_WILLOW_BARK_SOUP_BOWL ||
        item === AlimentItems.WILLOW_BARK_SOUP_BOTTLE ||
        item === AlimentItems.WILLOW_BARK_SOUP_BOWL ||
        item === AlimentItems.CRUDE_SALT_WATER ||
        item === AlimentItems.SALT_WATER ||
        item === AlimentItems.SWAMP_WATER_BOTTLE ||
        item === AlimentItems.SEA_WATER_BOTTLE ||
        item === AlimentItems.CRUDE_SALT_SWAMP_WATER ||
        item === AlimentItems.SALT_SWAMP_WATER ||
        item === AlimentItems.CRUDE_SALT_SEA_WATER ||
        item === AlimentItems.SALT_SEA_WATER ||
        item === AlimentItems.CRUDE_SALT_MUSHROOM_STEW ||
        item === AlimentItems.SALT_MUSHROOM_STEW ||
        item === AlimentItems.CRUDE_SALT_WILLOW_BARK_SOUP ||
        item === AlimentItems.SALT_WILLOW_BARK_SOUP ||
        item === AlimentItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP ||
        item === AlimentItems.SALT_RAW_WILLOW_BARK_SOUP ||
        // Farmer's Delight's bottled milk, its three sweetened drinks, and its soups - a bowl of
        // stew is drunk as much as eaten, which is how vanilla counts its own. False without it.
        FarmersDelightNutrition.isDrink(item)

    /**
     * Untreated swamp water, and its salted versions, carry the risks below.
     *
     * **Sea water is deliberately not on this list.** It is not contaminated, it is *hypertonic*:
     * drinking it causes no immediate effect at all, and everything it does to the player arrives
     * later and through the sodium it carries (see [SEA_WATER_SODIUM]). A bottle of it is a big
     * enough salt load to push sodium past the safe band on its own, and hypernatraemia is what
     * makes the player thirsty and eventually weak.
     */
    fun isFoulWater(item: Item): Boolean = item === AlimentItems.SWAMP_WATER_BOTTLE ||
        item === AlimentItems.CRUDE_SALT_SWAMP_WATER ||
        item === AlimentItems.SALT_SWAMP_WATER

    /**
     * The risks of drinking water you should not have: swamp water is full of bacteria and settles
     * badly on the stomach.
     *
     * * 30% bacterial infection
     * * 35% nausea (`MobEffects.NAUSEA`)
     * * 5% poisoning (`MobEffects.POISON`)
     *
     * Every one of them lasts 30 seconds. Note that there is no equivalent for sea water - see
     * [isFoulWater].
     */
    private fun applyFoulWater(player: ServerPlayer) {
        val random = player.level().random

        if (random.nextFloat() < FOUL_BACTERIA_CHANCE) {
            val data = player.getAttachedOrCreate(AlimentAttachments.DATA)
            player.setAttached(AlimentAttachments.DATA, AlimentPhysiology.seed(data, bacteria = BACTERIA_SEED))
        }
        if (random.nextFloat() < FOUL_NAUSEA_CHANCE) {
            player.addEffect(MobEffectInstance(MobEffects.NAUSEA, FOUL_EFFECT_TICKS, 0))
        }
        if (random.nextFloat() < FOUL_POISON_CHANCE) {
            player.addEffect(MobEffectInstance(MobEffects.POISON, FOUL_EFFECT_TICKS, 0))
        }
    }

    private fun isWillowSoup(item: Item): Boolean =
        item === AlimentItems.WILLOW_BARK_SOUP_BOTTLE ||
            item === AlimentItems.WILLOW_BARK_SOUP_BOWL ||
            item === AlimentItems.RAW_WILLOW_BARK_SOUP_BOTTLE ||
            item === AlimentItems.RAW_WILLOW_BARK_SOUP_BOWL ||
            item === AlimentItems.CRUDE_SALT_WILLOW_BARK_SOUP ||
            item === AlimentItems.SALT_WILLOW_BARK_SOUP ||
            item === AlimentItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP ||
            item === AlimentItems.SALT_RAW_WILLOW_BARK_SOUP

    /** Applies a dexamethasone injection, unless the body is frozen (see [onItemConsumed]). */
    @JvmStatic
    fun injectDexamethasone(player: ServerPlayer) {
        if (AlimentSymptoms.isFrozen(player)) {
            return
        }
        val data = player.getAttachedOrCreate(AlimentAttachments.DATA)
        player.setAttached(AlimentAttachments.DATA, AlimentPhysiology.inject(data, DEXAMETHASONE_PER_INJECTION))
    }

    /**
     * Applies an insulin aspart injection, unless the body is frozen (see [onItemConsumed]).
     *
     * Unlike the body's own insulin this keeps working at the bottom of the reference range, so a
     * second dose on top of the first is what takes a player hypoglycaemic.
     */
    @JvmStatic
    fun injectInsulin(player: ServerPlayer) {
        if (AlimentSymptoms.isFrozen(player)) {
            return
        }
        val data = player.getAttachedOrCreate(AlimentAttachments.DATA)
        player.setAttached(AlimentAttachments.DATA, AlimentPhysiology.injectInsulin(data))
    }
}
