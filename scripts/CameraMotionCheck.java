import local.godviewbuild.client.GodviewMotion;

class CameraMotionCheck {
    public static void main(String[] args) {
        var forward = GodviewMotion.pan(0, 1, 0, 0.05);
        assert Math.abs(forward.x()) < 1e-9 && Math.abs(forward.z() - 0.5) < 1e-9;
        var right = GodviewMotion.pan(0, 0, 1, 0.05);
        assert Math.abs(right.x() + 0.5) < 1e-9 && Math.abs(right.z()) < 1e-9;
        var turned = GodviewMotion.pan(90, 1, 0, 0.05);
        assert Math.abs(turned.x() + 0.5) < 1e-9 && Math.abs(turned.z()) < 1e-9;
        var diagonal = GodviewMotion.pan(0, 1, 1, 0.05);
        assert Math.abs(Math.hypot(diagonal.x(), diagonal.z()) - 0.5) < 1e-9;
        assert GodviewMotion.pan(0, 0, 0, 0.05).equals(new GodviewMotion.Offset(0, 0));
        assert GodviewMotion.pan(0, 1, 0, 10).equals(forward);
        assert GodviewMotion.pan(0, 1, 0, 0.025).z() * 2 == forward.z();
        assert GodviewMotion.pan(Double.NaN, 1, 0, 0.05).equals(new GodviewMotion.Offset(0, 0));
        assert GodviewMotion.pan(0, 1, 0, -1).z() == 0;
        System.out.println("Camera motion checks passed: direction, diagonal, frame rate, idle and limits.");
    }
}
