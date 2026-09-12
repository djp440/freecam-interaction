# 项目说明

- AE2 传输核心（2026-09-12）：Forge 1.7.10 已实现 AE2 rv3-beta-6 可选联动、传输器绑定/频道/权限、ME/合成/样板/接口四类原生远端终端、背包/网络导航与多目标选择，以及蓝图的背包优先、ME 补缺和实物回滚。客户端绑定入口会继续发送原版 C08 包，由服务端消费；自由视角 E 统一请求服务端，服务端按实时背包绑定状态打开默认合成终端或回退原版背包。客户端 GUI 使用本地会话占位宿主，不读取或强载远端区块；三类虚拟 Part 构造期间通过线程局部上下文提供传输器，兼容 AE2 父类构造器对 `getProxy()` 的早期动态调用。六个跨页导航采用容器左侧 `18×18` 方形纵排，避开 AE2 原生设置列。用户已实机确认终端功能正常；原生 Forge 冒烟与 lwjgl3ify 3.0.33 + Java 25 准备冒烟已通过，断线/断网回滚仍待人工验收。完整基线为 `PLAN-ae2-transfer-1710.md`；1.21.1 对应功能待实现。

- 移速核心（2026-09-11）：已按 `PLAN-speed-cores-1710.md` 完成 Forge 1.7.10 两倍/四倍移速核心并通过冒烟验证。核心装入法杖后，只要法杖位于主背包 0..35 即生效（耐久 1 也有效）；多把法杖只取最高档，不叠加。本体移速分别乘 1.25/1.5，服务端使用固定 UUID、`operation=2`、非持久化 `movementSpeed` modifier；自由视角相机水平/升降分别乘 2/4，潜行时临时恢复原速。同杖两档移速核心互斥，可与蓝图核心共存。配方为时钟无序合成两倍核心、两枚两倍核心加石英块无序合成四倍核心。32×32 透明像素 PNG 位于 `src/forge1710/resources/assets/freecam_interaction/textures/items/speed_core_{2x,4x}.png`；1.21.1 对应功能待实现。

- 蓝图系统（2026-09-11）：依据 `PLAN-blueprints-1710.md` 规范全量实现并完成冒烟验证。包含法杖 4 槽升级容器（`ContainerWandUpgrade` / `GuiWandUpgrade`，锁定编辑槽防刷，Shift 安全搬运，同杖同类核心互斥）、蓝图核心（合成、不可堆叠、32×32像素贴图）、个人蓝图存储（`BlueprintStorage`，魔数 BPFC，分段二进制 GZIP，`.tmp` 原子写入，损坏文件隔离）、三档权限（不可见/仅可见/可建造，默认第二档）、多人协作施工调度与物料结算（`BlueprintBuildScheduler` / `BlueprintBuildExecutor`，拓扑分步放置、坐标互斥锁、操作者自由视角及核心资格复核、严格消耗协作者背包物料与自身法杖耐久、创造免消耗、耐久降至 1 自动背包接续、失败三段式回滚、不碰主人库存、空气绝不破坏地形）。蓝图管理/保存界面覆写 `doesGuiPauseGame()` 返回 false（不暂停世界，刷新即时生效），任务主人与获准协作者均有"建造施工"按钮，施工请求被拒或施工暂停时通过聊天栏给出原因反馈。施工资格以"背包中任意一把法杖装有蓝图核心"为准（`FreecamInteraction.hasBlueprintCoreWand`），不要求装在当前生效法杖上；耐久仍由当前生效法杖支付。施工完工后任务直接从任务表移除并广播 TYPE_REMOVE（与取消同路径），不再保留已完工任务；仅缺料/越范围/区块未加载导致的暂停任务（STATUS_PENDING）留在列表中待继续施工。1.21.1 对应功能待实现。

