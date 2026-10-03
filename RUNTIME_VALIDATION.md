# 去 TabooLib 远端验证

时间：2026-09-26 00:51–01:04（Asia/Shanghai）。
主机：s1.dimc.cloud；服务根目录：`/home/xiaozhangup/Minecraft`。

## 结果

| 节点 | tmux | 结果 |
| --- | --- | --- |
| SlimeCargo-Master | ma:0.0 | CarbKotlin、Cubozoa、SlimeMasterNext 启动成功；数据库、API、传输服务初始化成功；节点和 MSPT 查询成功 |
| SlimeCargo-Lobby | lo:0.0 | 9 个迁移插件及 Pipes、Crab 启动成功；本轮日志无 ERROR、类加载异常或插件禁用 |
| SlimeCargo-Worker-1 | wo1:0.0 | 共享插件启动成功；时装缺失及其抽奖模块连带错误按用户要求跳过；原有 CraftEngine 工作流警告保留 |
| SlimeCargo-Worker-2 | wo2:0.0 | 同 Worker-1 |

Worker-3 未操作会话或单独测试。用户指定的 `./U.sh sc update q` 自身会同步 update 目录到全部 Worker，包括 Worker-3。

以上为首轮检查范围，当时未覆盖所有 WARN。后续排查发现 Lobby 的 CreativeSubset 顺序问题，以及 Worker-3 的待更新目录被后一次同步覆盖，详见文末补充。

没有发现重复类、LinkageError、ClassCastException 或其他 classloader 冲突；没有实施任何 classloader 冲突修复。

## 修复

1. **Crab Paper Placeholder 自动注册**
   - 首轮异常：`NoSuchMethodException: WhaleVisitor$activeModules$2.<init>()`。
   - 原因：Whale 的匿名 Placeholder 适配器捕获了模块实例，需手动创建；自动扫描却再次尝试无参构造。
   - 修复：自动注册排除匿名适配器，保留 Whale 已有的显式注册。
   - 验证：Whale 成功启动，其依赖插件缺少 Whale 类的连带异常消失；三个 Paper 节点均成功解析 `network-online=%network_online%`，返回 `network-online=0`。
2. **Pipes FlexibleItem 接口**
   - 首轮异常：`NoClassDefFoundError: me/xiaozhangup/slimecargo/utils/flexible/FlexibleItemHandler`。
   - 修复：注册表和 Handler 导入改为 Crab 的 `flexible` 包；编译依赖改为 `me.xiaozhangup.crab:CarbKotlin:2.3.20:paper`，关闭传递依赖。
   - 验证：构建、本地 Maven 发布成功；三个 Paper 节点加载 Pipes 并成功执行 `pipes info`。
   - Pipes 已列于 PROJECTS.md，不重复添加。

## 实际执行的功能验证

- `velocity plugins`：包含 CarbKotlin、Cubozoa、SlimeMasterNext。
- `slimemaster node` / `node worker` / `mspt`：Lobby、Worker-1、Worker-2 在线；两个 Worker 有实时 MSPT 返回。
- `plugins`：所有本次更新的 Paper 插件均完成启用，第二轮无插件禁用记录。
- `whale module Cosmetics`：命令和模块查询正常；仅证明模块已注册，不代表缺少资源的 Worker 时装初始化成功。
- `papi list` / `papi parse --null network-online=%network_online%`：扩展注册及实际解析成功。
- `tardigrade events`：三个节点均返回 73 个事件；`tardigrade instances` 返回 0 个当前实例。
- `opossum reload`：Lobby 返回配置重载成功。
- `dolphinsync` / `poly list`：命令解析和输出正常。
- `pipes info`：各节点查询成功，无孤立显示实体。
- `tps`：三个 Paper 节点当前 1 分钟 TPS 为 20。
- `save-all flush`：三个节点均完成保存，没有事件分发异常。
- Whale、DolphinSync、Raven、SlimeMaster 的 Hikari 数据源成功启动；Raven 加载 178 个任务并挂钩 SlimeCargo。

无参数 `tardigrade` / `opossum` 返回命令不完整，随后有效子命令验证成功。`slimecargo_platform` 的空玩家请求返回未实现，与原 TabooLib Placeholder 的 OfflinePlayer 默认实现一致，未为这次验证改动原有语义。

