[English Version](TECHNICAL.md) | [中文版本](TECHNICAL_zh.md)

# Aliment 技术架构与开发文档

本文档详细记录 **Aliment（供养）** 模组的技术架构、工程规范、多语言交互接缝、构建机制及测试工具体系。

面向平台：**Minecraft 26.3**，基于 **Fabric Loader 0.19.5** 与 **Fabric API 0.161.0+26.3**。

---

## 1. 多语言分层架构

为保证生理模型数值计算的纯粹性与 Minecraft 平台逻辑的解耦，项目采用严格的单向依赖三层架构：
`src/main/scala` -> `src/main/kotlin` -> `src/main/java` -> `src/client`

```
   src/main/scala (Scala 3.9)
   [纯数值模型，零 Minecraft/Fabric 依赖]
             │
             ▼
   src/main/java (Java 25)
   [AlimentModelBridge: 跨语言安全接缝与类型屏障]
             │
             ▼
   src/main/kotlin (Kotlin 2.4)
   [Minecraft 业务逻辑：注册表、Attachments、方块实体、事件与症状]
             │
             ▼
   src/client (Kotlin + Java)
   [HUD 口渴条、镜头振荡、迷雾收敛、后处理 Shader 视效]
```

### 1.1 Scala 3 模型层 (`src/main/scala/.../physiology/model`)
- **职责**：承载全部微分方程、生理稳态、电解质参考范围、免疫钟形曲线、体内药物动力学以及每 tick 的数值演化。
- **纯函数约束**：该层严禁导入任何 Minecraft、Kotlin、Fabric 的类库。输入输出均为纯数值与标准不可变 Case Class（`ModelState`, `ModelMineral`, `ModelMediators`, `ModelElectrolytes`, `ModelTraceElements`, `ModelDrugs`, `ModelEnzymes`）。
- **无状态计算**：`Physiology.tick(...)` 接受当前状态与环境输入，纯函数式返回演化后的下一状态；体内药物与生物碱集中于 `ModelDrugs` 并由 `Physiology.stepDrugs` 统一推进代谢衰减，而作用于它们的清除通路集中于 `ModelEnzymes`——药物是身体**携带**的东西，酶指标是它被**清除**的速度。

### 1.2 Java 接缝层 (`src/main/java/.../physiology/AlimentModelBridge.java`)
- **存在的根本原因**：Kotlin K2 编译器在引用一个含有 Scala 类型的类时，会主动尝试解析其所有父接口（包括 `scala.Product`）。即使 classpath 正确配置，Kotlin 也会报错 `Cannot access 'scala.Product'`。
- **设计规范**：
  - `AlimentModelBridge.java` 是全工程**唯一允许提及 Scala 类型**的文件。
  - 所有 Scala 类型必须限制在 `private` 内部变量和方法体中，不得作为 `public` 字段、参数或返回值暴露给 Kotlin。
  - 向上仅接受并输出 Kotlin 侧的数据载体（`AlimentData`）及 Java 原生基本类型。
  - 常量全部在 Scala 模型中定义，并通过 Bridge 的静态方法重新导出，杜绝阈值双重维护。

### 1.3 Kotlin 业务层 (`src/main/kotlin/...`)
- **职责**：承载与 Minecraft 引擎对接的所有逻辑。
  - **数据持久化与 Codec 突破**：通过 Fabric Data Attachment API 将 `AlimentData` 绑定至 Player 实体。针对 Mojang DataFixerUpper `RecordCodecBuilder.instance.group(...)` 最大支持 16 个字段（`Products.P16`）的硬性限制，在内部构建扁平化辅助 `Compounds` 结构及其 `MapCodec`，内联平铺嵌入主 Codec，在不破坏 NBT 扁平向下兼容的前提下完美容纳扩展指标。
  - **生理症状**：`AlimentSymptoms` 每 tick 驱动体温、脱水掉血、电解质失衡缓慢失明/反胃、麻黄碱速掘等效果。
  - **吃喝钩子**：`AlimentIngestion` 统一处理食物/药剂下肚时的水分、电解质吸收与病原体摄入。
  - **方块与物品交互**：`AlimentInteractions` 处理潜行研磨、炼药锅投料、注射剂使用等。

