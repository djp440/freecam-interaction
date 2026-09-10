package local.freecaminteraction.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import local.freecaminteraction.FreecamInteractionMod;
import local.freecaminteraction.FreecamGeometry;
import local.freecaminteraction.FreecamInteraction;
import local.freecaminteraction.FreecamRange;
import local.freecaminteraction.FreecamTarget;
import local.freecaminteraction.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = FreecamInteractionMod.MOD_ID, value = Dist.CLIENT)
public final class FreecamSelection {
    private static Matrix4f inverseViewProjection;
    private static Vec3 cameraPosition;
    private static BlockHitResult selected;
    private static EntityHitResult entityTarget;
    private static Vec3 rayEnd;
    private static BlockPos mining;
    private static boolean blockedUntilRelease;
    private static int viewportWidth;
    private static int viewportHeight;

    private FreecamSelection() {}

    public static BlockHitResult target() { return selected; }

    public static boolean hasTarget() { return selected != null || entityTarget != null; }
    public static boolean holdingRod() {
        return Minecraft.getInstance().player.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem;
    }
    public static boolean canRetrieve() { return holdingRod() && Minecraft.getInstance().player.fishing != null; }

    public static boolean customBucket(InteractionHand hand) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.player.getItemInHand(hand).getItem() instanceof BucketItem bucket)) return false;
        if (blockedUntilRelease || !FreecamClient.canBuild() || cameraPosition == null || rayEnd == null) return true;
        if (!client.getConnection().hasChannel(local.freecaminteraction.FreecamActions.Action.TYPE)) {
            client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.freecam_interaction.unsupported"), true);
            return true;
        }
        BlockHitResult hit = FreecamTarget.pickBlock(client.player, cameraPosition, rayEnd,
                bucket.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);
        if (hit == null) return true;
        client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(client.player.getInventory().selected));
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new local.freecaminteraction.FreecamActions.Action(
                local.freecaminteraction.FreecamActions.USE_BUCKET, client.player.getInventory().selected, hand.ordinal(),
                cameraPosition, rayEnd, hit.getLocation()));
        return true;
    }

    public static boolean customAction(boolean attack) {
        Minecraft client = Minecraft.getInstance();
        boolean rod = !attack && holdingRod();
        if (!rod && entityTarget == null) return false;
        if (blockedUntilRelease || !FreecamClient.canBuild()) return true;
        if (!client.getConnection().hasChannel(local.freecaminteraction.FreecamActions.Action.TYPE)) {
            client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.freecam_interaction.unsupported"), true);
            return true;
        }
        if ((!hasTarget() && !canRetrieve()) || cameraPosition == null || rayEnd == null) return true;
        client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(client.player.getInventory().selected));
        HitResult hit = entityTarget != null ? entityTarget : selected;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new local.freecaminteraction.FreecamActions.Action(
                attack ? 0 : rod ? 2 : 1, client.player.getInventory().selected,
                entityTarget == null ? -1 : entityTarget.getEntity().getId(), cameraPosition, rayEnd,
                hit == null ? rayEnd : hit.getLocation()));
        return true;
    }

    public static void reset() {
        inverseViewProjection = null;
        selected = null;
        entityTarget = null;
        rayEnd = null;
        mining = null;
        blockedUntilRelease = true;
    }

    public static void stopInput() {
        Minecraft client = Minecraft.getInstance();
        if (mining != null && client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
            ModLog.LOGGER.debug("Freecam mining stopped; position={}", mining);
        }
        mining = null;
        client.options.keyAttack.setDown(false);
        client.options.keyUse.setDown(false);
        while (client.options.keyAttack.consumeClick()) {}
        while (client.options.keyUse.consumeClick()) {}
        if (client.player != null && client.player.isUsingItem() && client.gameMode != null) {
            client.gameMode.releaseUsingItem(client.player);
        }
        blockedUntilRelease = true;
    }

    public static void beforeInput() {
        Minecraft client = Minecraft.getInstance();
        pick();
        if (!FreecamClient.canBuild()) {
            stopInput();
            return;
        }
        if (blockedUntilRelease) {
            if (!physicalDown(client.options.keyAttack) && !physicalDown(client.options.keyUse)) {
                blockedUntilRelease = false;
            } else {
                stopInput();
                return;
            }
        }
        client.options.keyAttack.setDown(physicalDown(client.options.keyAttack));
        client.options.keyUse.setDown(physicalDown(client.options.keyUse));
        if (selected == null || mining != null && !mining.equals(selected.getBlockPos())) {
            if (mining != null) {
                client.gameMode.stopDestroyBlock();
                mining = null;
            }
            if (!hasTarget() && !canRetrieve()) {
                client.options.keyAttack.setDown(false);
                client.options.keyUse.setDown(false);
                while (client.options.keyAttack.consumeClick()) {}
                while (client.options.keyUse.consumeClick()) {}
                if (client.player.isUsingItem()) {
                    client.gameMode.releaseUsingItem(client.player);
                }
            }
        }
    }

    private static boolean physicalDown(net.minecraft.client.KeyMapping mapping) {
        var key = mapping.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
        return key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM && key.getValue() >= 0
                && com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, key.getValue());
    }

    public static boolean continueAttack() {
        Minecraft client = Minecraft.getInstance();
        boolean held = FreecamClient.canBuild() && !blockedUntilRelease && selected != null
                && client.options.keyAttack.isDown();
        BlockPos next = held ? selected.getBlockPos() : null;
        if (next != null && !next.equals(mining)) {
            ModLog.LOGGER.debug("Freecam mining; position={}; face={}; slot={}", next,
                    selected.getDirection(), client.player.getInventory().selected);
        }
        mining = next;
        return held;
    }

    public static void pick() {
        Minecraft client = Minecraft.getInstance();
        selected = null;
        entityTarget = null;
        if (FreecamClient.canBuild() && inverseViewProjection != null
                && viewportWidth == client.getWindow().getWidth() && viewportHeight == client.getWindow().getHeight()) {
            float mouseX = (float) (client.mouseHandler.xpos() / client.getWindow().getScreenWidth() * 2 - 1);
            float mouseY = (float) (1 - client.mouseHandler.ypos() / client.getWindow().getScreenHeight() * 2);
            Vec3 direction = FreecamGeometry.direction(inverseViewProjection, mouseX, mouseY);
            double distance = Math.min(cameraPosition.distanceTo(client.player.position()) + 16,
                    client.options.getEffectiveRenderDistance() * 16.0);
            Vec3 end = cameraPosition.add(direction.scale(distance));
            AABB range = new AABB(client.player.position(), client.player.position()).inflate(8.5);
            if (Double.isFinite(direction.x) && Double.isFinite(direction.y) && Double.isFinite(direction.z)
                    && (range.contains(cameraPosition) || range.clip(cameraPosition, end).isPresent())) {
                rayEnd = end;
                HitResult hit = local.freecaminteraction.FreecamTarget.pick(client.player, cameraPosition, end);
                if (hit instanceof BlockHitResult block) selected = block;
                else if (hit instanceof EntityHitResult entity
                        && client.getConnection().hasChannel(local.freecaminteraction.FreecamActions.Action.TYPE)) entityTarget = entity;
            }
        }
        client.hitResult = entityTarget != null ? entityTarget : selected != null ? selected : BlockHitResult.miss(
                client.player == null ? Vec3.ZERO : client.player.position(), Direction.UP, BlockPos.ZERO);
        client.crosshairPickEntity = entityTarget == null ? null : entityTarget.getEntity();
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        if (FreecamClient.active()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (!FreecamClient.active()) {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            inverseViewProjection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert();
            cameraPosition = event.getCamera().getPosition();
            viewportWidth = Minecraft.getInstance().getWindow().getWidth();
            viewportHeight = Minecraft.getInstance().getWindow().getHeight();
            pick();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL
                && Minecraft.getInstance().screen == null && Minecraft.getInstance().getOverlay() == null) {
            Minecraft client = Minecraft.getInstance();
            if (!client.options.hideGui && FreecamInteraction.active(client.player)) {
                renderRange(event);
            }
            if (FreecamClient.canBuild() && selected != null) {
                renderFace(event);
            }
        }
    }

    private static void renderRange(RenderLevelStageEvent event) {
        Vec3 player = Minecraft.getInstance().player.position();
        double minX = FreecamRange.minimumBlock(player.x);
        double minY = FreecamRange.minimumBlock(player.y);
        double minZ = FreecamRange.minimumBlock(player.z);
        AABB range = new AABB(minX, minY, minZ,
                minX + FreecamRange.SIZE, minY + FreecamRange.SIZE, minZ + FreecamRange.SIZE)
                .move(event.getCamera().getPosition().reverse());
        var previousShader = RenderSystem.getShader();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (int axis = 0; axis < 3; axis++) {
                for (int sideA = 0; sideA < 2; sideA++) {
                    for (int sideB = 0; sideB < 2; sideB++) {
                        addEdge(buffer, event.getModelViewMatrix(), range, axis, sideA != 0, sideB != 0);
                    }
                }
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previousShader);
        }
    }

    private static void addEdge(com.mojang.blaze3d.vertex.VertexConsumer buffer, Matrix4f matrix,
            AABB box, int axis, boolean sideA, boolean sideB) {
        double x = box.minX;
        double y = box.minY;
        double z = box.minZ;
        if (axis == 0) {
            y = sideA ? box.maxY : box.minY;
            z = sideB ? box.maxZ : box.minZ;
        } else if (axis == 1) {
            x = sideA ? box.maxX : box.minX;
            z = sideB ? box.maxZ : box.minZ;
        } else {
            x = sideA ? box.maxX : box.minX;
            y = sideB ? box.maxY : box.minY;
        }
        buffer.addVertex(matrix, (float) x, (float) y, (float) z).setColor(0.90F, 0.97F, 0.96F, 0.28F);
        if (axis == 0) {
            x = box.maxX;
        } else if (axis == 1) {
            y = box.maxY;
        } else {
            z = box.maxZ;
        }
        buffer.addVertex(matrix, (float) x, (float) y, (float) z).setColor(0.90F, 0.97F, 0.96F, 0.28F);
    }

    private static void renderFace(RenderLevelStageEvent event) {
        Minecraft client = Minecraft.getInstance();
        BlockPos position = selected.getBlockPos();
        var boxes = client.level.getBlockState(position).getShape(client.level, position,
                CollisionContext.of(client.player)).toAabbs();
        Direction.Axis axis = selected.getDirection().getAxis();
        int normal = axis == Direction.Axis.X ? 0 : axis == Direction.Axis.Y ? 1 : 2;
        int horizontal = (normal + 1) % 3;
        int vertical = (normal + 2) % 3;
        int sign = selected.getDirection().getAxisDirection().getStep();
        Vec3 local = selected.getLocation().subtract(Vec3.atLowerCornerOf(position));
        double plane = coordinate(local, normal);
        var faces = FreecamGeometry.faces(boxes, normal, sign, plane);
        if (faces.isEmpty()) {
            return;
        }
        float alpha = (float) (0.15 + 0.07 * Math.sin(System.nanoTime() / 1.0e9 * Math.PI));
        Vec3 offset = Vec3.atLowerCornerOf(position).subtract(event.getCamera().getPosition());
        var previousShader = RenderSystem.getShader();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (double[] face : faces) {
                for (int corner = 0; corner < 4; corner++) {
                    double[] point = new double[3];
                    point[normal] = plane + sign * 0.002;
                    point[horizontal] = corner == 1 || corner == 2 ? face[2] : face[0];
                    point[vertical] = corner >= 2 ? face[3] : face[1];
                    buffer.addVertex(event.getModelViewMatrix(), (float) (point[0] + offset.x),
                            (float) (point[1] + offset.y), (float) (point[2] + offset.z)).setColor(1, 1, 1, alpha);
                }
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previousShader);
        }
    }

    private static double coordinate(Vec3 point, int axis) { return axis == 0 ? point.x : axis == 1 ? point.y : point.z; }
}
