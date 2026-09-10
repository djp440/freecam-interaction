package local.freecaminteraction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** 客户端选取和服务端复核使用同一射线，不跳过范围外的前景遮挡。 */
public final class FreecamTarget {
    private FreecamTarget() {}

    public static boolean allowed(EntityPlayer player, Entity entity) {
        AxisAlignedBB box = entity.boundingBox;
        return entity != player && entity instanceof EntityLivingBase && entity.isEntityAlive()
                && entity.worldObj == player.worldObj && box != null
                && FreecamRange.contains(player.posX, player.boundingBox.minY, player.posZ,
                    (box.minX + box.maxX) / 2 - 0.5, (box.minY + box.maxY) / 2 - 0.5, (box.minZ + box.maxZ) / 2 - 0.5);
    }

    public static MovingObjectPosition pickBlock(EntityPlayer player, Vec3 start, Vec3 end, boolean liquids) {
        if (!validRay(player, start, end)) return null;
        MovingObjectPosition hit = player.worldObj.rayTraceBlocks(
                Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord),
                Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord), liquids);
        return hit != null && FreecamRange.contains(player.posX, player.boundingBox.minY, player.posZ,
                hit.blockX, hit.blockY, hit.blockZ) ? hit : null;
    }

    public static MovingObjectPosition pick(EntityPlayer player, Vec3 start, Vec3 end) {
        if (!validRay(player, start, end)) return null;
        // 1.7.10 World 射线步进会原地改写起点；实体检测和网络复核必须保留相机原点。
        MovingObjectPosition nearest = player.worldObj.rayTraceBlocks(
                Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord),
                Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord));
        double distance = nearest == null ? start.squareDistanceTo(end) : start.squareDistanceTo(nearest.hitVec);
        AxisAlignedBB search = AxisAlignedBB.getBoundingBox(Math.min(start.xCoord, end.xCoord), Math.min(start.yCoord, end.yCoord),
                Math.min(start.zCoord, end.zCoord), Math.max(start.xCoord, end.xCoord), Math.max(start.yCoord, end.yCoord),
                Math.max(start.zCoord, end.zCoord)).expand(2, 2, 2);
        for (Object value : player.worldObj.getEntitiesWithinAABBExcludingEntity(player, search)) {
            Entity entity = (Entity) value;
            if (!entity.canBeCollidedWith() || entity.boundingBox == null) continue;
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.boundingBox.expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(start, end);
            Vec3 point = box.isVecInside(start) ? start : intercept == null ? null : intercept.hitVec;
            if (point != null && start.squareDistanceTo(point) < distance) {
                distance = start.squareDistanceTo(point);
                nearest = new MovingObjectPosition(entity, point);
            }
        }
        if (nearest == null) return null;
        if (nearest.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) return allowed(player, nearest.entityHit) ? nearest : null;
        return FreecamRange.contains(player.posX, player.boundingBox.minY, player.posZ,
                nearest.blockX, nearest.blockY, nearest.blockZ) ? nearest : null;
    }

    private static boolean validRay(EntityPlayer player, Vec3 start, Vec3 end) {
        return finite(start) && finite(end) && start.squareDistanceTo(end) <= 257 * 257
                && start.squareDistanceTo(end) >= 1.0e-8 && player != null && player.worldObj != null;
    }

    public static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.xCoord) && Double.isFinite(vector.yCoord) && Double.isFinite(vector.zCoord);
    }
}
