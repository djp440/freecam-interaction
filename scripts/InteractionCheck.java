import java.util.List;
import local.freecaminteraction.FreecamGeometry;
import local.freecaminteraction.FreecamRange;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

class InteractionCheck {
    public static void main(String[] args) {
        for (double center : new double[]{0, 0.5, -0.5, 12.125, -1000.75}) {
            int count = 0;
            for (int block = (int) Math.floor(center) - 10; block < center + 10; block++) {
                if (FreecamRange.contains(center, center, center, block, block, block)) {
                    count++;
                }
            }
            assert count == 16 : count;
        }
        assert FreecamRange.contains(0.5, 0.5, 0.5, -8, -8, -8);
        assert FreecamRange.contains(0.5, 0.5, 0.5, 7, 7, 7);
        assert !FreecamRange.contains(0.5, 0.5, 0.5, 8, 0, 0);
        assert !FreecamRange.contains(0.5, 0.5, 0.5, 0, -9, 0);
        assert !FreecamRange.contains(0.5, 0.5, 0.5, 0, 0, 8);
        assert !FreecamRange.contains(Double.NaN, 0, 0, 0, 0, 0);
        assert !FreecamRange.contains(Double.POSITIVE_INFINITY, 0, 0, 0, 0, 0);
        assert FreecamRange.minimumBlock(0.5) == -8;
        assert FreecamRange.minimumBlock(0.5001) == -7;
        assert FreecamRange.minimumBlock(-0.5) == -9;
        assert FreecamRange.minimumBlock(-0.4999) == -8;
        try {
            FreecamRange.minimumBlock(Double.NaN);
            throw new AssertionError("NaN range origin accepted");
        } catch (IllegalArgumentException expected) {}
        Matrix4f inverse = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, 0.05f, 256).invert();
        Vec3 center = FreecamGeometry.direction(inverse, 0, 0);
        assert center.distanceTo(new Vec3(0, 0, -1)) < 1e-6;
        assert FreecamGeometry.direction(inverse, 0.8f, 0).x > 0;
        assert FreecamGeometry.direction(inverse, 0, 0.8f).y > 0;
        Matrix4f rotated = new Matrix4f().perspective((float) Math.toRadians(70), 1, 0.05f, 256)
                .rotateY((float) Math.PI / 2).invert();
        assert Math.abs(FreecamGeometry.direction(rotated, 0, 0).x - 1) < 1e-6;
        AABB cube = new AABB(0, 0, 0, 1, 1, 1);
        assert cube.clip(new Vec3(0.5, 2, 0.5), new Vec3(0.5, -2, 0.5)).orElseThrow()
                .equals(new Vec3(0.5, 1, 0.5));
        for (int axis = 0; axis < 3; axis++) {
            assert area(FreecamGeometry.faces(List.of(cube), axis, 1, 1)) == 1;
            assert area(FreecamGeometry.faces(List.of(cube), axis, -1, 0)) == 1;
        }
        AABB slab = new AABB(0, 0, 0, 1, 0.5, 1);
        AABB step = new AABB(0.5, 0.5, 0, 1, 1, 1);
        assert area(FreecamGeometry.faces(List.of(slab), 1, 1, 0.5)) == 1;
        assert area(FreecamGeometry.faces(List.of(slab, step), 1, 1, 0.5)) == 0.5;
        assert area(FreecamGeometry.faces(List.of(slab, step), 1, 1, 1)) == 0.5;
        assert FreecamGeometry.faces(List.of(slab, step), 1, -1, 0.5).isEmpty();
        assert FreecamGeometry.faces(List.of(), 1, 1, 1).isEmpty();
        var buffer = io.netty.buffer.Unpooled.buffer();
        try {
            var origin = new Vec3(1, 2, 3);
            var action = new local.freecaminteraction.FreecamActions.Action(2, 8, -1, origin, Vec3.ZERO, Vec3.ZERO);
            local.freecaminteraction.FreecamActions.Action.CODEC.encode(buffer, action);
            assert buffer.readableBytes() == 84;
            assert local.freecaminteraction.FreecamActions.Action.CODEC.decode(buffer).equals(action);
            try {
                local.freecaminteraction.FreecamActions.Action.CODEC.decode(buffer);
                throw new AssertionError("Truncated action accepted");
            } catch (IndexOutOfBoundsException expected) {}
            assert local.freecaminteraction.FreecamTarget.pick(null, null, origin) == null;
            assert local.freecaminteraction.FreecamTarget.pickBlock(null, null, origin,
                    net.minecraft.world.level.ClipContext.Fluid.NONE) == null;
            assert local.freecaminteraction.FreecamActions.bucketTarget(null) == null;
            assert local.freecaminteraction.FreecamTarget.pick(null, origin, origin) == null;
            assert local.freecaminteraction.FreecamTarget.pick(null, origin, new Vec3(Double.NaN, 0, 0)) == null;
            assert local.freecaminteraction.FreecamTarget.pick(null, origin, new Vec3(1000, 0, 0)) == null;
            assert FreecamRange.contains(0, 0, 0, -8.5, -0.5, -0.5);
            assert !FreecamRange.contains(0, 0, 0, 7.5, -0.5, -0.5);
        } finally { buffer.release(); }
        System.out.println("Interaction checks passed: range, rays, exposed faces, action codec and invalid payloads.");
    }

    private static double area(List<double[]> faces) {
        return faces.stream().mapToDouble(face -> (face[2] - face[0]) * (face[3] - face[1])).sum();
    }
}
