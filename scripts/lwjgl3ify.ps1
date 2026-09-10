param([ValidateSet('setup', 'client', 'smoke')][string]$Action = 'smoke')
. "$PSScriptRoot/common.ps1"
Start-ProjectLog
$version = '3.0.33'
$root = Join-Path $env:LOCALAPPDATA "GodviewBuild/lwjgl3ify-$version"
$bundle = Join-Path $root "lwjgl3ify-$version-multimc.zip"
$instance = Join-Path $root 'instance'
$java = Join-Path $env:LOCALAPPDATA 'GodviewBuild/jdk-25.0.4.1+1/bin/java.exe'
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
$previousJava8 = $env:GODVIEW_JAVA8_HOME

function Get-CheckedFile([string]$Url, [string]$Path, [string]$Sha256) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    if (!(Test-Path -LiteralPath $Path) -or (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne $Sha256) {
        Invoke-WebRequest $Url -OutFile $Path -TimeoutSec 600
    }
    if ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne $Sha256) { throw "SHA-256 校验失败：$Path" }
}

function Rule-Applies($rule) {
    if (!$rule.PSObject.Properties['os']) { return !$rule.PSObject.Properties['features'] }
    if ($rule.os.PSObject.Properties['name'] -and $rule.os.name -ne 'windows') { return $false }
    if ($rule.os.PSObject.Properties['arch'] -and 'amd64' -notmatch $rule.os.arch) { return $false }
    if ($rule.os.PSObject.Properties['version'] -and [Environment]::OSVersion.VersionString -notmatch $rule.os.version) { return $false }
    return $true
}

function Library-Allowed($library) {
    if (!$library.PSObject.Properties['rules']) { return $true }
    $allowed = $false
    foreach ($rule in $library.rules) { if (Rule-Applies $rule) { $allowed = $rule.action -eq 'allow' } }
    return $allowed
}

function Get-Library([hashtable]$libraries, $artifact, [string]$key) {
    $path = Join-Path $root "libraries/$($artifact.sha1).jar"
    if (!(Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash -ne $artifact.sha1.ToUpperInvariant()) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
        Invoke-WebRequest $artifact.url -OutFile $path -TimeoutSec 600
    }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash -ne $artifact.sha1.ToUpperInvariant()) { throw "库 SHA-1 校验失败：$($artifact.url)" }
    $libraries[$key] = $path
}

function Prepare-Instance {
    if (!(Test-Path -LiteralPath $java)) { throw '缺少 Java 25，请先执行 scripts/forge1710.ps1 setup' }
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java)
    $env:PATH = "$env:JAVA_HOME/bin;$previousPath"
    $env:GODVIEW_JAVA8_HOME = Join-Path $env:LOCALAPPDATA 'GodviewBuild/jdk8u504-b01'
    Get-CheckedFile "https://github.com/GTNewHorizons/lwjgl3ify/releases/download/$version/lwjgl3ify-$version-multimc.zip" $bundle '7A71E04F0DD2DF84816CA6D7C79625F7F5A8B4EC8E6C90CC3F2865AC591279BD'
    $patches = Join-Path $root 'patches'
    if (!(Test-Path -LiteralPath "$patches/org.lwjgl3.json")) { Expand-Archive -LiteralPath $bundle -DestinationPath $root -Force }
    $libraries = @{}
    foreach ($file in @('net.minecraft.json', 'org.lwjgl3.json', 'net.minecraftforge.json')) {
        $component = Get-Content -LiteralPath (Join-Path $patches $file) -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($library in $component.libraries) {
            if (!(Library-Allowed $library)) { continue }
            $parts = $library.name -split ':'
            $key = "$($parts[0]):$($parts[1])" + $(if ($parts.Count -gt 3) { ":$($parts[3])" } else { '' })
            if ($library.downloads.PSObject.Properties['artifact']) { Get-Library $libraries $library.downloads.artifact $key }
            if ($library.PSObject.Properties['natives'] -and $library.natives.PSObject.Properties['windows']) {
                $classifier = $library.natives.windows.Replace('${arch}', '64')
                $nativeProperty = $library.downloads.classifiers.PSObject.Properties[$classifier]
                $native = if ($nativeProperty) { $nativeProperty.Value } else { $null }
                if ($native) {
                    Get-Library $libraries $native "native:$key"
                    $nativePath = $libraries["native:$key"]
                    $nativeDirectory = Join-Path $instance 'natives'
                    New-Item -ItemType Directory -Force -Path $nativeDirectory | Out-Null
                    try {
                        [IO.Compression.ZipFile]::ExtractToDirectory($nativePath, $nativeDirectory, $true)
                    } catch {
                        if (!(Test-Path -LiteralPath $nativeDirectory) -or (Get-ChildItem -LiteralPath $nativeDirectory).Count -eq 0) {
                            throw $_
                        }
                    }
                    $libraries.Remove("native:$key")
                }
            }
        }
        if ($component.PSObject.Properties['mainJar']) { Get-Library $libraries $component.mainJar.downloads.artifact 'minecraft:client' }
    }
    $forgePatches = Join-Path $root "libraries/lwjgl3ify-$version-forgePatches.jar"
    if (!(Test-Path -LiteralPath $forgePatches)) { throw '发行包缺少 forgePatches' }
    New-Item -ItemType Directory -Force -Path "$instance/mods", "$instance/logs", "$instance/config" | Out-Null
    Get-CheckedFile "https://github.com/GTNewHorizons/lwjgl3ify/releases/download/$version/lwjgl3ify-$version.jar" "$instance/mods/lwjgl3ify-$version.jar" 'E6763321E86D2F82685ADBE33F02FE9A090029D39B9AC1F0CE6928477CDBD673'
    Get-CheckedFile 'https://github.com/LegacyModdingMC/UniMixins/releases/download/0.3.1/%2Bunimixins-all-1.7.10-0.3.1.jar' "$instance/mods/+unimixins-all-1.7.10-0.3.1.jar" 'AD0EA4F92DAF7BF7EC5C10E16258425E47929126954AEA5D80DF69CE26CD7318'
    Get-CheckedFile 'https://nexus.gtnewhorizons.com/repository/releases/com/github/GTNewHorizons/ironchest/6.1.13/ironchest-6.1.13.jar' "$instance/mods/ironchest-6.1.13.jar" 'EAADB2B27192D1EC086C59BDE8C216A7AABD967F7B84CA2D389B05989C096735'
    $buildOutput = & "$ProjectRoot/gradlew.bat" build --console=plain 2>&1
    $buildOutput | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw 'Mod 构建失败' }
    Copy-Item -LiteralPath "$ProjectRoot/build/libs/freecam_interaction-0.1.0-forge1710-experiment.jar" -Destination "$instance/mods/freecam_interaction.jar" -Force
    $selected = Join-Path $root 'classpath'
    New-Item -ItemType Directory -Force -Path $selected | Out-Null
    Copy-Item -LiteralPath $forgePatches -Destination "$selected/000-forgePatches.jar" -Force
    foreach ($path in $libraries.Values) { Copy-Item -LiteralPath $path -Destination (Join-Path $selected (Split-Path -Leaf $path)) -Force }
    return "$selected/*"
}

