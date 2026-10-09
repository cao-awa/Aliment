package com.github.kusa233.aliment.dev

import com.github.kusa233.aliment.advancement.AlimentAdvancements
import com.github.kusa233.aliment.compat.farmersdelight.FarmersDelightNutrition
import com.github.kusa233.aliment.compat.farmersdelight.FarmersDelightRecipes
import com.github.kusa233.aliment.physiology.AlimentAttachments
import com.github.kusa233.aliment.physiology.AlimentData
import com.github.kusa233.aliment.physiology.TraceElements
import com.github.kusa233.aliment.registry.AlimentBlocks
import com.github.kusa233.aliment.registry.AlimentItems
import com.github.kusa233.aliment.registry.AlimentWorldGen
import com.github.kusa233.aliment.registry.Registration
import com.github.kusa233.aliment.world.AlimentGrinding
import com.github.kusa233.aliment.world.block.AlcoholCauldronBlock
import com.github.kusa233.aliment.world.block.BeerCauldronBlock
import com.github.kusa233.aliment.world.block.BrineCauldronBlock
import com.github.kusa233.aliment.world.block.CondenserPipeBlock
import com.github.kusa233.aliment.world.block.EphedraBlock
import com.github.kusa233.aliment.world.block.FermentationTankBlock
import com.github.kusa233.aliment.world.block.FermentationTankBlockEntity
import com.github.kusa233.aliment.world.block.GrapeVineBlock
import com.github.kusa233.aliment.world.block.MandrakeBlock
import com.github.kusa233.aliment.world.block.WillowSoupCauldronBlock
import com.github.kusa233.aliment.world.item.BeerItem
import com.github.kusa233.aliment.world.item.GrapeWineItem
import com.github.kusa233.aliment.world.item.WineItem
import com.github.kusa233.aliment.world.recipe.ShearEphedraRecipe
import net.minecraft.network.chat.contents.TranslatableContents
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.FakePlayer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.advancements.AdvancementType
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.BoneMealItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.item.crafting.AbstractCookingRecipe
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
import kotlin.math.abs

/**
 * DEVELOPMENT ONLY. Temporary entrypoint used to verify the willow features on a headless dev
 * server; it is not shipped. Remove the entrypoint from fabric.mod.json to disable it.
 *
 * Everything happens in chunk (0,0) at y=200 so it works in any world.
 *
 * NOTE: this must stay a plain class - Fabric instantiates entrypoints through a public no-arg
 * constructor, which a Kotlin `object` does not have.
 */
class AlimentSelfTest : ModInitializer {

    private val logger = LogManager.getLogger("AlimentSelfTest")

    private val FLOOR_Y = 200
    private val CENTER = BlockPos(8, FLOOR_Y + 1, 8)
    private val CAULDRON = BlockPos(4, FLOOR_Y + 1, 4)
    private val BRINE_PROBE = BlockPos(1, FLOOR_Y + 1, 4)
    private val GRINDSTONE = BlockPos(10, FLOOR_Y + 1, 4)
    private val LOG = BlockPos(4, FLOOR_Y + 1, 10)
    private val TREE = BlockPos(8, FLOOR_Y + 1, 8)

    private var ticks = 0
    private var stage = 0
    private var passed = 0
    private var failed = 0

    /** Whether Farmer's Delight is installed. Read once; the loader's answer never changes. */
    private val fdLoaded = FabricLoader.getInstance().isModLoaded("farmersdelight")
    private var cookStartTick = -1
    private var leaningChecked = false
    private var filledWhileRaw = false