## 未修改的问题与验证边界

- `Unknown resource pack: default` 已出现在 Worker 的 2026-09-24 启动日志，属于迁移前已有的 CraftEngine 配置警告。
- Worker 缺少 `craftengine:backpack_plus:bastion_backpack` 等资源，导致 Cosmetics 初始化失败以及 CosmeticLottery 连带报错。用户确认资源尚未同步，明确要求不处理。
- 未进行玩家登录后的菜单点击、物品交互、跨服背包同步等客户端验收。远端原有维护状态保持不变。

## 部署与保留

- Master 原有三个 jar 直接替换；Lobby 独占的 OrangDomain、SharkChest 直接替换。
- 共享插件仅放入 Lobby 的 `plugins/update`，通过 `./U.sh sc update q` 同步，再由正常重启应用。未直接改写 Worker 的插件目录。
- 保留原安装文件名 `CarbKotlin-2.3.20.jar`，分别使用对应平台运行包，避免同一目录出现两份 Crab。
- SHA-256 验证：Master 3 份、Lobby 11 份、Worker-1 9 份、Worker-2 9 份，均与本地构建一致。
- 旧 jar 备份位于远端 `migration-backup-20260926-005101`；未删除任何非 jar 文件。初轮启动日志已由服务器正常日志轮转保留。
- 本轮重新发布了 Crab API 和 Pipes 到本地 Maven，源码改动未提交，保留原工作区已有改动。

## 01:14 启动日志复查

对照各节点 `logs/2026-09-24-3.log.gz` 与本次最新启动日志，确认以下新增问题：

1. **Lobby：CreativeSubset 缺少 69 个书本条目**
   - Crab 扫描按类名排序，CreativeSubset 在 CustomRecipe 注册配方条目前执行，分组时找不到这些条目。
   - 初次修复采用 `afterInit()`，随后按用户要求撤销，最终改为 `@WhaleDepend` 声明 Unit 加载依赖，见下节。
   - 修复涉及 WhaleMechanism 1 个插件、4 个源码文件：WhaleModule.kt、WhaleVisitor.kt、CreativeSubset.kt、CreativeGuide.kt。
   - Whale 构建与本地 Maven 发布成功，运行包已上传到约定的 Minecraft 目录。
2. **Worker-3：Pipes 新旧注册表不一致**
   - Pipes 和 Crab 已更新，但包括 Whale、SlimeCargo 在内的其他共享插件仍是旧 jar；Whale 日志仍有旧 TabooLib 入口。
   - `U.sh sc update q` 对目标 update 目录执行整体替换。第二轮只同步 Crab、Pipes，覆盖了 Worker-3 尚未应用的其他更新包。这是前一轮部署遗漏。
   - 重新在 Lobby 的 update 目录准备完整 9 个共享插件，经 U.sh 同步；四个节点的待更新 jar 均通过 SHA-256 校验。Worker-3 旧 jar 备份于原备份目录的 `Worker-3-before-full-update/`。
   - Worker-3 实际位于另一台托管服务器，本机为 SSHFS 文件挂载；用户负责通过面板重启。

NBTAPI、PlugManX、SLF4J、旧 MySQL 驱动、spark 统计超时等对应提示在 9 月 24 日已有，未作为本次迁移回归修改。Worker 的时装资源缺失继续按用户要求跳过。

### 补充验证结果

- Lobby 于 01:29:27 完成启动；原有 69 条 CreativeSubset 警告归零，无配方缺失、模块初始化失败或新增 WARN/ERROR。`customrecipe list` 返回 125 个配方，`pipes info` 查询成功。
- Worker-1、Worker-2 分别于 01:31:25、01:31:26 完成启动；没有 CreativeSubset、Pipes 配方、类加载异常，仅保留未同步时装资源导致的 Cosmetics / CosmeticLottery 错误。
- 上述三个节点各 9 个共享插件的已安装 jar 均与最新本地产物 SHA-256 一致，update 目录已消费完毕。
- Worker-3 最初于 01:27:16 的启动早于同步完成。用户再次从面板重启后，于 01:34:27 完成启动，9 个已安装 jar 均与最新构建 SHA-256 一致，待更新目录已消费完毕。
- Worker-3 的 Pipes 配方缺失、CreativeSubset 文件处理异常及旧 TabooLib 调用栈均消失，无类加载异常。时装资源缺失继续忽略；spark 统计超时在 9 月 23 日已有，BiliBili 消息查询超时在 9 月 21 日已有。
- 本轮同步前确认所有目标 update 目录中没有非 jar 文件。没有直接改写 Worker 的 plugins 文件，没有删除配置或其他非 jar 文件。

