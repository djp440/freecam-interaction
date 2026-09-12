package local.freecaminteraction.client.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import local.freecaminteraction.ae2.Ae2Integration;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

/** 多绑定显式选择页；服务端会再验证 UUID 和可用性。 */
public final class GuiAe2TargetSelector extends GuiScreen {
    private final List<Target> targets = new ArrayList<Target>();

    public GuiAe2TargetSelector(EntityPlayer player) {
        Set<String> seen = new HashSet<String>();
        for (int slot = 0; slot < Math.min(36, player.inventory.mainInventory.length); slot++) {
            ItemStack wand = player.inventory.mainInventory[slot];
            if (!Ae2Integration.hasCore(wand) || wand == null || !wand.hasTagCompound()
                    || !wand.getTagCompound().hasKey("Ae2Transfer", 10)) continue;
            NBTTagCompound tag = wand.getTagCompound().getCompoundTag("Ae2Transfer");
            String id = tag.getString("Id");
            if (!id.isEmpty() && seen.add(id)) targets.add(new Target(id, tag.getInteger("Dim"),
                    tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z")));
        }
    }

    @Override public void initGui() {
        buttonList.clear();
        int y = 52;
        for (int i = 0; i < targets.size() && i < 8; i++) {
            Target t = targets.get(i);
            buttonList.add(new GuiButton(100 + i, width / 2 - 120, y, 240, 20,
                    "DIM " + t.dim + "  " + t.x + ", " + t.y + ", " + t.z));
            y += 24;
        }
    }

    @Override protected void actionPerformed(GuiButton button) {
        int index = button.id - 100;
        if (index >= 0 && index < targets.size()) Ae2Integration.selectTarget(targets.get(index).id);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, StatCollector.translateToLocal("gui.freecam_interaction.ae2.select"), width / 2, 28, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    private static final class Target {
        final String id; final int dim, x, y, z;
        Target(String id, int dim, int x, int y, int z) { this.id = id; this.dim = dim; this.x = x; this.y = y; this.z = z; }
    }
}
