# AGENTS.md · 日迹开发协作约定

本文件面向所有参与「日迹」（Daily_Trace）开发的人与 AI 代理。开始工作前先读本文件，再按「文档导航」查阅对应文档。若本文件与代码事实冲突，以代码与 `docs/项目状态与计划.md` 的已确认事实为准，并在当批文档中修正。

## 一、项目定位

- Android 本地优先的打卡 / 每日追踪应用，包名 `com.gh.daily_trace`，界面中文。
- 数据只存本机 Room 数据库；仅申请 `INTERNET` 用于向 GitHub 查询最新版本号，不上传任何本地数据，不做账号、云同步、统计上报。
- 当前版本：`versionName 1.0.1`、`versionCode 10001`；数据库版本 6。
- 最低 Android 7.0（minSdk 24），target / compileSdk 37。

## 二、文档地图

| 文档 | 用途 |
|------|------|
| `README.md` | 项目简介、技术栈、常用命令、领域约定 |
| `贡献指南.md` | 环境搭建、开发流程、提交与自查清单 |
| `docs/文档导航.md` | 全部文档的入口与阅读路径 |
| `docs/使用手册.md` | 面向用户的完整功能操作说明 |
| `docs/架构说明.md` | 分层、模块、数据流与关键设计 |
| `docs/测试指南.md` | 测试分层、命令、AVD 走查与排障 |
| `docs/发布流程.md` | 版本号、质量门禁、构建与发布检查 |
| `docs/数据格式说明.md` | 数据库、备份 JSON、CSV 口径 |
| `docs/决策记录.md` | D50 起的决策全文；D1–D49 索引 |
| `docs/项目状态与计划.md` | 事实、里程碑、D1–D49 决策全文、批次记录 |
| `docs/需求差距与验收矩阵.md` | 任务书条目与验收状态 |
| `docs/常见问题.md` | 使用与开发常见问题 |
| `docs/隐私与数据安全.md` | 本地数据、权限、导出与风险边界 |
| `docs/截图/` | 历史验收截图，只读引用，不随意覆盖 |

文档一律使用中文；除 `README.md`、`AGENTS.md` 外，新增文档文件名使用中文。文档内引用截图时，`docs/` 下的文档写相对路径 `截图/xxx.png`，根目录文档写 `docs/截图/xxx.png`。

## 三、领域硬约束（不得随意改动）

1. **逻辑日**：所有“今天 / 日期”以 `domain/time/DayClock` 的 `epochDay` 为准，默认凌晨 4 点前算前一天；时区取 `AppSettings.timezone`（默认 `Asia/Shanghai`，暂无 UI 修改入口）。跨逻辑日刷新依赖各页 `LifecycleResumeEffect`。
2. **历史快照**：记录展示一律使用 `itemNameSnapshot` / `itemColorSnapshot` / `itemIconSnapshot`，不得回查当前选项名称或颜色。改名、改色、软删除后历史必须保持不变。
3. **软删除与归档**：移入回收站 = `deletedAt` 非空；归档 = `archived=true`，与停用一样从今日列表、日期详情选择器、统计与小组件隐藏。两者都不删除历史记录。彻底删除选项也保留孤儿记录只读展示。
4. **打卡写入**：必须走 `CheckInPlanner` 生成计划，再 `applyPlan` 落库；不得在 ViewModel / UI 里直接拼记录。合并 / 累加 / 保留多条由 `MultiRecordRule` 决定，合并目标只认最新一条 `DONE`。
5. **补卡与计划**：补卡只在日期详情页，受 `maxBackfillDays` 限制（0 = 禁用），落库 `isBackfill=true`；计划仅限未来日期（`date > today`），同日同选项 upsert，`status=PLANNED`、`isBackfill=false`。
6. **跳过**：统一写 `FrequencyRule.skipDates`（受 `allowSkip` 门控），不新建 `status=SKIPPED` 记录；跳过日不计期望、不中断连续。旧 SKIPPED 数据仍按既有统计口径处理。
7. **部分完成**：始终追加 `status=PARTIAL` 记录，不参与合并 / 累加目标；是否计入完成率由 `partialCountsAsDone` 决定（默认 true，当前无 UI 开关）。
8. **统计口径**：`DAILY` / 无规则 = 每天；`DAYS_OF_WEEK` = 所选周几；`CUSTOM` = 锚点起每隔 N 天；`TIMES_PER_MONTH` / `TIMES_PER_WEEK` 是配额，无日级期望、不进完成率分母；今天未打卡有宽限；所有统计按 itemId 隔离。详见 D16、D43、D49。
9. **提醒**：时刻按 `AppSettings.timezone` 解释；精确闹钟权限可用则准点，否则降级并提示；免打扰时段走低优先级“安静”渠道；暂停期间撤下全部提醒与汇总，到点自动恢复；通知动作打卡必须幂等。
10. **备份 / CSV**：备份 JSON v1 为全表快照（含回收站），导入先校验、单事务全量替换；CSV 为 UTF-8 BOM + CRLF，一行一条记录，只用于分析、不承担恢复职责。任何格式变更必须同步 `docs/数据格式说明.md`、备份兼容测试与决策记录。
11. **权限**：自声明只允许 `POST_NOTIFICATIONS`、`SCHEDULE_EXACT_ALARM`、`RECEIVE_BOOT_COMPLETED`、`INTERNET`；合并后的 Manifest 另有 WorkManager 传递的 `WAKE_LOCK`、`ACCESS_NETWORK_STATE`、`FOREGROUND_SERVICE` 与 AndroidX 自动生成的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（口径见 D58）。`INTERNET` 只允许访问 `api.github.com` 与 `github.com`（更新检查，见 D59）：CI 校验源码中的 http(s) 主机必须落在白名单内，越界即失败；该检查是文本扫描，不是「绝不联网」的强保证。新增自声明权限、依赖或网络能力都必须先登记决策并说明必要性。
12. **数据库迁移**：Room schema 已到 v6，升级必须写显式 `Migration` 并导出 schema，禁止 destructive fallback；迁移后必须用旧库覆盖安装验证数据完整。

