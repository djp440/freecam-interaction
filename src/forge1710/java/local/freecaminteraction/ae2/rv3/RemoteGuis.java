package local.freecaminteraction.ae2.rv3;

import java.util.List;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.implementations.GuiCraftingTerm;
import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.parts.reporting.PartInterfaceTerminal;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import local.freecaminteraction.ae2.Ae2Integration;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

/** 在 AE2 原生 GUI 上只叠加跨页导航，不改其容器和协议。 */
@SideOnly(Side.CLIENT)
final class RemoteGuis {
    private static final int FIRST = 8700;
    private RemoteGuis() {}

    @SuppressWarnings("unchecked")
    private static void add(List buttons, int guiLeft, int guiTop, int current) {
        String[] labels = { "ME", "合", "样", "接", "包", "网" };
        int x = guiLeft - (current == 4 ? 20 : 40);
        for (int page = 0; page < labels.length; page++) {
            GuiButton button = new GuiButton(FIRST + page, x, guiTop + 4 + page * 20, 18, 18, labels[page]);
            button.enabled = page != current;
            buttons.add(button);
        }
    }

    private static boolean navigate(GuiButton button) {
        if (button.id < FIRST || button.id >= FIRST + 6) return false;
        Ae2Integration.requestTerminal(button.id - FIRST);
        return true;
    }

    static final class Items extends GuiMEMonitorable {
        Items(InventoryPlayer inventory, ITerminalHost host) { super(inventory, host); }
        @Override public void initGui() { super.initGui(); add(buttonList, guiLeft, guiTop, 0); }
        @Override protected void actionPerformed(GuiButton button) { if (!navigate(button)) super.actionPerformed(button); }
    }
    static final class Crafting extends GuiCraftingTerm {
        Crafting(InventoryPlayer inventory, ITerminalHost host) { super(inventory, host); }
        @Override public void initGui() { super.initGui(); add(buttonList, guiLeft, guiTop, 1); }
        @Override protected void actionPerformed(GuiButton button) { if (!navigate(button)) super.actionPerformed(button); }
    }
    static final class Pattern extends GuiPatternTerm {
        Pattern(InventoryPlayer inventory, ITerminalHost host) { super(inventory, host); }
        @Override public void initGui() { super.initGui(); add(buttonList, guiLeft, guiTop, 2); }
        @Override protected void actionPerformed(GuiButton button) { if (!navigate(button)) super.actionPerformed(button); }
    }
    static final class Interface extends GuiInterfaceTerminal {
        Interface(InventoryPlayer inventory, PartInterfaceTerminal host) { super(inventory, host); }
        @Override public void initGui() { super.initGui(); add(buttonList, guiLeft, guiTop, 3); }
        @Override protected void actionPerformed(GuiButton button) { if (!navigate(button)) super.actionPerformed(button); }
    }
    static final class Inventory extends GuiInventory {
        Inventory(EntityPlayer player) { super(player); }
        @Override public void initGui() { super.initGui(); add(buttonList, guiLeft, guiTop, 4); }
        @Override protected void actionPerformed(GuiButton button) { if (!navigate(button)) super.actionPerformed(button); }
    }
}