Push-Location -LiteralPath $ProjectRoot
try {
    $classpath = Prepare-Instance
    if ($Action -eq 'setup') { Write-Host "lwjgl3ify $version 实例已准备：$instance"; exit 0 }
    if (!(Test-Path -LiteralPath "$env:USERPROFILE/.gradle/caches/retro_futura_gradle/assets/indexes/1.7.10.json")) { throw '缺少 1.7.10 assets，请先运行原生客户端 setup' }
    if ($Action -eq 'smoke') {
        & $java -version
        if ($LASTEXITCODE -ne 0) { throw 'Java 25 无法运行' }
        Write-Host "lwjgl3ify $version 冒烟准备通过；第三方容器：Iron Chests 6.1.13"
        exit 0
    }
    $launch = Join-Path $root 'client.args'
    $jvm = (Get-Content -LiteralPath "$root/patches/me.eigenraven.lwjgl3ify.forgepatches.json" -Raw -Encoding UTF8 | ConvertFrom-Json).'+jvmArgs'
    $args = @('-Xms512m', '-Xmx2g', '-Dfreecam.lwjgl3ify=true', '-Dgodview.lwjgl3ify=true', "-Djava.library.path=$(Join-Path $instance 'natives')") + @($jvm)
    $args += @('-cp', [string]$classpath, 'com.gtnewhorizons.retrofuturabootstrap.MainStartOnFirstThread')
    $args += @('--username', 'FreecamLwjgl3', '--version', '1.7.10-lwjgl3ify', '--gameDir', $instance,
        '--assetsDir', "$env:USERPROFILE/.gradle/caches/retro_futura_gradle/assets", '--assetIndex', '1.7.10',
        '--uuid', '4bd03cd7-4f82-3bb4-b64f-89847d806b23', '--accessToken', '0', '--userProperties', '{}', '--userType', 'legacy',
        '--tweakClass', 'cpw.mods.fml.common.launcher.FMLTweaker')
    [IO.File]::WriteAllLines($launch, [string[]]$args, [Text.UTF8Encoding]::new($false))
    & $java "@$launch"
    if ($LASTEXITCODE -ne 0) { throw "lwjgl3ify 客户端退出：$LASTEXITCODE" }
} catch {
    Write-Host "执行失败：$_"
    exit 1
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
    $env:GODVIEW_JAVA8_HOME = $previousJava8
    Stop-Transcript | Out-Null
}
