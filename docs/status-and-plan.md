# 项目现状与实施计划

> 更新时间：2026-10-20。本文档由技术负责人盘点后沉淀，随进展更新。

## 一、已确认事实（来自代码与构建产物）

- 数据层（代码注释称 "Phase 1"）已完成：8 个实体 / 8 个 DAO / Repository / 手动 DI / 种子数据（4 个默认分组 + 默认设置）。
- Room schema v5 已导出（`app/schemas/`），约定后续升级写显式 Migration，不做破坏性迁移。
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
| M7 高频闭环（5 项） | 频率规则、状态录入、休息日登记、统计总量、日历标记 | ✅ 完成（127 用例全绿；lint 0 错误；模拟器走查通过；批次详情见第十至十三节） |
| M8 视图与效率（3 项） | 日历滑动切月 + 年月选择器、周/年/时间线视图、长按多选批量操作 | ✅ 完成（174 用例全绿；lint 0 错误；模拟器走查通过；批次详情见第十四~十八节） |
| M9 系统集成（4 项） | 搜索按日期范围、提醒「暂停」、每日汇总提醒、桌面小组件 | ✅ 完成（207 用例全绿；lint 0 错误；模拟器走查通过；批次详情见第十九~二十二节） |
| M10 数据与质量（7 项） | CSV 导出、按分组统计、色盲友好、日期格式/语言、大字号回归、压力测试、崩溃日志与数据修复 | ✅ 完成（6 / 7 完成；色盲友好按产品取舍不做；240 用例全绿；lint 0 错误；模拟器走查通过；批次详情见第二十三~二十八节） |
| M11 品牌与平板（2 项） | 应用名改回「日迹」、平板 / 响应式布局 | ✅ 完成（242 用例全绿；lint 0 错误；手机 / 平板走查通过；批次详情见第二十九节） |
| M12 高频与频率补全（4 项） | B16「每周 N 次」+ DB v6、B19 近期常用、B20 今日页 FAB、C28 全选（C27 配色顺延） | ✅ 完成（250 用例全绿；lint 0 错误；每批走查通过；批次详情见第三十节） |
| C27 筛选行视觉强化（顺延项） | 筛选编辑器行 = 颜色块 / 图标徽章 + 名称 + 复选框 | ✅ 完成（见第三十一节） |
| B18 周报 / 月报报告式汇总（顺延项收尾） | 统计页「周期报告」卡（周报 / 月报） | ✅ 完成（见第三十二节） |

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
- 应用名由 "Daily_Trace" 改为「每日打卡」（M11 已改回「日迹」，包名不变）。

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
| D22 | 2026-10-17 | 应用名改回「日迹」（M11 已执行） | 用户拍板；已改 strings.xml 显示名与小组件标题，包名保持 com.gh.daily_trace |
| D23 | 2026-10-17 | 提醒每周天数拆分到 `Reminder.daysOfWeek`；DB v1→v2 迁移时把旧 WEEKLY 提醒的星期从频率规则复制过来 | 原共享 `FrequencyRule.daysOfWeek`，编辑频率或提醒会互相覆盖并污染统计口径（M7-1） |
| D24 | 2026-10-17 | CUSTOM 频率 = 每隔 N 天（1..90），锚点 = 保存当天；修改 N 时锚点重置为今天，其他编辑保留；缺参数退化为每天 | 语义可确定、可统计（M7-1，`StatsCalculator.isExpectedDay`） |
| D25 | 2026-10-19 | 「跳过」统一写 `rule.skipDates`（休息日/请假登记）；UI 不再新建 `status=SKIPPED` 记录（旧数据仍参与统计）；跳过登记不受补卡窗口限制，但受 allowSkip 门控 | 日级语义与统计口径一致（D16）；可撤销、不产生记录噪音；未来日期可提前登记请假（M7-3） |
| D26 | 2026-10-19 | 「部分完成」= 始终追加 `status=PARTIAL` 记录，不参与 MERGE/ACCUMULATE 合并目标 | 部分完成是过程量，合并语义模糊；合并目标只认最新一条 DONE，避免覆盖与状态污染（M7-2，`CheckInPlanner.latestDoneOf`） |
| D27 | 2026-10-19 | 「计划中」仅限未来日期（`date > today`），同日同选项重复计划更新最近一条（upsert，`planAhead`）；`isBackfill=false`；布尔快速打卡与合并规则均忽略非 DONE 记录 | 与 D9 补卡窗口无冲突；防计划重复堆积；计划不干扰完成判定（M7-2，`BackfillPolicy.canPlan`） |
| D28 | 2026-10-19 | 统计总量 = 全时段累计（不随范围切换）；次数只算 DONE（PARTIAL 单独计数）；量值合计含 DONE+PARTIAL（与今日页 totalValue 一致）；PLANNED/SKIPPED 不计；计数型「总次数」取数值合计；按 itemId 隔离 | 交付验收 11；落于 `StatsCalculator.totals`（M7-4） |
| D29 | 2026-10-19 | 日历标记 = 逐选项快照色圆点：DONE/PARTIAL 产点、同项同日合一（色取当天最新一条记录）、按选项管理顺序、最多 4 个 +「+N」；PLANNED/SKIPPED 不产点、跳过日不单独标记；备注保留第三色点 | 与 D11（快照展示）、D25（跳过语义）一致；落于 `domain/calendar/CalendarMarks`（M7-5） |
| D30 | 2026-10-19 | 长按日期 = 当日摘要对话框（快照名/值/状态 + 休息日名单 + 日期备注 + 「查看详情」）；无内容的空日不弹（长按等同点击） | 不新增页面层级；对话框与筛选联动后数据一致（M7-5） |
| D31 | 2026-10-19 | 日历联动今日页「已应用筛选」仅取选项/分组范围（`FilterMatcher.itemMatches`），忽略 dayFilter；沿用 savedStateHandle 机制（D12）；日历页显示「筛选：xxx 标记已按筛选联动」指示条、清除仍在今日页 | 避免「未打卡」辅助筛选清空日历标记；一处应用全局可见（M7-5） |
| D32 | 2026-10-19 | 日历翻月 = 1970-01 ~ 2100-12 固定虚拟页（1572 页），边界停住不回绕；记录/备注窗口 = 当前月 ±1（相邻页预取）；年月选择器 = 年步进 + 3×4 月网格、点月即跳转；远距（>2 页）直接定位、近距平滑翻页 | 翻月范围可控可测；相邻预取保证滑动无空白；标题即入口、不增页面层级（M8-1） |
| D33 | 2026-10-19 | 日历视图 tab = 月 / 周（后续批次追加 年 / 时间线），点击切换、tab 之间不横滑；横滑留给各视图自身翻页（月 = 翻月、周 = 换周、年 = 换年）；筛选指示条与「今天」全视图共用（「今天」按视图回当前月 / 本周） | 分层交付、每批可独立验收；手势分工避免 tab 横滑与视图翻页冲突（M8-2a） |
| D34 | 2026-10-19 | 周视图 = 行（未删除选项、管理顺序）× 列（周内 7 天）状态网格；格状态优先级 完成 > 部分 > 计划 > 跳过（当天有记录时跳过不覆盖），跳过来自规则 `allowSkip + skipDates`（D25）；实心 / 半透明 / 空心 / 灰杠区分；回收站选项不作为行、归档选项保留 | 与 D11（快照展示）、D25（跳过语义）、D29（圆点口径）一致；表格便于一眼看清一周完成度（M8-2a） |
| D35 | 2026-10-19 | 年热力图 = 竖版 7 列（周起始按设置）× 53/54 行动态行数（年首周 ~ 年尾周，年外格留白）、卡片内竖向滚动；强度 = 当日去重 DONE 选项数分档 0/1/2/3/4+（PARTIAL/PLANNED/SKIPPED 不计，与 D28 一致）；筛选沿用 D31（抽出 `ItemScope` 与 `CalendarMarks` 共用）；‹年› + 横滑换年（1970 ~ 2100，131 页，停住不回绕）、「今天」回当年；数据窗口 = 当年 ±1 年、摘要只算当年；今天格高亮环、打开当年自动滚到今天行；汇总行「完成项次 + 打卡天数」 | 一眼看清全年密度与打卡天数；窗口/翻年范围可控可测；月份栏补足纵向定位（M8-2b） |
| D36 | 2026-10-19 | 时间线 = 倒序日流：只列窗口内有内容的天（命中筛选的记录 / 休息日登记 / 日期备注，口径完全复用 D30/D31 摘要）；卡片 = 日期（+今天徽标、条数）+ 条目 + 休息日 + 备注，点击进日期详情、长按弹 D30 摘要（与月/周/年一致）；默认窗口含今天共 30 天、「加载更早」每次再回溯 30 天（下限 1970-01-01），更早是否有内容由记录/备注存在性查询判定（无则显「已到最早记录」）；「今天」重置回最近 30 天并回到顶部；窗口上界 = 今天（未来计划不在时间线展示） | 日流聚焦「有内容的日子」、空日不干扰；分页窗口可控、避免无限空载；未来计划已由月/周/年承载（M8-2c） |
| D37 | 2026-10-19 | 批量记录默认值直记：BOOLEAN=完成（已完成跳过）、COUNT=+1、DURATION/VALUE=选项目标值 → 截至日期最近一次记录值，均无则该行置灰不可批量（走现有单笔写入，multiRecordRule 一致）；批量删除记录 / 彻底删除选项均加确认框；批量改备注 =「应用」（非空覆盖，空输入禁用）+「清除备注」（清空）；管理页一次只在一个分区内多选（使用中/已停用 → 归档/移入回收站；已归档 → 取消归档/移入回收站；回收站 → 恢复/彻底删除），「停用/启用」不批量化；交互 = 长按进多选 + 顶栏「已选 N / 全选 / 取消」+ 底部操作条 + 返回键先退出 | 直达常用值、少填表；复用单笔写入保证规则一致；分区限定避免跨状态误操作（M8-3） |
| D38 | 2026-10-20 | 搜索日期范围 = 预设「不限 / 近 7 天 / 近 30 天」+ 自定义起止；范围按逻辑日闭区间 [from, to]，null = 不限；近 N 天 = today-(N-1) ~ today；自定义用 Material3 DatePickerDialog（UTC 毫秒 → epochDay），起 > 止自动交换；仍要求关键词非空（不提供空关键词全量浏览） | 与既有逻辑日/闭区间口径一致；纯函数 SearchDateRange 可测；不增 schema、不改现有索引（M9-1） |
| D39 | 2026-10-20 | 提醒暂停 = 全局暂停截止时刻 pausedUntil（epoch millis，null = 未暂停）；预设 1 / 8 / 24 小时 + 自定义日期时间（DatePicker + TimePicker，按 settings.timezone 解释与展示）；暂停时撤下全部系列/稍后闹钟与已发通知，并排一个到点恢复闹钟（ACTION_RESUME → 重排全部）；恢复 = 清值 + 重排；暂停中 scheduleNext / scheduleSnooze 与触发接收器均静默兜底；DB v2→v3 仅加可空列 | 一个字段覆盖所有提醒入口；到点自动恢复不依赖打开 App；展示时区与提醒/逻辑日一致（M9-2） |
| D40 | 2026-10-20 | 每日汇总 = 可选开关（默认关闭）+ 时刻预设 20:00 / 21:00 / 22:00 + 自定义 TimePicker；WorkManager 唯一周期任务（24h，首次延迟到下一汇总时刻，时刻变更/应用启动时重排，关闭即取消）；内容 = 逻辑日当天在册选项（启用、未归档、未删除）为分母、DONE 去重为分子，三态文案（无选项 / 全部完成 / 待完成）；暂停中 / 无通知权限时静默跳过；DB v3→v4 增加 dailySummaryEnabled / dailySummaryTime，备份同步这两个字段与 pausedUntil | 汇总口径与今日进度一致；默认关闭避免突然打扰；WorkManager 周期任务容忍数分钟偏差，适合汇总而非准点提醒（M9-3） |
| D41 | 2026-10-20 | 桌面小组件 = 4×4 单一「日迹 · 今日」组件：头部今日进度（DONE 去重 / 在册选项）+ 百分比进度条；快捷项按管理顺序取前 4 个在册选项，布尔型未完成显示「打卡」并点击直写 `RecordSource.WIDGET`（已有 DONE 则幂等跳过），已完成或非布尔型显示「已完成 / 记录」并点击打开 App；所有实例展示同一份数据；刷新 = 系统 30 分钟周期 + Room Flow 观察者（进程存活，300ms debounce）+ 打卡后立即刷新，无实例不查库；首版不做逐实例配置与多尺寸 | 打卡口径与今日进度一致（D40）；经典 AppWidgetProvider + RemoteViews，不引入 Glance；固定顺序避免行位漂移；系统 30 分钟重绘作为进程不在时的兜底（M9-4） |
| D42 | 2026-10-20 | CSV 导出 = 设置页「导出 CSV（打卡记录）」单文件全量打卡记录，一行一条；列 = 日期 / 记录时间 / 选项快照 / 状态 / 数值 / 单位 / 备注 / 来源 / 补卡 / 时区；按 `date, createdAt, id` 升序；日期为逻辑日，记录时刻按 `AppSettings.timezone`；UTF-8 BOM + CRLF，文本按 RFC 4180 转义，对以 `= + - @` 开头的用户文本加单引号防公式注入；全部状态、补卡与历史快照均导出，不做日期范围筛选；CSV 仅用于分析，选项/分组/日期备注与完整恢复仍由 JSON 备份承载 | 无 schema 变更；Excel 直接打开；CSV 不承担恢复职责（M10-1） |
| D43 | 2026-10-20 | 分组统计 = 统计页按分组汇总在用选项；分组完成率 = 组内各选项 Σ DONE 期望日 / Σ 期望日（加权，不取各选项百分比平均），PARTIAL 沿用 partialCountsAsDone；TIMES_PER_MONTH 无日级期望，不计入分母并以「含 N 个配额项」标注；分组按 sort, id，未分组 / 孤儿 groupId 归并置末；无期望日显示 —；不改选项统计与趋势，不做分组筛选 / 下钻 | 与 D16 / D28 口径一致；无 schema；新增 GroupRate 纯函数（M10-2） |
| D44 | 2026-10-20 | 日期显示 = 独立于界面语言的日期标签设置：`dateLanguage = SYSTEM / ZH / EN`（默认 ZH），`dateFormat = AUTO / YMD / MDY / DMY`（默认 AUTO）；统一 `DateDisplay` 纯函数覆盖今日、日历月 / 周 / 年标题、星期表头、年视图月份栏、日期详情、搜索命中、时间线与长按摘要；AUTO 中文「2026年10月20日 周二 / 10月20日 周二」、英文「Tue, Oct 20, 2026 / Tue, Oct 20」；数字格式只作用于日级日期，月 / 周 / 年标题仍用语言自然写法；不翻译应用界面，CSV / 备份文件名 / 数据库日期不受影响；SYSTEM 仅区分中文 / 英文（其他系统语言回退英文）；DB v4→v5 增加两列，备份新增可选字段、旧备份默认 ZH + AUTO | 避免完整双语翻译成本；日期格式可独立于语言（如中文界面 + 20/10/2026）；统一纯函数便于测试与跨页面一致（M10-4） |
| D45 | 2026-10-21 | 大字号回归 = 系统 `font_scale` 1.3 / 1.5 / 2.0 下主要页面与关键弹窗不裁切、不重叠、可点击；密集网格不强行保持完整字阶：月 / 周日期数字在 >1.6 倍收紧为 labelSmall，月视图日期格高度按字阶分档（默认 48dp，大字号 60 / 66 / 76dp），彩点上限在大字号下降为 3（>1.6 倍为 2）并把隐藏项并入「+N」；日历 tab、年视图月份栏、周标题 / 名称栏、图例、统计趋势标签随字阶收紧、加宽、换行或堆叠；普通字号布局不变 | 优先保证可读与信息完整，不用横向裁切换缩放；只改 UI 自适应，不改数据、筛选与统计口径（M10-5） |
| D46 | 2026-10-21 | 大数据量统计 = `DailyRecordIndex` 一次遍历建立「日期」与「日期+选项」索引及每项最早日期；全时段总量用 `totalsByItem` 单遍历；统计聚合在 `Dispatchers.Default` 执行；统计页与长按摘要改用 `LazyColumn` 只组合可见行（摘要 >20 条时启用，普通数据保持原弹窗布局） | 500 选项 / 5 万记录下避免 O(选项数 × 记录数) 扫描与一次性组合 500 张卡片；不改变统计口径、schema、CSV / 备份格式（M10-6） |
| D47 | 2026-10-22 | 崩溃日志 = `Application.onCreate` 最早安装全局未捕获异常处理器，崩溃前写 `files/crash_logs/crash_*.log`（版本、设备、Android / SDK、线程、完整堆栈），最多 10 份，随后交回系统默认处理器；设置页显示份数 / 最近时间，可 SAF 导出 UTF-8 文本或清除；不采集用户数据、不联网、无新增权限。数据修复 = 设置页主动「检查数据 / 修复」：只清理孤立频率规则 / 提醒、无效分组引用（置空）、空日期备注、筛选视图失效 id，并修正越界设置；用 `PRAGMA quick_check` 报告完整性；选项彻底删除后的孤儿记录保留只读、不删除；另提供「从最近本机备份恢复」覆盖恢复 | 只修可由数据自身推导的不一致，不自动删除打卡历史；大面积异常走备份恢复；无 schema、无新增依赖 / 权限（M10-7） |
| D48 | 2026-10-22 | 响应式布局 = 按 Material 3 官方 600 / 840dp 断点划分 compact / medium / expanded，用 `BoxWithConstraints` 计算，不引入 window-size-class 依赖；medium / expanded 且可用高度 ≥480dp 显示左侧导航栏（日迹 + 今日 / 日历 / 统计 / 管理 / 设置），内容最大宽度 1000dp 居中；内容宽度 ≥640dp 时今日 / 统计的选项卡片双列，进度、筛选条、分组标题、趋势等整行跨列；紧凑宽度与矮横屏保持原顶栏导航 | 平板获得稳定导航与双列信息密度，手机布局不变；高度门槛避免横屏手机导航栏被裁切；不新增依赖、不改数据 / CSV / 备份口径（M11） |
| D49 | 2026-10-22 | B16「每周 N 次」按配额语义：不出日级期望、不进完成率分母、统计页展示「本周 x/N 次」（有 DONE / PARTIAL 记录的天数 = 1 次，按 weekStart 对齐）；B19 用自动方案（今日页按最近 30 天使用次数 top 3，≥1 即显示实际数量，总项数 ≥8 才显示）；B20 FAB 只放今日页；C28 先做、C27 顺延 | 与 TIMES_PER_MONTH 配额口径一致便于理解与实现；不改动完成率语义与 CSV / 备份格式；DB v6 为向后兼容增列迁移（M12） |

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
| P3 | TIMES_PER_MONTH 仅月度配额进度；「每周 N 次」目标未支持（CUSTOM 已在 M7-1 落地「每隔 N 天」语义） | 需求明确后再细化（M10+） |
| P3 | lint 余留警告 17 条：UnusedResources 7、GradleDependency 6、InlinedApi 2、RedundantLabel 1、NewerVersionAvailable 1 | 不影响发布；发版前可按需清理 |
| P3 | release 暂用调试签名；`AppSettings.encryption` 字段未实现（备份为明文 JSON） | 正式分发前替换签名；加密需求明确后再做 |
| P3 | `androidx.work:work-runtime` 已引入但未使用（自动备份改用应用生命周期触发） | 后续做后台定时或移除依赖 |

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

