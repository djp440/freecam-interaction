package local.freecaminteraction;

import cpw.mods.fml.common.registry.GameRegistry;
import local.freecaminteraction.item.ItemFreecamWand;
import local.freecaminteraction.WandTier;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public final class FreecamWandRegistry {
    public static ItemFreecamWand wandNormal;
    public static ItemFreecamWand wandAdvanced;
    public static ItemFreecamWand wandCreative;

    private FreecamWandRegistry() {}

    public static void initialize() {
        wandNormal = new ItemFreecamWand(WandTier.NORMAL);
        wandAdvanced = new ItemFreecamWand(WandTier.ADVANCED);
        wandCreative = new ItemFreecamWand(WandTier.CREATIVE);

        GameRegistry.registerItem(wandNormal, "wand_freecam");
        GameRegistry.registerItem(wandAdvanced, "wand_freecam_advanced");
        GameRegistry.registerItem(wandCreative, "wand_freecam_creative");

        // 普通自由视角法杖合成配方
        GameRegistry.addShapedRecipe(new ItemStack(wandNormal),
                " D ",
                "OSO",
                " S ",
                'D', Items.diamond,
                'O', Blocks.obsidian,
                'S', Items.stick);

        // 高级自由视角法杖合成配方
        GameRegistry.addShapedRecipe(new ItemStack(wandAdvanced),
                " N ",
                "GBG",
                " B ",
                'N', Items.nether_star,
                'G', Blocks.gold_block,
                'B', Blocks.diamond_block);

        ModLog.info("Wand items and recipes registered");
    }
}
