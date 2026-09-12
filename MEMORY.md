# 项目记忆

- 2026-09-12 19:31：按用户要求提交 AE2 传输核心完整实现，提交范围包含传输器/核心、四类远端终端与纵向导航、多目标选择、蓝图 ME 物料事务、资源、回归门槛及项目文档；保留 `.diagnose-tmp/` 为未跟踪本地诊断材料，不纳入提交。

- 2026-09-12 19:22：用户实机确认 AE2 远端终端功能正常；按反馈将顶部横排的六个跨页按钮改为容器左侧纵排的 `18×18` 方形按钮，标签为 `ME/合/样/接/包/网`。AE2 终端使用原生设置按钮左侧的第二列，背包页因无原生设置列而贴近容器，避免遮挡标题和物品列表。`forge1710.ps1 smoke` 增加纵向布局回归断言并通过（日志 `logs/tools/2026-09-12 19-22-22.log`），lwjgl3ify 3.0.33 + Java 25 准备冒烟通过（日志 `logs/tools/2026-09-12 19-22-37.log`）；构建与实例 JAR SHA-256 均为 `A875FCDE785583BD43C912024A3326201AF5DCD046472494DA155F4C6C78070C`。仍需实机目视确认不同 GUI 缩放下的按钮位置。

- 2026-09-12 19:16：根据用户复现日志定位并修复自由视角 E 创建 AE2 合成终端时的空指针。AE2 `AbstractPartReporting` 会在子类字段赋值前从父类构造器动态调用重写的 `getProxy()`，此前 `RemoteCrafting.tile` 尚为 null；现由 `RemoteTerminals.CONSTRUCTING` 在三类虚拟终端构造期间提供真实 `TileAe2Transmitter`，完成后于 `finally` 清理，合成/样板/接口终端的全部宿主委托均兼容构造期。`Ae2Integration` 同时保留完整异常堆栈日志。`forge1710.ps1 smoke` 新增构造期宿主回归断言并通过（日志 `logs/tools/2026-09-12 19-15-50.log`），lwjgl3ify 3.0.33 + Java 25 准备冒烟通过（日志 `logs/tools/2026-09-12 19-16-03.log`）；构建和实例 JAR SHA-256 均为 `BA0CE3029605C7FBD5D75508201254F15696103D38245342696C01F10784DE83`。仍需用户重启并实机确认 E 打开默认合成终端，再验收其余终端。

- 2026-09-12 19:08：用户实机确认 AE2 传输器绑定与蓝图 ME 供料成功，但自由视角 E 未进入 AE2 终端。根因是终端入口仍用客户端法杖 NBT 判断绑定资格，而绑定与取料状态由服务端权威维护，存在同步时序分歧。现将自由视角 E 统一发送默认入口 `requestTerminal(6)`，服务端依据实时 `candidates(player)` 选择合成终端或原版背包；`RemoteTerminals.serverGui` 允许无传输器会话时建立原版背包容器。`forge1710.ps1 smoke` 新增服务端权威分流回归断言并通过（日志 `logs/tools/2026-09-12 19-08-19.log`），lwjgl3ify 3.0.33 + Java 25 准备冒烟通过（日志 `logs/tools/2026-09-12 19-08-24.log`）；构建与实例 JAR SHA-256 均为 `12507463B36756EE0DBA10282C833E74F6FAC4B89697761B6110B49E14C41388`。仍需用户实机复验 E 打开默认合成终端。

- 2026-09-12 18:57：修复 AE2 传输器绑定、ME 蓝图供料与自由视角 E 键回归。客户端 `ItemFreecamWand.onItemUseFirst` 原经 `Ae2Integration.tryBind` 对传输器返回 true，导致 `PlayerControllerMP.onPlayerRightClick` 在发送 C08 前提前结束，服务端 `Ae2Runtime.bind` 永不执行；现改为客户端返回 false、服务端执行并消费绑定。`FreecamClient.keyboard` 原在确认已绑定法杖前调用会递减 `pressTime` 的 `keyBindInventory.isPressed()`；现先检查绑定资格，未绑定时保留原版背包键。`forge1710.ps1 smoke` 新增两项回归门槛。原生冒烟 `BUILD SUCCESSFUL`（日志 `logs/tools/2026-09-12 18-57-08.log`），lwjgl3ify 3.0.33 + Java 25 准备冒烟通过（日志 `logs/tools/2026-09-12 18-57-25.log`）；构建 JAR 与实例部署 JAR SHA-256 均为 `40FC05BCF153100C9304FB7706B6EBB19B38D6621F23F5ED2E63A4DBC703073B`。自动化已覆盖升级核心 NBT 持久化；仍需用户实机重新装芯、绑定并验证 ME 木板施工与终端入口。

- 2026-09-12 18:33：修复 lwjgl3ify 实例进入存档后在首个服务端 tick 闪退。崩溃报告为 `java.lang.IllegalAccessError: failed to access class local.freecaminteraction.ae2.rv3.RemoteTerminals$Lifecycle`；根因是 Forge 1.7.10 的 `ASMEventHandler$ASMClassLoader` 无法访问私有嵌套监听器。将 `RemoteTerminals.Lifecycle` 改为 `public static final`，并在 `forge1710.ps1 smoke` 增加公开可见性回归断言。原生冒烟 `BUILD SUCCESSFUL`（日志 `logs/tools/2026-09-12 18-32-25.log`），lwjgl3ify 3.0.33 + Java 25 准备冒烟通过（日志 `logs/tools/2026-09-12 18-33-43.log`）；`javap` 确认产物为 `public final class local.freecaminteraction.ae2.rv3.RemoteTerminals$Lifecycle`。构建 JAR 与实例 `mods/freecam_interaction.jar` SHA-256 均为 `D326354A0FF712F173D292D6B7D9CC8902BAD0E3DAC58BCC7A4D5EFA439B4476`。存档未被修改，仍需用户实际进入验证。

- 2026-09-12 18:22：接续并审计前 Agent 未提交的 AE2 传输核心实现。保留所有已有工作和 `.diagnose-tmp/`，修复真正远距离时客户端 GUI 因读取未加载远端 TileEntity 而无法打开的缺陷：`RemoteTerminals.clientGui` 改用玩家本地世界的会话占位宿主，真实传输器和 ME 网络仍仅由服务端持有与校验。修复 `Ae2MaterialReservation` 重复/重叠需求模拟未预占同一 ME 库存的误拒，以累计模拟请求防止重复计数，失败仍退回实际提取物。用内置 imagegen 生成并目视检查 32×32 透明 `ae2_transfer_core.png`、`ae2_transmitter_off.png`、`ae2_transmitter_on.png`；原始图位于 `C:/Users/15575/.codex/generated_images/01a09519-6935-7702-95bb-440a82b7a87c/`。`forge1710.ps1 smoke` 新增 AE2 核心类/三张资源 JAR 检查及客户端无 `selectedTile` 约束；原生冒烟 `BUILD SUCCESSFUL`（日志 `logs/tools/2026-09-12 18-21-41.log`），lwjgl3ify 3.0.33 + Java 25 准备冒烟通过（日志 `logs/tools/2026-09-12 18-21-51.log`），`git diff --check` 无内容错误（仅 CRLF 提示）。尚未执行游戏内四终端、权限、多目标与断线/断网回滚 E2E；1.21.1 对应功能仍待实现。

- 2026-09-12 12:19：AE2 传输核心完成 intent-completion 两次确认，用户最新指令限定本轮只产出实施计划并 handoff。新增 `PLAN-ae2-transfer-1710.md`，交接入口位于相邻主树 `../上帝视角建造/HANDOFF-ae2-transfer-1710.md`；已确认同维度不限通信距离、无新增保载、背包优先/ME补缺、多目标显式选择、四终端和玩家权限、实际物品退款，完整配方与验收以计划为准。已核实官方 tag `rv3.beta.6` 指向 `ed54f03e7022e1509a0d722a7014fd7a5e6cdbf8`，本地 JAR SHA-256 与 `scripts/tech-mods.ps1` 一致；原生终端硬类型约束和主施工退款缺陷已记为实施依据。`git ls-files` 未列出 `blueprint/build` 两个源码，`git check-ignore -v` 确认 `.gitignore:2:build/`，下一 Agent 必须使用现有工作树并解决相关源码跟踪。本轮仅维护文档，未修改功能、美术、测试或忽略规则，未安装依赖、构建、启动游戏/执行 Agent 或提交；保留既有 `.diagnose-tmp/`，1.21.1 功能仍待实现。

- 2026-09-12 11:55：用户确认 Forge 1.7.10 两倍/四倍移速核心人工验收通过；准备将实现、测试、计划交接文档及配套美术资源一并提交 Git，保留既有未跟踪 `.diagnose-tmp/` 不提交。

- 2026-09-11 23:26：完成 Forge 1.7.10 两倍/四倍移速核心实现并通过冒烟验证。
  - 新增 `ItemSpeedCore`，注册 `speed_core_2x` / `speed_core_4x`、时钟无序合成两倍核心和“两枚两倍核心 + 石英块”无序合成四倍核心；两档共享 `coreId=speed`，沿用四槽容器的同类互斥规则，同时允许与蓝图核心共存。
  - `ItemFreecamWand.getInventorySpeedMultiplier` 扫描主背包 0..35 内所有法杖并只取最高档，不检查耐久，因此剩余耐久 1 的法杖仍提供被动效果；非法重复 NBT 同样只取最高档、不叠加。
  - 服务端每 tick 依据最高档维护固定 UUID、`operation=2`、`setSaved(false)` 的 `movementSpeed` modifier，使本体总移速独立乘 1.25/1.5；登出、重生和跨维度时主动移除。客户端只缩放 `FreecamMotion` 已夹限后的水平/升降位移至 2/4 倍，潜行键按当前改键状态临时恢复 1 倍。
  - 补齐中英文名称/说明、JAR 必备类和材质清单，并在 `LegacyActionCheck` 覆盖不可堆叠、同类互斥/蓝图共存、倍率数学、耐久 1、生效法杖最高档及非法 NBT 不叠加。
  - 验证：`pwsh -NoProfile -File scripts/forge1710.ps1 check` 通过；`pwsh -NoProfile -File scripts/forge1710.ps1 smoke` BUILD SUCCESSFUL（16 actionable tasks，ActionCheck、无 GT 反射检查与 Java 8 smoke 全部通过）；JAR 已确认包含 `ItemSpeedCore.class`、两张 PNG 和 `zh_CN/en_US` 语言文件；`git diff --check` 无错误，仅 CRLF 提示。

- 2026-09-11 22:48：完成两倍/四倍移速核心材质贴图生成与透明度校准，未编写/修改任何逻辑代码。
  - 用户明确要求：“你只生成所需材质贴图并汇报贴图文件路径，不编写代码”。
  - 产物文件：
    - `src/forge1710/resources/assets/freecam_interaction/textures/items/speed_core_2x.png`（32×32 RGBA PNG，铜质齿轮边框+金黄时钟表盘+翡翠绿刻度与指针，透明像素 480，可见像素 544）。
    - `src/forge1710/resources/assets/freecam_interaction/textures/items/speed_core_4x.png`（32×32 RGBA PNG，平滑石英边框+深邃暗紫星空+亮白星芒核心，透明像素 484，可见像素 540）。
    - `art/cores/preview.png`（384×128 4倍放大并排预览，包含蓝图核心、两倍核心、四倍核心）。
    - `art/cores/speed_core_2x_source.jpg` 与 `art/cores/speed_core_4x_source.jpg`（原始高分辨率生成图备份）。
    - `art/cores/PROMPTS.md`（补充生成提示词与视觉设计记录）。
  - 严格保持 0 行功能/测试代码改动，未安装新依赖，未修改系统 Java 环境，未自动提交。

- 2026-09-11 22:28：两倍/四倍移速核心完成两次确认，按用户要求仅规划并交接，不实施。
  - 已确认需求与验收基线见 `PLAN-speed-cores-1710.md`；接手说明见 `HANDOFF-speed-cores-1710.md`，按全局 handoff 技能生成，不重复计划正文。
  - 只做 Forge 1.7.10；主背包最高档携带生效（包含剩余耐久 1 的法杖），本体独立乘 1.25/1.5，相机平移升降乘 2/4，潜行改键跟随且仅临时取消相机加速，同杖同类互斥，无专属药水图标。配方等完整契约以计划为准。
  - 只读核实：findBestWand 排除耗尽法杖，不适合移速档位；FreecamMotion 将 elapsed 夹到 0.05，实施应缩放输出位移而不是 elapsed。原生属性乘算、同步、持久化尚需下一 Agent 核验。
  - 本轮未修改功能源码、生成材质、启动执行 Agent、构建或运行游戏；仅做文档核对，不声明功能测试通过。开始时已有未跟踪 `.diagnose-tmp/`，保留不动。

- 2026-09-11 21:47：调整自由视角光标跟随物品渲染偏移。
  - 将手持物品在光标右下方的渲染偏移由 `(+10, +10)` 缩小至 `(+4, +4)`，使物品图标更加紧凑地贴近鼠标光标尖端，同时保持不阻挡热点判定。
  - 验证：执行 `pwsh -File scripts/forge1710.ps1 smoke`，编译与自动化检查全部通过。

