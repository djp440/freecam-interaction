# AE2 传输核心实施计划（Forge 1.7.10）

记录时间：2026-09-12 12:19（Asia/Shanghai）。状态：方向与完整范围已通过 intent-completion 两次确认；用户最新要求“先不实施，产出实施计划并 handoff 交接给下个 agent 实施”。本文件是下一会话的范围基线，本轮未实现功能、生成美术资源或运行游戏测试。

## 1. 授权、目标与版本

- 目标：绑定后的法杖提供自由视角下可操作的 AE2 多终端入口，并直接从指定 ME 网络为蓝图施工供料。
- 仅实现相邻工作树中的 Forge 1.7.10，源码根目录为 `src/forge1710`，Mod ID 为 `freecam_interaction`。不修改 `src/main` 的 1.21.1 功能。
- 固定联动对象：Applied Energistics 2 `rv3-beta-6`；覆盖原生 Forge 与现有 lwjgl3ify `3.0.33` 实例。不能将此表述为兼容 GTNH 整合包或 GTNH 的 AE2 分支。
- 本轮仅写计划、交接及项目记忆；下一会话受领实施任务后按此范围执行，不重复方向和范围确认。内部类名、协议字段等工程建议可调整；新增玩法、额外扩展 Mod 或改变已确认规则需增量确认。
- 原始请求包含核心材质以及传输器贴图、模型，不能只交付逻辑占位。传输器外形参照可放置的 ME 无线访问点，不是名为 Wireless Receiver 的合成组件。

## 2. 已确认功能契约

### 2.1 物品、设备、配方与美术

| 项目 | 确认行为 |
|---|---|
| AE2 传输核心 | 现有四槽升级系统中的一种核心；不可堆叠，同杖同类互斥，允许与蓝图核心、移速核心共存；32×32 透明像素 PNG，与已有核心视觉风格一致。 |
| 自由视角 AE2 传输器 | 新增可放置、连接 ME 线缆的设备；无线访问点风格的原创模型及贴图，以亮灭显示工作状态。 |
| 核心配方 | 各 1 个无线接收器、工程处理器、末影珍珠、钻石，无序合成 1 个核心。 |
| 传输器配方 | 各 1 个 ME 无线访问点、ME 合成终端、ME 样板终端、ME 接口终端，无序合成 1 个传输器。 |
| 频道与待机能耗 | 每个传输器占 1 个频道，遵守 AE2 当前频道配置；待机耗能参照无增幅卡无线访问点，默认 8 AE/t，并跟随该 AE2 配置值。 |
| 存取与法杖消耗 | 网络存取沿用 AE2 原生能耗，由对应 ME 网络供电；不为法杖新增电池。打开、切换终端不扣法杖耐久；施工沿用原有成功操作耐久规则。 |

资源补齐中文及英文名称、说明、缺失条件提示。AE2 的普通终端能力由传输器集成，不另要求玩家在网络上放置四个实体终端才能使用切换功能。

### 2.2 安装、生效与绑定

1. 玩家手持安装了 AE2 核心的法杖，潜行右键有效传输器，服务端验证后绑定；优先于现有潜行右键打开法杖升级界面的动作。其它目标继续使用原有升级入口。
2. 绑定要求玩家具有该网络的 `BUILD` 权限；绑定成功有反馈，法杖显示绑定信息。重复绑定同一目标不产生重复操作；新绑定成功后才替换旧绑定，失败保留旧信息。
3. 绑定保存在法杖上，重启仍保留。卸芯立即停用，但保留法杖绑定信息，重装核心可恢复；把散装核心移到另一把法杖不应把旧法杖的绑定一并转移。
4. 已绑定法杖位于玩家主背包含快捷栏 `0..35` 即可提供 AE2 能力，不要求一直手持，不扫描护甲、箱子等其它容器。剩余耐久 1 也可提供此能力；进入自由视角仍须满足现有可用法杖条件，不能借此恢复耗尽法杖的施工资格。
5. 蓝图核心可以位于另一把法杖上；保留现有“背包任意法杖携带蓝图核心”的施工资格规则。
6. 绑定识别具体传输器实例及位置，不能只保存坐标或临时 `IGrid` 对象。拆除重放需要重绑，原坐标出现另一台设备不得继承旧绑定。正常存档加载保持同一实例标识。
7. 网络随传输器当前接线解析；接线或网络身份变化时关闭旧会话、停止旧来源的施工操作并重新检查权限，不能继续访问缓存的旧网络。临时断线不删除法杖上的绑定记录。

