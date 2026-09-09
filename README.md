# 自由视角交互：Forge 1.7.10 移植实验

本分支 `codex/forge-1.7.10` 在独立工作树进行迁移。先验证原生 LWJGL2，再加入 lwjgl3ify 对比；不安装 GTNH 整合包。

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

## 当前迁移状态

- 旧版实验源码：`src/forge1710`。`src/main` 保留已提交的 1.21.1 实现供迁移参考，不参与当前构建。
- 原生 LWJGL2 已跑通相机、矩阵光标选取、16 格范围、挖掘、放置与服务端模式校验。未安装 Mod 的服务器不会确认交互，客户端保持观察模式。
- 服务端仍使用原版 C07/C08、`ItemInWorldManager`、Forge 交互/破坏/放置事件与权限检查；模式退出、死亡、换维度和断线时恢复原 reach 并清理挖掘状态。
- 原版箱子、熔炉、漏斗、酿造台、信标、末影箱、发射器、附魔台、铁砧和工作台的距离检查支持 16 格范围；第三方容器留到 lwjgl3ify/GTNH 兼容阶段逐项验证。
- lwjgl3ify 3.0.33 已在独立实例跑通主菜单、世界、自由视角和 Iron Chests 6.1.13 第三方容器；RFB/UniMixins 与本 Mod 核心转换器同时加载。
- 实验结果记录在 `ACCEPTANCE-1710.md`。

上游工具来源：[RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle)、[lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify)。

SoundManager 的异步重入窗口已在核心补丁中串行化；原生 LWJGL2 已连续启动并完成创造/生存世界交互验收，详见 `ACCEPTANCE-1710.md`。
