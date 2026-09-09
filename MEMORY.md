# 项目记忆

- 2026-09-08 22:49：用户通过 intent-completion 两道确认，要求轻量、友好的完整三维交互范围边界。实现仅在服务器支持交互且上帝视角有效时绘制受地形遮挡的淡青白色 16×16×16 线框；中键旋转保留，菜单、覆盖层、F1、退出、死亡、换维度及断线时隐藏或随既有会话清理。不加入透视线、填色、内部网格、文字、设置或新协议。
- 2026-09-08 22:49：范围线框和交互判定共用 `GodviewRange` 的 16 格尺寸与最小方块坐标计算，随玩家脚底在半格临界点同步吸附，包含负坐标与非法坐标自检。`scripts/dev.ps1 smoke` 通过，日志 `logs/tools/2026-09-08 22-49-05.log`；`git diff --check` 通过（仅换行提示）。未执行游戏内目视验收，后续需核对遮挡、透明度、FOV/窗口变化、闪烁及与选中面高亮的层次。

- 2026-09-08 22:20：用户明确反馈“验收通过，提交”，据此记录光标选取、范围内挖掘/放置/容器交互与服务端校验功能验收通过；未提供逐项测试步骤，沿用 22:12 冒烟和 22:10 客户端启动检查结果。

- 2026-09-08 22:12：完成光标建造的 intent-completion 两道确认后实现。交互范围固定为玩家脚底坐标中心、边长 16 的立方体，按方块中心判断、下含上不含；范围跟随玩家而非相机。光标射线首个可见方块才可选取，面高亮按实际形状外露面分割，约 2 秒呼吸周期。
- 2026-09-08 22:12：通过可选服务端模式协议与 Player.canInteractWithBlock Mixin 接通范围和原版容器有效性；放置前校验实际落点，标准多方块放置事件越界取消。保留原版输入、挖掘计时、主副手与 NeoForge 事件，未支持协议的服务器仅观察。菜单/失焦/旋转后必须先松键；服务端模式失效、越界及 ABORT 清理持续/延迟挖掘。玩家朝向不伪造，硬编码距离、自行射线或连锁副作用的 Mod 不保证兼容。
- 2026-09-08 22:12：最终 `scripts/dev.ps1 smoke` 通过，日志 `logs/tools/2026-09-08 22-12-41.log`；新增 InteractionCheck 覆盖范围、负坐标、非法数值、射线及立方体/半砖/楼梯外露面，使用主源码运行/编译类路径的 Java 源码启动，不引入测试框架。`git diff --check` 通过（仅换行提示）。22:10 启动客户端并目视确认主菜单，22:11 正常关闭；未进入或改动存档，未做玩法 E2E、独立服务器或第三方 Mod 验证。后续按 ACCEPTANCE.md 实际体验高亮、挖掘、放置和容器。
- 2026-09-08 22:12：前次 PowerShell 管道调用 apply_patch 因 UTF-8 参数方式不正确而失败，未写入文件；本轮改用直接 apply_patch 工具成功。知识图谱工具仍不可用，按固定版本依赖源码核验关键入口。渲染选择 AFTER_LEVEL，避开半透明阶段的额外 ModelView 变换和 Fabulous 离屏目标。

- 2026-09-08 21:38：用户明确反馈“验收通过，提交”，据此记录相机平移、旋转与 HUD 交互改造的用户验收通过；未提供逐项测试步骤。本次提交包含功能、自检和文档，沿用已通过的最小冒烟结果，不重复运行游戏验收。

- 2026-09-08 20:04：用户完成 intent-completion 两道确认，将本次范围收敛为仅 Minecraft 1.21.1。确认名称“上帝视角建造”、ID `godview_build`、包名 `local.godviewbuild` 与 Windows 兼容日志命名。
- 2026-09-08 20:06：原有 Oracle JDK 17.0.12、Git 2.53.0；独立安装 Temurin JDK 21.0.12.1，不改系统环境变量。项目原为空。固定官方 MDK 提交及构建依赖，不引入其他版本或游戏功能。
- 2026-09-08 20:12：`scripts/setup.ps1` 成功下载并校验 JDK；`scripts/dev.ps1 check` 显示 Gradle 9.2.1/JDK 21；非法操作返回非零并写入日志；`scripts/dev.ps1 smoke` 构建成功，验证 JAR 入口类、日志模块、中文元数据和精确 1.21.1 范围。

