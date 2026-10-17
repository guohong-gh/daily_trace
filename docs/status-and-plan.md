# 项目现状与实施计划

> 更新时间：2026-10-17。本文档由技术负责人盘点后沉淀，随进展更新。

## 一、已确认事实（来自代码与构建产物）

- 数据层（代码注释称 "Phase 1"）已完成：8 个实体 / 8 个 DAO / Repository / 手动 DI / 种子数据（4 个默认分组 + 默认设置）。
- Room schema v1 已导出（`app/schemas/`），约定后续升级写显式 Migration，不做破坏性迁移。
- 单元测试 61 个用例全部通过（Robolectric 内存库与纯逻辑单测，2026-10-17 验证，含 M2 流程测试）。
- debug APK 可构建；UI 层 M1 已从模板替换为真实页面（见下）。
- Manifest 无任何权限；allowBackup=true，备份规则仍为模板。
- git 已初始化（分支 main）并推送至 Gitee（https://gitee.com/itaita/daily_trace_app，2026-10-17 初始提交 8e6959b）；README 与本文档已补齐；无 CI。

## 二、核心架构与数据流

```
UI (Compose Screens) ← ViewModel ← DailyTraceRepository ← Room DAOs ← daily_trace.db
                                  ↑
        domain/time/DayClock（逻辑日）、domain/checkin/CheckInPlanner（写入计划，纯函数）
```

- 单一数据入口为 `DailyTraceRepository`，向上暴露 Flow。
- 打卡写入先由 `CheckInPlanner` 生成纯数据计划（inserts/updates/deletes），再由 `applyPlan` 落库 —— 规则可单测。
- 领域纯函数层（均可穷举单测）：`domain/time/DayClock`（逻辑日/周起始/月历网格）、`domain/checkin/CheckInPlanner`（写入计划）、`domain/checkin/BackfillPolicy`（补卡合法性）、`domain/filter/FilterMatcher`（筛选匹配）。
- UI 共享组件：`ui/common/`（Formatters、RecordDialog、AppUi：区块卡片/徽章/空态/胶囊/开关行），今日页/日期详情页复用同一记录对话框与格式化。

## 三、阶段计划（按里程碑）

| 阶段 | 内容 | 状态 |
|------|------|------|
| M0 基线化 | README、现状文档、基线构建验证（git 暂缓） | ✅ 完成 |
| M1 打卡 MVP | 逻辑日工具、导航、今日页、打卡交互、选项管理、设置最小集 | ✅ 完成（61 用例全绿；模拟器验收通过） |
| M2 回顾 | 日历、日期详情、三层备注、补卡、搜索、筛选视图 | ✅ 完成（61 用例全绿；模拟器验收通过） |
| M3 提醒 | 通知权限与渠道、调度器、通知动作、DND | ✅ 完成（79 用例全绿；模拟器验收通过） |
| M4 统计 | 完成率、连续打卡（allowSkip）、周/月趋势 | ✅ 完成（99 用例全绿；模拟器验收通过） |
| M5 备份 | 导出/导入、自动备份、备份规则定制 | ✅ 完成（103 用例全绿；模拟器验收通过） |
| M6 发布 | release 构建 + R8 验证、测试补强、lint | ✅ 完成（release 装机运行；lint 错误清零） |
| UI 优化 | 品牌主题、通用组件库、九屏空态/反馈/校验统一（仅 UI 层） | ✅ 完成（103 用例全绿；lint 无错误；模拟器走查截图 ui2-*） |
| M7-1 频率规则 | 频率规则编辑 UI、CUSTOM 统计口径、提醒星期拆分、DB v1→v2 迁移 | ✅ 完成（105 用例全绿；模拟器走查截图 m7-01..05；其余 M7 项与矩阵见 docs/spec-gap.md） |

M1 验收标准：新建 4 类型选项 → 各自打卡 → 杀进程重启数据完整；MultiRecordRule 三种规则行为有单测。
自动化部分已达成（40 用例全绿 + APK 构建成功）；模拟器（Medium_Phone_API_37.0）手动验收通过（见第八节）。

