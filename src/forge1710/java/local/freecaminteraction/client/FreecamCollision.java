package local.freecaminteraction.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * 自由视角镜头局部碰撞求解器（方案 A）。
 * 明确区分逻辑锚点 A 与光学镜头 C，保持 C = A + B(R) 正反向映射，
 * 仅对镜头自身扫过的空间做 swept broadphase/narrowphase 轴向滑动求解与 3D 圆弧旋转检测。
 */
public final class FreecamCollision {
    public static final double DISTANCE = 13.856406;
    public static final double CAMERA_RADIUS = 0.20;

    private FreecamCollision() {}

    /**
     * 计算由朝向 R=(yaw, pitch) 决定的固定后退向量 B(R)。
     * 与原版 EntityRenderer.orientCamera 在 thirdPersonView==1 时的后退几何完全等价。
     */
    public static double[] backwardVector(float yaw, float pitch) {
        double radYaw = Math.toRadians((double) yaw);
        double radPitch = Math.toRadians((double) pitch);
        double cosPitch = Math.cos(radPitch);
        double bx = Math.sin(radYaw) * cosPitch * DISTANCE;
        double by = Math.sin(radPitch) * DISTANCE;
        double bz = -Math.cos(radYaw) * cosPitch * DISTANCE;
        return new double[] { bx, by, bz };
    }

    /**
     * 由逻辑锚点 A 与朝向 R 计算机械/光学镜头中心 C = A + B(R)。
     */
    public static Vec3 cameraPos(double anchorX, double anchorY, double anchorZ, float yaw, float pitch) {
        double[] b = backwardVector(yaw, pitch);
        return Vec3.createVectorHelper(anchorX + b[0], anchorY + b[1], anchorZ + b[2]);
    }

    /**
     * 由光学镜头中心 C 与朝向 R 反求逻辑锚点 A = C - B(R)。
     */
    public static Vec3 anchorPos(double camX, double camY, double camZ, float yaw, float pitch) {
        double[] b = backwardVector(yaw, pitch);
        return Vec3.createVectorHelper(camX - b[0], camY - b[1], camZ - b[2]);
    }

    /**
     * 获取镜头体包围盒。
     */
    public static AxisAlignedBB cameraBox(double camX, double camY, double camZ) {
        return AxisAlignedBB.getBoundingBox(
                camX - CAMERA_RADIUS, camY - CAMERA_RADIUS, camZ - CAMERA_RADIUS,
                camX + CAMERA_RADIUS, camY + CAMERA_RADIUS, camZ + CAMERA_RADIUS
        );
    }

