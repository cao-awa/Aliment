package com.github.kusa233.aliment.command

import com.github.kusa233.aliment.physiology.Electrolytes
import com.github.kusa233.aliment.physiology.Mediators
import com.github.kusa233.aliment.physiology.Mineral
import com.github.kusa233.aliment.physiology.AlimentAttachments
import com.github.kusa233.aliment.physiology.AlimentData
import com.github.kusa233.aliment.physiology.AlimentPhysiology
import com.github.kusa233.aliment.physiology.AlimentSymptoms
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

/**
 * `/aliment` - inspect and poke at the physiology system.
 *
 * Useful both for players (to see how ill they are) and for anyone tuning the numbers, since the
 * whole model is data driven and otherwise invisible.
 */
object AlimentCommand {

    /** Every field `set` understands, in the order the suggestions list them. */
    private val FIELDS = listOf(
        "water",
        "sodium", "potassium", "magnesium", "chloride", "calcium",
        "iodine", "vitamin_c",
        "histamine", "prostaglandin", "leukotriene", "cytokine", "bradykinin",
        "bacteria", "virus",
        "salicin", "dexamethasone",
        "scopolamine", "atropine",
        "psilocybin", "psilocin",
        "ephedrine", "berberine", "glycyrrhizin",
        "ethanol",
        "temperature", "pyrogen",
    )

    /** `/aliment fever` with no argument lands here: a solid, clearly symptomatic fever. */
    private const val DEFAULT_FEVER = 39.5f

