package local.freecaminteraction;

import cpw.mods.fml.common.network.IGuiHandler;
import local.freecaminteraction.inventory.ContainerWandUpgrade;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * 自由视角 GUI 路由处理器。
 * 分端加载 Container（两侧）与 GuiContainer（仅客户端），确保服务端无类加载异常。
 */
public class FreecamGuiHandler implements IGuiHandler {
    public static final int GUI_WAND_UPGRADE = 1;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_WAND_UPGRADE) {
            return new ContainerWandUpgrade(player.inventory, x);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_WAND_UPGRADE) {
            return local.freecaminteraction.client.FreecamClient.getWandUpgradeGui(player, x);
        }
        return null;
    }
}