## WhaleDepend 替换 afterInit

- 新增 `WhaleMechanism/src/main/kotlin/me/xiaozhangup/whale/tag/visitor/WhaleDepend.kt`，支持 `@WhaleDepend("Unit名称", "另一Unit名称")`，以 `@WhaleUnit.name` 匹配。
- `WhaleVisitor.kt` 在实例化前按依赖关系排序；注册顺序与 `init()` 顺序保持一致，仍然先注册全部 Unit，再初始化全部 Unit。依赖缺失、被禁用或成环时报告相关名称并跳过受影响的 Unit；重复 Unit 名称明确报错。
- 已删除 `WhaleModule.afterInit()` 及调用，CreativeSubset 恢复通过 `init()` 初始化，手动重载仍调用 `init()`。
- 扫描 150 个 Unit，为其中 10 个 Unit 声明 14 条依赖关系：

| Unit | 依赖 | 源码位置（相对 WhaleMechanism/src/main/kotlin/me/xiaozhangup/whale） |
| --- | --- | --- |
| CreativeSubset | CreativeGuide、CreativeDisplay、CreativeRecipe、CustomRecipe | module/CreativeSubset.kt |
| CreativeDisplay | CreativeGuide | module/CreativeDisplay.kt |
| CreativeRecipe | CreativeGuide | module/CreativeRecipe.kt |
| CosmeticLottery | Cosmetics | module/shop/CosmeticLottery.kt |
| ActionGuide | ActionbarOverlay | module/ActionGuide.kt |
| KauCim | Sign | module/KauCim.kt |
| Villager | Sign、Coins | module/shop/Villager.kt |
| Payment | HandMap | module/payment/Payment.kt |
| EnchantmentRender | EnchantmentMenu | module/enchantment/ui/EnchantmentRender.kt |
| MiniBlock | HeadDatabase | module/MiniBlock.kt |

CosmeticLottery 同时移除原先用于等待 Cosmetics 的一 tick 启动任务及其取消状态，改为按依赖顺序直接加载卡池。玩家交互和抽奖动画自身的调度保持原有行为。

CreativeGuide 的手动重载反向引用，以及 ChatCommand、IdleDetector、Cosmetics、RareMobs 等已有的可选联动不声明为硬依赖；CustomRecipe 在没有 CreativeGuide 时仍可只注册普通配方。背包类型识别等静态工具调用不依赖对应 Unit 的加载，BiomeInquirer 属于物品实现，也不是 WhaleUnit。

Whale 构建与本地 Maven 发布成功，产物上传至约定目录；完整共享更新包通过 Lobby 的 update 目录及 `U.sh sc update q` 同步。01:51:00 在 ma 执行一次 `slimemaster execute * stop`，四个服务端均正常停止并自动重启，包括 Worker-3。

### 本轮实际验证

- 启动完成时间：Worker-3 01:51:40；Worker-1、Worker-2 01:52:26；Lobby 01:52:33。
- 四个节点各 9 个已安装共享 jar 均通过最新本地产物 SHA-256 校验，待更新目录已消费完毕。
- 没有 Unit 依赖缺失、循环依赖、CreativeSubset 条目缺失、配方缺失或 classloader 异常。Lobby 对照旧启动日志没有新增 WARN/ERROR；Worker 仅有已确认忽略的时装资源缺失及 CosmeticLottery 连带错误。
- 通过 ma 广播 `customrecipe list`、`pipes info`，四个节点均返回 125 个自定义配方，Pipes 查询成功且没有孤立显示实体。
- SlimeMaster 确认 lobby、worker-1、worker-2、remote-1（Worker-3）在线；三个工作节点 MSPT 分别约 0.65、0.66、0.36。
- MiniBlock 仍按原配置禁用，其依赖声明已构建，但没有进行对应运行功能验证。没有新增测试单元，没有删除非 jar 文件。