M2 验收标准：日历标记与真实记录一致；日期详情可编辑三层备注中的「日期整体备注」；补卡受 maxBackfillDays 限制且落库带 isBackfill；搜索命中记录/日期备注；筛选视图可保存并应用到今日页。
自动化部分已达成（新增 21 用例：FilterMatcher 8、BackfillPolicy 5、M2Flow 6、月历网格 2；合计 61 全绿）；模拟器手动验收通过（见第八节）。
M2 交付细节：
- 日历（`ui/calendar/`）：月视图 + 上/下月/今天；主色圆点=有打卡、第三色圆点=有备注；周起始与「今天」高亮遵循设置；点日期进详情。
- 日期详情（`ui/datedetail/`）：当天记录列表（用快照字段展示，补卡记录带「补卡」角标）+ 日期整体备注编辑器（空白=清除）+ 补卡入口（选项选择 → 记录对话框）。
- 搜索（`ui/search/`）：300ms 防抖；命中打卡备注/选项快照名与日期备注；点击结果跳转对应日期详情。
- 筛选视图（`ui/filter/`）：新建/编辑/删除/应用；范围（全部/仅选中）+ 选项多选 + 分组多选 + 任一/全部 + 日期状态（全部/已打卡/未打卡）。
- 今日页：顶栏改为「日历 / 搜索 / 更多（筛选视图、管理选项、设置）」；应用筛选显示指示条与「清除」；筛选后为空有专门空态。

M3 验收标准：编辑页可启用提醒并设定时刻/重复/免打扰；到点弹出通知（含「打卡」「稍后」动作）；通知内打卡直接落库且幂等；设备重启后自动重排。
自动化部分已达成（新增 18 用例：ReminderTime 12、ReminderScheduler 6；合计 79 全绿）；模拟器手动验收通过（见第八节）。
M3 交付细节：
- 调度（`reminder/ReminderScheduler`）：精确闹钟权限可用 → `setExactAndAllowWhileIdle` 准点；否则降级 `setAndAllowWhileIdle` 并在编辑页引导授权（D13）。每次触发由 Receiver 续排下一次，系列不会因单次异常中断。
- 通知（`reminder/ReminderNotifier`）：两渠道「打卡提醒」（HIGH）与「打卡提醒（安静）」（LOW）；免打扰窗口内触发走安静渠道（D14）。
- 动作（`reminder/ReminderActionReceiver`）：布尔型「打卡」直写今日记录（幂等：已有 DONE 则跳过）；「稍后 N 分钟」排一次性闹钟；点击通知本体打开应用（D15）。
- 重排：`ReminderBootReceiver` 监听开机/时间变更/时区变更/应用更新；应用启动时全量重排一次（覆盖强制停止等场景）。
- 编辑页「提醒」区块：启用开关、时刻选择（Material3 TimePicker 24h）、每天/工作日/每周（周几多选）、免打扰时段、通知与精确闹钟权限提示与引导。
- WEEKLY 的重复日存入 `Reminder.daysOfWeek`（M7-1 起与频率规则解耦；此前借用 FrequencyRule.daysOfWeek，见 D23）。

M4 验收标准：完成率/连续/趋势与真实数据一致（含补卡、跳过日）；范围切换（本周/本月/近30天）与周/月趋势切换正确；打卡后统计实时刷新。
自动化部分已达成（新增 20 用例：StatsCalculator 18、M4Flow 2；合计 99 全绿）；模拟器手动验收通过（见第八节）。
M4 交付细节：
- 纯逻辑（`domain/stats/StatsCalculator`）：期望日模型（D16）+ 完成率、当前/最佳连续、月度配额进度、周/月趋势聚合；所有函数按 itemId 隔离记录。
- 统计页（`ui/stats/`）：今日进度、范围切换、每项完成率与连续/最佳、周（8 周）或月（6 个月）合计完成率柱状图；TIMES_PER_MONTH 显示「本月 x/N 次」不计算连续。
- 入口：今日页「更多 → 统计」；回前台刷新（跨逻辑日）。
- 验收中修复真实缺陷：完成率/趋势最初未按选项过滤记录，导致把其他选项当天的打卡算作本项完成（Bool1 补卡被计入 Count1）；已改为显式 itemId 过滤并新增回归用例。

