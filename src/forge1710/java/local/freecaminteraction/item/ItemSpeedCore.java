package local.freecaminteraction.item;

import java.util.List;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/** 法杖移速升级核心；两档共享 coreId，因此同一法杖只能安装一个。 */
public final class ItemSpeedCore extends Item implements IWandCore {
    public static final String CORE_ID = "speed";
    public final int cameraMultiplier;
    public final double playerMultiplier;

    public ItemSpeedCore(int cameraMultiplier, double playerMultiplier) {
        this.cameraMultiplier = cameraMultiplier;
        this.playerMultiplier = playerMultiplier;
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
        setUnlocalizedName("freecam_interaction.speed_core_" + cameraMultiplier + "x");
        setTextureName("freecam_interaction:speed_core_" + cameraMultiplier + "x");
    }

    @Override
    public String getCoreId() {
        return CORE_ID;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        String key = "item.freecam_interaction.speed_core_" + cameraMultiplier + "x";
        tooltip.add("\u00A7b" + StatCollector.translateToLocal(key + ".desc"));
        tooltip.add("\u00A77" + StatCollector.translateToLocal(key + ".desc2"));
        tooltip.add("\u00A77" + StatCollector.translateToLocal("item.freecam_interaction.speed_core.rule"));
    }
}
