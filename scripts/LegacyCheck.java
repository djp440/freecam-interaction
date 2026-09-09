import java.util.Arrays;
import local.freecaminteraction.client.FreecamMotion;
import local.freecaminteraction.FreecamRange;
import local.freecaminteraction.ModLog;

public class LegacyCheck {
    public static void main(String[] args) {
        double[] forward = FreecamMotion.pan(0, 1, 0, 0.05);
        assert Math.abs(forward[0]) < 1e-9 && Math.abs(forward[1] - 0.5) < 1e-9;
        double[] turned = FreecamMotion.pan(90, 1, 0, 0.05);
        assert Math.abs(turned[0] + 0.5) < 1e-9 && Math.abs(turned[1]) < 1e-9;
        double[] diagonal = FreecamMotion.pan(0, 1, 1, 0.05);
        assert Math.abs(Math.hypot(diagonal[0], diagonal[1]) - 0.5) < 1e-9;
        assert Arrays.equals(forward, FreecamMotion.pan(0, 1, 0, 99));
        assert FreecamMotion.pan(0, 1, 0, -1)[1] == 0;
        assert FreecamMotion.pan(Double.NaN, 1, 0, 0.05)[1] == 0;
        assert FreecamRange.contains(0.5, 0.5, 0.5, -8, -8, -8);
        assert !FreecamRange.contains(0.5, 0.5, 0.5, 8, 0, 0);
        assert !FreecamRange.contains(Double.NaN, 0, 0, 0, 0, 0);
        assert FreecamRange.minimumBlock(-0.5) == -9;
        ModLog.initialize();
        ModLog.initialize();
        ModLog.info("Legacy Java 8 smoke passed: motion and idempotent log initialization.");
    }
}