### 2.3 多法杖、目标选择与通信

- 只有一个绑定目标时自动使用；存在不同目标时提供目标选择并显示当前目标，不按法杖品质、快捷栏顺序或最近可用网络偷偷切换。多个法杖绑定同一设备时可作为同一目标呈现，但服务端仍须确认至少存在合法携带来源。
- 记住已选目标，法杖耐久自动接续不能切换 AE2 网络。选定法杖被移走或卸芯时立即让对应会话失效，不自动改用另一个不同网络。
- 每个施工会话固定启动时选定的来源；修改终端选择不得影响正在运行的施工。来源失效则暂停，恢复时重新校验，不能在后台改取另一个网络。
- 同维度不限通信距离；不把相机位置、玩家到设备的距离或普通无线增幅卡范围作为额外限制。传输器和所依赖的网络必须已加载并正常工作。
- 不新增远程区块强加载、区块订阅或跨维度访问。不得通过获取 TileEntity 等调用隐式加载目标区块；已有法杖范围内保载逻辑保持原状。
- 不扩大施工交互距离：建造仍受操作者现有法杖范围、世界、区块及任务权限约束。

### 2.4 E 键、四类终端与临时物品

| 终端/入口 | 必须可操作的内容 |
|---|---|
| 普通 ME 终端 | 搜索、排序、网络库存浏览、物品存取及 Shift 搬运。 |
| ME 合成终端 | 网络库存及手动合成，正确处理实际消耗、配方容器物品与输出。 |
| ME 样板终端 | 合成与处理两种样板编码、编辑、空白/已编码样板槽操作；配方槽是虚拟材料，不能变成实物。 |
| ME 接口终端 | 本版本中对应“样板管理终端”；按网络内接口展示和存取其样板槽，遵守接口原有显示设置。 |
| 自动合成子流程 | 玩家手动请求合成、数量选择、确认、状态查看和原生返回/取消路径；要求现有 AE2 样板及 CPU 等前置条件。 |
| NEI | 接通开发实例中已有 NEI 的配方填充路径；不为本功能另装无线终端等扩展 Mod。 |
| 原版背包 | 终端中提供明确入口；未绑定时按 E 直接打开原版背包。 |

1. 使用游戏当前“打开背包”绑定键，默认 E，不能硬编码键码。只有自由视角有效且没有其它正在输入的界面时改路由；搜索框中的 E 不应触发新会话。
2. 默认进入合成终端，可显式切换上述终端；无对应权限的页显示不可用原因，不借切换绕过权限。
3. GUI 不暂停世界；GUI/失焦期间原有相机和交互输入暂停。关闭界面返回自由视角，恢复原有松键后再交互的防误触规则。
4. 每位操作者有独立临时合成格与样板编码格。切页保留本次会话内容；最终关闭、失效、登出等路径统一结算真实物品，优先返回操作者背包，装不下的余量安全落地。
5. 已写入 ME 接口的样板属于网络内实际库存，不是应在关闭时退还的临时物品。编码用虚拟输入/输出不能计入退款。鼠标持物由正确的容器关闭链处理，不能重复返还。
6. 临时断电、频道不足、网络断开、目标卸载或拆除、权限撤销、移走法杖、卸芯时停止网络操作，反馈原因并保留原版背包入口；不得出现空白死界面或关掉后仍能操作的旧容器。

### 2.5 施工材料及权限

