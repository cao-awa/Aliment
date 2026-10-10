[English Version](PHYSIOLOGY.md) | [中文版本](PHYSIOLOGY_zh.md)

# 生理系统 Physiology

Aliment（供养）的核心系统：每个玩家体内持续演算的一套**炎症介质 / 电解质 / 微量元素（碘与维生素C）/
水量 / 体温 / 病原体 / 药物**模型。

设计目标是让"感染"变成一件有过程的事——吃坏东西不会立刻掉血，而是让你的免疫系统慢慢失控，
而柳树皮汤（水杨苷）和地塞米松是控制它的手段，但用多了同样会出事。

面向 Minecraft **26.3**（Fabric，Scala 3.9 / Kotlin 2.4 / Java 25）。

---

## 数据模型

每个玩家身上挂着一份 `AlimentData`，由这几部分组成：炎症介质、病原体、水量、电解质、
微量元素（碘与维生素C）、血糖与胰岛素指数、药物浓度（含香柠檬素）、CYP3A4 酶指数，以及体温。

### 炎症介质 `Mediators`

炎症指数不再是单一数值，而是由五种介质**加权求和**得来：

```scala
inflammation = 0.15 * histamine + 0.20 * prostaglandin + 0.15 * leukotriene + 0.35 * cytokine + 0.15 * bradykinin
```

其中：
* `histamine` (H)：组胺（Histamine）
* `prostaglandin` (P)：前列腺素（Prostaglandin）
* `leukotriene` (Lk)：白三烯（Leukotriene）
* `cytokine` (C)：细胞因子（Cytokine）
* `bradykinin` (B)：缓激肽（Bradykinin）

| 介质 | 权重 | 现实中的作用 | 主要被谁抑制 |
| --- | --- | --- | --- |
| 组胺 histamine (H) | 0.15 | 血管扩张、瘙痒、肿胀（肥大细胞） | 两者都略有效 |
| 前列腺素 prostaglandin (P) | 0.20 | 疼痛、发热（COX 通路） | **水杨苷**（阿司匹林机制） |
| 白三烯 leukotriene (Lk) | 0.15 | 支气管收缩、黏液 | **地塞米松** |
| 细胞因子 cytokine (C) | 0.35 | 全身发热、风暴的主驱动 | **地塞米松**（最强） |
| 缓激肽 bradykinin (B) | 0.15 | 疼痛、血管扩张 | 水杨苷 |

安静状态下这五项分别是 `(H, P, Lk, C, B) = (25.0, 30.0, 30.0, 20.0, 25.0)`，加权和**正好 25.0**（安全区中点）：

```scala
// 静息基准：(H, P, Lk, C, B) = (25.0, 30.0, 30.0, 20.0, 25.0)
val restingInflammation = 0.15 * 25.0 + 0.20 * 30.0 + 0.15 * 30.0 + 0.35 * 20.0 + 0.15 * 25.0 // == 25.0
```

公式详见 `ModelMediators.getInflammation`（权重在 `MediatorLevels`）。

### 电解质 `ModelElectrolytes`（五项）与微量元素 `ModelTraceElements`（碘与维生素C）

**单位是真实的临床单位**：五项电解质用 **mmol/L**，微量元素用 **µmol/L**（碘的整个生理范围只有
零点几 mmol/L，用 mmol/L 写出来只能是"0.0005"，所以换算成千分之一的那一档）。

每一项都有自己的**参考范围**（下表就是临床上的参考值），并且**四个阈值都双向生效**——
缺和多都是病：

```
   min    severeLow   safeLow   normal   safeHigh   severeHigh    max
   |   重度不足  |  轻度不足  |   健康   |  轻度过量  |   重度过量   |
```

| 矿物质 | 正常 | 参考范围 | 轻度线 | 重度线 | 单位 |
| --- | --- | --- | --- | --- | --- |
| 钠 sodium | 140 | **135 – 145** | 125 / 150 | 100 / 190 | mmol/L |
| 钾 potassium | 4.2 | **3.5 – 5.0** | 3.0 / 6.0 | 1.5 / 9.0 | mmol/L |
| 镁 magnesium | 0.85 | **0.70 – 1.00** | 0.50 / 1.50 | 0.20 / 3.0 | mmol/L |
| 氯 chloride | 101 | **96 – 106** | 90 / 115 | 70 / 140 | mmol/L |
| 钙 calcium | 2.35 | **2.10 – 2.60** | 1.80 / 3.00 | 1.00 / 4.0 | mmol/L |
| **碘 iodine** | 0.50 | **0.25 – 0.80** | 0.20 / 1.20 | 0.05 / 2.0 | **µmol/L** |
| **维生素C vitamin C** | 60.0 | **40.0 – 80.0** | 40.0 / 80.0 | 15.0 / 120.0 | **µmol/L** |

**每一项都被拉回正常值**，玩家什么都不做也不会一路滑到极端：回补速率是
`(正常值 - 当前值) * 0.00005`（时间常数 20000 tick，不到一天）。**唯一的例外是碘**，
它只有流失、没有回补（见下）。

**因为单位是真的，喝盐水的后果也是真的**：一份粗盐 / 海水制品给 **+3.0 mmol/L 钠**，
精盐给 **+3.5**，而钠的参考上限是 145——所以

* **喝一份**：140 → 143 或 143.5，**还在范围内**；
* **喝两份**：→ 146 或 147，**越过参考范围**，开始口渴。

**腌肉是唯一一种「不是一勺盐」的膳食盐。** 培根与火腿在真实厨房里是盐水腌的猪肉，而腌就是盐，
所以农夫乐事的七种腌制品带钠——但一片培根咸，并不等于一勺盐，因此它们刻意排在盐的一份 3.0 **之下**：
一片肉 **+0.8**，以它为主的一盘（培根三明治、培根煎蛋）**+1.2**，整只蜜汁火腿 **+2.0**。连火腿都
不到一份盐，所以没有任何一种腌制品能单独把身体推出范围；两份较大的盘菜才会。该模组里其余食物都不算数，
因为农夫乐事既没有盐这个物品，**它自己的配方里也完全没有用到盐**，所以它的储藏室里别处没有藏钠。

碘仍然只能靠食物补——身体不会合成它，**也不会把它留住**：

| 食物 | 补碘量 | 附加效果与营养 |
| --- | --- | --- |
| 海带 `minecraft:kelp` | **+0.05 µmol/L** | 充饥 1 / 饱食 0.6 |
| 干海带 `minecraft:dried_kelp` | **+0.10 µmol/L** | 充饥 1 / 饱食 0.6 |
| 海藻 `aliment:seaweed` | **+0.10 µmol/L** | 充饥 1 / 饱食 0.2，可在海底农耕无限采收 |
| 熟海藻 `aliment:cooked_seaweed` | **+0.125 µmol/L** | 充饥 3 / 饱食 0.6，烹饪产物，高效碘源 |
| 海藻碘盐 `aliment:seaweed_iodized_salt` | **+0.20 µmol/L** | 同时补充 **+1.5 mmol/L 钠与氯**，兼顾电解质稳态与防甲减 |
| 海带卷 `farmersdelight:kelp_roll` | **+0.15 µmol/L** | *农夫乐事。* 三片干海带裹一碗米饭；米饭是主体，所以按「含海带的菜」计，而不是按三片干海带计 |
| 海带卷切片 `farmersdelight:kelp_roll_slice` | **+0.05 µmol/L** | *农夫乐事。* 一卷的三分之一，正是砧板切出来的份量 |

不吃含碘食物时碘每天固定流失 0.09 µmol/L，**五天整掉光**（0.50 → 下限 0.05），然后停在下限：
* **每日维持需求**：人体每天自然流失 0.09 µmol/L 碘，若发烧出汗流失进一步加速；
* **摄入策略**：
  * **日常维持**：每天 2 份干海带（各 +0.10）或 2 份海藻（各 +0.10）刚好抵住全天的自然流失，单独一份不够；
  * **高效抗甲减**：熟海藻（+0.125）补充效率高，四份就能把濒临枯竭的储备拉回大半；
  * **便携远征补给**：海藻碘盐（+0.20）一次性补充较多碘储备，且额外提供电解质钠与氯（各 +1.5 mmol/L），兼顾大量饮水后稀释性低钠血症的预防。
吃多了会越过上限 0.80，而多出来的部分会因为**自身浓度偏高而排得更快一点**（每天多排的速率正比于
超出正常值的部分），所以不会长期堆积。

参考下限**刻意比临床参考范围更低**：症状从 **0.25** 才开始出现，而不是 0.40。甲状腺是滞后的响应者，
玩家在碘从正常值掉下来很长一段路之前不该有任何感觉；真正的重度甲减仍然要等到 `severeLow`（0.20）。

### 维生素C（抗坏血酸）

人体无法内源合成维生素C，血清临床参考范围为 **40.0 – 80.0 µmol/L**（基准健康水平为 **60.0 µmol/L**）。

* **一级动力学代谢消除（浓度越高排出越快）**：

  ```scala
  // 一级消除动力学：半衰期 5 游戏日（120,000 ticks）
  // k = ln(2) / 120000 ≈ 5.776e-6 / tick
  val k = Math.log(2.0).toFloat / 120000f
  vitaminC -= vitaminC * k
  ```

  - **排出速率正比于当前体内浓度**：血清浓度高时排泄速率极快，低浓度时排泄显著减缓；
  - **衰减半衰期为 5 个游戏日（120,000 ticks）**：当玩家处于正常范围最大值（**80.0 µmol/L**）且不吃任何植物性食物时，恰好需要整整 **5 个游戏日** 代谢掉至异常线（**40.0 µmol/L**）。

* **临床症状表现**：
  - **轻度缺乏（< 40.0 µmol/L，早期维生素C缺乏）**：获得 **挖掘疲劳 I（Mining Fatigue I）** 负面效果；
  - **严重缺乏（< 15.0 µmol/L，坏血病期）**：叠加获得 **虚弱 I（Weakness I）** 效果（即同时受到 **挖掘疲劳 I + 虚弱 I**）；
  - **过量（> 80.0 µmol/L）**：水溶性维生素通过肾脏快速无害排出，不产生负面病理反应。
* **膳食食物来源（水果、胡萝卜、南瓜及植物食材）**：

| 食物 | 维生素C 补充量 |
| --- | --- |
| 苹果 `minecraft:apple` | **+12.0 µmol/L** |
| 金苹果 `minecraft:golden_apple` | **+20.0 µmol/L** |
| 附魔金苹果 `minecraft:enchanted_golden_apple` | **+30.0 µmol/L** |
| 西瓜片 `minecraft:melon_slice` | **+8.0 µmol/L** |
| 甜浆果 `minecraft:sweet_berries` / 发光浆果 `glow_berries` | **+6.0 µmol/L** |
| 胡萝卜 `minecraft:carrot` | **+10.0 µmol/L** |
| 金胡萝卜 `minecraft:golden_carrot` | **+15.0 µmol/L** |
| 南瓜派 `minecraft:pumpkin_pie` | **+15.0 µmol/L** |
| 甜菜根 `minecraft:beetroot` | **+6.0 µmol/L** |
| 甜菜汤 `minecraft:beetroot_soup` | **+16.0 µmol/L** |
| 曼陀罗果 `aliment:mandrake_fruit` | **+10.0 µmol/L** |
| 海藻 `aliment:seaweed` | **+5.0 µmol/L** |
| 熟海藻 `aliment:cooked_seaweed` | **+3.0 µmol/L** |
| 葡萄柚片 `aliment:grapefruit_slice` | **+10.0 µmol/L**——植物性食物，又是柑橘，所以和胡萝卜一样 |