M5 备份（交付细节）：
- 备份格式：JSON v1（`backup/BackupCodec`，org.json 零新增依赖）：format/version/exportedAt + 8 张表（选项含回收站）；枚举存 name、列表存数组、可空字段存 null；解析先校验，非法数据（非本应用/版本不符/字段缺失）报明确错误且不动库。
- 导入导出：`backup/BackupManager` 单事务全量替换，任一失败整体回滚；导入成功后自动重排提醒。
- 界面：设置页「备份」区块 —— 导出（SAF 自选位置）/ 导入（二次确认「覆盖导入」）/ 自动备份开关 + 间隔（1/3/7/15/30 天）/ 立即备份 + 份数与最近时间。
- 自动备份：`backup/AutoBackup` 存 `files/backups/`（保留最近 5 份），应用启动与退后台时按间隔检查（数据只在应用内变化，用前补齐上一轮变更；手动备份忽略开关与间隔）。
- 测试：M5BackupTest 4 用例（全表往返一致含回收站/可空/枚举/列表、导入替换、非法数据拒绝、间隔与保留策略）。

M6 发布（交付细节）：
- release：versionName 1.0.0、R8 开启、APK 约 2.3MB、装机无崩溃；签名暂用调试密钥（正式分发前必须替换）。
- R8 实测发现并修复：`optimization.packageScope = setOf("androidx.**","kotlin.**","kotlinx.**")` 触发 `IllegalAccessError: kotlin.sequences.d extended by om1`（跨包重打包破坏 kotlin stdlib 包内可见性）；移除该配置后今日/统计/备份在 release 下全部正常。
- lint：9 错误清零（其中 8 个为 NotificationChannel API 26 未守卫，属 minSdk 24 真实兼容缺陷，已加版本守卫；1 个通知权限已显式检查后就地抑制）；另为开机接收器加 action 白名单校验。剩余 18 条警告登记技术债。
- 应用名由 "Daily_Trace" 改为「每日打卡」（包名不变）。

## 四、决策记录

