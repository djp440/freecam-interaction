# 项目说明

Minecraft 1.21.1 / NeoForge 的“上帝视角建造”Mod，已实现高位第三人称观察模式，尚未实现建造功能。使用简体中文和 UTF-8。

- 默认 G 切换模式（可在控制设置中改键），右上角 X 按钮退出；Esc 打开原版菜单，不退出模式。客户端代码位于 `src/main/java/local/godviewbuild/client`。
- WASD 相对视角水平平移，中键拖拽旋转；保留原版数字键/滚轮快捷栏和 E 背包。模式状态独立于 Screen，菜单期间停止相机输入，死亡/世界切换/断线清理。
- 模式只改变相机与界面，不传送玩家、不暂停世界；使用原生第三人称避障，退出恢复原视角。

- Java 21，依赖版本固定，不提前创建跨版本抽象。
- 使用 `pwsh -File scripts/dev.ps1 check|build|client|server|smoke`；安装用 `scripts/setup.ps1`。
- 不修改系统默认 Java，脚本仅在当前进程选择项目 JDK。
- 完成修改执行最小冒烟，不默认执行全量 E2E。
- `smoke` 包含无测试框架的相机运动自检；相机锚点通过最小 Access Transformer 设置，后续距离仍使用原生第三人称避障。
- Mod 日志在运行目录 `logs/godview_build`，工具日志在项目 `logs/tools`；Windows 文件名使用 `yyyy-MM-dd HH-mm-ss.log`。
- 保留 Minecraft/Gradle 原生日志，不删除用户存档，不自动接受 EULA。
- 维护 MEMORY.md，记录精确到分钟的决定及验证结果。