## CE 同步后的重启复查（2026-09-26 01:59–02:04）

用户完成 CE 同步后，于 01:59:29 从 ma 执行一次 `slimemaster execute * stop`。Master 保持运行作为控制端，Lobby 和三个 Worker 均自动重启；本轮没有更新 jar 或删除文件。

| 节点 | 启动完成时间 | WARN 行数 | ERROR 行数 |
| --- | --- | --- | --- |
| Lobby | 02:01:00 | 21 | 0 |
| Worker-1 | 02:00:55 | 19 | 0 |
| Worker-2 | 02:00:56 | 19 | 0 |
| Worker-3 | 02:00:13 | 20 | 0 |

- 四端均加载 CE cosmetics 包并注册 cosmetics 占位符。先前的时装物品缺失、Cosmetics / CosmeticLottery 初始化失败均未再出现；没有 CreativeSubset 条目缺失、Unit 依赖错误或 classloader 异常。
- 02:03:24 SlimeMaster 确认 lobby、worker-1、worker-2、remote-1 全部在线。02:03:26 四端 `customrecipe list` 均返回 125 个配方，随后 `pipes info` 全部成功且没有孤立显示实体。
- Worker 的 `Failed to load resource pack workflow: default` 按用户要求忽略，表内 WARN 总数仍包含该警告。
- 仍有此前已见的 NBTAPI 2.15.7 不支持 26.2 / 更新提示、PlugManX 对 Paper 插件的限制、离线代理模式提示、spark 世界统计超时、服务端版本查询提示，以及 Worker-3 的默认语言回退；Lobby 的 DecentHolograms 内置 NBTAPI 同样报告兼容警告。
- **本轮另有一条需关注的 Lobby 警告**：02:01:07 `[Payment] QR payment account is not ready`。检索 9 月 24–26 日此前归档日志未找到同一提示。源码中它表示启动时 `paymentClient()` 失败，该调用包括读取凭据、请求爱发电账号资料和保存账号标记；当前日志没有保留具体异常，不能据此断定凭据失效或迁移回归。没有更改凭据或触发付款。
- 本轮验证覆盖启动日志、节点联通和上述查询命令；未验证客户端资源包显示、时装穿戴或实际支付。

## 补发 RealisticSeasons（2026-09-26 02:12–02:16）

清理上传包时发现遗漏：四端安装的 RealisticSeasons 仍内置并 relocate adventure-kt，根目录上传包已改为引用 Crab 共享版本。两份 plugin.yml 均标记 10.7.4，文件名仍为 RealisticSeasons-6.12.jar。

- 旧包备份：远端 `Minecraft/migration-backup-20260926-021143/RealisticSeasons-before-shared-adventure.jar`。
- 新包经 Lobby/plugins/update 和 `U.sh sc update q` 同步，02:12:10 从 ma 执行 `slimemaster execute * stop`。
- 四端安装包 SHA-256 均为 `d82409d219a30ea704a2a889befec0e9b164d44fe8c1dec823bde05c00194557`，待更新目录已消费。Worker-3 02:12:53、Worker-1 02:13:40、Worker-2 02:13:42、Lobby 02:13:48 完成启动；四端在线。
- RealisticSeasons 四端均正常启用，注册 401 个生物群系，帮助命令全部返回；启动阶段无 ERROR、无类加载冲突。资源包工作流警告继续按要求忽略。Payment 账号未就绪警告本轮出现在 Worker-1/2。
- 执行只读 `season world`：四端均成功经过 `plainMini` 调用共享 adventure-kt 并输出中文文本，Lobby 返回世界未启用季节，三个 Worker 返回季节信息。
- 查询同时暴露一个旧缺陷：Worker-1 在输出时间时抛出 `PatternSyntaxException: Illegal octal escape sequence`；Worker-2/3 的时间占位符未替换。`TimeManager.kt:175` 等代码把实际秒/分钟/小时数插入正则，而非匹配字面的 `$seconds$`、`$minutes$`、`$hours$`。更新前后 `TimeManager.class` SHA-256 完全相同（`b1f3989a0302b9ffcb4ea9fde6485fcecd8dbdff5ea452c80f7d67f1366d66da`），确认缺陷已存在于旧包。本轮没有扩大修改该业务代码；Worker-1 因本次查询新增 1 条 ERROR，其余三端 ERROR 为 0。
- 验证仅覆盖启动、依赖调用及查询；没有改变季节、时间、配置或玩家数据，未进行客户端季节视觉验收。