| 编号 | 日期 | 决策 | 说明 |
|------|------|------|------|
| D1 | 2026-10-17 | 暂不初始化 git | 用户决定 |
| D2 | 2026-10-17 | M1 文案暂用硬编码中文 | i18n 为 P3 技术债 |
| D3 | 2026-10-17 | 维持当前工具链（AGP 9.4 / JDK 25 / compileSdk 37） | 本机已验证可构建；CI 需求出现时再评估降版 |
| D4 | 2026-10-17 | 启用 core library desugaring | minSdk 24 需 java.time（API 26+ 才有） |
| D5 | 2026-10-17 | lifecycle 2.6.1 → 2.8.7，新增 viewmodel-compose / runtime-compose | 使用 viewModel()+viewModelFactory DSL 与 LifecycleResumeEffect |
| D6 | 2026-10-17 | 打卡写入统一走 CheckInPlanner（纯函数）→ applyPlan 落库 | 规则可穷举单测；ViewModel 仅做编排 |
| D7 | 2026-10-17 | 排序用「上移/下移」按钮重写 sort | 拖拽排序留待后续 |
| D8 | 2026-10-17 | 筛选匹配语义：mode=ALL 仅 dayFilter 生效；SELECTED 下选项/分组单边为空视为不限制，两边都有时 logic=ANY 取并集、ALL 取交集；dayFilter 按「当天是否有 DONE 记录」 | 落于 `domain/filter/FilterMatcher`，8 用例覆盖 |
| D9 | 2026-10-17 | 补卡策略：date==today 允许（非补卡）；date<today 需 maxBackfillDays>0 且 today-date<=max；date>today 拒绝；isBackfill=date!=today | 落于 `domain/checkin/BackfillPolicy`；写入前按最新设置二次校验 |
| D10 | 2026-10-17 | 日历按设置的周起始日与「今天」高亮；月历网格为 DayClock 纯函数（首尾补 null，4-6 行） | 单测覆盖周一起始与周日起始两种偏移 |
| D11 | 2026-10-17 | 历史记录展示一律用快照字段（itemNameSnapshot 等），不查当前选项 | 改名/软删除后历史仍正确 |
| D12 | 2026-10-17 | 筛选视图「应用」通过 NavBackStackEntry.savedStateHandle 回写今日页；切换/清除即时生效，进程重启后重置为未筛选 | 避免引入全局单例状态 |
| D13 | 2026-10-17 | 提醒精确度：精确闹钟权限（SCHEDULE_EXACT_ALARM）可用时 `setExactAndAllowWhileIdle` 准点；否则降级 `setAndAllowWhileIdle` 并在编辑页提示+一键跳系统设置 | 覆盖"任意答案"；不用 USE_EXACT_ALARM（Play 政策风险） |
| D14 | 2026-10-17 | 免打扰语义：窗口内触发用「安静」低优先级渠道（无声音），不申请 DND 豁免（NotificationPolicy access） | 纯函数 `ReminderTime.isInDndWindow` 支持跨午夜窗口，12 用例覆盖 |
| D15 | 2026-10-17 | 通知动作：布尔型「打卡」直写（幂等跳过已完成）；全类型「稍后 N 分钟」= 一次性闹钟（不推进系列）；点击本体打开应用。WEEKLY 重复日存 FrequencyRule.daysOfWeek | ReminderActionReceiver + ReminderScheduler.scheduleSnooze |
| D16 | 2026-10-17 | 统计口径：期望日 = DAILY/CUSTOM/无规则取每天、DAYS_OF_WEEK 取所选周几、TIMES_PER_MONTH 无日级期望（改用月度配额进度）；跳过日（SKIPPED 记录或 allowSkip+skipDates）不计期望也不中断连续；PARTIAL 计完成受 partialCountsAsDone；今天未打卡宽限不中断；所有函数按 itemId 隔离 | `domain/stats/StatsCalculator`，18 用例覆盖；范围「近30天」= today-29..today |
| D17 | 2026-10-17 | 备份：JSON v1 全表快照（org.json 手写编解码，不引入 kotlinx-serialization）；导入 = 单事务全量覆盖 + 先校验后写入；自动备份用应用生命周期触发（启动/退后台检查到期），不引入 WorkManager 后台任务；本机保留 5 份，SAF 负责用户位置选择 | `backup/` 三件套；未加密（encryption 字段保留未实现，待需求） |
| D18 | 2026-10-17 | release 本机验收暂用调试签名（与 debug 同钥匙串，便于覆盖安装）；正式分发前替换独立签名 | `app/build.gradle.kts` release.signingConfig = debug |
| D22 | 2026-10-17 | 应用名改回「日迹」（M11 执行） | 用户拍板；改 strings.xml 显示名，包名保持 com.gh.daily_trace |
| D23 | 2026-10-17 | 提醒每周天数拆分到 `Reminder.daysOfWeek`；DB v1→v2 迁移时把旧 WEEKLY 提醒的星期从频率规则复制过来 | 原共享 `FrequencyRule.daysOfWeek`，编辑频率或提醒会互相覆盖并污染统计口径（M7-1） |
| D24 | 2026-10-17 | CUSTOM 频率 = 每隔 N 天（1..90），锚点 = 保存当天；修改 N 时锚点重置为今天，其他编辑保留；缺参数退化为每天 | 语义可确定、可统计（M7-1，`StatsCalculator.isExpectedDay`） |

## 五、假设（因需求文档缺失而采用）

- 产品定位：本地优先、单机中文用户；暂不做云同步与账号。
- `MultiRecordRule` 语义定义为：MERGE=更新最新一条（空值保留原值）；ACCUMULATE=最新一条累加；KEEP_MULTIPLE=追加新记录。布尔型快速打卡=有记录则全部取消。
- 时区不提供 UI 修改（默认 Asia/Shanghai），M1 先读设置值。
- 补卡上限生效于日期详情页；统计语义（部分完成口径 partialCountsAsDone）留待 M4 落地。
- 提醒：时刻按 `AppSettings.timezone` 解释（真实用户设备时区通常与之一致）；无精确闹钟权限时接受系统延迟（编辑页有提示）；免打扰时段=静默渠道提示，而非完全不发通知。

