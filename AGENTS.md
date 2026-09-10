# 项目说明

Minecraft 1.21.1 / NeoForge 与 Forge 1.7.10 的“自由视角交互”Mod，已实现高位第三人称观察与范围内光标建造交互。使用简体中文和 UTF-8。

- 默认 G 切换模式（可在控制设置中改键），右上角 X 按钮退出；Esc 打开原版菜单，不退出模式。客户端代码位于 `local.freecaminteraction.client`。
- WASD 相对视角水平平移，中键拖拽旋转；保留原版数字键/滚轮快捷栏和 E 背包。模式状态独立于 Screen，菜单期间停止相机输入，死亡/世界切换/断线清理。
- 模式只改变相机与界面，不传送玩家、不暂停世界；使用原生第三人称避障，退出恢复原视角。
- 交互以玩家脚底坐标为中心，边长 16，按方块中心判定，下界包含、上界不包含；相机光标射线不跳过前方遮挡。放置落点也校验范围。
- `FreecamInteraction` 管理客户端/服务端模式及范围，替换模式内距离、放置边缘与输入入口；保留原版计时、权限和事件系统。联机未协商支持时仅观察。桶及其子类由服务端复核光标方块后继续走原版桶逻辑，不使用玩家朝向二次射线；空桶可选流体源，实际落点仍需在范围内。
- `FreecamSelection` 使用渲染矩阵选取，按实际形状外露面渲染；中键旋转、界面、失焦后需松开交互键再按下。Shift 保留辅助使用；新增实体交互走服务端授权与原生事件链，尚需按版本单独验收。

- Java 21（1.21.1）/ Java 8 与 25（1.7.10），依赖版本固定，不提前创建跨版本抽象。
- 使用 `pwsh -File scripts/forge1710.ps1 setup|check|build|smoke|client|server` 进行 1.7.10 开发；`scripts/dev.ps1` 用于 1.21.1。
- 不修改系统默认 Java，脚本仅在当前进程选择项目 JDK。
- 完成修改执行最小冒烟，不默认执行全量 E2E。
- `smoke` 包含无测试框架的相机运动与交互几何自检；交互检查使用主源码依赖类路径，不另引入测试框架。相机锚点设置后后续距离仍使用原生第三人称避障。
- Mod 日志在运行目录 `logs/freecam_interaction`，工具日志在项目 `logs/tools`；Windows 文件名使用 `yyyy-MM-dd HH-mm-ss.log`。
- 保留 Minecraft/Gradle 原生日志，不删除用户存档，不自动接受 EULA。
- 维护 MEMORY.md，记录精确到分钟的决定及验证结果。

## 当前工作树：Forge 1.7.10 实验

- 本工作树分支 codex/forge-1.7.10；代码在 src/forge1710，src/main 为 1.21.1 迁移参考，不参与当前 1.7.10 构建。
- 使用 scripts/forge1710.ps1 setup|check|build|smoke|client|server；构建 JDK 25，编译与原生 LWJGL2 游戏使用 Java 8。
- 基础相机平移、旋转、背包返回及玩家坐标不变已由用户人工确认；光标选取也由用户人工确认。
- 原生 LWJGL2 已接通模式握手、创造/生存挖掘、放置和服务端范围校验；原版容器距离入口已补丁，第三方容器已在 lwjgl3ify 环境验证。
- 先原生 LWJGL2，再加入 lwjgl3ify；后者兼容不代表整个 GTNH 整合包兼容。
- 1.7.10 的 `World.rayTraceBlocks` 会原地改写起点，必须传入副本；两端共用 `FreecamTarget.pick`，服务端不得仅凭客户端实体 ID 跳过遮挡复核。工具使用沿用 `EntityPlayer.interactWith`，禁止手写兜底绕过事件取消。
- `LegacyActionCheck` 用隔离世界桩调用真实原版射线，覆盖起点不变、远处生物优先于背景方块、前景墙体遮挡，并检查真实交易 AI 与 `ItemBucket` 目标替换字节码补丁；不代表桶、剪羊毛或交易 GUI 实机验收。

- SoundManager/OpenAL 初始化竞争已通过串行化原版加载入口修复，连续启动无上下文冲突；详见 ACCEPTANCE-1710.md。