- 模组方块/部件建造（2026-09-11）：`blueprint/BlueprintPartSupport.java` 纯反射复用 AE2 自身 API（IPartHost.getPart/addPart、IBlocks.multiPart 总线方块定义）采集与安装线缆/线缆锚/面板等附属部件（`side=6` 表示中心线缆槽，部件宿主位置不再另记方块条目以防重复扣料）；模组方块放置改走 ItemBlock.placeBlockAt 以创建 GT 等机器的元数据实体（MTE），原版方块仍走 setBlock。`BlueprintGregTechSupport.java` 纯反射采集并恢复 GT 机器正面与六面覆盖板：朝向保存为 Mod 自有静态键并通过 `isValidFacing/setFrontFacing` 恢复，覆盖板以物品注册名、damage、side、coverData 保存为现有 `BlueprintPartEntry`，安装走 `canPlaceCoverItemAtSide/setCoverItemAtSide/setCoverDataAtSide/issueCoverUpdate`；宿主机器方块继续保存，覆盖板作为独立物料在 PARTS 阶段结算。放置与 FINALIZE_CONFIG 共用安全配置入口，绝不把残缺 GT NBT 回灌给机器；未安装对应模组时自动降级禁用。延后项：AE2 部件配置 NBT、部件网络同步与部件虚影渲染。1.21.1 对应功能待实现。

- 自由视角生存挖掘修复（2026-09-10）：`FreecamTransformer` 定点拦截 `Minecraft.func_147115_a`（`sendClickBlockToController`），在自由视角激活时阻断原版主循环因失焦触发的无条件挖掘重置（`playerController.resetBlockRemoving`），恢复方块裂纹与破坏进度的正常累加与破坏；非自由视角保持 100% 原版逻辑。

- 通用交互掉落入包（2026-09-10）：Forge 1.7.10 自由视角合法同步交互产生的物品实体会立即尝试进入操作者主背包，覆盖挖掘、右键工具拆卸、实体交互/击杀、钓鱼及 AE2 ME 线缆/部件（PartPlacement 独立拆卸射线与掉落包裹）；实现按交互上下文归属，不依赖特定物品白名单。背包不足的余量留在原地，保留 NBT；延迟掉落不推测归属。1.21.1 对应功能待实现。

- 科技与辅助 Mod 开发环境（2026-09-10）：原生 Forge 与 lwjgl3ify 开发实例固定安装 IC2 2.2.828-experimental、AE2 rv3-beta-6、CodeChickenCore 1.0.7.47、NotEnoughItems 1.0.5.120 与 GregTech 5.09.31 Unofficial；使用 `pwsh -File scripts/tech-mods.ps1 all` 下载、校验并恢复两个实例。

- 法杖材质（2026-09-10）：`src/forge1710/resources/assets/freecam_interaction/textures/items/wand_{normal,advanced,creative}.png` 为32×32透明PNG，强调色依次蓝、红、紫。原图/提示词/预览在 `art/wands`；导出检查使用 `scripts/wand-textures.ps1`。

- 法杖系统（2026-09-10）：已按 `PLAN-wands-1710.md` 完成实现并通过冒烟验证。普通法杖（5×5区块/2048耐久/钻石修复）、高级法杖（7×7区块/8192耐久/钻石块修复）、创造法杖（9×9区块/无限耐久）；具备耐久<=1保底不损毁、耐久2->1自动背包选取接续（创造>高级>普通，同级按槽位）、有效操作动作扣费、ForgeChunkManager票据与PlayerManager区块订阅保载。1.21.1 对应功能待实现。

Minecraft 1.21.1 / NeoForge 与 Forge 1.7.10 的“自由视角交互”Mod，已实现高位第三人称观察与范围内光标建造交互。使用简体中文和 UTF-8。