## 十一、M7-2/M7-3 状态录入与休息日登记批次（2026-10-19）

范围：任务书 M7 第 2、3 项——状态录入（部分完成 / 跳过 / 计划中）与休息日 / 跳过 / 请假登记入口；其余 M7 项（统计总量、日历圆点）留待后续批次，进度矩阵见 `docs/spec-gap.md`。

交付内容：
1. 「部分完成」（D26）：`RecordDialog` 新增「完成 / 部分完成」状态选项；`CheckInPlanner.addPartial` 始终追加 `status=PARTIAL` 记录；合并/累加目标改取最新一条 DONE（`latestDoneOf`），布尔「全部取消」仅取消 DONE 记录；今日页 `totalValue` 只统计 DONE/PARTIAL。
2. 「跳过」（D25）：统一写 `rule.skipDates`——今日页展开区「跳过今天 / 取消跳过」，日期详情选择器逐项「跳过 / 取消跳过」（任意日期，含未来请假）；受 `allowSkip` 门控；今日页副标题「今日已跳过 · 不计完成率」，日期详情「已跳过：…（不计入完成率，不中断连续）」摘要行。
3. 「计划中」（D27）：`BackfillPolicy.canPlan`（仅 `date > today`）；`CheckInPlanner.planAhead`（同日 upsert、`isBackfill=false`）；日期详情未来日期按钮变「添加计划」、对话框切计划模式（计划数值可留空）；记录卡新增状态胶囊（部分完成 / 计划 / 跳过）；今日页当天有未完成计划时提示「计划：…」。
4. `RecordStatusPill` 提取为公共组件（`ui/common/AppUi.kt`）；`PickerRow`（选择器行状态：已跳过 / 已完成 / 部分完成 / 计划中）。

验证：
- `testDebugUnitTest` 113/113 全绿（新增 8：canPlan 窗口；部分完成追加/合并与累加目标跳过 PARTIAL/布尔忽略计划；计划 upsert 与保留原值；partial+plan+skip 端到端落库）。
- `lintDebug` 0 errors / 17 warnings（与基线一致，无新增）；`assembleRelease`（R8）成功并覆盖安装冒烟。
- 模拟器走查（release 包）截图 `docs/screenshots/m7-07..15`：部分完成对话框与记录行胶囊、跳过今天与撤销、未来日期添加计划、选择器逐项跳过与摘要行、今日页「计划：」提示。
- 拉库校验（debug 包 + run-as）：`check_records` (Val1) = `PARTIAL 42.0 @20745`、`PLANNED 60.0 @20746 isBackfill=0`；`frequency_rules`(Dur1) `skipDates='20745'`、`allowSkip=1`。

## 十二、M7-4 统计总量批次（2026-10-19）

范围：任务书 M7 第 4 项——统计总量（每项总次数 / 总时长 / 总数值）；剩余 M7 项（日历逐选项彩色圆点）留待后续批次，进度矩阵见 `docs/spec-gap.md`。

