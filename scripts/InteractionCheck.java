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
        assert GodviewGeometry.faces(List.of(slab, step), 1, -1, 0.5).isEmpty();
        assert GodviewGeometry.faces(List.of(), 1, 1, 1).isEmpty();
        System.out.println("Interaction checks passed: range edges, negative coordinates, invalid values, rays and exposed cube/slab/stair faces.");
    }

    private static double area(List<double[]> faces) {
        return faces.stream().mapToDouble(face -> (face[2] - face[0]) * (face[3] - face[1])).sum();
    }
}