## 统一源码包名为 me.xiaozhangup.carb（2026-09-26 02:38–02:41）

- Crab 的 common、paper、velocity 源码及入口统一到 `me.xiaozhangup.carb`；同步更新插件描述文件、API 打包路径和文档。Maven 坐标继续使用已约定的 `me.xiaozhangup.crab:CarbKotlin:2.3.20:paper/velocity`。
- 同步修改 12 个调用方：SlimeCargoNext、SlimeMasterNext、WhaleMechanism、Cubozoa、Opossum、Raven、Tardigrade、DolphinSync、OrangDomain、Pipes、SharkChest、Spectator。共 13 个项目、704 个文本文件更新，Crab 253 个源码文件迁移目录。
- 全部项目构建成功，相关 API 已发布本地 Maven；14 份运行 jar 均上传。当前源码及产物未检出旧源码包名前缀。扫描已安装插件的字节码未发现额外调用方，运行配置和脚本未发现旧包名引用。
- 9 个共享插件通过 Lobby/plugins/update 和 U.sh 同步；Lobby 的 OrangDomain、SharkChest 直接替换。02:38:17 从 ma 广播 stop，02:38:19 停止 Master，替换 Master 的 Crab、Cubozoa、SlimeMaster 后于 02:38:25 重新运行 Start.sh。本轮未额外创建备份。
- 启动完成：Master 02:38:42、Worker-3 02:39:01、Worker-1 02:39:47、Worker-2 02:39:48、Lobby 02:39:56。Master 3 个、Lobby 11 个、每个 Worker 9 个安装 jar 均与本地产物 SHA-256 一致，待更新目录已消费。
- 截至复查，五端 ERROR 均为 0，无旧包名类缺失、方法缺失、类加载冲突或 Unit 初始化错误。四个 Paper 节点全部在线；四端 customrecipe list 返回 125 个配方，pipes info 返回成功且无孤立显示实体。仅保留已有兼容/更新提示，资源包工作流警告按用户要求忽略。
- 已部署的根目录上传 jar 随后清理；没有删除非 jar 文件，没有新增测试单元。验证覆盖构建、包名引用、启动及上述查询，不代表所有游戏内交互已逐项验收。

## 统一 CrabKotlin 拼写并全服重启（2026-09-26 03:41–03:45）

- 项目目录、GitHub 仓库、插件名、入口类及 Maven artifactId 统一为 `CrabKotlin`；源码包名统一为 `me.xiaozhangup.crab`，Velocity 插件 ID 为 `crabkotlin`。坐标为 `me.xiaozhangup.crab:CrabKotlin:2.3.20:paper/velocity`，继续关闭调用方的依赖传递。历史验证记录保留当时名称。
- CrabKotlin 及 14 个调用方完成构建：WhaleMechanism、SlimeCargoNext、SlimeMasterNext、Cubozoa、Opossum、Raven、Tardigrade、DolphinSync、OrangDomain、Pipes、SharkChest、Spectator、Adapt、RealisticSeasons。16 份运行 jar 中未检出旧包名、旧入口或旧插件名引用，相关 API 已发布本地 Maven；Octopus 继续为 26.2。
- 10 个共享调用方通过 Lobby/plugins/update 和 `U.sh sc update q` 同步；OrangDomain、SharkChest 直接替换 Lobby 安装包。按用户对改名插件的要求，四个 Paper 节点和 Master 均直接安装 `CrabKotlin-2.3.20.jar` 并移除原 `CarbKotlin-2.3.20.jar`，没有通过 update 处理改名。
- 03:41:37 从 ma 广播 `slimemaster execute * stop`，03:41:39 停止 Master；替换 Master 三个运行包后，03:41:46 执行 `sh ./Start.sh`。没有创建额外备份。