## 六、待确认问题（未答复前按上述假设推进）

1. 原始需求文档（代码注释引用的 "Phase 1 / Phase 3"）在哪里？
2. 首个真正的 MVP 交付时间与范围？是否上架？
3. 提醒的精确程度要求（决定精确闹钟权限 vs WorkManager）？——已按 D13 默认方案落地：权限可用则准点，否则降级并在 UI 引导；如要求"任何情况下都准点"请告知（可再评估 WorkManager 兜底或 USE_EXACT_ALARM）。
4. git 已启用并推送 Gitee（2026-10-17）；是否需要 CI？
5. 工具链是否长期维持激进版本？

## 七、技术债登记

| 级别 | 项 | 计划 |
|------|----|------|
| P1 | 逻辑日跨 4 点切换：各页用 LifecycleResumeEffect 在回前台时刷新（今日/日历/日期详情已接入）；App 长驻前台跨 4 点边界时，界面在下次交互前可能仍显示旧日期 | M4 或按需引入可见时钟（定时刷新） |
| P2 | 依赖版本新旧混杂（core-ktx 1.10.1 等） | M6 发布前统一评估升级 |
| P2 | 删除分组后选项 groupId 悬空，UI 兜底为"未分组" | 可接受，后续迁移补强 |
| P2 | 字符串硬编码中文，无 i18n | P3 |
| P2 | 无 UI 自动化测试（依赖模拟器手工验收脚本） | 后续评估 Compose UI Test（M6 已补 lint/release 验证） |
| P3 | 「稍后」一次性闹钟在设备重启后丢失（系列提醒会由开机接收器重排） | 接受；如需保留再持久化 snooze 状态 |
| P3 | 提醒时刻按 AppSettings.timezone 解释；设备系统时区与设置不一致时视觉上有时区偏移 | 真实用户默认一致；后续设置页可加时区选择 |
| P2 | skipDates 已参与统计（跳过不计期望、不中断连续），但尚无 UI 登记入口；allowSkip 开关已在编辑页（M7-1） | 日期详情页后续提供「登记休息日」（M7-3） |
| P3 | TIMES_PER_MONTH 仅月度配额进度；「每周 N 次」目标未支持（CUSTOM 已在 M7-1 落地「每隔 N 天」语义） | 需求明确后再细化（M10+） |
| P3 | lint 余留警告 17 条：UnusedResources 7、GradleDependency 6、InlinedApi 2、RedundantLabel 1、NewerVersionAvailable 1 | 不影响发布；发版前可按需清理 |
| P3 | release 暂用调试签名；`AppSettings.encryption` 字段未实现（备份为明文 JSON） | 正式分发前替换签名；加密需求明确后再做 |
| P3 | `androidx.work:work-runtime` 已引入但未使用（自动备份改用应用生命周期触发） | 后续做后台定时或移除依赖 |
| P3 | 应用名当前为「每日打卡」（M6）；按 D22 将在 M11 改回「日迹」，包名保持 com.gh.daily_trace | M11 执行 |

## 八、验收记录（模拟器 Medium_Phone_API_37.0，2026-10-17）

设备：AVD `Medium_Phone_API_37.0`（1080×2400），APK 由 `assembleDebug` 产物直接安装。

M1 手动验收（通过）：
1. 新建 4 类型选项：Bool1（布尔）/ Count1（计数）/ Dur1（时长）/ Val1（数值）。
2. 打卡：勾选 Bool1；Count1 点 +1；Dur1 记一笔 30；Val1 记一笔 8.5 → 进度 4/4。
3. `am force-stop` 杀进程后重启 → 4/4 与各值完整（Room 持久化确认）。

