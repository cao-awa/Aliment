package com.github.kusa233.aliment

import com.github.kusa233.aliment.command.AlimentCommand
import com.github.kusa233.aliment.event.AlimentInteractions
import com.github.kusa233.aliment.physiology.AlimentAttachments
import com.github.kusa233.aliment.physiology.AlimentSymptoms
import com.github.kusa233.aliment.registry.AlimentBlockEntities
import com.github.kusa233.aliment.registry.AlimentBlocks
import com.github.kusa233.aliment.registry.AlimentCreativeTabs
import com.github.kusa233.aliment.registry.AlimentEffects
import com.github.kusa233.aliment.registry.AlimentEntities
import com.github.kusa233.aliment.registry.AlimentItems
import com.github.kusa233.aliment.registry.AlimentRecipes
import com.github.kusa233.aliment.registry.AlimentWorldGen
import com.github.kusa233.aliment.world.AlimentLoot
import com.github.kusa233.aliment.world.tree.AlimentTreeDecorators
import com.github.kusa233.aliment.world.tree.AlimentTrunkPlacers
import net.fabricmc.api.ModInitializer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class Aliment : ModInitializer {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger("Aliment")
        const val MOD_ID = "aliment"
    }

    override fun onInitialize() {
        LOGGER.info("Init aliment")

        // Order matters: the decorator and trunk placer types have to exist before worldgen JSON
        // is decoded, the block entity types need the blocks, and the creative tabs need
        // everything else.
        AlimentTreeDecorators.initialize()
        AlimentTrunkPlacers.initialize()
        AlimentBlocks.initialize()
        AlimentEntities.initialize()
        // The fever and pain indicators. They are content like the items below and depend on
        // nothing themselves; the only ordering that matters is that they exist by the time
        // `AlimentSymptoms` starts handing them out, near the bottom of this method.
        AlimentEffects.initialize()
        AlimentItems.initialize()
        AlimentRecipes.initialize()
        AlimentBlockEntities.initialize()
        AlimentCreativeTabs.initialize()
        AlimentWorldGen.initialize()
        AlimentInteractions.initialize()

        // Loot goes last of the world-facing hooks: it edits tables the vanilla datapack provides, so
        // the items it hands out have to exist by the time a chest is first opened.
        AlimentLoot.initialize()

        // The physiology system: attachments first, then the tick handler and the debug command.
        AlimentAttachments.initialize()
        AlimentSymptoms.initialize()
        AlimentCommand.initialize()

        LOGGER.info("Aliment content registered")
    }
}
