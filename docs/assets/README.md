# 文档资源

界面截图等资源放在此目录，文档中通过相对路径引用。

## 已收录

| 文件 | 内容 | 出现位置 |
| --- | --- | --- |
| `main-light.png` | 主界面（浅色主题，20 条示例作业） | README「界面」 |
| `dialog-assignment.png` | 编辑作业对话框 | README「界面」 |
| `icon-256.png` | 应用图标（256px） | README 标题右侧 |
| `icon-64.png` | 应用图标（64px） | 文档 / 宣传图 |

## 截图方式

```bash
java -jar target/coursework-tracker-1.0.0.jar --screenshot=docs/assets/main-light.png
```

由程序自身把主窗口渲染为 PNG，避免系统缩放与窗口遮挡造成的误差；截图前请先生成示例数据（`--seed-demo`）。

## 计划补充

| 文件 | 内容 |
| --- | --- |
| `main-dark.png` | 深色主题主界面 |
| `tray-reminder.png` | 托盘气泡提醒 |
| `grade-detail.png` | 课程成绩详情（目标分反推） |

> 截图要求：不含个人隐私信息（学号、真实姓名可打码）。
