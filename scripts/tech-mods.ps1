param([ValidateSet('native', 'lwjgl3ify', 'all')][string]$Target = 'all')
. "$PSScriptRoot/common.ps1"
Start-ProjectLog

$cacheRoot = Join-Path $env:LOCALAPPDATA 'GodviewBuild/mod-cache/1.7.10'
$mods = @(
    @{ Name = 'industrialcraft-2-2.2.828-experimental.jar'; Url = 'https://edge.forgecdn.net/files/6833/054/industrialcraft-2-2.2.828-experimental.jar'; Sha256 = 'DE1D4597972BE036ECCD1C3B37E9980C3C9D9CDB92F52DF2BF470971873893F6' },
    @{ Name = 'appliedenergistics2-rv3-beta-6.jar'; Url = 'https://edge.forgecdn.net/files/2296/430/appliedenergistics2-rv3-beta-6.jar'; Sha256 = '0EC8CD1EDE7F7BBBF73030EBA8B06EBCC0583045FF4CC9AEC080B1736581DA71' },
    @{ Name = 'CodeChickenCore-1.7.10-1.0.7.47-universal.jar'; Url = 'https://edge.forgecdn.net/files/2262/089/CodeChickenCore-1.7.10-1.0.7.47-universal.jar'; Sha256 = '3D1527C54E84DC8AE2F7D1109E646420E82BD3B09A811641F1F9A810C46C5F93' },
    @{ Name = 'NotEnoughItems-1.7.10-1.0.5.120-universal.jar'; Url = 'https://edge.forgecdn.net/files/2302/312/NotEnoughItems-1.7.10-1.0.5.120-universal.jar'; Sha256 = '3EBBC2F82B61812AA158375005A47DA4D450BEC870860FCBF015A64DE74CDE1C' },
    @{ Name = 'gregtech-5.09.31.jar'; Url = 'https://edge.forgecdn.net/files/2479/882/gregtech-5.09.31.jar'; Sha256 = '4BDC832550F5E8C60C59A3E3CB7EFDA5732F65BD57195FBDC885AD00A9614A18' },
    @{ Name = 'CodeChickenLib-1.7.10-1.1.3.138-universal.jar'; SubDir = '1.7.10'; Url = 'https://maven.covers1624.net/codechicken/CodeChickenLib/1.7.10-1.1.3.138/CodeChickenLib-1.7.10-1.1.3.138-universal.jar'; Sha256 = '4A0D192A34E7EF3E7AF039F64DC6426BD0A5A343AAE4DD1107B2043A616A77F7' }
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
            $destDir = if ($mod.ContainsKey('SubDir')) { Join-Path $destination $mod.SubDir } else { $destination }
            New-Item -ItemType Directory -Force -Path $destDir | Out-Null
            Copy-Item -LiteralPath (Join-Path $cacheRoot $mod.Name) -Destination (Join-Path $destDir $mod.Name) -Force
        }
        Write-Host "Mod 组件已同步：$destination"
    }
} catch {
    Write-Host "执行失败：$_"
    exit 1
} finally {
    Stop-Transcript | Out-Null
}
