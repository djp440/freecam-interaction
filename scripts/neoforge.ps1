param([ValidateSet('build', 'smoke')][string]$Action = 'smoke')
. "$PSScriptRoot/common.ps1"
Start-ProjectLog
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
Push-Location -LiteralPath $ProjectRoot
try {
    Use-ProjectJava
    $tasks = @('build')
    if ($Action -eq 'smoke') { $tasks += 'interactionCheck' }
    & ./gradlew.bat -p tooling/neoforge @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'NeoForge 构建或交互自检失败' }
    if ($Action -eq 'smoke') {
        & java -ea --class-path 'tooling/neoforge/build/classes/java/main' scripts/CameraMotionCheck.java
        if ($LASTEXITCODE -ne 0) { throw 'NeoForge 相机自检失败' }
    }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
    Stop-Transcript | Out-Null
}