**农夫乐事的植物按同一把尺子补维生素 C。** 蔬菜就是蔬菜，无论出自哪里，所以番茄等于胡萝卜（**+10.0**），
卷心菜 **+8.0**，洋葱 **+6.0**，番茄酱 **+8.0**。拼装的菜肴只拿到用料给它的那一份，不多不少——这正是
沙拉成为整张表里最富的原因（璀璨沙拉 **+18.0**、水果沙拉 **+16.0**），而只拿一片番茄做点缀的三明治
只有 **+4.0**。肉、蛋、面包、米饭、奶与巧克力一律为零，因为维生素在植物里；烤三文鱼无论怎么烤都不含
维生素 C。

### 水量 `water`

| 项 | 值 |
| --- | --- |
| 正常范围 | **30 – 100** |
| 上限 | 200 |
| 超过 100 | 标记为**过度补水**：虚弱 + 挖掘速度降低 |
| 低于 30 | 标记为**脱水** |
| **不出汗自然流失** | **5 游戏日从满（100）完全耗尽（0）**（无发热出汗时） |
| **发热 39 °C 流失** | **3.5 游戏日完全耗尽（100 → 0）**（伴随出汗加速流失） |
| **发热 40 °C 流失** | **2 游戏日完全耗尽（100 → 0）**（严重高热重度出汗） |
| 一份饮品补水 | **+15**（水、药水、蘑菇煲、牛奶、两种柳树皮汤、以及它们的带盐版本） |
| 一片葡萄柚片补水 | **+5**——果片是吃进嘴里的，不是喝下去的，所以拿不到一整份饮品的量 |

超过 100 后肾脏加速排水（最多两倍），同时**稀释并加速排出电解质**；
高钠和高钙也会加重口渴。体温高于 38.25 °C 时触发非线性出汗机制，随高热程度加剧失水。

### 体温 `temperature`

| 项 | 值 |
| --- | --- |
| 正常 | **37.0 °C** |
| 舒适区 | **36.0 – 38.5**（什么都不发生） |
| 发烧 / 超高热 | ≥ **38.5** / ≥ **40.0** |
| 普通免疫发热上限 | **39.5 °C**（感染载量 ≤ 55 时的免疫应答最高体温） |
| 应激风暴高热上限 | **42.0 °C**（感染载量 > 55 应激风暴或严重致热物） |
| 轻度 / 重度失温 | ≤ **36.0** / ≤ **35.0** |
| 上下限 | 30 – 42 |
| 趋向目标的速率 | 0.0004 / tick（约 2500 tick 走完 63%，两分多钟） |
| 疼痛提示阈值 `PAIN_THRESHOLD` | **39.5 °C** |

> 发烧的起点是 **38.5** 而不是 38.0。38 度是**不开药方也能到的温度**：炎热群系、
> 着火、加上甲状腺偏热就能凑出来。在感染载量 20~55 的常规免疫介入期间，体温被限制在 39.5 °C 以内；
> 只有当感染突破 55 触发应激风暴时，才会冲向 40 °C 以上的超高热。

`feverGrade(temperature)` 是在同一组阈值上的第二套刻度，共三档——38.5 起为 **1**、39.5 起为 **2**、
40.0 起为 **3**——它存在的唯一目的就是作为 `aliment:fever` 显示给玩家。它刻意**不是** `thermalTier`
（那个只有两档发烧）；`hasPain(temperature)` 则是第三条只有一级的刻度，阈值是 `PAIN_THRESHOLD`，
显示为 `aliment:pain`。两者都不返回任何模型会消费的东西，也都不改变任何症状：
39.5 °C 时的虚弱与 38.6 °C 时完全一致，这一点由自检断言。`PAIN_THRESHOLD` 与
`FEVER_NORMAL_IMMUNE_MAX` 是两个独立常量（今天都是 39.5），这样"身体自己对抗感染能到哪"
与"身体不吭声能忍到哪"就可以分别调整。

体温由四项相加决定（见模型一节）：感染带来的**前列腺素**、命令注射的**热原**、
**甲状腺**（碘）对设定点的偏移，以及**环境**。环境里只有一部分能突破体温调节。

### 药物与抗炎机制

| 药物 | 起效浓度 | 上限 | 代谢时间 | 主要药理机制 |
| --- | --- | --- | --- | --- |
| 水杨苷 salicin | 1.0 | 3.0 | **3 游戏日**（线性） | 抑制前列腺素合成（COX 抑制剂），退热镇痛，压制炎症 |
| 地塞米松 dexamethasone | 1.0 | 2.0 | **2 游戏日**（线性） | 糖皮质激素，强力抑制细胞因子与白三烯释放，平息免疫风暴 |

* **零级代谢消除动力学方程**：

  ```scala
  // 零级线性代谢消除：
  salicin = Math.max(salicin - 3.0f / 72000f, 0f)       // -4.167e-5 / tick（3 游戏日代谢 3.0）
  dexamethasone = Math.max(dexamethasone - 2.0f / 48000f, 0f) // -4.167e-5 / tick（2 游戏日代谢 2.0）
  ```

> **关键机制澄清**：**水杨苷与地塞米松均不直接杀灭或清除病原体**。它们的生理本质是**抗炎与免疫抑制**：
> - 适量使用可防止炎症过高导致的免疫风暴（炎症 >= 75 免疫功能同样崩溃并灼伤机体）；
> - **过量**（超过起效浓度）会将静息炎症水平整体压低至安全区以下（< 12），导致**免疫抑制**；
>   由于病原体依赖正常的免疫系统进行清除，免疫抑制状态下机体丧失清除能力，感染会迅速失控生长至 40 以上！

### 曼陀罗生物碱 `scopolamine` / `atropine`

曼陀罗（果实与种子，吃了才有）带进来两个独立数据，各自上限 **5.0**，**一个游戏日线性代谢完**：

```scala
// 曼陀罗生物碱线性消除：1 游戏日（24,000 ticks）代谢 5.0
scopolamine = Math.max(scopolamine - 5.0f / 24000f, 0f) // -2.083e-4 / tick
atropine = Math.max(atropine - 5.0f / 24000f, 0f)       // -2.083e-4 / tick
```

| 吃的东西 | 东莨菪碱 (S_scop) | 阿托品 (A_atro) |
| --- | --- | --- |
| `mandrake_fruit` | **+1.0** | **+0.1** |
| `mandrake_seeds` | **+0.75** | **+0.1** |

两者之和 `alkaloidSum = scopolamine + atropine` 决定**体温**（设定点上移，不走前列腺素，所以**水杨苷退不掉**）：

```scala
val deltaT =
  if (alkaloidSum >= 4.0f) 4.0f      // 核心体温目标 -> 41.0 °C
  else if (alkaloidSum >= 2.5f) 2.5f // 核心体温目标 -> 39.5 °C
  else if (alkaloidSum >= 1.5f) 1.0f // 核心体温目标 -> 38.0 °C
  else 0.0f
```

单个或合计决定**视觉模糊**：

```scala
val visualBlurActive = scopolamine >= 2.3f || atropine >= 2.3f || alkaloidSum >= 2.7f
```

激活时雾收到 **8 格**，8 格外的方块全糊掉。

高温和视觉模糊**互不干涉、可以叠加**：发烧的三层画面效果（泛红、扭曲、动态模糊）和
药物模糊是两个独立的东西，同时成立就同时挂在屏幕上。这就是"吃两颗曼陀罗会又烧又瞎"。

> 实现上体温那部分是模型的（`Physiology.anticholinergicFever`，加进 `targetTemperature`），
> 视觉那部分分两半：后处理效果由服务端像别的画面效果一样请求（`anticholinergic_blur`），
> **雾是客户端渲染决定**，所以走同步标志 `AlimentClientState.blurred` -> `FogRendererMixin`
> 把 `FogData` 的环境雾与天空/云淡出收到 8 格（照原版失明效果的做法，渲染距离本身不动）。

### 裸盖菇素与裸盖菇素醇 `psilocybin` / `psilocin`

橘黄裸伞（**生吃**；熟的什么物质都不带）带进来两个数据，各自上限 **10.0**：

| 数据 | 作用 | 代谢 |
| --- | --- | --- |
| 裸盖菇素 psilocybin | **本身没有任何效果**，是前药 | **半游戏日内一比一**变成裸盖菇素醇（1.3 每半日） |
| 裸盖菇素醇 psilocin | 视觉四阶段 + 体温 | **固定速率**：1.3 每游戏日，与体内含量无关 |

* **前药转化与零级代谢动力学**：

  ```scala
  // 前药转化：1.3 单位在半个游戏日（12,000 ticks）内 1:1 转化为裸盖菇素醇
  val converted = Math.min(psilocybin, 1.3f / 12000f) // 1.083e-4 / tick
  psilocybin -= converted

  // 裸盖菇素醇消除：固定零级速率 1.3 每游戏日（24,000 ticks）
  psilocin = clamp(psilocin + converted - (1.3f / 24000f), 0f, 10.0f) // -5.417e-5 / tick
  ```

一颗生蘑菇一次给 **1.3 / 1.3**。因为代谢是**固定速率**而不是按比例，一次蘑菇的总量是
1.3 + 1.3 = 2.6，正好 **两个游戏日**清完；五颗（6.5 / 6.5）总量 13，就是 **十个游戏日**——
吃得多不是"待得久一点"，是线性变长（上限 10/10 时约十六日）。

视觉四阶段（服务端按 `psilocin` 选一个后处理效果，客户端只负责画）：

| 条件 | 效果 |
| --- | --- |
| `psilocin > 1.2` | 从方块边缘**随机方向拉出彩色线条**（每 8 像素格子用自己的哈希选一个方向和长度，某像素沿该方向一段距离外正好有亮度跳变 -> 说明那里是边缘，这个像素就点亮。所以亮起来的其实是边缘的"位移副本"，四段不同距离不同强度叠起来就是一条从边缘射出的锥形线；边缘本身**不会被整条描边染色**） |
| `psilocin > 1.7` | 方块开始**染上随机亮色**（每 8 像素一格随机色相，**混回原像素而不是覆盖**，保留明暗所以形状仍然看得清；强度只有 0.30），再叠一层**全屏彩色噪点**（一像素一粒，约 45% 的像素各拿一个随机色相，强度 0.20） |
| `psilocin > 2.5` | 全屏幕**轻度扭曲**（两组正弦波推采样点） |
| `psilocin > 5.0` | **剧烈扭曲**，同时体温开始上升（最高 **39 °C**） |
| `psilocin >= 7.0` | 体温可达 **41 °C** |