- 每个施工步骤先消耗操作者背包，缺额从启动时选定的 ME 网络提取，直接用于施工，不要求先放入背包。无 AE2 来源时保留原背包施工能力。
- 适用于当前已支持的方块、AE2 部件及 GT 覆盖板需求；匹配沿用现有物品、metadata 与必要 NBT 语义，不能把机器名字相同视作材料相同。
- 普通缺料时暂停并提示，补齐后可继续。此版本不自动创建补料合成订单；玩家可以通过终端手动下单后继续施工。
- 材料、权限和耐久均属于实际操作者。协作者不自动借用任务主人的法杖、背包或绑定网络；共同使用同一网络也要按各自权限执行。
- 保留创造模式玩家免材料、创造法杖免耐久的区别；生存玩家持创造法杖不等于免费获得材料。
- 绑定检查 `BUILD`；存入检查 `INJECT`；取出和施工供料检查 `EXTRACT`；合成及样板页遵循 rv3 相应 `CRAFT` 门槛，接口样板管理遵循 `BUILD`。实际存取还须分别校验存取权限，不能只检查能否打开页面。
- 使用实际玩家身份访问 AE2。赠送法杖不会转移原绑定人的权限。未配置安全终端时遵守 AE2 本身的授权结果，不额外强制建造安全终端，也不自建与 AE2 冲突的权限白名单。

### 2.6 失败、并发与物料守恒

1. 以实际取出的 ItemStack、NBT、数量及来源建立本次步骤的结算记录，不能按需求样例重新生成退款物品。
2. 预检不能当作已经预留；AE2 模拟取料后，实际取料可能只有一部分。未取得完整步骤物料时不施工，已取得部分按记录原样退回。
3. 失败优先退回原来源；网络断开、无存入权限或网络容量不足时，剩余物品退给操作者背包，余量安全落地。不能在回退时绕过网络权限或静默丢弃 `injectItems` 返回的余量。
4. 重复请求、多人争同一份库存、并发建同一坐标、放置事件取消或抛错、耐久临界接续都不得免费放置、重复扣料/退款。只对实际成功施工结算对应材料与耐久。
5. 暂停保留任务进度；取消、结束会话和退出世界清理待处理操作、监听器、目标引用与坐标锁。恢复前复核携带资格、权限、来源和世界状态。

## 3. 已核对证据与不能当成事实的假设

### 3.1 本地基线

调查时目标工作树分支为 `codex/forge-1.7.10`，HEAD 为 `0b5e112d61676f14d4f07b5a491bc1bc9f2137a9`。入口工作树为 `../上帝视角建造`，其 HEAD 为 `9e65a9c9b91c9f42a92f5a90818fa88356d6f263`。实施时重新检查，不覆盖其后新增改动。

重要：本地 `src/forge1710/java/local/freecaminteraction/blueprint/build/BlueprintBuildScheduler.java` 与 `BlueprintBuildExecutor.java` 存在，但 `git ls-files` 未列出，`git check-ignore -v` 明确返回 `.gitignore:2:build/`。当前功能入口确实引用这些文件。接手必须使用现有工作树，不能假定仅检出 HEAD 就包含全部施工源码。实施前审查这一相关目录，采用精确忽略例外或等效最小修正使必要源码可跟踪，避免把构建产物一并纳入；本轮没有修改忽略规则或暂存文件。

本地 `run/mods/appliedenergistics2-rv3-beta-6.jar` 的 SHA-256 已验证为：

`0EC8CD1EDE7F7BBBF73030EBA8B06EBCC0583045FF4CC9AEC080B1736581DA71`

与 `scripts/tech-mods.ps1` 固定值一致。真实 JAR 的 `javap` 签名已核对三个终端容器、`ISecurityGrid`、`IMEInventory`；这只证明版本和接口存在，不代表自定义终端接入或运行兼容性已验证。

### 3.2 本地复用入口

所有路径相对于本计划所在工作树；行号为调查时定位，实施时以函数名复核。