    override fun onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            ticks++
            if (ticks < 60 || stage > 4) {
                return@register
            }
            try {
                step(server.overworld())
            } catch (t: Throwable) {
                logger.error("SELFTEST exception in stage {}", stage, t)
                failed++
                stage = 99
                report()
            }
        }
    }

    private fun step(level: ServerLevel) {
        when (stage) {
            0 -> {
                buildRig(level)
                stage = 1
            }

            1 -> {
                testStripping(level)
                testGrinding(level)
                testCauldronFill(level)
                // The campfire is placed in the next stage's tick so cooking starts cleanly.
                level.setBlockAndUpdate(CAULDRON.below(), Blocks.CAMPFIRE.defaultBlockState())
                cookStartTick = ticks
                stage = 2
            }

            2 -> {
                if (ticks - cookStartTick >= WillowSoupCauldronBlock.COOK_TICKS + 40) {
                    testCooked(level)
                    stage = 3
                }
            }

            3 -> {
                testLeaning(level)
                testMandrake(level, FakePlayer.get(level))
                testMandrakeWorldGen(level)
                testGymnopilusCooking(level)
                testAdvancements(level, FakePlayer.get(level))
                testFermentationAndDistillation(level, FakePlayer.get(level))
                testEphedra(level, FakePlayer.get(level))
                testGrapeVine(level, FakePlayer.get(level))
                testGrapeWine(level, FakePlayer.get(level))
                testGrapeTags(level)
                testFarmersDelight(level, FakePlayer.get(level))
                stage = 4
            }

            4 -> {
                report()
                stage = 99
            }
        }
    }

    // ------------------------------------------------------------------ rig

    private fun buildRig(level: ServerLevel) {
        level.setChunkForced(0, 0, true)
        level.getChunk(0, 0)
        for (x in 0..15) {
            for (z in 0..15) {
                level.setBlockAndUpdate(BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState())
                for (y in 1..20) {
                    level.setBlockAndUpdate(BlockPos(x, FLOOR_Y + y, z), Blocks.AIR.defaultBlockState())
                }
            }
        }
        level.setBlockAndUpdate(LOG, AlimentBlocks.WILLOW_LOG.defaultBlockState())
        level.setBlockAndUpdate(GRINDSTONE, Blocks.GRINDSTONE.defaultBlockState())
        level.setBlockAndUpdate(
            CAULDRON,
            Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3),
        )
        logger.info("SELFTEST rig built")
    }

    // ------------------------------------------------------------------ individual checks

    private fun testStripping(level: ServerLevel) {
        val player = FakePlayer.get(level)
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.DIAMOND_AXE))
        useOn(level, player, LOG)

        val stripped = level.getBlockState(LOG).`is`(AlimentBlocks.STRIPPED_WILLOW_LOG)
        check("axe stripping turns the log into stripped_willow_log", stripped)
    }

    private fun testGrinding(level: ServerLevel) {
        val player = FakePlayer.get(level)
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.WILLOW_BARK, 2))
        // The instant conversion is the sneak-right-click shortcut; a plain click opens the menu.
        player.isShiftKeyDown = true
        useOn(level, player, GRINDSTONE)
        player.isShiftKeyDown = false

        val held = player.mainHandItem
        val pieces = player.inventory.countItem(AlimentItems.WILLOW_BARK_PIECES) +
            (if (held.`is`(AlimentItems.WILLOW_BARK_PIECES)) held.count else 0)
        check("grinding a willow bark yields bark pieces", pieces >= 2)
    }

    private fun testCauldronFill(level: ServerLevel) {
        val player = FakePlayer.get(level)
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.WILLOW_BARK_PIECES, 1))
        useOn(level, player, CAULDRON)

        val soup = level.getBlockState(CAULDRON)
        check(
            "bark pieces turn a water cauldron into raw willow bark soup",
            soup.`is`(AlimentBlocks.WILLOW_SOUP_CAULDRON) && !soup.getValue(WillowSoupCauldronBlock.COOKED),
        )

        // take one serving while it is still raw
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GLASS_BOTTLE))
        useOn(level, player, CAULDRON)
        val held = player.mainHandItem
        filledWhileRaw = held.`is`(AlimentItems.RAW_WILLOW_BARK_SOUP_BOTTLE)
        check("a glass bottle fills with raw willow bark soup", filledWhileRaw)

        testBrineRefusesWater(level, player)
    }

    /**
     * Brine must not take water once it is evaporating.
     *
     * This is the one Aliment cauldron that used to inherit `AbstractCauldronBlock.useItemOn`, and
     * that inherited implementation reaches vanilla's `EMPTY` dispatcher - which replaces the whole
     * block with a full water cauldron. The check is therefore not merely that the level did not
     * rise: it is that the brine cauldron is still there at all. The lava bucket is in the same
     * inherited dispatcher, so it is checked too: the fix is that the dispatcher never runs, not
     * that water in particular is special-cased.
     */
    private fun testBrineRefusesWater(level: ServerLevel, player: FakePlayer) {
        level.setBlockAndUpdate(BRINE_PROBE, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3))
        level.setBlockAndUpdate(BRINE_PROBE.below(), Blocks.STONE.defaultBlockState())

        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.CRUDE_SALT_POWDER))
        useOn(level, player, BRINE_PROBE)
        check(
            "crude salt powder turns a water cauldron into brine",
            level.getBlockState(BRINE_PROBE).`is`(AlimentBlocks.BRINE_CAULDRON),
        )

        fun brineStage(): Int? =
            (level.getBlockState(BRINE_PROBE).block as? BrineCauldronBlock)
                ?.let { level.getBlockState(BRINE_PROBE).getValue(BrineCauldronBlock.STAGE) }

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
        useOn(level, player, BRINE_PROBE)
        check("a water bucket cannot top up a brine cauldron", brineStage() != null)
        check("and an untouched brine is still stage 0", brineStage() == 0)

        // Move it on, so the next refusal is a genuinely mid-process one.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.STIRRING_ROD))
        useOn(level, player, BRINE_PROBE)
        check("the stirring rod still advances the brine", brineStage() == 1)

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
        useOn(level, player, BRINE_PROBE)
        check("water is still refused part-way through evaporating", brineStage() == 1)

        player.setItemInHand(
            InteractionHand.MAIN_HAND,
            PotionContents.createItemStack(Items.POTION, Potions.WATER),
        )
        useOn(level, player, BRINE_PROBE)
        check("a water bottle cannot top it up either", brineStage() == 1)

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.LAVA_BUCKET))
        useOn(level, player, BRINE_PROBE)
        check("nor can a lava bucket, which the same dispatcher would have taken", brineStage() == 1)

        level.setBlockAndUpdate(BRINE_PROBE, Blocks.AIR.defaultBlockState())

        testNoCauldronTakesWater(level, player)
    }

    /**
     * Every Aliment cauldron refuses a water bucket, whichever one it is.
     *
     * The brine test above is the detailed one, because brine is the cauldron that actually had the
     * bug. This is the generalisation: all four are built on vanilla's `EMPTY` dispatcher, so all
     * four would be replaced by a full water cauldron if they ever stopped overriding `useItemOn`.
     * That is a one-line mistake to make and an easy one to make silently, so each is checked by
     * name rather than trusting that three of them happen to be safe today.
     */
    private fun testNoCauldronTakesWater(level: ServerLevel, player: FakePlayer) {
        val cauldrons = linkedMapOf(
            "willow bark soup" to AlimentBlocks.WILLOW_SOUP_CAULDRON,
            "brine" to AlimentBlocks.BRINE_CAULDRON,
            "alcohol" to AlimentBlocks.ALCOHOL_CAULDRON,
            "beer" to AlimentBlocks.BEER_CAULDRON,
        )

        for ((label, block) in cauldrons) {
            level.setBlockAndUpdate(BRINE_PROBE, block.defaultBlockState())
            val placed = level.getBlockState(BRINE_PROBE)
            if (!placed.`is`(block)) {
                check("a $label cauldron can be placed for the water test", false)
                continue
            }

            player.inventory.clearContent()
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
            useOn(level, player, BRINE_PROBE)
            check(
                "a water bucket does not replace the $label cauldron",
                level.getBlockState(BRINE_PROBE).`is`(block),
            )

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.LAVA_BUCKET))
            useOn(level, player, BRINE_PROBE)
            check(
                "and neither does a lava bucket on the $label cauldron",
                level.getBlockState(BRINE_PROBE).`is`(block),
            )
        }

        level.setBlockAndUpdate(BRINE_PROBE, Blocks.AIR.defaultBlockState())
    }

    private fun testCooked(level: ServerLevel) {
        val state = level.getBlockState(CAULDRON)
        if (!state.`is`(AlimentBlocks.WILLOW_SOUP_CAULDRON)) {
            check("cauldron still holds soup after cooking", false)
            return
        }
        check("60 seconds over a campfire cooks the soup", state.getValue(WillowSoupCauldronBlock.COOKED))

        val player = FakePlayer.get(level)
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.BOWL))
        useOn(level, player, CAULDRON)
        check("a bowl fills with cooked willow bark soup", player.mainHandItem.`is`(AlimentItems.WILLOW_BARK_SOUP_BOWL))

        // no campfire underneath any more -> a fresh raw cauldron must stay raw
        level.setBlockAndUpdate(CAULDRON.below(), Blocks.STONE.defaultBlockState())
    }

    /**
     * Builds a dirt bank with a pool of water to the east, grows a willow next to it and checks
     * that the trunk actually leans towards the water.
     */
    private fun testLeaning(level: ServerLevel) {
        if (leaningChecked) {
            return
        }
        leaningChecked = true

        for (x in 0..15) {
            for (z in 10..15) {
                level.setBlockAndUpdate(BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState())
                for (y in 1..20) {
                    level.setBlockAndUpdate(BlockPos(x, FLOOR_Y + y, z), Blocks.AIR.defaultBlockState())
                }
            }
        }
        // water pool on the +X side
        for (x in 12..15) {
            for (z in 10..15) {
                level.setBlockAndUpdate(BlockPos(x, FLOOR_Y, z), Blocks.WATER.defaultBlockState())
            }
        }

        // 26.3 removed ConfiguredFeature: the feature itself is now the registry entry.
        val feature = level.registryAccess()
            .lookupOrThrow(Registries.FEATURE)
            .get(ResourceKey.create(Registries.FEATURE, Registration.id("willow")))
            .orElse(null)

        if (feature == null) {
            check("aliment:willow feature is registered", false)
            return
        }

        val origin = BlockPos(6, FLOOR_Y + 1, 12)
        var maxX = origin.x
        var grew = false
        for (attempt in 0 until 40) {
            clearArea(level, origin)
            val placed = feature.value()
                .place(level, level.chunkSource.generator, level.random, origin)
            if (placed) {
                grew = true
                maxX = scanMaxLogX(level, origin)
                if (maxX > origin.x) {
                    break
                }
            }
        }
        check("willow grows on the test bank", grew)
        check("willow trunk leans towards the water (max log x=$maxX > ${origin.x})", maxX > origin.x)
    }

    private fun clearArea(level: ServerLevel, origin: BlockPos) {
        for (x in (origin.x - 4)..(origin.x + 8)) {
            for (z in (origin.z - 4)..(origin.z + 4)) {
                for (y in 1..20) {
                    level.setBlockAndUpdate(BlockPos(x, FLOOR_Y + y, z), Blocks.AIR.defaultBlockState())
                }
            }
        }
    }

    private fun scanMaxLogX(level: ServerLevel, origin: BlockPos): Int {
        var maxX = origin.x
        for (x in (origin.x - 6)..(origin.x + 10)) {
            for (z in (origin.z - 6)..(origin.z + 6)) {
                for (y in 1..24) {
                    if (level.getBlockState(BlockPos(x, FLOOR_Y + y, z)).`is`(AlimentBlocks.WILLOW_LOG)) {
                        if (x > maxX) {
                            maxX = x
                        }
                    }
                }
            }
        }
        return maxX
    }

    // ------------------------------------------------------------------ mandrake

    /**
     * The mandrake plant: sown into soil rather than onto farmland, grown through four stages with
     * bone meal, and fruit only from the fourth.
     *
     * Sowing goes through the real item path (`BlockItem.useOn`, via the fake player), because "a
     * mandrake grows on dirt and grass but not on farmland" is a placement rule and not a property
     * of the block state - the only way to catch a regression in it is to actually plant one.
     */
    private fun testMandrake(level: ServerLevel, player: FakePlayer) {
        val seeds = ItemStack(AlimentItems.MANDRAKE_SEEDS)
        val row = listOf(
            "dirt" to Blocks.DIRT,
            "grass" to Blocks.GRASS_BLOCK,
            "farmland" to Blocks.FARMLAND,
            "coarse dirt" to Blocks.COARSE_DIRT,
        )
        val planted = mutableMapOf<String, Boolean>()
        row.forEachIndexed { index, (label, soil) ->
            val ground = BlockPos(12 + index, FLOOR_Y + 1, 12)
            level.setBlockAndUpdate(ground, soil.defaultBlockState())
            level.setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState())
            level.setBlockAndUpdate(ground.below(), Blocks.STONE.defaultBlockState())

            player.inventory.clearContent()
            player.setItemInHand(InteractionHand.MAIN_HAND, seeds.copy())
            useOn(level, player, ground)
            planted[label] = level.getBlockState(ground.above()).`is`(AlimentBlocks.MANDRAKE)
        }
        logger.info("SELFTEST mandrake sown: {}", planted)
        check("a mandrake seed is sown on dirt", planted["dirt"] == true)
        check("and on grass", planted["grass"] == true)
        check("and on coarse dirt", planted["coarse dirt"] == true)
        check("and on farmland too", planted["farmland"] == true)

        // Bone meal one stage at a time, all the way to fruit.
        val plant = BlockPos(12, FLOOR_Y + 1, 14)
        level.setBlockAndUpdate(plant.below(), Blocks.DIRT.defaultBlockState())
        level.setBlockAndUpdate(plant.below(2), Blocks.STONE.defaultBlockState())
        level.setBlockAndUpdate(plant, AlimentBlocks.MANDRAKE.defaultBlockState())
        val ages = mutableListOf(age(level, plant))
        repeat(MandrakeBlock.MAX_AGE) {
            BoneMealItem.growCrop(ItemStack(Items.BONE_MEAL), level, plant)
            ages += age(level, plant)
        }
        logger.info("SELFTEST mandrake stages after bone meal: {}", ages)
        check("bone meal takes a mandrake through four stages", ages == listOf(0, 1, 2, 3))
        check("a ripe mandrake is no longer a bonemeal target", !BoneMealItem.growCrop(ItemStack(Items.BONE_MEAL), level, plant))

        val ripe = Block.getDrops(level.getBlockState(plant), level, plant, null)
        logger.info("SELFTEST ripe mandrake drops: {}", ripe.map { "${it.item} x${it.count}" })
        val fruit = ripe.filter { it.`is`(AlimentItems.MANDRAKE_FRUIT) }.sumOf { it.count }
        check("a ripe mandrake drops fruit", fruit in 1..2)
        check("and nothing else", ripe.all { it.`is`(AlimentItems.MANDRAKE_FRUIT) })

        // Every earlier stage is a waste of a seed, which is the whole point of waiting.
        var unripeDrops = 0
        for (unripe in 0 until MandrakeBlock.MAX_AGE) {
            level.setBlockAndUpdate(
                plant,
                AlimentBlocks.MANDRAKE.defaultBlockState().setValue(MandrakeBlock.AGE, unripe),
            )
            unripeDrops += Block.getDrops(level.getBlockState(plant), level, plant, null).size
        }
        check("an unripe mandrake drops nothing", unripeDrops == 0)

        // The seeds are a crafting recipe, so it has to be in the recipe manager rather than merely
        // in the file: this fails if the JSON is malformed or the id is wrong.
        val recipe = level.server.recipeManager.byKey(
            ResourceKey.create(Registries.RECIPE, Registration.id("mandrake_seeds")),
        )
        check("the mandrake seeds recipe is loaded", recipe.isPresent)
    }

    private fun age(level: ServerLevel, pos: BlockPos): Int =
        level.getBlockState(pos).getValue(MandrakeBlock.AGE)

    // ------------------------------------------------------------------ grape vine

    /**
     * The grape vine: sown into soil like the mandrake, four stages, and grapes only from the last.
     *
     * The vine is the only source of grapes, and the grapes are the only source of the seeds, so a
     * vine that drops nothing when ripe would make the whole chain unreachable. That is why the drop
     * check and the seed recipe are here rather than left to a datapack validation.
     */
    private fun testGrapeVine(level: ServerLevel, player: FakePlayer) {
        val row = listOf(
            "dirt" to Blocks.DIRT,
            "grass" to Blocks.GRASS_BLOCK,
            "farmland" to Blocks.FARMLAND,
            "coarse dirt" to Blocks.COARSE_DIRT,
        )
        val planted = mutableMapOf<String, Boolean>()
        row.forEachIndexed { index, (label, soil) ->
            val ground = BlockPos(10 + index, FLOOR_Y + 1, 2)
            level.setBlockAndUpdate(ground, soil.defaultBlockState())
            level.setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState())
            level.setBlockAndUpdate(ground.below(), Blocks.STONE.defaultBlockState())

            player.inventory.clearContent()
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.GRAPE_SEEDS))
            useOn(level, player, ground)
            planted[label] = level.getBlockState(ground.above()).`is`(AlimentBlocks.GRAPE_VINE)
        }
        logger.info("SELFTEST grape vine sown: {}", planted)
        check("a grape seed is sown on dirt", planted["dirt"] == true)
        check("and on grass", planted["grass"] == true)
        check("and on farmland too", planted["farmland"] == true)
        check("and on coarse dirt", planted["coarse dirt"] == true)

        val vine = BlockPos(10, FLOOR_Y + 1, 3)
        level.setBlockAndUpdate(vine.below(), Blocks.DIRT.defaultBlockState())
        level.setBlockAndUpdate(vine.below(2), Blocks.STONE.defaultBlockState())
        level.setBlockAndUpdate(vine, AlimentBlocks.GRAPE_VINE.defaultBlockState())
        val ages = mutableListOf(level.getBlockState(vine).getValue(GrapeVineBlock.AGE))
        repeat(GrapeVineBlock.MAX_AGE) {
            BoneMealItem.growCrop(ItemStack(Items.BONE_MEAL), level, vine)
            ages += level.getBlockState(vine).getValue(GrapeVineBlock.AGE)
        }
        logger.info("SELFTEST grape vine stages after bone meal: {}", ages)
        check("bone meal takes a grape vine through four stages", ages == listOf(0, 1, 2, 3))
        check("a ripe grape vine is no longer a bonemeal target", !BoneMealItem.growCrop(ItemStack(Items.BONE_MEAL), level, vine))

        val ripe = Block.getDrops(level.getBlockState(vine), level, vine, null)
        logger.info("SELFTEST ripe grape vine drops: {}", ripe.map { "${it.item} x${it.count}" })
        val grapes = ripe.filter { it.`is`(AlimentItems.GRAPE) }.sumOf { it.count }
        check("a ripe grape vine drops grapes", grapes in 1..3)
        check("and nothing else", ripe.all { it.`is`(AlimentItems.GRAPE) })

        // A vine picked green is a wasted seed, which is what makes waiting for the fruit matter.
        var unripeDrops = 0
        for (unripe in 0 until GrapeVineBlock.MAX_AGE) {
            level.setBlockAndUpdate(
                vine,
                AlimentBlocks.GRAPE_VINE.defaultBlockState().setValue(GrapeVineBlock.AGE, unripe),
            )
            unripeDrops += Block.getDrops(level.getBlockState(vine), level, vine, null).size
        }
        check("an unripe grape vine drops nothing", unripeDrops == 0)

        // Picking is the other half of the harvest: a ripe vine is right-clicked rather than broken,
        // and the point is that the plant survives it. Count what actually lands on the ground
        // instead of trusting the return value, so a harvest that reported success without dropping
        // anything would still fail here.
        fun grapesOnGround(): Int =
            level.getEntitiesOfClass(ItemEntity::class.java, AABB.ofSize(Vec3.atCenterOf(vine), 8.0, 8.0, 8.0))
                .filter { it.item.`is`(AlimentItems.GRAPE) }
                .sumOf { it.item.count }

        fun ripen() = level.setBlockAndUpdate(
            vine,
            AlimentBlocks.GRAPE_VINE.defaultBlockState().setValue(GrapeVineBlock.AGE, GrapeVineBlock.MAX_AGE),
        )

        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        ripen()
        val groundBefore = grapesOnGround()
        val picked = useOn(level, player, vine)
        logger.info("SELFTEST grape vine picked: {} grapes", grapesOnGround() - groundBefore)
        check("right clicking a ripe grape vine succeeds", picked.consumesAction())
        check("picking a ripe grape vine yields a bunch", (grapesOnGround() - groundBefore) in 1..3)
        check("and leaves the vine standing", level.getBlockState(vine).`is`(AlimentBlocks.GRAPE_VINE))
        check(
            "a picked vine falls back to stage 1 rather than being spent",
            level.getBlockState(vine).getValue(GrapeVineBlock.AGE) == 1,
        )

        // Falling back to 1 only means anything if the vine fruits again, which is the whole reason
        // the harvest stops one stage short of ripe instead of resetting to a seedling.
        repeat(2) { BoneMealItem.growCrop(ItemStack(Items.BONE_MEAL), level, vine) }
        check("a picked vine ripens again", level.getBlockState(vine).getValue(GrapeVineBlock.AGE) == GrapeVineBlock.MAX_AGE)
        val groundBeforeSecond = grapesOnGround()
        useOn(level, player, vine)
        check("and can be picked a second time", (grapesOnGround() - groundBeforeSecond) in 1..3)
        check("a second pick also falls back to stage 1", level.getBlockState(vine).getValue(GrapeVineBlock.AGE) == 1)

        // A green vine has nothing to give, and the click has to fall through rather than be
        // swallowed - otherwise the harvest branch would eat every right click on a growing vine.
        level.setBlockAndUpdate(vine, AlimentBlocks.GRAPE_VINE.defaultBlockState())
        val groundBeforeGreen = grapesOnGround()
        val greenClick = useOn(level, player, vine)
        check("a green grape vine yields nothing to a right click", grapesOnGround() == groundBeforeGreen)
        check("and the click is not swallowed by the harvest", !greenClick.consumesAction())
        check("and the green vine is left alone", level.getBlockState(vine).getValue(GrapeVineBlock.AGE) == 0)

        // The harvest branch must not swallow bone meal either. Driving it through the interaction
        // path rather than calling BoneMealItem directly is what makes this a real guard: the
        // four-stage assertion above would still pass if a ripe-vine click ate the meal.
        player.inventory.clearContent()
        val mealAges = mutableListOf(level.getBlockState(vine).getValue(GrapeVineBlock.AGE))
        repeat(GrapeVineBlock.MAX_AGE) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.BONE_MEAL))
            useOn(level, player, vine)
            mealAges += level.getBlockState(vine).getValue(GrapeVineBlock.AGE)
        }
        logger.info("SELFTEST grape vine stages after bone meal by hand: {}", mealAges)
        check("bone meal still reaches a growing grape vine through the interaction", mealAges == listOf(0, 1, 2, 3))

        // Leave nothing behind for the tank tests to trip over.
        level.getEntitiesOfClass(ItemEntity::class.java, AABB.ofSize(Vec3.atCenterOf(vine), 8.0, 8.0, 8.0))
            .forEach { it.discard() }
        player.inventory.clearContent()

        val recipe = level.server.recipeManager.byKey(
            ResourceKey.create(Registries.RECIPE, Registration.id("grape_seeds")),
        )
        check("the grape seeds recipe is loaded", recipe.isPresent)

        // The grapes are only reachable if the vine also grows wild: the seeds come from the fruit,
        // so without a worldgen patch there is no first bunch to plant.
        val registries = level.server.registryAccess()
        val vinePatch = registries.lookupOrThrow(Registries.PLACED_FEATURE)
            .getOrThrow(AlimentWorldGen.GRAPE_VINE_PATCH)
        val biomes = registries.lookupOrThrow(Registries.BIOME)
        fun vineyard(key: ResourceKey<Biome>): Boolean =
            biomes.getOrThrow(key).value().generationSettings.features().any { it.contains(vinePatch) }

        check("grape vines grow wild in the plains", vineyard(Biomes.PLAINS))
        check("and in the forest", vineyard(Biomes.FOREST))
        check("but not in a desert", !vineyard(Biomes.DESERT))
        check("nor in a taiga", !vineyard(Biomes.TAIGA))

        // Clean up so the tank tests below start on a clean floor.
        row.forEachIndexed { index, _ ->
            level.setBlockAndUpdate(BlockPos(10 + index, FLOOR_Y + 1, 2), Blocks.AIR.defaultBlockState())
        }
        level.setBlockAndUpdate(vine, Blocks.AIR.defaultBlockState())
    }

    // ------------------------------------------------------------------ grape wine

    /**
     * Grape wine, from the tank side: grapes and sugar are two inputs, and neither is accepted on its
     * own until the other is there.
     *
     * The interesting assertions are the refusals. A tank that took yeast straight after the grapes
     * would silently make 7% wine out of the plain-sugar branch, so "yeast is refused while the must
     * is incomplete" is the check that keeps the 5% figure meaningful.
     */
    private fun testGrapeWine(level: ServerLevel, player: FakePlayer) {
        val tankPos = BlockPos(2, FLOOR_Y + 1, 2)
        level.setBlockAndUpdate(tankPos, AlimentBlocks.FERMENTATION_TANK.defaultBlockState())
        level.setBlockAndUpdate(tankPos.below(), Blocks.STONE.defaultBlockState())
        val entity = level.getBlockEntity(tankPos) as? FermentationTankBlockEntity
        check("grape wine tank entity exists", entity != null)
        if (entity == null) return

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
        useOn(level, player, tankPos)
        check("grape wine tank filled with water", entity.waterLevel == 3)

        // Grapes first: the must is not ready yet, because a grape has no sugar to ferment.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.GRAPE))
        useOn(level, player, tankPos)
        check("grapes are accepted as a substrate", entity.substrate == FermentationTankBlockEntity.Substrate.GRAPE)
        check("grape must turns the liquid purple", level.getBlockState(tankPos).getValue(FermentationTankBlock.LIQUID) == FermentationTankBlock.TankLiquid.GRAPE)
        check("grapes alone are not a complete substrate", !entity.hasCompleteSubstrate)

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.BREWER_YEAST))
        useOn(level, player, tankPos)
        check("yeast is refused while the grape must has no sugar", !entity.hasYeast)
        check("and an incomplete must is not fermenting", !entity.isFermenting)

        // Sugar completes the must rather than replacing the grapes.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.SUGAR))
        useOn(level, player, tankPos)
        check("sugar completes the grape must", entity.sugarWithGrape)
        check("which is now a complete substrate", entity.hasCompleteSubstrate)
        check("and the liquid is still the grape must, not plain sugar", level.getBlockState(tankPos).getValue(FermentationTankBlock.LIQUID) == FermentationTankBlock.TankLiquid.GRAPE)

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.BREWER_YEAST))
        useOn(level, player, tankPos)
        check("yeast is accepted once the must is complete", entity.hasYeast)
        check("grape must is fermenting", entity.isFermenting)

        entity.fermentProgress = FermentationTankBlockEntity.FERMENT_TICKS - 1
        FermentationTankBlockEntity.serverTick(level, tankPos, level.getBlockState(tankPos), entity)
        check("grape fermentation produces 5% ethanol", entity.ethanol == 0.05f)
        check("and is recorded as grape wine rather than wine", entity.fermentedProduct == FermentationTankBlockEntity.FermentedProduct.GRAPE_WINE)
        check("grape wine turns the liquid deep red", level.getBlockState(tankPos).getValue(FermentationTankBlock.LIQUID) == FermentationTankBlock.TankLiquid.GRAPE_WINE)
        check("fermentation consumed the grapes and sugar", entity.substrate == FermentationTankBlockEntity.Substrate.NONE && !entity.sugarWithGrape)

        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GLASS_BOTTLE))
        useOn(level, player, tankPos)
        val bottled = (0 until player.inventory.containerSize)
            .map { player.inventory.getItem(it) }
            .firstOrNull { it.`is`(AlimentItems.GRAPE_WINE) }
        check("bottling a grape wine tank yields grape wine", bottled != null)
        check("and not the generic wine", (0 until player.inventory.containerSize).none { player.inventory.getItem(it).`is`(AlimentItems.WINE) })
        check(
            "grape wine is bottled at 5% ethanol",
            bottled != null && GrapeWineItem.getConcentration(bottled) == 0.05f,
        )
        check("the default grape wine stack is 5%", GrapeWineItem.getConcentration(AlimentItems.createGrapeWine()) == 0.05f)

        // No topping up a tank that already holds alcohol. The ethanol is a *concentration*, not a
        // total, so water poured in after fermentation comes back out as another full-strength
        // bottle: bottle one, refill to three, bottle three more. Refusing the water is what keeps
        // one dose of sugar and yeast to one batch.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
        useOn(level, player, tankPos)
        check("a water bucket cannot top up a tank that has already fermented", entity.waterLevel == 2)
        check(
            "and the grape wine is not watered down",
            entity.ethanol == 0.05f &&
                entity.fermentedProduct == FermentationTankBlockEntity.FermentedProduct.GRAPE_WINE,
        )
        check("and the tank is still a tank", level.getBlockState(tankPos).`is`(AlimentBlocks.FERMENTATION_TANK))
        check("and the bucket did not spill into the world", level.getBlockState(tankPos.above()).`is`(Blocks.AIR))

        player.setItemInHand(
            InteractionHand.MAIN_HAND,
            PotionContents.createItemStack(Items.POTION, Potions.WATER),
        )
        useOn(level, player, tankPos)
        check("a water bottle cannot top it up either", entity.waterLevel == 2)
        check("and that too leaves the grape wine alone", entity.ethanol == 0.05f)

        // The exploit, end to end, in the shape a player would actually try it: bottle, refill,
        // bottle again. The invariant is that the tank yields exactly as many bottles as it has
        // water levels and that the refills add nothing, however many times they are attempted.
        //
        // The count is taken off the hand before each overwrite rather than from the inventory
        // afterwards, because the loop has to put a fresh bucket and bottle into that same hand
        // slot - reading the inventory at the end would only see the last one. The loop is driven
        // by the batch still being live, so it stops when the tank is spent; a regression that let
        // the water in would spin here rather than quietly pass, which is why the counter is bounded.
        val levelsBefore = entity.waterLevel
        var refillAttempts = 0
        var bottledTotal = 0
        while (entity.ethanol > 0f && refillAttempts < 8) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
            useOn(level, player, tankPos)
            refillAttempts++

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GLASS_BOTTLE))
            useOn(level, player, tankPos)
            val inHand = player.mainHandItem
            if (inHand.`is`(AlimentItems.GRAPE_WINE)) {
                bottledTotal += inHand.count
            }
        }
        logger.info(
            "SELFTEST grape wine: {} refill attempts over {} levels -> {} bottles",
            refillAttempts, levelsBefore, bottledTotal,
        )
        check("a tank yields exactly one bottle per water level", bottledTotal == levelsBefore)
        check(
            "and every one of those levels was offered a refill and refused it",
            refillAttempts == levelsBefore,
        )
        check("and the tank ended up empty rather than refilled", entity.waterLevel == 0)
        check("and the batch is spent, so the water was never taken", entity.ethanol == 0f)

        // Plain sugar must still make the stronger generic wine: the new substrate must not have
        // taken over the old branch. The tank has to be cleared through air first - replacing the
        // tank block with itself keeps the existing block entity, and this one still holds the
        // 5% grape wine from above.
        level.setBlockAndUpdate(tankPos, Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(tankPos, AlimentBlocks.FERMENTATION_TANK.defaultBlockState())
        val plain = level.getBlockEntity(tankPos) as? FermentationTankBlockEntity
        check("plain wine tank entity exists", plain != null)
        if (plain != null) {
            check("a fresh tank starts empty", plain.waterLevel == 0 && plain.ethanol == 0f && plain.substrate == FermentationTankBlockEntity.Substrate.NONE)
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
            useOn(level, player, tankPos)
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.SUGAR))
            useOn(level, player, tankPos)
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.BREWER_YEAST))
            useOn(level, player, tankPos)
            check("plain sugar still ferments", plain.isFermenting)
            plain.fermentProgress = FermentationTankBlockEntity.FERMENT_TICKS - 1
            FermentationTankBlockEntity.serverTick(level, tankPos, level.getBlockState(tankPos), plain)
            check("plain sugar still makes 7% wine", plain.ethanol == 0.07f && plain.fermentedProduct == FermentationTankBlockEntity.FermentedProduct.WINE)
        }

        // Sugar first, then grapes. The recipe reads "grapes and sugar", so a player holding the
        // sugar first must not silently end up with plain wine instead.
        level.setBlockAndUpdate(tankPos, Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(tankPos, AlimentBlocks.FERMENTATION_TANK.defaultBlockState())
        val reversed = level.getBlockEntity(tankPos) as? FermentationTankBlockEntity
        check("reversed order tank entity exists", reversed != null)
        if (reversed != null) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
            useOn(level, player, tankPos)
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.SUGAR))
            useOn(level, player, tankPos)
            check("sugar first is plain sugar so far", reversed.substrate == FermentationTankBlockEntity.Substrate.SUGAR)

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.GRAPE))
            useOn(level, player, tankPos)
            check("grapes are accepted after the sugar", reversed.substrate == FermentationTankBlockEntity.Substrate.GRAPE)
            check("and the tank is promoted to a grape must", reversed.sugarWithGrape && reversed.hasCompleteSubstrate)

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.BREWER_YEAST))
            useOn(level, player, tankPos)
            check("yeast takes on the reversed must", reversed.hasYeast && reversed.isFermenting)
            reversed.fermentProgress = FermentationTankBlockEntity.FERMENT_TICKS - 1
            FermentationTankBlockEntity.serverTick(level, tankPos, level.getBlockState(tankPos), reversed)
            check("and it still lands on 5% grape wine", reversed.ethanol == 0.05f && reversed.fermentedProduct == FermentationTankBlockEntity.FermentedProduct.GRAPE_WINE)
        }

        level.setBlockAndUpdate(tankPos, Blocks.AIR.defaultBlockState())
    }

    // ------------------------------------------------------------------ conventional tags

    /**
     * The conventional `c:` tags the mod publishes so other mods can find the grape.
     *
     * These load whether or not Farmer's Delight is installed, so they are checked unconditionally -
     * they are Aliment's half of the contract, and the other mod reads them.
     */
    private fun testGrapeTags(level: ServerLevel) {
        val items = level.server.registryAccess().lookupOrThrow(Registries.ITEM)

        fun tagged(id: String, tag: String): Boolean {
            val holder = items.get(Registration.id(id)).orElse(null) ?: return false
            return holder.`is`(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", tag)))
        }

        check("the grape is in c:crops/grape", tagged("grape", "crops/grape"))
        check("and in c:crops", tagged("grape", "crops"))
        check("the grape seeds are in c:seeds", tagged("grape_seeds", "seeds"))
        check("grape wine is in c:drinks", tagged("grape_wine", "drinks"))

        // Farmland under a vine must stay farmland. Vanilla's FarmlandBlock reads this tag to decide
        // whether a crop above it holds the block open, and it is an *additive* tag file of ours -
        // vanilla's own entries survive the merge - so the assertion is membership, not equality.
        val vine = AlimentBlocks.GRAPE_VINE.defaultBlockState()
        check("the grape vine keeps the farmland under it tilled", vine.`is`(BlockTags.MAINTAINS_FARMLAND))
        check("and that tag also still holds wheat, so the merge was additive", Blocks.WHEAT.defaultBlockState().`is`(BlockTags.MAINTAINS_FARMLAND))
        check("vanilla's supports_vegetation still accepts farmland", Blocks.FARMLAND.defaultBlockState().`is`(BlockTags.SUPPORTS_VEGETATION))

        // Farmer's Delight is optional, so the gated recipes are only expected where it is loaded -
        // and, more importantly, must not be loaded where it is not, because a failed
        // `fabric:load_conditions` drops the file with no error of any kind.
        val fdLoaded = this.fdLoaded
        logger.info("SELFTEST farmersdelight loaded={}", fdLoaded)
        listOf("grapefruit_from_cutting", "grape_seeds_from_cutting", "grapefruit_juice_from_cooking").forEach { id ->
            val present = level.server.recipeManager
                .byKey(ResourceKey.create(Registries.RECIPE, Registration.id(id)))
                .isPresent
            check(
                "the $id recipe is ${if (fdLoaded) "loaded with" else "absent without"} Farmer's Delight",
                present == fdLoaded,
            )
        }

        // The pot's juice is drawn into a bottle and takes a single slice, and neither is visible
        // through the `Recipe` interface: the ingredients moved onto vanilla's `PlacementInfo` in
        // 26.3, and the container is Farmer's Delight's own field, which is what the compat helper
        // is for. The presence check above is what proves that mod *parsed* the file, because a
        // malformed `container` drops the recipe silently with no error anywhere; these only pin
        // down what it says, so that a later edit back to two slices is not a change nothing notices.
        if (fdLoaded) {
            val recipe = level.server.recipeManager
                .byKey(ResourceKey.create(Registries.RECIPE, Registration.id("grapefruit_juice_from_cooking")))
                .orElse(null)
                ?.value()
            val inputs = recipe?.placementInfo()?.ingredients().orEmpty()
            logger.info("SELFTEST cooking pot juice ingredient count: {}", inputs.size)
            check(
                "the cooking pot's juice takes one grapefruit slice, not two",
                inputs.count { it.test(ItemStack(AlimentItems.GRAPEFRUIT_SLICE, 1)) } == 1,
            )
            check("and takes one sugar", inputs.count { it.test(ItemStack(Items.SUGAR, 1)) } == 1)
            check("and takes nothing else", inputs.size == 2)
            check(
                "and is drawn into a glass bottle",
                FarmersDelightRecipes.cookingPotContainer(recipe).`is`(Items.GLASS_BOTTLE),
            )
        }
    }

    /**
     * Asserts that every edible Farmer's Delight item was given Aliment values, and that none was
     * missed.
     *
     * Farmer's Delight is optional, so the first thing this does is prove the integration is *inert*
     * without it. The four checks before the guard hold in either configuration, and each of them
     * would catch a specific way of getting the integration wrong:
     *
     * * a vanilla apple must not collect Farmer's Delight's vitamin C on top of its own;
     * * vanilla bread must not be charged twice;
     * * vanilla's milk *bucket* must not become a Farmer's Delight drink - only the mod's bottle is;
     * * a stone must stay inedible.
     *
     * The guard after them is the assertion that the mod is absent: reading
     * [FarmersDelightNutrition.loaded] is itself safe, but if the class behind it had named
     * `ModItems` on the way in, this run would already have died with `NoClassDefFoundError` rather
     * than reaching this line.
     *
     * With the mod present the real test runs. It asks the item registry - not a hand-written list -
     * for every `farmersdelight` item carrying a `FOOD` or `CONSUMABLE` component, and requires each
     * one to carry at least one Aliment value. A hand-written list would have agreed with itself;
     * this one cannot, because the items come from the mod and the values come from us. That is the
     * only way to show there is no omission.
     */
    private fun testFarmersDelight(level: ServerLevel, player: FakePlayer) {
        // These hold with or without the mod, and they are what catch a double count.
        check(
            "Farmer's Delight adds no vitamin C to a vanilla apple",
            FarmersDelightNutrition.vitaminCFor(Items.APPLE) == 0f,
        )
        check(
            "nor any glucose to vanilla bread",
            FarmersDelightNutrition.glucoseFor(Items.BREAD) == 0f,
        )
        check(
            "nor does it turn vanilla's milk bucket into a drink of its own",
            !FarmersDelightNutrition.isDrink(Items.MILK_BUCKET) && !FarmersDelightNutrition.isRisky(Items.BEEF),
        )
        check("nor is a stone edible", FarmersDelightNutrition.glucoseFor(Items.STONE) == 0f)

        if (!fdLoaded) {
            check(
                "the whole Farmer's Delight integration is inert without the mod",
                !FarmersDelightNutrition.loaded,
            )
            return
        }

        check("the Farmer's Delight integration is live with the mod", FarmersDelightNutrition.loaded)

        // The mod's own registry is the source of truth for *what is edible*, so the test cannot
        // agree with a mistake in our own list.
        val edible = BuiltInRegistries.ITEM.filter { item ->
            BuiltInRegistries.ITEM.getKey(item).namespace == "farmersdelight" &&
                (item.components().has(DataComponents.FOOD) ||
                    item.components().has(DataComponents.CONSUMABLE))
        }
        logger.info("SELFTEST farmersdelight edible items = {}", edible.size)
        check("Farmer's Delight's edible items are enumerable", edible.size >= 70)

        // The assertion that matters: nothing edible is left without a value of some kind.
        val unmodelled = edible.filter { item ->
            FarmersDelightNutrition.glucoseFor(item) <= 0f &&
                FarmersDelightNutrition.vitaminCFor(item) <= 0f &&
                FarmersDelightNutrition.iodineFor(item) <= 0f &&
                FarmersDelightNutrition.sodiumFor(item) <= 0f &&
                !FarmersDelightNutrition.isDrink(item)
        }
        unmodelled.forEach {
            logger.error(
                "SELFTEST unmodelled Farmer's Delight item: {}",
                BuiltInRegistries.ITEM.getKey(it),
            )
        }
        check(
            "every edible Farmer's Delight item has an Aliment value (${unmodelled.size} unmodelled)",
            unmodelled.isEmpty(),
        )

        // Spot checks, by registry id so that this file names no Farmer's Delight type at all. Each
        // one pins a tier or a nutrient to a specific food, so a table edited by mistake fails here
        // rather than in a player's body.
        val kelpRoll = fdItem("kelp_roll")
        check(
            "a kelp roll carries the iodine of the seaweed wrapped round it",
            kelpRoll != null && FarmersDelightNutrition.iodineFor(kelpRoll) > 0f,
        )
        val kelpRollSlice = fdItem("kelp_roll_slice")
        check(
            "and a slice carries a third of a roll's",
            kelpRollSlice != null && kelpRoll != null &&
                abs(
                    FarmersDelightNutrition.iodineFor(kelpRollSlice) * 3f -
                        FarmersDelightNutrition.iodineFor(kelpRoll),
                ) < 0.0001f,
        )
        val bacon = fdItem("bacon")
        check(
            "bacon carries curing salt",
            bacon != null && FarmersDelightNutrition.sodiumFor(bacon) > 0f,
        )
        check(
            "but not as much as a spoonful of salt",
            bacon != null && FarmersDelightNutrition.sodiumFor(bacon) < 3.0f,
        )
        check(
            "the crust of a bread does not count as cured",
            fdItem("cooked_rice")?.let { FarmersDelightNutrition.sodiumFor(it) } == 0f,
        )
        check(
            "a tomato carries vitamin C",
            fdItem("tomato")?.let { FarmersDelightNutrition.vitaminCFor(it) }?.let { it > 0f } == true,
        )
        check(
            "and a cooked steak carries none, because the vitamin is in the plants",
            fdItem("grilled_salmon")?.let { FarmersDelightNutrition.vitaminCFor(it) } == 0f,
        )

        // The tiers, each on an item that is unmistakably of its kind.
        check(
            "a mixed dish is charged as a mixed dish",
            fdItem("grilled_salmon")?.let { FarmersDelightNutrition.glucoseFor(it) } ==
                AlimentData.GLUCOSE_PER_MIXED_DISH,
        )
        check(
            "a cooked rice is charged as bread",
            fdItem("cooked_rice")?.let { FarmersDelightNutrition.glucoseFor(it) } ==
                AlimentData.GLUCOSE_PER_BREAD,
        )
        check(
            "a raw cut is charged as raw meat",
            fdItem("minced_beef")?.let { FarmersDelightNutrition.glucoseFor(it) } ==
                AlimentData.GLUCOSE_PER_RAW_MEAT,
        )
        check(
            "a cooked cut is charged as cooked meat",
            fdItem("cooked_bacon")?.let { FarmersDelightNutrition.glucoseFor(it) } ==
                AlimentData.GLUCOSE_PER_COOKED_MEAT,
        )
        check(
            "a tomato is charged as plant food",
            fdItem("tomato")?.let { FarmersDelightNutrition.glucoseFor(it) } ==
                AlimentData.GLUCOSE_PER_PLANT_FOOD,
        )

        // The drinks: the three sweetened ones count as drinks *and* as sweet drinks, and the
        // bottled milk counts as a drink and as nothing else, exactly like vanilla's bucket.
        check(
            "apple cider is a sweet drink",
            fdItem("apple_cider")?.let {
                FarmersDelightNutrition.isDrink(it) &&
                    FarmersDelightNutrition.glucoseFor(it) == AlimentData.GLUCOSE_PER_SWEET_DRINK
            } == true,
        )
        check(
            "bottled milk is a drink and no glucose at all",
            fdItem("milk_bottle")?.let {
                FarmersDelightNutrition.isDrink(it) && FarmersDelightNutrition.glucoseFor(it) == 0f
            } == true,
        )
        check(
            "a stew in a bowl is a drink, as vanilla's mushroom stew is",
            fdItem("bone_broth")?.let {
                FarmersDelightNutrition.isDrink(it) &&
                    FarmersDelightNutrition.glucoseFor(it) == AlimentData.GLUCOSE_PER_MIXED_DISH
            } == true,
        )

        // The risk roll: the mod's own `c:foods/food_poisoning` set, plus every raw cut.
        check(
            "raw dough rolls for an infection",
            fdItem("wheat_dough")?.let { FarmersDelightNutrition.isRisky(it) } == true,
        )
        check(
            "and so does a raw chicken cut",
            fdItem("chicken_cuts")?.let { FarmersDelightNutrition.isRisky(it) } == true,
        )
        check(
            "but a cooked one does not",
            fdItem("cooked_chicken_cuts")?.let { FarmersDelightNutrition.isRisky(it) } == false,
        )
        check(
            "nor does a cooked rice",
            fdItem("cooked_rice")?.let { FarmersDelightNutrition.isRisky(it) } == false,
        )

        // ------------------------------------------------------------------ eating one for real
        //
        // Everything above asks the *tables* what a food is worth. None of it proves the tables are
        // wired to anything: the lookup functions could be perfect and the ingestion hook could
        // still never call them, which is exactly the failure this half exists to catch. So the
        // item is actually eaten, through the same `finishUsingItem` path the mixin hooks, and the
        // body is read afterwards.
        val deficient = AlimentData.HEALTHY.copy(
            traceElements = TraceElements.HEALTHY.withIodine(0.30f).withVitaminC(30.0f),
        )

        // Vitamin C: a tomato is worth a carrot, which the suite already pins at +10.
        player.setAttached(AlimentAttachments.DATA, deficient)
        ItemStack(fdItem("tomato")!!, 1).finishUsingItem(level, player)
        val afterTomato = player.getAttachedOrCreate(AlimentAttachments.DATA).traceElements.vitaminC
        logger.info("SELFTEST farmersdelight vitamin C after a tomato = {}", afterTomato)
        check("eating a Farmer's Delight tomato adds its vitamin C", abs(afterTomato - 40.0f) < 0.001f)

        // Iodine: a kelp roll, and the glucose it carries as a dish.
        player.setAttached(AlimentAttachments.DATA, deficient)
        ItemStack(fdItem("kelp_roll")!!, 1).finishUsingItem(level, player)
        val afterRoll = player.getAttachedOrCreate(AlimentAttachments.DATA)
        check(
            "eating a kelp roll adds its iodine",
            abs(afterRoll.traceElements.iodine - 0.60f) < 0.001f,
        )
        check(
            "and charges it as a mixed dish",
            abs(afterRoll.glucose - (AlimentData.HEALTHY.glucose + AlimentData.GLUCOSE_PER_MIXED_DISH)) < 0.001f,
        )

        // Sodium: bacon is cured, so it moves sodium and chloride together.
        player.setAttached(AlimentAttachments.DATA, AlimentData.HEALTHY)
        ItemStack(fdItem("bacon")!!, 1).finishUsingItem(level, player)
        val afterBacon = player.getAttachedOrCreate(AlimentAttachments.DATA).electrolytes
        logger.info("SELFTEST farmersdelight sodium after bacon = {}", afterBacon.sodium)
        check(
            "eating Farmer's Delight bacon adds its curing salt",
            abs(afterBacon.sodium - 140.8f) < 0.001f && abs(afterBacon.chloride - 101.8f) < 0.001f,
        )

        // Water: a soup is a drink, so it hydrates as a bowl of vanilla stew does.
        player.setAttached(AlimentAttachments.DATA, AlimentData.HEALTHY.copy(water = 50f))
        ItemStack(fdItem("bone_broth")!!, 1).finishUsingItem(level, player)
        val afterBroth = player.getAttachedOrCreate(AlimentAttachments.DATA).water
        logger.info("SELFTEST farmersdelight water after a bowl of bone broth = {}", afterBroth)
        check("drinking a bowl of bone broth adds the standard drink's water", afterBroth > 50f)

        // The negative control for the whole block: a Farmer's Delight food that carries *nothing*
        // - a fried egg is protein and fat - must leave the body exactly where it was. If the hook
        // were charging every Farmer's Delight item some default, this is the assertion that fails.
        player.setAttached(AlimentAttachments.DATA, AlimentData.HEALTHY)
        ItemStack(fdItem("fried_egg")!!, 1).finishUsingItem(level, player)
        val afterEgg = player.getAttachedOrCreate(AlimentAttachments.DATA)
        check(
            "a fried egg carries no vitamin C, sodium or iodine",
            afterEgg.traceElements.vitaminC == AlimentData.HEALTHY.traceElements.vitaminC &&
                afterEgg.traceElements.iodine == AlimentData.HEALTHY.traceElements.iodine &&
                afterEgg.electrolytes.sodium == AlimentData.HEALTHY.electrolytes.sodium,
        )
        check(
            "and is charged as cooked meat and nothing more",
            abs(afterEgg.glucose - (AlimentData.HEALTHY.glucose + AlimentData.GLUCOSE_PER_COOKED_MEAT)) < 0.001f,
        )
    }

    /** An item by its Farmer's Delight registry id, or `null` if the mod does not define it. */
    private fun fdItem(path: String): Item? = BuiltInRegistries.ITEM
        .getOptional(Identifier.fromNamespaceAndPath("farmersdelight", path))
        .orElse(null)

    /**
     * Asserts that one recipe takes a **real water bottle** and nothing else that is a potion.
     *
     * The recipe must exist, must be a crafting recipe, must match [otherIngredients] plus a water
     * bottle, and must reject the same layout with an awkward potion in the bottle's place. The last
     * one is the whole point: these recipes used to name a bare `minecraft:potion`, which accepts a
     * potion of healing just as happily as water.
     */
    private fun checkWaterBottleRecipe(
        level: ServerLevel,
        id: String,
        otherIngredients: List<ItemStack>,
    ) {
        val recipe = level.server.recipeManager
            .byKey(ResourceKey.create(Registries.RECIPE, Registration.id(id)))
        check("the $id recipe is loaded", recipe.isPresent)
        if (!recipe.isPresent) return

        // `byKey` hands back a `Recipe<*>`, whose star projection blocks `matches`; these are all
        // crafting recipes, so binding the type parameter that way is what makes the call legal.
        val crafting = recipe.get().value() as? CraftingRecipe
        check("the $id recipe is a crafting recipe", crafting != null)
        if (crafting == null) return

        val water = PotionContents.createItemStack(Items.POTION, Potions.WATER)
        val size = otherIngredients.size + 1
        check(
            "the $id recipe takes a water bottle",
            crafting.matches(CraftingInput.of(size, 1, otherIngredients + water), level),
        )
        check(
            "and the $id recipe refuses a potion that is not water",
            !crafting.matches(
                CraftingInput.of(
                    size,
                    1,
                    otherIngredients + PotionContents.createItemStack(Items.POTION, Potions.AWKWARD),
                ),
                level,
            ),
        )
    }

    /**
     * Where the mandrake grows wild, checked where that is actually decided: the biome generation
     * settings Fabric's biome modification API writes into.
     *
     * A plains world is deliberately not needed for this. What has to hold is that the placed feature
     * ends up attached to the plains and the swamps and to nothing else, and that is a registry fact
     * that can be read straight off a running server - the worldgen itself is vanilla's
     * `minecraft:simple_block` patch, which the datapack validation has already accepted.
     */
    private fun testMandrakeWorldGen(level: ServerLevel) {
        val registries = level.server.registryAccess()
        val mandrake = registries.lookupOrThrow(Registries.PLACED_FEATURE)
            .getOrThrow(AlimentWorldGen.MANDRAKE_PATCH)
        val biomes = registries.lookupOrThrow(Registries.BIOME)

        fun growsWild(key: ResourceKey<Biome>): Boolean =
            biomes.getOrThrow(key).value().generationSettings.features().any { it.contains(mandrake) }

        check("mandrakes grow wild in the plains", growsWild(Biomes.PLAINS))
        check("and in the swamp", growsWild(Biomes.SWAMP))
        check("and in a mangrove swamp", growsWild(Biomes.MANGROVE_SWAMP))
        check("but not in a desert", !growsWild(Biomes.DESERT))
        check("nor in a forest", !growsWild(Biomes.FOREST))

        val gymnopilus = registries.lookupOrThrow(Registries.PLACED_FEATURE)
            .getOrThrow(AlimentWorldGen.GYMNOPILUS_PATCH)
        fun sprouted(key: ResourceKey<Biome>): Boolean =
            biomes.getOrThrow(key).value().generationSettings.features().any { it.contains(gymnopilus) }

        check("gymnopilus grows wild in the dark forest", sprouted(Biomes.DARK_FOREST))
        check("and in the taiga", sprouted(Biomes.TAIGA))
        check("but not in the plains", !sprouted(Biomes.PLAINS))
        check("nor in a desert", !sprouted(Biomes.DESERT))

        val ephedra = registries.lookupOrThrow(Registries.PLACED_FEATURE)
            .getOrThrow(AlimentWorldGen.EPHEDRA_PATCH)
        fun arid(key: ResourceKey<Biome>): Boolean =
            biomes.getOrThrow(key).value().generationSettings.features().any { it.contains(ephedra) }

        check("ephedra grows wild in the desert", arid(Biomes.DESERT))
        check("and in the badlands", arid(Biomes.BADLANDS))
        check("but not in the dark forest", !arid(Biomes.DARK_FOREST))
        check("nor in the plains", !arid(Biomes.PLAINS))
    }

    /**
     * The three ways to cook a gymnopilus, all of them with the timing raw beef has in this version:
     * 200 ticks in a furnace and in a smoker, 600 over a campfire.
     *
     * A recipe that failed to parse is simply not in the manager, so asking for it by name is the
     * whole check - and the ingredient and result are printed, because "it loaded" is not the same as
     * "it loaded correctly".
     */
    private fun testGymnopilusCooking(level: ServerLevel) {
        val recipes = level.server.recipeManager
        for ((id, seconds) in listOf("cooked_gymnopilus" to 10, "cooked_gymnopilus_from_smoking" to 10, "cooked_gymnopilus_from_campfire_cooking" to 30)) {
            val key = ResourceKey.create(Registries.RECIPE, Registration.id(id))
            val recipe = recipes.byKey(key).orElse(null)?.value() as? AbstractCookingRecipe
            logger.info("SELFTEST cooking recipe {}: {}", id, recipe)
            check("the $id recipe is loaded", recipe != null)
            check("and it cooks for $seconds seconds", recipe != null && recipe.cookingTime() == seconds * 20)
        }
    }

    private fun testAdvancements(level: ServerLevel, player: FakePlayer) {
        val server = level.server
        val advancements = server.advancements

        val aAncient = advancements.get(AlimentAdvancements.ANCIENT_ANTI_INFLAMMATORY)
        val aOre = advancements.get(AlimentAdvancements.JUST_CRUDE_SALT)
        val aSalt = advancements.get(AlimentAdvancements.CRUSHED_AGAIN)
        val aRefined = advancements.get(AlimentAdvancements.REFINED_SALT)
        val aMandrake = advancements.get(AlimentAdvancements.EVEN_IF_DANGEROUS)
        val aMushroom = advancements.get(AlimentAdvancements.PSYCHEDELIC_WORLD)
        val aFever = advancements.get(AlimentAdvancements.EXTREME_FEVER)

        check("ancient_anti_inflammatory is loaded", aAncient != null)
        check("just_crude_salt is loaded", aOre != null)
        check("crushed_again is loaded", aSalt != null)
        check("refined_salt is loaded", aRefined != null)
        check("even_if_dangerous is loaded", aMandrake != null)
        check("psychedelic_world is loaded", aMushroom != null)
        check("extreme_fever is loaded", aFever != null)

        if (aAncient == null || aOre == null || aSalt == null || aRefined == null ||
            aMandrake == null || aMushroom == null || aFever == null
        ) {
            return
        }

        check("ancient_anti_inflammatory is a root advancement", aAncient.value.isRoot)
        check("just_crude_salt connects to ancient_anti_inflammatory", aOre.value.parent.orElse(null) == AlimentAdvancements.ANCIENT_ANTI_INFLAMMATORY)
        check("crushed_again connects to just_crude_salt", aSalt.value.parent.orElse(null) == AlimentAdvancements.JUST_CRUDE_SALT)
        check("refined_salt connects to crushed_again", aRefined.value.parent.orElse(null) == AlimentAdvancements.CRUSHED_AGAIN)
        check("even_if_dangerous connects to ancient_anti_inflammatory", aMandrake.value.parent.orElse(null) == AlimentAdvancements.ANCIENT_ANTI_INFLAMMATORY)
        check("psychedelic_world connects to even_if_dangerous", aMushroom.value.parent.orElse(null) == AlimentAdvancements.EVEN_IF_DANGEROUS)
        check("extreme_fever connects to ancient_anti_inflammatory", aFever.value.parent.orElse(null) == AlimentAdvancements.ANCIENT_ANTI_INFLAMMATORY)
        check("extreme_fever is a challenge", aFever.value.display.orElse(null)?.type() == AdvancementType.CHALLENGE)

        // Test awarding
        AlimentAdvancements.award(player, AlimentAdvancements.ANCIENT_ANTI_INFLAMMATORY)
        check("ancient_anti_inflammatory can be awarded", player.advancements.getOrStartProgress(aAncient).isDone)

        AlimentAdvancements.onGrind(player, ItemStack(AlimentItems.CRUDE_SALT))
        check("grinding rock salt ore awards just_crude_salt", player.advancements.getOrStartProgress(aOre).isDone)

        AlimentAdvancements.onGrind(player, ItemStack(AlimentItems.CRUDE_SALT_POWDER))
        check("grinding crude salt awards crushed_again", player.advancements.getOrStartProgress(aSalt).isDone)

        AlimentAdvancements.award(player, AlimentAdvancements.REFINED_SALT)
        check("refined_salt can be awarded", player.advancements.getOrStartProgress(aRefined).isDone)

        AlimentAdvancements.award(player, AlimentAdvancements.EVEN_IF_DANGEROUS)
        check("even_if_dangerous can be awarded", player.advancements.getOrStartProgress(aMandrake).isDone)

        AlimentAdvancements.award(player, AlimentAdvancements.PSYCHEDELIC_WORLD)
        check("psychedelic_world can be awarded", player.advancements.getOrStartProgress(aMushroom).isDone)

        AlimentAdvancements.award(player, AlimentAdvancements.EXTREME_FEVER)
        check("extreme_fever can be awarded", player.advancements.getOrStartProgress(aFever).isDone)
    }

    private fun testFermentationAndDistillation(level: ServerLevel, player: FakePlayer) {
        val recipes = level.server.recipeManager
        check(
            "the fermentation_tank recipe is loaded",
            recipes.byKey(ResourceKey.create(Registries.RECIPE, Registration.id("fermentation_tank"))).isPresent,
        )
        check(
            "the condenser_pipe recipe is loaded",
            recipes.byKey(ResourceKey.create(Registries.RECIPE, Registration.id("condenser_pipe"))).isPresent,
        )
        check(
            "the brewer_yeast recipe is loaded",
            recipes.byKey(ResourceKey.create(Registries.RECIPE, Registration.id("brewer_yeast"))).isPresent,
        )

        // The recipes that take a **water bottle rather than any potion**. Vanilla's `Ingredient` is
        // a `HolderSet<Item>` and cannot filter by component, so these use Fabric's component filter -
        // and a filter that fails to parse drops the recipe silently, with no error anywhere. Asking
        // the manager for it by name, then checking that a water bottle matches and another potion
        // does not, is the only thing that would ever notice.
        checkWaterBottleRecipe(
            level,
            "grapefruit_juice",
            listOf(ItemStack(Items.SUGAR, 1), ItemStack(AlimentItems.GRAPEFRUIT_SLICE, 1)),
        )
        checkWaterBottleRecipe(level, "salt_water", listOf(ItemStack(AlimentItems.SALT_POWDER, 1)))
        checkWaterBottleRecipe(level, "crude_salt_water", listOf(ItemStack(AlimentItems.CRUDE_SALT, 1)))

        // 1. Fermentation Tank setup
        val tankPos = BlockPos(5, 201, 5)
        level.setBlockAndUpdate(tankPos, AlimentBlocks.FERMENTATION_TANK.defaultBlockState())
        val entity = level.getBlockEntity(tankPos) as? FermentationTankBlockEntity
        check("fermentation tank block entity exists", entity != null)
        if (entity == null) return

        check("tank starts empty", entity.waterLevel == 0 && !entity.hasSugar && !entity.hasYeast)

        // Add water with bucket
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
        useOn(level, player, tankPos)
        check(
            "using water bucket sets level to 3 and liquid to blue water",
            entity.waterLevel == 3 &&
                level.getBlockState(tankPos).getValue(FermentationTankBlock.LEVEL) == 3 &&
                level.getBlockState(tankPos).getValue(FermentationTankBlock.LIQUID) == FermentationTankBlock.TankLiquid.WATER,
        )

        // Add sugar
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.SUGAR))
        useOn(level, player, tankPos)
        check(
            "sugar is added to the tank and turns liquid white",
            entity.hasSugar &&
                level.getBlockState(tankPos).getValue(FermentationTankBlock.LIQUID) == FermentationTankBlock.TankLiquid.SUGAR,
        )

        // Add brewer's yeast
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.BREWER_YEAST))
        useOn(level, player, tankPos)
        check("yeast is added to the tank", entity.hasYeast)
        check("tank is now fermenting", entity.isFermenting)

        // Simulate 5 minutes fermentation completion
        entity.fermentProgress = FermentationTankBlockEntity.FERMENT_TICKS - 1
        FermentationTankBlockEntity.serverTick(level, tankPos, level.getBlockState(tankPos), entity)
        check("fermentation produces 7% ethanol", entity.ethanol == 0.07f)
        check("fermentation turns liquid light blue wine", level.getBlockState(tankPos).getValue(FermentationTankBlock.LIQUID) == FermentationTankBlock.TankLiquid.WINE)
        check("fermentation consumed sugar", !entity.hasSugar)
        check("fermentation consumed yeast", !entity.hasYeast)

        // Bottle one serving of wine (7%)
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GLASS_BOTTLE))
        useOn(level, player, tankPos)
        val bottledWine = (0 until player.inventory.containerSize)
            .map { player.inventory.getItem(it) }
            .firstOrNull { it.`is`(AlimentItems.WINE) }
        check("bottling fermented tank yields wine", bottledWine != null)
        check(
            "wine has 7% ethanol concentration in NBT",
            bottledWine != null && WineItem.getConcentration(bottledWine) == 0.07f,
        )
        check("tank water level decremented to 2", entity.waterLevel == 2)

        // 2. Condenser Pipe connection
        val riserPos = tankPos.above()
        level.setBlockAndUpdate(riserPos, AlimentBlocks.CONDENSER_PIPE.defaultBlockState())
        val riserState1 = level.getBlockState(riserPos)
        check("single condenser pipe outlet faces UP", !riserState1.getValue(CondenserPipeBlock.OUTLET_DOWN))
        check("single condenser pipe hitbox extends UP to 1.0", riserState1.getShape(level, riserPos).bounds().maxY == 1.0)

        val sidePos = riserPos.east()
        level.setBlockAndUpdate(sidePos, AlimentBlocks.CONDENSER_PIPE.defaultBlockState())
        val sideState = level.getBlockState(sidePos)
        val riserState2 = level.getBlockState(riserPos)
        check("connected side condenser pipe outlet faces DOWN", sideState.getValue(CondenserPipeBlock.OUTLET_DOWN))
        check("riser condenser pipe outlet faces DOWN when connected horizontally", riserState2.getValue(CondenserPipeBlock.OUTLET_DOWN))
        check("connected condenser pipe hitbox no longer extends UP (maxY = 0.75)", riserState2.getShape(level, riserPos).bounds().maxY == 0.75)

        // 3. Distillation into cauldron over campfire
        val firePos = tankPos.below()
        level.setBlockAndUpdate(firePos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true))
        val cauldronPos = sidePos.below()
        level.setBlockAndUpdate(cauldronPos, Blocks.CAULDRON.defaultBlockState())

        check("tank is ready for distillation", entity.isDistilling)

        // Advance distillation 30 seconds
        entity.distillProgress = FermentationTankBlockEntity.DISTILL_TICKS - 1
        FermentationTankBlockEntity.serverTick(level, tankPos, level.getBlockState(tankPos), entity)

        val cauldronState1 = level.getBlockState(cauldronPos)
        check("distillation into cauldron produces alcohol cauldron level 1", cauldronState1.`is`(AlimentBlocks.ALCOHOL_CAULDRON) && cauldronState1.getValue(AlcoholCauldronBlock.LEVEL) == 1)
        check("tank water level decremented to 1", entity.waterLevel == 1)

        // Bottle distilled wine from cauldron
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GLASS_BOTTLE))
        useOn(level, player, cauldronPos)
        val distilledWine = (0 until player.inventory.containerSize)
            .map { player.inventory.getItem(it) }
            .firstOrNull { it.`is`(AlimentItems.WINE) }
        check("bottling alcohol cauldron yields wine", distilledWine != null)
        check(
            "distilled wine has 40% ethanol concentration in NBT",
            distilledWine != null && WineItem.getConcentration(distilledWine) == 0.40f,
        )
        check("cauldron reverted to plain cauldron", level.getBlockState(cauldronPos).`is`(Blocks.CAULDRON))

        // 4. Waste when outlet faces UP (remove side pipe)
        level.setBlockAndUpdate(sidePos, Blocks.AIR.defaultBlockState())
        val riserState3 = level.getBlockState(riserPos)
        check("single riser pipe outlet reverted to UP", !riserState3.getValue(CondenserPipeBlock.OUTLET_DOWN))

        entity.distillProgress = FermentationTankBlockEntity.DISTILL_TICKS - 1
        FermentationTankBlockEntity.serverTick(level, tankPos, level.getBlockState(tankPos), entity)
        check("uncondensed distillation evaporates liquid from tank", entity.waterLevel == 0 && entity.ethanol == 0f)
        check("no alcohol went into cauldron when wasted", level.getBlockState(cauldronPos).`is`(Blocks.CAULDRON))

        // 5. Wheat fermentation and distillation into beer
        level.setBlockAndUpdate(tankPos, AlimentBlocks.FERMENTATION_TANK.defaultBlockState())
        val wheatTankEntity = level.getBlockEntity(tankPos) as? FermentationTankBlockEntity
        check("wheat tank entity exists", wheatTankEntity != null)
        if (wheatTankEntity != null) {
            // Fill water
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
            useOn(level, player, tankPos)
            check("wheat tank filled with water", wheatTankEntity.waterLevel == 3)

            // Add wheat (alternative to sugar)
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WHEAT))
            useOn(level, player, tankPos)
            check("wheat added to tank as substrate", wheatTankEntity.substrate == FermentationTankBlockEntity.Substrate.WHEAT)
            check("wheat tank liquid is wheat", level.getBlockState(tankPos).getValue(FermentationTankBlock.LIQUID) == FermentationTankBlock.TankLiquid.WHEAT)

            // Add brewer's yeast
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AlimentItems.BREWER_YEAST))
            useOn(level, player, tankPos)
            check("yeast added to wheat tank", wheatTankEntity.hasYeast)
            check("wheat tank is fermenting", wheatTankEntity.isFermenting)

            // Complete fermentation
            wheatTankEntity.fermentProgress = FermentationTankBlockEntity.FERMENT_TICKS - 1
            FermentationTankBlockEntity.serverTick(level, tankPos, level.getBlockState(tankPos), wheatTankEntity)
            check("wheat fermentation produces beer product", wheatTankEntity.fermentedProduct == FermentationTankBlockEntity.FermentedProduct.BEER)
            check("wheat tank liquid turns to beer", level.getBlockState(tankPos).getValue(FermentationTankBlock.LIQUID) == FermentationTankBlock.TankLiquid.BEER)

            // Proving that direct bottling is disabled for wheat (must be distilled)
            player.inventory.clearContent()
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GLASS_BOTTLE))
            useOn(level, player, tankPos)
            val bottledFromTank = (0 until player.inventory.containerSize)
                .map { player.inventory.getItem(it) }
                .firstOrNull { it.`is`(AlimentItems.BEER) || it.`is`(AlimentItems.WINE) }
            check("direct bottling wheat mash from tank does not yield beer (requires distillation)", bottledFromTank == null && wheatTankEntity.waterLevel == 3)

            // Set up distillation with condenser pipe and campfire
            level.setBlockAndUpdate(firePos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true))
            level.setBlockAndUpdate(riserPos, AlimentBlocks.CONDENSER_PIPE.defaultBlockState())
            level.setBlockAndUpdate(sidePos, AlimentBlocks.CONDENSER_PIPE.defaultBlockState())
            level.setBlockAndUpdate(cauldronPos, Blocks.CAULDRON.defaultBlockState())

            check("wheat tank ready for distillation", wheatTankEntity.isDistilling)

            wheatTankEntity.distillProgress = FermentationTankBlockEntity.DISTILL_TICKS - 1
            FermentationTankBlockEntity.serverTick(level, tankPos, level.getBlockState(tankPos), wheatTankEntity)

            val beerCauldronState = level.getBlockState(cauldronPos)
            check(
                "distillation of wheat into cauldron produces beer cauldron level 1",
                beerCauldronState.`is`(AlimentBlocks.BEER_CAULDRON) && beerCauldronState.getValue(BeerCauldronBlock.LEVEL) == 1
            )

            // Bottle beer from cauldron
            player.inventory.clearContent()
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GLASS_BOTTLE))
            useOn(level, player, cauldronPos)
            val bottledBeer = (0 until player.inventory.containerSize)
                .map { player.inventory.getItem(it) }
                .firstOrNull { it.`is`(AlimentItems.BEER) }
            check("bottling beer cauldron yields beer", bottledBeer != null)
            check("beer has 5% concentration", bottledBeer != null && BeerItem.getConcentration(bottledBeer) == 0.05f)
            check("cauldron reverted to plain cauldron after bottling", level.getBlockState(cauldronPos).`is`(Blocks.CAULDRON))

            // Clean up
            level.setBlockAndUpdate(tankPos, Blocks.AIR.defaultBlockState())
            level.setBlockAndUpdate(riserPos, Blocks.AIR.defaultBlockState())
            level.setBlockAndUpdate(sidePos, Blocks.AIR.defaultBlockState())
            level.setBlockAndUpdate(firePos, Blocks.STONE.defaultBlockState())
            level.setBlockAndUpdate(cauldronPos, Blocks.AIR.defaultBlockState())
        }

        // 6. Wine & Alcohol multi-tier cauldron distillation pipeline (7% -> 40% -> 75% -> 98%) and dynamic naming
        // Test dynamic item naming
        val wine7 = AlimentItems.createWine(0.07f)
        val wine40 = AlimentItems.createWine(0.40f)
        val alc75 = AlimentItems.createAlcohol(0.75f)
        val alc98 = AlimentItems.createAlcohol(0.98f)

        val name7Key = ((wine7.item as WineItem).getName(wine7).contents as? TranslatableContents)?.key
        val name40Key = ((wine40.item as WineItem).getName(wine40).contents as? TranslatableContents)?.key
        val name75Key = ((alc75.item as WineItem).getName(alc75).contents as? TranslatableContents)?.key
        val name98Key = ((alc98.item as WineItem).getName(alc98).contents as? TranslatableContents)?.key

        check("7% item is named wine", name7Key == "item.aliment.wine")
        check("40% item is named wine", name40Key == "item.aliment.wine")
        check("75% item is named alcohol", name75Key == "item.aliment.alcohol")
        check("98% item is named alcohol", name98Key == "item.aliment.alcohol")

        // Test pouring 7% wine into empty cauldron
        level.setBlockAndUpdate(cauldronPos, Blocks.CAULDRON.defaultBlockState())
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, wine7.copy())
        useOn(level, player, cauldronPos)
        val cState7 = level.getBlockState(cauldronPos)
        check(
            "adding 7% wine to empty cauldron produces alcohol cauldron level 1 concentration 7%",
            cState7.`is`(AlimentBlocks.ALCOHOL_CAULDRON) &&
                cState7.getValue(AlcoholCauldronBlock.LEVEL) == 1 &&
                cState7.getValue(AlcoholCauldronBlock.CONCENTRATION) == AlcoholCauldronBlock.AlcoholConcentration.P07,
        )

        // Add second bottle of 7% wine
        player.setItemInHand(InteractionHand.MAIN_HAND, wine7.copy())
        useOn(level, player, cauldronPos)
        check("adding second 7% wine increases level to 2", level.getBlockState(cauldronPos).getValue(AlcoholCauldronBlock.LEVEL) == 2)

        // Set up heated cauldron distillation at tankPos
        level.setBlockAndUpdate(firePos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true))
        level.setBlockAndUpdate(riserPos, AlimentBlocks.CONDENSER_PIPE.defaultBlockState())
        level.setBlockAndUpdate(sidePos, AlimentBlocks.CONDENSER_PIPE.defaultBlockState())
        level.setBlockAndUpdate(cauldronPos, Blocks.CAULDRON.defaultBlockState())

        // Distill 7% cauldron -> 40% cauldron
        level.setBlockAndUpdate(
            tankPos,
            AlimentBlocks.ALCOHOL_CAULDRON.defaultBlockState()
                .setValue(AlcoholCauldronBlock.LEVEL, 1)
                .setValue(AlcoholCauldronBlock.CONCENTRATION, AlcoholCauldronBlock.AlcoholConcentration.P07),
        )
        val cauldronBlock = level.getBlockState(tankPos).block as AlcoholCauldronBlock
        cauldronBlock.tick(level.getBlockState(tankPos), level, tankPos, level.random)
        check("source 7% cauldron boiled down to empty cauldron", level.getBlockState(tankPos).`is`(Blocks.CAULDRON))
        val dest40State = level.getBlockState(cauldronPos)
        check(
            "distilling 7% cauldron produces 40% wine cauldron",
            dest40State.`is`(AlimentBlocks.ALCOHOL_CAULDRON) &&
                dest40State.getValue(AlcoholCauldronBlock.CONCENTRATION) == AlcoholCauldronBlock.AlcoholConcentration.P40,
        )

        // Distill 40% cauldron -> 75% alcohol cauldron
        level.setBlockAndUpdate(
            tankPos,
            AlimentBlocks.ALCOHOL_CAULDRON.defaultBlockState()
                .setValue(AlcoholCauldronBlock.LEVEL, 1)
                .setValue(AlcoholCauldronBlock.CONCENTRATION, AlcoholCauldronBlock.AlcoholConcentration.P40),
        )
        level.setBlockAndUpdate(cauldronPos, Blocks.CAULDRON.defaultBlockState())
        cauldronBlock.tick(level.getBlockState(tankPos), level, tankPos, level.random)
        check("source 40% cauldron boiled down to empty cauldron", level.getBlockState(tankPos).`is`(Blocks.CAULDRON))
        val dest75State = level.getBlockState(cauldronPos)
        check(
            "distilling 40% cauldron produces 75% alcohol cauldron",
            dest75State.`is`(AlimentBlocks.ALCOHOL_CAULDRON) &&
                dest75State.getValue(AlcoholCauldronBlock.CONCENTRATION) == AlcoholCauldronBlock.AlcoholConcentration.P75,
        )

        // Distill 75% cauldron -> 98% alcohol cauldron
        level.setBlockAndUpdate(
            tankPos,
            AlimentBlocks.ALCOHOL_CAULDRON.defaultBlockState()
                .setValue(AlcoholCauldronBlock.LEVEL, 1)
                .setValue(AlcoholCauldronBlock.CONCENTRATION, AlcoholCauldronBlock.AlcoholConcentration.P75),
        )
        level.setBlockAndUpdate(cauldronPos, Blocks.CAULDRON.defaultBlockState())
        cauldronBlock.tick(level.getBlockState(tankPos), level, tankPos, level.random)
        check("source 75% cauldron boiled down to empty cauldron", level.getBlockState(tankPos).`is`(Blocks.CAULDRON))
        val dest98State = level.getBlockState(cauldronPos)
        check(
            "distilling 75% cauldron produces 98% alcohol cauldron",
            dest98State.`is`(AlimentBlocks.ALCOHOL_CAULDRON) &&
                dest98State.getValue(AlcoholCauldronBlock.CONCENTRATION) == AlcoholCauldronBlock.AlcoholConcentration.P98,
        )

        // Scoop 98% alcohol with glass bottle
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GLASS_BOTTLE))
        useOn(level, player, cauldronPos)
        val scooped98 = player.inventory.getItem(0)
        check("scooped item from 98% cauldron is alcohol", scooped98.`is`(AlimentItems.WINE) && WineItem.getConcentration(scooped98) == 0.98f)
        check(
            "scooped 98% item name is alcohol",
            ((scooped98.item as WineItem).getName(scooped98).contents as? TranslatableContents)?.key == "item.aliment.alcohol",
        )
        check("cauldron reverted to plain cauldron after scooping", level.getBlockState(cauldronPos).`is`(Blocks.CAULDRON))

        // Clean up test area
        level.setBlockAndUpdate(tankPos, Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(riserPos, Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(sidePos, Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(firePos, Blocks.STONE.defaultBlockState())
        level.setBlockAndUpdate(cauldronPos, Blocks.AIR.defaultBlockState())
    }

    private fun testEphedra(level: ServerLevel, player: FakePlayer) {
        val testBase = BlockPos(14, FLOOR_Y + 1, 14)
        val ground = testBase.below()

        // 1. Placement on sand, red sand, terracotta, and dirt
        val grounds = listOf(
            "sand" to Blocks.SAND,
            "red sand" to Blocks.RED_SAND,
            "terracotta" to Blocks.TERRACOTTA,
            "dirt" to Blocks.DIRT,
        )
        val item = ItemStack(AlimentItems.EPHEDRA)
        for ((label, block) in grounds) {
            level.setBlockAndUpdate(ground, block.defaultBlockState())
            level.setBlockAndUpdate(testBase, Blocks.AIR.defaultBlockState())
            player.setItemInHand(InteractionHand.MAIN_HAND, item.copy())
            useOn(level, player, ground)
            val planted = level.getBlockState(testBase).`is`(AlimentBlocks.EPHEDRA)
            check("ephedra can be sown on $label", planted)
        }

        // 2. Bone meal through four growth stages (0 -> 1 -> 2 -> 3)
        level.setBlockAndUpdate(testBase, AlimentBlocks.EPHEDRA.defaultBlockState())
        val ages = mutableListOf(level.getBlockState(testBase).getValue(EphedraBlock.AGE))
        repeat(EphedraBlock.MAX_AGE) {
            BoneMealItem.growCrop(ItemStack(Items.BONE_MEAL), level, testBase)
            ages += level.getBlockState(testBase).getValue(EphedraBlock.AGE)
        }
        logger.info("SELFTEST ephedra stages after bone meal: {}", ages)
        check("bone meal takes ephedra through four stages", ages == listOf(0, 1, 2, 3))
        check("a ripe ephedra is no longer a bonemeal target", !BoneMealItem.growCrop(ItemStack(Items.BONE_MEAL), level, testBase))

        // 3. Right-click harvest on ripe bush resets age to 1
        val hit = BlockHitResult(Vec3.atCenterOf(testBase), Direction.UP, testBase, false)
        val stateBefore = level.getBlockState(testBase)
        check("ephedra is ripe at age 3", stateBefore.getValue(EphedraBlock.AGE) == 3)
        val result = stateBefore.useItemOn(ItemStack.EMPTY, level, player, InteractionHand.MAIN_HAND, hit)
        check("right clicking ripe ephedra succeeds", result.consumesAction())
        val stateAfter = level.getBlockState(testBase)
        check("right click harvest resets ephedra to age 1", stateAfter.getValue(EphedraBlock.AGE) == 1)

        // 4. Drops when mined
        level.setBlockAndUpdate(testBase, AlimentBlocks.EPHEDRA.defaultBlockState().setValue(EphedraBlock.AGE, 3))
        val ripeDrops = Block.getDrops(level.getBlockState(testBase), level, testBase, null)
        check("ripe ephedra drops ephedra items", ripeDrops.any { it.`is`(AlimentItems.EPHEDRA) })

        level.setBlockAndUpdate(testBase, AlimentBlocks.EPHEDRA.defaultBlockState().setValue(EphedraBlock.AGE, 0))
        val unripeDrops = Block.getDrops(level.getBlockState(testBase), level, testBase, null)
        check("unripe ephedra drops 1 ephedra item", unripeDrops.any { it.`is`(AlimentItems.EPHEDRA) })

        // 5. Grindstone processing: Ephedra -> Crushed Ephedra
        check("ephedra is grindable", AlimentGrinding.isGrindable(ItemStack(AlimentItems.EPHEDRA)))
        val grindResult = AlimentGrinding.resultFor(ItemStack(AlimentItems.EPHEDRA), ItemStack.EMPTY)
        check("grinding ephedra yields crushed ephedra", grindResult.`is`(AlimentItems.CRUSHED_EPHEDRA) && grindResult.count == 1)

        // 6. Crafting with shears: Shears + Ephedra -> Crushed Ephedra (damages shears)
        val shearsStack = ItemStack(Items.SHEARS)
        val ephedraStack = ItemStack(AlimentItems.EPHEDRA)
        val craftInput = CraftingInput.of(2, 1, listOf(shearsStack, ephedraStack))
        check("shear recipe matches shears and ephedra", ShearEphedraRecipe.INSTANCE.matches(craftInput, level))
        val shearResult = ShearEphedraRecipe.INSTANCE.assemble(craftInput)
        check("shear recipe yields crushed ephedra", shearResult.`is`(AlimentItems.CRUSHED_EPHEDRA))
        val remainders = ShearEphedraRecipe.INSTANCE.getRemainingItems(craftInput)
        val remainderShears = remainders[0]
        check("shears remains in crafting grid with 1 damage taken", remainderShears.`is`(Items.SHEARS) && remainderShears.damageValue == 1)

        val shearsRecipe = level.server.recipeManager.byKey(
            ResourceKey.create(Registries.RECIPE, Registration.id("crushed_ephedra_from_shears")),
        )
        check("the crushed_ephedra_from_shears recipe is loaded", shearsRecipe.isPresent)

        // 7. Recipe for ephedrine potion (Bottle + Crushed Ephedra)
        val recipe = level.server.recipeManager.byKey(
            ResourceKey.create(Registries.RECIPE, Registration.id("ephedrine")),
        )
        check("the ephedrine crafting recipe is loaded", recipe.isPresent)

        // Clean up
        level.setBlockAndUpdate(testBase, Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState())
    }

    // ------------------------------------------------------------------ helpers

    private fun useOn(level: ServerLevel, player: FakePlayer, pos: BlockPos): InteractionResult {
        val hit = BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        return player.gameMode.useItemOn(player, level, player.mainHandItem, InteractionHand.MAIN_HAND, hit)
    }

    private fun check(name: String, ok: Boolean) {
        if (ok) {
            passed++
            logger.info("SELFTEST PASS: {}", name)
        } else {
            failed++
            logger.error("SELFTEST FAIL: {}", name)
        }
    }

    private fun report() {
        logger.info("SELFTEST DONE passed={} failed={}", passed, failed)
    }
}