M2 手动验收（通过）：
1. 日历：2026 年 10 月渲染正确（周一起始、10 月 1 日落在周四列、今天高亮、图例）。
2. 日期详情：当天 4 条记录以快照名/值展示；时间戳正确。
3. 三层备注第三层：当天备注保存后返回再进入仍保留（读库确认）。
4. 补卡：10-16 补 Bool1 成功，列表显示「补卡」角标；补卡上限与禁用态由设置驱动。
5. 搜索：键入 "day" 命中「日期备注（1）」并显示日期与内容。
6. 筛选视图：新建 OnlyBool（仅选中 + Bool1）→ 应用后今日页仅显示 Bool1（1/1），显示「筛选：OnlyBool」指示；清除后恢复 4/4；进程重启后筛选重置（符合 D12）。
7. 归档回归：Bool1 归档后今日页 3/3 且管理页出现在「已归档（1）」；取消归档后恢复 4/4。

M3 手动验收（通过）：
1. 编辑 Bool1 → 启用提醒：出现「尚未允许通知权限」提示与「允许通知」按钮，系统弹窗授权后提示消失；精确闹钟未允许时出现「去允许精准提醒」，跳系统设置（Alarms & reminders）允许后提示消失（回前台自动刷新）。
2. 设定 12:40（按 AppSettings.timezone=Asia/Shanghai 解释）→ `dumpsys alarm` 显示 RTC_WAKEUP 精确闹钟（`exactAllowReason=permission`、`window=0`）＝设备时区 04:40 GMT，准点到分钟。
3. 到点通知弹出：「打卡提醒：Bool1 / 点击「打卡」即可完成」+ 动作「打卡」「稍后 10 分钟」；系列已自动续排次日同一时刻。
4. 通知内「打卡」：先在今日页取消勾选（3/4）→ 点通知动作 → 今日页恢复 4/4 且通知撤下；对已完成项再次动作幂等跳过（无重复记录）。
5. 「稍后 10 分钟」：通知撤下并排一次性闹钟（`REMINDER_FIRE_SNOOZE`，触发时刻=点击时刻+10 分钟），系列闹钟不受影响。
6. 重启重排：`adb reboot` 后开机广播触发全量重排，`dumpsys alarm` 恢复次日 12:45（上海）精确闹钟；重启后今日数据仍 4/4。
7. 说明：模拟器系统时区是 GMT、App 设置时区为 Asia/Shanghai，故提醒按上海时间触发（设备显示 04:40 GMT = 上海 12:40）；真实设备两者通常一致（时区偏移已登记为 P3 技术债）。

M4 手动验收（通过）：
1. 更多 → 统计：今天已完成 4/4；本周范围 Bool1 33%（完成 2/6 天，连续 2 天、最佳 2 天），其余三项各 17%（1/6，连续 1）。
2. 范围切换：本月 Bool1 12%（2/17）、其余 6%（1/17）；近30天 Bool1 7%（2/30）、其余 3%（1/30）。
3. 趋势切换：周视图 8 根柱（标签 8/24…10/12，按周一起始对齐）；月视图 6 根柱（5月…10月）。
4. 实时刷新：取消勾选 Bool1 → 统计即时变为 1/6、今天进度相应下降（今天未打卡宽限，连续保持 1）；恢复勾选回到 2/6。
5. 真库核对（pull DB + sqlite3）：仅 Bool1 存在 10-16 补卡记录。修复前 Count1 等误显示「2/6」（把 Bool1 当天的记录算入自身），已修复为按 itemId 过滤；回归用例固化。

截图证据：`docs/screenshots/`（01 今日 4/4、02 日历、03 补卡、04 当天备注、05 筛选应用、06 提醒通知、07 统计-周、08 统计-月趋势、09 设置-备份、10 导入确认）。

M5 手动验收（通过）：
1. 设置页「备份」区块渲染完整（导出/导入/自动备份开关与间隔/立即备份/份数说明）。
2. 打开自动备份（间隔 7 天）→「立即备份到本机」→ `files/backups/daily_trace_20261017_055041.json`（3435B）生成，Snackbar「已备份到本机」。
3. 导出：系统文件对话框预填 `daily_trace_backup_20261017.json` → SAVE → `/sdcard/Download` 生成同大小文件；pull 后校验 format/version 与各表计数（items 4、records 5、dayNotes 1、groups 4、reminders 1、filterViews 1）。
4. 破坏数据（取消 Bool1 打卡 → 3/4）→ 导入备份 → 二次确认 → 提示「导入完成：选项 4、记录 5、备注 1、分组 4、提醒 1」→ 今日恢复 4/4；`dumpsys alarm` 确认提醒已重排（次日 04:45 GMT 精确闹钟，exactAllowReason=permission）。
5. 强停重启：启动查备份运行且未到期不重复写（仍 1 份）。