- 2026-09-08 20:46：按确认范围实现客户端上帝视角：默认 G 可改键，高位第三人称俯视、X/Esc 退出、非暂停、移动输入清理、断线清理、日志记录与中英文资源；`scripts/dev.ps1 smoke` 构建及产物检查通过，日志为 `logs/tools/2026-09-08 20-46-15.log`。普通 Java 测试因缺少 Minecraft 测试类路径未能运行，已移除该入口；不将构建冒烟视为玩法验收。
- 2026-09-08 21:04：用户明确反馈“测试通过，提交”，据此记录用户验收通过并提交当前功能；未提供具体测试步骤。用户终端此前无法识别 `pwsh`，不得假定工具运行环境中的 PowerShell 7 已加入用户 PATH。
- 2026-09-08 21:29：完成 intent-completion 两道确认后改造相机输入。最终约定以本条为准：默认 G 切换、右上角 X 按钮退出；Esc 不退出，而是保留原版游戏菜单和设置。模式独立于 Screen，E 背包及数字键/滚轮快捷栏沿用原版，菜单/失焦暂停相机控制，异常会话恢复状态。
- 2026-09-08 21:29：删除 GodviewScreen，新增 GodviewSession 与纯数学 GodviewMotion；WASD 相对视角水平等速平移，中键拖拽改变相机角度，俯仰限制 ±85°。通过 Camera.setPosition(Vec3) 的最小访问转换器在原生避障前设置锚点，不传送玩家或请求远程区块；模式内取消物品交互。HUD 中英文帮助读取当前绑定键。
- 2026-09-08 21:29：`scripts/dev.ps1 smoke` 构建、产物检查和无框架运动自检通过，日志 `logs/tools/2026-09-08 21-29-04.log`；`git diff --check` 通过，仅有 Windows 换行提示。保留既有 EventBusSubscriber 弃用警告。未执行真实客户端交互/目视验收，后续按 ACCEPTANCE.md 体验。当前未提供知识图谱工具，依赖接口直接核对本地固定版本源码。

- 2026-09-09 12:20：用户提出计划移植到 Minecraft 1.7.10 / Forge，并兼容 GTNH 社区维护的 lwjgl3ify。本轮完成初步源码与上游资料调研，尚未实施移植；目标 lwjgl3ify/整合包版本、是否兼容无 lwjgl3ify 环境和维护方式待明确。建议采用 GTNH ExampleMod1.7.10/GTNHGradle 工具链，独立维护旧版实现；具体依赖需按目标环境固定。图谱工具本轮不可用，已回退源码读取。保留工作区既有未提交修改；未运行游戏或编程测试。上游：https://github.com/GTNewHorizons/lwjgl3ify 、https://github.com/GTNewHorizons/ExampleMod1.7.10 。

- 2026-09-09 12:23：用户明确要求新工作树迁移，已从 509b278 建立 codex/forge-1.7.10，工作树 C:/Users/15575/project/上帝视角建造-forge1710；同步原工作区未提交的项目说明和调研记忆。实验顺序已确认：先 Forge 1.7.10 原生 LWJGL2，再加入 lwjgl3ify 对比。迁移实现和验证均在此工作树进行。

- 2026-09-09 12:43：Forge 1.7.10 原生 LWJGL2 实验已实现基础相机，用户明确人工确认平移、旋转、背包返回可用，且相机移动时玩家坐标保持不变；这四项不再重复测试。12:36 的 scripts/forge1710.ps1 smoke 已通过 Java 8 编译、运动断言、日志初始化与 JAR 检查，日志 logs/tools/2026-09-09 12-36-50.log。此前已目视主菜单、Mod 列表、新建世界、保存退出和重新进入。光标建造交互及 lwjgl3ify 对比仍未完成，不将相机验收扩大为完整移植通过。
- 2026-09-09 12:43：旧版实验位于 src/forge1710，构建使用 RFG 2.0.4 / Gradle 9.2.1 / JDK 25，游戏与 Mod 使用 Java 8u504。原生 LWJGL2 的 OpenAL 在中文 native 路径下加载失败，复制至 Gradle 用户缓存纯英文路径并设置 org.lwjgl.librarypath 后重启不再报该错。minecraft-gameplay 控制器自检 50 项通过；scripts/gameplay.py 复用其输入保护并补充 G 键，不修改全局技能。