### 1.4 客户端渲染层 (`src/client`)
- **HUD 扩展**：`AlimentThirstHud` 在玩家血量上方渲染 10 格独立口渴条（支持空、半格、满格状态）。
- **视效 Shader**：裸盖菇素中毒时的彩线描边与画面扭曲、高热失真、曼陀罗发热视线模糊。
- **动态迷雾**：曼陀罗抗胆碱能中毒时，通过 Mixin 将渲染迷雾硬性收敛至 8 格内，模拟真实散瞳与失焦。

---

## 2. 核心构建系统与避坑设计

### 2.1 独立的 `compileModelScala` 编译任务
Gradle 原生 Scala 插件默认的 `compileScala` 任务无条件依赖 `compileJava`，无论源码集里是否有 Java 代码。这会导致不可解的循环依赖：

```
compileJava -> compileKotlin -> compileScala -> compileJava
```

**解决方案**：
在 `build.gradle.kts` 中通过手写独立的 `ScalaCompile` 任务（命名为 `compileModelScala`），并配置四项底层约定：
1. `incrementalOptions.analysisFile`
2. `incrementalOptions.classfileBackupDir`
3. `targetCompatibility`
4. `javaLauncher`
并将原生的 `compileScala` 置空。模型编译产物以普通文件依赖（`files(compileModelScala)`）方式注入主工程编译路径。

### 2.2 IntelliJ IDEA 资源幽灵拷贝防护 (`dropIdeResourceCopies`)
当在 IntelliJ IDEA 中勾选或误选“使用 IntelliJ 运行/构建”时，IDE 会将 `src/main/resources` 拷贝至 `build/classes/java/main`。由于该目录优先于资源目录加载，未经过预处理的 `${version}` 字符串会导致 Fabric 加载崩溃，且打包时会因重复条目引发异常。

**解决方案**：
`build.gradle.kts` 注入专用任务 `dropIdeResourceCopies`，在所有 `runServer` / `runClient` 以及 `jar` 执行前自动清理 `build/classes/java/main` 中的非 `.class` 拷贝。

---

## 3. Mixin 扩展清单

| Mixin 类 | 注入目标 | 实现功能 |
| --- | --- | --- |
| `ItemMixin.java` | `net.minecraft.world.item.ItemStack` | 捕获物品被完整吃完/喝完的时刻，触发 `AlimentIngestion` 吸收逻辑 |
| `PlayerMixin.java` | `net.minecraft.world.entity.player.Player` | 动态干预饱食度消耗与极端失水脱水惩罚 |
| `GrindstoneInputSlotMixin.java` | `net.minecraft.world.inventory.GrindstoneMenu` 输入槽 | 解除原版砂轮只允许放入损坏/附魔物品的限制，允许放入树皮、岩盐、麻黄、黄连、黄柏与甘草 |
| `GrindstoneMenuMixin.java` | `net.minecraft.world.inventory.GrindstoneMenu` | 接入 `AlimentGrinding` 的研磨配方映射表，计算并输出研磨产物 |
| `CameraMixin.java` (Client) | `net.minecraft.client.Camera` | 实现发冷/寒战时仅晃动镜头的衰减振荡体验，不干扰实际实体物理位置 |
| `FogRendererMixin.java` (Client) | `net.minecraft.client.renderer.FogRenderer` | 曼陀罗中毒时将视野迷雾收缩至 8 格内 |
| `HudMixin.java` (Client) | `net.minecraft.client.gui.Gui` | 在原版生命值上方挂载口渴条渲染钩子 |

---

## 4. 自动化工具与资源生成体系 (`tools/`)

模组坚持**数据驱动与代码生成优先**原则，游戏内所有 JSON 与贴图均支持一键全量幂等生成：

### 4.1 数据生成器 (`tools/gen_data.ps1`)
- 自动提取 Minecraft 26.3 客户端 Jar 中的最新数据结构标准。
- 自动生成 400+ 份 JSON 文件：
  - `blockstates/` 与 `models/block/`：柳木全套、生熟汤炼药锅、盐水炼药锅、发酵罐、冷凝管、麻黄 4 阶段植株，以及黄连、黄柏、甘草各自 4 阶段作物植株。
  - `items/` 与 `models/item/`：全部自定义物品的模型与物品定义（包括原药材、碎药材与药水）。
  - `recipes/`：柳木建材合成表、酿酒酵母、发酵罐、冷凝管、搅拌棒、剪刀剪碎麻黄（自定义配方）、麻黄碱药水、黄连/黄柏/甘草药水合成。
  - `loot_tables/`：方块破坏掉落表（时运加成、未成熟/成熟区分）。
  - `worldgen/`：柳树河流注入、岩盐矿脉地底生成、干旱群系麻黄植被生成。
  - `lang/`：双向对齐同步 `en_us.json`、`zh_cn.json` 与 `ja_jp.json`。

