package local.freecaminteraction.item;

import java.util.List;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/** 法杖专用的 AE2 网络传输核心。 */
public final class ItemAe2TransferCore extends Item implements IWandCore {
    public ItemAe2TransferCore() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
        setUnlocalizedName("freecam_interaction.ae2_transfer_core");
        setTextureName("freecam_interaction:ae2_transfer_core");
    }

    @Override public String getCoreId() { return "ae2_transfer"; }

    @Override @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add("\u00A7b" + StatCollector.translateToLocal("item.freecam_interaction.ae2_transfer_core.desc"));
        tooltip.add("\u00A77" + StatCollector.translateToLocal("item.freecam_interaction.ae2_transfer_core.desc2"));
    }
}
