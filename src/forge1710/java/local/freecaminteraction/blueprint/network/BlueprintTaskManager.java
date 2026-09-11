package local.freecaminteraction.blueprint.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import local.freecaminteraction.FreecamInteraction;
import local.freecaminteraction.blueprint.BlueprintData;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 协作建造任务服务端数据与状态管理。
 * 包含三档他人权限：
 * 0 - HIDDEN: 不可见（其他玩家隐藏虚影与任务）
 * 1 - VISIBLE_NO_BUILD: 可见但不可建造（默认）
 * 2 - CAN_BUILD: 可建造（允许其他玩家协作施工）
 */
public final class BlueprintTaskManager {
    public static final byte PERM_HIDDEN = 0;
    public static final byte PERM_VISIBLE_NO_BUILD = 1;
    public static final byte PERM_CAN_BUILD = 2;

    public static final byte STATUS_PENDING = 0;
    public static final byte STATUS_BUILDING = 1;
    public static final byte STATUS_COMPLETED = 2;
    public static final byte STATUS_CANCELLED = 3;

    public static final class BuildTask {
        private final String taskId;
        private final String blueprintId;
        private final String blueprintName;
        private final UUID ownerUuid;
        private final String ownerName;
        private final int dimension;
        private final int anchorX;
        private final int anchorY;
        private final int anchorZ;
        private volatile byte permission; // 0, 1, 2
        private volatile boolean showOutside; // 模式外显示
        private volatile byte status; // 0 pending, 1 building, 2 completed, 3 cancelled
        private final long createdAt;
        private volatile BlueprintData blueprintSnapshot; // 独立不可变快照

        public BuildTask(String taskId, String blueprintId, String blueprintName,
                         UUID ownerUuid, String ownerName, int dimension,
                         int anchorX, int anchorY, int anchorZ,
                         byte permission, boolean showOutside, byte status,
                         long createdAt, BlueprintData snapshot) {
            this.taskId = taskId != null ? taskId : UUID.randomUUID().toString();
            this.blueprintId = blueprintId != null ? blueprintId : "";
            this.blueprintName = blueprintName != null ? blueprintName : "";
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName != null ? ownerName : "";
            this.dimension = dimension;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.permission = permission;
            this.showOutside = showOutside;
            this.status = status;
            this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
            this.blueprintSnapshot = snapshot;
        }

        public String getTaskId() { return taskId; }
        public String getBlueprintId() { return blueprintId; }
        public String getBlueprintName() { return blueprintName; }
        public UUID getOwnerUuid() { return ownerUuid; }
        public String getOwnerName() { return ownerName; }
        public int getDimension() { return dimension; }
        public int getAnchorX() { return anchorX; }
        public int getAnchorY() { return anchorY; }
        public int getAnchorZ() { return anchorZ; }
        public byte getPermission() { return permission; }
        public void setPermission(byte permission) { this.permission = permission; }
        public boolean isShowOutside() { return showOutside; }
        public void setShowOutside(boolean showOutside) { this.showOutside = showOutside; }
        public byte getStatus() { return status; }
        public void setStatus(byte status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public BlueprintData getBlueprintSnapshot() { return blueprintSnapshot; }
        public void setBlueprintSnapshot(BlueprintData snapshot) { this.blueprintSnapshot = snapshot; }

        public boolean isOwner(EntityPlayerMP player) {
            if (player == null || ownerUuid == null) return false;
            return ownerUuid.equals(player.getUniqueID());
        }

        /**
         * 判断指定玩家是否可见该任务
         */
        public boolean isVisibleTo(EntityPlayerMP player) {
            if (player == null) return false;
            if (isOwner(player)) return true;
            return permission != PERM_HIDDEN;
        }

        /**
         * 判断指定玩家是否具有建造权限
         */
        public boolean canBuild(EntityPlayerMP player) {
            if (player == null) return false;
            if (isOwner(player)) return true;
            return permission == PERM_CAN_BUILD;
        }
    }

    private static final ConcurrentHashMap<String, BuildTask> TASKS = new ConcurrentHashMap<String, BuildTask>();

    private BlueprintTaskManager() {}

    public static BuildTask createTask(EntityPlayerMP owner, BlueprintData blueprint, int anchorX, int anchorY, int anchorZ) {
        if (owner == null || blueprint == null) return null;
        if (!FreecamInteraction.active(owner)) return null;

        String taskId = UUID.randomUUID().toString();
        BuildTask task = new BuildTask(
                taskId, blueprint.getId(), blueprint.getName(),
                owner.getUniqueID(), owner.getCommandSenderName(), owner.dimension,
                anchorX, anchorY, anchorZ,
                PERM_VISIBLE_NO_BUILD, true, STATUS_PENDING,
                System.currentTimeMillis(), blueprint
        );
        TASKS.put(taskId, task);
        return task;
    }

    public static BuildTask getTask(String taskId) {
        if (taskId == null) return null;
        return TASKS.get(taskId);
    }

    public static BuildTask removeTask(String taskId) {
        if (taskId == null) return null;
        return TASKS.remove(taskId);
    }

    public static List<BuildTask> getAllTasks() {
        return new ArrayList<BuildTask>(TASKS.values());
    }

    public static List<BuildTask> getTasksVisibleTo(EntityPlayerMP player) {
        if (player == null) return Collections.emptyList();
        List<BuildTask> list = new ArrayList<BuildTask>();
        for (BuildTask task : TASKS.values()) {
            if (task.isVisibleTo(player) && task.getDimension() == player.dimension) {
                list.add(task);
            }
        }
        return list;
    }
}
