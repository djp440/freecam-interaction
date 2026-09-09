package local.freecaminteraction.client;

public final class FreecamMotion {
    private FreecamMotion() {}

    public static double[] pan(double yaw, double forward, double right, double elapsed) {
        double length = Math.hypot(forward, right);
        if (length == 0 || !Double.isFinite(length) || !Double.isFinite(yaw) || !Double.isFinite(elapsed)) {
            return new double[] {0, 0};
        }
        double angle = Math.toRadians(yaw);
        double distance = 10.0 * Math.max(0, Math.min(elapsed, 0.05)) / length;
        return new double[] {(-Math.sin(angle) * forward - Math.cos(angle) * right) * distance,
                (Math.cos(angle) * forward - Math.sin(angle) * right) * distance};
    }
}
