# 项目说明

Minecraft 1.21.1 / NeoForge 的“上帝视角建造”Mod，已实现高位第三人称观察模式，尚未实现建造功能。使用简体中文和 UTF-8。

- 默认 G 进入（可在控制设置中改键），X 或 Esc 退出；客户端代码位于 `src/main/java/local/godviewbuild/client`。
- 模式只改变相机与界面，不传送玩家、不暂停世界；使用原生第三人称避障，退出恢复原视角。

- Java 21，依赖版本固定，不提前创建跨版本抽象。
- 使用 `pwsh -File scripts/dev.ps1 check|build|client|server|smoke`；安装用 `scripts/setup.ps1`。
- 不修改系统默认 Java，脚本仅在当前进程选择项目 JDK。
- 完成修改执行最小冒烟，不默认执行全量 E2E。
- Mod 日志在运行目录 `logs/godview_build`，工具日志在项目 `logs/tools`；Windows 文件名使用 `yyyy-MM-dd HH-mm-ss.log`。
- 保留 Minecraft/Gradle 原生日志，不删除用户存档，不自动接受 EULA。
- 维护 MEMORY.md，记录精确到分钟的决定及验证结果。