| 节点 | 启动完成 | 已核对安装 jar 数量 | ERROR 行数 |
| --- | --- | --- | --- |
| Master | 03:42:03 | 3 | 0 |
| Lobby | 03:43:15 | 13 | 0 |
| Worker-1 | 03:43:08 | 11 | 0 |
| Worker-2 | 03:43:08 | 11 | 0 |
| Worker-3 | 03:42:21 | 11 | 0 |

- 49 份安装 jar 的 SHA-256 均与本地产物一致，五端 plugins 中旧名 jar 已移除，待更新目录没有遗留文件。CrabKotlin 在五端均以新名称正常加载；没有发现依赖缺失或类加载冲突。
- 03:43:50 的节点查询确认 lobby、worker-1、worker-2、remote-1 全部在线；随后四端 `customrecipe list` 均返回 125 个配方，`pipes info` 均成功且没有孤立显示实体。
- 与本轮重启前日志对照，未发现新增 WARN；资源包工作流警告继续按用户要求忽略。Lobby 仍有 PacketEvents 报出的 `EcoMode.packetSend(EcoMode.kt:207)` 空玩家异常（以 WARN 记录），同一堆栈在重启前 `2026-09-26-13.log.gz` 已出现 244 次，本轮按用户要求保留旧问题，没有扩大修改业务逻辑。此前 IdleDetector 修复仍包含在本轮构建中。
- 验证覆盖构建、产物引用、启动、节点联通及上述查询；不代表全部游戏内交互均已逐项验收。没有新增测试单元，没有删除非 jar 文件。

## CrabPlugin 生命周期基类（2026-09-26 04:06–04:10）

- Paper、Velocity 分别提供同包名 `me.xiaozhangup.crab.CrabPlugin`。Paper 基类继承 JavaPlugin；Velocity 基类通过 Guice 成员注入提供 server/dataDirectory，并处理父类上的原生代理生命周期事件。原生入口仍使用 class。
- 基类创建并绑定单个 Crab，统一执行 CONST、配置注入、INIT、LOAD、ENABLE、ACTIVE、DISABLE，注册注解命令、事件与 Paper 占位符，启动任务并在关闭时释放资源。四个业务钩子 load/enable/active/disable 均可选，不要求调用 super；平台回调不可覆写。
- 迁移 11 个 Kotlin 插件：WhaleMechanism、SlimeCargoNext、SlimeMasterNext、Cubozoa、Tardigrade、Opossum、Raven、DolphinSync、OrangDomain、SharkChest、Spectator。业务直接写在 override 方法内，没有保留 *Plugin 转发方法；移除手动生命周期分发、配置初始化、重复资源关闭及 OrangDomain 的重复注解命令注册。原有顶层 internal Crab.kt 函数继续使用同一个实例。
- 保持 Paper ACTIVE 延后一 tick、Velocity ACTIVE 由任务触发的调度方式；保留各自原有的 DISABLE 与业务关闭顺序。各业务钩子的内容与迁移前逐项核对；对 Bukkit 同名成员显式使用 Companion.config、Companion.saveConfig、Companion.reloadConfig，以保留原配置及保存行为。
- CrabKotlin 与 11 个调用方构建成功，相关 API 已发布本地 Maven；Adapt 下游兼容性构建也通过。13 份运行 jar 上传完成：8 个 Paper 共享更新经 Lobby/plugins/update 与 U.sh 同步；Lobby 独有的 OrangDomain、SharkChest 直接替换；Master 的三个插件在停止后替换。没有提交、推送或新增测试单元。
- 04:06:42 从 ma 广播 stop，04:06:44 关闭 Master，04:06:51 重新运行 Master Start.sh。

| 节点 | 启动完成 | 核验安装 jar 数量 | ERROR 行数 |
| --- | --- | --- | --- |
| Master | 04:07:09 | 3 | 0 |
| Lobby | 04:08:21 | 10 | 0 |
| Worker-1 | 04:08:12 | 8 | 0 |
| Worker-2 | 04:08:12 | 8 | 0 |
| Worker-3 | 04:07:24 | 8 | 0 |

