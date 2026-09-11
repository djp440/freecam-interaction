package local.godviewbuild.client;

import com.mojang.blaze3d.platform.InputConstants;
import local.godviewbuild.GodviewBuild;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = GodviewBuild.MOD_ID, value = Dist.CLIENT)
public final class GodviewClient {
    static final float PITCH = 60.0F;
    static final float CAMERA_DISTANCE = (float) (12.0 / Math.sin(Math.toRadians(PITCH)));
    private static KeyMapping enterKey;
    private static GodviewSession session;

    private GodviewClient() {}

    @EventBusSubscriber(modid = GodviewBuild.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            enterKey = new KeyMapping("key.godview_build.enter", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.godview_build");
            event.register(enterKey);
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (enterKey == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (session != null && !session.isCurrentSession()) {
            exit("session_changed");
        }
        while (enterKey.consumeClick()) {
            if (client.screen == null && client.getOverlay() == null && client.level != null
                    && client.player != null && client.gameMode != null && client.player.isAlive()
                    && !client.player.isSleeping() && client.getCameraEntity() == client.player) {
                if (session == null) {
                    session = new GodviewSession();
                } else {
                    exit("toggle_key");
                }
            }
        }
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (session != null && session.isCurrentSession()) {
            event.setYaw(session.yaw);
            event.setPitch(session.pitch);
            event.setRoll(0.0F);
        }
    }

    @SubscribeEvent
    public static void onDistance(CalculateDetachedCameraDistanceEvent event) {
        if (session != null && session.isCurrentSession()) {
            event.getCamera().setPosition(session.anchor);
            event.setDistance(cameraDistance(event.getEntityScalingFactor()));
        }
    }

    static float cameraDistance(float scale) {
        return Float.isFinite(scale) && scale > 0.0F ? CAMERA_DISTANCE / scale : CAMERA_DISTANCE;
    }

    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (session != null && session.isCurrentSession()) {
            clearMovement(event.getInput());
            event.getInput().shiftKeyDown = session.canControl()
                    && session.down(Minecraft.getInstance().options.keyShift);
            event.getEntity().setSprinting(false);
        }
    }

    static void clearMovement(Input input) {
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        exit("disconnect");
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (session != null) {
            if (!session.isCurrentSession()) {
                exit("session_changed");
            } else {
                session.update();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (session != null && session.isCurrentSession() && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        if (session != null && session.isCurrentSession()) {
            session.render(event.getGuiGraphics(), enterKey);
        }
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (session != null && session.canControl()) {
            event.setCanceled(true);
            if (enterKey.matchesMouse(event.getButton()) && event.getAction() == GLFW.GLFW_PRESS) {
                exit("toggle_mouse");
            } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS
                    && session.overClose()) {
                exit("close_button");
            } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                session.drag(event.getAction() == GLFW.GLFW_PRESS);
                GodviewSelection.stopInput();
            } else if (canBuild()) {
                var key = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
                boolean pressed = event.getAction() == GLFW.GLFW_PRESS;
                KeyMapping.set(key, pressed);
                if (pressed) {
                    KeyMapping.click(key);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (enterKey != null && event.getAction() == GLFW.GLFW_REPEAT
                && enterKey.matches(event.getKey(), event.getScanCode())) {
            while (enterKey.consumeClick()) {}
        }
        releaseMouse();
    }

    @SubscribeEvent
    public static void onMousePost(InputEvent.MouseButton.Post event) {
        releaseMouse();
    }

    private static void releaseMouse() {
        if (session != null && session.canControl() && Minecraft.getInstance().mouseHandler.isMouseGrabbed()) {
            Minecraft.getInstance().mouseHandler.releaseMouse();
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        boolean hasBlock = GodviewSelection.target() != null;
        boolean hasEntity = GodviewSelection.entityTarget() != null;
        if (session != null && (!canBuild() || !hasBlock && !hasEntity || event.isPickBlock()
                || event.isAttack() && hasEntity)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (session != null && event.isUseItem()) {
            if (hasEntity) {
                local.godviewbuild.ModLog.LOGGER.debug("Godview entity use requested; entity={}; hand={}",
                        GodviewSelection.entityTarget().getEntity().getId(), event.getHand());
            } else {
                local.godviewbuild.ModLog.LOGGER.debug("Godview block use requested; position={}; face={}; hand={}",
                        GodviewSelection.target().getBlockPos(), GodviewSelection.target().getDirection(), event.getHand());
            }
        }
    }

    public static boolean active() {
        return session != null;
    }

    public static boolean canBuild() {
        return session != null && session.canControl() && !session.dragging && !session.overClose()
                && local.godviewbuild.GodviewInteraction.active(Minecraft.getInstance().player);
    }

    private static void exit(String reason) {
        if (session != null) {
            session.restore(reason);
            session = null;
        }
    }
}