| 文件/入口 | 已确认事实与实施影响 |
|---|---|
| `item/ItemFreecamWand.java`（以下简写位于 `src/forge1710/java/local/freecaminteraction/`） | `loadUpgrades/saveUpgrades/getUpgradeCore` 管理四槽；`onItemUseFirst`、`onItemUse`、`onItemRightClick` 中潜行均有升级入口，绑定要防止三个入口重复触发。`findBestWand` 排除耐久 <=1，不能复用作 AE2 携带资格扫描。 |
| `item/IWandCore.java`、`item/ItemBlueprintCore.java`、`inventory/ContainerWandUpgrade.java` | 复用核心 ID 判同类、槽位互斥、锁定编辑法杖、Shift 安全搬运与不可堆叠设计。 |
| `FreecamWandRegistry.java`、`FreecamInteractionMod.java` | 现有注册及初始化入口；当前主构建没有 AE2 编译依赖。新增可选集成需控制类加载和 AE2 初始化时序。 |
| `FreecamInteraction.java` | `hasBlueprintCoreWand`（约114行）扫描背包任意法杖；`getActiveWandSlot/deductUsage/succeedWand` 决定支付耐久与接续，不能拿它们自动决定网络。已有模式、登出、换维度及容器生命周期入口。 |
| `client/FreecamClient.java` | `keyboard` 开头在 `MC.currentScreen != null` 时返回；现有键事件处理不包含背包改路由，不能只在这里追加 E 判断便认为成功，须核验原版打开背包和 Forge GUI 事件顺序。 |
| `FreecamGuiHandler.java` | 已有分端 `IGuiHandler`，当前只有法杖升级 GUI ID。扩展时避免服务端加载客户端类，并明确自定义与 AE2 GUI 路由关系。 |
| `blueprint/network/BlueprintNetwork.java` | 任务动作实际调用 `BlueprintBuildScheduler.INSTANCE.startBuild`（约416行）。不要只修改备用 `BlueprintBuildExecutor`。 |
| `blueprint/build/BlueprintBuildScheduler.java` | 主路径 `checkAndDeductMaterials`（约682行）预检后直接扣背包；`refundMaterials`（约711行）用 `createSampleStack` 重建退款，会丢失非需求字段 NBT。方块路径先扣耐久再放置，部件路径先安装再扣耐久，均须随本次结算整合核对失败顺序。 |
| `blueprint/build/BlueprintBuildExecutor.java` | `MaterialReservation` 只记录槽位和数量，提交时没有完整物品身份复核；备用路径放置后提交，不能把它当成已验证的跨来源事务直接照搬。 |
| `blueprint/MaterialRequirement.java` | 匹配允许需求的 metadata 通配与指定 NBT 字段；ME 候选选择必须遵循真实匹配规则。`createSampleStack` 仅用于样例，不能用于退款。 |
| `blueprint/BlueprintPartSupport.java`、`BlueprintGregTechSupport.java`、`VanillaBlueprintAdapter.java` | 保留当前部件/覆盖板及安全配置恢复能力；本次只接通材料来源和相应成功/失败结算，不扩大蓝图复制能力。 |
| `ModLog.java`、`scripts/common.ps1` | 已有 UTF-8 文件日志和一次启动单文件机制；复用它们，不新增独立日志框架。 |

### 3.3 官方资料与精确源码

上游 tag 是 **`rv3.beta.6`（点号）**，不是本地 JAR 名中的 `rv3-beta-6`（连字符）。`git ls-remote` 已确认 tag 指向 `ed54f03e7022e1509a0d722a7014fd7a5e6cdbf8`。后续检索以此固定版本为准，不能用现代 1.21 AE2 API 代替。