交付内容：
1. 口径（D28）：总量为全时段累计，不随范围切换；次数只算 DONE、PARTIAL 单独计数；量值合计（value 求和）含 DONE + PARTIAL，与今日页 `totalValue` 一致；PLANNED / SKIPPED 一律不计；按 itemId 隔离。
2. 领域纯函数 `StatsCalculator.totals(itemId, records)` → `ItemTotals(doneCount, partialCount, valueSum, isEmpty)`。
3. 统计页每项卡片新增「累计」行：布尔型「累计完成 N 次」（value 为空的布尔记录也计数）；计数型「累计 N{单位}」（单位空退化为「次」）；时长型单位为空或「分钟」时友好化为「X 小时 Y 分」（`Formatters.formatMinutes`），其余单位原样；数值型「累计 N{单位}」；存在部分完成时附注「含部分完成 N 次」；无 DONE/PARTIAL 记录时不占行。

验证：
- `testDebugUnitTest` 116/116 全绿（新增 3：DONE/PARTIAL/SKIPPED/PLANNED 混合聚合与 itemId 隔离、布尔空值记录只计次数、无记录时为空）。
- `lintDebug` 0 errors / 17 warnings（与基线一致，无新增）；`assembleRelease`（R8）成功，release 包 1.9MB 覆盖安装走查。
- 模拟器走查（release 包）截图 `docs/screenshots/m7-16/17`：四项「累计」行——布尔=完成次数、计数=数值合计（1+3=4）、时长小时化（30+60=「1 小时 30 分」）、数值含部分完成注记（8.5+42=50.5）；切「近30天」完成率变化而累计不变（D28 全时段口径）。
- 拉库校验（debug 包 + run-as，含 WAL）：`check_records` 聚合 = Bool1 DONE 1；Count1 DONE 4/4.0；Dur1 DONE 2/90.0；Val1 DONE 1/8.5 + PARTIAL 1/42.0（合计 50.5）；PLANNED 60.0 不计 —— 与界面一致。

## 十三、M7-5 日历标记增强批次（2026-10-19）

范围：任务书 M7 第 5 项——日历逐选项彩色圆点 + 「+N」、长按浮层、筛选实时联动标记。M7 全项收官，进度矩阵见 `docs/spec-gap.md`。

交付内容：
1. 领域纯函数 `domain/calendar/CalendarMarks`（D29/D31）：逐日圆点（DONE/PARTIAL 去重快照色、最多 4 个 + overflow 计数、备注标记）、逐日摘要（快照条目含类型/值/单位/状态、休息日名单、日期备注）；筛选只作用于选项/分组范围（忽略 dayFilter，选项已清除时按 `FilterMatcher` 空选择语义兜底）；备注为日级不受筛选影响。
2. 日历页（`ui/calendar/`）：逐选项彩色圆点 +「+N」溢出；图例改为「已完成的选项（颜色对应选项）｜有备注」+「长按日期查看当日摘要」提示；长按日期弹当日摘要对话框（D30：快照徽章 + 部分完成/计划/跳过胶囊、布尔完成对勾、休息日行、「查看详情」跳日期详情）；有筛选时顶部显示「筛选：xxx 标记已按筛选联动」指示条。
3. 联动机制（D31）：`AppNavHost` 的 CALENDAR 路由从 TODAY entry 的 savedStateHandle 读 `appliedFilterId`（沿用 D12 机制），经 `CalendarViewModel.setFilter` 接入；`getBackStackEntry` 以 NavBackStackEntry 作 remember key（lint 新规则）；日历页不提供清除（清除仍在今日页）。
4. `CalendarViewModel`：items/frequencyRules/settings/filter 合并为 Core 流，`CalendarMarks.build` 输出 marks + summaries；打卡/备注/筛选任意变化实时刷新。

验证：
- `testDebugUnitTest` 127/127 全绿（新增 11：圆点同项合一与最新快照色/管理顺序、上限 4 + 溢出、PLANNED/SKIPPED 不产点但进摘要、PARTIAL 产点、仅备注日、跳过日名单与 allowSkip 门控、空日无标记无摘要、筛选按选项/分组/ANY/ALL 且忽略 dayFilter、休息日名单受筛选、已清除选项兜底、摘要快照字段与值/状态）。
- `lintDebug` 0 errors / 17 warnings（与基线一致；期间修复 1 个新增错误：getBackStackEntry 的 remember key 需为 NavBackStackEntry）；`assembleRelease`（R8）成功，release 包 1.84MB。
- 模拟器走查（debug 包 + run-as）截图 `docs/screenshots/m7-18..23`：逐选项圆点（19 日 3 点）；新增 Run1（蓝）/Read1（橙）打卡后多色 + 「+1」溢出；长按摘要对话框（快照徽章、部分完成胶囊、休息日 Dur1、布尔对勾、「查看详情」跳转日期详情）；筛选视图 RunRead 联动（19 日只剩蓝/橙、17 日仅剩备注点与指示条）；筛选态摘要只列命中项、无休息日行；清除筛选后恢复；空日长按不弹浮层。
- 拉库校验（含 WAL）：Run1 `color=-15374912`、Read1 `color=-1086464`；20745 记录 = Count1 DONE×3 + Dur1 DONE 60 + Val1 PARTIAL 42 + Run1/Read1 DONE（5 项产点 → 4 圆点 +「+1」，与界面一致）；`filter_views` RunRead = SELECTED `5,6` ANY。

## 十四、M8-1 日历滑动切月批次（2026-10-19）

范围：任务书 M8 第 8 项——左右滑动切换月份 + 年月选择器（M8 首项，批次顺序 8 → 6 → 7）。

交付内容：
1. `DayClock` 新增静态纯函数 `weekStartOf(epochDay, weekStart)` 与 `monthGridRows(yearMonth, weekStart)`（实例方法 `weekStartOf` / `monthGridRowsOf` 委托之），供 pager 逐页渲染，行为与实例版一致（单测对比锁定）。
2. `CalendarViewModel`（D32）：state 增 `month`（与 pager 双向同步）与 `weekStart`（逐页网格渲染用）；页映射 `monthToPage/pageToMonth` + 范围常量 `CALENDAR_MIN_MONTH/MAX_MONTH/COUNT`（1572 页）；`setMonth` 越界钳制，`prevMonth/nextMonth/backToToday` 统一走之；记录/备注观察窗口由当月扩为「当前月 ±1」预取。
3. `CalendarScreen`：周表头固定 + `HorizontalPager` 包裹月网格（初始页 = 当前月）；`settledPage → setMonth` 与 `state.month → 定位` 双向同步（相等守卫防循环）；点标题（带 ▾）弹年月选择器：年步进 ‹ 年 ›（1970..2100 边界置灰）+ 3×4 月网格（当前月高亮），点月即跳转；远距（>2 页）直接定位、近距平滑翻页；DayCell 点按/长按行为不变。

验证：
- `testDebugUnitTest` 134/134 全绿（新增 7：网格纯函数版与实例版一致、4 行/6 行月、闰年二月含 29 日、每行 7 格非本月补 null 且日期连续、页↔月互逆、范围边界与总页数、年份跨越换算）。
- `lintDebug` 0 errors / 17 warnings（修复 1 条新增警告 AutoboxingStateCreation → `mutableIntStateOf`）；`assembleRelease`（R8）成功，release 包 1.85MB。
- 模拟器走查截图 `docs/screenshots/m8-01..11`：滑动切月 10→11→9→10 月（标题与网格随页更新，相邻月空、回载标记完整含 19 日「+1」）；点标题弹选择器（2026 年、10 月高亮）；点「7月」跳转（2026年7月，1 日周三）；「今天」从 7 月远跳回 10 月；选择器连点「上一年」56 次到 1970（‹ 置灰）、点「1月」入 1970-01（1 日周四）；1970-01 继续右滑停住不回绕、左滑到 1970-02 正常；长按 19 日摘要对话框、点按 19 日进日期详情（回归通过）。
- 本批无 schema 变更与数据写入，未做拉库校验。

## 十五、M8-2a 视图 tab + 周视图批次（2026-10-19）

范围：任务书 M8 第 6 项第 1 轮——周/年/时间线视图共享筛选状态，本批落地「视图 tab 骨架 + 周视图」（M8-2 三批：2a 周视图 → 2b 年热力图 → 2c 时间线）。

交付内容：
1. `domain/calendar/WeekMatrix`（D34，纯函数）：行 = 未删除选项（含归档，管理顺序）× 列 = 传入 7 天；格状态优先级 完成 > 部分 > 计划 > 跳过（当天有记录时跳过不覆盖），跳过来自规则 `allowSkip + skipDates`；筛选沿用 `FilterMatcher.itemMatches`（忽略 dayFilter，与 D31 一致）；回收站选项不作为行。
2. `CalendarViewModel`（D33）：新增 `CalendarViewMode`（月/周）与 `weekAnchor`（周起始日）；数据窗口按模式切换——月 = 当前月 ±1（原口径）、周 = 当前周 ±1 周（21 天，相邻页预取）；新增周页映射 `calendarWeekBounds/weekToPage/pageToWeekStart/weekPageCount`（1970-01-01 所在周 ~ 2100-12-31 所在周，6836 页，边界停住不回绕）；`setWeekAnchor` 越界钳制、`prevWeek/nextWeek`、`backToToday` 按视图（周视图回本周）、`refreshToday` 同步周起始设置变化（周锚点归一化）；state 增 `weekTitle`（如「2026年10月19日 – 25日」）与 `weekRowsByAnchor`（当前周 ±1 矩阵）。
3. `CalendarScreen`：顶部分段 tab（月/周，点击切换、tab 不横滑，D33）；月视图抽出为 `MonthView`（pager/选择器/图例/长按行为不变）；新增 `WeekView`：周箭头 + 标题、7 列星期+日期表头（今天圆底）、`HorizontalPager` 换周（settledPage ↔ weekAnchor 双向同步 + 相等守卫）、行首色块+名称、格 = 实心（完成）/半透明（部分）/空心描边（计划）/灰杠（跳过）；点格进日期详情、长按当日弹摘要对话框（D30 复用）；图例与提示随视图切换；空态「没有可展示的选项」。

验证：
- `testDebugUnitTest` 144/144 全绿（新增 10：WeekMatrix 7 —— 状态优先级、跳过受 allowSkip 门控、记录不被跳过覆盖、筛选命中/忽略 dayFilter、行顺序与日期连续、回收站不出行、周外记录不串档；周页映射 3 —— 周一/周日起始边界与总页数、页↔周互逆）。
- `lintDebug` 0 errors / 17 warnings（无新增规则）；`assembleRelease`（R8）成功，release 包 1.87MB。
- 模拟器走查截图 `docs/screenshots/m8-12..16`：周视图基线（10-19 多记录：Count1 完成、Val1 部分、Run1/Read1 完成、20 日计划空心格）；横滑到上周 10-12~18（16 补卡、17 三条）；长按 19 日摘要（含「休息日：Dur1」）；顶栏「今天」回本周；筛选 RunRead 联动后仅 Run1/Read1 两行且指示条显示；点格进 10-21 详情 → 添加计划内登记 Dur1 跳过 → 返回周视图显示灰杠（当场记录优先仍显示完成于 19 日）。
- 本批无 schema 变更与写入（走查新增 10-21 Dur1 跳过登记为演示数据，属正常业务写入，未拉库）。

## 十六、M8-2b 年热力图批次（2026-10-19）

范围：任务书 M8 第 6 项第 2 轮——年视图（热力图），共享筛选状态（M8-2 三批：2a 周视图 → 2b 年热力图 → 2c 时间线）。

