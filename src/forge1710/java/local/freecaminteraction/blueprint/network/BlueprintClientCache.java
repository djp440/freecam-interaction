package local.freecaminteraction.blueprint.network;

import java.util.concurrent.ConcurrentHashMap;
import local.freecaminteraction.blueprint.BlueprintData;

/**
 * 客户端蓝图与任务虚影缓存管理器。
 * 纯客户端侧维护当前可见任务元数据与已解压的 BlueprintData。
 * 当权限变为 HIDDEN 或任务被取消/完成时，自动清除该任务虚影缓存。
 */
public final class BlueprintClientCache {
    private static final ConcurrentHashMap<String, PacketBlueprintListResponse.TaskSummary> TASKS =
            new ConcurrentHashMap<String, PacketBlueprintListResponse.TaskSummary>();
    private static final ConcurrentHashMap<String, BlueprintData> BLUEPRINT_CACHE =
            new ConcurrentHashMap<String, BlueprintData>();
    private static final java.util.List<PacketBlueprintListResponse.BlueprintSummary> PERSONAL_BLUEPRINTS =
            new java.util.concurrent.CopyOnWriteArrayList<PacketBlueprintListResponse.BlueprintSummary>();

    public interface CacheListener {
        void onCacheUpdated();
    }
    private static final java.util.List<CacheListener> LISTENERS =
            new java.util.concurrent.CopyOnWriteArrayList<CacheListener>();

    private BlueprintClientCache() {}

    public static void addListener(CacheListener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(CacheListener listener) {
        if (listener != null) {
            LISTENERS.remove(listener);
        }
    }

    private static void notifyListeners() {
        for (CacheListener listener : LISTENERS) {
            try {
                listener.onCacheUpdated();
            } catch (Throwable ignored) {}
        }
    }

    public static void setPersonalBlueprints(java.util.List<PacketBlueprintListResponse.BlueprintSummary> list) {
        PERSONAL_BLUEPRINTS.clear();
        if (list != null) {
            PERSONAL_BLUEPRINTS.addAll(list);
        }
        notifyListeners();
    }

    public static java.util.List<PacketBlueprintListResponse.BlueprintSummary> getPersonalBlueprints() {
        return new java.util.ArrayList<PacketBlueprintListResponse.BlueprintSummary>(PERSONAL_BLUEPRINTS);
    }

    public static java.util.Collection<PacketBlueprintListResponse.TaskSummary> getAllTasks() {
        return new java.util.ArrayList<PacketBlueprintListResponse.TaskSummary>(TASKS.values());
    }

    public static void updateTask(PacketTaskSync sync) {
        if (sync == null || sync.taskId == null) return;
        // 主人始终可见自己的任务，仅对他人隐藏时从列表移除
        if (sync.syncType == PacketTaskSync.TYPE_REMOVE || (sync.permission == BlueprintTaskManager.PERM_HIDDEN && !sync.isOwner)) {
            removeTask(sync.taskId);
            return;
        }
        PacketBlueprintListResponse.TaskSummary summary = new PacketBlueprintListResponse.TaskSummary(
                sync.taskId, sync.blueprintId, sync.blueprintName, sync.ownerName,
                sync.dimension, sync.anchorX, sync.anchorY, sync.anchorZ,
                sync.permission, sync.showOutside, sync.status, sync.isOwner
        );
        TASKS.put(sync.taskId, summary);
        notifyListeners();
    }

    public static void removeTask(String taskId) {
        if (taskId == null) return;
        PacketBlueprintListResponse.TaskSummary removed = TASKS.remove(taskId);
        if (removed != null && removed.blueprintId != null) {
            // 检查是否还有其它可见任务引用同一张蓝图
            boolean stillUsed = false;
            for (PacketBlueprintListResponse.TaskSummary t : TASKS.values()) {
                if (removed.blueprintId.equals(t.blueprintId)) {
                    stillUsed = true;
                    break;
                }
            }
            if (!stillUsed) {
                BLUEPRINT_CACHE.remove(removed.blueprintId);
            }
        }
        notifyListeners();
    }

    public static void putBlueprint(String blueprintId, BlueprintData data) {
        if (blueprintId != null && data != null) {
            BLUEPRINT_CACHE.put(blueprintId, data);
            notifyListeners();
        }
    }

    public static BlueprintData getBlueprint(String blueprintId) {
        return blueprintId != null ? BLUEPRINT_CACHE.get(blueprintId) : null;
    }

    public static PacketBlueprintListResponse.TaskSummary getTask(String taskId) {
        return taskId != null ? TASKS.get(taskId) : null;
    }

    public static void clear() {
        TASKS.clear();
        BLUEPRINT_CACHE.clear();
        PERSONAL_BLUEPRINTS.clear();
        notifyListeners();
    }
}
