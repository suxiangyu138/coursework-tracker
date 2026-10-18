@echo off
setlocal
cd /d "%~dp0"

if not exist "coursework-tracker.jar" (
    echo [ERROR] coursework-tracker.jar not found in this folder.
    pause
    exit /b 1
)

set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA_EXE=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVA_EXE for /f "delims=" %%i in ('where javaw.exe 2^>nul') do set "JAVA_EXE=%%i"
if not defined JAVA_EXE for /f "delims=" %%i in ('where java.exe 2^>nul') do set "JAVA_EXE=%%i"

if not defined JAVA_EXE (
    echo [ERROR] Java not found. Install Java 17+ and set JAVA_HOME or PATH.
    pause
    exit /b 1
)

start "" "%JAVA_EXE%" -Xms32m -Xmx256m -XX:+UseSerialGC -jar "coursework-tracker.jar"
exit /b 0