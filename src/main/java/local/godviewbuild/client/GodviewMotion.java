package local.godviewbuild.client;

public final class GodviewMotion {
    private GodviewMotion() {}

    public record Offset(double x, double z) {}

    public static Offset pan(double yaw, double forward, double right, double elapsed) {
        double length = Math.hypot(forward, right);
        if (length == 0 || !Double.isFinite(length) || !Double.isFinite(yaw) || !Double.isFinite(elapsed)) {
            return new Offset(0, 0);
        }
        double angle = Math.toRadians(yaw);
        double distance = 10.0 * Math.clamp(elapsed, 0, 0.05) / length;
        return new Offset((-Math.sin(angle) * forward - Math.cos(angle) * right) * distance,
                (Math.cos(angle) * forward - Math.sin(angle) * right) * distance);
    }

    public static double vertical(double up, double elapsed) {
        if (up == 0 || !Double.isFinite(up) || !Double.isFinite(elapsed)) {
            return 0;
        }
        return Math.signum(up) * 10.0 * Math.clamp(elapsed, 0, 0.05);
    }
}
