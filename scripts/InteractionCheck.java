import java.util.List;
import local.godviewbuild.GodviewGeometry;
import local.godviewbuild.GodviewRange;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

class InteractionCheck {
    public static void main(String[] args) {
        for (double center : new double[]{0, 0.5, -0.5, 12.125, -1000.75}) {
            int count = 0;
            for (int block = (int) Math.floor(center) - 10; block < center + 10; block++) {
                if (GodviewRange.contains(center, center, center, block, block, block)) {
                    count++;
                }
            }
            assert count == 16 : count;
        }
        assert GodviewRange.contains(0.5, 0.5, 0.5, -8, -8, -8);
        assert GodviewRange.contains(0.5, 0.5, 0.5, 7, 7, 7);
        assert !GodviewRange.contains(0.5, 0.5, 0.5, 8, 0, 0);
        assert !GodviewRange.contains(0.5, 0.5, 0.5, 0, -9, 0);
        assert !GodviewRange.contains(0.5, 0.5, 0.5, 0, 0, 8);
        assert !GodviewRange.contains(Double.NaN, 0, 0, 0, 0, 0);
        assert !GodviewRange.contains(Double.POSITIVE_INFINITY, 0, 0, 0, 0, 0);
        assert GodviewRange.minimumBlock(0.5) == -8;
        assert GodviewRange.minimumBlock(0.5001) == -7;
        assert GodviewRange.minimumBlock(-0.5) == -9;
        assert GodviewRange.minimumBlock(-0.4999) == -8;
        try {
            GodviewRange.minimumBlock(Double.NaN);
            throw new AssertionError("NaN range origin accepted");
        } catch (IllegalArgumentException expected) {}
        Matrix4f inverse = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, 0.05f, 256).invert();
        Vec3 center = GodviewGeometry.direction(inverse, 0, 0);
        assert center.distanceTo(new Vec3(0, 0, -1)) < 1e-6;
        assert GodviewGeometry.direction(inverse, 0.8f, 0).x > 0;
        assert GodviewGeometry.direction(inverse, 0, 0.8f).y > 0;
        Matrix4f rotated = new Matrix4f().perspective((float) Math.toRadians(70), 1, 0.05f, 256)
                .rotateY((float) Math.PI / 2).invert();
        assert Math.abs(GodviewGeometry.direction(rotated, 0, 0).x - 1) < 1e-6;
        AABB cube = new AABB(0, 0, 0, 1, 1, 1);
        assert cube.clip(new Vec3(0.5, 2, 0.5), new Vec3(0.5, -2, 0.5)).orElseThrow()
                .equals(new Vec3(0.5, 1, 0.5));
        for (int axis = 0; axis < 3; axis++) {
            assert area(GodviewGeometry.faces(List.of(cube), axis, 1, 1)) == 1;
            assert area(GodviewGeometry.faces(List.of(cube), axis, -1, 0)) == 1;
        }
        AABB slab = new AABB(0, 0, 0, 1, 0.5, 1);
        AABB step = new AABB(0.5, 0.5, 0, 1, 1, 1);
        assert area(GodviewGeometry.faces(List.of(slab), 1, 1, 0.5)) == 1;
        assert area(GodviewGeometry.faces(List.of(slab, step), 1, 1, 0.5)) == 0.5;
        assert area(GodviewGeometry.faces(List.of(slab, step), 1, 1, 1)) == 0.5;
        assert GodviewGeometry.faces(List.of(), 1, 1, 1).isEmpty();

        // GodviewCollision 几何与映射测试
        Vec3 b = local.godviewbuild.client.GodviewCollision.backwardVector(0, 0);
        assert Math.abs(b.x) < 1e-9 && Math.abs(b.y) < 1e-9 && Math.abs(b.z - local.godviewbuild.client.GodviewCollision.DISTANCE) < 1e-9 : "0角度后退向量应为 (0, 0, DISTANCE)";
        Vec3 c = local.godviewbuild.client.GodviewCollision.cameraPos(10, 20, 30, 0, 0);
        assert Math.abs(c.x - 10) < 1e-9 && Math.abs(c.y - 20) < 1e-9 && Math.abs(c.z - (30 + local.godviewbuild.client.GodviewCollision.DISTANCE)) < 1e-9;
        Vec3 a = local.godviewbuild.client.GodviewCollision.anchorPos(c.x, c.y, c.z, 0, 0);
        assert Math.abs(a.x - 10) < 1e-9 && Math.abs(a.y - 20) < 1e-9 && Math.abs(a.z - 30) < 1e-9 : "反求锚点与原锚点应严格一致";

        // 无障碍平移与求解测试
        Vec3 move = local.godviewbuild.client.GodviewCollision.solveTranslation(null, c.x, c.y, c.z, 0.5, 0.2, -0.1);
        assert Math.abs(move.x - 0.5) < 1e-9 && Math.abs(move.y - 0.2) < 1e-9 && Math.abs(move.z - (-0.1)) < 1e-9;
        float[] rot = local.godviewbuild.client.GodviewCollision.solveRotation(null, 10, 20, 30, 0, 0, 45, 10);
        assert Math.abs(rot[0] - 45) < 1e-5 && Math.abs(rot[1] - 10) < 1e-5;

        // 包围盒尺寸与边缘测试
        AABB box = local.godviewbuild.client.GodviewCollision.cameraBox(0, 0, 0);
        assert Math.abs(box.getXsize() - 0.40) < 1e-9 && Math.abs(box.getYsize() - 0.40) < 1e-9 && Math.abs(box.getZsize() - 0.40) < 1e-9;

        System.out.println("Interaction checks passed: range edges, negative coordinates, invalid values, rays, exposed cube/slab/stair faces and GodviewCollision geometry.");
    }

    private static double area(List<double[]> faces) {
        return faces.stream().mapToDouble(face -> (face[2] - face[0]) * (face[3] - face[1])).sum();
    }
}