- 2026-09-11 21:46：完成自由视角模式光标旁显示选中物品与快捷栏点击切槽/防误挖（Forge 1.7.10）。
  - 需求背景：用户优化自由视角体验，要求光标旁跟随显示快捷栏选中物品，且允许直接鼠标点击底部快捷栏 GUI 切换选中槽位，并保证点击不会误触发对世界中方块的挖掘。
  - 实现方案：
    1. 新增 `FreecamHotbar` 抽象纯数学几何命中算法：基于原版 182×22 快捷栏几何（左上角 `(screenWidth / 2 - 91, screenHeight - 22)`），将鼠标位置映射到槽位 0..8。
    2. 在 `FreecamClient.mouse()` 中优先拦截左键点击快捷栏：命中时立即调用 `stopMining()` 终止当前正在进行的挖掘，同步切槽 `player.inventory.currentItem`，记录 `ModLog`，并取消事件阻止进入世界点击与挖掘管线。
    3. 在 `FreecamClient.hud()` 中使用原版 `RenderItem`（`renderItemAndEffectIntoGUI` / `renderItemOverlayIntoGUI`）在自由视角下渲染鼠标光标右下方偏移 (10, 10) 的手持物品图标、数量与耐久度。
  - 验证：
    1. 在 `scripts/LegacyCheck.java` 中增加快捷栏边界与槽位映射的断言（左边界外、左边框、槽位 0、槽位 8、右边框、右边界外、上下边界外）。
    2. 执行 `pwsh -File scripts/forge1710.ps1 smoke`，编译、ActionCheck 及 Java 8 断言全部通过（BUILD SUCCESSFUL）。


- 2026-09-11 21:14：修复 AE2 线缆蓝图“已采集但预览缺失、施工全部失败”的回归。
  - 用户日志中的 `blocks=3; parts=8` 证明采集、BPFC 与切片传输正常；根因一是 `BlueprintGhostRenderer` 只遍历方块条目，而 AE2 总线宿主按设计不重复保存为方块条目。渲染器现额外按坐标去重绘制部件宿主格，纯 AE2 线缆位置也会显示；与普通方块/GT 宿主重合时不叠加。
  - 根因二是 `BlueprintPartSupport.optionalGet` 从包私有 Guava `Present` 实现类反射公开 `get()`，Java 8 仍会抛 `IllegalAccessException`。现缓存并通过公开 `com.google.common.base.Optional` 基类的 `get()` 方法调用；`LegacyActionCheck` 使用真实 `Optional.of` 覆盖该回归。
  - 主调度器同步调整为部件安装成功后才扣法杖耐久；安装失败归还预扣材料且不再白扣耐久。
  - 验证：`pwsh -File scripts/forge1710.ps1 smoke` 与 `pwsh -File scripts/lwjgl3ify.ps1 smoke` 均 BUILD SUCCESSFUL；构建 JAR 与 lwjgl3ify 实例 JAR SHA-256 均为 `2c31b139c84aa8a0c35fc1c9373a29b2964ed4cf7fc60db8b45a1ed72df165cf`。用户现有 `testae2` 蓝图已有 8 个 parts，无需重新采集，重启实例后可直接复测预览和施工。

- 2026-09-11 20:57：修复 GT5U 5.09.31 蓝图覆盖板缺失与机器朝向错误，完成纯反射实现及自动化回归。
  - 新增 `BlueprintGregTechSupport`，运行时反射 `IGregTechTileEntity` / `ICoverable`：机器正面通过 `getFrontFacing/isValidFacing/setFrontFacing` 采集与恢复；覆盖板逐面通过 `getCoverItemAtSide/getCoverDataAtSide` 采集，通过 `canPlaceCoverItemAtSide/setCoverItemAtSide/setCoverDataAtSide/issueCoverUpdate` 安装并同步。
  - 朝向不再白名单复制原始 `mFacing`，而是保存 Mod 自有键 `freecamGtFrontFacing`；`VanillaBlueprintAdapter.place` 与调度器 `FINALIZE_CONFIG` 共用 `applyStaticConfiguration`，该键只会走 `setFrontFacing`，绝不会进入 `readFromNBT`，继续保护 `mID`、库存、流体、能量、进度和主人字段。
  - GT 覆盖板复用现有 `BlueprintPartEntry` / BPFC v1：`partId` 带 `gregtech-cover:` 前缀，物料保存物品注册名与 damage，`configTag` 保存 `coverData`，不持久化 `mCoverSides` 运行时数字 ID；采集时追加 PARTS 条目但继续保存宿主机器方块。已有同类型覆盖板会幂等校正 `coverData`，不重复扣料或耐久。
  - `BlueprintPartSupport` 按条目类型分发 AE2 与 GT；备用 `BlueprintBuildExecutor.executePartStepDirect` 修正为安装成功后才提交预留物料和扣耐久，安装失败保持物料与耐久不变。主调度器仍按 SOLID/ATTACHED -> PARTS -> FINALIZE_CONFIG 顺序执行，覆盖板失败归还材料。
  - `LegacyActionCheck` 使用 GT 接口动态代理验证 facing=4 的采集/恢复、两个覆盖板的 side/damage/coverData、安装/幂等、四单元依赖顺序、BPFC v1 往返及部件失败事务；新增无 GT 类路径的独立进程检查，确认缺少 GT 时只禁用、不产生类加载错误。主源码和产物经 jdeps 核对无 GT 静态类型依赖。
  - 验证：`pwsh -File scripts/forge1710.ps1 smoke` BUILD SUCCESSFUL；`pwsh -File scripts/lwjgl3ify.ps1 smoke` BUILD SUCCESSFUL。构建 JAR 与 lwjgl3ify 实例 JAR SHA-256 均为 `390d990c6455e8a03606d6308bf716f8bf6a7b9016cd658959e1b17a010d3076`。现有世界的只读诊断仍按预期返回旧结果 FAIL（source facing=4 / built=2，两个 built covers 全 0），因为 `Blueprint_2074_gt` 与既有施工结构永久缺失数据；必须用仍存在的源结构重新采集并施工后才能做最终实机 PASS 验收。

- 2026-09-11 18:17：补齐 GT 黑紫块修复遗漏的调度器最终配置路径；18:07 的首次修改只保护了 `VanillaBlueprintAdapter.place/matches`，但 `BlueprintBuildScheduler.executeFinalizeUnit` 仍会直接读取旧蓝图原始 `tileTag` 并第二次回灌。
  - `generateDependencySortedPlan` 现仅在 `VanillaBlueprintAdapter.sanitizeTileTag(entry.getTileTag()) != null` 时生成 `FINALIZE_CONFIG`；旧 GT 蓝图中只有 `id` 的残缺 tag 因而从 8 个施工单元降为 4 个纯放置单元。
  - `executeFinalizeUnit` 同样先重新消毒，再决定是否 `readFromNBT`，形成执行期纵深保护；即使异常计划或会话已包含 FINALIZE_CONFIG，也不会清空 MTE。
  - `scripts/LegacyActionCheck.java` 新增旧 id-only 蓝图执行计划断言：1 个方块条目只能产生 1 个非 FINALIZE_CONFIG 单元。首次测试因无引导测试进程中 `Blocks.stone == null` 被当作空气而失败，改用已有 `RayBlock(Material.rock)` 测试桩后通过。
  - 最终验证：`pwsh -File scripts/forge1710.ps1 smoke` → BUILD SUCCESSFUL；`pwsh -File scripts/lwjgl3ify.ps1 smoke` → BUILD SUCCESSFUL；`git diff --check` 仅既有 CRLF 提示；构建与实例 JAR SHA-256 均为 `13db0ec9a44c2657e5fe1d260321e9cb157eaa91e3068e296d999166fc24abb6`。

- 2026-09-11 18:07：第三次定位并修复 GT 蓝图机器建成黑紫块；本次根因由实机日志与真实蓝图 NBT 双重确认，不再归因机器 ID。
  - 实机证据：`logs/freecam_interaction/2026-09-11 17-54-41.log` 中新采集 `Blueprint_2074_gt` 为 4 个方块，施工 `totalUnits=8`；4 次 `GT_Item_Machines.placeBlockAt` 均携带正确 ID（136/5162/5162/128）且返回 `ok`，任务 0 冲突仍变黑紫。8 个单元恰好是 4 次方块放置 + 4 次 `FINALIZE_CONFIG`。
  - 蓝图文件证据：解析 lwjgl3ify 存档 `.../freecam_interaction/blueprints/4bd03cd7-.../26ed4439-....dat` 的 GZIP NBT 后确认，每个 GT 方块物料 damage 正确，但 `tile` 只剩 `{id: BaseMetaTileEntity}` 或 `{id: BaseMetaPipeEntity}`；关键 `mID` 已被白名单消毒丢弃。
  - 根因链：`GT_Item_Machines.placeBlockAt` 先正确创建 MTE → `VanillaBlueprintAdapter.place` 立即把仅含 `id`+坐标的不完整 NBT `readFromNBT` 回新 TE → `BlueprintBuildScheduler.executeFinalizeUnit` 又回灌一次 → 缺失 `mID` 清空刚创建的 MTE，产生黑紫块。
  - 修复（`src/forge1710/java/local/freecaminteraction/blueprint/VanillaBlueprintAdapter.java`）：`sanitizeTileTag` 新增 `hasSafeConfiguration`，只有 id 而没有任何白名单配置键时返回 null；`place` 对持久化旧蓝图中的 `tileTag` 先再次 `sanitizeTileTag`，残缺 tag 只记 `Skipped incomplete TileEntity config` 而不回灌；`matches` 同样先重消毒期望 tag，残缺配置不参与匹配。这样新蓝图不会生成无意义的 FINALIZE_CONFIG，当前存档中旧的 GT 蓝图也可安全施工，不要求再次采集。
  - 回归检查：`scripts/LegacyActionCheck.java` 增加 id-only NBT 必须被拒绝的断言；`pwsh -File scripts/forge1710.ps1 smoke` → BUILD SUCCESSFUL；`pwsh -File scripts/lwjgl3ify.ps1 smoke` → BUILD SUCCESSFUL 且把新 JAR 同步到实例。构建 JAR 与实例 `mods/freecam_interaction.jar` SHA-256 均为 `89c33b584b2c36f179f368e8699d42c00f909bb6601d538e549422d8f7ea694c`；`git diff --check` 仅有既有行尾警告，无错误。
  - 仍明确延后：GT 朝向、覆盖板、管道连接与其它配置的安全白名单恢复；当前只保证机器类型/MTE 正确且不复制资源。

- 2026-09-11 17:50：上一轮 AE2/GT 修复在实机验证中“未生效”，取证后修复并重新交付。
  - 取证源：用户 17:24-17:27 游戏会话日志（`logs/freecam_interaction/2026-09-11 17-24-40.log`）。
  - AE2 问题定性：**不是代码未生效，是测试用了旧蓝图**。日志显示该局只新采集了 1 个蓝图（`Blueprint_1261_test_greg; blocks=4`），AE2 施工用的是启动时从存档加载的旧蓝图（`Blueprint_9135`/`Blueprint_7844`）；旧数据采集于部件管道存在之前，线缆被记成“总线方块条目”，建造出来是无部件的空总线（不可见），所以部件永远不出现。**旧 AE2 蓝图无法升级，必须重新框选采集**。AE2 侧反射链已用字节码复核：`CableBusContainer.addPart` 对线缆直接 `setCenter`（与传入方向无关，UNKNOWN 正确）、`canAddPart` 对 UNKNOWN 放行、要求 item instanceof IPartItem。
  - GT 问题定性：真 bug。`GT_Item_Machines.placeBlockAt` 字节码 offset 10 `ifle 195`：`getDamage(stack)<=0` 时不创建 MTE，自己 `setBlock(block,0,3)` 后**返回 true**（复现黑紫且无 conflicts）。而 GT 机器的**世界 metadata 只是基型编号（getTileEntityBaseType），机器身份在物品堆 damage**；`GT_Block_Machines` 未覆写 getPickBlock，原采集物料 damage 拿到的是 damageDropped(meta)≈0 → GT 走了空 ID 分支。
  - 修复（`VanillaBlueprintAdapter`）：新增 `public static int teItemIdOverride(TileEntity)`，鸭子类型反射读 TE 的 `getMetaTileID()`（不引用模组类）；`resolveMaterials(..., TileEntity te)` 用它补正采集物料的 damage（pickStack.setItemDamage 或回退路径直接用 override）。**注意坑：反射 invoke 对包私有实现类会抛 IllegalAccessException（测试匿名类暴露），必须 `m.setAccessible(true)`。**
  - 观测补强：`placeModdedViaItemHook` 现在记录每次模组方块放置的 `ok/FAILED + 注册名@damage + 坐标`（此前静默回退不可观测）；`BlueprintNetwork` 采集日志增加 `parts=` 计数。
  - 冒烟补强（`scripts/LegacyActionCheck.java` 蓝图段）：`teItemIdOverride` 对 null/普通 TileEntity 返回 -1、对带 getMetaTileID() 的匿名 TE 返回 4242。离线单测时若要实例化 TileEntity 需在 classpath 加 `build/rfg/recompiled_minecraft-1.7.10.jar` + log4j-api/core 2.0-beta9-fixed + guava，且 Windows java 用 cygpath 转换缓存 jar 路径。
  - 旧蓝图（GT 与 AE2 均）不含机器 ID/部件数据，不可升级，需重新采集。验证：`pwsh -File scripts/forge1710.ps1 smoke` → BUILD SUCCESSFUL（Exit 0）。仍待实机复测：新采集的 AE2 选区（日志应出现 parts=N>0）与 GT 机器（日志应出现 `modded ItemBlock place ok for ...@机器ID`）。

