@echo off
setlocal
set "APP_DIR=%~dp0.."
set "JAR=%APP_DIR%\dist\coursework-tracker.jar"
if not exist "%JAR%" (
    for %%f in ("%APP_DIR%\target\coursework-tracker-*.jar") do set "JAR=%%~ff"
)
if not exist "%JAR%" (
    echo [ERROR] jar not found. Run "mvn package" first.
    pause
    exit /b 1
)

set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA_EXE=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVA_EXE for /f "delims=" %%i in ('where javaw.exe 2^>nul') do set "JAVA_EXE=%%i"
if not defined JAVA_EXE for /f "delims=" %%i in ('where java.exe 2^>nul') do set "JAVA_EXE=%%i"
if not defined JAVA_EXE (
    echo [ERROR] Java not found. Install Java 17+.
    pause
    exit /b 1
)

start "" "%JAVA_EXE%" -Xms32m -Xmx256m -XX:+UseSerialGC -jar "%JAR%"
exit /b 0