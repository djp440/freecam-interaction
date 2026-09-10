package local.freecaminteraction.client;

import java.lang.reflect.Field;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import local.freecaminteraction.FreecamActions;
import local.freecaminteraction.FreecamInteraction;
import local.freecaminteraction.FreecamRange;
import local.freecaminteraction.FreecamTarget;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.item.ItemFreecamWand;
import local.freecaminteraction.item.ItemFreecamWand.WandEntry;
import local.freecaminteraction.WandTier;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovementInput;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

@SideOnly(Side.CLIENT)
public final class FreecamClient {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final KeyBinding TOGGLE = new KeyBinding("key.freecam_interaction.enter", Keyboard.KEY_G, "key.categories.freecam_interaction");
    private static final Field DISTANCE = ReflectionHelper.findField(EntityRenderer.class, "thirdPersonDistance", "field_78490_B");
    private static final Field PREVIOUS_DISTANCE = ReflectionHelper.findField(EntityRenderer.class, "thirdPersonDistanceTemp", "field_78491_C");
    private static FreecamClient instance;
    private static WandTier activeTier = WandTier.NORMAL;
    private EntityClientPlayerMP player;
    private EntityOtherPlayerMP camera;
    private MovementInput previousInput;
    private int previousView;
    private float previousDistance;
    private float previousDistanceTemp;
    private boolean previousDebug;
    private boolean dragging;
    private boolean armed;
    private boolean leftMining;
    private boolean modeRequested;
    private int mouseX;
    private int mouseY;
    private long lastFrame;
    private final FreecamSelection selection = new FreecamSelection();

    public static void initialize() {
        instance = new FreecamClient();
        ClientRegistry.registerKeyBinding(TOGGLE);
        FMLCommonHandler.instance().bus().register(instance);
        MinecraftForge.EVENT_BUS.register(instance);
        ModLog.info("Client initialized; lwjgl=" + Sys.getVersion() + "; librarypath=" + System.getProperty("org.lwjgl.librarypath"));
    }

    public static boolean isFreecamActive() {
        return instance != null && instance.current();
    }

    public static WandTier currentTier() {
        return activeTier;
    }

    public static void onAck(int tierOrdinal, int radius) {
        activeTier = WandTier.fromOrdinal(tierOrdinal);
        ModLog.info("Client active tier updated: " + activeTier.name() + "; radius=" + radius);
    }

    public static void onReject() {
        if (instance != null && instance.current()) {
            instance.exit("server_rejected");
        }
    }

    public static void toggleFromItem() {
        if (instance == null || MC.thePlayer == null) return;
        if (instance.camera != null) return;
        if (MC.renderViewEntity == MC.thePlayer) {
            instance.enter();
        }
    }

    public static MovingObjectPosition cameraRayTrace(net.minecraft.world.World world, Vec3 start, Vec3 end) {
        if (isFreecamActive()) {
            return null;
        }
        return world.rayTraceBlocks(start, end);
    }

    private boolean current() {
        return camera != null && MC.thePlayer == player && MC.theWorld == camera.worldObj
                && player.isEntityAlive() && !player.isPlayerSleeping() && MC.renderViewEntity == camera;
    }

    private boolean control() {
        return current() && MC.currentScreen == null && Display.isActive();
    }

    private void requestMode() {
        if (!modeRequested && FreecamInteraction.available) {
            FreecamInteraction.request(true);
            modeRequested = true;
            ModLog.info("Requested server interaction mode");
        }
    }