    fun initialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("aliment")
                    .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                    .then(Commands.literal("status").executes(::status))
                    .then(
                        Commands.literal("set")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .then(
                                Commands.argument("field", StringArgumentType.word())
                                    .suggests { _, builder -> SharedSuggestionProvider.suggest(FIELDS, builder) }
                                    .then(
                                        // Negative values are allowed so that `set pyrogen -2`, an
                                        // antipyretic offset, is expressible.
                                        Commands.argument("value", DoubleArgumentType.doubleArg(-20.0, 200.0))
                                            .executes(::setField),
                                    ),
                            ),
                    )
                    .then(
                        Commands.literal("fever")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes { fever(it, DEFAULT_FEVER) }
                            .then(
                                // The reachable range is what a pyrogen can shift the set point by,
                                // not the whole scale: asking for 30 would silently come up short.
                                Commands.argument(
                                    "degrees",
                                    DoubleArgumentType.doubleArg(
                                        (AlimentData.TEMPERATURE_NORMAL - AlimentData.PYROGEN_CAP).toDouble(),
                                        AlimentData.TEMPERATURE_MAX.toDouble(),
                                    ),
                                ).executes { fever(it, DoubleArgumentType.getDouble(it, "degrees").toFloat()) },
                            ),
                    )
                    .then(
                        Commands.literal("cure")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(::cure),
                    ),
            )
        }
    }

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val data = player.getAttachedOrCreate(AlimentAttachments.DATA)
        for (line in statusLines(data, AlimentSymptoms.isGlucoseHeld(player))) {
            context.source.sendSuccess({ Component.literal(line) }, false)
        }
        return 1
    }

    /**
     * The whole readout `/aliment status` prints, one line per string.
     *
     * Split out from [status] for the same reason `AlimentInteractions.glucoseReadingMessage` is:
     * so the self test can assert the text the command really produces rather than one it built for
     * itself. Here that matters more than usual, because the readout is documented as *every*
     * physiological metric and the interesting assertion is completeness - the test walks the fields
     * the state actually has and requires each one to be accounted for. That check is the only thing
     * standing between the promise and a field quietly going missing from it, which is what had
     * already happened to the whole glucose chain and to the liver before it.
     *
     * [glucoseHeld] is the peaceful hold, and it is here rather than read from [data] because on
     * peaceful the glucose is *held*: the stored number stops being evidence of anything, so without
     * the tag the one line a player would want explained is the one that reads like every other.
     */
    internal fun statusLines(data: AlimentData, glucoseHeld: Boolean = false): List<String> {
        val e = data.electrolytes
        val m = data.mediators
        val condition = when {
            data.isImmuneStorm -> "immune storm"
            data.isImmunosuppressed -> "immunosuppressed"
            data.isSymptomatic -> "infected"
            data.isFebrile -> "fever"
            data.isHypothermic -> "hypothermia"
            // The only rung here that takes hitpoints off by itself, so it outranks the imbalances
            // rather than sitting behind them. Two rungs because the crash is the one that hurts.
            data.hypoglycemiaDamage > 0f -> "hypoglycaemic crash"
            data.isHypoglycemic -> "hypoglycaemic"
            data.hasElectrolyteImbalance -> "electrolyte imbalance"
            else -> "healthy"
        }

        val electrolytes = Electrolytes.MINERALS.joinToString("  ") { mineral ->
            "%s %s".format(shortName(mineral), mineral.display(e.of(mineral)))
        }
        val lines = listOf(
            "aliment: %s".format(condition),
            "  inflammation %.1f  (histamine %.0f, prostaglandin %.0f, leukotriene %.0f, cytokine %.0f, bradykinin %.0f)"
                .format(data.inflammation, m.histamine, m.prostaglandin, m.leukotriene, m.cytokine, m.bradykinin),
            "  water %.1f  (%d/%d cells)%s".format(
                data.water, data.thirstCells, AlimentData.THIRST_CELLS,
                if (data.isOverhydrated) "  [over-hydrated]" else if (data.isDehydrated) "  [dehydrated]" else "",
            ),
            "  electrolytes (mmol/L)  %s%s".format(
                electrolytes,
                if (data.hasElectrolyteImbalance) "  [out of range]" else "",
            ),
            "  trace elements (umol/L)  I %s  VitC %s%s".format(
                Mineral.IODINE.display(data.traceElements.iodine),
                Mineral.VITAMIN_C.display(data.traceElements.vitaminC),
                if (data.hasTraceElementImbalance) "  [out of range]" else "",
            ),
            "  temperature %.2f C  [%s]  pyrogen %+.2f".format(
                data.temperature, thermalName(data.thermalTier), data.pyrogen,
            ),
            // The mod's own two HUD indicators, printed even when they are off - this line is what
            // says whether the effect bar agrees with the temperature above it. Note that the grade
            // is deliberately not the `[%s]` on that line: it has three fever steps to the symptom
            // scale's two, which is the whole reason it exists.
            "  indicators  fever %s  pain %s".format(
                if (data.feverGrade == 0) "-" else "I".repeat(data.feverGrade),
                if (data.hasPain) "I" else "-",
            ),
            "  pathogens  bacteria %.1f  virus %.1f%s%s".format(
                data.bacteria, data.virus,
                // The latch that says the body has noticed, which it does well before the load is
                // high enough to make the player feel it - so it explains climbing inflammation
                // during a stretch where nothing seems to be wrong yet.
                if (data.immuneActive) "  [immune response active]" else "",
                if (data.isSevereInfection) "  [severe: taking damage]" else "",
            ),
            "  glucose %s mmol/L  (insulin %.2f, injected %.2f)%s%s".format(
                data.glucoseReading, data.insulin, data.insulinAspart,
                when {
                    data.hypoglycemiaDamage > 0f -> "  [hypoglycaemic crash: taking damage]"
                    data.isHypoglycemic -> "  [hypoglycaemic]"
                    data.isHyperglycemic -> "  [above range]"
                    else -> ""
                },
                // Added on top of the tag above rather than instead of it: a held body can be in a
                // crash as easily as a free one, and both facts are worth reading at once.
                if (glucoseHeld) "  [held: peaceful]" else "",
            ),
            "  drugs  salicin %.2f  dexamethasone %.2f".format(data.salicin, data.dexamethasone),
            "  mandrake  scopolamine %.2f  atropine %.2f  (load %.2f%s)%s".format(
                data.scopolamine, data.atropine, data.anticholinergicLoad,
                if (data.isVisionBlurred) ", sight blurred" else "",
                if (data.thermalTier > 0 && data.anticholinergicLoad >= 1.5f) "  [drug fever]" else "",
            ),
            "  gymnopilus  psilocybin %.2f  psilocin %.2f  (trip stage %d)%s".format(
                data.psilocybin, data.psilocin, data.psilocinTier,
                if (data.psilocin > 5f) "  [drug fever]" else "",
            ),
            "  ephedra  ephedrine %.2f%s".format(
                data.ephedrine,
                if (data.hasHasteFromEphedrine) "  [haste I]" else "",
            ),
            "  herbs  berberine %.2f  glycyrrhizin %.2f".format(
                data.berberine, data.glycyrrhizin,
            ),
            // Next to the herbs on purpose: CYP3A4 is the enzyme that clears the berberine on the
            // line above it, so the pair is what makes "the coptis is lasting because of the
            // grapefruit" visible in one place instead of two.
            "  liver  naringin %.1f/%.0f  CYP3A4 %.1f%s".format(
                data.naringin, AlimentData.NARINGIN_CAP, data.cyp3a4,
                if (data.cyp3a4 < AlimentData.CYP3A4_NORMAL) "  [inhibited]" else "",
            ),
            "  wine  ethanol %.2f (%.0f%%)%s".format(
                data.ethanol,
                data.ethanol * 100f,
                if (data.ethanol >= 0.70f) "  [severe drunkenness]" else if (data.ethanol >= 0.35f) "  [drunkenness]" else "",
            ),
        )
        return lines
    }

    /** The symbol `/aliment status` prints for [mineral]. */
    private fun shortName(mineral: Mineral): String = when (mineral) {
        Mineral.SODIUM -> "Na"
        Mineral.POTASSIUM -> "K"
        Mineral.MAGNESIUM -> "Mg"
        Mineral.CHLORIDE -> "Cl"
        Mineral.CALCIUM -> "Ca"
        Mineral.IODINE -> "I"
        Mineral.VITAMIN_C -> "VitC"
    }

    private fun thermalName(tier: Int): String = when {
        tier >= 2 -> "super-high fever"
        tier == 1 -> "fever"
        tier == -1 -> "hypothermia"
        tier <= -2 -> "severe hypothermia"
        else -> "normal"
    }

    private fun setField(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val field = StringArgumentType.getString(context, "field")
        val value = DoubleArgumentType.getDouble(context, "value").toFloat()
        val data = player.getAttachedOrCreate(AlimentAttachments.DATA)
        val e = data.electrolytes
        val m = data.mediators

        val updated = when (field) {
            "water" -> data.copy(water = value.coerceIn(AlimentData.WATER_MIN, AlimentData.WATER_MAX))
            "sodium" -> data.withElectrolytes(e.withSodium(Mineral.SODIUM.clamp(value)))
            "potassium" -> data.withElectrolytes(e.withPotassium(Mineral.POTASSIUM.clamp(value)))
            "magnesium" -> data.withElectrolytes(e.withMagnesium(Mineral.MAGNESIUM.clamp(value)))
            "chloride" -> data.withElectrolytes(e.withChloride(Mineral.CHLORIDE.clamp(value)))
            "calcium" -> data.withElectrolytes(e.withCalcium(Mineral.CALCIUM.clamp(value)))
            "iodine" -> data.withTraceElements(data.traceElements.withIodine(Mineral.IODINE.clamp(value)))
            "vitamin_c" -> data.withTraceElements(data.traceElements.withVitaminC(Mineral.VITAMIN_C.clamp(value)))
            "histamine" -> data.withMediators(m.withHistamine(clamp(value, Mediators.MAX)))
            "prostaglandin" -> data.withMediators(m.withProstaglandin(clamp(value, Mediators.MAX)))
            "leukotriene" -> data.withMediators(m.withLeukotriene(clamp(value, Mediators.MAX)))
            "cytokine" -> data.withMediators(m.withCytokine(clamp(value, Mediators.MAX)))
            "bradykinin" -> data.withMediators(m.withBradykinin(clamp(value, Mediators.MAX)))
            "bacteria" -> data.copy(bacteria = clamp(value, AlimentData.MAX_PATHOGEN))
            "virus" -> data.copy(virus = clamp(value, AlimentData.MAX_PATHOGEN))
            "salicin" -> data.copy(salicin = clamp(value, AlimentData.SALICIN_CAP))
            "dexamethasone" -> data.copy(dexamethasone = clamp(value, AlimentData.DEXAMETHASONE_CAP))
            "scopolamine" -> data.withScopolamine(clamp(value, AlimentData.ANTICHOLINERGIC_CAP))
            "atropine" -> data.withAtropine(clamp(value, AlimentData.ANTICHOLINERGIC_CAP))
            "psilocybin" -> data.withPsilocybin(clamp(value, AlimentData.PSILOCYBIN_CAP))
            "psilocin" -> data.withPsilocin(clamp(value, AlimentData.PSILOCIN_CAP))
            "ephedrine" -> data.withEphedrine(clamp(value, AlimentData.EPHEDRINE_CAP))
            "berberine" -> data.withBerberine(clamp(value, AlimentData.BERBERINE_CAP))
            "glycyrrhizin" -> data.withGlycyrrhizin(clamp(value, AlimentData.GLYCYRRHIZIN_CAP))
            "ethanol" -> data.withEthanol(clamp(value, AlimentData.ETHANOL_CAP))
            // Setting the temperature moves the body itself; setting the pyrogen moves the target
            // it is walking towards, which is what makes a fever persist.
            "temperature" -> data.copy(
                temperature = value.coerceIn(AlimentData.TEMPERATURE_MIN, AlimentData.TEMPERATURE_MAX),
            )
            "pyrogen" -> data.copy(pyrogen = value.coerceIn(-AlimentData.PYROGEN_CAP, AlimentData.PYROGEN_CAP))
            else -> {
                context.source.sendFailure(Component.literal("Unknown field. Try one of $FIELDS"))
                return 0
            }
        }

        player.setAttached(AlimentAttachments.DATA, updated)
        // The electrolytes are in mmol/L and iodine in umol/L, and each has its own clamp, so the
        // value the player typed is not always the value that landed: report what the field holds.
        val landed = when (field) {
            "sodium" -> Mineral.SODIUM.display(updated.electrolytes.sodium)
            "potassium" -> Mineral.POTASSIUM.display(updated.electrolytes.potassium)
            "magnesium" -> Mineral.MAGNESIUM.display(updated.electrolytes.magnesium)
            "chloride" -> Mineral.CHLORIDE.display(updated.electrolytes.chloride)
            "calcium" -> Mineral.CALCIUM.display(updated.electrolytes.calcium)
            "iodine" -> Mineral.IODINE.display(updated.traceElements.iodine)
            "vitamin_c" -> Mineral.VITAMIN_C.display(updated.traceElements.vitaminC)
            else -> "$value"
        }
        context.source.sendSuccess({ Component.literal("$field = $landed") }, false)
        return 1
    }

    /**
     * `/aliment fever [degrees]` - induces a fever (or, below 37, hypothermia) for testing.
     *
     * The temperature is not set directly: a pyrogen is injected so that the fever really *peaks*
     * at `degrees`, whatever the infection is already doing, and the core temperature then walks
     * towards it the way it does for a real fever. It is cleared over one in-game day, so the
     * screen effects are gone within twenty minutes of real time - and `/aliment cure` takes them
     * off immediately, which the feedback line says, because a fever you forgot about is
     * indistinguishable from a bug.
     */
    private fun fever(context: CommandContext<CommandSourceStack>, degrees: Float): Int {
        val player = context.source.playerOrException
        val data = player.getAttachedOrCreate(AlimentAttachments.DATA)
        val updated = AlimentPhysiology.induceFever(data, degrees, AlimentSymptoms.ambientTemperature(player))
        player.setAttached(AlimentAttachments.DATA, updated)

        val direction = if (degrees >= AlimentData.TEMPERATURE_NORMAL) "fever" else "hypothermia"
        val screen = when {
            degrees >= AlimentData.FEVER_SEVERE -> "heat haze + motion blur"
            degrees >= AlimentData.FEVER_MILD -> "heat haze"
            degrees <= AlimentData.COLD_MILD -> "cold shiver"
            else -> "no screen effect at this temperature"
        }
        context.source.sendSuccess(
            {
                Component.literal(
                    "aliment: %s peaking at %.2f C (%s) - about two minutes to get there, /aliment cure to stop it"
                        .format(direction, degrees, screen),
                )
            },
            true,
        )
        return 1
    }

    private fun cure(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        player.setAttached(AlimentAttachments.DATA, AlimentData.HEALTHY)
        // The screen effects are driven by the data, but clearing them here means `/aliment cure`
        // takes the shimmer off the screen on the same tick rather than on the next one.
        AlimentSymptoms.clearPostEffects(player)
        context.source.sendSuccess({ Component.literal("aliment: physiology reset") }, false)
        return 1
    }

    /** Clamp for the fields that are not minerals: mediators, pathogens and drugs. */
    private fun clamp(value: Float, max: Float): Float = value.coerceIn(0f, max)
}