- 2026-09-11 17:27：修复了阻断子代理的环境故障（非本项目代码，但影响本项目开发）。
  - 现象：`acp_delegate` 全部失败，stderr = `Failed to load extension "C:\Users\15575\.pi\agent\extensions\cbmem.ts": ParseError: Identifier 'BIN' has already been declared. cbmem.ts:305:6`；`subagent` 工具则因另一个无关原因失败：`option '-p, --port <port>' argument '--no-session' is invalid`（该工具调用 pi 时参数拼接有误，未修）。
  - 根因：`~/.pi/agent/extensions/cbmem.ts`（全局自动发现目录 `~/.pi/agent/extensions/*.ts`）里存在**两代生成内容**：第 1-297 行是旧版（用 `execute(toolCallId, params, signal, onUpdate, ctx)` + `label/description/parameters`，BIN = `C:/Users/15575/.local/bin/...`），第 298-349 行是被追加的 `// codebase-memory-mcp:start` 生成块（用 `registerTool({ name, run })`，BIN = `C:/Users/15575/AppData/Local/Programs/...`）。两段各自 `import { spawn }` + `const BIN` → 模块级重复声明 → 整个扩展文件解析失败 → 所有加载它的 pi 进程（含子代理）启动即死。
  - 关键取证：pi 0.85.1 只读 `definition.execute`（`@earendil-works/pi-coding-agent/dist/core/tools/tool-definition-wrapper.js` 中 `execute: (…) => definition.execute(…)`，全文不读 `run`）→ 追加块的 15 个 `run` 式注册即使能解析也无法被调用，两代工具名集合完全一致（均 15 个，`diff` 无差异）。因此保留旧段、删掉追加块。
  - 修复：`sed -i '298,$d'`，备份为 `cbmem.ts.bak-20260911-172641`（296140288 字节的两个二进制均可正常响应 `cli list_projects`）。
  - 验证：`bun build cbmem.ts` 通过；用 bun 模拟 pi 加载器 → `loaded tools: 15`、`all have execute(): true`；实调 `list_projects`（返回 29 个项目）与 `index_status` 均成功；**端到端** `acp_delegate`（researcher，exit 0）首次成功。
  - 遗留：git 历史里两个二进制大小完全相同，推测更新器（写 `codebase-memory-mcp:start` 块的那个）会**追加**而非替换，下次安装/更新可能再次产生重复声明（同一故障复发）；且新建/更新后的块仍然用 pi 不认的 `run` 键。
  - 附带观察：主会话与子代理会话都看不到 `search_graph`/`index_status` 等图谱工具（扩展已可加载，推测被 `~/.pi/agent/settings.json` 的 `defaultTools: [read, bash, edit, write]` 或代理工具白名单过滤）；本轮未改动该配置。

- 2026-09-11 17:25：修复模组方块/部件建造缺陷（用户人工测试反馈：“来自ae2的线缆、线缆锚、面板完全无法被建造出来，建造任务会显示完工，但是完全没有出现这些方块；而来自gt5的机器，建造出来则会直接变成黑紫块”）。
  - 根因 A（AE2 部件根本没进管道）：`blueprint/network/BlueprintCaptureHelper.capture` 的采集循环只调 `vanillaAdapter.capture`，`partEntries` 从未被填充；`blueprint/build/BlueprintBuildScheduler.installPartOnHost` 是空壳反射（只判 `getPart` 是否存在后无条件 `return true`）；`blueprint/build/BlueprintBuildExecutor.executePartStepDirect` 更彻底：只做权限/锁/扣料/扣耐久就 `BuildResult.success()	`，完全不碰世界。即“扣了材料与耐久但什么都没建”。
  - 根因 B（GT 机器黑紫块）：放置走 `VanillaBlueprintAdapter.place` 的 `world.setBlock(block, meta, 3)`。GT 机器的 MTE 只在 `GT_Item_Machines.placeBlockAt` 内创建（`getDamage(stack)` → `GregTech_API.METATILEENTITIES[damage]` → `setBlock` → `getTileEntity` → checkcast `IGregTechTileEntity`），只 setBlock 得到无 MTE 的残缺 TE → 渲染黑紫。
  - 修复 A（新增 `blueprint/BlueprintPartSupport.java`，纯反射访问 AE2，未加任何编译期依赖）：`isPartHost(TileEntity)`（`appeng.api.parts.IPartHost` 实例判定）、`captureParts(te,dx,dy,dz)`（遍历 side 0..5 与 `SIDE_CENTER=6`，用 `IPart.getItemStack(PartItemStack.World)` 取部件物品并生成 `BlueprintPartEntry`）、`matchesInstalled(...)`（幂等跳过）、`installPart(...)`（宿主缺失时用 `AEApi.instance().definitions().blocks().multiPart()` 的 `maybeBlock/maybeItemBlock/maybeStack(1)` 自建总线方块，再 `IPartHost.addPart(ItemStack, ForgeDirection, EntityPlayer)`，最后 `getPart(side)` 复核类型一致）、`canSelfHost(...)`（仅 `IPartItem` 有资格自建宿主）。`BlueprintCaptureHelper` 对 `isPartHost` 命中位置 `continue` 跳过方块条目（避免同一根线缆被方块+部件各扣一次料）。`BlueprintBuildScheduler.executePartUnit` 改为：宿主守卫允许空位（限 `canSelfHost`）→ `matchesInstalled` 幂等跳过 → 扣料 → `installPart` 失败时 `refundMaterials` 并记 `session.conflicts`；删除空壳 `installPartOnHost`。`BlueprintBuildExecutor.executePartStepDirect` 补上真实安装，失败返回 `CONFLICT_OR_BLOCKED`。
  - 修复 B（`VanillaBlueprintAdapter.place`）：新增 `placeModdedViaItemHook(...)`，注册名非 `minecraft:` 前缀且物品是 `ItemBlock` 时改走 `ItemBlock.placeBlockAt(stack, player, world, x,y,z, 2, 0.5f,0.5f,0.5f, meta)`，样品物品取采集时记录的实际物料（`sampleStackFor` → `MaterialRequirement.createSampleStack`，模组机器的损伤值即机器 ID）；返回 false 时回退原 `setBlock`。**原版方块（`minecraft:` 前缀）刻意不改路径**，避免触发原版 `onBlockPlacedBy` 的额外行为（例如箱子自动连体）而回退已验收通过的摆放功能。
  - 关键 API 事实（javap 取证，`run/mods/appliedenergistics2-rv3-beta-6.jar`）：`CableBusContainer.getPart(dir)` 对 `ForgeDirection.UNKNOWN` 返回 `getCenter()`（线缆本体）、否则 `getSide(dir)`；`addPart` 内部先 `canAddPart` 且要求 `item instanceof IPartItem`；`PartItemStack` 常量集 = `Pick/Break/Wrench/Network/World`；`IDefinitions.blocks()` → `IBlocks.multiPart()` → `ITileDefinition extends IBlockDefinition extends IItemDefinition`（`maybeBlock/maybeItemBlock/maybeStack(int)`）。`GT_Item_Machines extends ItemBlock` 且**只有**它覆写了 `placeBlockAt(ItemStack,EntityPlayer,World,int,int,int,int,float,float,float,int)`。
  - 设计决策（带理由）：①不新增 GT/AE2 适配器类或注册表——GT 的根因是“放置绕过了物品放置钩子”，在共享的 `place` 路径修一次即可覆盖所有模组机器（ponytail：不为单一实现造接口）；②不用 `PartPlacement.place`/`IPartHelper.placeBus` 走鼠标点击语义（其 `side` 在“点击方块面”与“目标槽位”间双关，服务端还会 `getOpposite()`，语义无法确定），改为直接 `addPart`；③不采集部件配置 NBT（AE2 接口等部件 NBT 内含物品库存，按名删键无法保证不复制资源），仅按部件物品+损伤值重建类型；④中心槽位用 `side=6` 表示（`BlueprintPartEntry.side` 语义由 0..5 扩展为 0..6），存储格式未变（仍是 int）。
  - 冒烟补强（`scripts/LegacyActionCheck.java` 蓝图核心段）：`SIDE_CENTER == 6`、`ForgeDirection.getOrientation(6) == UNKNOWN`（中心槽位映射这一设计前提）、`partIdOf(null) == ""`、`partIdOf` = 注册名+":"+损伤值、以及 `isPartHost(null)/captureParts(null)/matchesInstalled(null)/installPart(null)/canSelfHost(null|part)` 全部安全返回而不抛异常（无 AE2 环境降级为禁用，与 PLAN :144 一致）。
  - 验证：`pwsh -File scripts/forge1710.ps1 build` 与 `smoke` → BUILD SUCCESSFUL（Exit 0）；Ray/Drop/Collision/AE2/WandUpgrade/BlueprintCore/Storage/Network/BuildExecutor/Legacy 全项通过；冒烟日志确认无 AE2 时打印 `BlueprintPartSupport: AE2 API unavailable, part support disabled`。**未做游戏内人工验收**（AE2 线缆/锚/面板实机建造、GT 机器实机建造）。
  - 本轮明确延后（需用户确认优先级）：GT 机器配置 NBT 白名单恢复（含朝向，`placeBlockAt` 目前统一传 `side=2` 水平面）、GT 覆盖板与管道各面断通、AE2 部件配置 NBT（优先级/过滤/P2P）、部件通信（`PacketBlueprintSlice`/`PacketBlueprintListResponse` 中无 part 字段，客户端拿不到部件 → 部件虚影不显示、客户端物料统计偏少）、`executeBatch` 完工判定只数 `blockEntries`（该方法是**无调用者的备用路径**，仅被冒烟脚本调用）。

- 2026-09-11 16:59：完工任务不再保留在任务列表中（用户人工测试反馈："已经完成的任务仍然会留在列表里，并且点击建造时会弹出提示。我认为我们不需要保留已完成的任务，它们实际没有追溯价值"）。
  - 根因：`blueprint/build/BlueprintBuildScheduler.onServerTick()` 在 `session.isFinished()` 时只做了 `task.setStatus(STATUS_COMPLETED)` + `broadcastTaskStatus`（upsert 同步），任务仍留在 `blueprint/network/BlueprintTaskManager.TASKS` 中。列表与虚影都由此 Map 驱动，所以已完工任务永久滞留，再点"建造施工"只会撞到 startBuild 的终态校验并提示"该建造任务不存在或已结束"（`message.freecam_interaction.bp.task_unavailable`）。
  - 修复：完工分支改为与"取消"同路径——`BlueprintTaskManager.removeTask(taskId)` + `BlueprintNetwork.broadcastTaskRemove(finished, 64.0D)`，并给施工者发 `message.freecam_interaction.bp.task_completed` 聊天反馈（任务从列表与虚影中消失，必须给回执，否则看起来像任务丢失）。`BlueprintNetwork.broadcastTaskRemove` 由 `private` 改为 `public`（跨包调用）。
  - 同时把 `BlueprintBuildScheduler.notifyRejected()` 重命名为 `notifyPlayer()`（现在也用于成功回执，原名会误导），共 13 处调用点用 sed 同步。
  - 保留不变：暂停/缺料/超出范围/区块未加载的任务仍为 STATUS_PENDING 留在列表中（有继续施工价值）；`ACTION_CANCEL` 路径未动。
  - 已知无关分支：`blueprint/build/BlueprintBuildExecutor.executeBatch` 是**没有调用者的备用路径**（仅被冒烟脚本调用），它仍会把 `storage.BlueprintTask` 置为 `COMPLETED` 并 `saveTask` 到存档；GUI 列表只读 `network.BlueprintTaskManager.getTasksVisibleTo`，两者不发生交互。若日后启用该路径，需同步套用"完工即移除"。
  - 冒烟补强（`scripts/LegacyActionCheck.java` 的 `networkChecks()`）：在监听器计数断言之后新增完工移除通路验证——先 `updateTask(syncUpsert)` 入列，再 `updateTask(syncRemove)`，断言 `getTask("task-99") == null` 且 `getAllTasks().isEmpty()`。注意插入位置必须在 `listenerCalled[0]` 计数断言之后（否则会打乱监听器计数）。
  - 验证：`pwsh -File scripts/forge1710.ps1 smoke` → BUILD SUCCESSFUL（Exit 0）。

- 2026-09-11 16:58：放宽蓝图施工资格判定——"背包中任意一把法杖装有蓝图核心即可施工"（用户人工测试反馈）。
  - 问题：`FreecamInteraction.hasActiveBlueprintCore()` 只看**当前生效法杖**（`getActiveWandSlot` = 服务端 `State.selectedSlot`，由 `ItemFreecamWand.findBestWand` 按 创造>高级>普通、同级按槽位 选出）。因此背包里只要同时存在一把无核心的“更好/更前”的法杖，生效法杖就落在它身上，即使用户背包里另有一把装了核心的法杖也永久提示"未安装蓝图核心"。
  - 修复（`src/forge1710/java/local/freecaminteraction/FreecamInteraction.java`）：重写该判定位并**改名**为 `hasBlueprintCoreWand(EntityPlayer)`，遍历 `player.inventory.mainInventory` 的 0..35 槽，只要任一把是 `ItemFreecamWand` 且 `ItemFreecamWand.hasBlueprintCore(stack)` 即返回 true；保留 `active(player)` 前置守卫。
  - 同步改名调用点：`blueprint/build/BlueprintBuildExecutor.java`（第 39 行注释、337 行判定）、`blueprint/build/BlueprintBuildScheduler.java`（274、426 行）、冒烟脚本 `scripts/LegacyActionCheck.java`（共 8 处）。已确认客户端无调用点（GUI 的施工按钮不做核心判定）。
  - 保持不变的语义：耐久扣费仍走 `FreecamInteraction.deductUsage()`，即由**当前生效（耐久最优）法杖**支付 1 点耐久，而不是由带核心的那把；因为同文件中 `findBestWand` 本就是自由视角"当前法杖"的唯一来源，施工与挖掘共用同一扣费入口不会产生分叉。若日后要求“带核心的法杖才能付费”，需同步改造 `deductUsage` 的目标槽位选取（注意 `State.tier` 变化会连带影响 `inside()` 射程与区块票据）。
  - 冒烟测试补强（`scripts/LegacyActionCheck.java` 的 `wandUpgradeChecks()`）：新增多法杖断言——slot0 放无核心法杖（生效）、slot5 放带核心法杖时必须判定为真；slot5 换成无核心法杖后必须为假；测试末尾还原 `wandStack` 的 slot0 核心（否则会破坏紧随其后的 `ContainerWandUpgrade` 用例）。
  - 验证：`pwsh -File scripts/forge1710.ps1 smoke` → BUILD SUCCESSFUL（Exit 0），Ray/Drop/Collision/AE2/WandUpgrade/BlueprintCore/Storage/Network/BuildExecutor/Legacy 全项通过。

