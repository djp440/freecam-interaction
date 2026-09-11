package local.freecaminteraction.item;

import java.util.List;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * 蓝图升级核心物品类。
 * 安装至自由视角法杖后，解锁蓝图选区录制、任务投建与协作施工权限。
 */
public class ItemBlueprintCore extends Item implements IWandCore {
    public static final String CORE_ID = "blueprint";

    public ItemBlueprintCore() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
        setUnlocalizedName("freecam_interaction.blueprint_core");
        setTextureName("freecam_interaction:blueprint_core");
    }

    @Override
    public String getCoreId() {
        return CORE_ID;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add("\u00A7b" + StatCollector.translateToLocal("item.freecam_interaction.blueprint_core.desc"));
        tooltip.add("\u00A77" + StatCollector.translateToLocal("item.freecam_interaction.blueprint_core.desc2"));
    }
}
