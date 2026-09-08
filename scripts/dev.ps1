param([string]$Action = 'check')
. "$PSScriptRoot/common.ps1"
Start-ProjectLog
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
$runLock = $null
Push-Location -LiteralPath $ProjectRoot
try {
    $tasks = @{ check = '--version'; build = 'build'; client = 'runClient'; server = 'runServer'; smoke = 'build' }
    if (!$tasks.ContainsKey($Action)) { throw "未知操作 '$Action'，允许：check、build、client、server、smoke。" }
    if ($Action -in @('client', 'server')) {
        $runDirectory = Join-Path $ProjectRoot "run/$Action"
        New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
        try {
            $runLock = [System.IO.File]::Open((Join-Path $runDirectory '.dev.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
        } catch {
            throw "无法独占运行目录 $runDirectory，请检查是否已启动同类实例或缺少写入权限。"
        }
    }
    Use-ProjectJava
    & ./gradlew.bat $tasks[$Action] --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle $Action 失败，退出码 $LASTEXITCODE；检查日志后可重试。" }
    if ($Action -eq 'smoke') {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $jar = Join-Path $ProjectRoot 'build/libs/godview_build-0.1.0.jar'
        $archive = [System.IO.Compression.ZipFile]::OpenRead($jar)
        try {
            foreach ($entry in @('local/godviewbuild/GodviewBuild.class', 'local/godviewbuild/ModLog.class', 'META-INF/neoforge.mods.toml')) {
                if (!$archive.GetEntry($entry)) { throw "JAR 缺少 $entry" }
            }
            $reader = [System.IO.StreamReader]::new($archive.GetEntry('META-INF/neoforge.mods.toml').Open(), [System.Text.Encoding]::UTF8)
            try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
            if ($metadata.Contains('${') -or !$metadata.Contains('modId="godview_build"') -or !$metadata.Contains('versionRange="[1.21.1]"') -or !$metadata.Contains('上帝视角建造')) {
                throw 'JAR 元数据存在未展开占位符、版本错误或中文编码错误。'
            }
            Write-Host "冒烟通过：$jar；入口类、日志模块、中文元数据与精确版本范围正常。"
        } finally { $archive.Dispose() }
    }
} catch {
    Write-Host "执行失败：$_"
    exit 1
} finally {
    if ($null -ne $runLock) { $runLock.Dispose() }
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
    Stop-Transcript | Out-Null
}