交付内容：
1. `domain/calendar/ItemScope`（D31 抽取）：`passes(itemId, itemById, filter)` 由 `CalendarMarks` 与 `YearHeatmap` 共用；只取选项/分组范围、忽略 dayFilter；已彻底清除的选项（不在 items 表）按 ALL/未选范围视命中，与 `FilterMatcher.itemMatches` 语义一致。
2. `domain/calendar/YearHeatmap`（D35，纯函数）：`YearGrid/HeatRow/HeatCell/YearSummary`；行 = 年首周 ~ 年尾周（周一/周一起始动态 53/54 行，年外格 `inYear=false` 留白）、列 = 周内 7 天；强度 = 当日去重 DONE 选项数分档 0/1/2/3/4+（`MAX_LEVEL = 4`）；`monthStart` = 行内首个当年日月份（左侧月份栏数据）；汇总 = 当年筛选内「完成项次 + 打卡天数」（只计当年、同日同项去重）。
3. `CalendarViewModel`（D33 扩展）：`CalendarViewMode` 增 `YEAR`；新增 `yearAnchor` 与年页映射 `CALENDAR_MIN_YEAR/MAX_YEAR/COUNT`、`yearToPage/pageToYear`（1970 ~ 2100，131 页，停住不回绕）；数据窗口按模式切换为年 = 当年 ±1 年；`setYear` 越界钳制、`prevYear/nextYear`、`backToToday`（年视图回当年）；state 增 `yearTitle`（如「2026 年」）与 `yearGridsByYear`（当年 ±1 预取）；摘要只覆盖当年（相邻页仅渲染色格）。
4. `CalendarScreen`：tab 增「年」；新增 `YearView/YearPage/YearRowView`：‹年› 箭头 + 标题、横向 pager 换年（settledPage ↔ yearAnchor 双向同步 + 相等守卫）、卡片内竖向滚动、顶部星期表头随周起始轮转、左侧月份栏；格色阶 = 0 级灰 / 1..4 级 primary 透明度 0.28/0.55/0.78/1.0，今天格高亮环（tertiary 描边）；打开当年自动滚到今天行（视口就绪后居中定位）；点格进日期详情、长按弹 D30 摘要；底部图例 +「环 = 今天」+ 汇总行。

验证：
- `testDebugUnitTest` 155/155 全绿（新增 11：YearHeatmap 9 —— 分档封顶（5 项 → L4）、同日同项多条只计一项、部分/计划不计入、筛选只计命中且忽略日筛选、年首尾周与年外留白（2026 周一起始 53 行）、54 行年份（2012）、月份栏 12 个月各一次、汇总只计当年且去重、空数据全零；年页映射 2 —— 范围与边界页、页↔年互逆）。
- `lintDebug` 0 errors / 17 warnings（无新增规则）；`assembleRelease`（R8）成功，release 包 1.88MB。
- 模拟器走查截图 `docs/screenshots/m8-17..22`：年视图基线（2026 自动滚到今天行；19 日 L4 + 今天环、17 日 L3、16 日 L1；汇总 8 项次/3 天）；滚到年初（2025-12-29..31 年外留白 + 1月~4月月份栏）；‹ 到 2025（空年 0 项次/0 天）→「今天」回 2026 并自动滚回今天行；筛选 RunRead 联动后仅 19 日 L2、汇总 2 项次/1 天；长按 10-19 弹摘要（含「休息日：Dur1」）；点今天格进 10月19日 详情。
- 本批无 schema 变更与写入（纯读视图 + 筛选复用，走查未产生新数据）。

## 十七、M8-2c 时间线批次（2026-10-19）

范围：任务书 M8 第 6 项第 3 轮——列表时间线视图，共享筛选状态（M8-2 三批：2a 周视图 → 2b 年热力图 → 2c 时间线）。第 6 项完成（15 / 15）。

交付内容：
1. `domain/calendar/TimelineFeed`（D36，纯函数）：`TimelineDay`（`date + DaySummary`）；输入窗口 `[oldest, newest]` 与记录/备注/规则/选项/筛选，复用 `CalendarMarks` 摘要口径（D30/D31），只保留窗口内「有内容」的天（命中筛选的记录 / 休息日登记 / 日期备注）并倒序排列；空日不出现、窗口外记录剔除。
2. `CheckRecordDao.observeExistsBefore` / `DayNoteDao.observeExistsBefore`（`SELECT EXISTS(... WHERE date < :date)`）+ `DailyTraceRepository.observeHasContentBefore`（记录或备注取或，组合 Flow）：支撑「加载更早」可用性判定；无 schema 变更。
3. `CalendarViewModel`（D33/D36 扩展）：`CalendarViewMode` 增 `TIMELINE`；常量 `TIMELINE_PAGE_DAYS = 30`、`TIMELINE_FLOOR`（1970-01-01）、`timelineWindowStart`（含今天共 30 天、不早于下限）；`timelineOldest` 窗口流（`[起始日, 今天]`，今天变化自动重查）+ 存在性查询合成 `WindowData.Timeline`；`loadEarlier()`（步进 30 天下限钳制）；`backToToday`（重置窗口 + `timelineRecenter` 递增信号供 UI 回顶）；state 增 `timelineDays/timelineOldest/timelineCanLoadEarlier/timelineRecenter`，`summaries` 由日流直接映射（长按摘要复用 D30 弹层）。
4. `CalendarScreen`：tab 增「时间线」（4 tab：月/周/年/时间线）；新增 `TimelineView`（LazyColumn 日卡 + 底部「加载更早（30 天）」/「已到最早记录」，空窗口提示「最近 30 天没有记录」且仍可加载）、`TimelineDayCard`（日期 + 今天徽标 + 条数 + 条目行 + 休息日 + 备注；点击进日期详情、长按弹摘要）、`TimelineFooter`；「今天」重置后回顶，「加载更早」保持滚动位置。

验证：
- `testDebugUnitTest` 163/163 全绿（新增 8：TimelineFeed 6 —— 内容日倒序且空日不出现、窗口外内容剔除、筛选只留命中项且备注日仍在、纯备注日与休息日登记日出现、休息日登记受筛选联动、空窗口/空数据；时间线窗口 2 —— 含今天共 30 天起点、起点不早于下限）。
- `lintDebug` 0 errors / 17 warnings（无新增规则）；`assembleRelease`（R8）成功，release 包 1.88MB。
- 模拟器走查截图 `docs/screenshots/m8-23..28`：基线（10-19 七条 + 休息日、10-17 三条 + 备注、10-16 一条，底部「加载更早」）；「加载更早」回溯后 9-19 卡片出现、底部变「已到最早记录」；「今天」重置回最近 30 天（9-19 隐藏、按钮恢复）；筛选 RunRead 联动（10-19 仅 Run1/Read1、休息日栏隐藏、10-17 备注日保留）；点卡进 10月19日 详情（7 条记录）；长按弹摘要（按筛选显示 Run1/Read1）。
- 走查演示数据：经设置临时放开「补卡上限」至 30 天，在 9-19 补记 Bool1 一笔后改回 7 天（正常业务写入，未拉库）。

## 十八、M8-3 长按多选批量操作批次（2026-10-19）

范围：任务书 M8 第 7 项——长按多选批量操作（打卡 / 补卡 / 改备注 / 删除归档）。第 7 项完成，M8 收官（3 / 3）。

交付内容：
1. `domain/checkin/BatchCheckIn`（D37，纯函数）：`valueFor`（BOOLEAN=完成、COUNT=+1、DURATION/VALUE=目标值 → 截至日期最近值；null = 不可批量）+ `canBatch` + `BatchCheckInResult(recorded, skipped)`，今日页与日期详情共用。
2. `CheckRecordDao.updateNoteByIds(ids, note, now)`（空串 = 清除）与 `getLatestUpTo(itemId, date)`（截至日期最近一条，`date DESC, createdAt DESC LIMIT 1`）；`DailyTraceRepository.updateRecordsNote` / `getLatestRecordUpTo` 包装；无 schema 变更。
3. `ui/common/AppUi`：`rememberSelectedIds()`（Set<Long> 可保存多选状态）与 `SelectionActionBar`（底部操作条容器）。
4. 今日页：长按 ItemCard 进多选（复选框 + 顶栏「已选 N 项 / 全选 / 取消」+ 底部「打卡所选（N）」+ 返回键先退多选）；进入多选预载 `loadBatchValues()`（截至今天最近值，未加载完按可选、应用时兜底跳过）；`batchCheckIn` 逐项复用单笔 `addValue`（multiRecordRule 一致），跳过已完成布尔项与不可批量项；snackbar「已记录 N 项（跳过 M 项）」与不可批量行置灰。
5. 日期详情：选择器弹窗行首复选框 + 底部「记录所选（N）」（今天 = 打卡 / 过去 = 补卡 / 未来 = 计划，逐项复用 BackfillPolicy + CheckInPlanner）；记录区长按多选 + 底部「改备注」「删除」；批量改备注弹窗 =「应用」（非空覆盖所选，空输入禁用）+「清除备注」（清空所选）+「取消」；批量删除确认框「将删除所选 N 条记录，不可恢复」；RecordCard 选中态复选框。
6. 管理页：分区内长按多选——使用中/已停用 → 归档 / 移入回收站；已归档 → 取消归档 / 移入回收站；回收站 → 恢复 / 彻底删除（确认框「N 个选项将从回收站彻底删除，历史打卡记录保留」）；批量后只调一次 `scheduler.rescheduleAll()`；「停用/启用」不批量化。

验证：
- `testDebugUnitTest` 174/174 全绿（新增 11：BatchCheckIn 6 —— 布尔未完成 = 1 且已完成跳过、计数 = +1、时长/数值取目标值、无目标取截至日期最近值、均无不可批量、跳过集合聚合；M8BatchOps 5（Robolectric）—— 批量改备注只影响所选、空串清除备注、批量删除只删所选、`getLatestUpTo` 边界（取最近且不含未来）、真实数据链取值）。
- `lintDebug` 0 errors / 17 warnings（与基线同集合，无新增规则）；`assembleRelease`（R8）成功，release 包 1.91MB。
- 模拟器走查截图 `docs/screenshots/m8-29..43`（15 张）：今日页长按多选 Bool1 + Count1 →「打卡所选（2）」→ 进度 5/6、「已记录 2 项」；日期详情 10-17「补卡」选择器勾选 Bool1 + Run1 →「记录所选（2）」→ 记录 3 → 5 条、「已记录 2 项」；记录区长按多选 2 条 → 批量改备注「batch」→ Count1/Bool1 记录注记、「已更新 2 条记录的备注」；删除确认框「将删除所选的 1 条记录，此操作不可恢复。」→ 删除后 4 条、「已删除 1 条记录」；管理页多选 Dur1 + Val1 → 批量归档（使用中 4 / 已归档 2）→ 已归档区取消归档复原（使用中 6、「已取消归档 2 个选项」）；批量移入回收站（回收站 1）→ 彻底删除确认框（取消）→ 恢复（使用中 6、「已恢复 1 个选项」）。
- 走查演示数据：批量打卡/补卡与改备注产生的记录保留（Bool1 今日完成、Count1 今日 +1、10-17 补 Bool1 一笔、Count1/Bool1 记录备注「batch」；10-17 的 Run1 补卡记录已在批量删除演示中删除、最终 4 条）；管理页批量归档/回收站操作走查后已全部复原（6 选项均在「使用中」）；未拉库。


