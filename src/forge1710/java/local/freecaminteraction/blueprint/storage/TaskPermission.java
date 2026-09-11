package local.freecaminteraction.blueprint.storage;

/**
 * 建造任务权限枚举。
 * 三档权限：
 * - HIDDEN (0) - 不可见（隐藏虚影、禁止施工）
 * - VISIBLE_ONLY (1) - 可见但不可建造（默认）
 * - BUILDABLE (2) - 可建造（允许符合条件者施工）
 */
public enum TaskPermission {
    HIDDEN(0),
    VISIBLE_ONLY(1),
    BUILDABLE(2);

    private final int id;

    TaskPermission(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static TaskPermission fromId(int id) {
        switch (id) {
            case 0:
                return HIDDEN;
            case 2:
                return BUILDABLE;
            case 1:
            default:
                return VISIBLE_ONLY;
        }
    }
}
