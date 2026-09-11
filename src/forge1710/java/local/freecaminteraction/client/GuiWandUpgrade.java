package local.freecaminteraction.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import local.freecaminteraction.inventory.ContainerWandUpgrade;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

/**
 * 法杖核心升级客户端界面。
 */
@SideOnly(Side.CLIENT)
public class GuiWandUpgrade extends GuiContainer {
    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation("freecam_interaction", "textures/gui/wand_upgrade.png");

    public GuiWandUpgrade(InventoryPlayer playerInventory, int wandSlot) {
        super(new ContainerWandUpgrade(playerInventory, wandSlot));
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = StatCollector.translateToLocal("gui.freecam_interaction.wand_upgrade.title");
        this.fontRendererObj.drawString(title, this.xSize / 2 - this.fontRendererObj.getStringWidth(title) / 2, 6, 0x404040);
        String inv = StatCollector.translateToLocal("gui.freecam_interaction.wand_upgrade.inventory");
        this.fontRendererObj.drawString(inv, 8, this.ySize - 96 + 2, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(GUI_TEXTURE);
        int k = (this.width - this.xSize) / 2;
        int l = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(k, l, 0, 0, this.xSize, this.ySize);
    }
}
