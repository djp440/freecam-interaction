. "$PSScriptRoot/common.ps1"
Start-ProjectLog
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
try {
    if (!(Test-Path -LiteralPath (Join-Path $JdkHome 'bin/javac.exe'))) {
        $directory = Split-Path -Parent $JdkHome
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
        $archive = Join-Path $directory 'OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip'
        $expected = 'F9D6E191AB098C0D416E7D588A24420A8621CD2F4720DAB2459B8B7B2D2D8B4E'
        if (!(Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) {
            Write-Host '正在下载 Temurin JDK 21（约 205 MB）……'
            Invoke-WebRequest -Uri 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip' -OutFile $archive -TimeoutSec 600
        }
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) {
            throw 'JDK SHA-256 校验失败，未解压；请重新运行安装脚本。'
        }
        Expand-Archive -LiteralPath $archive -DestinationPath $directory -Force
    }
    Use-ProjectJava
    & (Join-Path $JdkHome 'bin/javac.exe') -version
    if ($LASTEXITCODE -ne 0) { throw 'javac 检查失败。' }
    Write-Host "JDK 就绪：$JdkHome；系统 PATH 和 JAVA_HOME 未修改。"
} catch {
    Write-Host "安装失败：$_"
    exit 1
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
    Stop-Transcript | Out-Null
}
