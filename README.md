# 自由视角交互

Minecraft **1.7.10** / Forge **10.13.4.1614** 自由观察与建造交互 Mod，兼容原生 LWJGL2 与 lwjgl3ify。本分支是项目的默认主分支和主要开发目标；NeoForge 1.21.1 版位于 `neoforge-1.21.1` 分支。

## 主要功能

- 高位自由视角，支持平移、升降、旋转、碰撞避障和范围限制，不传送玩家。
- 在玩家周围 16 格立方体内挖掘、放置、使用方块和交互实体，服务端重新校验范围与权限。
- 自由视角法杖与升级核心，支持持久区块、速度升级、蓝图施工和 AE2 传输。
- 通用交互掉落物直接入包，背包满时安全落地并保留 NBT。
- 蓝图捕获、个人存储、权限任务、拓扑施工、物料结算和未完工任务恢复。
- 可选 AE2 rv3-beta-6 联动，支持 ME 供料、传输器绑定、多目标选择与远程终端。

## 运行

在此工作树使用 PowerShell 7：

```powershell
pwsh -File scripts/forge1710.ps1 setup
pwsh -File scripts/forge1710.ps1 smoke
pwsh -File scripts/forge1710.ps1 client
pwsh -File scripts/lwjgl3ify.ps1 smoke
pwsh -File scripts/lwjgl3ify.ps1 client
```

`setup` 安装并校验独立的 Temurin Java 25.0.4.1+1（Gradle 构建）和 Java 8u504-b01（Mod 编译及 Minecraft 运行），不修改系统 Java。使用 Gradle 9.2.1、RetroFuturaGradle 2.0.4、Forge 10.13.4.1614 与原生 LWJGL2。首次构建需要下载并反编译 Minecraft。

`check` 查看工具版本，`build` 构建产物，`smoke` 检查构建及 JAR 内容，`server` 提供服务端启动入口。服务端 EULA 需用户自行阅读决定，脚本不自动接受。`scripts/lwjgl3ify.ps1` 固定使用 lwjgl3ify 3.0.33、UniMixins 0.3.1 和 Java 25，在 `%LOCALAPPDATA%/GodviewBuild/lwjgl3ify-3.0.33/instance` 创建独立实例。

运行目录由 RFG 生成在本工作树 `run` 下，工具日志在 `logs/tools`；Mod 每次启动一份 `logs/freecam_interaction/yyyy-MM-dd HH-mm-ss.log`（相对游戏运行目录）。不要并发使用同一个游戏运行目录。

## 兼容性与开发状态

- Forge 1.7.10 源码位于 `src/forge1710`。`src/main` 保留 1.21.1 实现供跨版本参考，不参与当前构建。
- 原生 LWJGL2 已跑通相机、矩阵光标选取、16 格范围、挖掘、放置与服务端模式校验。未安装 Mod 的服务器不会确认交互，客户端保持观察模式。
- 服务端仍使用原版 C07/C08、`ItemInWorldManager`、Forge 交互/破坏/放置事件与权限检查；模式退出、死亡、换维度和断线时恢复原 reach 并清理挖掘状态。
- 原版箱子、熔炉、漏斗、酿造台、信标、末影箱、发射器、附魔台、铁砧和工作台的距离检查支持 16 格范围；第三方容器留到 lwjgl3ify/GTNH 兼容阶段逐项验证。
- lwjgl3ify 3.0.33 已在独立实例跑通主菜单、世界、自由视角和 Iron Chests 6.1.13 第三方容器；RFB/UniMixins 与本 Mod 核心转换器同时加载。
- 实验结果记录在 `ACCEPTANCE-1710.md`。

上游工具来源：[RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle)、[lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify)。

SoundManager 的异步重入窗口已在核心补丁中串行化；原生 LWJGL2 已连续启动并完成创造/生存世界交互验收，详见 `ACCEPTANCE-1710.md`。

## 许可证

本项目以 [MIT License](LICENSE) 开源。