- 默认 G 切换模式（可在控制设置中改键），右上角 X 按钮退出；Esc 打开原版菜单，不退出模式。客户端代码位于 `local.freecaminteraction.client`。
- WASD 相对视角水平平移，中键拖拽旋转；保留原版数字键/滚轮快捷栏和 E 背包。模式状态独立于 Screen，菜单期间停止相机输入，死亡/世界切换/断线清理。
- 模式只改变相机与界面，不传送玩家、不暂停世界；使用镜头局部连续碰撞避障与防下潜，退出恢复原视角。
- 交互以玩家脚底坐标为中心，边长 16，按方块中心判定，下界包含、上界不包含；相机光标射线不跳过前方遮挡。放置落点也校验范围。
- `FreecamInteraction` 管理客户端/服务端模式及范围，替换模式内距离、放置边缘与输入入口；保留原版计时、权限和事件系统。联机未协商支持时仅观察。桶及其子类由服务端复核光标方块后继续走原版桶逻辑，不使用玩家朝向二次射线；空桶可选流体源，实际落点仍需在范围内。
- `FreecamSelection` 使用渲染矩阵选取，按实际形状外露面渲染；中键旋转、界面、失焦后需松开交互键再按下。Shift 保留辅助使用；新增实体交互走服务端授权与原生事件链，尚需按版本单独验收。

- Java 21（1.21.1）/ Java 8 与 25（1.7.10），依赖版本固定，不提前创建跨版本抽象。
- 使用 `pwsh -File scripts/forge1710.ps1 setup|check|build|smoke|client|server` 进行 1.7.10 开发；`scripts/dev.ps1` 用于 1.21.1。
- 不修改系统默认 Java，脚本仅在当前进程选择项目 JDK。
- 完成修改执行最小冒烟，不默认执行全量 E2E。
- `smoke` 包含无测试框架的相机运动与交互几何自检；交互检查使用主源码依赖类路径，不另引入测试框架。相机锚点设置后采用镜头局部碰撞避障（方案 A），阻断原版第三人称长连线回缩。
- Mod 日志在运行目录 `logs/freecam_interaction`，工具日志在项目 `logs/tools`；Windows 文件名使用 `yyyy-MM-dd HH-mm-ss.log`。
- 保留 Minecraft/Gradle 原生日志，不删除用户存档，不自动接受 EULA。
- 维护 MEMORY.md，记录精确到分钟的决定及验证结果。

## 相机障碍跳位修复（方案 A 已实现）

- 方案 A 已实现并通过自动化冒烟：在 `FreecamTransformer` 中定点拦截 `EntityRenderer.orientCamera` 的 8 条避障射线调用，自由视角下阻断长连线折叠，普通第三人称走原版；
- 新增 `FreecamCollision` 接管镜头局部 swept broadphase/narrowphase 轴向滑动碰撞与液面防下沉，中键旋转采用 3D 圆弧步进防穿；
- 移除原 `getDropFloor`，由镜头全向碰撞统一处理；
- 详见 `HANDOFF-camera-collision.md` 与 `LegacyActionCheck`。

## 当前工作树：Forge 1.7.10 实验

- 本工作树分支 codex/forge-1.7.10；代码在 src/forge1710，src/main 为 1.21.1 迁移参考，不参与当前 1.7.10 构建。
- 使用 scripts/forge1710.ps1 setup|check|build|smoke|client|server；构建 JDK 25，编译与原生 LWJGL2 游戏使用 Java 8。
- 基础相机平移、旋转、背包返回及玩家坐标不变已由用户人工确认；光标选取也由用户人工确认。
- 原生 LWJGL2 已接通模式握手、创造/生存挖掘、放置和服务端范围校验；原版容器距离入口已补丁，第三方容器已在 lwjgl3ify 环境验证。
- 先原生 LWJGL2，再加入 lwjgl3ify；后者兼容不代表整个 GTNH 整合包兼容。
- 1.7.10 的 `World.rayTraceBlocks` 会原地改写起点，必须传入副本；两端共用 `FreecamTarget.pick`，服务端不得仅凭客户端实体 ID 跳过遮挡复核。工具使用沿用 `EntityPlayer.interactWith`，禁止手写兜底绕过事件取消。
- `LegacyActionCheck` 用隔离世界桩调用真实原版射线，覆盖起点不变、远处生物优先于背景方块、前景墙体遮挡，并检查真实交易 AI 与 `ItemBucket` 目标替换字节码补丁；不代表桶、剪羊毛或交易 GUI 实机验收。

- SoundManager/OpenAL 初始化竞争已通过串行化原版加载入口修复，连续启动无上下文冲突；详见 ACCEPTANCE-1710.md。
