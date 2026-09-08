package local.godviewbuild.client;

import local.godviewbuild.ModLog;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class GodviewScreen extends Screen {
    private Minecraft client;
    private ClientLevel originLevel;
    private LocalPlayer originPlayer;
    private CameraType previousCamera;
    private float yaw;
    private boolean active;

    public GodviewScreen() {
        super(Component.translatable("screen.godview_build.title"));
    }

    @Override
    public void added() {
        client = Minecraft.getInstance();
        originLevel = client.level;
        originPlayer = client.player;
        previousCamera = client.options.getCameraType();
        yaw = originPlayer.getYRot();
        active = true;
        KeyMapping.releaseAll();
        KeyMapping.resetToggleKeys();
        GodviewClient.clearMovement(originPlayer.input);
        originPlayer.setSprinting(false);
        client.gameMode.stopDestroyBlock();
        if (originPlayer.isUsingItem()) {
            client.gameMode.releaseUsingItem(originPlayer);
        }
        setCamera(CameraType.THIRD_PERSON_BACK);
        ModLog.LOGGER.info("Godview entered; dimension={}; position={}; previousCamera={}; yaw={}",
                originLevel.dimension().location(), originPlayer.position(), previousCamera, yaw);
    }

    @Override
    protected void init() {
        int buttonSize = Math.max(1, Math.min(24, Math.min(width, height) - 8));
        addRenderableWidget(Button.builder(Component.literal("X"), button -> close("close_button"))
                .bounds(Math.max(0, width - buttonSize - 8), 4, buttonSize, buttonSize)
                .tooltip(Tooltip.create(Component.translatable("screen.godview_build.close")))
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, Math.min(32, height), 0xD9101C29);
        graphics.fill(0, Math.min(31, height), width, Math.min(32, height), 0xFF61D5C8);
        String heading = font.plainSubstrByWidth(title.getString(), Math.max(0, width - 56));
        graphics.drawString(font, heading, 10, 12, 0xFFE6F8F5);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    boolean isCurrentSession() {
        return active && client.level == originLevel && client.player == originPlayer
                && originPlayer.isAlive() && !originPlayer.isSleeping()
                && client.getCameraEntity() == originPlayer;
    }

    float cameraYaw() {
        return yaw;
    }

    @Override
    public void tick() {
        if (!isCurrentSession()) {
            close("session_changed");
        }
    }

    @Override
    public void onClose() {
        close("escape");
    }

    private void close(String reason) {
        restore(reason);
        if (client.screen == this && client.level != null && client.player != null) {
            client.setScreen(null);
        }
    }

    @Override
    public void removed() {
        restore("screen_replaced");
    }

    void restore(String reason) {
        if (!active) {
            return;
        }
        active = false;
        KeyMapping.releaseAll();
        KeyMapping.resetToggleKeys();
        GodviewClient.clearMovement(originPlayer.input);
        setCamera(previousCamera);
        ModLog.LOGGER.info("Godview exited; reason={}; restoredCamera={}", reason, previousCamera);
        originLevel = null;
        originPlayer = null;
    }

    private void setCamera(CameraType cameraType) {
        client.options.setCameraType(cameraType);
        client.gameRenderer.checkEntityPostEffect(cameraType.isFirstPerson() ? client.getCameraEntity() : null);
        client.levelRenderer.needsUpdate();
    }
}