## 四、固定批次工作流

每个功能批次按以下顺序执行，不得跳步：

1. **单测**：先补 / 改测试，再实现；运行 `.\gradlew.bat testDebugUnitTest`。
2. **lint**：运行 `.\gradlew.bat lintDebug`，错误必须为 0；新增警告需说明或修复。
3. **release 构建**：运行 `.\gradlew.bat assembleRelease`，确认 R8 下可构建；必要时覆盖安装冒烟。
4. **模拟器走查**：在 AVD 上按验收清单逐项操作，截图存入 `docs/截图/`，记录实际结果与演示数据变化。
5. **文档同步**：更新使用手册、状态文档、验收矩阵、决策记录、数据格式（如涉及）。
6. **一键门禁**：运行 `.\scripts\verify.ps1`，确认测试、lint、release 全绿并记录 APK 大小。
7. **单批提交并推送**：一个批次一个提交，提交信息使用中文类型前缀；推送 `origin main`。

若某一步失败，停止后续步骤，修复后从失败步骤重跑。不得把失败状态提交进 `main`。

## 五、提交规范

- 提交信息格式：`类型: 中文摘要`，类型用 `feat` / `fix` / `docs` / `test` / `refactor` / `chore`。
- 一个提交只做一件事；文档批次使用 `docs: 中文文档体系与软件使用手册`。
- 提交前必须 `git status` 确认没有 APK、`build/`、`local.properties`、`.idea/`、临时数据库或截图草稿。
- 不提交未跟踪的实验目录（如 `.trae-html-share-packages/`、`github-agent-skills-survey/`）。
- 不建 tag、不建 GitHub / Gitee Release，除非用户明确要求；推送目标为 `origin main`。
- 提交后确认工作区只剩既有未跟踪目录，并在批次记录中写明提交号。

## 六、禁止事项

- 禁止修改 `app/src` 业务逻辑来“让文档好写”；文档必须描述真实实现，冲突时在最终汇报中列出。
- 禁止新增依赖、权限、schema 字段、备份 / CSV 字段而不更新测试、迁移和文档。
- 禁止在 UI 中直接访问 DAO 或数据库；统一经 Repository。
- 禁止用当前选项信息渲染历史记录；禁止物理删除有历史记录的选项。
- 禁止提交 `local.properties`、`.idea/`、`build/`、`*.apk`、`*.iml`、崩溃日志、真实用户备份。
- 禁止在文档中写未经验证的命令、路径或截图；所有 PowerShell 命令必须真实可跑。
- 禁止覆盖或删除 `docs/截图/` 中已有验收证据；如需新增截图，使用新的批次前缀。
- 禁止把 D50 起的决策只写在聊天或提交信息里；必须落到 `docs/决策记录.md`。

## 七、AVD 走查约定

- 默认 AVD：`Medium_Phone_API_37.0`（1080×2400）。启动后先确认 `adb devices`。
- 本机 `adb` 不在 PATH，完整路径：`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`。
- 多设备时所有命令必须带 `-s emulator-5554`，避免装到其他模拟器。
- 走查使用 release 包时先 `.\gradlew.bat assembleRelease`，再 `adb -s emulator-5554 install -r app\build\outputs\apk\release\app-release.apk`。
- 截图统一放在 `docs/截图/`，命名为 `<批次>-<序号>-<主题>.png`；截图前后记录：
  - 构建类型与版本（debug / release、versionName）；
  - 设备 / AVD、分辨率、字体缩放、时区；
  - 操作步骤、预期、实际结果；
  - 演示数据是否变化，是否需要恢复快照。
- 走查结束恢复系统设置（`wm size`、`density`、`font_scale`、时区）与演示数据；不得把测试崩溃日志、测试选项留在演示库。
- 提醒 / 小组件 / 权限类走查需额外记录 `dumpsys alarm`、通知、`logcat` 或数据库核对结果。

## 八、决策登记（D50 起）

- D1–D49 的全文保留在 `docs/项目状态与计划.md`；`docs/决策记录.md` 只提供索引。
- 从 **D50** 起，新决策必须写入 `docs/决策记录.md`，格式：

  ```markdown
  ### D50 标题（YYYY-MM-DD）

  - 背景：
  - 决策：
  - 理由 / 取舍：
  - 影响范围：
  - 验证方式：
  ```

- 编号连续递增，不得复用；状态文档只保留编号与一行摘要，不重复全文。
- 涉及数据格式、权限、依赖、统计口径、备份兼容的决策，必须同时更新对应文档与测试。

## 九、常用命令

```powershell
.\gradlew.bat testDebugUnitTest          # JVM / Robolectric 单测
.\gradlew.bat lintDebug                  # 静态检查
.\gradlew.bat assembleDebug              # debug APK
.\gradlew.bat assembleRelease            # release APK（R8，正式签名，读 keystore.properties）
.\gradlew.bat installDebug               # 安装 debug 到已连接设备
.\scripts\verify.ps1                     # 单测 + lint + release 一键门禁
```

## 十、完成定义（Definition of Done）

- 行为有测试覆盖，`testDebugUnitTest` 全绿；
- `lintDebug` 0 error；
- `assembleRelease` 成功，必要时已装机冒烟；
- 模拟器走查完成，截图与记录入库；
- 使用手册 / 状态 / 验收矩阵 / 决策记录已同步；
- `verify.ps1` 全绿；
- 单批提交并推送，工作区干净。
