Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$JdkHome = Join-Path $env:LOCALAPPDATA 'GodviewBuild/jdk-21.0.12.1+1'

function Start-ProjectLog {
    $directory = Join-Path $ProjectRoot 'logs/tools'
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    do {
        $path = Join-Path $directory "$(Get-Date -Format 'yyyy-MM-dd HH-mm-ss').log"
        if (Test-Path -LiteralPath $path) { Start-Sleep -Seconds 1 }
    } while (Test-Path -LiteralPath $path)
    Start-Transcript -LiteralPath $path -NoClobber | Out-Null
    Write-Host "日志：$path"
}

function Use-ProjectJava {
    $java = Join-Path $JdkHome 'bin/java.exe'
    if (!(Test-Path -LiteralPath $java)) {
        throw '缺少项目 JDK 21，请先执行 pwsh -File scripts/setup.ps1'
    }
    $env:JAVA_HOME = $JdkHome
    $env:PATH = "$JdkHome\bin;$env:PATH"
    & $java -version
    if ($LASTEXITCODE -ne 0) { throw '项目 Java 无法运行，请重新检查安装。' }
}
