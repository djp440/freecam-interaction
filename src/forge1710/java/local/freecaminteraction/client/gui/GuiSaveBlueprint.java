package local.freecaminteraction.client.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import local.freecaminteraction.blueprint.network.BlueprintNetwork;
import local.freecaminteraction.blueprint.network.PacketCaptureRequest;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * 蓝图保存名称输入对话框。
 * 选区两点确定后弹出，输入名称后发送 PacketCaptureRequest。
 */
@SideOnly(Side.CLIENT)
public class GuiSaveBlueprint extends GuiScreen {
    private final int x1, y1, z1;
    private final int x2, y2, z2;
    private GuiTextField nameField;
    private GuiButton btnSave;
    private GuiButton btnCancel;
    private String errorMsg = "";

    public GuiSaveBlueprint(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.nameField = new GuiTextField(this.fontRendererObj, centerX - 100, centerY - 20, 200, 20);
        this.nameField.setMaxStringLength(48);
        this.nameField.setFocused(true);
        this.nameField.setText("Blueprint_" + (System.currentTimeMillis() % 10000));

        this.btnSave = new GuiButton(1, centerX - 100, centerY + 15, 95, 20, I18n.format("gui.freecam_interaction.bp.save"));
        this.btnCancel = new GuiButton(2, centerX + 5, centerY + 15, 95, 20, I18n.format("gui.freecam_interaction.bp.cancel"));

        this.buttonList.add(this.btnSave);
        this.buttonList.add(this.btnCancel);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        // 不暂停单机集成服务器，否则发包排队到关界面才执行
        return false;
    }

    @Override
    public void updateScreen() {
        if (this.nameField != null) {
            this.nameField.updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.nameField != null && this.nameField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            actionPerformed(this.btnSave);
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            actionPerformed(this.btnCancel);
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.nameField != null) {
            this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id == 1) {
            // 保存
            String name = (this.nameField != null) ? this.nameField.getText().trim() : "";
            if (name.isEmpty()) {
                this.errorMsg = I18n.format("gui.freecam_interaction.bp.name_empty");
                return;
            }
            BlueprintNetwork.sendToServer(new PacketCaptureRequest(x1, y1, z1, x2, y2, z2, name));
            this.mc.displayGuiScreen(null);
        } else if (button.id == 2) {
            // 取消
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        String title = I18n.format("gui.freecam_interaction.bp.save_title");
        drawCenteredString(this.fontRendererObj, title, centerX, centerY - 50, 0xFFFFFF);

        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
        String info = I18n.format("gui.freecam_interaction.bp.size_info", sx, sy, sz, (sx * sy * sz));
        drawCenteredString(this.fontRendererObj, info, centerX, centerY - 35, 0xAAAAAA);

        if (this.nameField != null) {
            this.nameField.drawTextBox();
        }

        if (this.errorMsg != null && !this.errorMsg.isEmpty()) {
            drawCenteredString(this.fontRendererObj, this.errorMsg, centerX, centerY + 40, 0xFF5555);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
