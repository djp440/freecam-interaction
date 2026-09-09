package local.freecaminteraction;

public final class FreecamRange {
    public static final int SIZE = 16;

    private FreecamRange() {}

    public static boolean contains(double playerX, double playerY, double playerZ,
            double blockX, double blockY, double blockZ) {
        return inside(blockX + 0.5 - playerX) && inside(blockY + 0.5 - playerY)
                && inside(blockZ + 0.5 - playerZ);
    }

    private static boolean inside(double offset) {
        return offset >= -8.0 && offset < 8.0;
    }

    public static int minimumBlock(double playerCoordinate) {
        if (!Double.isFinite(playerCoordinate)) {
            throw new IllegalArgumentException("Player coordinate must be finite");
        }
        return (int) Math.ceil(playerCoordinate - 8.5);
    }

    public static double cameraReach() {
        return (SIZE / 2.0) * 1.5;
    }

    public static double clampCamera(double playerCoordinate, double cameraCoordinate) {
        if (!Double.isFinite(playerCoordinate) || !Double.isFinite(cameraCoordinate)) {
            return playerCoordinate;
        }
        double reach = cameraReach();
        double min = Math.max(-29_999_984.0, playerCoordinate - reach);
        double max = Math.min(29_999_984.0, playerCoordinate + reach);
        return Math.max(min, Math.min(max, cameraCoordinate));
    }
}