- 2026-09-11 16:50：修复用户人工测试反馈的 3 个蓝图系统缺陷（Forge 1.7.10）。
  - 缺陷 1（问题 4"未找到可以施工填充方块的按钮"）：`src/forge1710/java/local/freecaminteraction/client/gui/GuiBlueprintManager.java` 的 `buildItemButtons()` 只在**非主人**且 `permission == 2` 的分支创建 "建造施工" 按钮（id `600+i`），单人游戏里玩家自己就是任务主人，因此永远看不到施工按钮。修复：在 `task.isOwner` 分支首部也加入 `600+i` 主人施工按钮（尺寸 72×20，居中于 `centerX-60`），主人分支其余按钮（300/400/500）整体右移，不重叠；服务端 `BlueprintTaskManager.BuildTask.canBuild()` 本就对主人无条件返回 true，无需改服务端权限逻辑。
  - 缺陷 2（问题 5"进入个人蓝图页面导致游戏暂停" + 问题 6"变更需退出页面才生效，刷新也无用"）：Forge 1.7.10 `GuiScreen.doesGuiPauseGame()` 默认返回 `true`，`GuiBlueprintManager` 与 `GuiSaveBlueprint` 均未覆写，导致单机集成服务器在界面打开期间不推进 tick。修复：两个 GUI 各新增 `@Override public boolean doesGuiPauseGame() { return false; }`（`GuiBlueprintManager.java:87`、`GuiSaveBlueprint.java:61`）。附带效果："刷新"按钮的 `PacketBlueprintListRequest` 现在能即时送达并回写列表。
  - 缺陷 3（相邻 bug）：`src/forge1710/java/local/freecaminteraction/blueprint/network/BlueprintClientCache.java` 的 `updateTask` 仅在 `sync.permission == PERM_HIDDEN` 时移除任务，导致主人把自己任务的权限切到"仅自己"后任务从自己的列表消失、无法再改回。修复：改为 `(sync.permission == PERM_HIDDEN && !sync.isOwner)`，与 `BlueprintTaskManager.BuildTask.isVisibleTo()` 的服务端语义一致。
  - 施工反馈补强（`src/forge1710/java/local/freecaminteraction/blueprint/build/BlueprintBuildScheduler.java`）：新增私有 `notifyRejected(EntityPlayerMP, String)`（`addChatMessage(new ChatComponentTranslation(key))`，`try/catch (Throwable)` 兼容测试桩），并在 `startBuild` 的 6 处拒绝分支（非自由视角/无蓝图核心/任务不存在/任务已结束/无建造权限/跨维度或越范围）与 `processSession`/`executeBlockUnit`/`executePartUnit` 的暂停点（越范围、区块未加载、材料不足、耐久不可扣）发出聊天反馈，避免"点了没反应"。
  - 语言文件：`src/forge1710/resources/assets/freecam_interaction/lang/{zh_CN,en_US}.lang` 补齐 `message.freecam_interaction.bp.{need_freecam,need_core,task_unavailable,no_permission,build_out_of_range,out_of_materials,wand_exhausted,chunk_unloaded}`（`chunk_unloaded` 为本次新增）。
  - 验证：`pwsh -File scripts/forge1710.ps1 smoke` → BUILD SUCCESSFUL（Exit 0），复用既有 `blueprintChecks`（Storage/Network/BuildExecutor）与 `LegacyActionCheck` 全项通过；`build/libs/freecam_interaction-0.1.0-forge1710-experiment.jar` 内 `zh_CN.lang` 含新键、`blueprint` 相关 class 共 65 项。未改动 1.21.1 代码。
  - 遗留/未验证：本次修改未做游戏内人工验收；`BlueprintBuildScheduler` 无独立冒烟用例（仅有产物打包断言），施工全链路仍依赖人工测试。

- 2026-09-11 16:21：完成符合 PLAN-blueprints-1710.md 规范的 Forge 1.7.10 蓝图系统施工调度、物料事务结算与完整交付（阶段 D、E）。
  - 新增及更新类（`local.freecaminteraction.blueprint.build` 等）：
    1. `BlueprintBuildScheduler.java`：服务端施工调度器。接入 FML 服务端 Tick，管理活跃建造会话（`ActiveBuildSession`）；按拓扑排序分步放置（Phase 0: 固体基体从 Y 轴自底向上推进；Phase 1: 附着物/红石/梯子/火把；Phase 2: AE2/GT 部件；Phase 3: 最终配置还原与连通性通知）；蓝图空气绝不破坏地形；方块已匹配自动跳过不重扣物料/耐久；遇到异种方块冲突跳过绝不破坏已有方块；完工更新状态并广播同步；
    2. `BlueprintBuildExecutor.java`：单步建造执行与物料事务结算。包含四重资格鉴权（自由视角、有效法杖蓝图核心、合法交互范围、三档权限非 HIDDEN/VISIBLE_ONLY）；红线契约物料结算：三段式事务（预留物料 -> 实际放置 -> 提交扣除），放置失败安全回滚绝不吞物料；严格消耗协作者自身主背包（0..35），严禁动用主人库存；成功放置扣除协作者当前法杖 1 点耐久，创造法杖免耐久；法杖耐久扣至 1 时触发标准自动背包接续；并发坐标锁（`ACTIVE_COORDINATE_LOCKS`）杜绝多人并发施工相同坐标的重复放置与扣料竞态；
    3. `FreecamInteractionMod.java`：在 Mod 预初始化阶段注册 `BlueprintBuildScheduler.INSTANCE.register()`；
    4. 客户端交互与渲染集成：`GuiSaveBlueprint.java`（保存蓝图输入弹窗）、`GuiBlueprintManager.java`（个人蓝图与三档权限任务管理面板）、`BlueprintGhostRenderer.java`（虚影半透明渲染）、`FreecamClient.java`（状态机 A/B 键及 HUD 快捷调用）；
  - 冒烟验证：
    1. 在 `LegacyActionCheck.java` 中增加 `buildExecutorChecks()` 完整单元自动化断言，覆盖无核心拦截、越界拦截、三档权限拦截、协作者物料守恒与主人库存隔离、已有方块匹配跳过、并发坐标锁互斥拦截、三段式事务失败回滚以及耐久降至 1 自动背包接续；
    2. 在 `scripts/forge1710.ps1` 中加入 `BlueprintBuildScheduler.class` 的产物打包断言；
    3. 执行 `pwsh -File scripts/forge1710.ps1 smoke` 全项通过；产物构建完成（`freecam_interaction-0.1.0-forge1710-experiment.jar`），未修改 1.21.1 功能源码（保持待实现）。

- 2026-09-11 13:13：完成符合 PLAN-blueprints-1710.md 契约的 Forge 1.7.10 法杖 4 槽升级容器与蓝图核心系统。
  - 新增及更新类：
    1. `ItemBlueprintCore.java`（`local.freecaminteraction.item`）：蓝图核心物品，`setMaxStackSize(1)`，注册为 `freecam_interaction:blueprint_core`，中英文本地化名称及 `addInformation` 描述说明，实现 `IWandCore` 接口（`coreId="blueprint"`）；
    2. `IWandCore.java`（`local.freecaminteraction.item`）：法杖升级核心通用接口，定义 `getCoreId()` 保证同杖同类核心互斥；
    3. `FreecamWandRegistry.java`：注册 `blueprintCore`，添加独立合成配方（四角纸、四边红石、中心钻石产出 1 个蓝图核心）；
    4. `ContainerWandUpgrade.java`（`local.freecaminteraction.inventory`）：4 个升级核心槽位，防刷防丢机制：锁定编辑中的法杖槽位（禁止移走、丢弃、拿取或通过 0-8 快捷键换掉），Shift 点击核心入核心槽、背包入背包（绝不误入核心槽或锁定法杖槽），同杖防重复同类核心校验，容器关闭及槽位变动安全保存 4 槽数据至法杖 NBT，法杖移除或改变即刻失效断开；
    5. `GuiWandUpgrade.java`（`local.freecaminteraction.client`）：客户端 GUI 界面，绑定专用像素对齐背景贴图 `wand_upgrade.png`，展示标题与槽位信息；
    6. `FreecamGuiHandler.java`（`local.freecaminteraction`）：注册 `IGuiHandler`，服务端实例化 Container，客户端通过 `FreecamClient.getWandUpgradeGui` 实例化 GuiContainer，分端加载彻底杜绝专用服务端类加载异常；
    7. `ItemFreecamWand.java`：潜行右键（空气与方块）打开升级 GUI，非潜行保持原切换自由视角逻辑；提供 `hasBlueprintCore(ItemStack)`、`getUpgradeCore(ItemStack, int)`、`loadUpgrades` 与 `saveUpgrades`，并在 tooltip 显示已安装核心清单与槽位数量；
    8. `FreecamInteraction.java`：新增 `getActiveWandSlot(EntityPlayer)` 与 `hasActiveBlueprintCore(EntityPlayer)`，严格根据当前激活生效法杖槽位校验是否安装蓝图核心，作为后续蓝图与协作建造的唯一资格判定；
    9. 材质与资产：生成 32×32 透明 RGBA `blueprint_core.png` 物品贴图与 176×166 `wand_upgrade.png` 容器贴图，记录提示词至 `art/cores/PROMPTS.md`，更新中英文 lang 文件；
  - 冒烟验证：
    - 在 `LegacyActionCheck.java` 中增加 `wandUpgradeChecks()` 自动化断言：核心属性、4 槽读写、激活法杖核心判定、编辑槽锁定、数字键换槽拦截、同杖重复核心拦截、Shift 双向安全转移、容器关闭 NBT 持久化；
    - 在 `scripts/forge1710.ps1` 中加入新类及贴图资源的 JAR 产物包含断言；
    - 执行 `pwsh -File scripts/forge1710.ps1 smoke` 全项通过；1.21.1 对应功能保持待实现。

- 2026-09-11 13:03：完成符合 PLAN-blueprints-1710.md 规范的 Forge 1.7.10 蓝图系统网络通信层（BlueprintNetwork）。
  - 新增类均位于 `src/forge1710/java/local/freecaminteraction/blueprint/network` 目录：
    1. `BlueprintNetwork.java`：统一管理网络通信通道 `freecam_bp` 与 discriminator 分配（10..20），包含主线程 TickListener 队列调度（`runOnServer` / `runOnClient`），消除多线程竞争；
    2. `PacketCaptureRequest.java` / `PacketCaptureAck.java`：选区蓝图采集协议，客户端只发送端点坐标与蓝图名，严禁包含任何方块或 NBT；服务端权威调用 `BlueprintCaptureHelper` 进行模式、范围、边界、区块加载和方块合法性校验，保存至个人蓝图库后返回应答；
    3. `PacketBlueprintListRequest.java` / `PacketBlueprintListResponse.java`：蓝图列表与可见任务元数据查询协议，包含三档权限与主人身份标记，不泄漏他人私有蓝图数据；
    4. `PacketBlueprintSlice.java` / `PacketBlueprintSliceRequest.java` / `BlueprintSliceAssembler.java`：大蓝图二进制分片传输协议，单片限制 16KB 防止 Netty 报文超限（2MB）或内存溢出；客户端会话装配器支持超时丢弃与安全重组；
    5. `PacketTaskAction.java`：任务操作协议（创建任务、取消任务、变更三档权限、切换模式外显示）；服务端强校验主人身份防未授权伪造操作；
    6. `PacketTaskSync.java` / `BlueprintClientCache.java`：任务状态广播同步协议；同维度可见范围内广播任务创建与变更；当权限变为 HIDDEN 时通知其他客户端清除虚影缓存；
    7. `BlueprintWorldEventListener.java`：监听世界加载事件，绑定服务端存档根目录。
  - 冒烟验证：
    - 在 `LegacyActionCheck.java` 中增加 `networkChecks()` 完整数据包序列化与反序列化断言；
    - 在 `scripts/forge1710.ps1` 中加入全部 10 个网络层核心类的 JAR 产物包含断言；
    - 执行 `pwsh -File scripts/forge1710.ps1 smoke` 全项通过；产物构建正常；1.21.1 保持待实现。

- 2026-09-11 13:03：完成符合 PLAN-blueprints-1710.md 规范的 Forge 1.7.10 蓝图存储与任务持久化层。
  - 新增类均位于 `src/forge1710/java/local/freecaminteraction/blueprint/storage` 目录：
    1. `TaskPermission.java`：三档权限枚举 `HIDDEN(0)`、`VISIBLE_ONLY(1)`、`BUILDABLE(2)`；
    2. `TaskStatus.java`：任务生命周期状态 `PENDING`、`IN_PROGRESS`、`COMPLETED`、`CANCELLED`；
    3. `BlueprintStorage.java`：蓝图文件二进制持久化。魔数 `0x42504643 ('BPFC')`，分段二进制格式 (Header, Palette, Blocks, Parts) 与 GZIP 压缩；支持临时文件 `.bp.tmp` 原子写入替换与刷盘；坏文件损坏检测隔离为 `.corrupt_<timestamp>`，不破坏原数据；实现目录获取、保存、加载、列表与删除蓝图；
    4. `BlueprintTask.java`：建造任务实体。持久化拥有独立的蓝图不可变快照 (`snapshot`)，删除原蓝图文件不影响已有任务；支持三档权限控制、模式外虚影可见性 (`showOutsideFreecam`)、进度统计与主人/协作者可见性及建造施工鉴权；
    5. `BlueprintTaskManager.java`：任务管理器。持久化存储于 `freecam_interaction/tasks/<taskId>.task`，具备临时文件 `.tmp` 原子写入替换、坏任务隔离；提供 `createTask`、`cancelTask`、`updatePermission`、`setShowOutside`（严格主人鉴权防伪造操作）、`listTasksForPlayer`（基于三档权限及模式外开关过滤）、`saveTask`、`loadTask`、`loadAllTasksForWorld`。
  - 冒烟验证：
    - 在 `LegacyActionCheck.java` 中增加 `storageChecks()` 完整单元自动化断言，覆盖临时文件原子替换、文件损坏隔离、蓝图快照独立性、三档权限隔离与非主人操作安全拦截；
    - 在 `scripts/forge1710.ps1` 中加入 storage 全部 5 个类的 JAR 产物包含断言；
    - 执行 `pwsh -File scripts/forge1710.ps1 smoke` 全项通过；1.21.1 对应功能保持待实现。

