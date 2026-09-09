import local.freecaminteraction.client.FreecamMotion;
import local.freecaminteraction.FreecamRange;

class CameraMotionCheck {
    public static void main(String[] args) {
        var forward = FreecamMotion.pan(0, 1, 0, 0.05);
        assert Math.abs(forward.x()) < 1e-9 && Math.abs(forward.z() - 0.5) < 1e-9;
        var right = FreecamMotion.pan(0, 0, 1, 0.05);
        assert Math.abs(right.x() + 0.5) < 1e-9 && Math.abs(right.z()) < 1e-9;
        var turned = FreecamMotion.pan(90, 1, 0, 0.05);
        assert Math.abs(turned.x() + 0.5) < 1e-9 && Math.abs(turned.z()) < 1e-9;
        var diagonal = FreecamMotion.pan(0, 1, 1, 0.05);
        assert Math.abs(Math.hypot(diagonal.x(), diagonal.z()) - 0.5) < 1e-9;
        assert FreecamMotion.pan(0, 0, 0, 0.05).equals(new FreecamMotion.Offset(0, 0));
        assert FreecamMotion.pan(0, 1, 0, 10).equals(forward);
        assert FreecamMotion.pan(0, 1, 0, 0.025).z() * 2 == forward.z();
        assert FreecamMotion.pan(Double.NaN, 1, 0, 0.05).equals(new FreecamMotion.Offset(0, 0));
        assert FreecamMotion.pan(0, 1, 0, -1).z() == 0;
        assert Math.abs(FreecamMotion.vertical(1, 0.05) - 0.5) < 1e-9;
        assert Math.abs(FreecamMotion.vertical(-1, 0.05) + 0.5) < 1e-9;
        assert FreecamMotion.vertical(0, 0.05) == 0;
        assert FreecamMotion.vertical(Double.NaN, 0.05) == 0;
        assert FreecamMotion.vertical(1, 99) == 0.5;
        assert Math.abs(FreecamRange.cameraReach() - 12.0) < 1e-9;
        assert Math.abs(FreecamRange.clampCamera(100.0, 115.0) - 112.0) < 1e-9;
        assert Math.abs(FreecamRange.clampCamera(100.0, 80.0) - 88.0) < 1e-9;
        assert Math.abs(FreecamRange.clampCamera(100.0, 105.0) - 105.0) < 1e-9;
        assert FreecamRange.clampCamera(100.0, Double.NaN) == 100.0;
        System.out.println("Camera motion checks passed: direction, diagonal, frame rate, idle and limits.");
    }
}
