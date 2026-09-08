package local.godviewbuild;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class GodviewGeometry {
    private GodviewGeometry() {}

    public static Vec3 direction(Matrix4f inverse, float mouseX, float mouseY) {
        Vector4f near = inverse.transform(new Vector4f(mouseX, mouseY, -1, 1));
        Vector4f far = inverse.transform(new Vector4f(mouseX, mouseY, 1, 1));
        near.div(near.w);
        far.div(far.w);
        return new Vec3(far.x - near.x, far.y - near.y, far.z - near.z).normalize();
    }

    public static List<double[]> faces(List<AABB> boxes, int normal, int sign, double plane) {
        int horizontal = (normal + 1) % 3;
        int vertical = (normal + 2) % 3;
        var widths = new TreeSet<Double>();
        var heights = new TreeSet<Double>();
        for (AABB box : boxes) {
            if (plane >= minimum(box, normal) - 1.0e-5 && plane <= maximum(box, normal) + 1.0e-5) {
                widths.add(minimum(box, horizontal));
                widths.add(maximum(box, horizontal));
                heights.add(minimum(box, vertical));
                heights.add(maximum(box, vertical));
            }
        }
        var columns = new ArrayList<>(widths);
        var rows = new ArrayList<>(heights);
        var faces = new ArrayList<double[]>();
        for (int column = 1; column < columns.size(); column++) {
            for (int row = 1; row < rows.size(); row++) {
                double[] point = new double[3];
                point[horizontal] = (columns.get(column - 1) + columns.get(column)) / 2;
                point[vertical] = (rows.get(row - 1) + rows.get(row)) / 2;
                point[normal] = plane - sign * 1.0e-5;
                boolean behind = boxes.stream().anyMatch(box -> box.contains(point[0], point[1], point[2]));
                point[normal] = plane + sign * 1.0e-5;
                boolean ahead = boxes.stream().anyMatch(box -> box.contains(point[0], point[1], point[2]));
                if (behind && !ahead) {
                    faces.add(new double[]{columns.get(column - 1), rows.get(row - 1), columns.get(column), rows.get(row)});
                }
            }
        }
        return faces;
    }

    private static double minimum(AABB box, int axis) { return axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ; }
    private static double maximum(AABB box, int axis) { return axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ; }
}
