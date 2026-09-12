# 移速核心实施计划（Forge 1.7.10）

状态：2026-09-11 22:28，方向与实施范围均已获用户明确确认。本轮仅规划交接，功能尚未实施。下个 Agent 按本文执行，不必重复两次确认；发现范围扩张时另行确认。

## 1. 已确认契约

- 仅修改 Forge 1.7.10（src/forge1710）；不修改 src/main 的 1.21.1 功能。
- 新增两倍移速核心、四倍移速核心；作为现有法杖升级核心物品，不可堆叠，各有 32×32 透明像素 PNG 材质与中英文名称、说明。
- 同一法杖只能安装一个移速核心，同档重复、异档混装均禁止；与蓝图核心可以共存。
- 无序配方：1 个时钟产出 1 个两倍核心；2 个两倍核心加 1 个普通下界石英块产出 1 个四倍核心。不接受錾制/柱状石英块，不添加其它材料。
- 主背包（含快捷栏，0..35）携带装有核心的法杖即生效，不要求手持或进入自由视角。散装核心、容器中的法杖不生效。
- 多把法杖统一取最高移速档，不叠加，不与当前支付耐久的法杖绑定。剩余耐久为 1 的法杖仍提供被动加速，但不恢复其施工/自由视角启动资格。加速本身不扣耐久。
- 玩家本体在其它移速效果基础上独立乘 1.25 或 1.5。例如其它效果为原速 120%，最终分别为原速 150% 或 180%。不用原版速度药水替代，不新增药水状态图标。
- 自由视角摄像机平移、升降分别乘 2 或 4，不改变旋转速度、交互范围或碰撞约束。
- 按住游戏的潜行键（默认 Shift，改键后跟随）时仅摄像机恢复未加速速度；松开恢复核心倍率。本体核心加成及原版潜行行为保留。
- 拆卸、更换、丢出法杖，死亡掉落、重生、重登、切维度均应重新计算，不残留、不重复叠加。

## 2. 已核对的复用入口

下列为本轮直接读取源码得到的事实，不代表完整调用图审计；实施前还需阅读相关完整源码与调用点。

- src/forge1710/java/local/freecaminteraction/item/IWandCore.java：同类核心接口。
- src/forge1710/java/local/freecaminteraction/item/ItemBlueprintCore.java：物品、不可堆叠、工具分类、纹理及 tooltip 的现成模式。
- src/forge1710/java/local/freecaminteraction/inventory/ContainerWandUpgrade.java：isSameCore 依据 IWandCore.getCoreId 判同类；SlotWandCore.isItemValid 遍历其它核心槽拒绝重复。应让两档移速核心共享一个类别 ID，优先复用，勿重写容器。
- src/forge1710/java/local/freecaminteraction/item/ItemFreecamWand.java：loadUpgrades/saveUpgrades/getUpgradeCore、四槽 NBT 与 tooltip 已存在。findBestWand 会跳过剩余耐久 <=1 的法杖，且按法杖品质选取，因此不能用于移速档位扫描。
- src/forge1710/java/local/freecaminteraction/FreecamWandRegistry.java：现有物品及配方注册入口。
- src/forge1710/java/local/freecaminteraction/FreecamInteraction.java：已有服务端 PlayerTickEvent 与 logout/respawn/dimension 入口；本体移速计算不能放在 ACTIVE 自由视角条件内，也不能在退出自由视角时无条件永久撤销仍应生效的被动效果。
- src/forge1710/java/local/freecaminteraction/client/FreecamClient.java：frame 中平移/升降独立于旋转；当前顺序为计算位移、范围夹取、FreecamCollision.solveTranslation、应用位置。down(gameSettings.keyBindSneak) 已提供潜行改键兼容。
- src/forge1710/java/local/freecaminteraction/client/FreecamMotion.java：pan/vertical 的基础速度均为 10.0，elapsed 被夹到 [0,0.05]。应在计算后对位移乘倍率，再走范围与碰撞，不能把 elapsed 乘倍率，否则正常帧/低帧率下倍率会失真。
- scripts/LegacyCheck.java、scripts/LegacyActionCheck.java、scripts/forge1710.ps1：复用现有无测试框架自检与产物核验入口。
- src/forge1710/resources/assets/freecam_interaction/lang/{zh_CN,en_US}.lang、textures/items/blueprint_core.png（相同 assets 根目录）、art/cores/PROMPTS.md：本地化与素材参考。

## 3. 实施步骤

### A. 读取与技术核验

1. 读取 AGENTS.md、MEMORY.md、本文及相关完整源码，检查 git status，保留已有改动。
2. 核对真实 1.7.10 movementSpeed 属性的计算、同步与保存机制。优先使用固定 UUID 的独立 AttributeModifier、总量乘法 operation=2（须由本地原版源码或字节码验证语义），不修改基础属性，不覆写其它 Mod 的 modifier。
3. 核对服务端属性变更到客户端的原生同步链路及死亡/重生生命周期。选择最小且可撤销的实现；重登后清除/替换同 UUID 的旧值，避免持久化残留。不得以每 Tick 重复叠加 modifier 实现。

