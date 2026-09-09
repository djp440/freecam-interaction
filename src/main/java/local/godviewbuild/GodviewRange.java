package local.godviewbuild;

public final class GodviewRange {
    public static final int SIZE = 16;

    private GodviewRange() {}

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
}
