# 法杖材质生成记录

使用内置 imagegen 生成透明原图，scripts/wand-textures.ps1 最近邻采样导出32×32 RGBA PNG，并生成4倍预览。

复用命令：`pwsh -File scripts/wand-textures.ps1 -SourceDirectory "$PWD/art/wands"`

## normal

Create ONE Minecraft inventory item texture: a magic free-camera wand. Normal tier: dark brown wooden handle, black obsidian collar, BLUE sapphire diamond shaped small crystal head. Simple modest silhouette. True 32x32 pixel PNG RGBA transparent background. Pixel art constructed on exactly a 32 by 32 logical pixel grid, hard square pixels, no antialiasing, no blur, no shadow outside silhouette, no gradient, no text, no border, no checkerboard baked in. Single diagonal wand from lower left (4,28) to upper right (25,5), centered fully within canvas with 2 pixel transparent padding. Handle about 3 logical pixels thick, crystal head about 8 pixels wide. Cohesive vanilla Minecraft fantasy item art, dark outline and 3-tone shading, mostly dark neutral material with colored gem as accent. Deliver actual 32x32 resolution if supported; otherwise exact integer nearest-neighbor enlargement of a 32x32 pixel sprite. Save image and return local output path.

## advanced

Create ONE Minecraft inventory item texture: a magic free-camera wand. Advanced tier: dark charcoal handle, restrained antique gold brackets, RED ruby pointed crystal head, two small angular gold prongs. More elaborate than basic wand. True 32x32 pixel PNG RGBA transparent background. Pixel art constructed on exactly a 32 by 32 logical pixel grid, hard square pixels, no antialiasing, no blur, no shadow outside silhouette, no gradient, no text, no border, no checkerboard baked in. Single diagonal wand from lower left (4,28) to upper right (25,5), centered fully within canvas with 2 pixel transparent padding. Handle about 3 logical pixels thick, crystal head about 8 pixels wide. Cohesive vanilla Minecraft fantasy item art, dark outline and 3-tone shading, mostly dark neutral material with colored gem as accent. Deliver actual 32x32 resolution if supported; otherwise exact integer nearest-neighbor enlargement of a 32x32 pixel sprite. Save image and return local output path.

## creative

Create ONE Minecraft inventory item texture: a magic free-camera wand. Creative tier: dark obsidian handle with silver bands, PURPLE amethyst star-shaped crystal head inside two silver angular crescent prongs. Elegant highest tier. True 32x32 pixel PNG RGBA transparent background. Pixel art constructed on exactly a 32 by 32 logical pixel grid, hard square pixels, no antialiasing, no blur, no shadow outside silhouette, no gradient, no text, no border, no checkerboard baked in. Single diagonal wand from lower left (4,28) to upper right (25,5), centered fully within canvas with 2 pixel transparent padding. Handle about 3 logical pixels thick, crystal head about 8 pixels wide. Cohesive vanilla Minecraft fantasy item art, dark outline and 3-tone shading, mostly dark neutral material with colored gem as accent. Deliver actual 32x32 resolution if supported; otherwise exact integer nearest-neighbor enlargement of a 32x32 pixel sprite. Save image and return local output path.
