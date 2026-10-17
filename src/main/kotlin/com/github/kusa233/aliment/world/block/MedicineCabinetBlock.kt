package com.github.kusa233.aliment.world.block

import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition

/**
 * The medicine cabinet. A wall of shelves for the apothecary to work from.
 *
 * It is also the **job site** for the apothecary villager, which is why it is a full cube that
 * faces the player: a villager walks to the block, not to a particular face, but a cabinet that
 * does not turn to face you reads as scenery rather than as furniture. The facing is stored the way
 * vanilla stores it - the direction the *front* points, which for a placed block is the opposite of
 * the direction the player was looking when they put it down, so the door ends up towards them.
 *
 * @see com.github.kusa233.aliment.registry.AlimentVillagers for the profession that claims it
 */
class MedicineCabinetBlock(properties: BlockBehaviour.Properties) : HorizontalDirectionalBlock(properties) {

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    /**
     * The front faces the player. `horizontalDirection` is the way the player is looking, so the
     * opposite of it is the way the player is, and a cabinet against a wall should open towards the
     * room rather than into the wall.
     */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
}