M6 手动验收（通过）：
1. `assembleRelease` 成功（app-release.apk 2.29MB）；installRelease 启动无崩溃、数据完整（4/4）。
2. R8 初版崩溃（IllegalAccessError，packageScope 所致）→ 移除后复测：今日/统计/设置/立即备份（JSON 编码）全部正常。
3. `lintDebug` 9 错误清零后 BUILD SUCCESSFUL（含 API 26 渠道守卫这一真实缺陷修复）。
4. release 下设置页显示「本机备份 2 份 · 最近 10-17 05:58」，写入路径正常（release 不可 run-as，经界面与 debug 版对照确认）。

## 九、UI/UX 优化批次（2026-10-17，仅 UI 层）

范围与原则：布局/色彩/字体层级/间距对齐、卡片与列表、导航清晰度、空态/加载/反馈提示、表单校验、减少步骤、可读性与无障碍（≥48dp 点击区、对比度、contentDescription）。所有业务规则、数据字段语义、导航路由与 ViewModel 行为保持不变。

交付内容：
1. 品牌主题：`ui/theme/Color.kt` 自建 Material 3 明暗双色板（主色「生长绿」#2E6B4A，辅助琥珀），`Type.kt` 完整字阶（标题半粗、辅助文字 11sp 起），`Shape.kt` 形状层级（卡片 14dp 圆角、按钮胶囊化）。
2. 通用组件库 `ui/common/AppUi.kt`：SectionCard（带主色标题的区块卡片）、ItemBadge/DoneBadge（选项色浅底 emoji 徽章 / 完成对勾徽章）、SectionTitle、EmptyState、LoadingState、StatusPill、SwitchRow。
3. 九个页面统一改造：
   - 今日页：进度卡（百分比 + 进度条 + 全完成鼓励语）、图标按钮导航（日历/搜索/更多菜单带 emoji 菜单项）、分组计数「3/4」、卡片展开箭头旋转动画、布尔完成态换对勾徽章、记录行删除改图标按钮 + Snackbar、双空态（无选项/筛选无命中）。
   - 日历页：月份卡片（图标箭头切换）、今日主色圆点高亮、图例点。
   - 日期详情：备注卡片 +「备注已保存」反馈、补卡选择器带徽章列表、记录卡（补卡胶囊 + 删除图标 + Snackbar）、空态。
   - 搜索：圆角搜索框（放大镜/一键清空）、加载态（转圈）、初始/无结果空态、命中卡片带日期胶囊。
   - 筛选视图：列表卡片（应用为主按钮、删除错误色）、空态、编辑器底部等宽「取消/保存视图」（名称空时禁用）。
   - 选项管理：行卡片（类型摘要 + MoreVert）、操作弹窗改为 emoji 图标行（危险操作分隔线 + 错误色）、空态。
   - 选项编辑：分区块卡片（基本信息/记录方式/分组与状态/提醒）、色板选中环 + 对勾、目标值非法行内红字校验、名称空必填提示、权限提示卡、底部整宽保存按钮 + 危险操作行。
   - 统计：今日总览卡（环形进度）、选项统计卡（进度条 + 连续天数胶囊）、趋势卡（圆角柱状图）、加载态与空态。
   - 设置：分区卡片（外观/使用习惯/备份/关于）、SwitchRow 带说明文案、版本号与数据说明。
4. 依赖：新增 `androidx.compose.material:material-icons-core:1.7.8`（material3 1.4 不再传递依赖图标库）；所有图标按钮带中文 contentDescription。

新决策：
- D19：默认关闭 Material You 动态取色（`AppSettings.dynamicColor` 默认 false），保证品牌色板一致性；用户可在设置开启（Android 12+）。
- D20：图标方案采用官方 material-icons-core 显式依赖（不手写矢量路径，避免运行时解析风险）。
- D21：操作反馈统一为 Snackbar（删除记录/保存备注/补卡/备份等）；空态与加载态统一走 AppUi 组件；文案维持硬编码中文（与 D2 一致）。

