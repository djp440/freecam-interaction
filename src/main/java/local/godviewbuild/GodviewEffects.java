package local.godviewbuild;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** 服务端权威的自由视角状态与交互指示效果。 */
public final class GodviewEffects {
    private static final Map<ServerPlayer, Set<BlockPos>> PLACEMENTS = new WeakHashMap<>();

    private GodviewEffects() {}

    public static void emitAura(ServerPlayer player) {
        if ((player.tickCount & 1) != 0 || !GodviewInteraction.active(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + player.getBbHeight() * 0.5,
                player.getZ(), 2, player.getBbWidth() * 0.6, player.getBbHeight() * 0.55,
                player.getBbWidth() * 0.6, 0.02);
    }

    public static void beginBlockInteraction(ServerPlayer player) {
        PLACEMENTS.remove(player);
    }

    public static void recordPlacement(ServerPlayer player, BlockPos position) {
        PLACEMENTS.computeIfAbsent(player, ignored -> new LinkedHashSet<>()).add(position.immutable());
    }

    public static void finishBlockInteraction(ServerPlayer player, BlockPos fallback, boolean successful) {
        Set<BlockPos> placements = PLACEMENTS.remove(player);
        if (!successful || !GodviewInteraction.active(player)) {
            return;
        }
        if (placements == null || placements.isEmpty()) {
            emitInteraction(player.serverLevel(), Vec3.atCenterOf(fallback));
            return;
        }
        for (BlockPos position : placements) {
            emitParticles(player.serverLevel(), Vec3.atCenterOf(position));
        }
    }

    public static void emitBreak(ServerPlayer player, BlockPos position) {
        if (GodviewInteraction.active(player)) {
            emitInteraction(player.serverLevel(), Vec3.atCenterOf(position));
        }
    }

    public static void emitEntity(ServerPlayer player, Entity target) {
        if (!GodviewInteraction.active(player) || target.isRemoved() || target.level() != player.level()) {
            return;
        }
        emitInteraction(player.serverLevel(), target.getBoundingBox().getCenter());
    }

    private static void emitInteraction(ServerLevel level, Vec3 position) {
        emitParticles(level, position);
    }

    private static void emitParticles(ServerLevel level, Vec3 position) {
        level.sendParticles(ParticleTypes.PORTAL, position.x, position.y, position.z,
                24, 0.45, 0.45, 0.45, 0.08);
    }

}
