# 构建 Windows 免安装版（exe + 内置 Java 运行时）
# 需要 JDK 17+，且 jpackage 在 PATH 中。
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    [xml]$pom = Get-Content 'pom.xml'
    $version = $pom.project.version
    $name = 'CourseWorkTracker'
    $inputDir = 'target\jpackage-input'
    $destDir = 'target\jpackage'
    $appDir = Join-Path $destDir $name

    if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
        throw '未找到 jpackage，请安装 JDK 17+ 并加入 PATH'
    }

    mvn -B -q -DskipTests package

    Remove-Item $inputDir, $destDir -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $inputDir | Out-Null
    Copy-Item 'dist\coursework-tracker.jar' (Join-Path $inputDir 'coursework-tracker.jar')

    jpackage --type app-image `
        --name $name `
        --app-version $version `
        --input $inputDir `
        --main-jar 'coursework-tracker.jar' `
        --main-class 'com.coursework.tracker.App' `
        --icon 'packaging\coursework-tracker.ico' `
        --dest $destDir `
        --vendor '苏巷雨' `
        --description '作业哨兵 · 课程作业截止提醒与进度追踪器' `
        --copyright '© 2026 苏巷雨 · MIT License' `
        --java-options '-Xms32m' `
        --java-options '-Xmx256m' `
        --java-options '-XX:+UseSerialGC' `
        --java-options '-Dfile.encoding=UTF-8'

    # 首次测试启动可能生成 data/logs，打包前清掉，避免把本地数据带进发布包
    Remove-Item (Join-Path $appDir 'app\data'), (Join-Path $appDir 'app\logs') -Recurse -Force -ErrorAction SilentlyContinue

    New-Item -ItemType Directory -Force 'release' | Out-Null
    $zip = "release\$name-$version-windows-x64.zip"
    Remove-Item $zip -Force -ErrorAction SilentlyContinue
    Compress-Archive -Path $appDir -DestinationPath $zip -Force
    Write-Host "生成: $zip"
    Write-Host "解压后双击: $name\$name.exe"
} finally {
    Pop-Location
}
