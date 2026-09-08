# 项目记忆

- 2026-09-08 20:04：用户完成 intent-completion 两道确认，将本次范围收敛为仅 Minecraft 1.21.1。确认名称“上帝视角建造”、ID `godview_build`、包名 `local.godviewbuild` 与 Windows 兼容日志命名。
- 2026-09-08 20:06：原有 Oracle JDK 17.0.12、Git 2.53.0；独立安装 Temurin JDK 21.0.12.1，不改系统环境变量。项目原为空。固定官方 MDK 提交及构建依赖，不引入其他版本或游戏功能。
- 2026-09-08 20:12：`scripts/setup.ps1` 成功下载并校验 JDK；`scripts/dev.ps1 check` 显示 Gradle 9.2.1/JDK 21；非法操作返回非零并写入日志；`scripts/dev.ps1 smoke` 构建成功，验证 JAR 入口类、日志模块、中文元数据和精确 1.21.1 范围。
