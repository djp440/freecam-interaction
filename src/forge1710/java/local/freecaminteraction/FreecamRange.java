package local.freecaminteraction;

public final class FreecamRange {
    public static final int LEGACY_SIZE = 16;

    private FreecamRange() {}

    public static int floor(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }

    public static int centerChunk(double coordinate) {
        return floor(coordinate / 16.0);
    }

    public static int minBlock(int chunkCoord, int radius) {
        return (chunkCoord - radius) * 16;
    }

    public static int maxBlock(int chunkCoord, int radius) {
        return (chunkCoord + radius + 1) * 16;
    }

    public static int minimumBlock(double playerCoordinate) {
        if (!Double.isFinite(playerCoordinate)) {
            throw new IllegalArgumentException("Player coordinate must be finite");
        }
        return (int) Math.ceil(playerCoordinate - 8.5);
    }

    public static boolean contains(Object world, WandTier tier, double playerX, double playerY, double playerZ,
            double blockX, double blockY, double blockZ) {
        int by = floor(blockY + 0.5);
        if (by < 0 || by >= 256) return false;

        int bx = floor(blockX + 0.5);
        int bz = floor(blockZ + 0.5);

        int cx = centerChunk(playerX);
        int cz = centerChunk(playerZ);
        int r = tier == null ? WandTier.NORMAL.radius : tier.radius;

        int minX = minBlock(cx, r);
        int maxX = maxBlock(cx, r);
        int minZ = minBlock(cz, r);
        int maxZ = maxBlock(cz, r);

        if (bx >= minX && bx < maxX && bz >= minZ && bz < maxZ) {
            return true;
        }

        if (tier == WandTier.CREATIVE && world instanceof net.minecraft.world.World) {
            return ((net.minecraft.world.World) world).blockExists(bx, by, bz);
        }

        return false;
    }

    public static boolean contains(double playerX, double playerY, double playerZ,
            double blockX, double blockY, double blockZ) {
        if (!Double.isFinite(playerX) || !Double.isFinite(playerY) || !Double.isFinite(playerZ)
                || !Double.isFinite(blockX) || !Double.isFinite(blockY) || !Double.isFinite(blockZ)) {
            return false;
        }
        return contains(null, WandTier.NORMAL, playerX, playerY, playerZ, blockX, blockY, blockZ);
    }

    public static double cameraReach(WandTier tier) {
        int r = tier == null ? WandTier.NORMAL.radius : tier.radius;
        return (2 * r + 1) * 12.0; // 范围 +50%
    }

    public static double cameraReach() {
        return cameraReach(WandTier.NORMAL);
    }

    public static double clampCamera(double playerCoordinate, double cameraCoordinate, WandTier tier) {
        if (!Double.isFinite(playerCoordinate) || !Double.isFinite(cameraCoordinate)) {
            return playerCoordinate;
        }
        double reach = cameraReach(tier);
        double min = Math.max(-29999984.0, playerCoordinate - reach);
        double max = Math.min(29999984.0, playerCoordinate + reach);
        return Math.max(min, Math.min(max, cameraCoordinate));
    }

    public static double clampCamera(double playerCoordinate, double cameraCoordinate) {
        return clampCamera(playerCoordinate, cameraCoordinate, WandTier.NORMAL);
    }

    public static double clampCameraY(double cameraY) {
        if (!Double.isFinite(cameraY)) return 64.0;
        return Math.max(0.5, Math.min(256.0, cameraY));
    }
}
