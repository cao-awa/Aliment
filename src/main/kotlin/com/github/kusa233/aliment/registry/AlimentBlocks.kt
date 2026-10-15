package com.github.kusa233.aliment.registry

import com.github.kusa233.aliment.world.block.AlcoholCauldronBlock
import com.github.kusa233.aliment.world.block.BeerCauldronBlock
import com.github.kusa233.aliment.world.block.BrineCauldronBlock
import com.github.kusa233.aliment.world.block.CondenserPipeBlock
import com.github.kusa233.aliment.world.block.CoptisBlock
import com.github.kusa233.aliment.world.block.EphedraBlock
import com.github.kusa233.aliment.world.block.FermentationTankBlock
import com.github.kusa233.aliment.world.block.GrapeVineBlock
import com.github.kusa233.aliment.world.block.GrapefruitBlock
import com.github.kusa233.aliment.world.block.GymnopilusBlock
import com.github.kusa233.aliment.world.block.LicoriceBlock
import com.github.kusa233.aliment.world.block.MandrakeBlock
import com.github.kusa233.aliment.world.block.PhellodendronBlock
import com.github.kusa233.aliment.world.block.SeaweedBlock
import com.github.kusa233.aliment.world.block.WillowSoupCauldronBlock
import com.github.kusa233.aliment.world.block.WillowVinesBlock
import com.github.kusa233.aliment.world.block.WillowVinesPlantBlock
import net.minecraft.world.item.DoubleHighBlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.CeilingHangingSignBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.level.block.PressurePlateBlock
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.block.SaplingBlock
import net.minecraft.world.level.block.ShelfBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.StandingSignBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.TintedParticleLeavesBlock
import net.minecraft.world.level.block.WallHangingSignBlock
import net.minecraft.world.level.block.WallSignBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction

/**
 * Every willow block. Each entry registers the block and (where applicable) its [Item] in one
 * step, mirroring how vanilla's `Blocks` + `Items` pair cooperates in 26.2.
 *
 * Declaration order matters: later blocks derive their properties from earlier ones.
 */
object AlimentBlocks {

