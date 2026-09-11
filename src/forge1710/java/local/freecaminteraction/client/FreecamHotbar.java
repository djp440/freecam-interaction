package local.freecaminteraction.client;

public final class FreecamHotbar {
    private FreecamHotbar() {}

    public static int getSlotAt(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int left = screenWidth / 2 - 91;
        int top = screenHeight - 22;
        if (mouseX < left || mouseX >= left + 182 || mouseY < top || mouseY >= top + 22) {
            return -1;
        }
        int relX = mouseX - (left + 1);
        if (relX < 0) return -1;
        int slot = relX / 20;
        return (slot >= 0 && slot < 9) ? slot : -1;
    }
}
