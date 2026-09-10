package local.freecaminteraction;

import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.CommonHooks;

/** 只传瞄准信息，实际目标、物品、权限与效果均由服务端决定。 */
public final class FreecamActions {
    public static final int ATTACK = 0, INTERACT = 1, FISH = 2, USE_BUCKET = 3;
    private static final Map<Player, long[]> LAST = new WeakHashMap<>();
    private static final ThreadLocal<BucketTarget> BUCKET_TARGET = new ThreadLocal<>();
    private FreecamActions() {}

    public record Action(int kind, int slot, int target, Vec3 start, Vec3 end, Vec3 point) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FreecamInteractionMod.MOD_ID, "action_v1"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.of((b, a) -> {
            b.writeInt(a.kind); b.writeInt(a.slot); b.writeInt(a.target);
            write(b, a.start); write(b, a.end); write(b, a.point);
        }, b -> new Action(b.readInt(), b.readInt(), b.readInt(), read(b), read(b), read(b)));
        @Override public Type<Action> type() { return TYPE; }
        private static void write(ByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
        private static Vec3 read(ByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    }

    public static void clear(Player player) { LAST.remove(player); }

    public static BlockHitResult bucketTarget(Player player) {
        BucketTarget target = BUCKET_TARGET.get();
        return target != null && target.player == player ? target.hit : null;
    }

    public static void handle(ServerPlayer player, Action action) {
        if (!FreecamInteraction.active(player) || player.isSpectator() || action.kind < ATTACK || action.kind > USE_BUCKET
                || action.slot < 0 || action.slot > 8 || player.getInventory().selected != action.slot
                || action.kind == USE_BUCKET && (action.target < 0 || action.target >= InteractionHand.values().length)
                || !FreecamTarget.finite(action.start) || !FreecamTarget.finite(action.end) || !FreecamTarget.finite(action.point)) {
            reject(player, "state_or_payload"); return;
        }
        long now = player.level().getGameTime();
        long[] last = LAST.computeIfAbsent(player, p -> new long[] {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
        int index = action.kind == ATTACK ? 0 : 1;
        if (now - last[index] < (index == 0 ? 1 : 4)) return;
        last[index] = now;
        InteractionHand hand = action.kind == USE_BUCKET && action.target >= 0 && action.target < InteractionHand.values().length
                ? InteractionHand.values()[action.target] : InteractionHand.MAIN_HAND;
        ItemStack item = player.getItemInHand(hand);
        if (!item.isItemEnabled(player.level().enabledFeatures())) return;
        boolean retrieve = action.kind == FISH && item.getItem() instanceof FishingRodItem && player.fishing != null;
        HitResult hit = null;
        if (!retrieve) {
            // 相机锚点允许半径加原生第三人称臂长；不接受任意远处的射线起点。
            double cameraBound = Math.sqrt(3) * FreecamRange.cameraReach() + 16;
            if (action.start.distanceToSqr(player.position()) > cameraBound * cameraBound) { reject(player, "camera"); return; }
            if (action.kind == USE_BUCKET) {
                if (!(item.getItem() instanceof BucketItem bucket)) { reject(player, "not_bucket"); return; }
                hit = FreecamTarget.pickBlock(player, action.start, action.end,
                        bucket.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);
                if (hit == null || hit.getLocation().distanceToSqr(action.point) > 0.25) {
                    reject(player, "stale_or_occluded_bucket_target"); return;
                }
                BlockHitResult blockHit = (BlockHitResult) hit;
                if (bucket.content != Fluids.EMPTY) {
                    var state = player.level().getBlockState(blockHit.getBlockPos());
                    boolean contains = state.getBlock() instanceof LiquidBlockContainer container
                            && container.canPlaceLiquid(player, player.level(), blockHit.getBlockPos(), state, bucket.content);
                    if (!FreecamInteraction.allowed(player, contains ? blockHit.getBlockPos()
                            : blockHit.getBlockPos().relative(blockHit.getDirection()))) {
                        reject(player, "outside_bucket_destination"); return;
                    }
                }
            } else {
                hit = FreecamTarget.pick(player, action.start, action.end);
                int target = hit instanceof EntityHitResult entityHit ? entityHit.getEntity().getId() : -1;
                if (hit == null || target != action.target || hit.getLocation().distanceToSqr(action.point) > 0.25) {
                    reject(player, "stale_or_occluded_target"); return;
                }
            }
        }
        player.resetLastActionTime();
        if (action.kind == USE_BUCKET) {
            BUCKET_TARGET.set(new BucketTarget(player, (BlockHitResult) hit));
            try {
                player.gameMode.useItem(player, player.level(), item, hand);
            } finally {
                BUCKET_TARGET.remove();
            }
        } else if (action.kind == FISH) {
            if (!(item.getItem() instanceof FishingRodItem)) { reject(player, "not_fishing_rod"); return; }
            float yaw = player.getYRot(), pitch = player.getXRot();
            try {
                if (!retrieve) {
                    Vec3 direction = hit.getLocation().subtract(player.getEyePosition());
                    if (direction.lengthSqr() < 1.0e-8) return;
                    player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
                    player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
                }
                player.gameMode.useItem(player, player.level(), item, InteractionHand.MAIN_HAND);
            } finally { player.setYRot(yaw); player.setXRot(pitch); }
        } else if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
            if (action.kind == INTERACT) {
                for (InteractionHand entityHand : InteractionHand.values()) {
                    ItemStack held = player.getItemInHand(entityHand);
                    if (!held.isItemEnabled(player.level().enabledFeatures())) break;
                    ItemStack previous = held.copy();
                    Vec3 local = entityHit.getLocation().subtract(living.position());
                    InteractionResult result = CommonHooks.onInteractEntityAt(player, living, local, entityHand);
                    if (result == null) result = living.interactAt(player, local, entityHand);
                    if (!result.consumesAction()) result = player.interactOn(living, entityHand);
                    if (result.consumesAction()) {
                        CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(player, result.indicateItemUse() ? previous : ItemStack.EMPTY, living);
                        if (result.shouldSwing()) player.swing(entityHand, true);
                        break;
                    }
                    if (result == InteractionResult.FAIL) break;
                }
            } else {
                if (living instanceof Player other && !player.canHarmPlayer(other)) return;
                if (player.canInteractWithEntity(living, 0)) {
                    player.attack(living);
                } else if (living.isAttackable() && !living.isInvulnerableTo(player.damageSources().playerAttack(player))
                        && living.hurtTime == 0 && !net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                                new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(player, living)).isCanceled()) {
                    // 不调用 hurt/attack，不写入伤害来源、仇恨、附魔或武器命中状态。
                    living.knockback(0.4, player.getX() - living.getX(), player.getZ() - living.getZ());
                    living.hurtMarked = true;
                    living.animateHurt(0);
                    player.serverLevel().getChunkSource().broadcastAndSend(living,
                            new ClientboundDamageEventPacket(living, living.damageSources().generic()));
                    player.resetAttackStrengthTicker();
                }
                player.swing(InteractionHand.MAIN_HAND, true);
            }
        } else { reject(player, "not_living_target"); return; }
        player.inventoryMenu.broadcastChanges();
        ModLog.LOGGER.debug("Freecam action={}; player={}; target={}", action.kind, player.getUUID(), action.target);
    }

    private record BucketTarget(Player player, BlockHitResult hit) {}

    private static void reject(ServerPlayer player, String reason) {
        ModLog.LOGGER.debug("Freecam action rejected; player={}; reason={}", player.getUUID(), reason);
    }
}