评审修复（2026-10-20，提交仅代码）：
1. 今日页 / 日期详情批量按钮防连点——先同步退出多选或关闭选择器，再异步写入并提示；重复点击不再产生二次写入。
2. 管理页多选态长按守卫——已进入多选后长按任意行不再触发「重新进入多选」，选中集合保持（ManageItemsScreen onLongClick = { if (!selecting) onLongPress() }）。
3. 今日页批量 snackbar——补 
ecorded == 0 && skipped > 0 分支「没有可记录的选项（跳过 M 项）」。
4. 新增 M8BatchViewModelTest（4 例，Robolectric）：今日批量跳过已完成布尔/不可批量项、KEEP_MULTIPLE 计数追加；日期详情补卡跳过已完成布尔并标记 isBackfill、未来日期生成 PLANNED 计划。

验证：
testDebugUnitTest 178/178 全绿（+4）；lintDebug 0 errors / 17 warnings（与基线同集合）；模拟器快检三项修复均符合预期（选择条/对话框即时关闭、多选态长按保持「已选 2 项」）。

## 十九、M9-1 搜索按日期范围批次（2026-10-20）

范围：任务书 M9 第 12 项——搜索支持按日期范围过滤（M9 首批，1 / 4）。

交付内容：
1. domain/search/SearchDateRange.kt（D38，纯函数）：SearchDatePreset（不限 / 近 7 天 / 近 30 天 / 自定义）+ SearchDateRange(from, to)（闭区间，null = 不限）+ 
resolveSearchRange(preset, today, customFrom, customTo) + 
normalizeSearchRange（起 > 止自动交换、允许单边）。
2. CheckRecordDao.search(query, from, to) / DayNoteDao.search(query, from, to)：关键词条件加括号后拼接 (:from IS NULL OR date >= :from) 与 (:to IS NULL OR date <= :to)；DailyTraceRepository 包装加默认参数（null = 不限）；无 schema 变更。
3. SearchViewModel：关键词 + 日期范围合并为 criteria 流统一防抖 300ms；范围在查询时按 DayClock.today() 解析（近 N 天随逻辑日滚动）；setPreset / setCustomRange（自定义自动归一）。
4. SearchScreen：FilterChip 行「不限 / 近 7 天 / 近 30 天 / 自定义」；自定义展开「起始 / 结束」按钮，Material3 DatePickerDialog 选择（UTC 毫秒 → toEpochDay()），范围即时生效；无结果文案在范围生效时提示「放宽日期范围」。

验证：
- testDebugUnitTest 188/188 全绿（新增 10：SearchDateRange 7 —— 不限、近 7 / 30 天含端点、自定义保序、起止交换、单边、空自定义；M9SearchRange 3（Robolectric）—— 记录闭区间 / 端点 / 单边、日期备注同口径、关键词与范围组合）。
- lintDebug 0 errors / 17 warnings（与基线同集合）；assembleRelease（R8）成功，release 包 2.12MB。
- 模拟器走查截图 docs/screenshots/m9-01..07（7 张）：搜索页预设行；关键词「1」不限 19 条 → 近 7 天 18 条（排除 9-19）；自定义起止行；DatePickerDialog；自定义 10-17 ~ 10-17 → 5 条；同范围日期备注「day nayiao test」命中 1 条；起选 10-20 / 止选 10-17 自动归一为 10-17 ~ 10-20。
- 走查未写入数据（仅查询与范围切换）。

## 二十、M9-2 提醒暂停批次（2026-10-20）

范围：任务书 M9 第 11 项——提醒「暂停」（现状仅单个提醒开 / 关）。M9 进度 2 / 4。

交付内容：
1. AppSettings.pausedUntil: Long?（暂停截止 epoch millis，null = 未暂停）；DB version 2 → 3，MIGRATION_2_3 仅 ALTER TABLE app_settings ADD COLUMN pausedUntil INTEGER；schema 3.json 入库。
2. domain/reminder/ReminderPause（D39，纯函数）：isPaused（严格 > now）、activeUntil、isValidUntil。
3. ReminderScheduler：暂停中 scheduleNext 取消当前闹钟、scheduleSnooze 不排定（兜底）；pauseUntil 撤下全部提醒 / 稍后闹钟 + 清已发通知 + 排一次到点恢复闹钟（ReminderBootReceiver.ACTION_RESUME，进程不在也能被广播拉起）；
resume 撤恢复闹钟并重排全部启用提醒；ReminderReceiver 触发时再判暂停，静默丢弃。
4. 设置页新增「提醒」区：未暂停显示 1 小时 / 8 小时 / 24 小时 / 自定义（AssistChip）；暂停中显示「将于 M-d HH:mm 恢复（约 N 分钟 / 小时）」+「立即恢复」；自定义为 DatePicker → TimePicker 两步，按 settings.timezone 解释与展示；SettingsViewModel 先落库再撤 / 重排（避免重排读到旧暂停值），并清理已到期暂停值。

验证：
- testDebugUnitTest 196/196 全绿（新增 8：ReminderPause 4 —— 未设置 / 等于与过去 / 未来、截止有效性；ReminderScheduler 4（Robolectric）—— 暂停中重排不排定、暂停中稍后不排定、暂停撤提醒并排恢复闹钟、恢复后重排下一次）。
- lintDebug 0 errors / 17 warnings（与基线同集合）；assembleRelease（R8）成功，release 包 2.13MB。
- 真机库升级验证（模拟器 v2 → v3，install -r 保留数据）：user_version = 3、pausedUntil 列存在、6 选项 / 19 条记录完整、无崩溃。
- 模拟器走查截图 docs/screenshots/m9-08..13（6 张）：设置页提醒区；暂停 1 小时（dumpsys 确认只剩 REMINDER_RESUME 闹钟、系列闹钟已撤）；立即恢复（系列闹钟回来）；自定义日期；自定义时间 21:00（与设置时区一致）；暂停生效（pausedUntil = 2026-10-20 21:00 +08:00）。走查结束已立即恢复，演示数据未变。

## 二十一、M9-3 每日汇总提醒批次（2026-10-20）

范围：任务书 M9 第 10 项——每日汇总提醒（WorkManager）。M9 进度 3 / 4。

交付内容：
1. AppSettings.dailySummaryEnabled（默认 false）/ dailySummaryTime（当地分钟数，默认 1260 = 21:00）；DB version 3 → 4，MIGRATION_3_4 两条 ALTER TABLE；schema 4.json 入库。
2. domain/summary/DailySummary.kt（D40，纯函数）：buildDailySummary（在册选项为分母、当天 DONE 去重为分子，排除停用 / 归档 / 软删除）、summaryMessage（无选项 / 全部完成 / 待完成三态）、nextSummaryMillis（今天未过则今天、否则明天，按 settings.timezone 解释）。
3. DailySummaryWorker（CoroutineWorker）：开关关闭 / 暂停中 / 无通知权限时静默返回；否则按 DayClock 逻辑日汇总并发布「每日汇总」通知（点按打开 App）；单次失败不重试。
4. DailySummaryScheduler：唯一周期任务 daily_summary（24h，首次延迟到下一汇总时刻；CANCEL_AND_REENQUEUE 保证时刻变更立即生效）；应用启动、设置变更、备份导入后同步，关闭开关即取消任务。
5. 设置页「提醒」区新增「每日汇总提醒」开关 + 汇总时间 FilterChip（20:00 / 21:00 / 22:00 / 自定义 TimePicker）；说明按 settings.timezone 计算、系统调度可能有几分钟偏差；暂停说明补充「每日汇总」。
6. BackupCodec 补充 pausedUntil / dailySummaryEnabled / dailySummaryTime 编解码，恢复备份不再丢失这些设置（旧备份缺字段按默认值解析）。

验证：
- testDebugUnitTest 202/202 全绿（新增 6：DailySummary 6 —— 仅统计在册选项、DONE 去重、全完成、三态文案、今天 / 明天 / 午夜边界）。
- lintDebug 0 errors / 17 warnings（与基线同集合）；assembleRelease（R8）成功，release 包 2.13MB。
- 真机库升级验证（模拟器 v3 → v4，install -r 保留数据）：user_version = 4；dailySummaryEnabled / dailySummaryTime 列存在；6 选项 / 19 条记录 / 2 条提醒完整。
- 模拟器走查截图 docs/screenshots/m9-14..16（3 张）：设置页汇总开关与时刻 chip；自定义 TimePicker；19:00 触发通知「今天已打卡 2 / 6 项，还有 4 项未完成」（WorkManager 准点执行，logcat Worker result SUCCESS）。
- 暂停静默验证：暂停 1 小时后把汇总设为 19:05，Worker 按时执行但未发布通知（dumpsys 无汇总通知）；恢复后重排回 21:00（dumpsys 约 +1h53m）。走查结束已恢复暂停、汇总时间 21:00，演示数据未变。

## 二十二、M9-4 桌面小组件批次（2026-10-20）

范围：任务书 M9 第 9 项——桌面小组件（今日进度 + 快捷打卡）。M9 进度 4 / 4，M9 收官。

交付内容：
1. `domain/widget/WidgetDigest.kt`（D41，纯函数）：在册 = 启用、未归档、未删除，按 `sort, id` 排序；`done` = 当天 DONE 记录按 itemId 去重，百分比四舍五入；`quickItems` 取前 4 个在册选项（已完成项保留位置，避免行跳动）。
2. `widget/TodayWidget.kt`：按 `DayClock + settings` 取逻辑日，组装 RemoteViews（`done / total`、百分比进度条、最多 4 行、无选项空态）；布尔未完成行 → `PendingIntent.getBroadcast` 到 `WidgetActionReceiver`（动作 `WIDGET_CHECK_IN` + item_id）；已完成 / 非布尔 / 头部 /「还有 N 项」→ 打开 `MainActivity`；无组件实例直接返回、不查库；全部实例更新同一份 RemoteViews。
3. `widget/TodayWidgetProvider.kt`（`onUpdate` + `goAsync`）与 `widget/WidgetActionReceiver.kt`（`exported=false`）：后者二次校验选项在册且为布尔、已有 DONE 则幂等跳过，否则 `CheckInPlanner.toggleBoolean(source = WIDGET)` 落库并立即刷新组件。
4. 资源：`res/layout/widget_today.xml`（4 行快捷项）、`res/drawable/widget_background.xml`、`values/colors.xml` + `values-night/colors.xml` 日夜配色、`res/xml/today_widget_info.xml`（4×4、30 分钟周期、previewLayout、description）；Manifest 注册 Provider（系统入口）与 Receiver（仅应用内 PendingIntent 可触发）。
5. `DailyTraceApp`：进程存活时合并 `activeItems + allRecords + settings` Flow，300ms debounce 后刷新组件；`CheckInPlanner.toggleBoolean / newRecord` 增加可选 `source` 参数（默认 MANUAL），通知动作与组件打卡共用写入规则。
6. 无 schema 变更；无新增依赖（经典 RemoteViews，未引入 Glance）。

验证：
- `testDebugUnitTest` 207/207 全绿（新增 5：WidgetDigest —— 在册口径与停用 / 归档 / 删除排除、按管理顺序取前 4、DONE 去重与进度、已完成标记、百分比取整、空数据与零快捷项）。
- `lintDebug` 0 errors / 17 warnings（与基线同集合，无新增规则）；`assembleRelease`（R8）成功，release 包 2.14MB；反查 APK：`TodayWidgetProvider` / `WidgetActionReceiver` / `TodayWidget` 类与 Manifest 注册均保留。
- 模拟器走查截图 `docs/screenshots/m9-17..18`（2 张）：桌面第二页 4×4 组件显示「2 / 6」、进度条、Bool1 / Count1「已完成」、Dur1 / Val1「记录」、「还有 2 项」；临时停用 Bool1 / Count1 后组件刷新为「0 / 4」、Run1 / Read1 显示「打卡」；点击 Run1 行 → 立即变「1 / 4」且 Run1「已完成」。
- 一键打卡数据验证：拉库确认新增 Run1 记录为 `status=DONE`、`source=WIDGET`、`isBackfill=0`；随后用验证前数据库快照还原（还原后 MD5 与快照一致），6 选项 / 19 条记录 / 今日 2/6 复原。走查结束组件仍为 2/6。

