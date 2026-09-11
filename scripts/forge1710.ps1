param([ValidateSet('setup', 'check', 'build', 'client', 'server', 'smoke')][string]$Action = 'check')
. "$PSScriptRoot/common.ps1"
Start-ProjectLog
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
$previousJava8 = $env:GODVIEW_JAVA8_HOME
$java8 = Join-Path $env:LOCALAPPDATA 'GodviewBuild/jdk8u504-b01'
$JdkHome = Join-Path $env:LOCALAPPDATA 'GodviewBuild/jdk-25.0.4.1+1'
$runLock = $null
Push-Location -LiteralPath $ProjectRoot
try {
    if ($Action -eq 'setup' -and !(Test-Path -LiteralPath "$JdkHome/bin/javac.exe")) {
        $directory = Split-Path -Parent $JdkHome
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
        $archive = Join-Path $directory 'OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip'
        $expected = '00C847D804F4A78E9F04F2683FAF14FED898535B177B7FC704486CB0284E9283'
        if (!(Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) {
            try {
                Invoke-WebRequest 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip' -OutFile $archive -TimeoutSec 600
            } catch {
                Invoke-WebRequest 'https://ghproxy.net/https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip' -OutFile $archive -TimeoutSec 600
            }
        }
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) { throw 'Java 25 SHA-256 校验失败' }
        Expand-Archive -LiteralPath $archive -DestinationPath $directory -Force
    }
    if ($Action -eq 'setup' -and !(Test-Path -LiteralPath "$java8/bin/javac.exe")) {
        $directory = Split-Path -Parent $java8
        $archive = Join-Path $directory 'OpenJDK8U-jdk_x64_windows_hotspot_8u504b01.zip'
        $expected = 'EA43D46EDE95B51E44A12C66711706CDDC762E0A766C54BCCEA18954E902B2AA'
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
        if (!(Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) {
            try {
                Invoke-WebRequest 'https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u504-b01/OpenJDK8U-jdk_x64_windows_hotspot_8u504b01.zip' -OutFile $archive -TimeoutSec 600
            } catch {
                Invoke-WebRequest 'https://ghproxy.net/https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u504-b01/OpenJDK8U-jdk_x64_windows_hotspot_8u504b01.zip' -OutFile $archive -TimeoutSec 600
            }
        }
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) { throw 'Java 8 SHA-256 校验失败' }
        Expand-Archive -LiteralPath $archive -DestinationPath $directory -Force
    }
    if (!(Test-Path -LiteralPath "$java8/bin/javac.exe")) { throw '请先执行 scripts/forge1710.ps1 setup' }
    if (!(Test-Path -LiteralPath "$JdkHome/bin/java.exe")) { throw '缺少构建 JDK 25，请执行 scripts/forge1710.ps1 setup' }
    Use-ProjectJava
    $env:GODVIEW_JAVA8_HOME = $java8
    & "$java8/bin/java.exe" -version
    if ($LASTEXITCODE -ne 0) { throw 'Java 8 无法运行' }
    if ($Action -in @('client', 'server')) {
        New-Item -ItemType Directory -Path run -Force | Out-Null
        $runLock = [System.IO.File]::Open((Join-Path $ProjectRoot 'run/.forge1710.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    }
    $tasks = @{ setup = '--version'; check = '--version'; build = 'build'; client = 'runClient'; server = 'runServer'; smoke = 'build' }
    $gradleTasks = @($tasks[$Action])
    if ($Action -eq 'smoke') { $gradleTasks += 'actionCheck' }
    & ./gradlew.bat @gradleTasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Forge 1.7.10 $Action 失败：$LASTEXITCODE" }
    if ($Action -eq 'smoke') {
        New-Item -ItemType Directory -Path build/checks -Force | Out-Null
        & "$java8/bin/javac.exe" -encoding UTF-8 -cp build/classes/java/main -d build/checks scripts/LegacyCheck.java
        if ($LASTEXITCODE -ne 0) { throw 'Java 8 自检编译失败' }
        & "$java8/bin/java.exe" -ea '-Dfile.encoding=UTF-8' -cp 'build/classes/java/main;build/checks' LegacyCheck
        if ($LASTEXITCODE -ne 0) { throw 'Java 8 自检失败' }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $jar = Join-Path $ProjectRoot 'build/libs/freecam_interaction-0.1.0-forge1710-experiment.jar'
        $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
        try {
            foreach ($name in @('mcmod.info', 'local/freecaminteraction/FreecamInteractionMod.class', 'local/freecaminteraction/ModLog.class',
                    'local/freecaminteraction/FreecamInteraction.class', 'local/freecaminteraction/core/FreecamLoadingPlugin.class',
                    'local/freecaminteraction/FreecamEffects.class',
                    'local/freecaminteraction/core/FreecamTransformer.class', 'local/freecaminteraction/client/FreecamClient.class',
                    'local/freecaminteraction/item/ItemFreecamWand.class', 'local/freecaminteraction/WandTier.class', 'local/freecaminteraction/FreecamWandRegistry.class',
                    'local/freecaminteraction/FreecamChunkLoader.class', 'local/freecaminteraction/FreecamDropCollector.class',
                    'local/freecaminteraction/blueprint/BlueprintData.class', 'local/freecaminteraction/blueprint/BlueprintBlockEntry.class',
                    'local/freecaminteraction/blueprint/BlueprintPartEntry.class', 'local/freecaminteraction/blueprint/MaterialRequirement.class',
                    'local/freecaminteraction/blueprint/IBlueprintAdapter.class', 'local/freecaminteraction/blueprint/VanillaBlueprintAdapter.class',
                    'local/freecaminteraction/blueprint/BlueprintGregTechSupport.class',
                    'local/freecaminteraction/blueprint/storage/BlueprintStorage.class',
                    'local/freecaminteraction/blueprint/storage/BlueprintTask.class',
                    'local/freecaminteraction/blueprint/storage/BlueprintTaskManager.class',
                    'local/freecaminteraction/blueprint/storage/TaskPermission.class',
                    'local/freecaminteraction/blueprint/storage/TaskStatus.class',
                    'local/freecaminteraction/blueprint/network/BlueprintNetwork.class',
                    'local/freecaminteraction/blueprint/network/PacketCaptureRequest.class',
                    'local/freecaminteraction/blueprint/network/PacketCaptureAck.class',
                    'local/freecaminteraction/blueprint/network/PacketBlueprintListRequest.class',
                    'local/freecaminteraction/blueprint/network/PacketBlueprintListResponse.class',
                    'local/freecaminteraction/blueprint/network/PacketBlueprintSlice.class',
                    'local/freecaminteraction/blueprint/network/PacketTaskAction.class',
                    'local/freecaminteraction/blueprint/network/PacketTaskSync.class',
                    'local/freecaminteraction/blueprint/network/BlueprintTaskManager.class',
                    'local/freecaminteraction/blueprint/network/BlueprintStorageManager.class',
                    'local/freecaminteraction/blueprint/build/BlueprintBuildExecutor.class',
                    'local/freecaminteraction/blueprint/build/BlueprintBuildScheduler.class',
                    'local/freecaminteraction/item/ItemBlueprintCore.class',
                    'local/freecaminteraction/item/IWandCore.class',
                    'local/freecaminteraction/inventory/ContainerWandUpgrade.class',
                    'local/freecaminteraction/client/GuiWandUpgrade.class',
                    'local/freecaminteraction/client/gui/GuiSaveBlueprint.class',
                    'local/freecaminteraction/client/gui/GuiBlueprintManager.class',
                    'local/freecaminteraction/client/renderer/BlueprintGhostRenderer.class',
                    'local/freecaminteraction/FreecamGuiHandler.class',
                    'assets/freecam_interaction/textures/items/blueprint_core.png',
                    'assets/freecam_interaction/textures/gui/wand_upgrade.png',
                    'assets/freecam_interaction/lang/zh_CN.lang')) {
                if (!$zip.GetEntry($name)) { throw "产物缺少 $name" }
            }
            $reader = [System.IO.StreamReader]::new($zip.GetEntry('mcmod.info').Open(), [System.Text.Encoding]::UTF8)
            try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
            if ($metadata.Contains('${') -or !$metadata.Contains('1.7.10') -or !$metadata.Contains('自由视角交互')) { throw '元数据错误' }
            if ($zip.GetEntry('META-INF/neoforge.mods.toml')) { throw '错误地包含 NeoForge 资源' }
            Write-Host "Forge 1.7.10 产物冒烟通过：$jar"
        } finally { $zip.Dispose() }
    }
} catch {
    Write-Host "执行失败：$_"
    exit 1
} finally {
    if ($null -ne $runLock) { $runLock.Dispose() }
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
    $env:GODVIEW_JAVA8_HOME = $previousJava8
    Stop-Transcript | Out-Null
}