- 37 份安装 jar 均与本地产物 SHA-256 一致，update 目录已消费。没有注入失败、生命周期重复执行、配置未初始化或类加载冲突；对照本轮重启前日志，未发现新增 WARN/异常消息。资源包工作流等已确认的旧问题保留。
- 04:09:04 节点查询确认四个 Paper 节点在线；四端均返回 125 个自定义配方，Pipes 查询成功且没有孤立显示实体。Lobby 的 poly 命令正确返回帮助树，确认基类自动注册的注解命令可用。
- 本轮验证覆盖构建、启动、初始化/激活及只读查询；新版本的关闭路径经代码核对，未为验证关闭路径额外安排第二次全服重启。未逐项验收全部游戏内交互。

## 编解码与 FlexibleItem 处理器下沉（2026-09-26 04:56–05:03）

- 本轮沿用会话内已验证的 CrabKotlin、SlimeCargoNext、WhaleMechanism、Opossum、Tardigrade、Spectator、SharkChest 构建，部署前补跑 SharkChest 构建（通过）；8 份上传产物均按 SHA-256 核对。Crab 运行包区分 Paper/Velocity，安装名仍为 `CrabKotlin-2.3.20.jar`。
- 6 个共享 Paper 插件通过 Lobby/plugins/update 与 `sh ./U.sh sc update q` 分发，24 份暂存哈希一致；SharkChest 仅在 Lobby 原子替换。Master 仅替换 Velocity 版 Crab。五端原有 update 目录均为空，没有覆盖其他更新批次。
- 04:56:06 由 ma 广播 stop，04:56:19 关闭 Master；确认旧 PID 退出后替换 Master Crab，04:57:05 执行 Start.sh。启动完成时间：Worker-3 04:56:51、Master 04:57:22、Worker-2 04:57:37、Worker-1 04:57:38、Lobby 04:57:44。
- 26 份实际安装 jar 哈希均与清单一致，五端 update 均已消费。未发现缺类、缺方法或 classloader 冲突。三台 s1 Paper 的预期插件已启用；04:58:08 均返回 125 个自定义配方，04:59:27 Pipes 查询均返回没有孤立显示实体。
- Worker-3 在 Master 未就绪时首次连接被拒绝，未自动重连，继而出现 `SlimeService.getChannel` 空指针，Whale 的 BiliBili/Teleport 初始化失败及 Spectator 任务异常。04:58:07 Master 节点查询只有 lobby、worker-1、worker-2 在线。用户随后通过已有托管面板重启 Worker-3，05:00:58 完成第二次启动；上述连接及初始化异常没有再出现，BiliBili 视频列表也已成功加载。没有新增控制通道或改动连接代码。
- 05:02:57 Master 查询确认 lobby、worker-1、worker-2、remote-1 全部在线；定向发送给 remote-1 的 `customrecipe list` 实际返回 125 个配方，`pipes info` 返回没有孤立显示实体。最终复查 26 份安装 jar 哈希仍全部一致，五端 update 为空。
- 其余节点对比本轮重启前日志没有新增警告类型。Lobby 的 `EcoMode.packetSend(EcoMode.kt:207)` 空玩家异常、Worker 的 CraftEngine `Unknown resource pack: default` 均在重启前存在；没有按 ERROR 为零将这些 WARN/异常判为通过。
- 未提交推送，未新增测试单元，未清理上传文件；未删除非 jar 文件。客户端交互及新版本关闭路径未逐项验收。

## 补齐代理端编解码迁移（2026-09-26 05:10 起）

