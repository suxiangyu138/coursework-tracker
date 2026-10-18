# 贡献指南

感谢你关注「作业哨兵 CourseWorkTracker」。这是一个 Java 课程设计项目，目前由作者维护，欢迎通过 Issue 反馈问题、通过 Pull Request 提交改进。

参与本项目即表示你同意遵守 [行为准则](CODE_OF_CONDUCT.md)。

---

## 1. 环境准备

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 及以上 | CI 使用 Temurin 17；开发机 JDK 25 也可编译 `--release 17` |
| Maven | 3.8 及以上 | 依赖管理与打包 |
| 操作系统 | Windows / macOS / Linux | 托盘功能需要桌面环境 |
| Git | 2.x | 建议开启 `core.autocrlf` 或使用仓库自带 `.gitattributes` |

```bash
git clone https://github.com/suxiangyu138/coursework-tracker.git
cd coursework-tracker
mvn clean test        # 跑测试
mvn clean package     # 打包 fat jar
java -jar target/coursework-tracker-1.0.0.jar
```

首次启动会询问是否生成示例数据；也可以 `--seed-demo` 直接生成 8 门课 / 20 条作业。

---

## 2. 分支与提交

### 2.1 分支模型

| 分支 | 用途 |
| --- | --- |
| `main` | 始终可运行、可打包；受分支保护，CI 必须通过 |
| `feature/<简述>` | 新功能，例如 `feature/export-backup` |
| `fix/<简述>` | 缺陷修复，例如 `fix/countdown-refresh` |
| `docs/<简述>` | 文档改动 |

建议流程：

```bash
git checkout -b feature/xxx
# 开发、测试
git add -A
git commit -m "feat(service): ..."
git push -u origin feature/xxx
# 在 GitHub 发起 Pull Request
```

### 2.2 提交信息（Conventional Commits）

```text
<type>(<scope>): <中文简述>
```

| type | 用途 |
| --- | --- |
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `docs` | 文档 |
| `refactor` | 重构（不改变行为） |
| `test` | 测试 |
| `build` | 构建、依赖 |
| `ci` | 持续集成 |
| `style` | 格式 |
| `chore` | 杂项 |

示例：

```text
feat(service): 增加 CSV 批量导入
fix(ui): 修复倒计时跨天后未变为逾期
test(util): 补充闰年与跨年边界用例
docs(readme): 补充备份与恢复说明
```

> 一次提交只做一件事；提交前确保 `mvn test` 通过。

---

## 3. 代码规范

- 包名全小写：`com.coursework.tracker.*`
- 模型优先使用 `record`，服务使用 `XxxService`，数据访问使用 `XxxDao`
- 业务代码禁止直接调用 `LocalDateTime.now()`，统一使用 `ClockProvider.now()`（便于测试）
- SQL 必须使用 `PreparedStatement` 参数化，禁止字符串拼接
- 不吞异常：捕获后要么记录日志，要么转换为 `BusinessException` / `DataAccessException`
- 单类 ≤ 400 行、单方法 ≤ 40 行；复杂算法写清"为什么"
- 全项目 UTF-8；提交前确认中文无乱码

更完整的规范见 [docs/03-开发计划.md](docs/03-开发计划.md) 第 5 节。

---

## 4. 测试要求

| 改动类型 | 要求 |
| --- | --- |
| 新增算法（排序、成绩、时间） | 必须补单元测试，覆盖边界（逾期、闰年、除零、不可达等） |
| 修改 DAO / SQL | 更新 `DaoIntegrationTest`，使用内存库隔离 |
| 纯 UI 调整 | 至少手工走查 `docs/04-测试与验收.md` 中相关用例 |
| 缺陷修复 | 先补一个能复现缺陷的测试，再修复 |

```bash
mvn test                              # 全部测试
mvn test -Dtest=UrgencyCalculatorTest # 单个测试类
mvn test jacoco:report                # 覆盖率报告 target/site/jacoco/index.html
```

CI 要求：JDK 17 下测试通过、打包成功、`--selftest` 通过。

---

## 5. Pull Request 检查清单

- [ ] 目标分支为 `main`，分支已同步最新代码
- [ ] `mvn clean test` 通过
- [ ] 新增/修改的功能有对应测试或手工验证记录
- [ ] 更新了相关文档（README / docs / CHANGELOG）
- [ ] 无调试残留（`System.out.println`、临时注释）
- [ ] 提交信息符合 Conventional Commits
- [ ] 不包含数据库、日志、构建产物等本地文件（`data/`、`logs/`、`target/` 已被忽略）

---

## 6. Issue 规范

- **Bug**：使用 [Bug 报告模板](.github/ISSUE_TEMPLATE/bug_report.yml)，附复现步骤、JDK 版本、日志片段
- **功能建议**：使用 [功能请求模板](.github/ISSUE_TEMPLATE/feature_request.yml)，说明使用场景与期望行为
- 提 Issue 前先搜索是否已有相同问题
- 涉及安全问题的，请勿公开提交 Issue，按 [SECURITY.md](SECURITY.md) 私下联系

Gitee 镜像同样接受 Issue：<https://gitee.com/suxiangyu138/coursework-tracker/issues>

---

## 7. 文档要求

功能变更时同步更新：

| 变更 | 需要更新 |
| --- | --- |
| 新功能 / 行为变化 | `README.md` 功能表、`CHANGELOG.md` |
| 接口 / 数据模型 | `docs/02-技术方案.md` |
| 测试策略 / 用例 | `docs/04-测试与验收.md` |
| 交付范围 | `docs/05-课程设计交付说明.md` |

---

## 8. 联系方式

- GitHub Issues：<https://github.com/suxiangyu138/coursework-tracker/issues>
- 邮箱：suxiangyu_dev@foxmail.com

感谢你的贡献！
