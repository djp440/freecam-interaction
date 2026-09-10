package local.freecaminteraction;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** 客户端选取和服务端复核使用同一射线，不跳过范围外的前景遮挡。 */
public final class FreecamTarget {
    private FreecamTarget() {}

    public static boolean allowed(Player player, Entity entity) {
        Vec3 center = entity.getBoundingBox().getCenter();
        return entity != player && entity instanceof LivingEntity && entity.isAlive()
                && entity.level() == player.level()
                && FreecamRange.contains(player.getX(), player.getY(), player.getZ(), center.x - 0.5, center.y - 0.5, center.z - 0.5)
                && player.level().getWorldBorder().isWithinBounds(entity.blockPosition());
    }

    public static BlockHitResult pickBlock(Player player, Vec3 start, Vec3 end, ClipContext.Fluid fluid) {
        if (!validRay(player, start, end)) return null;
        BlockHitResult hit = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, fluid, player));
        return hit.getType() == HitResult.Type.BLOCK && FreecamInteraction.allowed(player, hit.getBlockPos()) ? hit : null;
    }

    public static HitResult pick(Player player, Vec3 start, Vec3 end) {
        if (!validRay(player, start, end)) return null;
        BlockHitResult block = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        HitResult nearest = block.getType() == HitResult.Type.BLOCK ? block : null;
        double distance = nearest == null ? start.distanceToSqr(end) : start.distanceToSqr(nearest.getLocation());
        for (Entity entity : player.level().getEntities(player, new AABB(start, end).inflate(1), Entity::isPickable)) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            Vec3 point = box.contains(start) ? start : box.clip(start, end).orElse(null);
            if (point != null && start.distanceToSqr(point) < distance) {
                distance = start.distanceToSqr(point);
                nearest = new EntityHitResult(entity, point);
            }
        }
        if (nearest instanceof EntityHitResult hit) return allowed(player, hit.getEntity()) ? hit : null;
        return nearest instanceof BlockHitResult hit && FreecamInteraction.allowed(player, hit.getBlockPos()) ? hit : null;
    }

    private static boolean validRay(Player player, Vec3 start, Vec3 end) {
        if (player == null || !finite(start) || !finite(end) || start.distanceToSqr(end) > 257 * 257
                || start.distanceToSqr(end) < 1.0e-8) return false;
        for (int x = ((int) Math.floor(Math.min(start.x, end.x))) >> 4; x <= ((int) Math.floor(Math.max(start.x, end.x))) >> 4; x++) {
            for (int z = ((int) Math.floor(Math.min(start.z, end.z))) >> 4; z <= ((int) Math.floor(Math.max(start.z, end.z))) >> 4; z++) {
                if (!player.level().hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    public static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