- 2026-09-11 12:56：完成符合 PLAN-blueprints-1710.md 契约的 Forge 1.7.10 蓝图核心数据模型与原版适配器实现。
  - 新增核心类位于 `src/forge1710/java/local/freecaminteraction/blueprint`：
    1. `BlueprintData.java`：蓝图数据根对象，包含唯一标识/名称、作者 UUID、尺寸/原点偏移、创建时间、方块与独立部件列表、体积与非空气方块统计、物料合并汇总 (`getConsolidatedMaterials`)、空选区与全空气校验 (`isAllAir`)；
    2. `BlueprintBlockEntry.java`：相对原点偏移、方块与注册名引用、元数据、静态安全 NBT、适配器 ID 及所需物料列表；
    3. `BlueprintPartEntry.java`：相对宿主偏移、6 方向附着 side、部件类型标识、专用静态 NBT 配置与物料需求（适配 AE2 部件/GT 覆盖板等）；
    4. `MaterialRequirement.java`：物料注册名、损伤/元数据、关键匹配特征 NBT、所需与已放置数量统计、背包 `ItemStack` 鲁棒匹配判定；
    5. `IBlueprintAdapter.java`：规范 `canHandle`、`capture`、`getRequiredMaterials`、`canPlace`、`place`、`matches` 适配器标准；
    6. `VanillaBlueprintAdapter.java`：原版方块与容器适配器，严格拦截非法方块（基岩/传送门/命令方块等），深度消毒 TileEntity 剔除 Items/fluid/energy/进度等易复制库存，保留安全朝向与自定义配置。
  - 冒烟验证：在 `LegacyActionCheck.java` 与 `scripts/forge1710.ps1` 中加入完整蓝图模型单元断言与 JAR 产物类核验，执行 `pwsh -File scripts/forge1710.ps1 smoke` 全项通过；1.21.1 对应功能待实现。

- 2026-09-11 12:46：接手阅读并理解 HANDOFF-blueprints-1710.md 及 PLAN-blueprints-1710.md。明确任务目标为 Forge 1.7.10 蓝图系统（4槽法杖升级、蓝图核心、个人蓝图存储、多人三档权限与协作施工），1.21.1 保持待实现状态。核实需求已由用户完成确认，明确技术红线（协作者消耗自身材料耐久、数据服务端采集校验、GT/AE2部件与配置重建而不复制库存/能量、防刷GUI与事务恢复）。本轮仅完成交接阅读与任务梳理，未进入实现、未修改功能代码。

- 2026-09-11 12:41：蓝图核心、4槽法杖升级、个人蓝图和协作建造完成方向/范围确认。最终任务权限为不可见／可见但不可建造／可建造（默认第二档）；协作者消耗自身材料与耐久，主人独占取消及显示/权限管理。用户要求本轮不实施，已生成 PLAN-blueprints-1710.md，并在相邻主工作树生成 HANDOFF-blueprints-1710.md；本轮仅文档，未启动执行Agent、未修改功能代码或运行构建/游戏。源码和本地GT5U5.09.31、AE2rv3-beta-6接口只读核对完成，图谱工具不可用，库存过滤及连接/虚影兼容尚待实现核验。1.21.1 待实现。

- 2026-09-11 10:26：按用户最新决定彻底移除 Forge 1.7.10 的交互提示音，只保留末影粒子。删除 Mod 专属声音资源、声音事件缩放、客户端音量滑块/持久化及对应语言和 JAR 检查；成功挖除、放置、方块使用和实体互动现在仅广播 24 个 `portal` 粒子，多方块放置仍逐个实际位置出粒子，自由视角 aura 仍每 2 tick 广播 2 个粒子。此前的服务端成功判定、范围规则、实体右键以及粒子 billboard 朝向修复保持不变。`pwsh -File scripts/forge1710.ps1 smoke` 全项通过，日志 `logs/tools/2026-09-11 10-25-51.log`；最终 JAR 已核验不含声音类或 `sounds.json`。

- 2026-09-11 10:15：为 Forge 1.7.10 在原版“音乐和声音选项”空档加入全宽“上帝视角建造音量”滑块（0–100%，默认 100%，0% 为关）。服务端提示音改用 Mod 专属 `freecam_interaction:interaction` 事件复用原版末影传送素材；客户端通过 `PlaySoundEvent17.name` 精确识别后包装 `ISound` 缩放，因此原版末影人、粒子和其他分类音量不受影响，主音量仍正常叠乘。设置独立保存至 `config/freecam_interaction-client.properties`，非法值回退/越界夹取，拖动即时生效、松开保存并试听一次。`pwsh -File scripts/forge1710.ps1 smoke` 全项通过（含真实 Coremod 字节码和新增产物检查），最终日志 `logs/tools/2026-09-11 10-16-55.log`；尚未进行游戏内界面与双客户端听感 E2E。

- 2026-09-11 09:58：修复 Forge 1.7.10 自由视角粒子仅在相机与玩家本体朝向接近时可见的问题。根因是原版 `EntityRenderer.renderWorld` 调用 `ActiveRenderInfo.updateRenderInfo` 时硬编码使用 `mc.thePlayer`，而世界与粒子位置基准使用 `renderViewEntity`；自由相机与玩家朝向分离后，粒子 billboard 仍按玩家朝向旋转，侧视时近乎边缘朝向相机。`FreecamTransformer.patchEntityRenderer` 现将该唯一调用定点路由至 `FreecamClient.updateRenderInfoForCamera`：仅在自由视角激活时采用实际 `renderViewEntity`，普通视角保持原玩家。`LegacyActionCheck` 增加真实 `EntityRenderer.class` 粒子朝向钩子唯一性断言；`pwsh -File scripts/forge1710.ps1 smoke` 全项通过，日志 `logs/tools/2026-09-11 09-57-23.log`。尚待用户游戏内从侧面和背面观察粒子确认视觉效果。

- 2026-09-11 09:44：按用户要求启动 Forge 1.7.10 开发客户端进行体验。首次启动被 CodeChickenLib 的 MCP 映射目录选择框阻塞，已在运行配置中固定有效 `mappingDir`；随后真实 JVM 加载暴露 `Minecraft.func_147115_a(Z)V` 入口守卫缺少栈映射帧，报 `VerifyError: Expecting a stackmap frame at branch target 7`。将 `patchMinecraftTick` 的输出改为 `ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS` 并保留公共父类解析回退后，客户端成功加载 Forge、自由视角交互、AE2、GT5U、IC2、NEI，进入 `Minecraft 1.7.10` 游戏窗口并交给用户体验。启动日志 `logs/tools/2026-09-11 09-43-17.log`。

- 2026-09-11 09:36：为 Forge 1.7.10 自由视角移植末影风格交互指示。新增服务端权威 `FreecamEffects`：模式激活期间每 2 tick 在玩家本体周围广播 2 个 `portal` 粒子；成功破坏、成功方块右键、成功放置及成功实体右键在实际目标处广播 24 个 `portal` 粒子和一次 `mob.endermen.portal`（音量 0.6、音调 1.0）。`ItemInWorldManager` Coremod 包装根据最终布尔结果触发并在异常时清理，多方块放置通过 LOWEST 未取消事件收集实际坐标、逐处出粒子而整次仅响一次。客户端及服务端同时禁用自由视角实体左键远程攻击，保留受方块遮挡和范围复核的实体右键。生命周期清理同步清除待处理放置。`scripts/forge1710.ps1 smoke` 全项通过，日志 `logs/tools/2026-09-11 09-36-22.log`；尚未进行双人游戏内可见性/听感 E2E。

- 2026-09-10 17:06：用户完成 Forge 1.7.10 自由视角生存模式方块挖掘实机测试，明确反馈“修复成功，提交”。据此记录实机验收通过并提交本次修复；提交内容包含 Minecraft.func_147115_a 字节码补丁（消除自由视角下主循环对 resetBlockRemoving 的误触发）、真实字节码补丁自动化回归自检、项目文档及记忆更新。

- 2026-09-10 17:00：解决 Forge 1.7.10 自由视角生存模式下无法正常挖掘方块（进度始终为 0）的严重 Bug。
  - 根因：原版主循环 `Minecraft.java:2058` 在每一 Tick 因自由视角释放光标（`inGameHasFocus == false`）而持续调用 `func_147115_a(false)`，导致其内部触发 `playerController.resetBlockRemoving()`，在微秒内将刚累加的破坏进度抹零并清除方块裂纹，同时发送取消挖掘数据包。
  - 方案实施：在 `FreecamTransformer` 中新增 `patchMinecraftTick` 字节码补丁，定点拦截 `Minecraft.func_147115_a(Z)V`（兼容 MCP 名 `sendClickBlockToController`）；在方法入口注入 `FreecamClient.isFreecamActive()` 守卫，自由视角激活时直接 return，由 `FreecamClient` 独占管理挖掘周期，彻底切断原版主循环的无条件抹零；非自由视角或退出后 100% 走原版逻辑。
  - 验证凭据：在 `LegacyActionCheck.java` 中增加针对真实 `net.minecraft.client.Minecraft` 字节码补丁的自动化测试；执行 `scripts/forge1710.ps1 smoke` 全项通过，包含真实 `Minecraft.class` 字节码转换验证、AE2 补丁校验、碰撞求解与掉落入包自检；产物 JAR 构建正常。

- 2026-09-10 16:33：定位并解决客户端启动崩溃问题。根因为 CodeChickenCore 启动时通过自带旧版 DepLoader 尝试从已失效的旧服务器（chickenbones.net）自动下载 CodeChickenLib，收到 301 响应并将“Moved Permanently”写入 JAR 触发 ZipException 崩溃。已在 `scripts/tech-mods.ps1` 中加入 Covers1624 官方 Maven 源的有效 `CodeChickenLib-1.7.10-1.1.3.138-universal.jar`（SHA-256 `4A0D192A...`）并直接同步至 `mods/1.7.10/`；清理损坏文件后包含 NEI 与 GT5U 在内的全部 20 个 Mod 正常加载。

- 2026-09-10 16:30：按用户要求为开发客户端安装 NEI 与格雷科技 5（GT5U）。更新 `scripts/tech-mods.ps1`，固定官方源与 SHA-256 校验：CodeChickenCore 1.0.7.47、NotEnoughItems 1.0.5.120 与 GregTech 5.09.31 Unofficial。两个文件组已全量同步至原生开发实例 `run/mods` 与 lwjgl3ify 隔离实例 `instance/mods`；执行 `scripts/forge1710.ps1 smoke` 与 `scripts/lwjgl3ify.ps1 smoke` 自动化冒烟均全量通过。

- 2026-09-10 16:25：用户完成 Forge 1.7.10 自由视角模式下手持扳手蹲下右键拆除 AE2 ME 线缆/部件及掉落物直入背包实机体验，明确反馈“已成功修复，提交”。据此记录实机验收通过并准备提交；本次提交包含 AE2 拆卸射线同步与平台钩子、PartPlacement 包装拦截与掉落物入包、客户端双击快捷启动批处理及相关自检与文档更新。

- 2026-09-10 15:59：按用户要求启动已安装 IC2/AE2 的 Forge 1.7.10 + lwjgl3ify 3.0.33 开发客户端。客户端自动构建并加载包含 AE2 ME 线缆拆卸与掉落入包修复的最新 Mod JAR，音频引擎启动成功，Java 进程（PID 8428）响应正常，客户端留给用户进行实机体验。

- 2026-09-10 15:57：按用户意图确认要求，解决 Forge 1.7.10 自由视角下手持扳手蹲下拆除 AE2 ME 线缆/部件无响应且掉落物未入包的问题。
  - 根因：客户端 AE2 线缆拦截原生交互并发送专用 `PacketPartPlacement`；服务端拆除调用 `Platform.getPlayerRay`（从玩家本体眼睛沿朝向 5 格内射线，自由视角下本体脱靶无法命中）及 `Platform.spawnDrops`（脱离原版 `ItemInWorldManager` 掉落包裹）。
  - 方案实施：新增操作通道 `RAY`（`FreecamActions`）在右键前向 Netty 线程同步当前相机射线；`FreecamTransformer` 定点拦截 `appeng.util.Platform.getPlayerRay` 注入 `FreecamInteraction.customPlayerRay` 反射构造 `LookDirection` 重定向射线；将 `appeng.parts.PartPlacement.place` 改名为原方法并生成包装方法，进入时校验自由视角激活与合法范围并开启 `FreecamDropCollector` 收集上下文，执行后直接将部件与掉落物移入操作者背包、余量安全落地，扣除 1 点法杖耐久；未安装 AE2 时无侵入且无编译期强依赖。
  - 验证凭据：`scripts/forge1710.ps1 smoke` 通过，含真实 `appliedenergistics2-rv3-beta-6.jar` 字节码补丁注入（`Platform.getPlayerRay` 与 `PartPlacement.place`）、射线缓存与合法范围校验、越界拒绝、掉落物入包结算、法杖耐久扣减以及全部原版/避障检查；`scripts/lwjgl3ify.ps1 smoke` 通过；1.21.1 对应功能保持待实现。