### 4.2 贴图引擎 (`tools/gen_textures.ps1`, `tools/gen_ephedra_textures.ps1`, `tools/gen_herbs_textures.ps1`)
- 全量贴图通过脚本算法自动渲染，绝不依赖手工绘制。
- **原版药水贴图复合算法**：自动提取原版 `potion.png` 玻璃瓶图层与 `potion_overlay.png` 液体遮罩，采用正片叠底根据指定色调矩阵（如麻黄碱药水的琥珀金黄色、黄连药水的清亮苦黄色、黄柏药水的棕金色、甘草药水的深棕色）即时合成，像素级百分之百与原版药水风格融合。
- `tools/gen_grape_textures.ps1` 沿用同一套遮罩思路生成葡萄藤各阶段与葡萄酒瓶身，并用其余 `tank_liquid_*` 贴图已有的闭式织纹 `channel = base + ((11x + 7y) mod 25)` 合成两种新罐内液体，使新液体在风格上与旧液体无法区分。

### 4.3 可选跨模组联动（`fabric:load_conditions`）

农夫乐事联动**从各个层面都是可选的**：不写 `fabric.mod.json` 条目。在没有该模组的情况下，本模组必须能启动、通过自检，并可完整游玩。

#### 配方：纯数据层，由资源条件门控

联动的配方与标签部分就是纯 JSON，由 Fabric API 的资源条件系统门控，`fabric-api` 已经把它带进了 classpath。每个联动 JSON 都带一个前置条件：

```json
"fabric:load_conditions": [
  { "condition": "fabric:all_mods_loaded", "values": ["farmersdelight"] }
]
```

真正值得写成测试而不是注释的，是它的**失败方式**。条件解析失败、或者 **codec 字段名拼错**，都不会
在任何地方报错——该资源只是被丢弃并留一行日志，配方就此不存在。因此
`AlimentSelfTest.testGrapeTags` 会同时断言两个方向：每个被门控的配方，在
`FabricLoader.isModLoaded("farmersdelight")` 为真时**必须**存在，为假时**必须**不存在。

#### 营养：一个 `compileOnly` 依赖与三层类结构

农夫乐事的**食物**也带有 Aliment 数值——血糖、维生素 C、碘、钠——而这一半不可能是 JSON，因为它必须在进食钩子内部运行。它是一份真实的编译期依赖，但很**窄**：

```kotlin
compileOnly("maven.modrinth:farmers-delight-refabricated:${project.property("farmersdelight_version")}")
localRuntime("maven.modrinth:farmers-delight-refabricated:${project.property("farmersdelight_version")}")
```

`compileOnly` 把该模组放上编译器的 classpath，而**不**放进发布的 jar，因此发行版既不打包农夫乐事也不要求它；`localRuntime` 只把它装进开发运行环境，好让自检能枚举它的物品。它从 Modrinth 自家的 maven 解析——那不是常见的模组仓库——详见 `build.gradle.kts` 里仓库块上的注释。

**不需要重映射**，这一点与这个 Minecraft 版本有关：26.3 未混淆发布，而农夫乐事的 jar 直接写着 `net/minecraft/world/item/Item`、完全不含 intermediary 名称，所以普通的 `compileOnly` 就是对的，而在更早的版本上这会需要专门的 remap 配置。

**用具名字段而不是注册名字符串，是刻意的选择。** 字符串表不需要依赖，但拼错或改名会**悄无声息**地失效；具名字段表让**编译器**检查全部八十项，因此农夫乐事某次更新若重命名了某个物品，就会让构建失败，而不是悄悄从模型里掉一种食物。

不过加载期风险是真实的：`compileOnly` 意味着这些类在运行时**并不存在**，所以任何具名 `ModItems` 的类在农夫乐事缺席时都不能被加载，否则 JVM 会抛 `NoClassDefFoundError`。因此这套联动被拆成四个文件，保证模组其余部分真正触达的那个守卫类完全不具名该模组：