    /**
     * 查询指定包围盒内的所有方块碰撞盒，未加载区块作为边界阻挡。
     * 若 checkLiquidsAsFloor 为 true，将液体顶面作为支撑盒加入，防止镜头下潜穿透水面/熔岩。
     */
    public static List<AxisAlignedBB> getCollisionBoxes(World world, AxisAlignedBB box, boolean checkLiquidsAsFloor) {
        List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
        if (world == null || box == null) return boxes;
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX + 1.0D);
        int minY = MathHelper.floor_double(box.minY);
        int maxY = MathHelper.floor_double(box.maxY + 1.0D);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ + 1.0D);

        for (int x = minX; x < maxX; ++x) {
            for (int z = minZ; z < maxZ; ++z) {
                if (!world.blockExists(x, 64, z)) {
                    // 未加载区块边缘阻挡
                    boxes.add(AxisAlignedBB.getBoundingBox(x, minY, z, x + 1.0D, maxY, z + 1.0D));
                    continue;
                }
                for (int y = minY - 1; y < maxY; ++y) {
                    Block block;
                    if (x >= -30000000 && x < 30000000 && z >= -30000000 && z < 30000000) {
                        block = world.getBlock(x, y, z);
                    } else {
                        block = Blocks.bedrock;
                    }
                    if (block == null || block.isAir(world, x, y, z)) continue;

                    block.addCollisionBoxesToList(world, x, y, z, box, boxes, (Entity) null);

                    if (checkLiquidsAsFloor && block.getMaterial().isLiquid()) {
                        boxes.add(AxisAlignedBB.getBoundingBox(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
                    }
                }
            }
        }
        return boxes;
    }

    /**
     * 平移碰撞与贴墙滑动求解（Swept Narrowphase）。
     * 复用 Minecraft 原生 AxisAlignedBB calculateYOffset / calculateXOffset / calculateZOffset。
     */
    public static double[] solveTranslation(World world, double camX, double camY, double camZ,
                                            double deltaX, double deltaY, double deltaZ) {
        if (Math.abs(deltaX) < 1e-9 && Math.abs(deltaY) < 1e-9 && Math.abs(deltaZ) < 1e-9) {
            return new double[] { 0.0, 0.0, 0.0 };
        }
        AxisAlignedBB box = cameraBox(camX, camY, camZ);
        AxisAlignedBB broadphase = box.addCoord(deltaX, deltaY, deltaZ);
        List<AxisAlignedBB> collidingBoxes = getCollisionBoxes(world, broadphase, deltaY < 0);

        double y = deltaY;
        for (int i = 0; i < collidingBoxes.size(); ++i) {
            y = collidingBoxes.get(i).calculateYOffset(box, y);
        }
        box.offset(0.0D, y, 0.0D);

        double x = deltaX;
        for (int i = 0; i < collidingBoxes.size(); ++i) {
            x = collidingBoxes.get(i).calculateXOffset(box, x);
        }
        box.offset(x, 0.0D, 0.0D);

        double z = deltaZ;
        for (int i = 0; i < collidingBoxes.size(); ++i) {
            z = collidingBoxes.get(i).calculateZOffset(box, z);
        }
        box.offset(0.0D, 0.0D, z);

        return new double[] { x, y, z };
    }

    /**
     * 绕锚点的 3D 圆弧步进防穿旋转求解。
     * 按弧长计算细分微步，遇阻截停在最后安全角度。
     */
    public static float[] solveRotation(World world, double anchorX, double anchorY, double anchorZ,
                                        float currYaw, float currPitch, float targetYaw, float targetPitch) {
        if (Math.abs(targetYaw - currYaw) < 1e-5 && Math.abs(targetPitch - currPitch) < 1e-5) {
            return new float[] { targetYaw, targetPitch };
        }
        double dyaw = Math.toRadians((double) (targetYaw - currYaw));
        double dpitch = Math.toRadians((double) (targetPitch - currPitch));
        double angDist = Math.hypot(dyaw, dpitch);
        double arcLen = DISTANCE * angDist;

        int steps = Math.max(1, Math.min(16, (int) Math.ceil(arcLen / (CAMERA_RADIUS * 1.5))));
        float acceptedYaw = currYaw;
        float acceptedPitch = currPitch;

        for (int step = 1; step <= steps; step++) {
            double t = (double) step / steps;
            float testYaw = currYaw + (targetYaw - currYaw) * (float) t;
            float testPitch = currPitch + (targetPitch - currPitch) * (float) t;
            Vec3 testCam = cameraPos(anchorX, anchorY, anchorZ, testYaw, testPitch);
            AxisAlignedBB testBox = cameraBox(testCam.xCoord, testCam.yCoord, testCam.zCoord);
            List<AxisAlignedBB> coll = getCollisionBoxes(world, testBox, false);

            boolean collided = false;
            for (int i = 0; i < coll.size(); i++) {
                if (coll.get(i).intersectsWith(testBox)) {
                    collided = true;
                    break;
                }
            }
            if (collided) {
                return new float[] { acceptedYaw, acceptedPitch };
            }
            acceptedYaw = testYaw;
            acceptedPitch = testPitch;
        }
        return new float[] { targetYaw, targetPitch };
    }

    /**
     * 检查当前镜头位置是否无障碍阻挡。
     */
    public static boolean isCameraClear(World world, double camX, double camY, double camZ) {
        AxisAlignedBB box = cameraBox(camX, camY, camZ);
        List<AxisAlignedBB> coll = getCollisionBoxes(world, box, false);
        for (int i = 0; i < coll.size(); i++) {
            if (coll.get(i).intersectsWith(box)) return false;
        }
        return true;
    }
}