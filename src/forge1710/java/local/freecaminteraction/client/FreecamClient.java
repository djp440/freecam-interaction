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
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.ActiveRenderInfo;
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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

@SideOnly(Side.CLIENT)
public final class FreecamClient {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final RenderItem ITEM_RENDERER = new RenderItem();
    private static final KeyBinding TOGGLE = new KeyBinding("key.freecam_interaction.enter", Keyboard.KEY_G, "key.categories.freecam_interaction");
    private static final KeyBinding KEY_BLUEPRINT = new KeyBinding("key.freecam_interaction.blueprint", Keyboard.KEY_B, "key.categories.freecam_interaction");
    private static final Field DISTANCE = ReflectionHelper.findField(EntityRenderer.class, "thirdPersonDistance", "field_78490_B");
    private static final Field PREVIOUS_DISTANCE = ReflectionHelper.findField(EntityRenderer.class, "thirdPersonDistanceTemp", "field_78491_C");
    private static FreecamClient instance;
    private static WandTier activeTier = WandTier.NORMAL;

    public enum SelectionMode {
        IDLE,
        SELECTING_A,
        SELECTING_B
    }

    private static SelectionMode selectionMode = SelectionMode.IDLE;
    private static int[] pointA = null;
    private static int[] pointB = null;

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
        ClientRegistry.registerKeyBinding(KEY_BLUEPRINT);
        FMLCommonHandler.instance().bus().register(instance);
        MinecraftForge.EVENT_BUS.register(instance);
        MinecraftForge.EVENT_BUS.register(local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE);
        ModLog.info("Client initialized; lwjgl=" + Sys.getVersion() + "; librarypath=" + System.getProperty("org.lwjgl.librarypath"));
    }

    public static boolean isFreecamActive() {
        return instance != null && instance.current();
    }

    public static WandTier currentTier() {
        return activeTier;
    }

    public static SelectionMode getSelectionMode() {
        return selectionMode;
    }

    public static void setSelectionMode(SelectionMode mode) {
        selectionMode = mode;
        if (mode == SelectionMode.IDLE) {
            pointA = null;
            pointB = null;
        }
    }

    public static int[] getPointA() {
        return pointA;
    }

    public static int[] getPointB() {
        return pointB;
    }

    public static void startSelection() {
        selectionMode = SelectionMode.SELECTING_A;
        pointA = null;
        pointB = null;
        local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE.cancelPlacementPreview();
        ModLog.info("Entered blueprint selection mode: SELECTING_A");
    }

    public static void cancelSelection() {
        if (selectionMode != SelectionMode.IDLE) {
            ModLog.info("Cancelled blueprint selection mode");
        }
        selectionMode = SelectionMode.IDLE;
        pointA = null;
        pointB = null;
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

    public static Object getWandUpgradeGui(EntityPlayer player, int wandSlot) {
        return new GuiWandUpgrade(player.inventory, wandSlot);
    }

    public static MovingObjectPosition getSelectionHit() {
        if (instance == null || instance.selection == null) return null;
        return instance.selection.hit;
    }

    public static MovingObjectPosition cameraRayTrace(net.minecraft.world.World world, Vec3 start, Vec3 end) {
        if (isFreecamActive()) {
            return null;
        }
        return world.rayTraceBlocks(start, end);
    }

    /** 让旧版粒子 billboard 使用实际自由相机朝向，而不是玩家本体朝向。 */
    public static void updateRenderInfoForCamera(EntityPlayer vanillaPlayer, boolean reverseView) {
        EntityPlayer view = vanillaPlayer;
        if (isFreecamActive() && MC.renderViewEntity instanceof EntityPlayer) {
            view = (EntityPlayer) MC.renderViewEntity;
        }
        ActiveRenderInfo.updateRenderInfo(view, reverseView);
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
        cancelSelection();
        local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE.cancelPlacementPreview();
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
        // 选区或投放预览期间，禁止普通挖掘
        if (selectionMode != SelectionMode.IDLE || local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE.isPlacementPreviewActive()) {
            stopMining();
            return;
        }
        if (FreecamInteraction.acknowledged && Mouse.isButtonDown(0) && !dragging && selection.hit != null
                && selection.hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) damageSelection();
        else if (leftMining) stopMining();
    }

    @SubscribeEvent
    public void keyboard(InputEvent.KeyInputEvent event) {
        if (MC.currentScreen != null) return;

        if (TOGGLE.isPressed() && !Keyboard.isRepeatEvent() && MC.thePlayer != null
                && MC.thePlayer.isEntityAlive() && !MC.thePlayer.isPlayerSleeping()) {
            if (camera != null) exit("toggle");
            else if (MC.renderViewEntity == MC.thePlayer) enter();
            return;
        }

        // B 键触发蓝图菜单或取消选区/预览
        if (KEY_BLUEPRINT.isPressed() && !Keyboard.isRepeatEvent() && isFreecamActive()) {
            if (selectionMode != SelectionMode.IDLE) {
                cancelSelection();
            } else if (local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE.isPlacementPreviewActive()) {
                local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE.cancelPlacementPreview();
            } else {
                openBlueprintManager();
            }
            return;
        }

        // ESC 键取消选区或预览
        if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE) && isFreecamActive()) {
            if (selectionMode != SelectionMode.IDLE) {
                cancelSelection();
            } else if (local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE.isPlacementPreviewActive()) {
                local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE.cancelPlacementPreview();
            }
        }
    }

    @SubscribeEvent
    public void mouse(MouseEvent event) {
        if (!control()) return;
        if (event.button == 2) { stopMining(); armed = false; }
        event.setCanceled(true);

        if (event.buttonstate && event.button - 100 == TOGGLE.getKeyCode()) {
            exit("toggle_mouse");
            return;
        }
        if (event.button == 0 && event.buttonstate) {
            int hotbarSlot = getHotbarSlotUnderMouse();
            if (hotbarSlot >= 0) {
                stopMining();
                if (MC.thePlayer != null && MC.thePlayer.inventory.currentItem != hotbarSlot) {
                    MC.thePlayer.inventory.currentItem = hotbarSlot;
                    ModLog.info(String.format("Hotbar slot selected via cursor click: %d", hotbarSlot));
                }
                event.setCanceled(true);
                return;
            }
            if (overClose()) {
                exit("close_button");
                return;
            }
            if (overBlueprintButton()) {
                openBlueprintManager();
                return;
            }
            if (overSelectButton()) {
                if (selectionMode == SelectionMode.IDLE) {
                    startSelection();
                } else {
                    cancelSelection();
                }
                return;
            }
        }

        if (event.dwheel != 0) {
            player.inventory.changeCurrentItem(event.dwheel);
            return;
        }

        // 选区状态机与投放预览拦截
        if (selectionMode == SelectionMode.SELECTING_A) {
            if (event.button == 0 && event.buttonstate && armed) {
                // 左键点击捕获方块 A
                MovingObjectPosition hit = selection.hit;
                if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    pointA = new int[] { hit.blockX, hit.blockY, hit.blockZ };
                    selectionMode = SelectionMode.SELECTING_B;
                    stopMining();
                    player.swingItem();
                    ModLog.info("Selection point A set: " + pointA[0] + "," + pointA[1] + "," + pointA[2]);
                }
            } else if (event.button == 1 && event.buttonstate) {
                // 右键取消选区
                cancelSelection();
            }
            return;
        } else if (selectionMode == SelectionMode.SELECTING_B) {
            if (event.button == 0 && event.buttonstate && armed) {
                // 左键点击捕获方块 B
                MovingObjectPosition hit = selection.hit;
                if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    pointB = new int[] { hit.blockX, hit.blockY, hit.blockZ };
                    stopMining();
                    player.swingItem();
                    ModLog.info("Selection point B set: " + pointB[0] + "," + pointB[1] + "," + pointB[2]);
                    // 弹出蓝图保存对话框
                    int x1 = pointA[0], y1 = pointA[1], z1 = pointA[2];
                    int x2 = pointB[0], y2 = pointB[1], z2 = pointB[2];
                    cancelSelection();
                    MC.displayGuiScreen(new local.freecaminteraction.client.gui.GuiSaveBlueprint(x1, y1, z1, x2, y2, z2));
                }
            } else if (event.button == 1 && event.buttonstate) {
                // 右键取消选区
                cancelSelection();
            }
            return;
        } else if (local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE.isPlacementPreviewActive()) {
            if (event.button == 0 && event.buttonstate && armed) {
                MovingObjectPosition hit = selection.hit;
                if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    int tx = hit.blockX;
                    int ty = hit.blockY;
                    int tz = hit.blockZ;
                    Block b = MC.theWorld.getBlock(tx, ty, tz);
                    if (b != null && !b.isAir(MC.theWorld, tx, ty, tz) && !b.isReplaceable(MC.theWorld, tx, ty, tz)) {
                        tx += net.minecraft.util.Facing.offsetsXForSide[hit.sideHit];
                        ty += net.minecraft.util.Facing.offsetsYForSide[hit.sideHit];
                        tz += net.minecraft.util.Facing.offsetsZForSide[hit.sideHit];
                    }
                    stopMining();
                    player.swingItem();
                    local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE.onPlacementClick(tx, ty, tz);
                }
            } else if (event.button == 1 && event.buttonstate) {
                local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE.cancelPlacementPreview();
            }
            return;
        }

        // 普通建造与挖掘操作
        if (event.button == 0) {
            if (event.buttonstate && armed && FreecamInteraction.acknowledged) startMining();
            else if (!event.buttonstate) stopMining();
        } else if (event.button == 1 && event.buttonstate && armed && FreecamInteraction.acknowledged) {
            useSelection();
        }
    }

    public static void openBlueprintManager() {
        if (instance != null && instance.current()) {
            instance.stopMining();
            instance.armed = false;
            MC.displayGuiScreen(new local.freecaminteraction.client.gui.GuiBlueprintManager());
        }
    }

    private void startMining() {
        MovingObjectPosition hit = selection.hit;
        if (dragging) return;
        if (hit != null && hit.entityHit != null) {
            stopMining();
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
        FreecamActions.sendRay(selection.rayStart, selection.rayEnd, hit.hitVec);
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
            boolean sneaking = down(MC.gameSettings.keyBindSneak);
            player.movementInput.sneak = sneaking;
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
            double speed = sneaking ? 1.0D : ItemFreecamWand.getInventorySpeedMultiplier(player);
            double[] offset = FreecamMotion.pan(camera.rotationYaw, forward, right, elapsed);
            offset[0] *= speed;
            offset[1] *= speed;
            double yOffset = FreecamMotion.vertical(up, elapsed) * speed;
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

    private int getHotbarSlotUnderMouse() {
        if (MC.thePlayer == null || MC.gameSettings.hideGUI) return -1;
        ScaledResolution resolution = new ScaledResolution(MC, MC.displayWidth, MC.displayHeight);
        int mouseX = Mouse.getX() * resolution.getScaledWidth() / MC.displayWidth;
        int mouseY = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / MC.displayHeight - 1;
        return FreecamHotbar.getSlotAt(mouseX, mouseY, resolution.getScaledWidth(), resolution.getScaledHeight());
    }

    private boolean overClose() {
        ScaledResolution resolution = new ScaledResolution(MC, MC.displayWidth, MC.displayHeight);
        int x = Mouse.getX() * resolution.getScaledWidth() / MC.displayWidth;
        int y = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / MC.displayHeight - 1;
        return !MC.gameSettings.hideGUI && x >= resolution.getScaledWidth() - 30 && x < resolution.getScaledWidth() - 6 && y >= 6 && y < 30;
    }

    private boolean overBlueprintButton() {
        ScaledResolution resolution = new ScaledResolution(MC, MC.displayWidth, MC.displayHeight);
        int x = Mouse.getX() * resolution.getScaledWidth() / MC.displayWidth;
        int y = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / MC.displayHeight - 1;
        int width = resolution.getScaledWidth();
        return !MC.gameSettings.hideGUI && x >= width - 82 && x < width - 36 && y >= 6 && y < 30;
    }

    private boolean overSelectButton() {
        ScaledResolution resolution = new ScaledResolution(MC, MC.displayWidth, MC.displayHeight);
        int x = Mouse.getX() * resolution.getScaledWidth() / MC.displayWidth;
        int y = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / MC.displayHeight - 1;
        int width = resolution.getScaledWidth();
        return !MC.gameSettings.hideGUI && x >= width - 134 && x < width - 88 && y >= 6 && y < 30;
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

        String help;
        if (selectionMode == SelectionMode.SELECTING_A) {
            help = I18n.format("screen.freecam_interaction.hud_selecting_a");
        } else if (selectionMode == SelectionMode.SELECTING_B) {
            help = I18n.format("screen.freecam_interaction.hud_selecting_b");
        } else if (local.freecaminteraction.client.renderer.BlueprintGhostRenderer.INSTANCE.isPlacementPreviewActive()) {
            help = I18n.format("screen.freecam_interaction.hud_preview");
        } else {
            help = I18n.format(FreecamInteraction.acknowledged ? "screen.freecam_interaction.help" : "screen.freecam_interaction.unsupported",
                    GameSettings.getKeyDisplayString(TOGGLE.getKeyCode()));
        }
        MC.fontRenderer.drawSplitString(help, 8, 22, Math.max(1, width - 140), 0xE6F8F5);

        // 选区模式按钮 (右上角宽 42)
        boolean inSelect = (selectionMode != SelectionMode.IDLE);
        int selColor = inSelect ? 0xFF2A7545 : (overSelectButton() ? 0xFF487C78 : 0xD9101C29);
        Gui.drawRect(width - 134, 6, width - 88, 30, selColor);
        MC.fontRenderer.drawStringWithShadow(I18n.format("screen.freecam_interaction.btn_select"), width - 128, 14, 0xFFFFFF);

        // 蓝图管理按钮 (右上角宽 42)
        Gui.drawRect(width - 82, 6, width - 36, 30, overBlueprintButton() ? 0xFF487C78 : 0xD9101C29);
        MC.fontRenderer.drawStringWithShadow(I18n.format("screen.freecam_interaction.btn_blueprint"), width - 76, 14, 0xFFFFFF);

        // 关闭 X 按钮
        Gui.drawRect(width - 30, 6, width - 6, 30, overClose() ? 0xFF487C78 : 0xD9101C29);
        MC.fontRenderer.drawStringWithShadow("X", width - 21, 14, 0xFFFFFF);

        renderCursorHeldItem(event.resolution);
    }

    private void renderCursorHeldItem(ScaledResolution resolution) {
        if (MC.thePlayer == null) return;
        ItemStack held = MC.thePlayer.inventory.getCurrentItem();
        if (held == null) return;

        int mouseX = Mouse.getX() * resolution.getScaledWidth() / MC.displayWidth;
        int mouseY = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / MC.displayHeight - 1;
        int renderX = mouseX + 4;
        int renderY = mouseY + 4;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        ITEM_RENDERER.renderItemAndEffectIntoGUI(MC.fontRenderer, MC.getTextureManager(), held, renderX, renderY);
        ITEM_RENDERER.renderItemOverlayIntoGUI(MC.fontRenderer, MC.getTextureManager(), held, renderX, renderY);

        RenderHelper.disableStandardItemLighting();
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