| 文件 | 是否具名农夫乐事 | 何时被加载 |
| --- | --- | --- |
| `FarmersDelightNutrition.kt` | 否 | 始终。只在初始化时读一次 `isModLoaded`，为假时提前返回 `0f`/`false`。 |
| `FarmersDelightValues.kt` | 否——只提到下面两层 | 仅当 `loaded` 为真；它的任何签名里都不出现农夫乐事的类型。 |
| `FarmersDelightItems.kt` | 是——`ModItems`，写在静态初始化器里 | 仅当该 object 首次被触达，而那只在 `loaded` 为真时发生。 |
| `FarmersDelightRecipes.kt` | 是——`CookingPotRecipe` | 仅自检在它的 `loaded` 守卫之内触达。26.3 起配方材料已移到原版的 `PlacementInfo` 上，但烹饪锅要的容器没有跟着搬走，仍是该模组自己的字段，所以读它就必须具名那个配方类。 |

机制就是 JVM 自身的惰性类加载：一个类在**首次被使用**时才初始化，所以没装农夫乐事的玩家永远不会让 `FarmersDelightItems` 初始化，`ModItems` 也永远不会被索取。中间那层文件的意义在于：守卫类不必为了通过校验而去解析农夫乐事的类型——把该类型挡在所有签名之外，守卫本身就能在模组缺席时被加载并运行。`FarmersDelightItems` 的分类凡是农夫乐事自己发布了标签的，都沿用它自己的物品标签（`c:foods/raw_meat`、`c:foods/cooked_meat`、`c:foods/soup`、`c:foods/vegetable`、`c:foods/pie`、`c:foods/cookie`、`c:foods/food_poisoning`、`farmersdelight:drinks`），所以分组是农夫乐事自己的答案，而不是对它的猜测。

另外两个坑都在设计上避开了：

* **`farmersdelight:knives` 这个物品标签并不存在。** 用到刀的条件必须写约定的 `#c:tools/knife`，
  这正是农夫乐事自己发布并读取的那个标签。
* **约定标签是合并而不是替换。** Aliment 自己的 `data/c/tags/item/*` 只包含自己的条目，也没有
  `replace: true`，因此它们是**追加**到农夫乐事的标签上而不是覆盖。这些标签是无条件的——它们是
  Aliment 这一侧的契约，任何读取 `c:` 的模组都能用，与农夫乐事是否在场无关。

---

## 5. 自动化开发自检框架

由于普通 JUnit 测试难以模拟真实的世界生成（WorldGen）、区块边界检查、玩家手持交互及网络同步，模组设计了基于 Fabric `FakePlayer` 的游戏内无头自检系统：