## 二十三、M10-1 CSV 导出批次（2026-10-20）

范围：任务书 M10 第 13 项——CSV 导出（验收 14）。M10 进度 1 / 7。

交付内容：
1. `domain/csv/CsvExporter.kt`（D42，纯函数）：全部记录按 `date, createdAt, id` 升序；日期输出 ISO 逻辑日，记录时刻按 `AppSettings.timezone` 格式化；状态 / 来源输出中文标签；数值整数不带 `.0`，空值留空；UTF-8 BOM + CRLF；RFC 4180 转义逗号、引号与换行；以 `= + - @` 开头的选项快照 / 单位 / 备注 / 时区加单引号防公式注入。
2. `SettingsViewModel.exportCsvTo`：IO 线程读取设置与全部记录、生成 CSV、写入 SAF 目标 URI；成功后提示「CSV 导出完成（N 条记录）」，失败提示具体原因。
3. `SettingsScreen`：备份区新增「导出 CSV（打卡记录）」按钮与 `CreateDocument("text/csv")` launcher，默认文件名 `daily_trace_records_YYYYMMDD.csv`（按 `settings.timezone` 取日期）；说明 CSV 仅供表格分析，完整恢复仍用「导出备份」。
4. 无 schema 变更、无新增依赖、无新增权限；CSV 只读导出，不影响演示数据。

验证：
- `testDebugUnitTest` 214/214 全绿（新增 7：CsvExporter —— BOM/表头/排序、逗号引号换行转义、公式注入防护且不误伤负数、状态/来源/补卡/值格式、整数与空值、时区换算、空记录仅表头）。
- `lintDebug` 0 errors / 17 warnings（与基线同集合）；`assembleRelease`（R8）成功，release 包 2.16MB（2,260,326 bytes）。
- 模拟器走查截图 `docs/screenshots/m10-01-csv-export.png`：设置 → 备份 →「导出 CSV（打卡记录）」；SAF 默认文件名 `daily_trace_records_20261020.csv`；保存后提示「CSV 导出完成（19 条记录）」。
- 拉取导出文件校验：UTF-8 BOM；20 个 CRLF（表头 + 19 行）；20 行 × 10 列；状态 17 完成 / 1 部分完成 / 1 计划中；记录时刻为 `Asia/Shanghai`；示例数值 `8.5`、`60`、空值均正确。
- 导出后拉库复核：`user_version = 4`；6 个在册选项、19 条记录、今日 2 个 DONE，演示数据未变。

## 二十四、M10-2 按分组统计批次（2026-10-20）

范围：任务书 M10 第 14 项——按分组统计（验收 11）。M10 进度 2 / 7。

交付内容：
1. `StatsCalculator.GroupRate` + `groupRates(...)`（D43，纯函数）：仅在用选项（启用、未归档、未删除）；按分组 `sort, id` 顺序汇总，未分组 / 孤儿 groupId 置末；分组完成率 = 组内各选项 `Σ doneDays / Σ expectedDays`，不取百分比平均；PARTIAL 沿用设置；TIMES_PER_MONTH 不计入分母，`quotaItemCount` 单独标注；无期望日时 `rate.rate = null`。
2. `StatsViewModel`：合并 `repository.groups` Flow；`StatsUi.groupStats` 随范围 / 趋势 / 数据变化重算；原选项统计与周 / 月趋势逻辑不变。
3. `StatsScreen`：统计范围下方新增「分组统计」卡片；每行显示分组色点、名称、加权完成率、`N 个选项 · 完成 x/y 项次`、配额项数量、进度条；无分组数据时显示短空态。
4. 无 schema 变更、无新增依赖；分组统计只读，不改变选项、记录或趋势。

验证：
- `testDebugUnitTest` 219/219 全绿（新增 5：GroupStatsTest —— 期望日加权合并、PARTIAL 口径、配额项排除与标注、分组顺序 / 未分组 / 孤儿 groupId、无在用选项）。
- `lintDebug` 0 errors / 17 warnings（与基线同集合）；`assembleRelease`（R8）成功，release 包 2.16MB（2,260,326 bytes）。
- 模拟器走查截图 `docs/screenshots/m10-02-group-stats.png`：临时把 6 个演示选项分配到 4 个种子分组，本周分组卡显示健康 100%（4/4）、学习 / 工作 / 运动各 50%（1/2）；色点、项次与进度条正常，分组顺序与种子 sort 一致；配额项排除 / 标注口径由单测覆盖。
- 走查后以数据库快照还原（MD5 与快照一致：`160cff2c360c20be85a6e2ff5ca05f39`），首页恢复未分组 2/6；6 个在册选项 / 19 条记录未变。

## 二十五、M10-4 日期格式与日期语言批次（2026-10-20）

范围：任务书 M10 第 16 项——日期格式、语言设置（视觉与交互）。M10 进度 3 / 7；第 15 项色盲友好模式按产品取舍不做，仅保留既有字段，不进入实现。M10-4 只做日期标签，不翻译应用界面。

交付内容：
1. `DateLanguage`（SYSTEM / ZH / EN）与 `DateFormat`（AUTO / YMD / MDY / DMY）枚举；`AppSettings.dateLanguage` / `dateFormat` 默认 ZH / AUTO；`Converters` 持久化；DB v4→v5 迁移新增 `dateLanguage TEXT NOT NULL DEFAULT 'ZH'` 与 `dateFormat TEXT NOT NULL DEFAULT 'AUTO'`，旧数据保持中文自然格式。
2. `domain/time/DateDisplay.kt`（D44，纯函数）：`short` / `full` / `monthTitle` / `yearTitle` / `weekTitle` / `weekdayHeader` / `monthShort`；AUTO 中文「2026年10月20日 周二」「10月20日 周二」、英文「Tue, Oct 20, 2026」「Tue, Oct 20」；YMD / MDY / DMY 输出 `2026-10-20` / `10/20/2026` / `20/10/2026`；SYSTEM 仅按系统语言区分中文 / 英文，其他语言回退英文。
3. 接入今日标题、日历月 / 周 / 年标题、星期表头、年视图月份栏、年月选择器、日期详情标题、搜索命中日期、时间线卡片与长按摘要；数字格式不改变月 / 周 / 年标题的语言自然写法。
4. 设置页新增「日期与语言」区块：日期语言三 chips、日期格式四 chips、实时预览与「仅影响日期显示，应用界面仍为中文」说明；修改后立即写入设置并刷新页面。
5. `BackupCodec` 编解码新增两字段；旧备份缺失时回退 ZH + AUTO，新备份完整往返。
6. CSV、备份文件名与数据库日期保持原口径；无新增依赖、无新增权限。

验证：
- `testDebugUnitTest` 228/228 全绿（新增 9：`DateDisplayTest` 8 —— 中英文 AUTO、三种数字格式、中英文跨月 / 跨年周标题、SYSTEM 语言回退、星期表头与月份短名、设置工厂；`M5BackupTest` 1 —— 旧备份缺字段回退 ZH + AUTO；设置往返断言同步扩展）。
- `lintDebug` 0 errors / 17 warnings（与基线同集合）；`assembleRelease`（R8）成功，release 包 2.16MB（2,260,326 bytes）。
- 模拟器走查：release 覆盖安装后 DB 从 v4 迁移到 v5，6 个在册选项 / 19 条记录 / 今日 2/6 全部保留；中文默认显示「10月20日 周二」。切换 English + AUTO 后，日历月标题 `October 2026`、周标题 `Oct 19 – 25, 2026`、年标题 `2026`、月份栏 `Sep / Oct / Nov / Dec`、星期表头 `Mon…Sun`、时间线 `10/20/2026` 均正确；切换 English + MDY 后首页 `10/20/2026`、设置预览 `10/20/2026`、搜索命中 `10/20/2026` 一致。
- 走查后恢复中文 + AUTO，杀进程重启确认持久化；截图 `docs/screenshots/m10-04-date-language.png`（设置页英文 + 月/日/年预览）。
- Room schema v5 已导出：`app/schemas/com.gh.daily_trace.data.DailyTraceDatabase/5.json`。

## 二十六、M10-5 系统大字号字体缩放回归批次（2026-10-21）

范围：任务书 M10 第 17 项——系统字体缩放回归。M10 进度 4 / 7；第 15 项色盲友好模式按产品取舍不做。M10-5 只改 UI 自适应，不改数据、筛选与统计口径。

交付内容：
1. 日历月视图：日期数字在 >1.6 倍时使用 labelSmall，避免两位数被 28dp 日期圆裁切；日期格高度按字阶分档（默认 48dp，大字号 60 / 66 / 76dp），给彩点与「+N」留出垂直空间；大字号下彩点上限降为 3（>1.6 倍为 2），隐藏的完成项合并进「+N」，图例在 >1.4 倍改为纵向排列。
2. 日历 tab：字阶随缩放收紧（>1.25 倍 labelMedium，>1.5 倍 labelSmall），保持单行、不折行；年视图月份栏宽度随字号加宽，避免「10月」等标签折行。
3. 周视图：标题最多两行；选项名称栏按字阶加宽（上限 1.6 倍），日期数字在 >1.6 倍收紧，星期与日期完整显示。
4. 统计趋势图：>1.4 倍时日期标签分两行，绘图区高度随标签行数增加，避免底部标签截断。
5. 无 schema、无新增依赖、无新增权限；普通字号布局保持原样。

验证：
- `testDebugUnitTest` 228/228 全绿（M10-5 为布局回归，无新增纯逻辑，沿用 M10-4 基线）。
- `lintDebug` 0 errors / 17 warnings（与基线同集合）；`assembleRelease`（R8）成功，release 包 2.16MB（2,260,326 bytes）。
- 模拟器走查（Medium_Phone_API_37.0，1080×2400）：`font_scale` 1.3 / 1.5 / 2.0 覆盖今日、日历月 / 周 / 年 / 时间线、日期详情、搜索、筛选、统计、管理、设置、长按摘要、记录弹窗与年月选择器；修复 2.0 月视图日期数字裁剪、图例竖排挤压、日历 tab「时间线」折行、月视图「+N」垂直裁剪，以及 1.5 年视图月份栏折行、统计趋势标签截断。
- 截图 `docs/screenshots/m10-05-large-font.png`：2×2 拼图（1.3 月视图 / 1.5 统计趋势 / 2.0 月视图 / 2.0 周视图）。
- 走查后系统字号恢复默认；未改数据库、备份与 CSV 口径。

## 二十七、M10-6 压力测试批次（2026-10-21）

范围：任务书 M10 第 18 项——500 选项 × 数万记录。M10 进度 5 / 7；第 15 项色盲友好模式不做，第 19 项崩溃日志 / 数据修复待做。M10-6 不改 schema、CSV / 备份口径，不新增依赖或权限。

压力数据：
- 500 个在册选项（BOOLEAN / COUNT / DURATION / VALUE 各 125，分入 4 个分组，每组 125；500 条 DAILY 频率规则）。
- 50,000 条记录，覆盖 100 个逻辑日（每日 500 条），DONE / PARTIAL / SKIPPED 混合；DB 约 6.3MB（6,586,368 bytes）。
- 今日页只勾选 DONE，统计页按默认 `partialCountsAsDone=true` 把 PARTIAL 也计入完成，因此同一天显示 451/500 与 490/500 是两套既有口径，不是数据不一致。

