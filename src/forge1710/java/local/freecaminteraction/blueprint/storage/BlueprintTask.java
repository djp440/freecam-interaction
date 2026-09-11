package local.freecaminteraction.blueprint.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import local.freecaminteraction.blueprint.BlueprintData;
import local.freecaminteraction.blueprint.MaterialRequirement;

/**
 * 建造任务实体。
 * 保存独立的蓝图快照 (snapshot)，保证删除原蓝图文件不影响已有任务。
 * 包含三档权限、模式外虚影开关、状态及进度统计。
 */
public final class BlueprintTask {
    private final String taskId;
    private final UUID ownerUuid;
    private final String ownerName;
    private final int worldId; // dimension ID
    private final int originX;
    private final int originY;
    private final int originZ;
    private final BlueprintData snapshot;
    private TaskPermission permission;
    private boolean showOutsideFreecam;
    private TaskStatus status;
    private final long createdAt;
    private long lastModifiedAt;

    // 进度统计
    private int totalBlocks;
    private int placedBlocks;
    private List<MaterialRequirement> missingMaterials;

    public BlueprintTask(String taskId, UUID ownerUuid, String ownerName, int worldId,
                         int originX, int originY, int originZ, BlueprintData snapshot,
                         TaskPermission permission, boolean showOutsideFreecam,
                         TaskStatus status, long createdAt, long lastModifiedAt,
                         int totalBlocks, int placedBlocks, List<MaterialRequirement> missingMaterials) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("taskId must not be null or empty");
        }
        if (ownerUuid == null) {
            throw new IllegalArgumentException("ownerUuid must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        this.taskId = taskId;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName != null ? ownerName : "";
        this.worldId = worldId;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;

        // 制造独立的不可变深拷贝快照
        try {
            byte[] snapshotBytes = BlueprintStorage.serializeBlueprint(snapshot);
            this.snapshot = BlueprintStorage.deserializeBlueprint(snapshotBytes);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to create snapshot for task " + taskId, error);
        }

        this.permission = permission != null ? permission : TaskPermission.VISIBLE_ONLY;
        this.showOutsideFreecam = showOutsideFreecam;
        this.status = status != null ? status : TaskStatus.PENDING;
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.lastModifiedAt = lastModifiedAt > 0 ? lastModifiedAt : this.createdAt;

        this.totalBlocks = totalBlocks >= 0 ? totalBlocks : this.snapshot.getNonAirBlockCount();
        this.placedBlocks = Math.max(0, placedBlocks);
        if (missingMaterials != null) {
            this.missingMaterials = new ArrayList<MaterialRequirement>(missingMaterials);
        } else {
            this.missingMaterials = new ArrayList<MaterialRequirement>(this.snapshot.getConsolidatedMaterials());
        }
    }

    public BlueprintTask(String taskId, UUID ownerUuid, String ownerName, int worldId,
                         int originX, int originY, int originZ, BlueprintData snapshot) {
        this(taskId, ownerUuid, ownerName, worldId, originX, originY, originZ, snapshot,
                TaskPermission.VISIBLE_ONLY, true, TaskStatus.PENDING,
                System.currentTimeMillis(), System.currentTimeMillis(),
                -1, 0, null);
    }

    public String getTaskId() {
        return taskId;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getWorldId() {
        return worldId;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    public BlueprintData getSnapshot() {
        return snapshot;
    }

    public synchronized TaskPermission getPermission() {
        return permission;
    }

    public synchronized void setPermission(TaskPermission permission) {
        if (permission != null) {
            this.permission = permission;
            markModified();
        }
    }

    public synchronized boolean isShowOutsideFreecam() {
        return showOutsideFreecam;
    }

    public synchronized void setShowOutsideFreecam(boolean showOutsideFreecam) {
        this.showOutsideFreecam = showOutsideFreecam;
        markModified();
    }

    public synchronized TaskStatus getStatus() {
        return status;
    }

    public synchronized void setStatus(TaskStatus status) {
        if (status != null) {
            this.status = status;
            markModified();
        }
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public synchronized long getLastModifiedAt() {
        return lastModifiedAt;
    }

    public synchronized void markModified() {
        this.lastModifiedAt = System.currentTimeMillis();
    }

    public synchronized int getTotalBlocks() {
        return totalBlocks;
    }

    public synchronized void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = Math.max(0, totalBlocks);
        markModified();
    }

    public synchronized int getPlacedBlocks() {
        return placedBlocks;
    }

    public synchronized void setPlacedBlocks(int placedBlocks) {
        this.placedBlocks = Math.max(0, placedBlocks);
        markModified();
    }

    public synchronized List<MaterialRequirement> getMissingMaterials() {
        return Collections.unmodifiableList(new ArrayList<MaterialRequirement>(missingMaterials));
    }

    public synchronized void setMissingMaterials(List<MaterialRequirement> missingMaterials) {
        if (missingMaterials != null) {
            this.missingMaterials = new ArrayList<MaterialRequirement>(missingMaterials);
        } else {
            this.missingMaterials.clear();
        }
        markModified();
    }

    /**
     * 判断当前任务是否对指定玩家可见。
     * - 主人：始终可见
     * - 协作者：若 permission == HIDDEN 则不可见；若 VISIBLE_ONLY 或 BUILDABLE 则可见
     * - 若处于自由视角外且 showOutsideFreecam == false，则无论权限均不可见（除主人外亦受此开关影响模式外展示）
     */
    public synchronized boolean isVisibleTo(UUID playerUuid, boolean isFreecamActive) {
        if (playerUuid == null) return false;
        boolean isOwner = ownerUuid.equals(playerUuid);

        // 处于自由视角外且关闭了外部显示
        if (!isFreecamActive && !showOutsideFreecam) {
            return false;
        }

        if (isOwner) {
            return true;
        }

        return permission != TaskPermission.HIDDEN;
    }

    /**
     * 判断当前任务是否允许指定玩家进行建造施工。
     * - 主人：始终允许（需处于未完成且未取消状态）
     * - 协作者：必须 permission == BUILDABLE
     */
    public synchronized boolean isBuildableBy(UUID playerUuid) {
        if (playerUuid == null) return false;
        if (status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED) {
            return false;
        }
        if (ownerUuid.equals(playerUuid)) {
            return true;
        }
        return permission == TaskPermission.BUILDABLE;
    }
}