    private void enter() {
        player = MC.thePlayer;
        WandEntry wand = ItemFreecamWand.findBestWand(player);
        if (wand == null) {
            player.addChatMessage(new ChatComponentTranslation("message.freecam_interaction.no_wand"));
            return;
        }
        activeTier = wand.tier;

        previousInput = player.movementInput;
        previousView = MC.gameSettings.thirdPersonView;
        previousDebug = MC.gameSettings.debugCamEnable;
        try {
            previousDistance = DISTANCE.getFloat(MC.entityRenderer);
            previousDistanceTemp = PREVIOUS_DISTANCE.getFloat(MC.entityRenderer);
            DISTANCE.setFloat(MC.entityRenderer, 13.856406F);
            PREVIOUS_DISTANCE.setFloat(MC.entityRenderer, 13.856406F);
        } catch (IllegalAccessException error) { throw new IllegalStateException("无法设置旧版相机距离", error); }
        camera = new EntityOtherPlayerMP(MC.theWorld, player.getGameProfile());
        camera.setPosition(player.posX, player.boundingBox.minY, player.posZ);
        camera.rotationYaw = player.rotationYaw;
        camera.rotationPitch = 60;
        syncCamera();
        MC.renderViewEntity = camera;
        MC.gameSettings.thirdPersonView = 1;
        MC.gameSettings.debugCamEnable = false;
        player.movementInput = new MovementInput();
        player.setSprinting(false);
        MC.playerController.resetBlockRemoving();
        MC.setIngameNotInFocus();
        KeyBinding.unPressAllKeys();
        mouseX = Mouse.getX();
        mouseY = Mouse.getY();
        lastFrame = System.nanoTime();
        armed = false;
        leftMining = false;
        modeRequested = false;
        requestMode();
        if (!local.freecaminteraction.core.FreecamTransformer.entityRendererPatched) {
            ModLog.info("Warning: EntityRenderer orientCamera patch not yet applied or missing");
        }
        Vec3 initCam = FreecamCollision.cameraPos(camera.posX, camera.posY, camera.posZ, camera.rotationYaw, camera.rotationPitch);
        if (!FreecamCollision.isCameraClear(MC.theWorld, initCam.xCoord, initCam.yCoord, initCam.zCoord)) {
            ModLog.info("Initial camera position partially obstructed at " + initCam.xCoord + "," + initCam.yCoord + "," + initCam.zCoord);
        }
        ModLog.info("Camera entered; player=" + player.posX + "," + player.posY + "," + player.posZ + "; tier=" + activeTier.name());
    }