- [ME 无线访问点](https://appliedenergistics.org/ae2-site-archive/ME-Wireless-Access-Point/index.html)、[无线接收器](https://appliedenergistics.org/ae2-site-archive/Wireless-Receiver/index.html)、[原生无线终端](https://appliedenergistics.org/ae2-site-archive/Wireless-Terminal/index.html)：区分设备、合成组件及手持终端。本计划的同维度不限距离、网络供电属于已确认的自定义行为。
- [ME 合成终端](https://appliedenergistics.org/ae2-site-archive/ME-Crafting-Terminal/index.html)、[ME 样板终端](https://appliedenergistics.org/ae2-site-archive/ME-Pattern-Terminal/index.html)、[ME 接口终端](https://appliedenergistics.org/ae2-site-archive/ME-Interface-Terminal/index.html)：分别对应手动合成、样板编码、网络接口样板管理。
- [安全终端](https://appliedenergistics.org/ae2-site-archive/ME-Security-Terminal/index.html)、[频道](https://appliedenergistics.org/ae2-site-archive/Channels/index.html)、[合成 CPU](https://appliedenergistics.org/ae2-site-archive/Crafting-CPU/index.html)：权限、设备在线条件、手动自动合成前置概念。
- [GuiBridge](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/core/sync/GuiBridge.java)：普通无线入口是物品宿主，合成/样板/接口终端依赖各自部件宿主；已核对 CRAFT/BUILD 入口权限。
- [ContainerCraftingTerm](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/container/implementations/ContainerCraftingTerm.java)、[ContainerPatternTerm](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/container/implementations/ContainerPatternTerm.java)：虽然构造参数写作 `ITerminalHost`，内部仍强制转换为具体终端部件；不能传入普通无线宿主就期待工作。
- [ContainerInterfaceTerminal](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/container/implementations/ContainerInterfaceTerminal.java)：容器是 `final`，构造需要 `PartInterfaceTerminal`；不能直接设计继承该容器的实现。接口列表操作还需持续复核目标和权限。
- [PartCraftingTerminal](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/parts/reporting/PartCraftingTerminal.java)、[PartPatternTerminal](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/parts/reporting/PartPatternTerminal.java)：原生真实格/虚拟格属于部件宿主；下一 Agent 必须实现每位操作者的独立会话存储，而非直接共享一组设备格子。
- [WirelessTerminalGuiObject](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/helpers/WirelessTerminalGuiObject.java)、[IWirelessTermHandler](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/api/java/appeng/api/features/IWirelessTermHandler.java)：原生无线绑定、范围和电池流程不是本任务全部能力的现成适配器。
- [TileWireless](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/tile/networking/TileWireless.java)、[AEConfig](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/core/AEConfig.java)：频道标志、方向连接、在线渲染和无增幅卡待机耗能的参考。
- [ISecurityGrid](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/api/java/appeng/api/networking/security/ISecurityGrid.java)、[SecurityPermissions](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/api/java/appeng/api/config/SecurityPermissions.java)、[SecurityCache](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/me/cache/SecurityCache.java)：使用实际玩家和相应权限；不要给 `hasPermission` 传空权限。
- [IMEInventory](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/api/java/appeng/api/storage/IMEInventory.java)、[NetworkInventoryHandler](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/me/storage/NetworkInventoryHandler.java)、[Platform](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/rv3.beta.6/src/main/java/appeng/util/Platform.java)：模拟/实际取料、未存入余量、玩家身份权限和 `poweredExtraction/poweredInsert` 能耗语义。原生取料不是跨步骤库存锁。

本轮已做多轮网络检索、官方文档和上述关键源码读取，但没有编译自定义宿主、演练 AE2 数据包或验证 NEI 填充。以下工程设计属于实施建议，不是已完成结果。

## 4. 实施顺序与交付关口

### A. 工程入口与可选依赖

1. 读取两个工作树的 AGENTS.md、MEMORY.md、本计划，检查最新状态。保留入口树既有未提交文档及目标树 `.diagnose-tmp/`，不清理存档、不重置用户改动。
2. 核实第3.1节被忽略的施工源码，处理本次涉及文件的可跟踪性；不把忽略规则造成的搜索缺失解释为“没有施工模块”。必要时对源码目录使用 `rg --no-ignore`。
3. 固定 AE2 版本与校验值，核验 RetroFuturaGradle 当前配置下的依赖映射方式。可选集成实现应独立加载：通用核心/法杖/启动路径不静态引用 AE2 客户端或缺失时不可解析的类型。
4. 优先复用 AE2 的容器、协议、物品定义和库存 API；可以在可选集成包中使用固定版本的编译期类型，不强制全反射，也不把 AE2 实现类打进本 Mod JAR。需要新下载时核验官方来源和精确版本。
5. 最先做最小的服务端宿主/容器可构造验证，核对 final、强制类型转换、宿主位置、`markForSave`、配置、网络监听及分端类加载；再扩展完整页面，避免先画界面后发现后端不成立。

### B. 设备、核心与绑定

1. 复用 `IWandCore` 与现有注册，新增独立 AE2 核心类别；AE2 不存在时不注册依赖 AE2 的配方和可运行集成入口，并提供可理解状态，不影响普通法杖。
2. 实现网络节点设备，连接方向与外观一致，管理节点创建、加载、卸载、销毁和在线同步；一个设备只代表一个需要频道的网络接入点，不为每个玩家或页面额外创建占频道设备。
3. 为传输器生成并保存实例标识；为法杖保存版本化绑定字段。推荐字段是维度、坐标、传输器标识，具体名称由实现决定；不能持久化运行时 `IGrid` 引用。
4. 在服务端统一绑定入口校验手持物品、核心、潜行、实际目标、交互合法性、设备在线和权限。成功才更新法杖、同步和反馈；失败不跌落到另一个会重复触发的右键分支。
5. 建立携带候选/选定目标服务，分别服务终端和施工。玩家选择映射到服务端已验证的候选，客户端传来的坐标或网络 ID 不能直接作为访问凭据。

### C. 四终端与会话

1. 找到原版背包打开时序中的准确入口，优先使用适用的 Forge GUI 事件或已有最小输入补丁；处理从终端返回原版背包时的单次绕过标记，防止再次重定向形成循环。
2. 建议会话维护玩家、选定来源、设备实例、世界/网络有效性标识、当前页与玩家私有临时格；用会话 ID/窗口 ID 拒绝迟到、重复和已关闭窗口的请求。
3. 先接普通库存与原版背包往返，再接合成、样板、接口页；同时验证手动自动合成子流程的打开上下文和返回目标。具体宿主适配和 GUI 组合策略在 A 阶段核实，不依赖对 `final` 容器继承或未验证的反射强改。
4. 自定义远端宿主必须支持“服务端目标已加载，但客户端并未加载传输器所在区块”的使用情形；不能靠强制加载远处客户端区块满足 GUI 构造要求。
5. 在打开、切换和每次变更请求前复核资格、来源、网络与权限。菜单开启期间持续检查失效条件，关闭后移除 AE2 监听器。NEI 填充和 AE2 子页面发包同样走有效会话，不绕过校验。
6. 空库存/无搜索结果正常显示；大量库存沿用有效的同步/分包与计数机制，不能把 AE2 长整型库存数量不加限制地转换成一个原版 ItemStack。普通点击与 Shift 输出尊重物品最大堆叠及背包容量。
7. 切页不触发最终退款；最终关闭统一执行且只执行一次临时格结算。死亡、登出、换维度及服务器正常停止走可保存/结算的生命周期，避免内存会话成为真实物品的唯一归宿。

### D. 施工材料结算

1. 为背包与 ME 来源实现本次所需的统一步骤结算对象；记录实际物品身份、扣除数量、来源及完成状态。目标是补齐本次供料路径，不建立通用仓储平台。
2. 主调度器与仍被调用的备用执行路径复用同一规则。一个请求内重复或重叠材料需求必须按可用余量分配，不能重复预检同一份库存。
3. 推荐顺序：校验任务/坐标/法杖/来源 → 模拟完整步骤用料及供电 → 实际取得材料并记账 → 执行受保护的放置/部件安装 → 按真实结果提交材料和耐久；失败退回实际取得物品。具体放置可回滚能力和可重入事件需结合现有适配器核验。
4. 修复主路径先扣耐久后失败，以及安装成功但后续扣费失败仍退全款的错误组合；不能留下世界已改变但材料被退回的免费建造状态。耐久 2→1 接续须完成当前合法步骤结算后平滑停止或继续。
5. 调用 AE2 原生供电存取时检查实际结果及返回余量；模拟不扣能量，真实调用不得被本 Mod 重复扣费。物料退款不等于承诺回滚 AE2 已实际使用的电量，不直接重写网络电池数值。
6. 协作者按本人 PlayerSource 取料，不能用机器来源或网络主人替代。坐标互斥锁、结算终态及异常清理覆盖正常放置、独立部件、重复包和错误回滚。
7. 缺料、断电、权限或来源失效区分反馈；已完成步骤保持进度，恢复从未完成位置继续，并重新验证启动时的来源。

### E. 美术、日志与资源

1. 查看现有 `art/cores` 和核心 PNG 后制作 AE2 核心贴图；选择与其它核心易区分、符合 AE2 风格的视觉细节。传输器建立与连接方向和碰撞体一致的模型，制作在线/离线可辨的原创贴图。
2. 使用届时可用且已读取的图像生成技能制作需要的位图；像素导出、透明度处理和工具选择遵守该技能。模型使用 1.7.10 能加载的实际渲染方式，不把现代方块 JSON 模型当作旧版可直接加载的资源。
3. 资源路径和注册 ID 由实施 Agent 统一确定，补充中英文、配方与 JAR 资源检查；保留必要提示词/制作说明和可复用导出检查入口。不要只提供高分辨率原图或缺失纹理的占位模型。
4. 复用 `ModLog`，在 `logs/freecam_interaction` 每次启动一个日志文件；工具日志复用 `logs/tools`。Windows 名称继续使用 `yyyy-MM-dd HH-mm-ss.log`。
5. 日志记录绑定结果、会话开关/失效原因、来源选择、施工步骤结算与退款余量，避免逐帧重复；异常记录必要上下文，不能只吞掉反射错误。不要输出账户凭据或无关完整 NBT。
6. 将新增测试、资源检查、启动等常用操作写入可复用 ps1/sh 入口；不改变系统默认 Java。

### F. 验证与交付

- 先做基础编译、资源打包及第5节的定向结算/权限冒烟，复用无测试框架的项目惯例，不新建重复实现的测试堆栈。
- 基础入口：`pwsh -File scripts/forge1710.ps1 smoke`。如现有全量检查过大，拆出可重复的 AE2 定向入口，同时保留编译、核心包和关键既有行为检查。
- 目标包含现有 lwjgl3ify 实例；在需要核验运行包时使用 `pwsh -File scripts/lwjgl3ify.ps1 smoke`。不要将该准备/打包检查写成四终端已通过实机操作。
- 需要恢复已确认的测试 Mod 时使用 `pwsh -File scripts/tech-mods.ps1 all`，无需换 AE2 版本。客户端入口为 `scripts/forge1710.ps1 client` 或 `scripts/lwjgl3ify.ps1 client`，使用隔离测试世界，不破坏用户存档。
- 新材质、模型与 GUI 必须目视检查；权限、多人共享和真实 NEI/AE2 协议行为做针对性游戏验证，受限项目明确记为未验证。无需默认跑整个项目全量 E2E。
- 更新两个工作树的 AGENTS.md、MEMORY.md 及本计划执行状态，精确到分钟、时间倒序。交付列明已实现、验证证据和未验证限制；未经另行要求不自动提交或启动新的执行任务。

## 5. 可观察验收矩阵

以下均为待实施、待验收项；本轮没有任何一项功能测试结果。

| 编号 | 场景 | 通过条件 |
|---|---|---|
| A01 | 核心与配方 | 两个无序配方各消耗约定数量并产出1件；核心不可堆叠，同杖重复安装被拒，和蓝图/移速核心共存。 |
| A02 | 外观与设备连接 | 核心32×32透明无黑底；传输器物品/世界模型正确，接线方向一致；通电且有频道时亮，断电或无频道时灭。 |
| A03 | 绑定动作 | 潜行右键传输器只执行一次绑定，不误开升级界面；其它目标仍能升级；失败保留旧绑定且给出原因。 |
| A04 | 携带资格 | 只携带装芯法杖即可使用，耐久1可以提供能力；散装核心、箱子内法杖不生效；蓝图核心在另一把杖上仍能施工。 |
| A05 | 多来源 | 多个不同绑定可选择并显示目标；耐久接续、槽位移动或选择另一终端不偷偷切换正在施工的来源。 |
| A06 | 重启及实例识别 | 正常重启保留绑定；卸芯停用、重装恢复；原位置拆除重放新设备时旧绑定不继承。 |
| A07 | 远端通信 | 同维度距离超过普通无线范围且服务端区块已加载时可操作；客户端无需加载远端区块；跨维度或目标卸载明确拒绝，不强加载。 |
| A08 | 输入与导航 | E及改键进入默认合成终端；四页和原版背包可往返，搜索输入不误触发；关闭返回自由视角且不会误挖，相机在界面期间不移动。 |
| A09 | 普通与合成终端 | 存取、搜索、空结果、Shift、满包、容器物品合成正常；网络和背包数量实际更新，没有虚假输出。 |
| A10 | 样板编码 | 两种模式可编码/编辑，确实消耗空白样板；虚拟材料不可取出；切页保留，最终关闭仅归还真实临时物品。 |
| A11 | 接口样板管理 | 可以操作正确网络中各接口的样板，遵守隐藏设置；网络接线变化后旧接口请求失效，已存入接口的样板不会随关闭被退回。 |
| A12 | 自动合成与 NEI | NEI填充有效；手动下单、数量确认、CPU状态与返回流程正常；无CPU或缺材料时遵循AE2反馈，不创建本Mod自动补料订单。 |
| A13 | 权限 | 分别撤销BUILD/INJECT/EXTRACT/CRAFT并观察相应拒绝；开着界面撤权后不能继续变更；转交法杖不继承原主人权限。 |
| A14 | 背包与ME混合施工 | 背包只有部分材料时优先使用背包、缺额来自选定ME；缺料暂停，补料可续；满背包仍可直接从ME施工。 |
| A15 | 现有模组施工 | 原版方块、AE2部件和GT覆盖板分别从ME供料并成功建造；metadata/NBT匹配与原有方向/配置恢复保持正确。 |
| A16 | 退款与物品身份 | 覆盖含额外NBT物品、重复需求、部分提取、放置拒绝/异常、网络满或断开；实际物品完整退回且仅一次，落地余量和总数一致。 |
| A17 | 并发及耐久 | 两人争同一份材料、同一坐标、快速重复请求均不刷物；耐久2→1接续/停止时当前步骤结算正确，失败不白扣耐久。 |
| A18 | 协作与创造规则 | 协作者使用本人选择和权限，不取任务主人资源；创造模式玩家免材料；生存玩家持创造法杖仍付材料。 |
| A19 | 会话清理 | 断电、无频道、拆设备、卸载、移走法杖、卸芯、登出/死亡/换维度后无残留可用窗口、监听或物品；原版背包可用、任务保留可恢复进度。 |
| A20 | 可选依赖及日志 | 不装AE2可启动原功能；有AE2的客户端、服务端分端正确；原生及lwjgl3ify各自验证；一次程序启动单个Mod日志，错误有可定位信息。 |

## 6. 排除项、待核验点与实施边界

已明确排除：1.21.1 移植、GTNH AE2 分支兼容、多网络库存合并、流体终端、普通手动放置自动供料、缺料自动下单合成、跨维度通信、远程区块自动保载、无线增幅卡升级及新增法杖电池系统。

缺料自动下单会增加 CPU 任务、重复下单和取消结算；跨维度/保载会扩大世界与区块管理范围。它们是已讨论但未纳入的增强，不能因“完善体验”在实施中加入。

仍需工程核验而非再次询问用户的事项：固定 AE2 的构建映射/分端加载，四类原生容器可用的远端宿主方案，NEI与自动合成子流程包路由，设备节点完整生命周期，放置事件失败后的世界/材料一致性。这些必须由实际源码、编译及针对性验证证实；不能用“官方有这个接口”代替可用性证据。

不把程序被强制杀死、存档损坏或断电时跨Minecraft/AE2多个存档的原子提交当作本次已承诺保证。正常会话关闭与异常请求必须按第2节安全结算；若发现已确认目标必须改变玩法或增加额外依赖才能实现，提交明确增量问题，而非擅自删减四终端或把远端访问降成只读。
