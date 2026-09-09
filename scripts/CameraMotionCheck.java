import local.freecaminteraction.client.FreecamMotion;

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
        System.out.println("Camera motion checks passed: direction, diagonal, frame rate, idle and limits.");
    }
}