- 2026-09-09 12:53：继续旧版迁移，新增 GodviewSelection：按 RenderWorldLastEvent 实际 GL 矩阵反投影鼠标，首个原版射线命中按 GodviewRange 限制；显示受深度遮挡的范围线框与原版选中方块包围框。复用原版范围类，新增负坐标/边界/NaN 断言。scripts/forge1710.ps1 smoke 通过（logs/tools/2026-09-09 12-49-35.log）。尚未实现外露面呼吸高亮、建造协议、服务端校验或 lwjgl3ify 对比。
- 2026-09-09 12:53：本轮客户端启动在进入世界前音频崩溃：run/crash-reports/crash-2026-09-09_12.51.19-client.txt，OpenAL AL10.nalGetSourcei UnsatisfiedLinkError。fml-client-latest.log 显示 Thread-8 已初始化 OpenAL，Thread-10 再次初始化报 Only one OpenAL context；源码 SoundManager.loadSoundSystem 异步启动线程后才设置 loaded，reloadSoundSystem 可再次触发。此前纯英文 native 路径只解决 DLL 查找，不代表音频完全修复。新选择渲染尚未目视验证；客户端已崩溃结束，无继续控制。

- 2026-09-09 19:24：原生 LWJGL2 启动稳定性已修复。核心补丁将 `SoundManager.loadSoundSystem` 内 `Thread.start()` 改为在原同步方法内 `run()`，关闭 `loaded` 置位前的重入窗口；多次真实启动完成清理及重载，未再出现 OpenAL context 竞争或 `nalGetSourcei` 崩溃。最近启动日志 `logs/tools/2026-09-09 19-20-58.log`。Forge 旧版版本检查返回非 JSON 的后台异常仍存在，与 Mod 和音频无关。
- 2026-09-09 19:24：交互无效的直接原因有两处：客户端仍强制松开攻击/使用键且未调用 `PlayerControllerMP`；网络层把同一 Mode 类注册为两个 discriminator，后注册覆盖发送编号。现已直接复用 `clickBlock`、`onPlayerDamageBlock`、`onPlayerRightClick`，并拆分 `ModeRequest`/`ModeAck`。实际日志确认服务端模式开启、确认及退出恢复。
- 2026-09-09 19:24：创造世界已真实验证左键移除方块与右键放置海绵；生存世界已验证持续左键 1.5 秒遵循原版破坏计时。截图 `logs/acceptance/interaction-break.png`、`interaction-place.png`、`survival2-break.png`。服务端扩大玩家 reach 后仍走原版 C07/C08、ItemInWorldManager、Forge 权限/交互/破坏/放置事件，并按玩家脚底 16 格范围验证目标与实际放置结果。
- 2026-09-09 19:24：原版十类容器的距离调用由核心补丁定点路由到同一范围函数，避免远程打开后立即关闭；不全局修改 Entity 距离，以免放宽实体交互。第三方自定义容器尚未验证，留待 lwjgl3ify/GTNH 兼容阶段。`scripts/forge1710.ps1 smoke` 通过（`logs/tools/2026-09-09 19-16-32.log`）；lwjgl3ify 尚未加入。
- 2026-09-09 19:27：最终原生 LWJGL2 冒烟通过（`logs/tools/2026-09-09 19-27-06.log`），生存实验世界已保存，客户端正常关闭；Mod 日志确认服务端模式从 true 恢复为 false，客户端收到关闭确认。当前阶段完成，下一步是建立隔离 lwjgl3ify 实例做同功能对比。
- 2026-09-09 20:02：完成 lwjgl3ify 对比。固定 lwjgl3ify 3.0.33、UniMixins 0.3.1、Iron Chests 6.1.13 和 Java 25；独立实例使用纯 ASCII 路径 `%LOCALAPPDATA%/GodviewBuild/lwjgl3ify-3.0.33/instance`，避免 Java 25 参数文件将中文工作树路径按 ANSI 解码。日志 `logs/tools/2026-09-09 19-44-44.log` 确认 RFB、UniMixins、LWJGL3 3.4.2、本 Mod 与 Iron Chest 共存，`TileEntityIronChest` 被容器距离转换器改写。用户人工确认主菜单、世界、上帝视角、平移和第三方容器放置/远程打开成功；截图 `logs/acceptance/lwjgl3ify-main.png`、`lwjgl3ify-godview.png`、`lwjgl3ify-ironchest-placed.png`。该证据只覆盖最小实例，不代表整个 GTNH 整合包。