阈值是**严格大于**（`>`），体温那档从 5 起、7 再上一档，都是设定点上移、不走前列腺素，
所以**和曼陀罗的热、感染的热全部叠加**（模型上限仍是 42 °C）。四阶段是**包含关系**：
第 4 阶段仍然有彩线和染色，只是强度更高——四个后处理 JSON 指向同一个着色器
（`shaders/post/psilocin.fsh`），只是三个强度不同，而不是四层叠加。

### 存在哪里

用 Fabric 的 **Data Attachment API**：

| Attachment | 持久化 | 同步 | 说明 |
| --- | --- | --- | --- |
| `aliment:physiology` | ✅ **死亡重置** | ❌ | 完整数据，服务端权威。没有 `copyOnDeath()`：复活的是一个新身体，感染不会跟着走 |
| `aliment:client_state` | ✅ | ✅ 全客户端 | 抖动序号 + 幅度 + 水量整数，客户端用来画镜头抖动和口渴条 |
| `aliment:runtime` | ❌ | ❌ | 抖动计时、蝙蝠冷却、上次同步值 |

水量只在**整数位变化时**才重发，所以一条正在下降的口渴条大约每 270 tick 一个包，
而不是每 tick 一个。

### 创造模式：整套系统停摆

`AlimentSymptoms.isFrozen(player)` 就是 `player.isCreative`，创造模式玩家身上：

* **模型不推进**：`tick` 直接返回，病原体不增长也不清除、体温不走、药物不代谢；
* **不施加任何症状**：不挂效果、不扣血（重度感染伤害也在 tick 里）、不算挖掘惩罚、
  不给饱食度倍率（`PlayerMixin` 那条也返回 1.0）；
* **屏幕清干净**：已经挂上的发热扭曲 / 动态模糊 / 冷抖动当 tick 就撤掉，抖动本身靠
  "服务端不再推序号"停住；
* **吃喝注射都进不去**：`AlimentIngestion` 的两个入口同样先看这个开关，
  否则状态会从另一扇门继续变动。原版的食物 / 饱和度的部分不受影响。

是**冻结**不是**重置**：带病进创造不会当场痊愈，回到生存就从原处继续。`/aliment` 指令
仍然能改数据，但因为 tick 停着，改完也看不到效果。

---

## 感染来源

一共有三条路，全都是"传染"，没有一条是运气：

| 来源 | 病原体 | 概率 | 单次载量 |
| --- | --- | --- | --- |
| 生肉（牛猪鸡羊兔鳕鲑热带鱼）、腐肉、毒马铃薯 | 细菌 | **30%** | +6 |
| 生柳树皮汤（含带盐版） | 细菌 | **30%** | +6 |
| **任何生物**走到 2 格以内 | 病毒 | **5% / 秒** | +5 |
| **免疫抑制时**（炎症 ≤ 12）凭空 | 细菌 | **15% / 秒** | **随机 +4 ~ +12** |

* 食物走 `Item.finishUsingItem`（见下面的 mixin），所以是"真正咽下去的那一刻"判定。
* 接触判定看的是**任何 `Mob`**——牛、狼、村民、蝙蝠都算，盔甲架不算（它不是 `Mob`）。
  每秒掷一次，所以在一群牛里站十秒，几乎必然中招。
* **免疫抑制是唯一一条不需要外因的路**：炎症被压到 12 以下是免疫抑制，身体自己的菌群
  就能进来，掷骰间隔同样是一秒。这也让"地塞米松打多了"和"柳树皮汤喝太多"真的危险。

---

## 模型

每 tick 演算一次，核心是纯数据运算（`AlimentPhysiology.tick(data)`，不依赖任何 Minecraft 对象）。

### 感染动力学与免疫分期

病原体基础逻辑斯蒂增殖速率：

```scala
// 逻辑斯蒂增殖方程：r = 0.0004 / tick，容纳上限 K = 100.0
val baseGrowth = 0.0004f * load * (1.0f - load / 100.0f)
```

机体免疫系统主动清除速率：

```scala
// 主动清除速率：基准清除速度 = 20.0 载量 / 48,000 ticks（2 游戏日）
val immuneClearance = if (immuneActive || load > 20.0f) {
  baseGrowth * competence + (20.0f / 48000f) * competence
} else {
  0.0f
}
```

其中 `competence` 为综合炎症效能函数。

- **隐匿生长期（载量 `load <= 20.0`）**：
  - 感染病原体持续按逻辑斯蒂生长（logistic growth）；
  - 免疫系统尚未警觉介入，刺激强度为 0，炎症介质与免疫指数维持基准静息状态（`inflammation == 25.0`）；
  - 体温维持正常基准（`temperature == 37.0 °C`），无前列腺素发热。
- **免疫介入与压制期（载量 `20.0 < load <= 55.0`）**：
  - 载量突破 **20.0** 时触发免疫介入（`immuneActive = true`），炎症介质开始上升；
  - 前列腺素随之升高带动体温上升，此阶段最高发热限制在 **39.5 °C**；
  - **正常范围免疫的压制速度**：在正常免疫力（`competence == 1.0`）下，主动压制能在 **2 游戏日（48,000 tick）** 内将感染完全压至 0；
  - 感染降至 0 后，机体退出免疫应答状态（`immuneActive = false`），介质平稳回退至静息基准。
- **免疫失常与应激风暴期（载量 `load > 55.0`）**：
  - 若免疫力不正常（如过量使用水杨苷/地塞米松引发免疫抑制，或免疫力缺陷），免疫压制失效，感染将持续肆虐生长突破 55；
  - 载量突破 **55.0** 时免疫系统进入严重应激状态，炎症介质暴增（细胞因子激增 2.5 倍），极易触发致命免疫风暴（`inflammation >= 75.0`）；
  - 体温上限解锁至 42.0 °C 超高热，同时造成败血症魔法持续伤害。

免疫效能呈非对称高斯钟形曲线，在安全区（25.0）最高，两侧塌陷：

```scala
// 非对称高斯钟形曲线：sigma = 10.0（25 以下）/ 15.0（25 以上）
val delta = inflammation - 25.0f
val sigma = if (inflammation < 25.0f) 10.0f else 15.0f
val bell = Math.exp(-(delta * delta) / (2.0 * sigma * sigma)).toFloat
val lowEndSuppression = clamp(inflammation / 12.0f, 0.0f, 1.0f)
val competence = bell * lowEndSuppression
```

| 炎症 | 0 | 6 | 12 | 25 | 40 | 50 | 75 | 100 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 免疫力 | 0.00 | 0.22 | 0.69 | **1.00** | 0.61 | 0.25 | 0.004 | ~0 |

**炎症太低 -> 免疫罢工；炎症太高 -> 免疫风暴，同样压不住病原，还反过来伤害宿主。**

### 介质动力学

感染刺激强度分段函数：

```scala
val stimulus = if (!immuneActive && load <= 20.0f) {
  0.0f
} else if (load <= 55.0f) {
  (load - 20.0f) / (55.0f - 20.0f)
} else {
  1.0f + 2.5f * clamp((load - 55.0f) / (100.0f - 55.0f), 0.0f, 1.0f)
}
```

抗炎药物抑制与响应阻尼（水杨苷与地塞米松）：

```scala
val salicinEffect = Math.min(salicin / 1.0f, 1.0f)
val dexEffect = Math.min(dexamethasone / 1.0f, 1.0f)
val maxDrugEffect = Math.max(salicinEffect, dexEffect)

val damping = Math.max(1.0f - 0.88f * maxDrugEffect, 0.05f)
val overdose = Math.max(salicin / 1.0f - 1.0f, 0.0f) + Math.max(dexamethasone / 1.0f - 1.0f, 0.0f)
val baselineScale = Math.max(1.0f - overdose * 1.8f, 0.0f)
```

各介质的目标渐近值公式：

```scala
// 静息基准：(H, P, Lk, C, B) = (25.0, 30.0, 30.0, 20.0, 25.0)
val cytokineTarget = 20.0f * baselineScale + 100.0f * stimulus * damping * (1.0f - 0.4f * dexEffect)
val histamineTarget = 25.0f * baselineScale + 45.0f * stimulus * damping
val bradykininTarget = 25.0f * baselineScale + 60.0f * stimulus * damping
val prostaglandinTarget = 20.0f * baselineScale +
  (cytokine * 0.5f + 25.0f * stimulus) * damping * (1.0f - 0.5f * salicinEffect)
val leukotrieneTarget = 20.0f * baselineScale +
  (cytokine * 0.5f + 20.0f * stimulus) * damping * (1.0f - 0.4f * dexEffect)

// 每个介质以 k_m = 0.002 / tick（约 25 秒）逼近自己的目标
mediator += (target - mediator) * 0.002f
```

前列腺素和白三烯**由细胞因子诱导**（COX-2 / 脂氧合酶），所以它们是下游产物——
这也让"地塞米松抑制上游、水杨苷抑制下游"的差别有了实际意义。
**两者均不直接清除病原体，而是通过压低炎症介质来平抑反应。**

### 抗菌与抗病毒靶向药物动力学（黄连素与甘草酸）

与只平抑免疫反应的水杨苷/地塞米松不同，**黄连素（Berberine）**与**甘草酸（Glycyrrhizin）**直接针对病原体本身发挥抑菌与抑毒药效：

对于靶向药物浓度在 `[0.0, 7.0]`，减速阈值为 1.5，抑制阈值为 3.0：

```scala
if (drugConc >= 3.0f) {
  // 彻底阻断病原体复制；主动清除速率随药物浓度线性提高
  val drugClearance = (100.0f / (1.5f * 24000f)) * (drugConc / 3.0f)
  load = Math.max(load - immuneClearance - drugClearance, 0.0f)
} else if (drugConc > 1.5f) {
  // 抑制病原体增殖速率，使免疫系统占据上风
  val slowRatio = (drugConc - 1.5f) / (3.0f - 1.5f)
  val reducedGrowth = baseGrowth * (1.0f - 0.75f * slowRatio)
  load = Math.max(load + reducedGrowth - immuneClearance, 0.0f)
} else {
  // 正常病原体复制减去机体免疫清除
  load = Math.max(load + baseGrowth - immuneClearance, 0.0f)
}
```

