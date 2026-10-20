# Daily_Trace

Android 本地打卡 / 每日追踪应用。纯本地数据（Room），无网络权限。

## 技术栈

- Kotlin + Jetpack Compose (Material3) + Navigation Compose
- Room 2.8.5（schema 导出至 `app/schemas/`，升级必须写显式 Migration）
- 手动依赖注入（`di/Container.kt`，随 Application 生存）
- Gradle 9.6 / AGP 9.4.0 / Kotlin 2.2.10 / KSP；minSdk 24，target & compileSdk 37
- 单元测试：JUnit4 + Robolectric（本地 JVM，无设备即可运行）

## 常用命令

```powershell
.\gradlew.bat testDebugUnitTest   # 单元测试（改代码后必跑）
.\gradlew.bat assembleDebug       # 打 debug APK
.\gradlew.bat installDebug        # 安装到已连接设备/模拟器
.\gradlew.bat assembleRelease     # 打 release APK（R8 开启；当前用调试签名）
.\gradlew.bat lintDebug           # 静态检查（发布前必跑，错误必须清零）
```

## 目录结构

```
app/src/main/java/com/gh/daily_trace/
  MainActivity.kt            入口，主题接线
  DailyTraceApp.kt / di/     应用与手动 DI 容器
  data/                      实体、DAO、Repository（数据层，Phase 1 已完成）
  domain/                    业务纯逻辑：time/ 逻辑日与月历网格，checkin/ 打卡写入计划与补卡策略，filter/ 筛选匹配，reminder/ 提醒时刻与免打扰窗口计算，summary/ 每日汇总口径，widget/ 组件数据口径，stats/ 完成率与连续/趋势统计
  reminder/                  提醒设施：AlarmManager 调度、WorkManager 每日汇总、通知渠道与动作、开机/时区变更重排
  widget/                    桌面小组件：RemoteViews 渲染、AppWidgetProvider、快捷打卡接收器
  backup/                    备份：JSON 编解码（BackupCodec）、事务导入导出（BackupManager）、本机自动备份（AutoBackup）
  ui/                        界面：navigation / today / calendar / datedetail / search / filter / stats / manage / itemedit / settings / common / theme
app/src/test/                单元测试（Robolectric 内存库 + 纯逻辑单测）
```

## 关键领域约定

- 日期一律用「逻辑日」`epochDay`（Long），见 `domain/time/DayClock.kt`：
  当日 `startOfDay`（默认凌晨 4 点）之前算前一天；换算以 `AppSettings.timezone` 为准。
- 打卡记录带快照（名称/颜色/图标），选项改名、删除后历史展示不变。
- 选项删除为软删除（回收站）；归档仅隐藏于打卡列表，均不影响历史记录。
- 同一天多次打卡行为由 `MultiRecordRule` 决定：合并一条 / 累加 / 保留多条。
- 补卡：只在日期详情页进行；受「补卡上限」（`maxBackfillDays`，0=禁用）限制，落库 `isBackfill=true`。
- 日期备注（第三层备注）：一天一条 `DayNote`，空白即删除；与选项默认备注、单次打卡备注相互独立。
- 提醒：时刻按 `AppSettings.timezone` 解释；精确闹钟权限可用则准点，否则系统可能延迟数分钟（编辑页有提示与引导）；免打扰时段内用「安静」低优先级渠道静默提示；通知动作「打卡」（布尔型直写、幂等）与「稍后 N 分钟」（一次性闹钟）；每次触发自动续排下一次。支持全局暂停：1 / 8 / 24 小时或自定义截止时刻，暂停期间撤下闹钟与通知，到点自动重排。
- 每日汇总：设置页可开启（默认关闭），每天 20:00 / 21:00 / 22:00 或自定义时刻汇总当天完成情况（WorkManager 周期任务，系统可能有几分钟偏差）；内容为「已打卡 X / 总数」，暂停期间同样静默。
- 桌面小组件：4×4「每日打卡 · 今日」显示今日进度与最多 4 个管理顺序快捷项；布尔未完成项点按直接打卡（来源记为 WIDGET，幂等），已完成 / 非布尔项点按打开 App；数据变化即时刷新，系统 30 分钟周期兜底。
- 统计：期望日模型（DAILY/无规则=每天；DAYS_OF_WEEK=所选周几；TIMES_PER_MONTH=月度配额进度）；跳过日（SKIPPED 或 allowSkip+skipDates）不计期望也不中断连续；今天未打卡宽限不中断；PARTIAL 计完成受 `partialCountsAsDone`；所有统计按 itemId 隔离。
- 备份：JSON v1 全表快照（选项含回收站；枚举存 name；可空字段存 null；含暂停与每日汇总设置）；导入为单事务全量覆盖，先校验后写入（非法数据不动库），成功后自动重排提醒与汇总任务；本机自动备份在应用启动与退后台时按间隔检查，存 `files/backups/`（保留最近 5 份），设置页可导出到 SAF 自选位置或手动立即备份。

## 界面设计（UI v2，2026-10-17）

- 品牌主题：自建 Material 3 明暗色板（主色「生长绿」，辅助琥珀），完整字阶与形状层级；默认关闭动态取色，设置中可开启 Material You（Android 12+）。
- 通用组件集中在 `ui/common/AppUi.kt`：区块卡片、空态/加载态、徽章、状态胶囊、开关行；页面统一 16dp 边距、卡片列表与 Snackbar 操作反馈。
- 无障碍：图标按钮均带 contentDescription；可点击行 ≥48dp；正文对比度 ≥4.5:1；颜色仅作辅助（选项 emoji 双重编码）。
- 截图：`docs/screenshots/`（`01–10` 为旧版；`ui2-01`–`ui2-20` 为 UI v2 前后对比素材）。

## 当前状态与计划

见 `docs/status-and-plan.md`（含已确认事实、假设、待确认问题、阶段计划与决策记录）。

注：git 已初始化（分支 `main`）并推送至 Gitee：`https://gitee.com/itaita/daily_trace_app`（2026-10-17）。
