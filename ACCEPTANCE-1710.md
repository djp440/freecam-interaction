# Forge 1.7.10 实验验收

使用 real-user-acceptance 路径记录，结果只依据本次日志与目视观察。

## 隔离

工作树：`C:/Users/15575/project/上帝视角建造-forge1710`，分支 `codex/forge-1.7.10`。使用 RFG 创建的全新 run 目录，不读取或修改原有游戏存档。原生 LWJGL2 阶段不安装 lwjgl3ify 或 GTNH 整合包。

## 用户路径和成功判据

1. 按 README 执行 setup：Java 25 构建工具与 Java 8 运行工具均能显示版本，重复运行复用安装。
2. 执行 smoke：生成旧版 JAR，包含 Forge 入口、中文元数据和日志模块，不含 NeoForge 元数据。
3. 执行 client：真实看到 Minecraft 1.7.10 主菜单，并在 Mods 列表看到上帝视角建造；本地日志确认 Java 8 与 LWJGL2。
4. 创建隔离实验世界：能够进入世界，保存退出回到主菜单，无 Mod 初始化崩溃。

## 结果

进行中。首次构建发现 RFG 2.0.4 字节码要求 Java 25，已据此分离构建 JDK 与游戏 Java 8。功能迁移与 lwjgl3ify 对比尚未验收。

## 2026-09-09 12:43 阶段验收

- 用户人工确认通过：平移、旋转、背包返回，以及相机移动时玩家坐标不变。凭证为本次用户明确反馈，不扩展为其他边界场景通过。
- 已完成：Java 环境安装、产物及 Java 8 运动自检、真实主菜单与 Mod 列表、新世界创建、保存退出和重新进入。
- 截图：logs/acceptance/1710-mods.png、1710-world.png、1710-saved.png、camera-g2.png、camera-pan.png、camera-back.png。
- 原生 OpenAL 加载错误已通过纯英文 native 缓存路径修复；未进行听觉验证。
- 尚未完成：光标选取、范围建造、服务端交互移植，以及 lwjgl3ify 对比。
- 实验世界保留供后续对比；用户正在人工验收，本轮不发送游戏输入或关闭其客户端。

## 2026-09-09 12:53 光标选取阶段

- 通过：编译、JAR、运动和范围边界冒烟，日志 logs/tools/2026-09-09 12-49-35.log。
- 新增：矩阵反投影光标选取、范围线框、原版选中包围框；仍禁用建造。外露面呼吸高亮尚未移植。
- 卡死：启动时两个音频加载线程竞争 OpenAL context，随后原版音乐更新发生 UnsatisfiedLinkError。崩溃凭证 run/crash-reports/crash-2026-09-09_12.51.19-client.txt。本次未进入世界，不能声称选取位置或遮挡已经目视验收。
- 修正此前结论：纯英文 native 缓存解决了之前 DLL 查找问题，音频初始化仍有独立的并发问题。客户端进程已退出。

## 2026-09-09 19:24 原生 LWJGL2 建造阶段

- 用户人工确认矩阵光标选中可用；不重复验收既有相机平移、旋转、背包返回和玩家坐标不变。
- 启动稳定性：核心补丁把原版 `SoundManager.loadSoundSystem` 中的异步启动改为在其同步锁内完成，关闭 `loaded` 尚未置位时的重入窗口。多次真实启动均完成 OpenAL 清理与重新初始化，没有再次出现 `Only one OpenAL context` 或 `nalGetSourcei` 崩溃；最近一次日志 `logs/tools/2026-09-09 19-20-58.log`。
- 网络根因：旧实现把同一 `Mode` 消息类注册为两个 discriminator，1.7.10 编码表后注册覆盖前注册，客户端请求被错误编码。已拆为 `ModeRequest` 与 `ModeAck`，实际日志确认通道注册、服务端模式开启/关闭和客户端确认。
- 真实交互：创造世界左键移除选中方块、右键放置海绵成功，截图 `logs/acceptance/interaction-break.png`、`logs/acceptance/interaction-place.png`；生存世界持续左键 1.5 秒后选中目标移除，截图 `logs/acceptance/survival2-mode.png`、`logs/acceptance/survival2-break.png`。
- 服务端校验：模式仅在服务端 tick 应用；扩大 reach 后仍走原版 C07/C08、`ItemInWorldManager`、Forge 事件、出生点保护和权限检查。交互目标、破坏结果、单/多方块放置结果均按玩家脚底 16 格范围检查，越界取消并清理挖掘。
- 原版容器的距离调用已定点路由到相同范围函数，启动时七个方块实体类成功转换；三个容器类在实际打开时按需加载。第三方 Mod 自定义容器尚未覆盖，留待 lwjgl3ify/GTNH 阶段验证。
- 最终 `scripts/forge1710.ps1 smoke` 通过，日志 `logs/tools/2026-09-09 19-27-06.log`；客户端正常退出，日志确认服务端模式关闭并恢复。lwjgl3ify 尚未加入。

## 2026-09-09 20:02 lwjgl3ify 对比验收

- 固定版本：lwjgl3ify 3.0.33、UniMixins 0.3.1、Iron Chests 6.1.13、Java 25.0.4.1；独立实例位于 `%LOCALAPPDATA%/GodviewBuild/lwjgl3ify-3.0.33/instance`，不污染原生 LWJGL2 的 `run`。
- `scripts/lwjgl3ify.ps1 smoke` 通过；客户端日志确认 RFB、UniMixins、LWJGL3 3.4.2、Iron Chest 和上帝视角建造共存，日志 `logs/tools/2026-09-09 19-44-44.log`。
- 真实目视通过：主菜单、创建世界、上帝视角 HUD、相机平移；使用 `/give` 获得 Iron Chest，放置并在相机远离玩家后仍可选中和打开第三方容器。截图 `logs/acceptance/lwjgl3ify-main.png`、`lwjgl3ify-godview.png`、`lwjgl3ify-ironchest-placed.png`；用户已人工确认该路径成功。
- 核心日志确认 `cpw.mods.ironchest.TileEntityIronChest` 被同一容器距离转换器改写；LWJGL3 路径跳过仅适用于原生 LWJGL2 的 SoundManager 串行化补丁，OpenAL 正常初始化。
- 结论：该 Mod 已在此最小 Forge 1.7.10＋lwjgl3ify 实例完成功能对比；尚未等同于整个 GTNH 整合包兼容，也未覆盖所有第三方硬编码距离逻辑。

## 2026-09-09 21:12 更名与 lwjgl3ify 游戏内真实验收

- 全量更名完成：显示名称“自由视角交互”，Mod ID `freecam_interaction`，包名 `local.freecaminteraction`，类名 `Freecam*`，日志目录 `logs/freecam_interaction/`。
- 使用 `minecraft-gameplay` 技能驱动 lwjgl3ify 独立实例进行真实交互测试：
  - Mods 列表：成功进入 Mod List 界面，显示“自由视角交互”，详情面板确认 Mod ID 为 `freecam_interaction`，截图 `logs/acceptance/freecam-mod-details.png`。
  - 世界内交互：创建并进入测试世界，按下 G 键激活模式，高位俯视视角生效，HUD 正确显示标题“Freecam Interaction”与按键指引，截图 `logs/acceptance/freecam-mode-active.png`。
  - 退出恢复：再次按 G 键成功退出自由视角，相机与控制权恢复原位，网络注销成功；正常保存退出世界并关闭客户端，截图 `logs/acceptance/freecam-title-returned.png`。
  - 日志闭环：独立日志 `logs/freecam_interaction/2026-09-09 21-05-09.log` 完整记录整个会话生命周期。全部验收通过。