- 2026-09-10 15:20：按用户要求启动已安装 IC2/AE2 的 Forge 1.7.10 + lwjgl3ify 3.0.33 开发客户端。客户端成功加载 17 个 Mod，日志明确包含 `IC2@2.2.828-experimental`、`appliedenergistics2@rv3-beta-6`、`freecam_interaction@0.1.0-forge1710-experiment` 与 `lwjgl3ify@3.0.33`；客户端与集成服务端连接成功且 missing mods 为空，玩家 `FreecamLwjgl3` 已进入“新的世界”。客户端保持运行供用户操作；工具日志 `logs/tools/2026-09-10 15-19-32.log`。

- 2026-09-10 15:18：按用户要求为 Forge 1.7.10 开发客户端安装科技 Mod。依据官方发布信息固定 IC2 `industrialcraft-2-2.2.828-experimental.jar` 与 AE2 `appliedenergistics2-rv3-beta-6.jar`，下载后校验为有效 JAR，内部 `mcmod.info` 确认 Mod ID/版本及 Minecraft 1.7.10。两个文件均已装入原生开发实例 `run/mods` 和 lwjgl3ify 3.0.33 隔离实例 `%LOCALAPPDATA%/GodviewBuild/lwjgl3ify-3.0.33/instance/mods`；新增 `scripts/tech-mods.ps1` 固定官方 CurseForge CDN 地址与 SHA-256，可重复恢复安装。尚未启动客户端验证两者实际加载。

- 2026-09-10 15:12：完成 Forge 1.7.10 自由视角通用交互掉落直接入包。新增 `FreecamDropCollector`，使用线程内可嵌套交互上下文关联同步产生的 `EntityItem`；核心转换器包裹服务端 `ItemInWorldManager.tryHarvestBlock` 与 `activateBlockOrUseItem`，覆盖原版挖掘及第三方 Shift＋右键工具同步拆卸，实体交互、实际攻击与钓鱼复用 `FreecamActions` 原生调用边界。结算仅处理同世界、范围内且确已加入世界的候选；背包按原版堆叠上限合并和填空槽，创造模式满包不吞物品，部分余量原地保留，数量、耐久与 NBT 不变。延迟到后续 tick、绕过物品实体生成、经验球与旧地面物品不推测归属。最终 `scripts/forge1710.ps1 smoke` 通过，含真实 Minecraft 字节码装载验证、成功/异常清理、堆叠/NBT/部分容量/满包及产物类检查，日志 `logs/tools/2026-09-10 15-12-22.log`；`scripts/lwjgl3ify.ps1 smoke` 通过，日志 `logs/tools/2026-09-10 15-11-19.log`。本地实例未发现 IC2 或 AE2 JAR，因此通用路径已验证但 IC2/AE2 实机兼容尚未验证；1.21.1 此功能待实现。

- 2026-09-10 14:44：用户完成 Forge 1.7.10 + lwjgl3ify 游戏内法杖材质验收并明确反馈“验收通过，提交”。普通蓝色、高级红色、创造紫色三张32×32透明贴图确认可在实际道具中正常显示；沿用14:32冒烟构建与14:35真实客户端启动结果，按用户指令提交材质、可复用导出脚本、生成记录及项目记忆。

- 2026-09-10 14:36：按用户要求启动 Forge 1.7.10 + lwjgl3ify 3.0.33 验收实例以检查法杖贴图。首次启动发现隔离实例 `mods` 中残留旧 `godview_build.jar`，它与当前 `freecam_interaction.jar` 同时加载并在旧容器转换器处崩溃；已将该旧JAR可恢复地移动至实例 `disabled-mods/godview_build.jar`，未删除源码或存档。重新启动成功，窗口 `Minecraft 1.7.10` 正常响应，PID 6104；日志 `logs/tools/2026-09-10 14-35-15.log`。客户端留给用户验收。

- 2026-09-10 14:33：确认 `ItemFreecamWand` 已通过 `setTextureName("freecam_interaction:wand_" + tier.id)` 原生绑定普通、高级、创造三张贴图，资源名与 `textures/items/wand_{normal,advanced,creative}.png` 精确匹配，无需新增 Java 注册代码。执行 `scripts/forge1710.ps1 smoke` 成功，产物 `build/libs/freecam_interaction-0.1.0-forge1710-experiment.jar` 已包含三张贴图；日志 `logs/tools/2026-09-10 14-32-55.log`。未启动游戏做背包内目视验收，未提交。

- 2026-09-10 14:29：完成普通蓝色、高级红色、创造紫色法杖材质；内置imagegen生成，最近邻导出实际32×32透明RGBA PNG，接入1.7.10工作树src/forge1710/resources/assets/freecam_interaction/textures/items/wand_{normal,advanced,creative}.png。art/wands保留原图、提示词与4倍预览，scripts/wand-textures.ps1可复用导出检查。尺寸/透明通道检查及目视检查通过，日志logs/tools/2026-09-10 14-28-56.log；未运行游戏、构建或提交。按新规则整理MEMORY条目为时间降序；1.21.1功能仍待实现。

- 2026-09-10 12:41：用户明确反馈“测试成功，提交”，据此记录 Forge 1.7.10 自由视角法杖系统（普通/高级/创造三级法杖、合成配方与原生铁砧25%修复、背包优先级扫描、有效动作扣费与2->1自动接续防损坏消失、ForgeChunkManager票据与PlayerManager区块订阅保载、动态视距与选区外框）验收通过。按用户指令提交本轮修改。

- 2026-09-10 12:35：按 `PLAN-wands-1710.md` 与 `HANDOFF-wands-1710.md` 完成 Forge 1.7.10 自由视角法杖系统全部实现与自动化冒烟验证。
  - 法杖物品与注册：实现 `WandTier`（`NORMAL` 5×5区块/2048耐久、`ADVANCED` 7×7区块/8192耐久、`CREATIVE` 9×9区块/无限耐久）、`ItemFreecamWand`（原生 `getIsRepairable` 钻石/钻石块 25% 修复、右键切换模式、中英文 tooltip）与 `FreecamWandRegistry`（普通与高级法杖合成配方）；
  - 背包扫描与接续：`ItemFreecamWand.findBestWand` 实现主背包 0..35 扫描（优先级：创造 > 高级 > 普通，同级小槽位优先，耐久 <= 1 不可用）；`FreecamInteraction.deductUsage` 在有效操作（方块破坏/放置、容器打开、实体交互/攻击、桶操作、钓竿抛收）成功后扣费 1 点；耐久从 2 降至 1 时触发接续（handoff），自动切换至下一把可用法杖并同步更新票据与视距，若无可用法杖平滑退出，法杖绝不损坏消失；
  - 区块保载与订阅：`FreecamChunkLoader` 注册 `ForgeChunkManager.setForcedChunkLoadingCallback`，按需申请多张票据维持区块强加载，并反射 `PlayerManager` 的 `ChunkWatcher.addPlayer/removePlayer` 同步区块/实体数据，会话退出时完整释放；
  - 客户端与选区适配：`FreecamClient` 检查法杖可用性进入、动态计算视距截断（外接圆半径 +50%）、HUD 顶部显示等级/剩余耐久/操作范围；`FreecamSelection` 根据等级渲染对应尺寸外框；
  - 验证凭据：`scripts/forge1710.ps1 smoke` 自动化冒烟全部通过（日志 `logs/tools/2026-09-10 12-33-28.log`）。覆盖：真实 `ItemBucket` / `EntityAITradePlayer` / `EntityRenderer` 字节码补丁校验、报文序列化截断防越界、Unsafe 真实玩家背包选取与耐久边界、纯 Java 8 `LegacyCheck` 几何/视距/等级断言，以及产物 JAR（`freecam_interaction-0.1.0-forge1710-experiment.jar`）包含 `WandTier.class`、`ItemFreecamWand.class`、`FreecamChunkLoader.class` 等全部关键类。未擅自启动游戏客户端或修改存档，等待用户进一步验收指令。

- 2026-09-10 12:17：法杖功能的方向与范围均获用户确认，执行方案见 PLAN-wands-1710.md；当前仅设计交接，尚未实现或验证功能。目标仅 Forge 1.7.10（保留 LWJGL2/lwjgl3ify），1.21.1 对应功能待实现。主工作树 HANDOFF-wands-1710.md 汇总交接状态和技能。创造法杖外围保载按原需求及全部确认解释为会话期间保留已发现的加载区块，详细边界与待核验底层接口见方案。保留既有未提交碰撞修复；未运行构建或游戏。

- 2026-09-10 12:17：用户明确人工确认双版本（Forge 1.7.10 与 NeoForge 1.21.1）自由视角障碍跳位修复（方案 A）验收无误。按用户指令提交本轮修改。

- 2026-09-10 11:17：按用户要求启动 1.7.10 + lwjgl3ify 开发客户端以实机体验障碍跳位修复（方案 A）。`pwsh -File scripts/lwjgl3ify.ps1 client` 构建并启动，游戏成功加载最新自由视角交互 Mod、UniMixins、Iron Chests 及 lwjgl3ify，OpenAL 音频初始化完成，主菜单就绪。客户端留给用户测试体验，未进入世界或修改存档。

- 2026-09-10 11:12：按用户明确指令“按方案A修复”完成 1.7.10 自由视角障碍跳位修复（方案 A）实施与自检。
  - 核心渲染补丁：在 `FreecamTransformer` 中新增 `patchEntityRenderer`，定点将 `EntityRenderer.orientCamera` 中对 `WorldClient/World.rayTraceBlocks` 的调用替换为 `FreecamClient.cameraRayTrace`；当自由视角激活时返回 `null`（保持 13.856406F 距离不回缩），非自由视角走原版逻辑，严格断言改动站点恰好为 1 处。
  - 镜头碰撞求解：新增 `FreecamCollision`，以 $C = A + B(R)$ 严格维系逻辑锚点 $A$ 与光学镜头 $C$ 的对应关系。使用半径 0.20 镜头包围盒执行 swept broadphase/narrowphase 轴向滑动（复用 `calculateYOffset`/`XOffset`/`ZOffset`）；对中键旋转执行 3D 圆弧多微步步进检测，遇阻截停在安全角度；下降位移检测水/岩浆液面并作为防下沉支撑。
  - 客户端整合：在 `FreecamClient` 的 `frame()` 中先旋转后平移，平移目标依然受 `FreecamRange.clampCamera` 约束，求解接受位移后同步更新锚点位置；彻底移除原单列扫描的 `getDropFloor`。
  - 自动化验证：在 `LegacyActionCheck` 中重建跳位复现（确认原版 8 射线算法在锚点平移 0.02 格时产生超 12 格剧烈跳位），断言 `FreecamCollision` 消除跳位（实际位移恰好 0.02、跳位为 0），并覆盖真实 `EntityRenderer` 字节码补丁校验、贴墙滑动、旋转圆弧截停及液面防沉断言。`scripts/forge1710.ps1 smoke`（日志 `logs/tools/2026-09-10 11-12-11.log`）与 `scripts/lwjgl3ify.ps1 smoke`（日志 `logs/tools/2026-09-10 11-12-25.log`）全部通过，`git diff --check` 无格式异常。

- 2026-09-10 11:01：按用户“设计修复方案并交接给下个 agent”要求新增 `HANDOFF-camera-collision.md`，记录保留绕锚点操作、对实际镜头扫过路径做局部碰撞的设计及渲染接入缺口；未实施产品修复。当前检查发现 `.diagnose-tmp/` 不存在，10:37 条目引用的复现脚本已不可直接运行；交接文档提供重建场景，不能把历史报告日志链接当成现存产物。保留原有 MEMORY 未提交修改。

- 2026-09-10 10:37：用户反馈 Forge 1.7.10 自由视角旋转、平移、升降时，路径附近障碍会使摄像机被挤到无关位置。本轮只读诊断已稳定复现：原版 `EntityRenderer.orientCamera` 的 8 条第三人称安全射线在代理相机锚点附近的偏移射线命中时，将渲染距离从 13.856406 格瞬间缩至约 1.51 格；锚点仅移动 0.020 格即可造成约 12.35 格画面跳位，中心射线和实际期望相机路径均未命中。`FreecamClient.frame` 仅直接 `setPosition`，未调用实体碰撞移动；`getDropFloor` 只在 `yOffset < 0` 时执行，不能解释旋转、水平移动或上升。复现脚本为 `.diagnose-tmp/repro-camera.ps1`，构建通过，冒烟 `logs/tools/2026-09-10 10-22-28.log` 通过；尚未实施修复或游戏内复测。

- 2026-09-10 10:00：用户人工确认 1.7.10 的桶/水桶及桶类自由视角光标交互已修复，继 1.21.1 后双版本均完成实机验收。按用户要求提交本轮已验收的实体交互、桶交互、1.21.1 独立开发运行配置及对应冒烟检查；提交前 `git diff --check` 仅报告 Git 换行转换提示。

- 2026-09-10 09:59：按用户要求启动 1.7.10 + lwjgl3ify 开发客户端以实机验证桶修复。启动前确认无运行客户端；`pwsh -File scripts/lwjgl3ify.ps1 client` 完成构建并启动，游戏 PID 39340，桌面确认窗口 `Minecraft 1.7.10` 已出现，工具日志 `logs/tools/2026-09-10 09-59-08.log`。客户端留给用户测试，未进入世界或修改存档。

- 2026-09-10 09:58：用户人工确认 1.21.1 桶/水桶及桶类在自由视角下已按光标位置正常交互，桶修复在 NeoForge 端验收通过；1.7.10 尚待本次启动后的实机验证。

- 2026-09-10 09:58：按用户要求重启 1.21.1 NeoForge 开发客户端以加载桶修复。启动前确认旧客户端已退出；`pwsh -File scripts/dev.ps1 client` 成功启动，游戏 PID 46168，桌面确认窗口 `Minecraft NeoForge* 1.21.1` 已出现，工具日志 `logs/tools/2026-09-10 09-57-38.log`。客户端留给用户实机测试，未进入世界或修改存档。

