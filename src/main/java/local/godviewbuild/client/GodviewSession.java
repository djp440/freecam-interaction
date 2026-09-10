package local.godviewbuild.client;

import com.mojang.blaze3d.platform.InputConstants;
import local.godviewbuild.ModLog;
import local.godviewbuild.GodviewInteraction;
import local.godviewbuild.GodviewRange;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.glfw.GLFW;

final class GodviewSession {
    private final Minecraft client = Minecraft.getInstance();
    private final ClientLevel originLevel = client.level;
    private final LocalPlayer originPlayer = client.player;
    private final CameraType previousCamera = client.options.getCameraType();
    Vec3 anchor = originPlayer.getEyePosition();
    float yaw = originPlayer.getYRot();
    float pitch = GodviewClient.PITCH;
    boolean dragging;
    private final boolean supported = client.getConnection().hasChannel(GodviewInteraction.Mode.TYPE);
    private double mouseX;
    private double mouseY;
    private long lastFrame = System.nanoTime();

    GodviewSession() {
        KeyMapping.releaseAll();
        KeyMapping.resetToggleKeys();
        GodviewClient.clearMovement(originPlayer.input);
        originPlayer.setSprinting(false);
        client.gameMode.stopDestroyBlock();
        if (originPlayer.isUsingItem()) {
            client.gameMode.releaseUsingItem(originPlayer);
        }
        setCamera(CameraType.THIRD_PERSON_BACK);
        client.mouseHandler.releaseMouse();
        if (supported) {
            GodviewInteraction.setActive(originPlayer, true);
            PacketDistributor.sendToServer(new GodviewInteraction.Mode(true));
        } else {
            originPlayer.displayClientMessage(Component.translatable("screen.godview_build.unsupported"), false);
        }
        GodviewSelection.reset();
        ModLog.LOGGER.info("Godview entered; dimension={}; anchor={}; previousCamera={}",
                originLevel.dimension().location(), anchor, previousCamera);
    }

    boolean isCurrentSession() {
        return client.level == originLevel && client.player == originPlayer && originPlayer.isAlive()
                && !originPlayer.isSleeping() && client.getCameraEntity() == originPlayer;
    }

    boolean canControl() {
        return isCurrentSession() && client.screen == null && client.getOverlay() == null
                && client.isWindowActive();
    }

    void update() {
        long now = System.nanoTime();
        double elapsed = Math.min((now - lastFrame) / 1_000_000_000.0, 0.05);
        lastFrame = now;
        if (client.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            setCamera(CameraType.THIRD_PERSON_BACK);
        }
        if (!canControl()) {
            drag(false);
            GodviewSelection.stopInput();
            return;
        }
        if (client.mouseHandler.isMouseGrabbed()) {
            client.mouseHandler.releaseMouse();
        }
        double currentX = client.mouseHandler.xpos();
        double currentY = client.mouseHandler.ypos();
        if (dragging) {
            if (GLFW.glfwGetMouseButton(client.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                    != GLFW.GLFW_PRESS) {
                drag(false);
            } else {
                float targetYaw = Mth.wrapDegrees(yaw + (float) ((currentX - mouseX) * 0.2));
                float targetPitch = Mth.clamp(pitch + (float) ((currentY - mouseY) * 0.2), -85.0F, 85.0F);
                float[] safeRot = GodviewCollision.solveRotation(client.level, anchor.x, anchor.y, anchor.z,
                        yaw, pitch, targetYaw, targetPitch);
                yaw = safeRot[0];
                pitch = safeRot[1];
            }
        }
        mouseX = currentX;
        mouseY = currentY;
        double forward = (down(client.options.keyUp) ? 1 : 0) - (down(client.options.keyDown) ? 1 : 0);
        double right = (down(client.options.keyRight) ? 1 : 0) - (down(client.options.keyLeft) ? 1 : 0);
        double up = (down(client.options.keyJump) || isKeyDown(GLFW.GLFW_KEY_SPACE) ? 1 : 0)
                - (down(client.options.keySprint) || isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL) ? 1 : 0);
        if (forward != 0 || right != 0 || up != 0) {
            var offset = GodviewMotion.pan(yaw, forward, right, elapsed);
            double yOffset = GodviewMotion.vertical(up, elapsed);
            double targetX = GodviewRange.clampCamera(originPlayer.getX(), anchor.x + offset.x());
            double targetY = GodviewRange.clampCamera(originPlayer.getY(), anchor.y + yOffset);
            double targetZ = GodviewRange.clampCamera(originPlayer.getZ(), anchor.z + offset.z());

            Vec3 currentCam = GodviewCollision.cameraPos(anchor.x, anchor.y, anchor.z, yaw, pitch);
            double deltaX = targetX - anchor.x;
            double deltaY = targetY - anchor.y;
            double deltaZ = targetZ - anchor.z;

            Vec3 accepted = GodviewCollision.solveTranslation(client.level, currentCam.x, currentCam.y, currentCam.z,
                    deltaX, deltaY, deltaZ);
            double nextX = GodviewRange.clampCamera(originPlayer.getX(), anchor.x + accepted.x);
            double nextY = GodviewRange.clampCamera(originPlayer.getY(), anchor.y + accepted.y);
            double nextZ = GodviewRange.clampCamera(originPlayer.getZ(), anchor.z + accepted.z);
            anchor = new Vec3(nextX, nextY, nextZ);
        }
    }

