package com.github.kusa233.aliment.registry

import com.google.common.collect.ImmutableSet
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.fabricmc.fabric.api.`object`.builder.v1.world.poi.PoiHelper
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.ai.village.poi.PoiType
import net.minecraft.world.entity.npc.villager.VillagerProfession
import net.minecraft.world.item.trading.TradeSet
import net.minecraft.world.item.trading.VillagerTrade

/**
 * The apothecary: a villager who trades the mod's medicines out of a
 * [com.github.kusa233.aliment.world.block.MedicineCabinetBlock medicine cabinet].
 *
 * ## The trades are data, not code
 *
 * In 26.3 a villager's offers are no longer built in Java. [VillagerTrade] and [TradeSet] became
 * registry entries loaded from the datapack - `data/<ns>/villager_trade/<profession>/<level>/…` and
 * `data/<ns>/trade_set/<profession>/level_<n>.json` - and Fabric's old `TradeOfferHelper` is gone
 * (the resolved `fabric-object-builder-api-v1` keeps only `PoiHelper` under its `world/poi`
 * package). So this file registers the *profession* and points it at trade sets by key; the ten
 * actual offers live in `src/main/resources/data/aliment/villager_trade/apothecary`.
 *
 * That split is why the level map below is the only place the two halves meet: [tradeSetsByLevel]
 * builds the five `aliment:apothecary/level_<n>` keys that the JSON files must be named after. Get a
 * level wrong here and vanilla logs a missing trade set rather than failing the build, because the
 * registry contents are only known once the datapack is read.
 *
 * ## Why the job site needs a tag entry as well
 *
 * An unemployed villager searches for a job site through its *current* profession, which is
 * `minecraft:none`, whose `acquirableJobSite` is `VillagerProfession.ALL_ACQUIRABLE_JOBS` - a test
 * against the `minecraft:acquirable_job_site` **POI tag**, not against any individual POI. Vanilla
 * lists all thirteen of its job sites there. So the cabinet has to be added to that tag from our own
 * datapack (`data/minecraft/tags/point_of_interest_type/acquirable_job_site.json`) or no villager
 * will ever consider it, however correctly the profession itself is registered.
 *
 * @see com.github.kusa233.aliment.registry.AlimentBlocks.MEDICINE_CABINET
 */
object AlimentVillagers {

    /** The profession's own id, and the stem of every trade-set key below. */
    private const val PROFESSION_PATH = "apothecary"

    /** Villagers gain a level at each of these, so there are exactly five trade sets. */
    private const val MAX_LEVEL = 5

    /**
     * The cabinet's point of interest.
     *
     * Tickets and range are 1, which is what every vanilla job site uses: one villager may work a
     * given block, and it is claimed from the block itself rather than from a radius around it. The
     * returned [PoiType] is not kept - what callers need is [POI_KEY], and vanilla keys such a POI
     * by its own registry id.
     */
    private val MEDICINE_CABINET_POI: PoiType = PoiHelper.register(
        Registration.id("medicine_cabinet"),
        1,
        1,
        AlimentBlocks.MEDICINE_CABINET,
    )

    /**
     * The same key [PoiHelper] registered the POI under, rebuilt so the profession's predicates can
     * test a villager's job site against it.
     *
     * This mirrors what vanilla does: `VillagerProfession.register` receives a `ResourceKey<PoiType>`
     * and turns it into `holder -> holder.is(key)`. The predicate compares keys rather than values,
     * so rebuilding it here is the intended use rather than a workaround.
     */
    private val POI_KEY: ResourceKey<PoiType> =
        ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Registration.id("medicine_cabinet"))

    /**
     * The apothecary.
     *
     * Built with the public record constructor because vanilla's own `register` helpers are private.
     * Both job-site predicates are the same test - the cabinet - which is what vanilla does for
     * every profession that has no secondary block: `heldJobSite` is what the villager stands at,
     * `acquirableJobSite` what it will walk to and claim.
     *
     * The work sound is the cleric's. 26.3 ships a `VILLAGER_WORK_*` event per vanilla profession -
     * thirteen of them - and none for an apothecary, and of those the cleric's is the one that
     * already means "standing at a block mixing something", which is what this villager is doing.
     */
    val APOTHECARY: VillagerProfession = Registry.register(
        BuiltInRegistries.VILLAGER_PROFESSION,
        ResourceKey.create(Registries.VILLAGER_PROFESSION, Registration.id(PROFESSION_PATH)),
        VillagerProfession(
            // Vanilla builds this as "entity.<namespace>.villager.<path>" for its own professions,
            // and the lang files follow the same shape for ours.
            Component.translatable("entity.aliment.villager.$PROFESSION_PATH"),
            { holder -> holder.`is`(POI_KEY) },
            { holder -> holder.`is`(POI_KEY) },
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_CLERIC,
            tradeSetsByLevel(),
        ),
    )

    /**
     * Touching this object at all registers the POI and the profession, provided the caller has
     * already been through [AlimentBlocks] - the POI resolves block states off the cabinet.
     */
    fun initialize() {
        // Classloading triggers registration
    }

    /**
     * Level -> the trade set that level rolls from.
     *
     * The paths are `<profession>/level_<n>` and not `trade_set/…`: [TradeSet]'s registry directory
     * is already `trade_set`, exactly as `BLOCK`'s is `block`. `TradeSets.resourceKey` in vanilla
     * takes `"armorer/level_1"` and produces `minecraft:armorer/level_1`, which is the file
     * `data/minecraft/trade_set/armorer/level_1.json`.
     */
    private fun tradeSetsByLevel(): Int2ObjectMap<ResourceKey<TradeSet>> {
        val map = Int2ObjectOpenHashMap<ResourceKey<TradeSet>>(MAX_LEVEL)
        for (level in 1..MAX_LEVEL) {
            map[level] = ResourceKey.create(
                Registries.TRADE_SET,
                Registration.id("$PROFESSION_PATH/level_$level"),
            )
        }
        return map
    }
}