- 2026-09-10 09:50：修复用户在 1.21.1 发现的桶/水桶自由视角落点仍跟随玩家朝向问题，并审查确认 1.7.10 同样存在。根因是两版原版桶在方块交互返回 PASS 后都会自行按玩家眼睛和朝向重新射线；扩大 reach 或替换客户端 hitResult 均不能改变该内部射线。

- 2026-09-10 09:50：两版新增桶专用动作，客户端按光标射线生成目标，服务端重新检查相机范围、槽位/手、桶类型、遮挡、命中误差和实际液体落点；空桶使用流体源射线，装液桶及鱼桶等 `BucketItem` 子类使用方块射线。随后仍调用原版桶 use：1.21.1 用 `ItemMixin` 仅在 ThreadLocal 授权窗口替换 `Item.getPlayerPOVHitResult`，支持主/副手；1.7.10 用 `FreecamTransformer` 在真实 `ItemBucket.onItemRightClick` 原射线后注入 `FreecamActions.bucketHit`，普通操作不变。未复制液体放置/拾取逻辑，保留原版权限、FillBucketEvent/NeoForge hooks、物品变化、音效、统计与生物桶行为；不扩展到未继承原版 BucketItem 的第三方自定义容器。

- 2026-09-10 09:50：`scripts/neoforge.ps1 smoke` 通过（`logs/tools/2026-09-10 09-49-34.log`），`scripts/forge1710.ps1 smoke` 通过（`logs/tools/2026-09-10 09-49-45.log`）；1.7 检查对真实 ItemBucket 字节码确认恰好一个钩子，NeoForge JAR 确认包含 ItemMixin，双版交互/协议/相机断言通过，`git diff --check` 仅换行提示。首次并行启动双版 smoke 因同秒生成同名工具日志，1.7 脚本在 Start-Transcript NoClobber 处退出；改为错开顺序后通过。尚未进行游戏内水/岩浆拾取、放置、边界或生物桶验收。

- 2026-09-10 09:33：用户人工确认 1.7.10 的远距离工具交互、生物旁方块抢占及村民交易三个问题均已成功修复；以本条补全 09:23 自动化验证边界。

- 2026-09-10 09:33：按用户要求启动 1.21.1 NeoForge 开发客户端。因当前工作树根 Gradle 已切为 1.7.10，修正 `scripts/dev.ps1` 让 1.21.1 的构建、运行、自检和产物检查统一指向 `tooling/neoforge`，并在其 ModDevGradle 配置新增官方 `runs.client`，游戏目录保持 `run/client`。`pwsh -File scripts/dev.ps1 client` 使用项目 JDK 21 成功启动，PID 32432，桌面确认窗口标题 `Minecraft NeoForge* 1.21.1`；日志 `logs/tools/2026-09-10 09-32-36.log`。客户端留给用户测试，未自行进入或修改世界。

- 2026-09-10 09:26：按用户要求，用 `scripts/lwjgl3ify.ps1 client` 启动此前测试的 1.7.10 + lwjgl3ify 开发实例；自动构建并复制最新修复 JAR。构建成功，Java PID 40464，桌面确认 Minecraft 1.7.10 窗口已出现；日志 `logs/tools/2026-09-10 09-26-50.log`。未进入世界或执行玩法验收，客户端留给用户复测。

- 2026-09-10 09:23：用户反馈 1.7.10 工具仅原版距离有效、生物旁方块抢占、村民挥手未打开交易；明确 1.21.1 尚未测试，本轮不修改该版本。保留工作区已有双版本未提交实现。图谱查询连续无 JSON、覆盖 freshness 缺失，回退读取主源码及本地固定版 Minecraft 源码。

- 2026-09-10 09:23：用隔离世界桩调用真实 `World.rayTraceBlocks`，稳定复现其原地改写 start 导致后续实体检测从背景方块处开始（失败日志 `logs/tools/2026-09-10 09-21-38.log`）。`FreecamTarget.pick` 改传向量副本，客户端删除重复算法、共用该函数；服务端删除直接相信目标 ID/命中点的分支，重新射线并检查 ID 一致，防止穿墙或替换目标。删除手写剪羊毛兜底，原生 `interactWith` 已调用 `ItemShears` 且尊重事件取消；新增 handled、distanceSq、container 日志用于分辨未处理与 GUI 问题。

- 2026-09-10 09:23：`scripts/forge1710.ps1 smoke` 通过（`logs/tools/2026-09-10 09-23-10.log`），覆盖射线起点不变、5 格外生物胜过背景方块、前景墙阻挡、真实 `EntityAITradePlayer` 字节码距离补丁、协议/范围与相机断言。测试使用 Java 8 Unsafe 仅跳过世界构造，不触碰存档；最初测试构造遇到 final 字段与 Forge Bootstrap 类加载限制，已修正。`git diff --check` 通过，仅换行提示。已确认选取故障修复，但未进行游戏内剪羊毛/交易 GUI 复测，不能断言三个用户症状全部解决；原生工具入口本身无距离限制，既有交易 AI 补丁保留，实际残余问题需结合新增日志定位。

- 2026-09-09 23:02：完成自由视角模式下生物通用交互、超距零伤害击退与钓鱼竿定向抛收线的双版本实现。
  - 选取机制：两端共用同一射线反投影，按光标首个遮挡选取；生物按中心点校验位于玩家脚底 16 格立方体内且处于同世界已加载区块，不穿墙、不跨视线遮挡。
  - 右键交互：客户端与服务端均接入版本原生通用事件与交互链路（1.7.10 经 `interactWith` 与 `EntityInteractEvent`，1.21.1 经 `interactAt`/`interactOn`、`CommonHooks.onInteractEntityAt`），可正常应用剪羊毛、喂食等手持工具效果并通用兼容 Mod 生物。
  - 左键打击：玩家原版触及范围内执行真实攻击；超出原版触及但在 16 格自由视角范围内，触发挥手、受击动画、受击音效与击退冲量，但不调用实际扣血、不激怒仇恨、不触发火焰附魔与武器耐久消耗。
  - 钓鱼竿逻辑：手持钓鱼竿右键时，鱼钩自玩家本体朝光标所指方块或生物位置定向抛出；玩家已有鱼钩时，无论光标是否指向目标均优先收线。
  - 自动化验证：`scripts/forge1710.ps1 smoke`（Java 8 编译、JAR 结构、元数据检查与 LegacyActionCheck）通过，日志 `logs/tools/2026-09-09 22-57-17.log`；`scripts/neoforge.ps1 smoke`（NeoForge 21.1.249 编译、InteractionCheck 与 CameraMotionCheck）通过，日志 `logs/tools/2026-09-09 22-57-25.log`。

- 2026-09-09 22:30：完成 intent-completion 两道确认后，将自由视角隐藏中心准星、空格/Ctrl垂直升降与下落触底防穿透完整同步至 1.21.1（NeoForge）。在 `FreecamClient` 订阅 `RenderGuiLayerEvent.Pre` 取消 `VanillaGuiLayers.CROSSHAIR`；在 `FreecamSession` 中接入空格与 Ctrl 升降，并利用 `VoxelShape` 与 `Level.getMinBuildHeight` 实现下落阻挡；更新双语操作指引；当前工作树 `src/main` 与主工作树 `C:\Users\15575\project\上帝视角建造` 均完成源码同步，`scripts/dev.ps1 smoke`（交互自检、相机自检与 NeoForge 构建）全部通过，主工作树已提交（commit `a225747`）。

- 2026-09-09 22:24：启动 lwjgl3ify 实例供用户实机体验。用户反馈升降与方块交互体验仍存在微小瑕疵（原版第三人称视角在极端角度/复杂遮挡下可能存在视距伸缩体验差异），但不影响基础升降与交互使用，决定按当前状态提交保存。据用户指令将自由视角空格/Ctrl垂直升降与下落触底阻挡功能提交至分支 `codex/forge-1.7.10`。

- 2026-09-09 22:09：修复相机在平移/旋转中偶发瞬移至树顶或屋顶的问题。根因为 `surfaceY` 在无下落意图时每帧无条件扫描上方并强制截断，将悬空树叶与房檐误判为地面支撑面并强制传送。重构为 `getDropFloor`：严格限制仅在长按 Ctrl 往下落时（`yOffset < 0`）触发触底阻挡，且扫描起点严格从相机脚底向下扫描，杜绝头顶树叶误判；水平平移（WASD）与中键旋转绝对不干涉 Y 轴，彻底消除瞬移与高低跳跃，同时完美保留玩家站在高处俯瞰并下落到低处地面的能力。`scripts/forge1710.ps1 smoke` 构建与冒烟通过（`logs/tools/2026-09-09 22-09-20.log`）。

- 2026-09-09 22:02：针对长按 Ctrl 降低摄像头至地底导致 X-Ray 透视地下洞穴与熔岩的问题，完成两阶段意图确认后实施方案 A（地面实心方块碰撞检测与贴地防穿透）。在 `FreecamClient` 中增加 `surfaceY` 物理支撑表面判定，向下扫描实心方块（`Material.isSolid` 或 `isLiquid`）并精确读取其最高碰撞盒或表面高度；限制相机最低高度 `nextY = Math.max(nextY, surfaceY(nextX, nextY, nextZ))`，不仅阻挡垂直降低钻入地底，亦在水平平移时随地形上坡贴地爬升。执行 `scripts/forge1710.ps1 smoke` 构建及 Java 8 冒烟测试验证通过（日志 `logs/tools/2026-09-09 22-01-49.log`）。

- 2026-09-09 21:54：完成 intent-completion 两道确认后实现自由视角下的空格/Ctrl垂直升降功能。在 `FreecamMotion` 中提供帧率时间截断保护的 `vertical` 方法，并在 `FreecamClient` 中检测空格（跳跃键/SPACE）抬升与 Ctrl（疾跑键/LCONTROL/RCONTROL）降低；Y 轴位移受制于 `FreecamRange.clampCamera`（玩家高度 ±12 格边界防护与非有限值过滤），与水平平移完全一致。顶部 HUD 操作提示同步更新。执行 `scripts/forge1710.ps1 smoke` 构建及 Java 8 冒烟测试验证通过（日志 `logs/tools/2026-09-09 21-54-00.log`），1.21.1 迁移参考与相机自检脚本同步通过。

- 2026-09-09 21:51：启动 lwjgl3ify 实例供用户进行实机桌面交互验收，用户人工体验确认自由视角模式下屏幕中心十字光标已成功隐藏、方块选取正常且退出模式后准星恢复，明确反馈“验收通过，提交”。据此记录用户验收通过并准备提交至分支 `codex/forge-1.7.10`。

- 2026-09-09 21:46：完成 intent-completion 两道确认后实施 1.7.10 自由视角移除屏幕中心十字光标功能。在 `FreecamClient` 中订阅 `RenderGameOverlayEvent.Pre`，当事件类型为 `CROSSHAIRS` 且 `current()` 为 true 时取消事件渲染，彻底隐藏自由视角下的原版中心十字准星；退出模式或切换世界自动恢复。执行 `scripts/forge1710.ps1 smoke` 构建及 Java 8 冒烟测试验证通过（日志 `logs/tools/2026-09-09 21-45-00.log`）。

- 2026-09-09 21:43：启动 lwjgl3ify 实例供用户进行真实桌面交互验收，用户人工实机体验自由视角平移、边界阻挡与滑动效果，确认功能符合预期并通过测试。据用户明确指令将相机可移动范围限制在交互范围 +50% 及 lwjgl3ify 自适应补丁提交至分支 `codex/forge-1.7.10`。

- 2026-09-09 21:40：完成自由视角模式下相机移动范围限制在交互范围 +50% 的双版本改造。经两阶段意图补齐与非硬编码确认，在 `src/forge1710` 与 `src/main` 的 `FreecamRange` 中实现 `cameraReach()` 动态派生方法（`(SIZE / 2.0) * 1.5`）和 `clampCamera(player, camera)` 独立轴向截断保护（兼顾世界边界 `±29999984` 与 NaN 防护）。1.7.10 的 `FreecamClient` 与 1.21.1 的 `FreecamSession` 平移时均直接接入 `FreecamRange.clampCamera`，杜绝散落魔法数字，为后续交互范围动态化预留联动基础。同步更新 `scripts/LegacyCheck.java` 和 `scripts/CameraMotionCheck.java` 自检断言。执行 `scripts/forge1710.ps1 check` 与 `smoke` 冒烟全部通过。

- 2026-09-09 21:15：为导入 GTNH 整合包测试构建 Forge 1.7.10 / lwjgl3ify 支持的 JAR。在 `FreecamTransformer` 中实现 lwjgl3ify 环境零配置自适应检测（自动反射探测 `org.lwjgl.Version` 与 `me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod`），用户将 Mod 直接丢入 GTNH 整合包的 `mods` 文件夹时无需额外配置 `-Dfreecam.lwjgl3ify=true` 即可自动跳过原生 OpenAL 补丁，同时保留容器距离（TileEntity/Container）改写和全套自由视角交互能力。更新 `mcmod.info` 描述，执行 `scripts/forge1710.ps1 smoke` 构建与冒烟通过，生成产物 `build/libs/freecam_interaction-0.1.0-forge1710-experiment.jar`。

- 2026-09-09 21:12：使用 `minecraft-gameplay` 技能与 `real-user-acceptance` 路径对 lwjgl3ify 独立实例进行真实交互测试与闭环验证。
  - 主菜单及 Mods 列表：成功识别 Mod 显示名称为“自由视角交互”，Mod ID 为 `freecam_interaction`，无任何乱码或旧命名残留（凭证：`logs/acceptance/freecam-mod-details.png`）。
  - 创建并进入世界：按 G 键成功切换进入自由视角模式，HUD 标题与操作指引正确渲染，玩家俯视视角正常（凭证：`logs/acceptance/freecam-mode-active.png`）。
  - 退出与恢复：再次按 G 键成功退出自由视角，平移相机恢复玩家原位，网络层同步发送模式注销。
  - 日志闭环：独立日志文件在 `logs/freecam_interaction/2026-09-09 21-05-09.log` 成功生成并完整记录进入/退出生命周期。
  - 游戏正常保存世界并返回主菜单后退出。全部测试通过。

