@echo off
chcp 65001 >nul
setlocal

rem 以较小的内存占用启动作业哨兵（无控制台窗口）
rem 用法：双击本文件，或在命令行执行 scripts\start.bat

set "APP_DIR=%~dp0.."
set "JAR=%APP_DIR%\dist\coursework-tracker.jar"
if not exist "%JAR%" set "JAR=%APP_DIR%\target\coursework-tracker-1.0.0.jar"

if not exist "%JAR%" (
    echo [错误] 未找到 %JAR%
    echo 请先在项目根目录执行：mvn package
    pause
    exit /b 1
)

start "" javaw -Xms32m -Xmx256m -XX:+UseSerialGC -jar "%JAR%"
exit /b 0