基线（修复前）：
- 统计页用 `Column + verticalScroll` 一次性组合全部 500 张卡片，首次进入约 13 秒才显示；logcat 出现 146 帧跳过，主线程明显阻塞。
- 长按日期摘要一次性组合 500 行，出现 99 帧跳过。
- 统计聚合按选项反复扫描全量记录，存在 O(选项数 × 记录数) 的重复过滤。

交付内容：
1. `StatsScreen` 改为 `LazyColumn`：今日摘要、范围、分组、选项卡片、趋势分节；500 个选项只组合当前可见的卡片，趋势仍在列表末尾。
2. `StatsViewModel` 聚合管线加 `flowOn(Dispatchers.Default)`；新增 `StatsCalculator.totalsByItem` 单遍历聚合，替代逐项扫描全部记录。
3. 新增 `DailyRecordIndex`（D46）：一次遍历建立按日、按「日 + 选项」索引与每项最早日期；`StatsCalculator` 增加索引重载，逐项完成率、连续、分组、周 / 月趋势、月度配额均走 O(1) 查找；原按日分组接口保留供测试与旧调用。
4. `DaySummaryDialog` 在条目 >20 时改用限高 `LazyColumn`，普通 6 项演示数据仍保持原紧凑弹窗；500 行摘要可滚动浏览。
5. 新增 `DailyRecordIndexTest`：5 万条记录索引定位、完成率 / 连续计算，以及索引路径与按日分组路径的统计结果一致性。

验证：
- `testDebugUnitTest` 231/231 全绿（新增 3：`totalsByItem` 单遍历聚合 1；`DailyRecordIndex` 5 万条索引定位 1、索引路径与按日分组统计一致性 1）。
- `lintDebug` 0 errors / 17 warnings（与基线同集合）；`assembleRelease`（R8）成功，release 包 2.16MB（2,260,326 bytes）。
- 模拟器 release 走查：安装后首次启动约 7 秒（含首次 5 万条数据读取 / 系统 dexopt），之后进程冷启动约 2.2 秒；首页 500 项正常显示并可打卡（451 → 452 在 700ms 内完成）。
- 统计页：首页稳定后进入，临时 release 日志显示数据到达约 1.1 秒、聚合 382ms，内容约 2 秒内出现；由 500 项顶部滚动到第 500 项与趋势区正常，月趋势切换约 1 秒；logcat 不再出现长时主线程阻塞。
- 日历：月视图约 2 秒加载；长按 21 日摘要由 99 帧跳过降至 30 帧，500 行可连续滚动；时间线 500 条与日期详情可正常滚动，无 ANR。
- 搜索「001」返回 100 条记录，2 秒内显示；长按摘要、时间线与日期详情均使用懒加载列表。
- 截图 `docs/screenshots/m10-06-stress.png`：2×2 拼图（500 项今日页 / 500 项统计顶部分组 / 第 500 项与趋势 / 500 行长按摘要）。
- 走查后恢复演示 DB 快照（6 个在册选项 / 19 条记录，首页 0/6；MD5 `6e100dbab6e1b2633a9976c3c6b91b9a`）。

## 二十八、M10-7 崩溃日志与数据修复批次（2026-10-22）

范围：任务书 M10 第 19 项——崩溃日志、数据修复。M10 进度 6 / 7（第 15 项色盲友好模式按产品取舍不做）；M10 全部可执行项完成。本批不改 schema、CSV / 备份口径，不新增依赖或权限。

交付内容：
1. 新增 `diagnostics/CrashReportFormatter` 与 `CrashLogManager`：`DailyTraceApp.onCreate` 最先安装全局未捕获异常处理器，崩溃前把应用版本、设备、Android / SDK、线程与完整堆栈写入 `files/crash_logs/crash_*.log`（UTF-8，最多保留 10 份），随后继续交给系统默认处理器，不吞掉系统崩溃流程；写入失败静默兜底，避免二次崩溃。
2. 设置页新增「诊断与修复」区块：显示崩溃日志份数与最近时间；可 SAF 导出全部日志为文本、清除日志（二次确认）；不采集用户数据、不联网、不需要存储权限。
3. 新增 `diagnostics/DataRepairManager`：只读扫描 + 事务内安全修复。检查孤立频率规则 / 提醒、无效分组引用、空日期备注、筛选视图失效 id、缺失或越界的设置（周起始、日起始小时、补卡上限、自动备份间隔、每日汇总时刻、时区、过期暂停值），并执行 `PRAGMA quick_check` 报告数据库文件完整性。
4. 修复口径：删除选项已不存在的规则 / 提醒；无效 `groupId` 置空；空备注删除；筛选视图仅清理失效 id；设置夹到合法范围或回退默认时区。选项彻底删除后的孤儿打卡记录**保留只读展示，不删除**，仅在报告中计数。
5. 「从最近本机备份恢复」：二次确认后用 `files/backups/` 最近一份自动备份覆盖恢复，成功后重排提醒与每日汇总并刷新设置。
6. 新增仅 debug 构建的 `CrashTestReceiver`（`app/src/debug/`），用于真实进程崩溃验收；release 包不包含该入口。

验证：
- `testDebugUnitTest` 240/240 全绿（新增 9：`CrashReportFormatterTest` 2——格式字段与空版本回退；`M10DiagnosticsTest` 7——只读扫描、修复引用 / 设置、缺失设置重建、最近备份恢复、无备份返回 null、崩溃日志写入 / 导出 / 清理、10 份上限）。
- `lintDebug` 0 errors / 17 warnings（与基线同集合；debug 测试接收器已用 `tools:ignore` 标注）；`assembleRelease`（R8）成功，release 包 2.17MB（2,276,710 bytes）。
- 模拟器走查（Medium_Phone_API_37.0）：安装 debug 后通过广播触发真实未捕获异常，`files/crash_logs/` 生成 1200 bytes 日志，含「M10-7 崩溃日志验收测试」、版本 1.0.0 (1)、设备 / 系统、main 线程与完整堆栈；logcat 同时出现 `FATAL EXCEPTION` 且进程退出，证明已交回系统处理器。
- 覆盖安装 release 后崩溃日志保留，设置页显示「崩溃日志 1 份 · 最近 10-22 03:29」；导出 / 清除按钮在无日志时禁用，清除后显示「崩溃日志 0 份 · 最近 暂无」。
- 演示库注入 5 类异常后「检查数据」显示：孤立规则 1、孤立提醒 1、无效分组引用 1、空日期备注 1、筛选失效引用 1，数据库完整性通过；点「修复」后 Snackbar 显示清理计数，复查为「未发现数据异常」，记录 19 条与备注 1 条保留。
- 截图 `docs/screenshots/m10-07-diagnostics.png`：2×2 拼图（诊断与修复区块 / 数据检查发现异常 / 修复完成 / 复查通过）。
- 走查后恢复演示 DB 快照（6 个在册选项 / 19 条记录 / 1 条备注 / 3 条规则 / 2 条提醒，首页 0/6），清除测试崩溃日志；未改 schema、备份与 CSV 口径。

## 二十九、M11 品牌与响应式布局批次（2026-10-22）

范围：任务书 M11 第 20 项——应用名改回「日迹」；第 21 项——平板 / 响应式布局。M11 进度 2 / 2，全部完成。

交付内容：
1. `strings.xml` 的 `app_name` 改为「日迹」，小组件名改为「日迹 · 今日」，组件头部 `widget_today.xml` 同步改为「日迹」；包名保持 `com.gh.daily_trace`，不迁移、不丢数据。
2. 新增 `ui/common/WindowSize.kt`（D48）：按 Material 3 官方 600 / 840dp 断点划分 compact / medium / expanded，用 `BoxWithConstraints` 计算，不新增 window-size-class 依赖；`ContentMaxWidth = 1000dp`，内容宽度 ≥640dp 时网格双列。
3. 根导航 `AppNavHost`：medium / expanded 且可用高度 ≥480dp 时显示左侧 `NavigationRail`（品牌「日迹」+ 今日 / 日历 / 统计 / 管理 / 设置），点击切换顶层目的地；紧凑宽度与矮横屏继续用原顶栏导航，避免导航栏被裁切。
4. 今日页、统计页改为 `LazyVerticalGrid`：今日进度、筛选条、分组标题、统计范围 / 分组 / 趋势等整行跨列；选项卡片在内容宽度 ≥640dp 时双列。其他页面沿用各自顶栏，并通过根级 1000dp 限宽居中，避免平板上单列文本/表单过长。
5. 手机（<600dp）布局与交互不变；平板字体缩放 2.0 下今日页导航栏与双列卡片无裁切。
6. 无 schema 变更、无 CSV / 备份口径变化、无新增依赖或权限。

验证：
- `testDebugUnitTest` 242/242 全绿（新增 2：`WindowSizeTest` —— 600 / 840dp 断点、640dp 双列阈值）。
- `lintDebug` 0 errors / 17 warnings（与基线同集合）；`assembleRelease`（R8）成功，release 包 2.17MB（2,293,078 bytes）。
- 模拟器（Medium_Phone_API_37.0）release 覆盖安装：`wm size 2560x1600` + `density 320`（1280×800dp）平板横屏，导航栏显示、今日双列、统计双列、日历 / 设置内容限宽居中；`wm size 1600x2560` + `density 320`（800×1280dp）竖屏，导航栏保留、今日仍双列；恢复 1080×2400 手机后无导航栏、今日单列，布局与改动前一致。`font_scale 2.0` 平板复测今日页导航栏 / 双列无裁切。
- `aapt dump badging` 显示 `application-label:'日迹'`；系统 App info 页面显示「日迹」，包名仍为 `com.gh.daily_trace`。
- 截图 `docs/screenshots/m11-01-brand.png`（应用名）、`docs/screenshots/m11-02-tablet.png`（2×2：平板今日 / 统计 / 日历 / 设置）。
- 走查后恢复 `wm size` / `density` / `font_scale` 默认；演示数据 6 个在册选项 / 19 条记录未变，首页 0/6；未改 schema、备份与 CSV 口径。

## 三十、M12 高频与频率补全批次（2026-10-22 起）

范围：任务书未排期项 B16「每周 N 次」、B19 常用选项置顶、B20 FAB 快速添加、C28 筛选全选；C27 筛选行配色顺延至后续批次。用户确认（2026-10-22）：B16 按配额语义处理；B19 用方案 A（自动：按最近使用次数取 top 3，不足 3 个显示实际数量 ≥1 即显示，总项数 ≥8 才显示）；B20 FAB 只放今日页；先做 C28。

### M12-1：B16「每周 N 次」（2026-10-22 完成）