    boolean isKeyDown(int key) {
        return key >= 0 && InputConstants.isKeyDown(client.getWindow().getWindow(), key);
    }

    boolean down(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(client.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0
                && InputConstants.isKeyDown(client.getWindow().getWindow(), key.getValue());
    }

    void drag(boolean value) {
        if (dragging != value) {
            ModLog.LOGGER.info("Godview drag={}; anchor={}; yaw={}; pitch={}", value, anchor, yaw, pitch);
        }
        dragging = value;
        mouseX = client.mouseHandler.xpos();
        mouseY = client.mouseHandler.ypos();
    }

    boolean overClose() {
        int width = client.getWindow().getGuiScaledWidth();
        double scaledX = client.mouseHandler.xpos() * width / client.getWindow().getScreenWidth();
        double scaledY = client.mouseHandler.ypos() * client.getWindow().getGuiScaledHeight()
                / client.getWindow().getScreenHeight();
        return scaledX >= width - 30 && scaledX < width - 6 && scaledY >= 6 && scaledY < 30
                && !client.options.hideGui;
    }

    void render(GuiGraphics graphics, KeyMapping toggle) {
        if (client.options.hideGui || client.screen != null) {
            return;
        }
        int width = graphics.guiWidth();
        Component help = Component.translatable("screen.godview_build.help",
                client.options.keyUp.getTranslatedKeyMessage(), client.options.keyLeft.getTranslatedKeyMessage(),
                client.options.keyDown.getTranslatedKeyMessage(), client.options.keyRight.getTranslatedKeyMessage(),
                client.options.keyInventory.getTranslatedKeyMessage(), toggle.getTranslatedKeyMessage());
        var lines = client.font.split(help, Math.max(1, width - 52));
        int count = Math.min(lines.size(), Math.max(1, (graphics.guiHeight() - 60) / 10));
        graphics.fill(4, 4, width - 36, 22 + count * 10, 0xD9101C29);
        graphics.drawString(client.font, Component.translatable("screen.godview_build.title"), 8, 8, 0xFFE6F8F5);
        for (int index = 0; index < count; index++) {
            graphics.drawString(client.font, lines.get(index), 8, 22 + index * 10, 0xFFE6F8F5);
        }
        graphics.fill(width - 30, 6, width - 6, 30, overClose() ? 0xFF487C78 : 0xD9101C29);
        graphics.drawCenteredString(client.font, "X", width - 18, 14, 0xFFFFFFFF);
        if (overClose()) {
            graphics.renderTooltip(client.font, Component.translatable("screen.godview_build.close"), width - 30, 32);
        }
    }

    void restore(String reason) {
        GodviewSelection.stopInput();
        GodviewSelection.reset();
        GodviewInteraction.setActive(originPlayer, false);
        if (supported && client.player == originPlayer && client.getConnection() != null
                && client.getConnection().hasChannel(GodviewInteraction.Mode.TYPE)) {
            PacketDistributor.sendToServer(new GodviewInteraction.Mode(false));
        }
        drag(false);
        KeyMapping.releaseAll();
        KeyMapping.resetToggleKeys();
        GodviewClient.clearMovement(originPlayer.input);
        setCamera(previousCamera);
        if (client.screen == null && client.isWindowActive() && client.level != null && client.player != null) {
            client.mouseHandler.grabMouse();
        }
        ModLog.LOGGER.info("Godview exited; reason={}; restoredCamera={}; anchor={}; yaw={}; pitch={}",
                reason, previousCamera, anchor, yaw, pitch);
    }

    private void setCamera(CameraType cameraType) {
        client.options.setCameraType(cameraType);
        client.gameRenderer.checkEntityPostEffect(cameraType.isFirstPerson() ? client.getCameraEntity() : null);
        client.levelRenderer.needsUpdate();
    }
}
