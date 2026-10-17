package com.github.kusa233.aliment.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike

/**
 * aliment owns a single creative tab so the mod's content stays in one place as it grows.
 *
 * The tab sits in the top row right after the seven vanilla slots. Its title is localised as
 * `itemGroup.Aliment.main` (see `assets/aliment/lang`).
 */
object AlimentCreativeTabs {

    /** Placed after the seven vanilla top-row tab slots. */
    private const val TAB_COLUMN = 7

    private val TAB_KEY: ResourceKey<CreativeModeTab> =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Registration.id("main"))

    /**
     * Everything the mod can currently show, in the order it appears in the tab.
     *
     * Blocks without an item form (`willow_vines_plant`, the wall signs, the potted sapling and
     * the soup cauldron) are deliberately absent because they cannot be held.
     */
    private val CONTENT: List<ItemLike> = listOf(
        // logs and bark
        AlimentBlocks.WILLOW_LOG,
        AlimentBlocks.WILLOW_WOOD,
        AlimentBlocks.STRIPPED_WILLOW_LOG,
        AlimentBlocks.STRIPPED_WILLOW_WOOD,
        AlimentItems.WILLOW_BARK,
        AlimentItems.WILLOW_BARK_PIECES,
        // planks and shaped blocks
        AlimentBlocks.WILLOW_PLANKS,
        AlimentBlocks.WILLOW_STAIRS,
        AlimentBlocks.WILLOW_SLAB,
        AlimentBlocks.WILLOW_FENCE,
        AlimentBlocks.WILLOW_FENCE_GATE,
        AlimentBlocks.WILLOW_DOOR,
        AlimentBlocks.WILLOW_TRAPDOOR,
        AlimentBlocks.WILLOW_PRESSURE_PLATE,
        AlimentBlocks.WILLOW_BUTTON,
        AlimentBlocks.WILLOW_SHELF,
        // foliage
        AlimentBlocks.WILLOW_LEAVES,
        AlimentBlocks.WILLOW_SAPLING,
        AlimentBlocks.WILLOW_VINES,
        // signs and boats
        AlimentItems.WILLOW_SIGN,
        AlimentItems.WILLOW_HANGING_SIGN,
        AlimentItems.WILLOW_BOAT,
        AlimentItems.WILLOW_CHEST_BOAT,
        // soups
        AlimentItems.RAW_WILLOW_BARK_SOUP_BOTTLE,
        AlimentItems.RAW_WILLOW_BARK_SOUP_BOWL,
        AlimentItems.WILLOW_BARK_SOUP_BOTTLE,
        AlimentItems.WILLOW_BARK_SOUP_BOWL,
        // salt chain
        AlimentBlocks.ROCK_SALT_ORE,
        AlimentItems.CRUSHED_ROCK_SALT,
        AlimentItems.CRUDE_SALT,
        AlimentItems.CRUDE_SALT_POWDER,
        AlimentItems.SALT_POWDER,
        AlimentItems.STIRRING_ROD,
        AlimentItems.DEXAMETHASONE_INJECTION,
        // the glucose chain
        AlimentItems.INSULIN_INJECTION,
        AlimentItems.GLUCOSE_METER,
        AlimentItems.GLUCOSE_TEST_STRIP,
        AlimentItems.BLOODIED_TEST_STRIP,
        AlimentItems.MICRONEEDLE,
        AlimentItems.CRUDE_SALT_WATER,
        AlimentItems.SALT_WATER,
        AlimentItems.SWAMP_WATER_BOTTLE,
        AlimentItems.SEA_WATER_BOTTLE,
        AlimentItems.CRUDE_SALT_SWAMP_WATER,
        AlimentItems.SALT_SWAMP_WATER,
        AlimentItems.CRUDE_SALT_SEA_WATER,
        AlimentItems.SALT_SEA_WATER,
        AlimentItems.CRUDE_SALT_MUSHROOM_STEW,
        AlimentItems.SALT_MUSHROOM_STEW,
        AlimentItems.CRUDE_SALT_WILLOW_BARK_SOUP,
        AlimentItems.SALT_WILLOW_BARK_SOUP,
        AlimentItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP,
        AlimentItems.SALT_RAW_WILLOW_BARK_SOUP,
        // mandrake: the plant has no item form, so the tab carries the fruit and the seeds
        AlimentItems.MANDRAKE_FRUIT,
        AlimentItems.MANDRAKE_SEEDS,
        // grapes: the fruit and the seeds, then the juice and the wine they become
        AlimentItems.GRAPE,
        AlimentItems.GRAPE_SEEDS,
        AlimentItems.GRAPE_JUICE,
        AlimentItems.GRAPE_WINE,
        // gymnopilus: raw and cooked, the second one only food
        AlimentItems.GYMNOPILUS,
        AlimentItems.COOKED_GYMNOPILUS,
        // brewing & distillation
        AlimentBlocks.FERMENTATION_TANK,
        AlimentBlocks.CONDENSER_PIPE,
        // the apothecary's job site
        AlimentBlocks.MEDICINE_CABINET,
        AlimentItems.BREWER_YEAST,
        AlimentItems.WINE,
        AlimentItems.BEER,
        // ephedra & ephedrine
        AlimentItems.EPHEDRA,
        AlimentItems.CRUSHED_EPHEDRA,
        AlimentItems.EPHEDRINE,
        // traditional herbs (coptis, phellodendron, licorice)
        AlimentItems.COPTIS,
        AlimentItems.CRUSHED_COPTIS,
        AlimentItems.COPTIS_POTION,
        AlimentItems.PHELLODENDRON,
        AlimentItems.CRUSHED_PHELLODENDRON,
        AlimentItems.PHELLODENDRON_POTION,
        AlimentItems.LICORICE,
        AlimentItems.CRUSHED_LICORICE,
        AlimentItems.LICORICE_POTION,
        // seaweed
        AlimentItems.SEAWEED,
        AlimentItems.COOKED_SEAWEED,
        AlimentItems.CRUSHED_SEAWEED,
        AlimentItems.SEAWEED_IODIZED_SALT,
        // grapefruit: the wood set first, then the tree and the fruit
        AlimentBlocks.GRAPEFRUIT_LOG,
        AlimentBlocks.GRAPEFRUIT_WOOD,
        AlimentBlocks.STRIPPED_GRAPEFRUIT_LOG,
        AlimentBlocks.STRIPPED_GRAPEFRUIT_WOOD,
        AlimentBlocks.GRAPEFRUIT_PLANKS,
        AlimentBlocks.GRAPEFRUIT_STAIRS,
        AlimentBlocks.GRAPEFRUIT_SLAB,
        AlimentBlocks.GRAPEFRUIT_FENCE,
        AlimentBlocks.GRAPEFRUIT_FENCE_GATE,
        AlimentBlocks.GRAPEFRUIT_DOOR,
        AlimentBlocks.GRAPEFRUIT_TRAPDOOR,
        AlimentBlocks.GRAPEFRUIT_PRESSURE_PLATE,
        AlimentBlocks.GRAPEFRUIT_BUTTON,
        AlimentBlocks.GRAPEFRUIT_SHELF,
        AlimentItems.GRAPEFRUIT_SIGN,
        AlimentItems.GRAPEFRUIT_HANGING_SIGN,
        AlimentItems.GRAPEFRUIT_BOAT,
        AlimentItems.GRAPEFRUIT_CHEST_BOAT,
        AlimentBlocks.GRAPEFRUIT_LEAVES,
        AlimentBlocks.GRAPEFRUIT_SAPLING,
        AlimentBlocks.GRAPEFRUIT,
        AlimentItems.GRAPEFRUIT_SLICE,
        AlimentItems.GRAPEFRUIT_JUICE,
    )

    /**
     * Touching this object at all runs the object initialiser, which is what registers the tab
     * in [BuiltInRegistries.CREATIVE_MODE_TAB].
     */
    val MAIN: CreativeModeTab = Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB,
        TAB_KEY,
        CreativeModeTab.builder(CreativeModeTab.Row.TOP, TAB_COLUMN)
            .title(Component.translatable("itemGroup.aliment.main"))
            .icon { ItemStack(AlimentBlocks.WILLOW_SAPLING) }
            .displayItems { _, output ->
                CONTENT.forEach { itemLike ->
                    if (itemLike === AlimentItems.WINE) {
                        output.accept(AlimentItems.createWine(0.07f))
                        output.accept(AlimentItems.createWine(0.40f))
                        output.accept(AlimentItems.createGrapeWine())
                        output.accept(AlimentItems.createAlcohol(0.75f))
                        output.accept(AlimentItems.createAlcohol(0.98f))
                    } else {
                        output.accept(itemLike)
                    }
                }
            }
            .build(),
    )

    fun initialize() {
    }
}