    private void exit(String reason) {
        if (camera == null) return;
        stopMining();
        if (modeRequested) FreecamInteraction.request(false);
        if (MC.renderViewEntity == camera) MC.renderViewEntity = MC.thePlayer;
        player.movementInput = previousInput;
        MC.gameSettings.thirdPersonView = previousView;
        MC.gameSettings.debugCamEnable = previousDebug;
        try {
            DISTANCE.setFloat(MC.entityRenderer, previousDistance);
            PREVIOUS_DISTANCE.setFloat(MC.entityRenderer, previousDistanceTemp);
        } catch (IllegalAccessException error) { throw new IllegalStateException("无法恢复旧版相机距离", error); }
        ModLog.info("Camera exited; reason=" + reason + "; anchor=" + camera.posX + "," + camera.posY + "," + camera.posZ
                + "; player=" + player.posX + "," + player.posY + "," + player.posZ);
        camera = null;
        player = null;
        dragging = false;
        armed = false;
        modeRequested = false;
        activeTier = WandTier.NORMAL;
        KeyBinding.unPressAllKeys();
        if (MC.currentScreen == null && MC.theWorld != null && Display.isActive()) MC.setIngameFocus();
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || camera == null) return;
        if (!current()) { exit("session_changed"); return; }
        requestMode();
        if (!control()) {
            stopMining();
            armed = false;
            player.movementInput.sneak = false;
            return;
        }
        if (!armed) {
            if (!Mouse.isButtonDown(0) && !Mouse.isButtonDown(1)) armed = true;
            return;
        }
        if (FreecamInteraction.acknowledged && Mouse.isButtonDown(0) && !dragging && selection.hit != null
                && selection.hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) damageSelection();
        else if (leftMining) stopMining();
    }

    @SubscribeEvent
    public void keyboard(InputEvent.KeyInputEvent event) {
        if (TOGGLE.isPressed() && !Keyboard.isRepeatEvent() && MC.currentScreen == null && MC.thePlayer != null
                && MC.thePlayer.isEntityAlive() && !MC.thePlayer.isPlayerSleeping()) {
            if (camera != null) exit("toggle");
            else if (MC.renderViewEntity == MC.thePlayer) enter();
        }
    }

    @SubscribeEvent
    public void mouse(MouseEvent event) {
        if (!control()) return;
        if (event.button == 2) { stopMining(); armed = false; }
        event.setCanceled(true);
        if (event.buttonstate && event.button - 100 == TOGGLE.getKeyCode()) exit("toggle_mouse");
        else if (event.button == 0 && event.buttonstate && overClose()) exit("close_button");
        else if (event.dwheel != 0) player.inventory.changeCurrentItem(event.dwheel);
        else if (event.button == 0) {
            if (event.buttonstate && armed && FreecamInteraction.acknowledged) startMining();
            else if (!event.buttonstate) stopMining();
        } else if (event.button == 1 && event.buttonstate && armed && FreecamInteraction.acknowledged) useSelection();
    }

    private void startMining() {
        MovingObjectPosition hit = selection.hit;
        if (dragging) return;
        if (hit != null && hit.entityHit != null) {
            stopMining();
            sendAction(FreecamActions.ATTACK);
            player.swingItem();
            return;
        }
        if (hit == null || MC.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ).isAir(MC.theWorld, hit.blockX, hit.blockY, hit.blockZ)) return;
        MC.playerController.clickBlock(hit.blockX, hit.blockY, hit.blockZ, hit.sideHit);
        player.swingItem();
        leftMining = true;
    }

    private void damageSelection() {
        MovingObjectPosition hit = selection.hit;
        if (hit == null || MC.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ).isAir(MC.theWorld, hit.blockX, hit.blockY, hit.blockZ)) {
            stopMining();
            return;
        }
        MC.playerController.onPlayerDamageBlock(hit.blockX, hit.blockY, hit.blockZ, hit.sideHit);
        MC.effectRenderer.addBlockHitEffects(hit.blockX, hit.blockY, hit.blockZ, hit);
        player.swingItem();
    }

    private void stopMining() {
        if (leftMining && MC.playerController != null && MC.theWorld != null) MC.playerController.resetBlockRemoving();
        leftMining = false;
    }

    private void sendAction(int kind) { sendAction(kind, selection.hit); }

    private void sendAction(int kind, MovingObjectPosition hit) {
        if (selection.rayStart == null || selection.rayEnd == null) return;
        MC.playerController.updateController();
        FreecamActions.send(new FreecamActions.Action(kind, player.inventory.currentItem,
                hit != null && hit.entityHit != null ? hit.entityHit.getEntityId() : -1,
                selection.rayStart, selection.rayEnd, hit == null ? selection.rayEnd : hit.hitVec));
    }

    private void useSelection() {
        if (dragging) return;
        MovingObjectPosition hit = selection.hit;
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof net.minecraft.item.ItemFishingRod) {
            if (hit != null || player.fishEntity != null) sendAction(FreecamActions.FISH);
            return;
        }
        if (held != null && held.getItem() instanceof ItemBucket) {
            MovingObjectPosition bucket = FreecamTarget.pickBlock(player, selection.rayStart, selection.rayEnd,
                    FreecamActions.emptyBucket(held));
            if (bucket != null) sendAction(FreecamActions.USE_BUCKET, bucket);
            return;
        }
        if (hit != null && hit.entityHit != null) {
            sendAction(FreecamActions.INTERACT);
            player.swingItem();
            return;
        }
        if (hit == null || MC.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ).isAir(MC.theWorld, hit.blockX, hit.blockY, hit.blockZ)) return;
        ItemStack item = player.inventory.getCurrentItem();
        int size = item == null ? 0 : item.stackSize;
        boolean allowed = !ForgeEventFactory.onPlayerInteract(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,
                hit.blockX, hit.blockY, hit.blockZ, hit.sideHit, MC.theWorld).isCanceled();
        if (allowed && MC.playerController.onPlayerRightClick(player, MC.theWorld, item,
                hit.blockX, hit.blockY, hit.blockZ, hit.sideHit, hit.hitVec)) player.swingItem();
        if (item == null) return;
        if (item.stackSize == 0) player.inventory.mainInventory[player.inventory.currentItem] = null;
        else if (item.stackSize != size || MC.playerController.isInCreativeMode()) MC.entityRenderer.itemRenderer.resetEquippedProgress();
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || camera == null) return;
        if (!current()) { exit("session_changed"); return; }
        long now = System.nanoTime();
        double elapsed = (now - lastFrame) / 1_000_000_000.0;
        lastFrame = now;
        MC.gameSettings.thirdPersonView = 1;
        MC.gameSettings.debugCamEnable = false;
        int x = Mouse.getX(), y = Mouse.getY();
        boolean rotate = control() && Mouse.isButtonDown(2);
        if (control()) {
            MC.setIngameNotInFocus();
            player.movementInput.sneak = down(MC.gameSettings.keyBindSneak);
            if (rotate && dragging) {
                float targetYaw = MathHelper.wrapAngleTo180_float(camera.rotationYaw + (x - mouseX) * 0.2F);
                float targetPitch = MathHelper.clamp_float(camera.rotationPitch - (y - mouseY) * 0.2F, -85, 85);
                float[] safeRot = FreecamCollision.solveRotation(MC.theWorld, camera.posX, camera.posY, camera.posZ,
                        camera.rotationYaw, camera.rotationPitch, targetYaw, targetPitch);
                camera.rotationYaw = safeRot[0];
                camera.rotationPitch = safeRot[1];
            }
            double forward = (down(MC.gameSettings.keyBindForward) ? 1 : 0) - (down(MC.gameSettings.keyBindBack) ? 1 : 0);
            double right = (down(MC.gameSettings.keyBindRight) ? 1 : 0) - (down(MC.gameSettings.keyBindLeft) ? 1 : 0);
            double up = (down(MC.gameSettings.keyBindJump) || isKeyDown(Keyboard.KEY_SPACE) ? 1 : 0)
                    - (down(MC.gameSettings.keyBindSprint) || isKeyDown(Keyboard.KEY_LCONTROL) || isKeyDown(Keyboard.KEY_RCONTROL) ? 1 : 0);
            double[] offset = FreecamMotion.pan(camera.rotationYaw, forward, right, elapsed);
            double yOffset = FreecamMotion.vertical(up, elapsed);
            double targetAnchorX = FreecamRange.clampCamera(player.posX, camera.posX + offset[0], activeTier);
            double targetAnchorY = FreecamRange.clampCameraY(camera.posY + yOffset);
            double targetAnchorZ = FreecamRange.clampCamera(player.posZ, camera.posZ + offset[1], activeTier);
            double deltaX = targetAnchorX - camera.posX;
            double deltaY = targetAnchorY - camera.posY;
            double deltaZ = targetAnchorZ - camera.posZ;

            Vec3 camPos = FreecamCollision.cameraPos(camera.posX, camera.posY, camera.posZ, camera.rotationYaw, camera.rotationPitch);
            double[] accepted = FreecamCollision.solveTranslation(MC.theWorld, camPos.xCoord, camPos.yCoord, camPos.zCoord, deltaX, deltaY, deltaZ);
            camera.setPosition(camera.posX + accepted[0], camera.posY + accepted[1], camera.posZ + accepted[2]);
            syncCamera();
        }
        if (dragging != rotate) ModLog.info("Camera dragging=" + rotate + "; yaw=" + camera.rotationYaw + "; pitch=" + camera.rotationPitch);
        dragging = rotate;
        mouseX = x;
        mouseY = y;
    }

    private static boolean isKeyDown(int code) {
        return code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
    }

    private static boolean down(KeyBinding key) {
        int code = key.getKeyCode();
        return code < 0 ? code + 100 >= 0 && code + 100 < Mouse.getButtonCount() && Mouse.isButtonDown(code + 100)
                : isKeyDown(code);
    }

    @SubscribeEvent
    public void worldRender(net.minecraftforge.client.event.RenderWorldLastEvent event) {
        if (current() && MC.currentScreen == null) selection.render(camera, control() && !dragging);
        else selection.hit = null;
    }

    private void syncCamera() {
        camera.lastTickPosX = camera.prevPosX = camera.posX;
        camera.lastTickPosY = camera.prevPosY = camera.posY;
        camera.lastTickPosZ = camera.prevPosZ = camera.posZ;
        camera.prevRotationYaw = camera.rotationYaw;
        camera.prevRotationPitch = camera.rotationPitch;
    }

    private boolean overClose() {
        ScaledResolution resolution = new ScaledResolution(MC, MC.displayWidth, MC.displayHeight);
        int x = Mouse.getX() * resolution.getScaledWidth() / MC.displayWidth;
        int y = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / MC.displayHeight - 1;
        return !MC.gameSettings.hideGUI && x >= resolution.getScaledWidth() - 30 && x < resolution.getScaledWidth() - 6 && y >= 6 && y < 30;
    }

    @SubscribeEvent
    public void crosshair(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS && current()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !current() || MC.currentScreen != null || MC.gameSettings.hideGUI) return;
        int width = event.resolution.getScaledWidth();
        Gui.drawRect(4, 4, width - 36, 42, 0xD9101C29);
        String wandName = I18n.format("item.freecam_interaction.wand_" + activeTier.id + ".name");
        String title = I18n.format("screen.freecam_interaction.title") + " [" + wandName + "]";
        MC.fontRenderer.drawStringWithShadow(title, 8, 8, 0xE6F8F5);
        String help = I18n.format(FreecamInteraction.acknowledged ? "screen.freecam_interaction.help" : "screen.freecam_interaction.unsupported",
                GameSettings.getKeyDisplayString(TOGGLE.getKeyCode()));
        MC.fontRenderer.drawSplitString(help, 8, 22, Math.max(1, width - 52), 0xE6F8F5);
        Gui.drawRect(width - 30, 6, width - 6, 30, overClose() ? 0xFF487C78 : 0xD9101C29);
        MC.fontRenderer.drawStringWithShadow("X", width - 21, 14, 0xFFFFFF);
    }
}
