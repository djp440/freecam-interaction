import java.util.Arrays;
import local.freecaminteraction.client.FreecamMotion;
import local.freecaminteraction.FreecamRange;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.WandTier;

public class LegacyCheck {
    public static void main(String[] args) {
        // 1. 相机运动纯数学校验
        double[] forward = FreecamMotion.pan(0, 1, 0, 0.05);
        assert Math.abs(forward[0]) < 1e-9 && Math.abs(forward[1] - 0.5) < 1e-9;
        double[] turned = FreecamMotion.pan(90, 1, 0, 0.05);
        assert Math.abs(turned[0] + 0.5) < 1e-9 && Math.abs(turned[1]) < 1e-9;
        double[] diagonal = FreecamMotion.pan(0, 1, 1, 0.05);
        assert Math.abs(Math.hypot(diagonal[0], diagonal[1]) - 0.5) < 1e-9;
        assert Arrays.equals(forward, FreecamMotion.pan(0, 1, 0, 99));
        assert FreecamMotion.pan(0, 1, 0, -1)[1] == 0;
        assert FreecamMotion.pan(Double.NaN, 1, 0, 0.05)[1] == 0;
        assert Math.abs(FreecamMotion.vertical(1, 0.05) - 0.5) < 1e-9;
        assert Math.abs(FreecamMotion.vertical(-1, 0.05) + 0.5) < 1e-9;
        assert FreecamMotion.vertical(0, 0.05) == 0;
        assert FreecamMotion.vertical(Double.NaN, 0.05) == 0;
        assert FreecamMotion.vertical(1, 99) == 0.5;

        // 2. 法杖等级与属性断言
        assert WandTier.NORMAL.radius == 2 && WandTier.NORMAL.maxDamage == 2048 && WandTier.NORMAL.repairAmount == 512;
        assert WandTier.ADVANCED.radius == 3 && WandTier.ADVANCED.maxDamage == 8192 && WandTier.ADVANCED.repairAmount == 2048;
        assert WandTier.CREATIVE.radius == 4 && WandTier.CREATIVE.maxDamage == 0 && WandTier.CREATIVE.repairAmount == 0;
        assert WandTier.fromOrdinal(0) == WandTier.NORMAL;
        assert WandTier.fromOrdinal(1) == WandTier.ADVANCED;
        assert WandTier.fromOrdinal(2) == WandTier.CREATIVE;
        assert WandTier.fromOrdinal(99) == WandTier.NORMAL;

        // 3. 区块与动态范围断言（负坐标 floor、下含上不含、世界高度）
        assert FreecamRange.centerChunk(0.0) == 0;
        assert FreecamRange.centerChunk(15.9) == 0;
        assert FreecamRange.centerChunk(16.0) == 1;
        assert FreecamRange.centerChunk(-0.1) == -1;
        assert FreecamRange.centerChunk(-16.0) == -1;
        assert FreecamRange.centerChunk(-16.1) == -2;

        // 玩家在 (8, 64, 8) -> chunk (0, 0)
        // 普通法杖 5x5: chunk [-2, 2], block [-32, 48)
        assert FreecamRange.contains(8, 64, 8, 0, 64, 0);
        assert FreecamRange.contains(8, 64, 8, -32, 0, -32);
        assert FreecamRange.contains(8, 64, 8, 47, 255, 47);
        assert !FreecamRange.contains(8, 64, 8, -33, 64, 0);
        assert !FreecamRange.contains(8, 64, 8, 48, 64, 0);
        assert !FreecamRange.contains(8, 64, 8, 0, -1, 0);
        assert !FreecamRange.contains(8, 64, 8, 0, 256, 0);
        assert !FreecamRange.contains(Double.NaN, 0, 0, 0, 0, 0);
        assert FreecamRange.minimumBlock(-0.5) == -9;

        // 高级法杖 7x7: chunk [-3, 3], block [-48, 64)
        assert FreecamRange.contains(null, WandTier.ADVANCED, 8, 64, 8, -48, 100, -48);
        assert FreecamRange.contains(null, WandTier.ADVANCED, 8, 64, 8, 63, 100, 63);
        assert !FreecamRange.contains(null, WandTier.ADVANCED, 8, 64, 8, -49, 100, 0);
        assert !FreecamRange.contains(null, WandTier.ADVANCED, 8, 64, 8, 64, 100, 0);

        // 4. 视距（范围 +50%）与相机限制
        assert Math.abs(FreecamRange.cameraReach(WandTier.NORMAL) - 60.0) < 1e-9;
        assert Math.abs(FreecamRange.cameraReach(WandTier.ADVANCED) - 84.0) < 1e-9;
        assert Math.abs(FreecamRange.cameraReach(WandTier.CREATIVE) - 108.0) < 1e-9;
        assert Math.abs(FreecamRange.clampCamera(100.0, 180.0, WandTier.NORMAL) - 160.0) < 1e-9;
        assert Math.abs(FreecamRange.clampCamera(100.0, 20.0, WandTier.NORMAL) - 40.0) < 1e-9;
        assert Math.abs(FreecamRange.clampCamera(100.0, 120.0, WandTier.NORMAL) - 120.0) < 1e-9;
        assert FreecamRange.clampCamera(100.0, Double.NaN, WandTier.NORMAL) == 100.0;
        assert Math.abs(FreecamRange.clampCameraY(300.0) - 256.0) < 1e-9;
        assert Math.abs(FreecamRange.clampCameraY(-10.0) - 0.5) < 1e-9;
        assert Math.abs(FreecamRange.clampCameraY(128.0) - 128.0) < 1e-9;

        ModLog.initialize();
        ModLog.initialize();
        ModLog.info("Legacy Java 8 smoke passed: motion, wand tiers, chunk range, and idempotent log initialization.");
    }
}