验证：
- `testDebugUnitTest` 103/103 全绿；`lintDebug` BUILD SUCCESSFUL（17 条警告均为历史存量，无错误、无新增类别）。
- 模拟器走查 10+ 屏：今日/日历/日期详情/搜索（空态+结果）/筛选（列表+编辑器）/管理（列表+操作弹窗）/选项编辑（上/提醒区）/统计/设置（浅色+深色）/记录对话框/补卡选择器/展开态/删除反馈 Snackbar，logcat 无 FATAL。
- 截图：`docs/screenshots/ui2-01..ui2-20`（浅色品牌色板为主；ui2-14/15 为深色模式；ui2-18 删除反馈；ui2-19 补卡选择器）；既存 01–10 为优化前素材，可前后对比。

已知取舍：
- 模拟器演示数据中的选项无 emoji 图标（历史数据），徽章显示为「•」；在编辑页填入图标后即正常。
- 导出/导入仍使用系统 SAF 原生交互与确认弹窗，未做自定义样式（原生一致性优先）。
- 日期详情「清除」备注按钮沿用原逻辑（清空并立即保存）。

## 十、M7-1 频率规则编辑批次（2026-10-17）

范围：任务书 M7 第 1 项——频率规则编辑 UI（每天 / 每周几 / 每月几次 / 自定义）；其余 M7 项（状态录入、休息日登记、统计总量、日历圆点）留待后续批次，进度矩阵见 `docs/spec-gap.md`。

交付内容：
1. 编辑页新增「频率规则」卡片（`ui/itemedit/`）：类型 4 个 chips + 条件输入——每周几（7 天多选，默认预选今天）、每月几次（1-31，默认 3）、自定义间隔（1-90，默认 2）；「允许跳过/请假」开关；保存校验（每周几至少一天、数字越界行内报错）。
2. CUSTOM 语义落地（D24）：每隔 N 天、锚点=保存当天；改 N 时锚点重置为今天、其他编辑保留锚点；缺参数（旧数据/异常导入）退化为每天。`StatsCalculator.isExpectedDay` 新增 CUSTOM 分支（锚点前不计期望）。
3. 提醒星期拆分（D23）：`Reminder.daysOfWeek` 新字段；编辑页提醒区块读写提醒自身字段；`ReminderScheduler` 不再读频率规则。理由：原共享字段会在「频率=每周几」与「提醒=每周」并存时互相覆盖并污染统计口径。
4. DB v1→v2（`DailyTraceDatabase.MIGRATION_1_2`）：`frequency_rules` + `intervalDays`/`anchorEpochDay`；`reminders` + `daysOfWeek TEXT NOT NULL DEFAULT ''`，并把旧 WEEKLY 提醒的星期从对应频率规则复制过来；`app/schemas/.../2.json` 已导出。
5. 备份 JSON 扩展：规则 intervalDays/anchorEpochDay、提醒 daysOfWeek（均为可选字段，旧备份兼容；format VERSION 保持 1）。

验证：
- `testDebugUnitTest` 105/105 全绿（新增：CUSTOM 按间隔与锚点计期望日、CUSTOM 缺参数退化每天；更新：提醒调度独立性回归、数据库/备份新字段 roundtrip）。
- `lintDebug` 无错误（17 条警告与基线一致，无新增）；`assembleRelease`（R8）成功，release 包 1.9MB 覆盖安装冒烟通过（截图 m7-06）。
- `assembleDebug` 成功；模拟器覆盖安装旧库（v1 真实数据）→ 迁移成功：4 选项 / 4 条记录 / 旧提醒完好，logcat 无 FATAL / SQLiteException。
- 走查截图 `docs/screenshots/m7-01..05`：四种类型切换与条件 UI、保存后重进回显。拉库校验：`frequency_rules`(Dur1) = DAYS_OF_WEEK `1,3,5`；`reminders`(Dur1) = WEEKLY `1,6`；提醒与频率星期互不影响。
