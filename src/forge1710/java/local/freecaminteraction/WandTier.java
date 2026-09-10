package local.freecaminteraction;

public enum WandTier {
    NORMAL("normal", 2, 2048, 512, "5x5"),
    ADVANCED("advanced", 3, 8192, 2048, "7x7"),
    CREATIVE("creative", 4, 0, 0, "9x9+");

    public final String id;
    public final int radius;
    public final int maxDamage;
    public final int repairAmount;
    public final String rangeText;

    WandTier(String id, int radius, int maxDamage, int repairAmount, String rangeText) {
        this.id = id;
        this.radius = radius;
        this.maxDamage = maxDamage;
        this.repairAmount = repairAmount;
        this.rangeText = rangeText;
    }

    public static WandTier fromOrdinal(int ordinal) {
        if (ordinal >= 0 && ordinal < values().length) {
            return values()[ordinal];
        }
        return NORMAL;
    }
}