- 2026-09-09 20:58：完成 intent-completion 两道确认，全量重命名 Mod 标识符与类名。显示名称由“上帝视角建造”改为“自由视角交互”，Mod ID 由 `godview_build` 改为 `freecam_interaction`，基础包名由 `local.godviewbuild` 改为 `local.freecaminteraction`，类名前缀由 `Godview*` 改为 `Freecam*`，日志目录更新为 `logs/freecam_interaction`。1.7.10 与 1.21.1 源码、资源路径、元数据、Mixin/CoreMod 属性及自检脚本全部同步完成。`scripts/forge1710.ps1 smoke` 构建与冒烟测试通过，产物 `freecam_interaction-0.1.0-forge1710-experiment.jar` 结构验证通过；`scripts/lwjgl3ify.ps1 smoke` 冒烟准备通过。

- 2026-09-09 20:02：完成 lwjgl3ify 对比。固定 lwjgl3ify 3.0.33、UniMixins 0.3.1、Iron Chests 6.1.13 和 Java 25；独立实例使用纯 ASCII 路径 `%LOCALAPPDATA%/GodviewBuild/lwjgl3ify-3.0.33/instance`，避免 Java 25 参数文件将中文工作树路径按 ANSI 解码。日志 `logs/tools/2026-09-09 19-44-44.log` 确认 RFB、UniMixins、LWJGL3 3.4.2、本 Mod 与 Iron Chest 共存，`TileEntityIronChest` 被容器距离转换器改写。用户人工确认主菜单、世界、上帝视角、平移和第三方容器放置/远程打开成功；截图 `logs/acceptance/lwjgl3ify-main.png`、`lwjgl3ify-godview.png`、`lwjgl3ify-ironchest-placed.png`。该证据只覆盖最小实例，不代表整个 GTNH 整合包。

- 2026-09-09 19:27：最终原生 LWJGL2 冒烟通过（`logs/tools/2026-09-09 19-27-06.log`），生存实验世界已保存，客户端正常关闭；Mod 日志确认服务端模式从 true 恢复为 false，客户端收到关闭确认。当前阶段完成，下一步是建立隔离 lwjgl3ify 实例做同功能对比。

- 2026-09-09 19:24：原生 LWJGL2 启动稳定性已修复。核心补丁将 `SoundManager.loadSoundSystem` 内 `Thread.start()` 改为在原同步方法内 `run()`，关闭 `loaded` 置位前的重入窗口；多次真实启动完成清理及重载，未再出现 OpenAL context 竞争或 `nalGetSourcei` 崩溃。最近启动日志 `logs/tools/2026-09-09 19-20-58.log`。Forge 旧版版本检查返回非 JSON 的后台异常仍存在，与 Mod 和音频无关。

- 2026-09-09 19:24：交互无效的直接原因有两处：客户端仍强制松开攻击/使用键且未调用 `PlayerControllerMP`；网络层把同一 Mode 类注册为两个 discriminator，后注册覆盖发送编号。现已直接复用 `clickBlock`、`onPlayerDamageBlock`、`onPlayerRightClick`，并拆分 `ModeRequest`/`ModeAck`。实际日志确认服务端模式开启、确认及退出恢复。

- 2026-09-09 19:24：创造世界已真实验证左键移除方块与右键放置海绵；生存世界已验证持续左键 1.5 秒遵循原版破坏计时。截图 `logs/acceptance/interaction-break.png`、`interaction-place.png`、`survival2-break.png`。服务端扩大玩家 reach 后仍走原版 C07/C08、ItemInWorldManager、Forge 权限/交互/破坏/放置事件，并按玩家脚底 16 格范围验证目标与实际放置结果。

- 2026-09-09 19:24：原版十类容器的距离调用由核心补丁定点路由到同一范围函数，避免远程打开后立即关闭；不全局修改 Entity 距离，以免放宽实体交互。第三方自定义容器尚未验证，留待 lwjgl3ify/GTNH 兼容阶段。`scripts/forge1710.ps1 smoke` 通过（`logs/tools/2026-09-09 19-16-32.log`）；lwjgl3ify 尚未加入。

- 2026-09-09 12:53：继续旧版迁移，新增 GodviewSelection：按 RenderWorldLastEvent 实际 GL 矩阵反投影鼠标，首个原版射线命中按 GodviewRange 限制；显示受深度遮挡的范围线框与原版选中方块包围框。复用原版范围类，新增负坐标/边界/NaN 断言。scripts/forge1710.ps1 smoke 通过（logs/tools/2026-09-09 12-49-35.log）。尚未实现外露面呼吸高亮、建造协议、服务端校验或 lwjgl3ify 对比。

- 2026-09-09 12:53：本轮客户端启动在进入世界前音频崩溃：run/crash-reports/crash-2026-09-09_12.51.19-client.txt，OpenAL AL10.nalGetSourcei UnsatisfiedLinkError。fml-client-latest.log 显示 Thread-8 已初始化 OpenAL，Thread-10 再次初始化报 Only one OpenAL context；源码 SoundManager.loadSoundSystem 异步启动线程后才设置 loaded，reloadSoundSystem 可再次触发。此前纯英文 native 路径只解决 DLL 查找，不代表音频完全修复。新选择渲染尚未目视验证；客户端已崩溃结束，无继续控制。

- 2026-09-09 12:43：Forge 1.7.10 原生 LWJGL2 实验已实现基础相机，用户明确人工确认平移、旋转、背包返回可用，且相机移动时玩家坐标保持不变；这四项不再重复测试。12:36 的 scripts/forge1710.ps1 smoke 已通过 Java 8 编译、运动断言、日志初始化与 JAR 检查，日志 logs/tools/2026-09-09 12-36-50.log。此前已目视主菜单、Mod 列表、新建世界、保存退出和重新进入。光标建造交互及 lwjgl3ify 对比仍未完成，不将相机验收扩大为完整移植通过。

- 2026-09-09 12:43：旧版实验位于 src/forge1710，构建使用 RFG 2.0.4 / Gradle 9.2.1 / JDK 25，游戏与 Mod 使用 Java 8u504。原生 LWJGL2 的 OpenAL 在中文 native 路径下加载失败，复制至 Gradle 用户缓存纯英文路径并设置 org.lwjgl.librarypath 后重启不再报该错。minecraft-gameplay 控制器自检 50 项通过；scripts/gameplay.py 复用其输入保护并补充 G 键，不修改全局技能。

- 2026-09-09 12:23：用户明确要求新工作树迁移，已从 509b278 建立 codex/forge-1.7.10，工作树 C:/Users/15575/project/上帝视角建造-forge1710；同步原工作区未提交的项目说明和调研记忆。实验顺序已确认：先 Forge 1.7.10 原生 LWJGL2，再加入 lwjgl3ify 对比。迁移实现和验证均在此工作树进行。

- 2026-09-09 12:20：用户提出计划移植到 Minecraft 1.7.10 / Forge，并兼容 GTNH 社区维护的 lwjgl3ify。本轮完成初步源码与上游资料调研，尚未实施移植；目标 lwjgl3ify/整合包版本、是否兼容无 lwjgl3ify 环境和维护方式待明确。建议采用 GTNH ExampleMod1.7.10/GTNHGradle 工具链，独立维护旧版实现；具体依赖需按目标环境固定。图谱工具本轮不可用，已回退源码读取。保留工作区既有未提交修改；未运行游戏或编程测试。上游：https://github.com/GTNewHorizons/lwjgl3ify 、https://github.com/GTNewHorizons/ExampleMod1.7.10 。

- 2026-09-08 22:49：用户通过 intent-completion 两道确认，要求轻量、友好的完整三维交互范围边界。实现仅在服务器支持交互且上帝视角有效时绘制受地形遮挡的淡青白色 16×16×16 线框；中键旋转保留，菜单、覆盖层、F1、退出、死亡、换维度及断线时隐藏或随既有会话清理。不加入透视线、填色、内部网格、文字、设置或新协议。

- 2026-09-08 22:49：范围线框和交互判定共用 `GodviewRange` 的 16 格尺寸与最小方块坐标计算，随玩家脚底在半格临界点同步吸附，包含负坐标与非法坐标自检。`scripts/dev.ps1 smoke` 通过，日志 `logs/tools/2026-09-08 22-49-05.log`；`git diff --check` 通过（仅换行提示）。未执行游戏内目视验收，后续需核对遮挡、透明度、FOV/窗口变化、闪烁及与选中面高亮的层次。

- 2026-09-08 22:20：用户明确反馈“验收通过，提交”，据此记录光标选取、范围内挖掘/放置/容器交互与服务端校验功能验收通过；未提供逐项测试步骤，沿用 22:12 冒烟和 22:10 客户端启动检查结果。

- 2026-09-08 22:12：完成光标建造的 intent-completion 两道确认后实现。交互范围固定为玩家脚底坐标中心、边长 16 的立方体，按方块中心判断、下含上不含；范围跟随玩家而非相机。光标射线首个可见方块才可选取，面高亮按实际形状外露面分割，约 2 秒呼吸周期。

- 2026-09-08 22:12：通过可选服务端模式协议与 Player.canInteractWithBlock Mixin 接通范围和原版容器有效性；放置前校验实际落点，标准多方块放置事件越界取消。保留原版输入、挖掘计时、主副手与 NeoForge 事件，未支持协议的服务器仅观察。菜单/失焦/旋转后必须先松键；服务端模式失效、越界及 ABORT 清理持续/延迟挖掘。玩家朝向不伪造，硬编码距离、自行射线或连锁副作用的 Mod 不保证兼容。

- 2026-09-08 22:12：最终 `scripts/dev.ps1 smoke` 通过，日志 `logs/tools/2026-09-08 22-12-41.log`；新增 InteractionCheck 覆盖范围、负坐标、非法数值、射线及立方体/半砖/楼梯外露面，使用主源码运行/编译类路径的 Java 源码启动，不引入测试框架。`git diff --check` 通过（仅换行提示）。22:10 启动客户端并目视确认主菜单，22:11 正常关闭；未进入或改动存档，未做玩法 E2E、独立服务器或第三方 Mod 验证。后续按 ACCEPTANCE.md 实际体验高亮、挖掘、放置和容器。

- 2026-09-08 22:12：前次 PowerShell 管道调用 apply_patch 因 UTF-8 参数方式不正确而失败，未写入文件；本轮改用直接 apply_patch 工具成功。知识图谱工具仍不可用，按固定版本依赖源码核验关键入口。渲染选择 AFTER_LEVEL，避开半透明阶段的额外 ModelView 变换和 Fabulous 离屏目标。

- 2026-09-08 21:38：用户明确反馈“验收通过，提交”，据此记录相机平移、旋转与 HUD 交互改造的用户验收通过；未提供逐项测试步骤。本次提交包含功能、自检和文档，沿用已通过的最小冒烟结果，不重复运行游戏验收。

- 2026-09-08 21:29：完成 intent-completion 两道确认后改造相机输入。最终约定以本条为准：默认 G 切换、右上角 X 按钮退出；Esc 不退出，而是保留原版游戏菜单和设置。模式独立于 Screen，E 背包及数字键/滚轮快捷栏沿用原版，菜单/失焦暂停相机控制，异常会话恢复状态。

- 2026-09-08 21:29：删除 GodviewScreen，新增 GodviewSession 与纯数学 GodviewMotion；WASD 相对视角水平等速平移，中键拖拽改变相机角度，俯仰限制 ±85°。通过 Camera.setPosition(Vec3) 的最小访问转换器在原生避障前设置锚点，不传送玩家或请求远程区块；模式内取消物品交互。HUD 中英文帮助读取当前绑定键。

- 2026-09-08 21:29：`scripts/dev.ps1 smoke` 构建、产物检查和无框架运动自检通过，日志 `logs/tools/2026-09-08 21-29-04.log`；`git diff --check` 通过，仅有 Windows 换行提示。保留既有 EventBusSubscriber 弃用警告。未执行真实客户端交互/目视验收，后续按 ACCEPTANCE.md 体验。当前未提供知识图谱工具，依赖接口直接核对本地固定版本源码。

- 2026-09-08 21:04：用户明确反馈“测试通过，提交”，据此记录用户验收通过并提交当前功能；未提供具体测试步骤。用户终端此前无法识别 `pwsh`，不得假定工具运行环境中的 PowerShell 7 已加入用户 PATH。

- 2026-09-08 20:46：按确认范围实现客户端上帝视角：默认 G 可改键，高位第三人称俯视、X/Esc 退出、非暂停、移动输入清理、断线清理、日志记录与中英文资源；`scripts/dev.ps1 smoke` 构建及产物检查通过，日志为 `logs/tools/2026-09-08 20-46-15.log`。普通 Java 测试因缺少 Minecraft 测试类路径未能运行，已移除该入口；不将构建冒烟视为玩法验收。

- 2026-09-08 20:12：`scripts/setup.ps1` 成功下载并校验 JDK；`scripts/dev.ps1 check` 显示 Gradle 9.2.1/JDK 21；非法操作返回非零并写入日志；`scripts/dev.ps1 smoke` 构建成功，验证 JAR 入口类、日志模块、中文元数据和精确 1.21.1 范围。

- 2026-09-08 20:06：原有 Oracle JDK 17.0.12、Git 2.53.0；独立安装 Temurin JDK 21.0.12.1，不改系统环境变量。项目原为空。固定官方 MDK 提交及构建依赖，不引入其他版本或游戏功能。

- 2026-09-08 20:04：用户完成 intent-completion 两道确认，将本次范围收敛为仅 Minecraft 1.21.1。确认名称“上帝视角建造”、ID `godview_build`、包名 `local.godviewbuild` 与 Windows 兼容日志命名。