- **黄连素（0.0 ~ 7.0）**：特异性对抗**细菌（Bacteria）**
  - 在 CYP3A4 基准值下，它以最高浓度 7.0 为基准需 **2.5 游戏日（60,000 ticks）** 线性代谢归零，
    而酶指标会缩放这个速率——见[香柠檬素与 CYP3A4](#naringin-and-cyp3a4)：
    ```scala
    // CYP3A4_NORMAL 是 85，所以在干净的基准下系数正好是 1，
    // 这就是模型一直以来清除黄连素所用的 7.0f / 60000f = -1.167e-4 / tick。
    berberine = Math.max(
      berberine - (7.0f / 60000f) * (cyp3a4 / 85.0f),
      0.0f)
    ```

- **甘草酸（0.0 ~ 7.0）**：特异性对抗**病毒（Virus）**
  - 在体内以最高浓度 7.0 为基准需 **2 游戏日（48,000 ticks）** 线性代谢归零：
    ```scala
    glycyrrhizin = Math.max(glycyrrhizin - 7.0f / 48000f, 0.0f) // -1.458e-4 / tick
    ```

<a id="naringin-and-cyp3a4"></a>

### 香柠檬素与 CYP3A4

在这个模型里，葡萄柚不是药，香柠檬素本身也没有任何效果。它有的是一个**后果**：它压住
**CYP3A4**——清除黄连素的那个肝酶，而黄连素没有别的清除途径。于是这三个数连成一条链：
果片抬高香柠檬素，香柠檬素决定一个酶活性，酶活性再缩放一次代谢。

香柠檬素范围 **0.0 ~ 10.0**（十个葡萄柚片），从上限起以**一个游戏日（24,000 ticks）**线性清完，
是甘草酸寿命的一半：

```scala
naringin = Math.max(naringin - 10.0f / 24000f, 0.0f) // -4.167e-4 / tick
```

CYP3A4 范围 **0.0 ~ 100.0**，在从未吃过葡萄柚的身体里是 **85**。它是香柠檬素上的**曲线**
而不是阶梯，所以指标是从一档滑向下一档，而不是瞬间跳过去；多吃一片，总归会从肝脏再多拿走一点。
下面这些标定点就是它的全部：

| 香柠檬素 | CYP3A4 | 黄连素被清除的速率 |
| --- | --- | --- |
| 0 | **85** | 1.00x——模型当初写下的速率 |
| 2 | **60** | 0.71x |
| 4 | **45** | 0.53x |
| 7 | **25** | 0.29x |
| 8.5 及以上 | **10** | 0.12x |

两点之间用 smoothstep 插值，它的值与斜率在两端都为零，所以曲线在**每个**标定点上都是平的，
两段相接处也没有折角。超过 8.5 后保持在 10，这正是"八片就是抑制的最深处"的原因：

```scala
// 各节点，按香柠檬素升序。
val knots = Array((0f, 85f), (2f, 60f), (4f, 45f), (7f, 25f), (8.5f, 10f))

def cyp3a4For(naringin: Float): Float = {
  val load = Math.max(naringin, 0f)
  var segment = 0
  while (segment < knots.length - 1 && load > knots(segment + 1)._1) segment += 1
  if (segment >= knots.length - 1) knots(knots.length - 1)._2
  else {
    val (fromNaringin, fromActivity) = knots(segment)
    val (toNaringin, toActivity) = knots(segment + 1)
    val t = clamp((load - fromNaringin) / (toNaringin - fromNaringin), 0f, 1f)
    fromActivity + (toActivity - fromActivity) * (t * t * (3f - 2f * t))
  }
}
```

酶指标是**由 tick 写的，不是由吃写的**：果片只改动 `naringin`，下一 tick 才从它读出 `cyp3a4`，
所以两者最多只会在一个 tick 内不一致，绝不会更久。这也正是指标能自己恢复的原因——
香柠檬素一没，曲线自己就返回 85，不需要另立一项恢复速率去跟它对齐。

这个效果有多大值得直说，因为它是模组里第一个"玩家吃下的东西会改变另一样东西持续多久"的相互作用。
一份黄连（**1.1 黄连素**）单独吃下去约 **9,400 tick** 清完；先吃九个葡萄柚片再吃同一份黄连，
就要约 **20,100 tick**——比两倍还多，因为在那段时间的前半段，肝脏是从 10 一路爬回 85，而不是满速运转。
香柠檬素只持续一天，正是这一点给它封了顶：如果它再短些，最深的那个节点就会在剂量生命的大半时间里
什么都不做。

### 水分与出汗消耗模型

体内总水量每 tick 的变化速率：

```scala
// 肾脏排水流失
var renalLoss = (100.0f / 120000f) * (1.0f + 0.01f * Math.max(water - 100.0f, 0.0f))
if (sodium > 150.0f) renalLoss *= 1.3f
if (calcium > 3.0f)  renalLoss *= 1.2f

// 发热出汗流失（核心体温高于 38.25 °C 触发，即 deltaT = temp - 37.0 > 1.25）
val deltaT = Math.max(temperature - 37.0f, 0.0f)
val sweatLoss = if (deltaT > 1.25f) {
  Math.max((-1.0f / 3360.0f) * deltaT + (1.0f / 4200.0f) * deltaT * deltaT, 0.0f)
} else {
  0.0f
}

water = clamp(water - renalLoss - sweatLoss, 0.0f, 200.0f)
```

总流失速度基准：
- 正常体温（37.0 °C，无发汗）：`waterLoss = 1 / 1200 / tick`（整 5 游戏日耗尽 100 水分）
- 发热 39.0 °C：`waterLoss = 1 / 840 / tick`（整 3.5 游戏日耗尽 100 水分）
- 高热 40.0 °C：`waterLoss = 1 / 480 / tick`（整 2.0 游戏日耗尽 100 水分）

### 电解质稳态与排出

每项血清电解质的动力学方程：

```scala
// 过度补水冲刷系数
val flushFraction = clamp((water - 100.0f) / 100.0f, 0.0f, 1.0f) * 0.000009f

// 出汗流失系数
val sweatFraction = if (temperature > 38.25f) Math.max(temperature - 37.0f, 0.0f) * 0.000002f else 0.0f

// 相对排出系数：(Na: 1.0, Cl: 1.0, K: 0.7, Mg: 0.4, Ca: 0.4)
// 再乘上「自身浓度」：肾脏处理的滤过负荷就是血里的浓度，
// 所以浓度高的电解质排得比浓度低的快。
val concentration = currentConcentration / normalConcentration
val totalLossRate = (flushFraction + sweatFraction) * excretionMultiplier * concentration * normalConcentration
val restorationRate = 0.00005f * (normalConcentration - currentConcentration)

electrolyte += restorationRate - totalLossRate
```

钠和氯流失最快，镁和钙最慢，因此猛喝水最先导致稀释性低钠血症。

因为流失量乘上了自身浓度，正常值附近的曲线完全没有变（在该点系数正好是 1），而**已经偏高的身体会把
这种电解质排得比健康的身体更快**。这正是「各自的排出速率随各自浓度而变」的含义：同一份冲刷系数，
在满仓时排得比接近空仓时快，而各矿物离场的先后顺序（氯钠最先、镁钙最后）仍然保持不变，因为每种矿物
自己的系数没有动。

碘是唯一一种额外带自己那一项的矿物，因为它没有回补机制能把它拉回来：

```scala
val excess = Math.max(iodine - 0.50f, 0f)
val iodineLoss = (drainFraction + flush * EXCRETION_IODINE) * 0.50f + excess * 0.000002f
```

流失在**数值不高于正常值时是恒定的**，所以「从 0.50 到 0.05 下限正好五天」仍然是一个精确值；只有
**过量**的部分才会排得更快一点——这让超量的碘更快排掉，同时不弯曲测试与本文档都写明的那条耗尽曲线。

### 碘与维生素C

碘代谢方程（无自体回补，持续单向消耗）：

```scala
// 碘：正常值及以下每天固定损耗 0.09 µmol/L（整 5 游戏日从 0.50 耗空至下限 0.05），
// 出汗与多尿额外带走；超出正常值的部分按自身浓度再快一点排出（每天约 0.000002 * 超出量 / tick）
val excess = Math.max(iodine - 0.50f, 0f)
val iodineLossRate = (0.45f / 120000f) + (flushFraction + sweatFraction) * 0.35f + excess * 0.000002f
iodine = Math.max(iodine - iodineLossRate, 0.05f)

// 维生素C：一级动力学代谢，半衰期 5 游戏日（120,000 ticks）
val kVitC = Math.log(2.0).toFloat / 120000f
vitaminC -= vitaminC * kVitC
```

体内浓度越高排出越快，半衰期整 5 游戏日（120,000 ticks 从正常上限 80.0 降至异常线 40.0）。

### 体温调节

目标核心体温计算公式：

```scala
val prostaglandinExcess = Math.max(prostaglandin - 20.0f, 0.0f)
val feverMax = if (pathogenLoad <= 55.0f) 2.5f else 4.0f
val fever = Math.min(0.05f * prostaglandinExcess, feverMax)

val thyroidDelta = clamp(2.0f * (iodine - 0.50f) / 0.50f, -0.8f, 0.8f)
val ambientOffset = 0.6f * (ambientTemperature - 37.0f)

val targetTemperature = clamp(
  37.0f + fever + pyrogen + thyroidDelta + anticholinergicFever + psilocinFever + ambientOffset,
  30.0f, 42.0f
)

// 体温趋向目标的动态松弛积分（k_T = 0.0004 / tick，约 2500 ticks 走完 63% 差距）
temperature += (targetTemperature - temperature) * 0.0004f
```

* **发热走前列腺素（PGE2）**，这正是下丘脑真正用来升温的介质——所以水杨苷（COX 抑制剂）
  在这里天然就是退烧药，而细胞因子风暴会烧到 40 °C 以上（自检里实测 40.1）。
* **热原 `pyrogen`** 是给测试用的外部发热源，像药物一样存储（正负都行，负值就是退烧偏移），
  **一个游戏日**代谢完。但它有个停顿：**体温没走到设定点之前不代谢**（差 0.2 °C 以内才开始清）。
  原因是设定点就是热原本身——如果一打进去就开始掉，设定点会在体温还在往上爬的时候
  先溜走，发烧的峰值永远够不到你要的温度（要 39.5 只能烧到 38.8）。
  停顿之后峰值就是你要的那个数，整场发烧也会在二十分钟内结束，不会留下一个
  "一小时前开的命令，现在屏幕还在晃"的 bug。
* **甲状腺**：碘不足 -> 设定点下移（怕冷），碘过量 -> 设定点上移（怕热）。幅度不到 1 °C，
  它改的是"平时的体温"，不是发烧。
* **环境**：见下表。温带和沙漠都在**中性温度区**里（体温调节完全扛得住），
  真正能打穿防御的是湿透、细雪、火和岩浆。

| 环境 | 权重（°C） |
| --- | --- |
| 生物群系（每 1.0 群系温度） | ×2.0，相对温带的 0.8 |
| 中性温度区 | **±2.0 以内直接忽略** |
| 浸水 / 淋雨 | −2.5 |
| 埋在细雪里 | −4.0（与上面叠加） |
| 着火 | +4.0 |
| 岩浆 | +6.0 |

换算下来：雪原（群系温度 −0.5）净 −0.6，只是**把平时的体温压到 36.4**，
一个人待在雪地里不会有症状；雪地里泡水 −3.1，体温落到 35.1（轻度失温）；
再加上细雪 −7.1，体温落到 32.8（重度失温）。热的一侧反过来：
沙漠 +0.4，几乎无感；岩浆 +4.0，足以让健康人烧到 41 °C。

### 盐的摄入

| 来源 | 钠 | 氯 | 镁 | 钙 |
| --- | --- | --- | --- | --- |
| 粗盐制品（粗盐水、粗盐蘑菇煲…） | **+3.0** | **+3.0** | **+0.04** | **+0.07** |
| 精盐制品（盐水、盐蘑菇煲…） | **+3.5** | **+3.5** | 0 | 0 |
| 海水瓶（含带盐版里的海水本身） | **+3.0** | **+3.0** | **+0.05** | **+0.05** |
| 农夫乐事腌肉（培根、火腿…） | **+0.8** | **+0.8** | 0 | 0 |
| 以腌肉为主的一盘（培根三明治、培根煎蛋） | **+1.2** | **+1.2** | 0 | 0 |
| 蜜汁火腿（整只） | **+2.0** | **+2.0** | 0 | 0 |

单位都是 mmol/L。钠的参考范围是 135–145，所以**一份 140 → 143～143.5（还在范围里），
两份 → 146～147（越界）**——"喝两杯盐水就高钠"这句话在这个刻度上才成立。
粗盐带着岩盐里的其他矿物（补一点镁和钙），精盐几乎是纯氯化钠，所以更容易把钠推高。

---

## 症状

### 感染

有症状（载量 ≥ 8）时：

| 症状 | 实现 |
| --- | --- |
| 挖掘速度略微降低 | `BLOCK_BREAK_SPEED` 属性修饰符，按严重度最多 **-6%** |
| 饱食度下降略微加快 | `Player.causeFoodExhaustion` 参数倍率 `1 + 0.5×严重度` |
| 视角震颤 | 每 **15 秒**掷一次，**20% 概率**，持续 16 tick |
| **重度感染（载量 ≥ 60）直接扣血** | **魔法伤害**：每两秒 `1 + (载量 - 60) / 40` 点 |

**重度感染是唯一一个不需要怪物参与就能杀死你的症状**：载量 60 时每两秒扣 1 点（半颗心
每 10 秒），载量 100 时每两秒扣 2 点（一颗心每 10 秒）。伤害类型是魔法，所以护甲挡不住——
这是病原体本身在伤害宿主，不是挨了一拳。搭配上面的第三条路（免疫抑制时凭空感染），
**"把炎症压太低"是一个能自己把自己杀死的状态**。

### 水量

| 状态 | 表现 |
| --- | --- |
| 水量 > 100 | 虚弱、挖掘 **-8%**、额外掉饱食度 |
| 水量 > 150 | 再加**反胃**（稀释性低钠的典型表现），**从这一刻起才可能抖镜头** |
| 水量 < 15 | 饥饿 |

> 口渴条只有 10 格，101 和 149 都画成"满"。所以镜头抖动——唯一一个没有图标、
> 玩家无从得知来源的效果——要等到 150 以后（那时已经有反胃图标了）才可能出现。

### 矿物质紊乱（按现实，两侧都算）

每一项用的是它自己的**参考范围**（见上面的表），只有症状各不相同。这张表就是
`AlimentSymptoms.MINERALS` 里的那张表：

| 矿物质 | 重度不足 | 轻度不足 | 轻度过量 | 重度过量 |
| --- | --- | --- | --- | --- |
| 钠 | 反胃 + 缓慢（低钠：意识模糊） | 虚弱 | 饥饿（剧渴） | 饥饿 + 虚弱 |
| 钾 | 虚弱 II + 挖掘疲劳 | 虚弱（挖掘 −6%） | 虚弱 | 缓慢 II + 周期性魔法伤害（心律不齐） |
| 镁 | 虚弱 + 缓慢（抽搐、震颤） | 虚弱（抖动概率 +15%） | 缓慢 | 缓慢 + 虚弱（嗜睡、肌无力） |
| 氯 | 反胃（代谢性碱中毒） | 虚弱 | 饥饿（代谢性酸中毒） | 饥饿 + 反胃 |
| 钙 | 缓慢 + 虚弱（手足抽搐） | 虚弱（抖动概率 +20%、幅度加大） | 缓慢 | 缓慢 II + 虚弱（乏力、嗜睡） |
| 碘 | 缓慢 II + 虚弱 II + 挖掘疲劳（严重甲减、黏液性水肿） | 虚弱 + 缓慢 + 饥饿（甲减：乏力、怕冷、反应迟钝） | 饥饿 + 反胃（甲亢：食欲亢进但不长肉、恶心） | 再加虚弱（肌肉消耗） |

> 现实中的缺碘还会导致**甲状腺肿（大脖子病）**——方块游戏里没法让玩家脖子肿大，
> 所以用「缓慢 + 虚弱 + 挖掘疲劳」这组甲减表现来代表，物质代谢变慢、反应迟钝。

### 体温

| 状态 | 表现 |
| --- | --- |
| **发烧**（38.5 – 40.0） | **虚弱 + 挖掘疲劳**（等级随温度升高），画面边缘出现热的扭曲与泛红（`aliment:heat_haze`） |
| **超高热**（≥ 40.0） | 上面全部保留，**再加动态模糊滤镜**（`aliment:heat_blur`） |
| **失温**（≤ 36，≤ 35 加强） | **虚弱 + 挖掘疲劳 + 缓慢**，画面边缘出现冷的抖动与泛蓝（`aliment:cold_shiver`） |
| 任一档 | 抖动概率每档 +15%，幅度 +0.4；食物消耗额外 +25% |

**镜头抖动只有在玩家能看到别的问题时才可能出现。** 这是修掉那个
"体征正常、视角还在晃"的 bug 后立下的规则：抖动的每一个来源都必须同时给一个
**效果图标**（虚弱 / 反胃 / 饥饿……），否则玩家没有线索知道自己在晃什么。
38 度以下、以及只到"口渴条满"程度的过水，都不再满足这个条件。

画面效果不是 HUD 贴图，而是**原版后处理链**（`assets/aliment/post_effect/*.json` +
`assets/aliment/shaders/post/*.fsh`）：只在画面**边缘**（`smoothstep(0.45, 1.0, ...)`）
做 UV 位移和偏色，正中央——准星和玩家真正在看的东西——保持完全清晰。
服务端只通过 `ServerPlayer.addPostEffect` 发一个 id，客户端自己去加载，
加载失败只会在日志里报一行然后跳过，不会影响玩法。

### 血糖症状

| 血糖 | 效果 |
| --- | --- |
| **< 2.0 mmol/L** | 挖掘疲劳 |
| **< 1.7 mmol/L** | 挖掘疲劳 + 虚弱 |
| **< 1.3 mmol/L** | 以上全部，再加每两秒一次的魔法伤害：`(1.3 - glucose) * 1.0` |

血糖是持续被消耗的，只有进食能补回来，所以一个不吃东西的玩家迟早会走到这几档，
无论身体其他部分有多健康。伤害是魔法伤害，护甲帮不上忙——大脑除了葡萄糖没有别的燃料。

---

## 血糖与胰岛素

血糖是模型里第二个——仅次于水量——**被消耗掉、而不是被拉回某个设定点的**量。这里没有糖异生：
身体每 tick 都在烧血糖，只有吃东西能把它放回去，所以 `glucose` 是玩家自己管理的一项资源，
而不是模型会去防守的一个值。

### 参考值

| 项 | 值 | 单位 |
| --- | --- | --- |
| 正常 / 空腹 | **5.0** | mmol/L |
| 参考范围 | **4.0 – 5.5** | mmol/L |
| 空腹下限（2 游戏日） | **3.5** | mmol/L |
| 偏高（胰岛素陡升） | **> 8.0** | mmol/L |
| 模型上下限 | 0.0 – 30.0 | mmol/L |
| 基准胰岛素指数 | **1.0** | 指数 |

### 食物值多少

| 食物类别 | 血糖 | 例子 |
| --- | --- | --- |
| 淀粉、糖与面包 | **+0.7** | `minecraft:bread`、甜饮、一碗米饭 |
| 混合菜 | **+0.6** | 拼装出来的一盘：炖菜、三明治、意面 |
| 熟肉与熟鱼 | **+0.5** | 熟牛肉、熟猪排、熟鸡、熟羊、熟兔、熟鳕鱼、熟鲑鱼 |
| 生肉与生鱼 | **+0.4** | 牛肉、猪排、鸡、羊、兔、鳕鱼、鲑鱼、热带鱼、腐肉 |
| 植物性食物 | **+0.4** | 水果、蔬菜、海带、海藻、蘑菇、每一种柳树皮汤 |

面包是唯一一种从正常的 5.0 出发、自己就能把血糖推出参考范围的食物（5.0 + 0.7 = 5.7），
这就是它被从其余植物性食物里单独拎出来的原因。所有能吃的、不是肉也不是面包的东西都算植物性食物，
所以玩家没法靠天天吃浆果来躲开进食的血糖代价。

**「混合菜」这一档是为拼装食物设的。** 一盘同时由淀粉、肉与蔬菜拼成的东西——炖菜、三明治、意面——
按 **+0.6** 计，介于植物性食物与面包之间，因为其中的肉与脂肪会拖慢淀粉。按面包计会让每一顿熟食都变成
血糖尖峰，按植物性食物计又会让它比里面的米饭还便宜。农夫乐事的三十六道菜就是使用这一档的食物；
本模组自己的单一原料不用这一档。

### 每 tick 的动力学

有三个项在推动血糖，它们之所以分开写，是因为行为各不相同：

```scala
// 1. 基础消耗：两个游戏日固定掉 1.5 mmol/L，也就是空腹时从 5.0 走到 3.5 的那条路。
//    过了 3.5 之后它按剩下的量缩放，所以下降是减速的，而不是一路直冲到零。
val basal =
  if (glucose > 3.5f) 1.5f / 48000f          // 3.125e-5 / tick
  else (1.5f / 48000f) * (glucose / 3.5f)

// 2. 身体自己的胰岛素。它在参考范围底部是关掉的：胰腺不会自己把身体推向低血糖。
val endogenousDrive = 0.0001f * Math.max(0f, insulin - 1.0f)
val endogenous =
  if (glucose <= 4.0f) 0f
  else Math.min(endogenousDrive, glucose - 4.0f)

// 3. 注射的门冬胰岛素，它没有这道刹车。
val injected = 0.00008f * insulinAspart

glucose = clamp(glucose - basal - endogenous - injected, 0f, 30f)
```

胰岛素指数本身按当前血糖要求的水平松弛过去，速率 0.0005 / tick（时间常数 2,000 tick）：

```scala
// 两段斜坡，都从正常的 5.0 起：一段平缓的横跨参考范围，真正消化掉一顿普通饭的正是它；
// 另一段陡一倍，从 8 以上开始。
val target =
  if (glucose <= 5.0f) 1.0f
  else if (glucose <= 8.0f) 1.0f + (glucose - 5.0f) * 2.0f
  else 7.0f + (glucose - 8.0f) * 4.0f

insulin = clamp(insulin + (target - insulin) * 0.0005f, 0f, 60f)
```

> **为什么要有那段平缓的斜坡。** 字面上的"血糖超过 8 才分泌胰岛素"没法在半个游戏日内把 9 或 10
> 拉回参考范围：血糖一越过 8 指数就关掉了，剩下的胰岛素在活干完之前就没了。横跨参考范围的那段
> 斜坡，才是"半天之内回落"这句承诺成立的原因；而 8 以上那段更陡的部分，正是
> "糖越高、胰岛素越多、回落越快"的意思。

### 实测行为

| 场景 | 结果 |
| --- | --- |
| 从 5.0 开始空腹 | 正好 2 个游戏日（48,000 ticks）后到 **3.49** |
| 从 3.5 开始空腹 | 接下来 2 天只花掉 **1.22**，而不是 1.5——下降在减速 |
| 一顿 8.0 的饭 | 半个游戏日内回到 **4.87** |
| 一顿 10.0 的饭 | 半个游戏日内回到 **4.77** |
| 一顿 20.0 的饭 | 半个游戏日内回到 **3.90** |
| 一顿 30.0 的饭 | 半个游戏日内回到 **3.85** |
| 2,400 tick 之后 | 20 比 9 掉得更多，曲线上的每一点都如此 |
| 打一针门冬胰岛素 | 5.0 -> **2.74**：只是下探，还没崩 |
| 打两针 | 低血糖崩溃，因为没有任何东西会把注射的胰岛素关掉 |
| 八个游戏日不进食 | **0.97** mmol/L：低血糖危机 |

### 门冬胰岛素 `insulinAspart`

| 项 | 值 |
| --- | --- |
| 来源 | `aliment:insulin_injection`（右键），一次 **+10.0** |
| 上限 | **60.0** |
| 代谢时间 | **1 个游戏日**（线性） |

```scala
insulinAspart = Math.max(insulinAspart - 60.0f / 24000f, 0f) // -2.5e-3 / tick
```

它是模组里唯一一种**故意把身体弄坏**的药。它刻意与身体自己的 `insulin` 是**两个分开的量**：
血糖下降时胰腺会关掉自己的分泌，而注射进去的门冬胰岛素不会被任何东西关掉。正常身体打一针还能扛，
冷却期内叠上两针就是危机，唯一的出路是吃东西。

### 和平模式会把它按住

在**和平**难度下，血糖完全不会移动。这句话比听起来更强：进入血糖的路径**恰好有四条**，而它们是一起被关掉的。

| 进入路径 | 方向 | 按住时 |
| --- | --- | --- |
| 基础空腹消耗 | 降 | 关 |
| 身体自身的胰岛素清除负荷 | 降 | 关 |
| 注射的门冬胰岛素 | 降 | 关 |
| 食物（`addGlucose`） | **升** | 关 |

```scala
// 整个开关就在这一个决定血糖是否移动的地方。没有任何东西是算出来再丢掉的，
// 所以也不存在"悄悄照跑不误"的运算。
val glucose =
  if (glucoseHeld) state.glucose else stepGlucose(state, insulin, drugs.insulinAspart)
```

`addGlucose` 收同一个标志，而且必须收：tick 只会把血糖**拿走**，所以只按在 tick 上的开关，
会留下进食作为唯一还能撬动它的东西。

它是**按住，而不是重置**——和创造模式做的是同一个选择。和平模式不是用来给自己补血糖的：
玩家进去时是多少，调回来时还是多少。胰岛素指标是**故意不按住**的：它是对血糖的*响应*，
而不是推动血糖的东西，所以它仍然会朝被按住的水平松弛过去；自检钉住了这一点——
被按住的一次 tick 与自由的一次 tick **只差一个字段**。

> **被按住的崩溃是难受，而不是致命，而这是原版的功劳。** 一个*已经*低血糖的玩家进入和平模式后仍然是低血糖，
> 并且仍然在承受随之而来的魔法伤害：`player.damageSources().magic()` 不带任何施加者实体，
> 而 `minecraft:magic` 声明的是 `"scaling": "when_caused_by_living_non_player"`，
> 所以 `Player.hurtServer` **不会**像抹掉怪物攻击那样在和平模式下把它归零。
> 真正救下玩家的是 `ServerPlayer.tickRegeneration`：和平模式下（且 `naturalRegeneration` 为默认开启），
> 只要生命值未满，它就**每秒回 1.0 点**。崩溃每两秒结算一次、每次 0.8 点——也就是 **0.4 点/秒**——
> 于是血条停在满值，永远不会掉下去。三个不是伤害的症状（挖掘疲劳、虚弱，以及 `/aliment status` 里那行诊断标签）
> 原样保留，而 `/aliment cure` 或把难度调回去都能结束它。若关掉 `naturalRegeneration`，
> 这笔账就会反过来，被按住的崩溃会真的掉血，因为它所依赖的那份回复没有了。

---

## 血糖仪

血糖仪是模组里唯一没法靠试出来发现的部分——没有试卡，血糖仪就没用；没有采血针，试卡就没用——
所以这三样总是一起出现在箱子里。

| 物品 | 战利品概率 |
| --- | --- |
| `aliment:insulin_injection` | **10%** |
| `aliment:glucose_meter` | **35%** |
| `aliment:glucose_test_strip` | **35%**，以 **5 – 9** 张一叠出现 |
| `aliment:microneedle` | **35%** |

这四样都会生成在每一种原版村庄箱子以及掠夺者前哨站的箱子里。

**诊断链：**

1. 手持**采血针**右键。手指会流血 **15 秒**（300 ticks），采血针消耗 1 点耐久。
2. 趁手指还在流血时手持**试卡**右键。它会变成**带血的血糖试卡**；血滴干后再用试卡会被拒绝，
   并在动作栏给出一条提示。这一滴血花在那张试卡上，所以第二张试卡需要再扎一次。
3. **主手**持**血糖仪**、**副手**持**带血的血糖试卡**，右键。读数会打印在聊天栏里——
   `Blood glucose: 5.1 mmol/L`，在 `zh_cn` 中本地化为 `当前血糖：5.1 mmol/L`——试卡随即被消耗。

系统特意从副手读试卡、从主手读血糖仪，所以只有一种摆法有效，也不存在"到底是哪件物品在起作用"的歧义。

### 试卡是样本，不是传感器

一张带血的试卡报告的读数，是**取血那一刻玩家的血糖**，以 mmol/L 的形式存在试卡自己的数据组件上。
它之后不会再跟随身体变化。

这正是取样的意义。一个会跟着玩家血糖走的读数，说明不了它被取下的那一刻，也会让
"扎手指、吃饭、再比较"变成不可能——更没法把一张试卡带给另一个玩家而它仍然代表原来的意思。

因为这个值挂在物品堆上，所以它经得起被放进箱子、丢在地上或转交他人，和一瓶酒上的浓度完全一样。
完全没有样本的试卡——在这个数据存在之前留下来的，或者由指令生成的——会退回到身体当前的血糖，
这也是所有试卡从前的行为。

血糖仪在消耗试卡**之前**读样本：清空一叠会把它的组件一起带走，所以事后再读就什么都找不到了。

---

## 用到的 mixin

一共 **5 个服务端 + 3 个客户端**，都写在 **Java** 里（Kotlin 混入需要 refmap，Java 有 Loom 的注解处理器，
而且能靠 Java 编译器直接校验目标），全部极短，只做转发：

| Mixin | 目标 | 为什么必须用 mixin |
| --- | --- | --- |
| `ItemMixin` | `Item.finishUsingItem` | 原版**没有**"吃完/喝完"事件，等到 server tick 时物品栏已经变了 |
| `PlayerMixin` | `Player.causeFoodExhaustion` | 缩放参数才能覆盖**所有**消耗来源（走路、疾跑、挖掘、跳跃） |
| `GrindstoneInputSlotMixin` | `GrindstoneMenu$2` / `GrindstoneMenu$3` 的 `mayPlace` | 原版砂轮只收"可损坏或带附魔"的物品，我们的物品根本放不进去 |
| `GrindstoneMenuMixin` | `GrindstoneMenu.computeResult` | 原版不知道我们的转化表，放进去也磨不出东西 |
| `CameraMixin`（客户端） | `Camera.alignWithEntity` | 原版没有镜头抖动，这个版本的 Fabric 也移除了 `ViewportEvent` |
| `FogRendererMixin`（客户端） | `FogRenderer.setupFog` | "只能看清 8 格"是渲染决定，服务端只说"你瞎了"（`client_state.blurred`），雾得客户端自己收 |
| `HudMixin`（客户端） | `Hud.extractPlayerHealth` | 原版没有口渴条，挂在生命值渲染之后就能紧贴血条上方 |

> 砂轮的两个输入槽是**匿名内部类**（`GrindstoneMenu$2` / `GrindstoneMenu$3`），
> 只能用字符串 `@Mixin(targets = ...)` 指定，没法用编译期引用。
>
> 挖掘速度、感染判定、抖动计时、口渴条取值、体温画面效果**都不需要** mixin：
> 分别用原版属性、事件、属性同步（`aliment:client_state`）和原版后处理链实现。

---

## 脏水（沼泽）与海水

在沼泽和海里用水瓶取水会得到**沼泽水瓶**和**海水瓶**（河流及其他仍然是原版水瓶）。
两者完全不同：

| 水 | 喝下去会怎样 |
| --- | --- |
| **沼泽水瓶** + 加盐版（粗盐 / 盐） | 每次**独立掷骰**，全部持续 **30 秒**：细菌感染 30%（+6 载量）、反胃 35%、中毒 5% |
| **海水瓶** + 加盐版 | **没有任何即时效果** |

**海水不是脏水，是"高渗水"**：喝下它本身不会让你感染或反胃，代价在于它带的钠
（一瓶 **+3.0 钠 +3.0 氯**，外加海水本身的 **+0.05 镁 +0.05 钙**）。一份还留在参考范围里
（140 → 143），两份就越过上限（→ 146）；之后的事全部由电解质系统接管——剧渴、加速失水，
再往下才是虚弱。一口海水不会立刻惩罚你，它只是把账记到后面。

所有带盐饮品（粗盐水/盐水/粗盐蘑菇煲… 和沼泽水/海水系列）喝下都**补水 +15 并补钠**，
粗盐版额外补镁和钙。

---

## 口渴条

客户端 HUD：**10 格**，画在**生命值正上方、左侧**（血条和护甲都在左边，口渴条排在最上面那一行；
如果玩家有护甲，就放在护甲上面，绝不会盖住任何一个）。

* 每 10 点水量一格：30 → 3 格，100 → 10 格
* 超过 100 一律 10 格
* 最后 10 点里过半时显示**半格**

水滴造型刻意做**细**：最宽处只有 5 像素（9×9 画布里），水滴外全部是**完全透明**
（alpha 严格 0／255，没有半透明像素），所以 10 格排开也不会糊成一片。

贴图在 `assets/aliment/textures/gui/sprites/hud/thirst_{empty,half,full}.png`，
走原版 GUI 图集（`assets/minecraft/atlases/gui.json` 的 `directory` 源会扫描所有命名空间，
所以模组命名空间不需要额外的图集配置）。

---

## 盐业链

```
岩盐矿 ──砂轮──> 粗盐 ×9 ──砂轮──> 粗盐粉
                                      │
                                      ├─ 右键水炼药锅 ──> 粗盐水炼药锅
                                      │        │
                                      │   下方篝火/灵魂篝火
                                      │        │  30 秒后每 30 秒浓缩一级
                                      │        ▼
                                      │   完全烧干 ──> 掉落 盐粉 ×1（炼药锅变空）
                                      │
粗盐 + 水/蘑菇煲/柳树皮汤/生柳树皮汤 ──> 粗盐水 / 粗盐蘑菇煲 / 粗盐柳树皮汤 / 粗盐生柳树皮汤
盐粉 + 水/蘑菇煲/柳树皮汤/生柳树皮汤 ──> 盐水   / 盐蘑菇煲   / 盐柳树皮汤   / 盐生柳树皮汤
```

* **搅拌棒**：手持右键粗盐水炼药锅，**立刻推进一级蒸发**，消耗 1 点耐久（共 16 点）。
  合成方式：**上下各一根木棍**。
* 粗盐水炼药锅有 3 个阶段（`stage` 0/1/2），对应 3 张贴图（越来越咸、越来越干）。
* 火被移走时蒸发**暂停**，重新点上会继续。
* 岩盐矿是**掉落自身**的（因为砂轮磨的是矿石方块本身），需要石镐，
  在 y=20–90 之间以 6 次/区块的频率生成。

### 砂轮

砂轮的**输入槽被 mixin 拓宽**了，所以粗盐、岩盐矿、柳树皮可以直接放进原版砂轮界面：

| 放入 | 产出 |
| --- | --- |
| 柳树树皮 ×1 | 柳树皮碎片 ×2 |
| 岩盐矿 ×1 | 粗盐 ×9 |
| 粗盐 ×1 | 粗盐粉 ×1 |

每次**只收一个**（结果槽取走时会清空输入槽，放整堆会被吞掉，所以槽位直接拒绝堆叠）。

想批量处理就用**潜行 + 右键**砂轮：不开界面、直接转化一个，可以连点。
（普通右键仍然打开原版界面——这也是"物品放不进砂轮"那个问题的修复点。）

---

## 命令

```
/aliment status                              查看全部数据（介质、炎症、水量、电解质、体温、病原、药物）
/aliment fever [温度]                        测试用：诱发发烧（或低温），默认 39.5 °C，范围 31–42（需要 OP）
/aliment cure                                重置为健康并清掉画面效果（需要 OP）
/aliment set <field> <value>                 调数值（需要 OP）
       field = water | sodium | potassium | magnesium | chloride | calcium | iodine
             | histamine | prostaglandin | leukotriene | cytokine | bradykinin
             | bacteria | virus | salicin | dexamethasone
             | temperature | pyrogen
```

`status` 会按**真实单位**打印，并标出参考范围：

```
  electrolytes (mmol/L)  Na 140.0  K 4.2  Mg 0.85  Cl 101.0  Ca 2.35
  trace elements (umol/L)  I 0.42
```

`set` 的每一个矿物字段都按它自己的范围夹取，回显打印的是**真正落进去的值**——
比如 `set iodine 0.6` 会回显 `iodine = 0.60`，而 `set sodium 500` 回显 `sodium = 190.0`。

**`/aliment fever [温度]`** 是我专门为测试发烧加的：它**不直接改体温**，而是算出"要让这次
发烧**峰值**落在目标温度上需要多少热原"，然后把热原打进去。这样做的好处是

* 已经感染的人也能精确落到目标温度（热原是补差价，不是覆盖）；
* 体温像真发烧一样**两分多钟慢慢走上去**，所以能顺便看体温上升的过程；
* 峰值误差在 **0.03 °C** 以内（自检里 39.5 → 实测 39.53，40.5 → 40.53）；
* 热原在发烧形成后**一个游戏日内清完**，整场发烧十五分钟左右结束——
  不会留下"一小时前开的命令，现在屏幕还在晃"。

传一个低于 37 的温度就是低温症，比如 `/aliment fever 34`。
命令回显会告诉你这次会触发哪一层画面效果，以及怎么立刻停掉。

```mcfunction
/aliment fever          # 39.5 °C，发烧：画面边缘泛红 + 扭曲
/aliment fever 38.5     # 刚好进入发烧档
/aliment fever 41       # 超高热：上面全部 + 动态模糊
/aliment fever 34       # 低温症：画面边缘冷抖动 + 缓慢
/aliment cure           # 一切归零，画面效果同 tick 消失
```

---

## 验证

`src/main/kotlin/.../dev/AlimentPhysiologySelfTest.kt`（**默认不启用**，把它加进
`fabric.mod.json` 的 `main` 入口点再 `gradle runServer` 就会在开服后 40 tick 自动跑完）
跑出 **686/686 全过**：

```
homeostasis 3 days (one day's kelp a day): inflammation 25.0..25.0
  electrolytes Na 140.0 K 4.2 Mg 0.85 Cl 101.0 Ca 2.35   temperature 37.0
mild infection: peak inflammation 28.1 -> cleared
untreated 40 point infection: load 99.93, peak inflammation 90.7  (storm)
salicin treated:     load 0.022, peak inflammation 25.0
dexamethasone:       load 0.026, peak inflammation 25.0
overdose:            load 58.5,  inflammation 6.2   (immunosuppressed)
drug metabolism: salicin 3 days -> 0 (linear), dexamethasone 2 days -> 0
competence: 0 -> 0.0  baseline -> 1.0  storm -> 0.004
resting mediators are the model's fixed point (25.0 == 25.0)
thirst after one game day: water 10.47 -> 1 cell
over-hydration: water loss 0.0060/tick vs 0.0037 normal
two days of heavy drinking (mmol/L): Na 117.1  K 3.72  Mg 0.79  Cl 84.5  Ca 2.20
iodine: after 1.5 days 0.275, after 3 days 0.05 (the floor); one kelp from empty 0.15
  four days of a day's kelp: one a day 0.299 (deficient), two a day 0.699 (in range)
  three helpings from normal: 0.95 (excess), and six days later back to the floor
  half a day of fever drains it faster: at 37 0.425, at 41 0.391
thyroid: iodine 0.36 -> 36.79 resting / iodine 0.79 -> 37.17
a healthy player holds exactly 37.0 for a whole game day
ambient: temperate 37.0 | snowy 36.4 | snowy+wet 33.9 | powder snow 30.0 | desert 37.4 | lava 41.0
  -> 12000 ticks in powder snow: 32.83 (tier -2), and back to 36.97 ten minutes after leaving
untreated infection fever: peak 40.13 ; with salicin on board: 37.0
/aliment fever 39.5 -> peak 39.53, 18439 ticks (15 min) in the fever band
/aliment fever 40.5 -> peak 40.53 ; /aliment fever 34 -> lowest 33.97
shake chance: healthy 0.00 | 38.0 C 0.00 | 38.5 C 0.15 | 40.0 C 0.30 | water 149 0.00 | water 151 0.10
hottest a healthy body reaches (desert + hot thyroid): 37.40, tier 0, nothing on screen
screen effects: 38.0 none | 39.0 haze | 40.5 haze + blur | dropping to 39.0 clears only the blur
translations: [en_us, zh_cn] languages, missing item names [] missing entity names []
  -> every Aliment item and entity is named, and the standing sign takes block.aliment.willow_sign
every mineral probed on both sides of both of its thresholds (36 cases) + the 14 named rows
sepsis damage per two-second pass: load 60 -> 1.0  load 100 -> 2.0  (59 -> 0)
contact dice: 20/400 came up (5%) ; the roll arms a one second cooldown and nothing nearby is safe
immunosuppression: 47/400 seeded bacteria (15%), 47 distinct loads, all inside +4..+12
one serving of salt water: Na 143.5 (inside 135..145) ; two servings: 147.0 (past it)
one bottle of sea water: water 95, sodium 143.0, magnesium 0.90
raw meat 107/400 | rotten flesh 113/400 | poisonous potato ~30% | bread 0/400 (control)
swamp water over 600 drinks: infection 179 nausea 197 poison 32
sea water over 600 drinks:   infection 0   nausea 0   poison 0
all 13 drinks add exactly 15 water
a real GrindstoneMenu slot accepts willow bark and produces willow_bark_pieces x2
exhaustion multiplier: healthy 1.0 vs ill 1.458
mining speed: ill 0.945 | over-hydrated < 1.0 | healthy 1.0
client state: synced water 55
chest loot: a chest holds aliment:dexamethasone_injection 254/8000 = 3.2%
            and aliment:willow_bark_soup_bowl 2854/8000 = 35.7%
creative: a severe infection does not advance, damage or symptomise the body, and the shimmer
          comes off the screen; back in survival the same body carries on from where it stopped
death:    the respawned body is bacteria 0.0 virus 0.0 temperature 37.0 water 80.0
mandrake: one fruit 1.0/0.1; two 2.2 (38 C); three 3.1 (39.5 C, sight blurred); a full dose 41 C
          a game day clears both alkaloids; the drug fever and an infection fever add up to 42 C
          blurred and feverish at once: [anticholinergic_blur, heat_haze, heat_blur]
gymnopilus: one raw mushroom 1.3/1.3; half a day later psilocybin 0.65 psilocin 1.63
          a day finishes the conversion with 1.3 psilocin left; five doses take ten game days
          the trip: 1.5 -> psilocin_outline 2.0 -> psilocin_colour 3.0 -> psilocin_warp 6.0 -> storm
          a raw mushroom is 3 hunger / 4 saturation, cooked 4 / 5 and neither compound
naringin: cap 10; cyp3a4 85 at 2 | 60 past 2 | 45 past 4 | 25 past 7 | 10 at 8.5
          a full body of naringin clears in one game day; the enzyme is back to 85 once it has
          coptis (1.1 berberine) cleared in 9429 ticks alone, 17827 with a body full of grapefruit
grapefruit slice: 2 hunger / 3 saturation; +5 water +1 naringin +10 vitamin C; always edible
          eleven slices still leave the body at the naringin cap, and the next tick puts the enzyme at 10
          milk adds 15 water
glucose: fasting 5.0 -> 3.49 in two game days; 0.97 after eight
          a meal of 8 / 10 / 20 / 30 is home within half a day, and a 20 falls further than a 9
          insulin 1.0 fasting; one injection 5.0 -> 2.74, two are a hypoglycaemic crash
          bread 0.7 | cooked meat 0.5 | raw meat 0.4 | plant food 0.4 per item
          the meter prints 6.4 from the strip and never the body's 9.9; a dry finger refuses a strip
          a bloodied strip keeps its reading after the player's glucose changes
PHYSIOLOGY SELFTEST DONE passed=686 failed=0
```

覆盖了**纯模型**、**mixin 端到端**（真的调 `ItemStack.finishUsingItem` 吃生肉 / 喝汤 / 喝海水，
以及**真的构造一个 `GrindstoneMenu`** 验证两个砂轮 mixin 生效）、**症状表**、**三条感染路径**、
**箱子战利品**、**曼陀罗生物碱**（含同步给客户端的模糊标志）、**创造模式冻结与死亡重置**、
**香柠檬素与 CYP3A4**（含"吃过葡萄柚的黄连"端到端对照）、**血糖与胰岛素**（含低血糖三档的
效果与伤害）、**血糖诊断链**（采血针 / 试卡 / 带血试卡 / 血糖仪，真的走物品交互）、
**葡萄柚与奶的进食路径**、**翻译覆盖**与**同步**。

> 箱子战利品那两条概率是**掷出来的**，不是把常数读回来断言：`LootPool` 建好之后什么也读不到
> （只有一个 `addRandomItems` 和一个 `CODEC`），所以自检用 `LootParams` 把模组真正加进去的
> 那个池子掷 4000 次、数命中次数。这样验的是"玩家实际摸到的东西"，而不是写在旁边的常数。

> 矿物那一组自检是**照着规格反推探针值**的：每一项都在它两个阈值的两侧各探一次、在参考范围
> 的两端内侧各探一次，所以它断言的是规则（"范围内安静、范围外出症状"），换成别的单位或别的
> 参考值也不用重写。挑出来的 14 行（低钠反胃、高钾心律不齐、缺碘甲减……）再按效果单独断言。

> 自检抓到过的问题：
> 1. `AttachmentRegistry.create(id)` 建的 attachment 没有默认值，单参 `getAttachedOrCreate`
>    会抛 `IllegalArgumentException` —— 那会在**每个玩家每一 tick**炸一次。
> 2. 盐类合成表用了 `"category": "food"`，26.2 的 shapeless 配方不接受这个值，8 个配方
>    全部加载失败。已改为 `"misc"`。
> 3. 静息介质水平与模型不动点不一致（28.75 vs 25），新玩家一出生就接近免疫抑制。
> 4. 过量用药不足以把炎症压进免疫抑制区，感染反而被清掉了；`OVERDOSE_BASELINE_DROP`
>    从 1.2 调到 1.8。
> 5. 口渴条三个水滴贴图的**背景被填成了不透明色**（81/81 像素 alpha=255），
>    所以在 HUD 上显示成三个实心方块。原因是造型表里 `.` 背景字符没有被单独处理，
>    掉进了"填内部颜色"的分支。现在 `.` 会保留 `New-Grid` 的透明值，
>    水滴外严格 alpha=0。
> 6. 碘加入后，`homeostasis` 那条自检拿 `electrolytes.lowest` 判定"所有电解质都正常"，
>    而碘按设计本来就会掉光，于是误报。已改成只检查真正的五项电解质。
> 7. 体温这一版：出汗原本挂在**本 tick 刚算出来的**新体温上，而其余步骤读的都是上一 tick
>    的状态。改成出汗也读旧体温，否则"持续发烧"根本没法在纯模型里复现。
> 8. 热原原本按 2 个游戏日代谢，而体温要 2500 tick 才走到设定点——结果是
>    `/aliment fever 39.5` 只能烧到 38.8。先改成 5 个游戏日（准了，但一场测试发烧要挂一小时），
>    最后改成"**走到设定点之前不代谢**"：峰值精确到 0.03 °C，二十分钟内结束。
> 9. 碘的平衡点原本定在 90，一份海带正好推到 115.3，**刚好过量**（吃一根海带就反胃）。
>    把固定流失从 0.0004 提到 0.0006，平衡点落到 88，一份海带 113。
>    （后来换成 mmol/L 时这两个数又按比例重算了一次，见第 12 条。）
> 10. **"体征正常但视角还在晃"**：发烧档原本从 38.0 开始，而 38.0 是不开药方也能到的温度
>    （炎热群系 + 甲状腺偏热），屏幕上却已经泛红扭曲加抖动了；另外过水到 100–149 之间
>    口渴条全画成"满"，却能单独触发镜头抖动。现在发烧档从 38.5 起，**抖动的每一个来源
>    都必须同时给一个效果图标**（自检里逐条断言了 shake chance）。
> 11. **`item.aliment.willow_sign` 没有翻译**：立式告示牌注册时漏了
>    `useBlockDescriptionPrefix()`，而它的悬挂版有，于是物品栏里直接显示原始键名
>    （名字在 `block.aliment.willow_sign` 下，原版告示牌也一样）。自检现在会枚举
>    **所有**注册项、拿两种语言文件核对，缺一个就报出具体是哪个键——先故意把修复撤掉跑了一遍，
>    它确实红：`missing item names [item.aliment.willow_sign (en_us), item.aliment.willow_sign (zh_cn)]`。
> 12. **换成 mmol/L 之后"猛喝水"把钠算到了 28**：旧的流失量是**绝对数值**，在 0–200 的刻度上
>    占正常的 80%，换成真实单位就成了"血清钠 28 mmol/L"——人已经没了。现在流失一律表示成
>    **正常值的比例**（出汗每度 2e-6、膀胱撑满 9e-6），最极端的情况落在 117 mmol/L，
>    是重但真实存在的低钠血症。顺带把碘/甲状腺的位移也改成比例，否则 0.5 这个数量级下
>    0.02 的系数等于没有。
> 13. **`FakePlayer` 打不掉血**：Fabric 的 `FakePlayer` 覆写了 `isInvulnerableTo` 让它永远返回
>    true，所以"重度感染扣血"没法靠观察血量来测。把伤害量拆成一个纯函数
>    （`sepsisDamage(data)`）单独断言，落伤害那一行是一句 `hurtServer`，和矿物表共用。
> 14. **无头服务器没有实体 tick 区块**：想放一头牛在玩家旁边测"接触传染"，结果
>    `getEntitiesOfClass` 永远返回空。于是把两颗骰子拆成 `contactRoll(random)` /
>    `opportunisticRoll(random)` 单独测概率，"旁边有没有东西"那一关由反例和冷却断言覆盖。
> 15. **碘又改回"会掉光"了**（第 6、9 条的旧设计是掉光，中间一段改成平衡点 85%）：
>    这次是**指定时间**——三个游戏日从正常值排到硬下限，所以比例调节整个删掉，
>    只留一根直线的漏水。"完全消耗时间"这种东西和"拉回正常值"在数学上不能共存，
>    这是这次唯一必须在两者里选一个的地方。顺带把海带的补碘量各降 0.05
>    （0.15→0.10、0.25→0.20），一天一份不够、两份刚好，干海带顶两份湿的。
>    自检里那 9 条围着"平衡点 0.425"写的断言全部重写成围着"三天排空"写，
>    并且新加了"从空储备吃一份海带能顶多少"的算术断言——这条比旧的"稳态在范围内"
>    更能说明设计，因为现在根本没有稳态。
> 16. **"去掉 `copyOnDeath()` 就够了吗"**：不够，如果自检是按字面去调
>    `PlayerList.respawn(player, /*alive=*/ true, …)` 的话。Fabric 只在 `alive == false`
>    （真的死了一次，旧实例被丢弃）时才按 `copyOnDeath` 过滤，`alive == true` 是
>    "换维度/从末地回来"，附件会**整份照抄**。所以自检必须先 `kill(level)` 再
>    `respawn(player, false, KILLED)`，断言的才是真正的死亡路径；这个参数名在 26.3 里就叫
>    `alive`，不是 `keepInventory`，一开始正是按旧名字理解才测出"删了也没用"的假象。

---

## 调参入口

**所有数值和演算都在 Scala 里**：`src/main/scala/com/github/kusa233/aliment/physiology/model/`。
Kotlin 只保留 Minecraft 那一侧的东西（attachment、编解码器、效果、事件、指令），
`AlimentPhysiology` 与 `AlimentSymptoms` 里的同名函数只是转发，不再自带任何数字。
Kotlin **从不提到 Scala 的类型**：两者之间隔着 `AlimentModelBridge.java`
（见 `AGENTS.md` 的「Languages: where code goes」）。

模型里的名字全部带 `Model` 前缀或放在单独命名的 object 里，和 Kotlin 侧永远不重名，
所以那个 Java 文件可以 `import ...physiology.model.*` 然后全用短名：

| Scala 类型 | 常量放在 | 对应 Kotlin 侧 |
| --- | --- | --- |
| `ModelMineral` | `MineralRanges`（SODIUM…IODINE） | `Mineral` 枚举 |
| `ModelMediators` | `MediatorLevels`（权重、`MAX`、`CALM`、`RESTING`） | `Mediators` |
| `ModelElectrolytes` | `ElectrolyteDefaults`（`MINERALS`、`HEALTHY`） | `Electrolytes` |
| `ModelTraceElements` | `TraceElementDefaults`（`HEALTHY`） | `TraceElements` |
| `ModelDrugs` | `DrugDefaults`（`CLEAN`） | `AlimentData`（体内药物各字段与 `Compounds`） |
| `ModelEnzymes` | `EnzymeDefaults`（`NORMAL`） | `AlimentData.cyp3a4`（单字段，同药物一样平铺存储） |
| `ModelState` | `ModelConstants`（全部标量常数） | `AlimentData` |

| 文件 | 内容 |
| --- | --- |
| `.../physiology/model/Model.scala` | **矿物表**（单位 + 正常值 + 参考范围 + 重度线 + 上下限）、介质与电解质结构、`ModelConstants` 上的全部标量常数 |
| `.../physiology/model/Physiology.scala` | 全部演算：介质动力学、病原体增长与清除、免疫力曲线、排水排电解质稳态、碘稳态、体温与热原、重度感染伤害、抖动概率、环境温度 |
| `.../physiology/AlimentModelBridge.java` | **唯一的接缝**：Kotlin ⇄ Scala 的互相转换、全部转发、把模型的常数改名导出给 Kotlin。项目里唯一允许出现 Scala 类型的文件 |
| `physiology/AlimentData.kt` | 只做存储与序列化：Kotlin 自己的数据类、编解码器、阈值常数的转发、attachment |
| `physiology/Mineral.kt` | Kotlin 侧的矿物枚举，每个 case 的参考范围都从模型取（这样 `when` 又是穷尽的） |
| `physiology/AlimentSymptoms.kt` | 症状的应用：挂效果、扣血、画面效果、抖动掷骰 |
| `physiology/AlimentInfection.kt` | 三条感染路径的概率、单次载量、接触范围、掷骰间隔 |
| `physiology/AlimentIngestion.kt` | 每杯补水、盐分摄入（mmol/L，含海水的钠镁钙）、碘摄入（µmol/L，海带 0.10 / 干海带 0.20）、维生素C 摄入、每种食物给多少血糖与果片带的香柠檬素、每服汤的药量 |
| `world/AlimentLoot.kt` | 村庄（`chests/village/*`）与掠夺者前哨站箱子里模组加进去的物品，以及各自的概率（地塞米松注射液 3% / 柳树皮汤 35% / 门冬胰岛素 10% / 血糖仪、试卡、采血针各 35%） |
| `assets/aliment/post_effect/*.json` + `assets/aliment/shaders/post/*.fsh` | 边缘扭曲（`heat_haze`）、动态模糊（`heat_blur`）、冷抖动（`cold_shiver`），以及各自的强度、起始半径、频率、反馈系数 |

> **以后新的数值 / 稳态代码写在 Scala 里**，见 `AGENTS.md` 的「Languages: where code goes」。
