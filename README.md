# 上帝视角建造

Minecraft **1.21.1** / NeoForge **21.1.249** 上帝视角建造 Mod，不兼容其他 Minecraft 版本。

## 环境与快捷命令

在项目目录打开 PowerShell 7：

```powershell
pwsh -File scripts/setup.ps1
pwsh -File scripts/dev.ps1 check
pwsh -File scripts/dev.ps1 smoke
pwsh -File scripts/dev.ps1 client
```

- 安装脚本校验并安装 Temurin JDK 21.0.12.1+1 到 `%LOCALAPPDATA%\GodviewBuild\jdk-21.0.12.1+1`，不更改系统 Java 设置。重复执行复用安装。
- 使用 Gradle Wrapper，无需全局 Gradle。首次构建需要网络下载开发依赖，可能耗时较长。失败后查看 `logs/tools` 并重试相同命令。
- `build` 只构建；`smoke` 构建后验证 JAR 类文件、中文元数据和版本范围。
- 产物：`build/libs/godview_build-0.1.0.jar`，用于 **1.21.1 + NeoForge 21.1.249 或兼容的更新 21.1 版本**实例的 `mods` 目录。
- 客户端运行目录为 `run/client`，与现有游戏实例、存档隔离。
- IDE 以项目根目录导入 Gradle，Gradle JVM 和项目 SDK 选择上述 JDK 21。

## 上帝视角操作

- 默认 G 进入/退出，也可点击右上角 X 退出；支持在控制设置中修改切换键。
- WASD 相对视角在水平面平移，按住鼠标中键拖拽旋转，松开停止；不移动或转动玩家。
- 数字键和滚轮沿用原版快捷栏选择，E 打开背包。Esc 打开游戏菜单，可进入设置，不退出模式。
- 背包、菜单和设置关闭后保留相机位置与角度；打开界面或窗口失焦时停止相机操作。
- HUD 显示操作说明和当前绑定键；F1 隐藏 HUD 后仍可用切换键退出。
- 光标选取范围内首先命中的方块面，外露面轻微提亮并以约 2 秒周期呼吸闪烁；不穿墙选取，也不选取实体。
- 按住左键挖掘，右键使用方块/放置物品，Shift 保留原版辅助交互；挖掘速度、耐久、材料消耗及主副手顺序沿用原版。攻击/使用改键仍生效，中键保留给旋转。
- 范围以玩家脚底位置为中心，XYZ 各延伸 8 格，按方块中心判定（下界包含、上界不包含）；随玩家而非相机变化。普通放置落点也必须在范围内。
- 打开界面、失焦、中键旋转和退出模式时中止操作；恢复后需松开攻击/使用键再按下，防止误挖误放。死亡、切换世界、断线清理模式。
- 相机仅观察客户端已经加载的世界，不额外请求远处区块；原生相机避障可能缩短观察距离。
- `scripts/dev.ps1 smoke` 包含相机运动、范围边界、射线方向、台阶/楼梯外露面自检；实际交互需在客户端体验。

### 服务端与兼容边界

- 单人世界内置服务端支持交互；联机需要服务器安装相同版本 Mod。服务器未支持协议时显示提示，仅保留观察模式。
- 服务端重新检查范围、世界高度、边界和区块有效性，继续执行原版权限及 NeoForge 事件。容器有效性沿用原版目标检查，仅在模式内替换距离判断，不保证领地插件或其他 Mod 必然允许操作。
- 原版方块及使用标准交互链的 Mod 是兼容目标。自行检测射线、硬编码距离或依赖玩家朝向的物品可能需要专项适配；没有针对具体第三方 Mod 的兼容测试。
- 不传送或转动玩家来伪造操作位置，因此依赖玩家朝向的放置仍以玩家朝向为准；实体操作、范围设置界面、线框提示、批量建造和撤销不在本次范围。
- 通过 NeoForge 标准放置事件产生的多方块放置也会校验范围；第三方连锁破坏、异步或自行写世界的副作用不保证被限制在范围内。

## 服务端启动

```powershell
pwsh -File scripts/dev.ps1 server
```

运行目录为 `run/server`。首次可能因未接受 Minecraft EULA 退出；请自行阅读生成的协议说明，再决定是否修改 `eula.txt`。脚本不自动接受协议或关闭在线认证。

## 日志与修改入口

- 入口：`src/main/java/local/godviewbuild/GodviewBuild.java`。
- `ModLog.LOGGER` 记录 Mod 消息；每次启动一份 `run/client/logs/godview_build/yyyy-MM-dd HH-mm-ss.log`（服务端对应 `run/server`）。Windows 文件名不能包含冒号。
- 每次工具调用一份 `logs/tools/yyyy-MM-dd HH-mm-ss.log`，游戏及 Gradle 原生日志照常保留。
- 配置：`gradle.properties`。修改 Mod ID 时同步修改入口常量、包／资源位置和冒烟脚本。
- 元数据暂用 `All Rights Reserved`，不代选开源许可证；未初始化 Git 或创建提交。

## 来源

以官方 `NeoForgeMDKs/MDK-1.21.1-ModDevGradle` 提交 `70d335c962ee8a773b38fb0690c7e7f30d1bafa6` 的构建配置和 Wrapper 为基础。未加入示例物品、发布插件、自动 JDK 解析、Parchment 或跨版本框架。
