param([ValidateSet('native', 'lwjgl3ify', 'all')][string]$Target = 'all')
. "$PSScriptRoot/common.ps1"
Start-ProjectLog

$cacheRoot = Join-Path $env:LOCALAPPDATA 'GodviewBuild/mod-cache/1.7.10'
$mods = @(
    @{ Name = 'industrialcraft-2-2.2.828-experimental.jar'; Url = 'https://edge.forgecdn.net/files/6833/054/industrialcraft-2-2.2.828-experimental.jar'; Sha256 = 'DE1D4597972BE036ECCD1C3B37E9980C3C9D9CDB92F52DF2BF470971873893F6' },
    @{ Name = 'appliedenergistics2-rv3-beta-6.jar'; Url = 'https://edge.forgecdn.net/files/2296/430/appliedenergistics2-rv3-beta-6.jar'; Sha256 = '0EC8CD1EDE7F7BBBF73030EBA8B06EBCC0583045FF4CC9AEC080B1736581DA71' }
)

try {
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    foreach ($mod in $mods) {
        $cached = Join-Path $cacheRoot $mod.Name
        if (!(Test-Path -LiteralPath $cached) -or (Get-FileHash -LiteralPath $cached -Algorithm SHA256).Hash -ne $mod.Sha256) {
            Invoke-WebRequest -Uri $mod.Url -OutFile $cached -TimeoutSec 600
        }
        if ((Get-FileHash -LiteralPath $cached -Algorithm SHA256).Hash -ne $mod.Sha256) { throw "SHA-256 校验失败：$cached" }
    }

    $destinations = @()
    if ($Target -in @('native', 'all')) { $destinations += (Join-Path $ProjectRoot 'run/mods') }
    if ($Target -in @('lwjgl3ify', 'all')) { $destinations += (Join-Path $env:LOCALAPPDATA 'GodviewBuild/lwjgl3ify-3.0.33/instance/mods') }
    foreach ($destination in $destinations) {
        New-Item -ItemType Directory -Force -Path $destination | Out-Null
        foreach ($mod in $mods) {
            Copy-Item -LiteralPath (Join-Path $cacheRoot $mod.Name) -Destination (Join-Path $destination $mod.Name) -Force
        }
        Write-Host "IC2 与 AE2 已安装：$destination"
    }
} catch {
    Write-Host "执行失败：$_"
    exit 1
} finally {
    Stop-Transcript | Out-Null
}
