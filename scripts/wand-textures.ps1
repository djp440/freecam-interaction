param([Parameter(Mandatory=$true)][string]$SourceDirectory)
. "$PSScriptRoot/common.ps1"
Start-ProjectLog
Add-Type -AssemblyName System.Drawing
try {
    $target = Join-Path $ProjectRoot 'src/forge1710/resources/assets/freecam_interaction/textures/items'
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    $preview = [System.Drawing.Bitmap]::new(384, 128)
    try {
        $index = 0
        foreach ($tier in @('normal', 'advanced', 'creative')) {
            $source = [System.Drawing.Bitmap]::new((Join-Path $SourceDirectory "wand_$tier.png"))
            $texture = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            try {
                $transparent = 0
                $visible = 0
                for ($y = 0; $y -lt 32; $y++) {
                    for ($x = 0; $x -lt 32; $x++) {
                        $color = $source.GetPixel([int][Math]::Floor(($x + 0.5) * $source.Width / 32), [int][Math]::Floor(($y + 0.5) * $source.Height / 32))
                        $texture.SetPixel($x, $y, $color)
                        if ($color.A -eq 0) { $transparent++ } else { $visible++ }
                        for ($dy = 0; $dy -lt 4; $dy++) {
                            for ($dx = 0; $dx -lt 4; $dx++) { $preview.SetPixel($index * 128 + $x * 4 + $dx, $y * 4 + $dy, $color) }
                        }
                    }
                }
                if ($transparent -eq 0 -or $visible -eq 0) { throw "$tier 缺少透明背景或可见像素" }
                $path = Join-Path $target "wand_$tier.png"
                $texture.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
                Write-Host "$tier : 32x32 RGBA，透明像素=$transparent，可见像素=$visible，路径=$path"
            } finally { $source.Dispose(); $texture.Dispose() }
            $index++
        }
        $preview.Save((Join-Path $SourceDirectory 'preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $preview.Dispose() }
} finally { Stop-Transcript | Out-Null }