- SlimeMasterNext、Cubozoa 删除重复的基础 ByteReader/ByteWriter/byteArray，直接使用 Crab common；Master、Opossum、OrangDomain 删除重复 UUIDSerializable。当前工作区只保留 Crab 的这套共享实现。
- Master 的岛屿、用户、展示信息读写方法改为共享 reader/writer 上的扩展，保留在 `utils/ByteArrayUtils.kt`；Cargo 的 `DomainSerialization.kt` 同步改名为 `ByteArrayUtils.kt`。静态对比确认 Master 六个业务函数体不变、基础编解码与 Crab 一致，Cubozoa 与 Crab 一致（Crab 额外提供 remaining）。
- SlimeMasterNext、SlimeCargoNext、Cubozoa、Opossum、OrangDomain 均构建通过，Master/Cargo API 已发布本地 Maven。五份运行产物的描述符、旧编解码字节码引用检查通过，并上传校验。
- Cargo/Opossum 经 Lobby update 与 U.sh 分发，OrangDomain 仅替换 Lobby，Master 停止后替换 SlimeMasterNext/Cubozoa。05:10:42 广播 stop；本轮临时脚本将日志字节偏移用于字符串切片，未识别停服回执，因此先保留 Master。重新核实回执后，05:10:56 shutdown，05:11:03 Start.sh，05:11:22 Master 启动完成。11 份安装哈希一致，update 均已消费。
- Worker-3 于 05:11:17 连接尚未就绪的 Master，被拒绝后出现与上轮相同的 channel/模块初始化异常；05:11:23 启动完成但未联网。已请用户通过已有面板再次重启，待完成重新启动后的联通与查询验收；没有修改连接代码或增加控制通道。
- Worker-2 05:12:12、Worker-1 05:12:13、Lobby 05:12:18 完成启动；05:13:02 Master 确认这三个节点在线。三端 `customrecipe list` 均返回 125 个配方、`pipes info` 均返回没有孤立显示实体，Lobby `poly` 返回 Usage。对照重启前日志，Master 和这三个 Paper 节点没有新增 WARN/ERROR 类型，既有 EcoMode 与 CraftEngine 问题仍保留。
- 未新增测试单元，未提交推送，未清理上传文件，未删除非 jar 文件；客户端交互未逐项验收。

## 提交与上传包清理（2026-09-26）

- 按用户后续授权提交本轮 9 个仓库的源码与文档。Whale 的 CraftEngineHandler、CustomFishingHandler 源码统一到 `flexible.handler`，补跑构建与本地 Maven 发布通过；运行 JAR 的 SHA-256 与已安装产物完全相同，无需再次部署。
- 按用户授权清理 `~/Minecraft` 根目录的 11 个上传 JAR；删除前逐项复核清单哈希与全部目标节点安装哈希一致。根目录仅保留 `SlimeUpdater-1.0-SNAPSHOT-all.jar`，未删除插件目录、待更新目录或任何非 JAR 文件。Worker-3 本轮重新启动后的联通验收仍待完成。

## Velocity 编译依赖升级及直接替换（2026-10-02）

- 依据当前 `PROJECTS.md`，将 CrabKotlin Velocity、SlimeMasterNext、Cubozoa、MultiAuth 的 Velocity API 依赖统一为 `4.2.1-SNAPSHOT`。MultiAuth 的内部实现编译依赖同步到 PaperMC 构建 #36；服务端 jar 和 Gradle 解析的 API jar 均与官方校验和一致。
- 四个项目构建成功，CrabKotlin 和 SlimeMasterNext API 已发布本地 Maven。未遇到 API 编译错误，没有为此次升级修改业务源码或新增测试单元；原有未提交改动保留。
- 按用户授权直接原子替换 `s1.dimc.cloud:~/Minecraft/SlimeCargo-Master/plugins` 中四个同名运行 jar，旧文件已被替换。没有检查运行状态或重启，也没有提交、推送。其余 11 个 Velocity 插件 jar 的哈希保持不变，目录内没有重复插件 ID。

| 运行 jar | 替换后 SHA-256 |
| --- | --- |
| CrabKotlin-2.3.20-velocity.jar | `867284411b9578b6a045c9570a3b3b7bbccf8653ce56598e8d845f2a532fb777` |
| SlimeMasterNext-1.0.1.jar | `d2c275c10f323e1eb9a37d03fe2ec7aee58c02616269f4ff15172995cf129a5f` |
| Cubozoa-1.0.0.jar | `f28f6a4dc90009fba2e4a8a5853f32a112f33e34da8b22ce6e9e6a5f658f6aa5` |
| MultiAuth-2.2.3.jar | `d8fab17a5197c2ed0f5388db40f9bf848d4ada51e660748f99ca0a1e207a42e7` |

- 四份安装文件的 SHA-256 与本地运行产物一致；Cubozoa 和 MultiAuth 重建后的哈希与原安装包相同。验证覆盖构建、描述符、完整性及文件替换，不代表新版本已加载或完成运行/客户端验收。Paper 插件仅按后续请求检查更新，不操作其 jar。