    /** Bark / plank-ish properties shared by most of the set. */
    private fun woodProperties(): BlockBehaviour.Properties = BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_BROWN)
        .instrument(NoteBlockInstrument.BASS)
        .strength(2.0F, 3.0F)
        .sound(SoundType.WOOD)
        .ignitedByLava()

    // ---------------------------------------------------------------- logs & bark

    val WILLOW_LOG: Block = Registration.registerBlockWithItem(
        "willow_log",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    val WILLOW_WOOD: Block = Registration.registerBlockWithItem(
        "willow_wood",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    val STRIPPED_WILLOW_LOG: Block = Registration.registerBlockWithItem(
        "stripped_willow_log",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    val STRIPPED_WILLOW_WOOD: Block = Registration.registerBlockWithItem(
        "stripped_willow_wood",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    // ---------------------------------------------------------------- planks

    val WILLOW_PLANKS: Block = Registration.registerBlockWithItem("willow_planks", woodProperties()) { Block(it) }

    // ---------------------------------------------------------------- leaves & sapling

    val WILLOW_LEAVES: Block = Registration.registerBlockWithItem(
        "willow_leaves",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .strength(0.2F)
            .randomTicks()
            .sound(SoundType.GRASS)
            .noOcclusion()
            .isSuffocating { _, _, _ -> false }
            .isViewBlocking { _, _, _, _ -> false }
            .ignitedByLava()
            .pushReaction(PushReaction.POPPED)
            .isRedstoneConductor { _, _, _ -> false },
    ) { TintedParticleLeavesBlock(0.01F, it) }

    val WILLOW_SAPLING: Block = Registration.registerBlockWithItem(
        "willow_sapling",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.POPPED),
    ) { SaplingBlock(AlimentTreeGrowers.WILLOW, it) }

    // ---------------------------------------------------------------- willow vines (drooping strands)

    val WILLOW_VINES: WillowVinesBlock = Registration.registerBlockWithItem(
        "willow_vines",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.POPPED),
    ) { WillowVinesBlock(it) }

    val WILLOW_VINES_PLANT: Block = Registration.registerBlock(
        "willow_vines_plant",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.POPPED),
    ) { WillowVinesPlantBlock(it) }

    val POTTED_WILLOW_SAPLING: Block = Registration.registerBlock(
        "potted_willow_sapling",
        BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.POPPED),
    ) { FlowerPotBlock(WILLOW_SAPLING, it) }

    // ---------------------------------------------------------------- shaped wooden blocks

    val WILLOW_STAIRS: Block = Registration.registerBlockWithItem("willow_stairs", woodProperties()) {
        StairBlock(WILLOW_PLANKS.defaultBlockState(), it)
    }

    val WILLOW_SLAB: Block = Registration.registerBlockWithItem("willow_slab", woodProperties()) { SlabBlock(it) }

    val WILLOW_FENCE: Block = Registration.registerBlockWithItem("willow_fence", woodProperties()) { FenceBlock(it) }

    val WILLOW_FENCE_GATE: Block = Registration.registerBlockWithItem("willow_fence_gate", woodProperties()) {
        FenceGateBlock(AlimentWoodTypes.WILLOW, it)
    }

    val WILLOW_DOOR: Block = Registration.registerBlockWithItem(
        "willow_door",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(3.0F)
            .noOcclusion()
            .pushReaction(PushReaction.POPPED)
            .ignitedByLava(),
        Item.Properties().stacksTo(16),
        { block, props -> DoubleHighBlockItem(block, props) },
    ) { DoorBlock(AlimentWoodTypes.WILLOW_SET_TYPE, it) }

    val WILLOW_TRAPDOOR: Block = Registration.registerBlockWithItem(
        "willow_trapdoor",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(3.0F)
            .noOcclusion()
            .isValidSpawn { _, _, _, _ -> false }
            .ignitedByLava()
            .pushReaction(PushReaction.POPPED),
    ) { TrapDoorBlock(AlimentWoodTypes.WILLOW_SET_TYPE, it) }

    val WILLOW_PRESSURE_PLATE: Block = Registration.registerBlockWithItem(
        "willow_pressure_plate",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(0.5F)
            .ignitedByLava()
            .pushReaction(PushReaction.POPPED),
    ) { PressurePlateBlock(AlimentWoodTypes.WILLOW_SET_TYPE, it) }

    val WILLOW_BUTTON: Block = Registration.registerBlockWithItem(
        "willow_button",
        BlockBehaviour.Properties.of().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED),
    ) { ButtonBlock(AlimentWoodTypes.WILLOW_SET_TYPE, 30, it) }

    val WILLOW_SHELF: Block = Registration.registerBlockWithItem(
        "willow_shelf",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .sound(SoundType.SHELF)
            .ignitedByLava()
            .strength(2.0F, 3.0F),
    ) { ShelfBlock(it) }

    // ---------------------------------------------------------------- signs

    val WILLOW_SIGN: Block = Registration.registerBlock(
        "willow_sign",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { StandingSignBlock(AlimentWoodTypes.WILLOW, it) }

    val WILLOW_WALL_SIGN: Block = Registration.registerBlock(
        "willow_wall_sign",
        BlockBehaviour.Properties.of()
            .overrideLootTable(WILLOW_SIGN.lootTable)
            .overrideDescription(WILLOW_SIGN.descriptionId)
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { WallSignBlock(AlimentWoodTypes.WILLOW, it) }

    val WILLOW_HANGING_SIGN: Block = Registration.registerBlock(
        "willow_hanging_sign",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { CeilingHangingSignBlock(AlimentWoodTypes.WILLOW, it) }

    val WILLOW_WALL_HANGING_SIGN: Block = Registration.registerBlock(
        "willow_wall_hanging_sign",
        BlockBehaviour.Properties.of()
            .overrideLootTable(WILLOW_HANGING_SIGN.lootTable)
            .overrideDescription(WILLOW_HANGING_SIGN.descriptionId)
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { WallHangingSignBlock(AlimentWoodTypes.WILLOW, it) }

    // ---------------------------------------------------------------- willow bark soup cauldron

    /**
     * Filled with raw willow bark soup, then cooked by a campfire underneath. Created by using
     * [AlimentItems.WILLOW_BARK_PIECES] on a water cauldron, so it has no item form of its own.
     */
    val WILLOW_SOUP_CAULDRON: Block = Registration.registerBlock(
        "willow_soup_cauldron",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(2.0F)
            .noOcclusion(),
    ) { WillowSoupCauldronBlock(it) }

    // ---------------------------------------------------------------- salt

    /**
     * Rock salt ore. It breaks into [AlimentItems.CRUSHED_ROCK_SALT] rather than dropping itself,
     * and it is still mined with a pickaxe. Grinding the ore still works and yields one shard, the
     * same as grinding what it drops, so the block itself is the shortcut and the shard the haul.
     */
    val ROCK_SALT_ORE: Block = Registration.registerBlockWithItem(
        "rock_salt_ore",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(3.0F, 3.0F)
            .sound(SoundType.STONE),
    ) { Block(it) }

    /**
     * Holds crude brine while it evaporates over a fire. Created by using
     * [AlimentItems.CRUDE_SALT_POWDER] on a water cauldron, so it has no item form of its own.
     */
    val BRINE_CAULDRON: Block = Registration.registerBlock(
        "brine_cauldron",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(2.0F)
            .noOcclusion(),
    ) { BrineCauldronBlock(it) }

    // ---------------------------------------------------------------- mandrake

    /**
     * The mandrake plant, in four stages.
     *
     * It has no item form on purpose, exactly like vanilla's wheat and sweet berry bush: a mandrake
     * is sown with [AlimentItems.MANDRAKE_SEEDS] rather than placed from the inventory, which is
     * what makes the fruit - and the wait for it - the point of the plant.
     */
    val MANDRAKE: Block = Registration.registerBlock(
        "mandrake",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.POPPED),
    ) { MandrakeBlock(it) }

    // ---------------------------------------------------------------- grape vine

    /**
     * The grape vine, in four stages, sown from [AlimentItems.GRAPE_SEEDS].
     *
     * Like the mandrake it has no item form: a vine is planted from its seeds rather than placed
     * out of the inventory, which is what makes growing one the way to get grapes.
     */
    val GRAPE_VINE: Block = Registration.registerBlock(
        "grape_vine",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.POPPED),
    ) { GrapeVineBlock(it) }

    // ---------------------------------------------------------------- gymnopilus

    /**
     * The gymnopilus mushroom. Its item form is an ordinary edible mushroom registered in
     * [AlimentItems], because the food values live with the rest of the food.
     */
    val GYMNOPILUS: Block = Registration.registerBlock(
        "gymnopilus",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .noCollision()
            .instabreak()
            .sound(SoundType.GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.POPPED),
    ) { GymnopilusBlock(it) }

    // ---------------------------------------------------------------- ephedra

    /**
     * Ephedra: an arid medicinal shrub in four growth stages.
     */
    val EPHEDRA: Block = Registration.registerBlock(
        "ephedra",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.POPPED),
    ) { EphedraBlock(it) }

    // ---------------------------------------------------------------- traditional medicinal herbs

    /**
     * Coptis: medicinal herb rich in berberine.
     */
    val COPTIS: Block = Registration.registerBlock(
        "coptis",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.POPPED),
    ) { CoptisBlock(it) }

    /**
     * Phellodendron: medicinal shrub/bark source rich in berberine.
     */
    val PHELLODENDRON: Block = Registration.registerBlock(
        "phellodendron",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.POPPED),
    ) { PhellodendronBlock(it) }

    /**
     * Licorice: medicinal herb rich in glycyrrhizin.
     */
    val LICORICE: Block = Registration.registerBlock(
        "licorice",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.POPPED),
    ) { LicoriceBlock(it) }

    /**
     * Seaweed: an underwater edible crop with 4 growth stages, yielding iodine.
     */
    val SEAWEED: Block = Registration.registerBlock(
        "seaweed",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.WET_GRASS)
            .pushReaction(PushReaction.POPPED),
    ) { SeaweedBlock(it) }

    // ---------------------------------------------------------------- brewing & distillation

    /**
     * Glass Fermentation Tank for brewing wine from water, sugar, and yeast.
     */
    val FERMENTATION_TANK: Block = Registration.registerBlockWithItem(
        "fermentation_tank",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE)
            .strength(0.3F)
            .sound(SoundType.GLASS)
            .noOcclusion(),
    ) { FermentationTankBlock(it) }

    /**
     * Glass Condenser Pipe for distilling alcohol above a fermentation tank.
     */
    val CONDENSER_PIPE: Block = Registration.registerBlockWithItem(
        "condenser_pipe",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE)
            .strength(0.3F)
            .sound(SoundType.GLASS)
            .noOcclusion(),
    ) { CondenserPipeBlock(it) }

    /**
     * Distilled Alcohol Cauldron to collect condensed alcohol from a condenser pipe.
     */
    val ALCOHOL_CAULDRON: Block = Registration.registerBlock(
        "alcohol_cauldron",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(2.0F)
            .noOcclusion(),
    ) { AlcoholCauldronBlock(it) }

    /**
     * Distilled Beer Cauldron to collect condensed beer from a condenser pipe.
     */
    val BEER_CAULDRON: Block = Registration.registerBlock(
        "beer_cauldron",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(2.0F)
            .noOcclusion(),
    ) { BeerCauldronBlock(it) }

    // ---------------------------------------------------------------- grapefruit

    /**
     * The grapefruit tree and the wood set cut from it.
     *
     * The tree generates in the jungles and the savannas and carries its fruit, but its wood is a
     * full second set alongside the willow's: logs, bark, planks, the shaped blocks, the signs and
     * its own two boats. It is a separate wood rather than a recolour of the willow's, so a
     * `#aliment:grapefruit_logs` recipe cannot be satisfied with willow logs.
     */
    val GRAPEFRUIT_LOG: Block = Registration.registerBlockWithItem(
        "grapefruit_log",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    val GRAPEFRUIT_WOOD: Block = Registration.registerBlockWithItem(
        "grapefruit_wood",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    val STRIPPED_GRAPEFRUIT_LOG: Block = Registration.registerBlockWithItem(
        "stripped_grapefruit_log",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    val STRIPPED_GRAPEFRUIT_WOOD: Block = Registration.registerBlockWithItem(
        "stripped_grapefruit_wood",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    val GRAPEFRUIT_PLANKS: Block =
        Registration.registerBlockWithItem("grapefruit_planks", woodProperties()) { Block(it) }

    /** The canopy a grapefruit hangs from. */
    val GRAPEFRUIT_LEAVES: Block = Registration.registerBlockWithItem(
        "grapefruit_leaves",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .strength(0.2F)
            .randomTicks()
            .sound(SoundType.GRASS)
            .noOcclusion()
            .isSuffocating { _, _, _ -> false }
            .isViewBlocking { _, _, _, _ -> false }
            .ignitedByLava()
            .pushReaction(PushReaction.POPPED)
            .isRedstoneConductor { _, _, _ -> false },
    ) { TintedParticleLeavesBlock(0.01F, it) }

    val GRAPEFRUIT_SAPLING: Block = Registration.registerBlockWithItem(
        "grapefruit_sapling",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.POPPED),
    ) { SaplingBlock(AlimentTreeGrowers.GRAPEFRUIT, it) }

    // ---------------------------------------------------------------- grapefruit shaped blocks

    val GRAPEFRUIT_STAIRS: Block = Registration.registerBlockWithItem("grapefruit_stairs", woodProperties()) {
        StairBlock(GRAPEFRUIT_PLANKS.defaultBlockState(), it)
    }

    val GRAPEFRUIT_SLAB: Block =
        Registration.registerBlockWithItem("grapefruit_slab", woodProperties()) { SlabBlock(it) }

    val GRAPEFRUIT_FENCE: Block =
        Registration.registerBlockWithItem("grapefruit_fence", woodProperties()) { FenceBlock(it) }

    val GRAPEFRUIT_FENCE_GATE: Block = Registration.registerBlockWithItem(
        "grapefruit_fence_gate",
        woodProperties(),
    ) { FenceGateBlock(AlimentWoodTypes.GRAPEFRUIT, it) }

    val GRAPEFRUIT_DOOR: Block = Registration.registerBlockWithItem(
        "grapefruit_door",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(3.0F)
            .noOcclusion()
            .pushReaction(PushReaction.POPPED)
            .ignitedByLava(),
        Item.Properties().stacksTo(16),
        { block, props -> DoubleHighBlockItem(block, props) },
    ) { DoorBlock(AlimentWoodTypes.GRAPEFRUIT_SET_TYPE, it) }

    val GRAPEFRUIT_TRAPDOOR: Block = Registration.registerBlockWithItem(
        "grapefruit_trapdoor",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(3.0F)
            .noOcclusion()
            .isValidSpawn { _, _, _, _ -> false }
            .ignitedByLava()
            .pushReaction(PushReaction.POPPED),
    ) { TrapDoorBlock(AlimentWoodTypes.GRAPEFRUIT_SET_TYPE, it) }

    val GRAPEFRUIT_PRESSURE_PLATE: Block = Registration.registerBlockWithItem(
        "grapefruit_pressure_plate",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(0.5F)
            .ignitedByLava()
            .pushReaction(PushReaction.POPPED),
    ) { PressurePlateBlock(AlimentWoodTypes.GRAPEFRUIT_SET_TYPE, it) }

    val GRAPEFRUIT_BUTTON: Block = Registration.registerBlockWithItem(
        "grapefruit_button",
        BlockBehaviour.Properties.of().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED),
    ) { ButtonBlock(AlimentWoodTypes.GRAPEFRUIT_SET_TYPE, 30, it) }

    val GRAPEFRUIT_SHELF: Block = Registration.registerBlockWithItem(
        "grapefruit_shelf",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .sound(SoundType.SHELF)
            .ignitedByLava()
            .strength(2.0F, 3.0F),
    ) { ShelfBlock(it) }

    // ---------------------------------------------------------------- grapefruit signs

    val GRAPEFRUIT_SIGN: Block = Registration.registerBlock(
        "grapefruit_sign",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { StandingSignBlock(AlimentWoodTypes.GRAPEFRUIT, it) }

    val GRAPEFRUIT_WALL_SIGN: Block = Registration.registerBlock(
        "grapefruit_wall_sign",
        BlockBehaviour.Properties.of()
            .overrideLootTable(GRAPEFRUIT_SIGN.lootTable)
            .overrideDescription(GRAPEFRUIT_SIGN.descriptionId)
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { WallSignBlock(AlimentWoodTypes.GRAPEFRUIT, it) }

    val GRAPEFRUIT_HANGING_SIGN: Block = Registration.registerBlock(
        "grapefruit_hanging_sign",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { CeilingHangingSignBlock(AlimentWoodTypes.GRAPEFRUIT, it) }

    val GRAPEFRUIT_WALL_HANGING_SIGN: Block = Registration.registerBlock(
        "grapefruit_wall_hanging_sign",
        BlockBehaviour.Properties.of()
            .overrideLootTable(GRAPEFRUIT_HANGING_SIGN.lootTable)
            .overrideDescription(GRAPEFRUIT_HANGING_SIGN.descriptionId)
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { WallHangingSignBlock(AlimentWoodTypes.GRAPEFRUIT, it) }

    val POTTED_GRAPEFRUIT_SAPLING: Block = Registration.registerBlock(
        "potted_grapefruit_sapling",
        BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.POPPED),
    ) { FlowerPotBlock(GRAPEFRUIT_SAPLING, it) }

    /**
     * The fruit itself, hanging under the canopy.
     *
     * Breaking one drops the [AlimentItems]-side item of the same name, which is what the recipe
     * turns into eight slices. It carries its own item so that a player can hang one back under a
     * tree; see [GrapefruitBlock] for what it will and will not hang from.
     */
    val GRAPEFRUIT: Block = Registration.registerBlockWithItem(
        "grapefruit",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .noCollision()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.POPPED),
    ) { GrapefruitBlock(it) }

    /** Touching this forces the whole object graph to be built. */
    fun initialize() {
    }
}