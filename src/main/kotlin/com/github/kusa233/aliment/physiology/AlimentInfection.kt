package com.github.kusa233.aliment.physiology

import com.github.kusa233.aliment.compat.farmersdelight.FarmersDelightNutrition
import com.github.kusa233.aliment.registry.AlimentItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB

/**
 * Which pathogens a player can pick up, and from what.
 *
 * There are three ways in, and all of them are contagion rather than bad luck:
 *
 * * **Eating something risky.** Raw meat, rotten flesh, poisonous potatoes and raw willow bark soup
 *   carry a 30% chance of seeding a bacterial infection.
 * * **Being touched by a creature.** Anything that walks, swims or flies past within
 *   [CONTACT_RANGE] blocks has a 5% chance per second of passing on a virus. Bats are simply the
 *   mob people notice.
 * * **Having no immune system left.** While inflammation is at or below
 *   [AlimentData.IMMUNOSUPPRESSION_THRESHOLD] the player picks up a random bacterial infection at
 *   15% per second, whether or not anything is nearby. This is what makes an overdose of
 *   dexamethasone - or a long course of willow bark soup - genuinely dangerous.
 */
object AlimentInfection {

    /** Chance that a risky meal infects the player with bacteria. */
    const val BACTERIA_CHANCE = 0.30f

    /** Chance per second that contact with a creature transmits a virus. */
    const val CONTACT_CHANCE = 0.05f

    /** Chance per second that a severely immunosuppressed player picks up bacteria from nowhere. */
    const val IMMUNOSUPPRESSION_CHANCE = 0.15f

    /**
     * How often the two per-second rolls happen, in ticks. Exactly one second: the counter is
     * decremented first and the roll happens when it reaches zero.
     */
    const val ROLL_TICKS = 20

    /** How much pathogen a single successful roll adds. */
    private const val BACTERIA_SEED = 6f
    private const val VIRUS_SEED = 5f

    /**
     * What an opportunistic infection seeds: a random load, so two players who both let their
     * immune system collapse do not get the same illness.
     */
    private const val OPPORTUNISTIC_SEED_MIN = 4f
    private const val OPPORTUNISTIC_SEED_MAX = 12f

    /** How close a creature has to be to count as contact, in blocks. */
    private const val CONTACT_RANGE = 2.0

    /** Raw meat plus the explicitly named risky foods. */
    private val RISKY_FOODS: Set<Item> = setOf(
        Items.BEEF,
        Items.PORKCHOP,
        Items.CHICKEN,
        Items.MUTTON,
        Items.RABBIT,
        Items.COD,
        Items.SALMON,
        Items.TROPICAL_FISH,
        Items.ROTTEN_FLESH,
        Items.POISONOUS_POTATO,
    )

    /**
     * Rolls for a bacterial infection from a meal, and returns the updated data.
     *
     * Called for anything the player swallows, so it has to be cheap when the item is not risky.
     */
    fun rollRiskyFood(player: ServerPlayer, data: AlimentData, stack: ItemStack): AlimentData {
        if (!isRiskyFood(stack.item)) {
            return data
        }
        return if (player.level().random.nextFloat() < BACTERIA_CHANCE) {
            AlimentPhysiology.seed(data, bacteria = BACTERIA_SEED)
        } else {
            data
        }
    }

    fun isRiskyFood(item: Item): Boolean =
        item in RISKY_FOODS || item === AlimentItems.RAW_WILLOW_BARK_SOUP_BOTTLE ||
            item === AlimentItems.RAW_WILLOW_BARK_SOUP_BOWL ||
            item === AlimentItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP ||
            item === AlimentItems.SALT_RAW_WILLOW_BARK_SOUP ||
            // Farmer's Delight's raw dough, raw pasta, chicken cuts and nether salad - the four it
            // tags `c:foods/food_poisoning` - plus every other raw cut it adds. Vanilla raw meat is
            // already on the list above; this is the same rule applied to the same kind of food.
            FarmersDelightNutrition.isRisky(item)

    /**
     * Rolls once a second for a virus from whatever creature is standing next to the player.
     *
     * Any [Mob] counts - a cow, a wolf, a villager, a bat - but not the player themselves, and not
     * decorative entities like armour stands, which are `LivingEntity`s but not `Mob`s.
     */
    fun rollContact(player: ServerPlayer, data: AlimentData, runtime: AlimentRuntime): AlimentData {
        runtime.contactCooldown--
        if (runtime.contactCooldown > 0) {
            return data
        }
        runtime.contactCooldown = ROLL_TICKS

        val box: AABB = player.boundingBox.inflate(CONTACT_RANGE)
        // No predicate needed: a player is not a Mob, so the player can never be in this list.
        val creatures = player.level().getEntitiesOfClass(Mob::class.java, box)
        if (creatures.isEmpty()) {
            return data
        }
        return if (contactRoll(player.level().random)) {
            AlimentPhysiology.seed(data, virus = VIRUS_SEED)
        } else {
            data
        }
    }

    /**
     * Rolls once a second for an opportunistic bacterial infection while the immune system is
     * suppressed.
     *
     * Nothing has to be nearby and nothing has to have been eaten: this is the body's own flora
     * getting in, which is exactly what "immunosuppressed" means.
     */
    fun rollImmunosuppression(player: ServerPlayer, data: AlimentData, runtime: AlimentRuntime): AlimentData {
        if (!data.isImmunosuppressed) {
            // Not suppressed: leave the counter ready so the first suppressed tick rolls at once.
            runtime.immunosuppressionCooldown = 0
            return data
        }
        runtime.immunosuppressionCooldown--
        if (runtime.immunosuppressionCooldown > 0) {
            return data
        }
        runtime.immunosuppressionCooldown = ROLL_TICKS

        val random = player.level().random
        if (!opportunisticRoll(random)) {
            return data
        }
        return AlimentPhysiology.seed(data, bacteria = opportunisticLoad(random))
    }

    /**
     * The two per-second dice, split out so that the odds can be tested without a world - a
     * headless dev server has no entity-ticking chunks, so it cannot put a cow next to anybody.
     */
    fun contactRoll(random: RandomSource): Boolean = random.nextFloat() < CONTACT_CHANCE

    fun opportunisticRoll(random: RandomSource): Boolean = random.nextFloat() < IMMUNOSUPPRESSION_CHANCE

    /** The load an opportunistic infection seeds, somewhere in [OPPORTUNISTIC_SEED_MIN]..[MAX]. */
    fun opportunisticLoad(random: RandomSource): Float =
        OPPORTUNISTIC_SEED_MIN + random.nextFloat() * (OPPORTUNISTIC_SEED_MAX - OPPORTUNISTIC_SEED_MIN)
}
