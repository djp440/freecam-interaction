package local.godviewbuild.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 自由视角镜头局部碰撞求解器（方案 A）。
 * 明确区分逻辑锚点 A 与光学镜头 C，保持 C = A + B(R) 正反向映射，
 * 仅对镜头自身扫过的空间做 swept broadphase/narrowphase 轴向滑动求解与 3D 圆弧旋转检测。
 */
public final class GodviewCollision {
    public static final double DISTANCE = 13.856406460551018; // 12.0 / sin(60°)
    public static final double CAMERA_RADIUS = 0.20;

    private GodviewCollision() {}

    /**
     * 计算由朝向 R=(yaw, pitch) 决定的固定后退向量 B(R)。
     * 与 1.21.1 Camera.move(-distance, 0, 0) 在 rotation=(yaw, pitch) 下的后退几何完全等价。
     */
    public static Vec3 backwardVector(float yaw, float pitch) {
        float radYaw = yaw * ((float) Math.PI / 180.0F);
        float radPitch = pitch * ((float) Math.PI / 180.0F);
        float cosPitch = Mth.cos(radPitch);
        double bx = -Mth.sin(radYaw) * cosPitch * DISTANCE;
        double by = Mth.sin(radPitch) * DISTANCE;
        double bz = Mth.cos(radYaw) * cosPitch * DISTANCE;
        return new Vec3(bx, by, bz);
    }

    /**
     * 由逻辑锚点 A 与朝向 R 计算机械/光学镜头中心 C = A + B(R)。
     */
    public static Vec3 cameraPos(double anchorX, double anchorY, double anchorZ, float yaw, float pitch) {
        Vec3 b = backwardVector(yaw, pitch);
        return new Vec3(anchorX + b.x, anchorY + b.y, anchorZ + b.z);
    }

    /**
     * 由光学镜头中心 C 与朝向 R 反求逻辑锚点 A = C - B(R)。
     */
    public static Vec3 anchorPos(double camX, double camY, double camZ, float yaw, float pitch) {
        Vec3 b = backwardVector(yaw, pitch);
        return new Vec3(camX - b.x, camY - b.y, camZ - b.z);
    }

    /**
     * 获取镜头体包围盒。
     */
    public static AABB cameraBox(double camX, double camY, double camZ) {
        return new AABB(
                camX - CAMERA_RADIUS, camY - CAMERA_RADIUS, camZ - CAMERA_RADIUS,
                camX + CAMERA_RADIUS, camY + CAMERA_RADIUS, camZ + CAMERA_RADIUS
        );
    }

    /**
     * 查询指定包围盒内的所有方块碰撞盒，未加载区块作为边界阻挡。
     * 若 checkLiquidsAsFloor 为 true，将液体顶面作为支撑盒加入，防止镜头下潜穿透水面/熔岩。
     */
    public static List<VoxelShape> getCollisionShapes(Level level, AABB box, boolean checkLiquidsAsFloor) {
        List<VoxelShape> shapes = new ArrayList<>();
        if (level == null || box == null) return shapes;
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX + 1.0D);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY + 1.0D);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ + 1.0D);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        CollisionContext context = CollisionContext.empty();

        for (int x = minX; x < maxX; ++x) {
            for (int z = minZ; z < maxZ; ++z) {
                if (!level.hasChunkAt(pos.set(x, 64, z))) {
                    // 未加载区块边缘阻挡
                    shapes.add(Shapes.create(new AABB(x, minY, z, x + 1.0D, maxY, z + 1.0D)));
                    continue;
                }
                for (int y = minY - 1; y < maxY; ++y) {
                    pos.set(x, y, z);
                    BlockState state;
                    if (x >= -30000000 && x < 30000000 && z >= -30000000 && z < 30000000) {
                        state = level.getBlockState(pos);
                    } else {
                        state = Blocks.BEDROCK.defaultBlockState();
                    }
                    if (state.isAir()) continue;

                    VoxelShape collision = state.getCollisionShape(level, pos, context);
                    if (!collision.isEmpty()) {
                        shapes.add(collision.move(x, y, z));
                    }

                    if (checkLiquidsAsFloor && !state.getFluidState().isEmpty()) {
                        shapes.add(Shapes.create(new AABB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D)));
                    }
                }
            }
        }
        return shapes;
    }

    /**
     * 平移碰撞与贴墙滑动求解（Swept Narrowphase）。
     * 复用 Shapes.collide 按 Y-X-Z 轴向滑动。
     */
    public static Vec3 solveTranslation(Level level, double camX, double camY, double camZ,
                                        double deltaX, double deltaY, double deltaZ) {
        if (Math.abs(deltaX) < 1e-9 && Math.abs(deltaY) < 1e-9 && Math.abs(deltaZ) < 1e-9) {
            return Vec3.ZERO;
        }
        AABB box = cameraBox(camX, camY, camZ);
        AABB broadphase = box.expandTowards(deltaX, deltaY, deltaZ);
        List<VoxelShape> colliders = getCollisionShapes(level, broadphase, deltaY < 0);
        if (colliders.isEmpty()) {
            return new Vec3(deltaX, deltaY, deltaZ);
        }

        double y = deltaY;
        if (Math.abs(y) > 1e-9) {
            y = Shapes.collide(Direction.Axis.Y, box, colliders, y);
            box = box.move(0.0D, y, 0.0D);
        }

        double x = deltaX;
        if (Math.abs(x) > 1e-9) {
            x = Shapes.collide(Direction.Axis.X, box, colliders, x);
            box = box.move(x, 0.0D, 0.0D);
        }

        double z = deltaZ;
        if (Math.abs(z) > 1e-9) {
            z = Shapes.collide(Direction.Axis.Z, box, colliders, z);
        }

        return new Vec3(x, y, z);
    }

    /**
     * 绕锚点的 3D 圆弧步进防穿旋转求解。
     * 按弧长计算细分微步，遇阻截停在最后安全角度。
     */
    public static float[] solveRotation(Level level, double anchorX, double anchorY, double anchorZ,
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
            AABB testBox = cameraBox(testCam.x, testCam.y, testCam.z);
            List<VoxelShape> coll = getCollisionShapes(level, testBox, false);

            boolean collided = false;
            for (int i = 0; i < coll.size(); i++) {
                if (Shapes.joinIsNotEmpty(coll.get(i), Shapes.create(testBox), net.minecraft.world.phys.shapes.BooleanOp.AND)) {
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
    public static boolean isCameraClear(Level level, double camX, double camY, double camZ) {
        AABB box = cameraBox(camX, camY, camZ);
        List<VoxelShape> coll = getCollisionShapes(level, box, false);
        for (int i = 0; i < coll.size(); i++) {
            if (Shapes.joinIsNotEmpty(coll.get(i), Shapes.create(box), net.minecraft.world.phys.shapes.BooleanOp.AND)) {
                return false;
            }
        }
        return true;
    }
}