### B. 物品与生效档位

1. 以最少代码实现两档物品并注册，共享移速类别 ID，复用升级槽互斥与四槽保存。
2. 注册两个无序配方，四倍配方明确消耗两件不可堆叠的两倍核心，石英块限定 metadata=0。
3. 提供一处共享的主背包最高档位计算，客户端相机与服务端本体复用。空玩家/背包、空槽、非核心物品安全处理；非法重复核心 NBT 也不得叠加效果。
4. 不改变 findBestWand、耐久结算、自动接续、蓝图资格等既有行为。

### C. 本体与相机

1. 服务端按库存实际档位维护唯一移速 modifier，只在档位变化或发现残留时增删更新；无核心回到无本 Mod 加成，保留所有其它 modifier。
2. 生命周期中清理旧实体状态并在新实体按实际背包恢复，兼顾死亡掉落与保留背包；避免跨会话缓存泄漏。
3. frame 中将 pan 两个分量、vertical 位移乘以有效倍率；潜行键按下倍率取 1。保持旋转分支不变，范围夹取与 swept 碰撞仍处理倍率后的位移。
4. 复用 ModLog 记录加速档位变化/清理，不按帧或每 Tick 刷日志；沿用现有单次启动日志文件机制，不新增日志框架。

### D. 资源与说明

1. 使用 generate_image 生成核心素材，沿用蓝图核心视觉语言，同时让两档形状/色彩有明确区分；导出 32×32 透明 PNG 并目视检查实际像素与透明边缘。
2. 保存必要生成提示词/来源至 art/cores；导出或检查需要的新常用命令写成可复用脚本，优先复用既有资源脚本。
3. 添加中英文名称及说明，包含本体/相机倍率、携带生效、同类互斥及潜行临时原速规则；不新增 GUI 或药水图标。

### E. 冒烟与交付

1. 在现有脚本中加入最小可运行回归断言，按第 4 节验收。
2. 执行 pwsh -File scripts/forge1710.ps1 smoke。若既有脚本过大，可拆出可复用的定向检查，但仍验证编译和资源打包，不默认跑全量 E2E。
3. 如实际涉及 lwjgl3ify 运行或用户要求实例更新，再使用现有 scripts/lwjgl3ify.ps1 流程；不将自动化检查表述为游戏内验收。
4. 更新 AGENTS.md、MEMORY.md（精确到分钟、倒序）及计划执行状态。交付注明产物、已验证项目和未做的实机项目。没有单独授权不提交、不启动游戏或删除用户数据。

## 4. 验收清单

- [ ] 两个配方输入数量、输出数量准确，非普通石英块不匹配。
- [ ] 两档不可堆叠；普通放入和 Shift 搬运均拒绝重复/混装，与蓝图核心共存；退出 GUI、重新打开后 NBT 保留。
- [ ] 无核心倍率为 1；散装核心不生效；背包多法杖取最高，交换槽位不改变结果；耗尽法杖仍有被动效果。
- [ ] 本体无其它加成时为 1.25/1.5；有其它 1.2 倍效果时为 1.5/1.8；反复更新不累乘，移除仅去掉本 Mod modifier。
- [ ] 四倍切两倍、移除最后一把法杖、拆卸核心后效果正确变化；死亡/重生（含保留背包）、重登、切维度无残留与重复。
- [ ] 相机同输入同 elapsed 下位移为 2/4 倍，覆盖常规 elapsed 与大于 0.05 的帧时间，确保不是缩放时间参数。
- [ ] 潜行按下/松开分别为 1 倍/核心倍率，改键有效，本体加成不被清除；旋转路径未乘倍率。
- [ ] 四倍位移仍经过既有边界、碰撞与液面保护，菜单/失焦期间不引入相机移动。
- [ ] 资源在 JAR 中，32×32 RGBA 材质可见、无黑底、两档清晰可辨，中英文本可用。
- [ ] 日志只在状态变化输出，冒烟成功；明确区分自动断言与实机待验项目，特别是联机同步、潜行手感和高速碰撞。

## 5. 排除项与风险边界

- 不做 1.21.1 移植、专属 Buff 图标、新升级界面、配置系统、额外核心档位、范围扩大或旋转提速。
- 不重构蓝图、容器、法杖耐久系统，不为本功能创建通用 Buff 框架，不添加非必要依赖。
- 当前只确认使用原生属性机制的方案方向，尚未核验 operation/sync/save 的真实实现，下一 Agent 必须核验，不能把计划当作已验证事实。
- 如发现必须新增依赖、修改网络协议或引入其它用户可观察行为，暂停提出增量范围确认；纯内部实现细节不重复询问。
