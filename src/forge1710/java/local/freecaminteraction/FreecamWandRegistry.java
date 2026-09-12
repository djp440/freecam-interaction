package local.freecaminteraction;

import cpw.mods.fml.common.registry.GameRegistry;
import local.freecaminteraction.item.ItemFreecamWand;
import local.freecaminteraction.item.ItemSpeedCore;
import local.freecaminteraction.WandTier;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public final class FreecamWandRegistry {
    public static ItemFreecamWand wandNormal;
    public static ItemFreecamWand wandAdvanced;
    public static ItemFreecamWand wandCreative;
    public static local.freecaminteraction.item.ItemBlueprintCore blueprintCore;
    public static ItemSpeedCore speedCore2x;
    public static ItemSpeedCore speedCore4x;
    public static final int GUI_WAND_UPGRADE = FreecamGuiHandler.GUI_WAND_UPGRADE;

    private FreecamWandRegistry() {}

    public static void initialize() {
        wandNormal = new ItemFreecamWand(WandTier.NORMAL);
        wandAdvanced = new ItemFreecamWand(WandTier.ADVANCED);
        wandCreative = new ItemFreecamWand(WandTier.CREATIVE);
        blueprintCore = new local.freecaminteraction.item.ItemBlueprintCore();
        speedCore2x = new ItemSpeedCore(2, 1.25D);
        speedCore4x = new ItemSpeedCore(4, 1.5D);

        GameRegistry.registerItem(wandNormal, "wand_freecam");
        GameRegistry.registerItem(wandAdvanced, "wand_freecam_advanced");
        GameRegistry.registerItem(wandCreative, "wand_freecam_creative");
        GameRegistry.registerItem(blueprintCore, "blueprint_core");
        GameRegistry.registerItem(speedCore2x, "speed_core_2x");
        GameRegistry.registerItem(speedCore4x, "speed_core_4x");

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

        // 蓝图核心合成配方：四角纸、四边红石、中心钻石
        GameRegistry.addShapedRecipe(new ItemStack(blueprintCore),
                "PRP",
                "RDR",
                "PRP",
                'P', Items.paper,
                'R', Items.redstone,
                'D', Items.diamond);

        GameRegistry.addShapelessRecipe(new ItemStack(speedCore2x), Items.clock);
        GameRegistry.addShapelessRecipe(new ItemStack(speedCore4x),
                speedCore2x, speedCore2x, new ItemStack(Blocks.quartz_block, 1, 0));

        ModLog.info("Wand items and recipes registered");
    }
}