### 5.1 方块与交互测试 (`AlimentSelfTest.kt`，275 项断言)
- **树木生成与形态**：测试河流河岸检测、树干倾斜算法向水面弯曲、垂柳藤条生成。
- **方块交互**：斧头剥皮掉落树皮、砂轮输入/产出槽研磨逻辑（树皮、岩盐、麻黄、黄连、黄柏、甘草）、剪刀合成耐久扣减 1 点、炼药锅 60 秒营火加热熬汤、蒸馏冷凝管方向判定。
- **禁止中途加水**：断言装着成品或半成品的容器拒绝被补水。发酵罐存的是乙醇的**浓度**，发酵后加水就等于无限装瓶；而粗盐水炼药锅会被**整个替换成满的水炼药锅**，因为它是唯一基于原版 `EMPTY` 分发器、而没有重写 `useItemOn` 的 Aliment 炼药锅——所以断言是「之后它仍然是粗盐水」而不是「水位没涨」。同时从同一个分发器检查岩浆桶，并确认搅拌棒依然可用。随后四个 Aliment 炼药锅**逐一具名**接受水桶与岩浆桶检查，这样将来漏掉某个 `useItemOn` 重写会直接报错，而不是悄然把洞重新打开。发酵罐还按玩家实际的刷法跑了一遍完整流程——装瓶、补水、再装瓶——断言一批料仍然**恰好每格水出一瓶**，不多出；移除守卫后这个循环会用 3 格水产出 8 瓶，这正是该断言存在的意义。
- **作物**：让曼陀罗、麻黄与葡萄藤沿真实的 `BlockItem.useOn` 路径种到方块自称接受的每一种基质上，用骨粉走完全部生长阶段，并断言成熟与未成熟的掉落差异——其中包括葡萄藤**必须**野生生长于其地物所挂载的生物群系，因为种子来自果实，没有地物的葡萄藤将无法获得。葡萄藤的**右键采收**是从玩家一侧覆盖的：测试统计真正落到地上的 `ItemEntity`，而不是相信返回的 `InteractionResult`，所以「报告成功却没掉东西」的采收依然会被判失败；随后它把这株被采过的藤重新催熟再采第二次，这才让「退回 `age=1`」有意义，而不是一个恰好对得上的常量。由于 `BlockBehaviour.useItemOn` 返回 `TRY_WITH_EMPTY_HAND`、`BlockBehaviour.useWithoutItem` 返回 `PASS`，如果采收忘了在 `age=3` 以下放行，就会悄悄吞掉生长中葡萄藤上的每一次右键，所以骨粉既走交互路径、也直接调用 `BoneMealItem`——只调后者的话，点击被吞掉时测试仍会全绿。删掉这段放行会挂 4 项断言，这就是守卫本身。
- **配方与战利品**：验证数据包加载后所有 RecipeSerializer 与 LootTable 的正确性。
- **可选联动**：断言每个被农夫乐事门控的配方**恰好在该模组加载时**存在——因为 `fabric:load_conditions` 是静默丢弃文件的，条件 codec 写错看起来会和正常构建一模一样。该模组在场时，它还会从配方管理器里把烹饪锅的葡萄柚汁读回来、钉住它的形状：一个葡萄柚片而不是两个、一份糖、以及用玻璃瓶接出。材料走 26.3 起移到其上的原版 `PlacementInfo`，容器则是农夫乐事自己的字段，所以那一半经由 `FarmersDelightRecipes`——第四个、也是最后一个被允许具名它某个类型的文件。把配方改回两个果片且不要容器，会恰好让三项断言失败，所以这是一个被守住、而不只是被写下来的形状。
- **不遗漏的跨模组营养**：农夫乐事的食物带有 Aliment 数值，而真正重要的断言是**没有任何一种被漏掉**。手写的食物清单只会自我印证，所以 `testFarmersDelight` 改为向**物品注册表**索取每一个带 `FOOD` 或 `CONSUMABLE` 组件、命名空间为 `farmersdelight` 的物品（共 80 个），并要求每一个都至少带一项数值——因此农夫乐事日后新增的食物、或 Aliment 数值表里被删掉的一行，都会让自检失败而不是无人察觉。删掉一道菜会**恰好**产生 `1 unmodelled` 与一次失败。随后它**真的吃下**其中若干种——走 mixin 挂钩的同一个 `finishUsingItem` 路径——再读取身体状态，因为一张完全正确却什么都没接上的查表会通过上面每一项检查：番茄把维生素 C 从 30 抬到 40，海带卷补上它的碘与混合菜血糖，培根按腌制盐推动钠与氯，一碗骨汤补水，而本身不含维生素 C、碘与钠的煎蛋除了熟肉血糖之外什么都不改——这正是防「给每种农夫乐事食物都塞一个默认值」的反向对照。把进食接线关掉，恰好这六项断言会失败。与之并列的是防止**重复计算**的反向对照——原版苹果不得再收一份农夫乐事的维生素 C、原版面包含糖不得被收两次、原版**奶桶**不得变成农夫乐事的饮品——以及把每个档位与营养素钉在一种不会认错的物品上的逐项抽查（生肉按生肉计、米饭按面包计、海带卷切片恰好是一片卷的三分之一、培根含盐但低于一勺盐）。

### 5.2 生理模型与本地化自检 (`AlimentPhysiologySelfTest.kt`，800+ 项断言)
- **数值稳态**：验证健康状态各指标处于参考范围中心。
- **免疫钟形曲线**：严格验证炎症在低区、中区（有效清除）、高区（细胞因子风暴）时的病原体增长速度，以及载量突破 55 时的免疫应激风暴。
- **电解质紊乱演化**：高钠血症、低钠血症、高钾血症对实体造成的负面状态与致死机制。
- **靶向药理动力学**：
  - 水杨苷退烧抗炎、地塞米松强效平息风暴；
  - 麻黄碱每 tick 衰减（1 游戏日完全代谢）及速掘状态激活；
  - 黄连素对抗细菌：<= 1.5 正常生长，> 1.5 减缓，>= 3.0 彻底阻断生长且始终向下压制，在 1.5 游戏日内将满额感染清零，体内 2.5 游戏日完全代谢；
  - 甘草酸对抗病毒：<= 1.5 正常生长，> 1.5 减缓，>= 3.0 彻底阻断生长且始终向下压制，在 1.5 游戏日内将满额感染清零，体内 2.0 游戏日完全代谢。
