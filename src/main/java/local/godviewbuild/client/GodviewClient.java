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
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = GodviewBuild.MOD_ID, value = Dist.CLIENT)
public final class GodviewClient {
    static final float PITCH = 60.0F;
    static final float CAMERA_DISTANCE = (float) (12.0 / Math.sin(Math.toRadians(PITCH)));
    private static KeyMapping enterKey;

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
        while (enterKey.consumeClick()) {
            if (client.screen == null && client.getOverlay() == null && client.level != null
                    && client.player != null && client.gameMode != null && client.player.isAlive()
                    && !client.player.isSleeping() && client.getCameraEntity() == client.player) {
                client.setScreen(new GodviewScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (Minecraft.getInstance().screen instanceof GodviewScreen screen && screen.isCurrentSession()) {
            event.setYaw(screen.cameraYaw());
            event.setPitch(PITCH);
            event.setRoll(0.0F);
        }
    }

    @SubscribeEvent
    public static void onDistance(CalculateDetachedCameraDistanceEvent event) {
        if (Minecraft.getInstance().screen instanceof GodviewScreen screen && screen.isCurrentSession()) {
            event.setDistance(cameraDistance(event.getEntityScalingFactor()));
        }
    }

    static float cameraDistance(float scale) {
        return Float.isFinite(scale) && scale > 0.0F ? CAMERA_DISTANCE / scale : CAMERA_DISTANCE;
    }

    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (Minecraft.getInstance().screen instanceof GodviewScreen screen && screen.isCurrentSession()) {
            clearMovement(event.getInput());
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
        if (Minecraft.getInstance().screen instanceof GodviewScreen screen) {
            screen.restore("disconnect");
        }
    }
}
