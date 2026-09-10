# 项目记忆

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