- **本地化完整性**：自动反射所有已注册物品与方块，确保英、中、日三语翻译字典覆盖率 100%，无任何缺失未汉化键。
- **参考区间本身被单独钉住，与关于它的规则分开**：自检里每一条症状检查都是**相对**它所在区间写的——"刚进下限以内是安静的，刚出下限就有症状"——这既是自检能熬过一次重新调参的原因，也正是这些检查**都注意不到区间被移动**的原因。碘的下限被调到 **0.25**，好让储备掉到一半之前完全没有症状，而这个值被三种方式断言：`homeostasis` 里的字面 `safeLow`、三日权重检查，以及通过症状层以玩家实际会遇到的形式——**0.30** 的身体必须是安静的，而 **0.24** 的身体不能是。把下限调回 0.40 会让这几项失败，而只按规则写的检查是做不到这一点的。
- **排出速率随浓度变化**：电解质的流失按数值本身缩放——肾脏滤过的就是血里的浓度——所以自检断言的是这条**正比关系**，而不是一张流失量表。这项检查最显然的写法毫无价值，因此被刻意弃用：在任何模型下，钠超标的身体在绝对值上都流失更多，因为稳态本来就在把它往下拉，所以"更高的那个流失更多"根本无法失败。自检改为用**差分**把流失单独隔离出来——同一个身体 tick 一次膀胱满、一次正常，两者的差额就是纯粹的流失，因为稳态拉力在两次里完全相同而被抵消掉——再在两个钠浓度上各测一次。在旧的恒定模型下这两份流失相等，所以它们的比值正是发生变化的东西；自检要求这个比值等于浓度比（180/140 = 1.2857，实测 1.2771），而不只是大于 1。这里刻意只用**一个 tick**，因为稳态是非线性的，跑得久一点两条轨迹就会漂开，差值便不再是干净的隔离。把流失改回恒定会把比值压到正好 1.0 并恰好挂掉这两项。碘带着同一个思路的独立项，而那里两个主张真的会在模型同一行上互相拉扯：过量必须比恒定流失排得更快，**同时**正常储备必须仍然恰好流失那个恒定值，因为"从正常值到下限五天"是文档写明的一个数字。把过量那一项清零会挂掉过量那一半，而耗尽那一半照样全绿——这就是那一项是按「高出正常值的部分」而不是整个数值来写的证据。
- **诊断读数完整性**：`/aliment status` 的文档写的是*所有*生理指标，而自检就用这一点来要求它——手法与上面的本地化检查相同，只是反射的对象从注册表换成了身体状态。它读出 `AlimentData` 实际声明的字段，为这 24 个各配上一段**只有它那一行**才会产出的文本片段，然后要求每一个片段都真的**出现在输出里**。删掉肝脏那一行会报出 `unprinted: [naringin, cyp3a4]` 并挂两项断言。这个「配对」正是关键，而第一版做错了：把声明字段和另一张字段**名字**清单相比，只是两张清单互相比，任何对读数的修改都不可能让它失败——删掉肝脏那一行时它照样全绿。让它真正成为「对读数的断言」而不是「对自己的断言」的，是与命令实际返回文本做匹配的那个片段。行的拼装被从命令处理器里拆成 `statusLines(data)`，理由与血糖仪那条消息相同：让自检断言那段文本，而不是它自己拼给自己的字符串。跑这项检查也正是发现它所守的那个问题的过程：读数一直自称完整，而整条血糖链路与 CYP3A4 指标都不在其中。
- **自检会碰的全局状态**：整个自检里只有一处会改动自己那个玩家之外的东西。和平难度的检查会调用 `MinecraftServer.setDifficulty`，并在 `finally` 里把世界难度放回去——因为血糖的按住是**世界**的属性而不是身体的属性，没有别的路径能触及它。两半都被断言到：正常难度下该标志读到 `false`、和平下读到 `true`——否则一个根本没接到 `Level.getDifficulty()` 上的开关，会替模型"通过"这项检查。两组反向对照说明了这值多少：把 tick 上的闸门去掉会挂 **8** 项断言，把 `addGlucose` 上的去掉会挂 **4** 项，而两组互不重叠——这正是"进入血糖的四条路径是被分别覆盖的、而不是靠某一项恰好撞上的检查"的证据。
