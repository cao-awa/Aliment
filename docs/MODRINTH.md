# Aliment

> This project is conceptualized, architecturally designed, tested, and code-reviewed by human developers. Concrete code implementations are generated with AI assistance, with all additions thoroughly verified, proofread, and refined through human oversight.

Aliment is a realistic physiology, pathology, pharmacology, and herbal medicine mod for Minecraft Fabric.

Instead of treating player health as an abstract hitpoint bar that depletes instantly upon eating bad food, Aliment simulates continuous physiological kinetics, multi-pathway immune responses, electrolyte balances, and pharmacodynamics with clinical fidelity.

## Key Features

### Dynamic Physiology and Nutrition
- **Thirst and Hydration**: Introduces an independent thirst bar. Water balance drains steadily during daily activity and accelerates significantly during fevers, sweating, and heavy exertion.
- **Serum Electrolytes**: Tracks sodium, potassium, magnesium, chloride, and calcium in clinical mmol/L units. Overhydrating with plain water washes out electrolytes, while consuming salt water or excessive salt induces hypernatremia and acute thirst.
- **Trace Elements and Micronutrients**:
  - **Iodine (µmol/L)**: Essential for thyroid function and metabolic temperature set point regulation. Sourced from kelp, seaweed, and seaweed iodized salt. Unchecked deficiency causes hypothyroidism, hypothermia, sluggishness, and fatigue.
  - **Vitamin C (µmol/L)**: Follows real-world first-order clearance kinetics (higher concentrations clear faster). Replenished by plant foods such as apples, melons, carrots, and pumpkins. Depletion causes mining fatigue and scurvy-induced weakness.

### Infection and Immune Dynamics
- **Logistic Pathogen Growth**: Bacteria and viruses reproduce according to biological carrying capacity models instead of applying static damage ticks.
- **Five-Mediator Immune Regulation**: Histamine, prostaglandins, leukotrienes, cytokines, and bradykinin coordinate inflammatory defenses.
- **Bell-Shaped Immune Clearance**: Too little inflammation fails to clear pathogens, while runaway inflammation triggers dangerous cytokine storms that cause severe organ stress.
- **Thermoregulation and Fevers**: Core temperature reacts dynamically to pyrogens and the surrounding environment. Hypothermia slows physical reactions, whereas severe fevers induce profuse sweating, dehydration, and exhaustion.

### Herbal Medicine and Pharmacology
- **Willow (Salicin)**: Strip bark from riverbank willow trees and brew willow bark soup in cauldrons to obtain natural salicin for fever reduction and pain relief.
- **Ephedra (Ephedrine)**: Forage arid ephedra shrubs and grind twigs to prepare stimulant remedies granting increased mining speed and alertness.
- **Coptis and Phellodendron (Berberine)**: Bitter medicinal herbs yielding berberine, which specifically inhibits bacterial proliferation.
- **Licorice (Glycyrrhizin)**: Sweet root herb containing glycyrrhizin, acting as an antiviral agent and soothing mucosal inflammation.
- **Mandrake and Gymnopilus**: Dangerous nightshade plants carrying anticholinergic alkaloids (scopolamine and atropine) that induce blurred vision and fever, alongside psychoactive mushrooms producing perceptual distortions.
- **Dexamethasone**: Potent glucocorticoid injections that rapidly suppress out-of-control cytokine storms in critical medical emergencies.

### Agriculture, Brewing, and Crafting
- **Salt Processing**: Extract rock salt deposits underground, pulverize chunks on grindstones into crude salt, and refine them into high-grade salt powder.
- **Seaweed Aquaculture**: Plant and harvest underwater seaweed crops, cook them as nutritious food, or grind and combine them with salt to manufacture seaweed iodized salt.
- **Fermentation and Distillation**: Assemble glass fermentation tanks and condenser pipes to ferment mash into wine and distill concentrated ethanol. The tank takes wheat for beer, sugar for 7% wine, and grapes with sugar for a lighter 5% grape wine that bottles straight out.
- **Grape Vines**: A four-stage crop that grows wild across the plains and temperate forests and sows onto any soil a vanilla crop accepts, farmland included. Right-click a ripe vine to pick 1-3 grapes and leave the plant standing one stage short of ripe, so a vineyard is something you return to; breaking it pays the same. One grape yields two seeds.
- **Willow Woodset**: Complete decorative set featuring willow wood blocks, planks, hanging signs, and cascading hanging willow vines.

### Optional Farmer's Delight Integration
- **No dependency required**: Aliment is built against Farmer's Delight at compile time so it can price that mod's food, and bundles none of it. The integration is one-way and entirely optional - a game without Farmer's Delight boots, plays and passes the same test suite, and Aliment publishes its conventional `c:` tags either way.
- **Recipes**: with that mod installed, a knife cuts a bunch of grapes into three seeds on its cutting board, and its cooking pot boils one grapefruit slice with sugar and draws the juice into a glass bottle you supply. Without it those two recipes simply do not exist.
- **Every edible item is priced**: all **80** of its foods and drinks feed the physiology instead of sitting behind one generic food value. Cured bacon and ham carry sodium and chloride - below a serving of Aliment's own salt, because a rasher is salty without being a spoonful of salt; its vegetables and salads carry vitamin C, a tomato being worth a carrot; its two kelp rolls carry iodine, a slice exactly a third of a roll's; every dish is charged blood glucose by what it is made of, with assembled plates taking a band of their own between plant food and bread; and its bottled milk, sweetened drinks and nine soups hydrate as vanilla's milk bucket does.
- **Risk carries over too**: its raw dough, raw pasta, chicken cuts and nether salad can infect you exactly as raw meat can.

---

## Documentation and Guides

For detailed gameplay walkthroughs, mathematical formulas, and clinical reference ranges:

- **Mechanics and Spoilers Guide**: [SPOILER.md](https://github.com/cao-awa/Aliment/blob/main/docs/SPOILER.md)  
  Contains item recipes, progression paths, symptom reference charts, and clinical tips.
- **Mathematical and Biological Model**: [PHYSIOLOGY.md](https://github.com/cao-awa/Aliment/blob/main/docs/PHYSIOLOGY.md)  
  Contains differential equations, pharmacokinetic clearance rates, clinical units, and model derivations.
- **Technical Architecture**: [TECHNICAL.md](https://github.com/cao-awa/Aliment/blob/main/docs/TECHNICAL.md)  
  Contains the tri-language architecture notes, Mixin registry, and build/compiler guidance.
- **Changelog**: [CHANGE_LOG.md](https://github.com/cao-awa/Aliment/blob/main/docs/CHANGE_LOG.md)  
  Contains the notable changes to the mod, in Keep a Changelog format.