交付内容：
1. `FrequencyType` 新增 `TIMES_PER_WEEK`（枚举位置在 TIMES_PER_MONTH 与 CUSTOM 之间）；`FrequencyRule` 新增 `timesPerWeek: Int?` 列。
2. Room DB v5 → v6：`MIGRATION_5_6` 手写 `ALTER TABLE frequency_rules ADD COLUMN timesPerWeek INTEGER`（非破坏性，旧数据为 null），不开 destructive fallback；`app/schemas/…/6.json` 已导出。
3. 备份编解码：`encodeRule` / `decodeRule` 增加 `timesPerWeek` 字段（旧备份缺字段回退 null，向后兼容）。
4. 统计口径（D49）：`isExpectedDay` 对 TIMES_PER_WEEK 返回 false（无日级期望）；分组统计配额判定改为 `rule?.type in QUOTA_TYPES`（月度 / 周度都是配额项，不计完成率分母、单独标注）；新增 `weeklyQuotaProgress()`（本自然周按 `weekStart` 对齐，有 DONE/PARTIAL 记录的天数 = 1 次），与 `monthlyQuotaProgress()` 共用 `quotaProgress()` 内部口径；统计页配额项显示「x/N 次」+「按本周 / 本月配额统计，不计算连续」。
5. 编辑页（M7-1 UI 模式镜像）：频率类型 chips 增加「每周几次」，选中后输入「每周目标次数」（1–7 校验，默认 3；isError + supportingText 与月度配额一致）；`ItemEditViewModel` 补 `freqTimesPerWeek` 状态、校验（“每周次数需为 1-7 的整数”）与保存分支（非当周类型保留旧值）。
6. 今日页 / 日历 / 尾注不受影响：配额类型当天无期望，今日页计数仍按全选项统计；CSV / 备份格式口径不变（备份 JSON 为增列）、不新增依赖或权限。

验证：
- `testDebugUnitTest` 246/246 全绿（新增 4：TIMES_PER_WEEK 无日级期望、周配额统计本周有完成记录的天数、周配额按 weekStart 对齐、周配额项分组不计分母并单独标注）。
- `lintDebug` 0 errors / 17 warnings（与基线同集合）；`assembleRelease`（R8）成功，release 包 2,293,078 bytes；classes.dex 含 `TIMES_PER_WEEK` 确认为新构建。
- 模拟器（Medium_Phone_API_37.0，1080×2400 手势模式）release 覆盖安装：旧 DB v5 演示数据直接启动 → v6 迁移无崩溃，6 选项 / 19 记录完整；编辑页频率 chips 出现「每周几次」，选中后输入框默认 3 且提示正确；保存后统计页 Bool1 显示「2/3 次 · 按本周配额统计，不计算连续」，分组统计标注「含 1 个配额项」。
- 截图 `docs/screenshots/m12-01-home.png`（迁移后首页数据完整）、`m12-02-edit-chip.png`（新增 chip）、`m12-03-week-quota-editor.png`（周配额编辑态）、`m12-04-week-quota-stats.png`（统计页周配额展示）。
- 走查后 Bool1 的频率规则保存为「每周 3 次」（演示数据变化：6 选项 / 19 条记录不变，其中 1 个选项由 DAILY 改为 TIMES_PER_WEEK）；CSV / 备份 / schema 版本迁移均已在真实设备上生效。

### M12-2：B19「⭐ 近期常用」（2026-10-22 完成）

交付内容：
1. `CheckRecordDao` 新增 `observeRecentUsage(from, to, limit)`：给定 epochDay 闭区间按 itemId 统计记录条数（全部状态含 PLANNED，一条记录 = 一次使用），SQL `GROUP BY itemId ORDER BY useCount DESC LIMIT :limit`；`DailyTraceRepository` 透传。
2. `TodayViewModel` 新增 `recentItems: StateFlow<List<CheckItem>>`：最近 30 天（含今天）窗口取 top 3；拦截条件 `总在册项数 ≥ 8` 才显示，可见时不足 3 个显示实际数量；与今日列表同口径受筛选视图约束（FilterMatcher，hasRecords 只看当天记录）；次数并列按 sort / id 稳定排序。纯计算抽为 `computeRecentFrequent()` 顶层函数（可直接单测）。
3. `TodayScreen` 在进度卡之后插入全跨「⭐ 近期常用」区块：每行 图标 + 名称 + 主操作按钮，点击与该选项卡片主按钮行为一致（布尔=打卡 / 计数=+1 / 时长·数值=记一笔）；不提供长按多选与明细展开，避免与卡片主列表重复。
4. 走查运行数据：新增 W8 / W9 / W10 三个选项补足门槛（6 → 9 项），走查期间由 chip 直写与手动打卡产生少量新记录。
5. 无 schema 变更、无新增依赖或权限；CSV / 备份口径不变。

验证：
- `testDebugUnitTest` 250/250 全绿（新增 4：DAO 30 天窗口计数排序与 limit 截断、纯计算次数排序 / 停用与不在册剔除、不足 3 个显示实际数量；门槛显隐以纯函数直测，不依赖 stateIn 初值）。
- `lintDebug` 0 errors（warning 与基线同集合）；`assembleRelease`（R8）成功，release 包 2,293,078 bytes（classes.dex 内含「近期常用」字符串确认新构建）。
- 模拟器（Medium_Phone_API_37.0）release 覆盖安装：9 个在册选项达门槛后区块出现，排序、快捷直写行为符合预期；6 个选项（<8）时不显示（纯计算用例覆盖）。
- 截图 `docs/screenshots/m12-05-recent-frequent.png`（区块与 chip）、`m12-06-recent-frequent-done.png`（chip 打卡后 1/9、Bool1 已完成）。

### M12-3：B20 今日页 FAB + C28 筛选全选（2026-10-22 完成）

交付内容：
1. B20：今日页 Scaffold 增加 `ExtendedFloatingActionButton`（「＋ 新增」），仅今日页（日期详情页等其余页面不加）；点击直达「新建选项」编辑页（`AppRoutes.itemEdit(0L)`），`TodayRoute` / `TodayScreen` 增加 `onCreateItem` 回调，由 `AppNavHost` 接通；多选模式下隐藏 FAB，避免遮挡批量操作条。
2. C28：筛选视图编辑器「按选项（可多选）」「按分组（可多选）」标题行右侧各加一枚「全选 / 清空」切换按钮——未全选时一键全选该区全部条目，已全选时清空；区为空时禁用。行为纯 UI 状态切换，不改 `FilterView` 结构与筛选语义。
3. 无 schema 变更、无 CSV / 备份口径变化、无新增依赖或权限。

验证：
- `testDebugUnitTest` 250/250 全绿（本批无新增纯逻辑，保持基线；`FilterMatcher` / 编辑器既有用例不受影响）。
- `lintDebug` 0 errors / 17 warnings（与基线同集合）；`assembleRelease`（R8）成功，release 包 2,309,462 bytes（较上批 +16KB，与新 FAB / 全选 UI 一致）。
- 模拟器（Medium_Phone_API_37.0）release 覆盖安装走查：
  - 今日页右下角出现「＋ 新增」FAB，点击直达新建选项编辑页（名称为空校验提示正常）；返回后未产生数据。
  - 筛选视图编辑器（RunRead 视图）：「全选」→ 9 项全部选中且按钮变「清空」；「清空」→ 全部取消且按钮变「回全选」；分组区按钮同构。退出未保存，原视图「选项 2 个（任一命中）」保持不变。
- 截图 `docs/screenshots/m12-07-fab-new-item.png`（FAB 打开的新建编辑页）、`m12-08-filter-select-all.png`（全选态）。

## 三十一、C27 筛选行视觉强化批次（2026-10-22）

M12-3 收尾后开工的顺延项：筛选视图编辑器的选中行由「文字 + 复选框」升级为「颜色块 / 图标 + 名称 + 复选框」。

交付内容：
1. `FilterScreen.CheckRow` 泛化：增加 `leading` 组合槽（前导视觉），布局改为 行首前导视觉 + 名称（weight 填充）+ 行尾复选框；整行可点切换选中（复选框只读展示，避免双重响应），行高保持 ≥48dp 触达标准。
2. 按选项区行首放通用 `ItemBadge`（选项色 16% 浅底 + emoji，28dp，与今日页 / 管理页同款，颜色为辅助、emoji 始终保留）；按分组区行首放分组色圆点（12dp，`CircleShape`），分组无图标故只用色块。
3. 仅 UI 层变更：不改 `FilterView` 结构、筛选语义（FilterMatcher）、CSV / 备份，无 schema / 依赖 / 权限变化。

验证：
- `testDebugUnitTest` 250/250 全绿（纯 UI 变更，保持基线）；`lintDebug` 0 errors / 17 warnings；`assembleRelease`（R8）成功，release 包 2,309,462 bytes。
- 模拟器（Medium_Phone_API_37.0）release 覆盖安装走查：筛选视图 → RunRead 编辑器，选项行显示各选项颜色浅底 emoji 徽章（演示项无 emoji 显示默认「•」，徽章底色随选项色变化），分组行显示「健康（绿）/ 学习（蓝）」分组色圆点；截图 `docs/screenshots/c27-01-filter-rows.png`（选项行）、`c27-02-filter-group-rows.png`（分组行）。
- 交互验证：点击选项行任意位置（含行首徽章与名称）均可切换选中——点击 Bool1 行后像素级确认复选框由空框变为实心勾选态，再点「取消」退出不保存；截图复检行内布局为「徽章 + 名称（填满中段）+ 行尾复选框」，无遮挡无裁切。

## 三十二、B18 周报 / 月报报告式汇总批次（2026-10-22）

任务书最后一项未排期（现状仅周/月趋势柱状图）：统计页新增「周期报告」卡，把一段周期聚合成报告式文字汇总。

交付内容：
1. 新域 `domain/report/PeriodReport.kt`：纯函数 `buildPeriodReport(items, rulesByItem, index, startDay, endDay, partialCountsAsDone)`，双口径并列：
   - 期望日口径（与统计页 / 趋势柱一致，D16）：完成 / 期望「项×日」聚合为完成率（配额类型无日级期望、不计分母）；
   - 实际完成口径（报告专属）：完成项次 = DONE 记录数（partialCountsAsDone 时 PARTIAL 计入）、打卡天数 = 当天至少一个在用选项有完成记录、最佳日 = 当天完成的不同选项数最多（并列取最早）、最勤选项 = 期内实际完成天数 top3（并列保持管理顺序）。
   - 上期对比 = 紧邻等长时段（本月 1..22 → 上月同长 22 天）期望日完成率；仅统计在用选项（启用、未归档、未删除）。
   - 文案纯函数 `reportRateText`（null → 「—」，否则整数百分比）与 `reportDeltaText`（±Npp / 持平，任一缺失不展示）。
2. `StatsViewModel`：新增 `ReportMode`（周报 = 本周按 weekStart 对齐 / 月报 = 本月，窗口与「统计范围」同口径复用 rangeDays）+ `report` 进 `StatsUi`；沿用 flowOn(Default) 后台聚合。
3. `StatsScreen`：趋势卡后新增「周期报告」区（周报 / 月报 FilterChip）+ `PeriodReportCard`：标题与日期段、当期完成率、完成项次 · 打卡天数、部分完成注记、最佳日、最勤选项「名称 ×N」、较上期对比；无在用选项显示 EmptyState。日期段沿用统计页「M/d」短格式（不引入日期语言设置）。
4. 无 schema / 依赖 / 权限变化，不改 CSV / 备份。

验证：
- `testDebugUnitTest` 260/260 全绿（新增 `domain/report/PeriodReportTest` 10 例：空数据基线、DAILY 聚合、最佳日并列取最早、PARTIAL 两口径、配额项无期望但计入实际口径、最勤选项前三与并列顺序、上期等长窗口、完成率 / 差值文案、单日窗口守卫）。
- `lintDebug` 0 errors / 17 warnings；`assembleRelease`（R8）成功，release 包含 B18 内容（classes.dex 含「周期报告」「最佳日」字符串）。
- 模拟器（Medium_Phone_API_37.0）release 覆盖安装走查：统计页 → 周期报告（周报 / 月报切换、数值与截图核对）。
- 截图 `docs/screenshots/b18-01-report-week.png`（周报）、`b18-02-report-month.png`（月报）。