- 2026-09-09 20:58：完成 intent-completion 两道确认，全量重命名 Mod 标识符与类名。显示名称由“上帝视角建造”改为“自由视角交互”，Mod ID 由 `godview_build` 改为 `freecam_interaction`，基础包名由 `local.godviewbuild` 改为 `local.freecaminteraction`，类名前缀由 `Godview*` 改为 `Freecam*`，日志目录更新为 `logs/freecam_interaction`。1.7.10 与 1.21.1 源码、资源路径、元数据、Mixin/CoreMod 属性及自检脚本全部同步完成。`scripts/forge1710.ps1 smoke` 构建与冒烟测试通过，产物 `freecam_interaction-0.1.0-forge1710-experiment.jar` 结构验证通过；`scripts/lwjgl3ify.ps1 smoke` 冒烟准备通过。

- 2026-09-09 21:12：使用 `minecraft-gameplay` 技能与 `real-user-acceptance` 路径对 lwjgl3ify 独立实例进行真实交互测试与闭环验证。
  - 主菜单及 Mods 列表：成功识别 Mod 显示名称为“自由视角交互”，Mod ID 为 `freecam_interaction`，无任何乱码或旧命名残留（凭证：`logs/acceptance/freecam-mod-details.png`）。
  - 创建并进入世界：按 G 键成功切换进入自由视角模式，HUD 标题与操作指引正确渲染，玩家俯视视角正常（凭证：`logs/acceptance/freecam-mode-active.png`）。
  - 退出与恢复：再次按 G 键成功退出自由视角，平移相机恢复玩家原位，网络层同步发送模式注销。
  - 日志闭环：独立日志文件在 `logs/freecam_interaction/2026-09-09 21-05-09.log` 成功生成并完整记录进入/退出生命周期。
  - 游戏正常保存世界并返回主菜单后退出。全部测试通过。

- 2026-09-09 21:15：为导入 GTNH 整合包测试构建 Forge 1.7.10 / lwjgl3ify 支持的 JAR。在 `FreecamTransformer` 中实现 lwjgl3ify 环境零配置自适应检测（自动反射探测 `org.lwjgl.Version` 与 `me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod`），用户将 Mod 直接丢入 GTNH 整合包的 `mods` 文件夹时无需额外配置 `-Dfreecam.lwjgl3ify=true` 即可自动跳过原生 OpenAL 补丁，同时保留容器距离（TileEntity/Container）改写和全套自由视角交互能力。更新 `mcmod.info` 描述，执行 `scripts/forge1710.ps1 smoke` 构建与冒烟通过，生成产物 `build/libs/freecam_interaction-0.1.0-forge1710-experiment.jar`。

- 2026-09-09 21:40：完成自由视角模式下相机移动范围限制在交互范围 +50% 的双版本改造。经两阶段意图补齐与非硬编码确认，在 `src/forge1710` 与 `src/main` 的 `FreecamRange` 中实现 `cameraReach()` 动态派生方法（`(SIZE / 2.0) * 1.5`）和 `clampCamera(player, camera)` 独立轴向截断保护（兼顾世界边界 `±29999984` 与 NaN 防护）。1.7.10 的 `FreecamClient` 与 1.21.1 的 `FreecamSession` 平移时均直接接入 `FreecamRange.clampCamera`，杜绝散落魔法数字，为后续交互范围动态化预留联动基础。同步更新 `scripts/LegacyCheck.java` 和 `scripts/CameraMotionCheck.java` 自检断言。执行 `scripts/forge1710.ps1 check` 与 `smoke` 冒烟全部通过。

- 2026-09-09 21:43：启动 lwjgl3ify 实例供用户进行真实桌面交互验收，用户人工实机体验自由视角平移、边界阻挡与滑动效果，确认功能符合预期并通过测试。据用户明确指令将相机可移动范围限制在交互范围 +50% 及 lwjgl3ify 自适应补丁提交至分支 `codex/forge-1.7.10`。

- 2026-09-09 21:46：完成 intent-completion 两道确认后实施 1.7.10 自由视角移除屏幕中心十字光标功能。在 `FreecamClient` 中订阅 `RenderGameOverlayEvent.Pre`，当事件类型为 `CROSSHAIRS` 且 `current()` 为 true 时取消事件渲染，彻底隐藏自由视角下的原版中心十字准星；退出模式或切换世界自动恢复。执行 `scripts/forge1710.ps1 smoke` 构建及 Java 8 冒烟测试验证通过（日志 `logs/tools/2026-09-09 21-45-00.log`）。

- 2026-09-09 21:51：启动 lwjgl3ify 实例供用户进行实机桌面交互验收，用户人工体验确认自由视角模式下屏幕中心十字光标已成功隐藏、方块选取正常且退出模式后准星恢复，明确反馈“验收通过，提交”。据此记录用户验收通过并准备提交至分支 `codex/forge-1.7.10`。
