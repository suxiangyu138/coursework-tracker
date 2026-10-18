# 构建 Windows 免安装版（exe + 内置 Java 运行时）
# 需要 JDK 17+，且 jpackage 在 PATH 中。
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root

# 用 .NET 手工写 zip，确保条目使用正斜杠（/），Windows 资源管理器与跨平台解压都正常。
# 不直接用 Compress-Archive：部分 PowerShell 版本会把路径分隔符写成反斜杠。
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
function New-StandardZip([string]$sourceDir, [string]$destination, [string]$baseName) {
    $sourceDir = (Resolve-Path -LiteralPath $sourceDir).Path.TrimEnd('\')
    if (Test-Path -LiteralPath $destination) { Remove-Item -LiteralPath $destination -Force }
    $zip = [System.IO.Compression.ZipFile]::Open($destination, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Get-ChildItem -LiteralPath $sourceDir -Recurse -File | ForEach-Object {
            $relative = $_.FullName.Substring($sourceDir.Length + 1).Replace('\', '/')
            $entryName = if ([string]::IsNullOrEmpty($baseName)) { $relative } else { "$baseName/$relative" }
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $zip, $_.FullName, $entryName, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally {
        $zip.Dispose()
    }
}

try {
    [xml]$pom = Get-Content 'pom.xml'
    $version = $pom.project.version
    $name = 'CourseWorkTracker'
    $inputDir = 'target\jpackage-input'
    $destDir = "target\jpackage-$version"
    $appDir = Join-Path $destDir $name

    if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
        throw '未找到 jpackage，请安装 JDK 17+ 并加入 PATH'
    }

    mvn -B -q -DskipTests package

    Remove-Item $inputDir -Recurse -Force -ErrorAction SilentlyContinue
    if (Test-Path $destDir) { Remove-Item $destDir -Recurse -Force }
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
    New-StandardZip -sourceDir $appDir -destination $zip -baseName $name
    Write-Host "生成: $zip"
    Write-Host "解压后双击: $name\$name.exe"
} finally {
    Pop-Location
}
