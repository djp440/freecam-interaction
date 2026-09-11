package local.freecaminteraction.blueprint.storage;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.blueprint.BlueprintData;
import local.freecaminteraction.blueprint.MaterialRequirement;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * 建造任务管理器。
 * 任务持久化保存在 world 存档目录下的 freecam_interaction/tasks/<taskId>.task 中。
 * 负责任务的增删改查、快照固化、三档权限隔离与主人操作鉴权。
 */
public final class BlueprintTaskManager {
    /** 任务文件魔数 'TASK' (0x5441534B) */
    public static final int TASK_MAGIC = 0x5441534B;
    /** 任务格式版本 */
    public static final int TASK_VERSION = 1;

    private static final Object IO_LOCK = new Object();
    // 内存中缓存世界激活的任务 Map<taskId, BlueprintTask>
    private static final Map<String, BlueprintTask> ACTIVE_TASKS = new ConcurrentHashMap<String, BlueprintTask>();

    private BlueprintTaskManager() {}

    /**
     * 获取任务文件存储目录：freecam_interaction/tasks/
     */
    public static File getTasksDir(World world) {
        if (world == null) {
            throw new IllegalArgumentException("World must not be null");
        }
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File baseDir = new File(worldDir, "freecam_interaction");
        File tasksDir = new File(baseDir, "tasks");
        if (!tasksDir.exists()) {
            tasksDir.mkdirs();
        }
        return tasksDir;
    }

    /**
     * 创建建造任务。从玩家个人蓝图库中加载指定蓝图并固化为独立快照存盘。
     */
    public static BlueprintTask createTask(World world, EntityPlayer owner, String bpId,
                                          int originX, int originY, int originZ) {
        if (world == null || owner == null || bpId == null || bpId.trim().isEmpty()) {
            return null;
        }

        UUID ownerUuid = owner.getUniqueID();
        BlueprintData bpData = BlueprintStorage.loadBlueprint(world, ownerUuid, bpId);
        if (bpData == null) {
            ModLog.info("Cannot create task: blueprint not found id=" + bpId + " for player=" + owner.getCommandSenderName());
            return null;
        }

        String taskId = UUID.randomUUID().toString();
        int dimension = world.provider != null ? world.provider.dimensionId : 0;
        BlueprintTask task = new BlueprintTask(taskId, ownerUuid, owner.getCommandSenderName(),
                dimension, originX, originY, originZ, bpData);

        if (saveTask(world, task)) {
            ACTIVE_TASKS.put(taskId, task);
            ModLog.info("Created task taskId=" + taskId + " for owner=" + owner.getCommandSenderName());
            return task;
        }
        return null;
    }

    /**
     * 取消建造任务。仅主人有权限取消。
     */
    public static boolean cancelTask(World world, EntityPlayer player, String taskId) {
        if (world == null || player == null || taskId == null) {
            return false;
        }
        BlueprintTask task = getTask(world, taskId);
        if (task == null) {
            return false;
        }
        if (!task.getOwnerUuid().equals(player.getUniqueID())) {
            ModLog.info("Security rejection: player=" + player.getCommandSenderName() + " tried to cancel task=" + taskId + " owned by " + task.getOwnerUuid());
            return false;
        }

        task.setStatus(TaskStatus.CANCELLED);
        saveTask(world, task);
        ModLog.info("Task cancelled: " + taskId);
        return true;
    }

    /**
     * 更新任务权限。仅主人有权限修改。
     */
    public static boolean updatePermission(World world, EntityPlayer player, String taskId, TaskPermission permission) {
        if (world == null || player == null || taskId == null || permission == null) {
            return false;
        }
        BlueprintTask task = getTask(world, taskId);
        if (task == null) {
            return false;
        }
        if (!task.getOwnerUuid().equals(player.getUniqueID())) {
            ModLog.info("Security rejection: player=" + player.getCommandSenderName() + " tried to change permission on task=" + taskId);
            return false;
        }

        task.setPermission(permission);
        saveTask(world, task);
        ModLog.info("Task permission updated: " + taskId + " -> " + permission);
        return true;
    }

    /**
     * 设置是否在自由视角外显示虚影。仅主人有权限修改。
     */
    public static boolean setShowOutside(World world, EntityPlayer player, String taskId, boolean show) {
        if (world == null || player == null || taskId == null) {
            return false;
        }
        BlueprintTask task = getTask(world, taskId);
        if (task == null) {
            return false;
        }
        if (!task.getOwnerUuid().equals(player.getUniqueID())) {
            ModLog.info("Security rejection: player=" + player.getCommandSenderName() + " tried to change showOutside on task=" + taskId);
            return false;
        }

        task.setShowOutsideFreecam(show);
        saveTask(world, task);
        return true;
    }

    /**
     * 根据三档权限与模式外可见性过滤对该玩家可见的任务列表。
     */
    public static List<BlueprintTask> listTasksForPlayer(World world, EntityPlayer player, boolean isFreecamActive) {
        if (world == null || player == null) {
            return Collections.emptyList();
        }

        // 确保已从磁盘完全加载当前世界的任务
        loadAllTasksForWorld(world);

        UUID playerUuid = player.getUniqueID();
        int currentDim = world.provider != null ? world.provider.dimensionId : 0;
        List<BlueprintTask> visible = new ArrayList<BlueprintTask>();

        for (BlueprintTask task : ACTIVE_TASKS.values()) {
            if (task.getWorldId() != currentDim) {
                continue;
            }
            if (task.isVisibleTo(playerUuid, isFreecamActive)) {
                visible.add(task);
            }
        }
        return Collections.unmodifiableList(visible);
    }

    /**
     * 获取单个任务（内存中不存在则尝试从磁盘加载）。
     */
    public static BlueprintTask getTask(World world, String taskId) {
        if (taskId == null) return null;
        BlueprintTask task = ACTIVE_TASKS.get(taskId);
        if (task != null) {
            return task;
        }
        task = loadTask(world, taskId);
        if (task != null) {
            ACTIVE_TASKS.put(taskId, task);
        }
        return task;
    }

    /**
     * 将任务保存至磁盘（原子替换写入 .tmp -> .task）。
     */
    public static boolean saveTask(World world, BlueprintTask task) {
        if (world == null || task == null || task.getTaskId() == null) {
            return false;
        }

        String taskId = task.getTaskId();
        synchronized (IO_LOCK) {
            File tasksDir = getTasksDir(world);
            File targetFile = new File(tasksDir, taskId + ".task");
            File tempFile = new File(tasksDir, taskId + ".task.tmp");

            try {
                FileOutputStream fos = new FileOutputStream(tempFile);
                try {
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    GZIPOutputStream gzos = new GZIPOutputStream(bos);
                    DataOutputStream out = new DataOutputStream(gzos);

                    writeTaskData(task, out);
                    out.flush();
                    gzos.finish();
                    bos.flush();
                    fos.getFD().sync();
                } finally {
                    fos.close();
                }

                Files.move(tempFile.toPath(), targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (Throwable error) {
                ModLog.info("Failed to save task taskId=" + taskId + ": " + error.getMessage());
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                return false;
            }
        }
    }

    /**
     * 从磁盘加载单个任务。坏文件隔离为 .corrupt。
     */
    public static BlueprintTask loadTask(World world, String taskId) {
        if (world == null || taskId == null || taskId.trim().isEmpty()) {
            return null;
        }

        synchronized (IO_LOCK) {
            File tasksDir = getTasksDir(world);
            File taskFile = new File(tasksDir, taskId + ".task");
            if (!taskFile.exists() || !taskFile.isFile()) {
                return null;
            }

            try {
                byte[] rawBytes = Files.readAllBytes(taskFile.toPath());
                return deserializeTask(rawBytes);
            } catch (Throwable error) {
                ModLog.info("Corrupt task detected at " + taskFile.getAbsolutePath() + ": " + error.getMessage());
                File corruptFile = new File(tasksDir, taskId + ".task.corrupt_" + System.currentTimeMillis());
                try {
                    Files.move(taskFile.toPath(), corruptFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    ModLog.info("Isolated corrupt task to " + corruptFile.getName());
                } catch (Throwable moveError) {
                    ModLog.info("Failed to isolate corrupt task file: " + moveError.getMessage());
                }
                return null;
            }
        }
    }

    /**
     * 加载当前世界存档下的全部任务到内存。
     */
    public static List<BlueprintTask> loadAllTasksForWorld(World world) {
        if (world == null) {
            return Collections.emptyList();
        }

        synchronized (IO_LOCK) {
            File tasksDir = getTasksDir(world);
            File[] files = tasksDir.listFiles((dir, name) -> name.endsWith(".task"));
            if (files == null || files.length == 0) {
                return Collections.emptyList();
            }

            List<BlueprintTask> loaded = new ArrayList<BlueprintTask>(files.length);
            for (File file : files) {
                String fileName = file.getName();
                String taskId = fileName.substring(0, fileName.length() - 5);
                BlueprintTask existing = ACTIVE_TASKS.get(taskId);
                if (existing != null) {
                    loaded.add(existing);
                } else {
                    BlueprintTask task = loadTask(world, taskId);
                    if (task != null) {
                        ACTIVE_TASKS.put(taskId, task);
                        loaded.add(task);
                    }
                }
            }
            return Collections.unmodifiableList(loaded);
        }
    }

    /**
     * 写入任务完整二进制数据。
     */
    private static void writeTaskData(BlueprintTask task, DataOutputStream out) throws IOException {
        out.writeInt(TASK_MAGIC);
        out.writeInt(TASK_VERSION);

        writeUtf(out, task.getTaskId());
        out.writeLong(task.getOwnerUuid().getMostSignificantBits());
        out.writeLong(task.getOwnerUuid().getLeastSignificantBits());
        writeUtf(out, task.getOwnerName());
        out.writeInt(task.getWorldId());
        out.writeInt(task.getOriginX());
        out.writeInt(task.getOriginY());
        out.writeInt(task.getOriginZ());

        // 写入不可变蓝图快照
        BlueprintStorage.writeBlueprint(task.getSnapshot(), out);

        out.writeInt(task.getPermission().getId());
        out.writeBoolean(task.isShowOutsideFreecam());
        writeUtf(out, task.getStatus().name());
        out.writeLong(task.getCreatedAt());
        out.writeLong(task.getLastModifiedAt());

        // 进度统计
        out.writeInt(task.getTotalBlocks());
        out.writeInt(task.getPlacedBlocks());

        List<MaterialRequirement> missing = task.getMissingMaterials();
        out.writeInt(missing.size());
        for (MaterialRequirement req : missing) {
            writeUtf(out, req.getItemRegistryName());
            out.writeInt(req.getDamage());
            writeNbt(out, req.getMatchTag());
            out.writeInt(req.getCount());
            out.writeInt(req.getPlacedCount());
        }
    }

    /**
     * 反序列化任务二进制数据。
     */
    public static BlueprintTask deserializeTask(byte[] compressedBytes) throws IOException {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(compressedBytes);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             DataInputStream in = new DataInputStream(gzis)) {

            int magic = in.readInt();
            if (magic != TASK_MAGIC) {
                throw new IOException("Invalid task magic: 0x" + Integer.toHexString(magic));
            }
            int version = in.readInt();
            if (version != TASK_VERSION) {
                throw new IOException("Unsupported task version: " + version);
            }

            String taskId = readUtf(in);
            long most = in.readLong();
            long least = in.readLong();
            UUID ownerUuid = new UUID(most, least);
            String ownerName = readUtf(in);
            int worldId = in.readInt();
            int originX = in.readInt();
            int originY = in.readInt();
            int originZ = in.readInt();

            BlueprintData snapshot = BlueprintStorage.readBlueprint(in);

            int permId = in.readInt();
            TaskPermission permission = TaskPermission.fromId(permId);
            boolean showOutside = in.readBoolean();
            String statusName = readUtf(in);
            TaskStatus status;
            try {
                status = TaskStatus.valueOf(statusName);
            } catch (Throwable ignored) {
                status = TaskStatus.PENDING;
            }
            long createdAt = in.readLong();
            long modifiedAt = in.readLong();

            int totalBlocks = in.readInt();
            int placedBlocks = in.readInt();

            int missingCount = in.readInt();
            List<MaterialRequirement> missing = new ArrayList<MaterialRequirement>(missingCount);
            for (int i = 0; i < missingCount; i++) {
                String itemName = readUtf(in);
                int damage = in.readInt();
                NBTTagCompound matchTag = readNbt(in);
                int count = in.readInt();
                int placed = in.readInt();
                MaterialRequirement req = new MaterialRequirement(itemName, damage, matchTag, count);
                req.setPlacedCount(placed);
                missing.add(req);
            }

            return new BlueprintTask(taskId, ownerUuid, ownerName, worldId, originX, originY, originZ,
                    snapshot, permission, showOutside, status, createdAt, modifiedAt,
                    totalBlocks, placedBlocks, missing);
        }
    }

    private static void writeNbt(DataOutputStream out, NBTTagCompound tag) throws IOException {
        if (tag == null || tag.hasNoTags()) {
            out.writeInt(0);
            return;
        }
        byte[] bytes = CompressedStreamTools.compress(tag);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static NBTTagCompound readNbt(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0) {
            return null;
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return CompressedStreamTools.func_152457_a(bytes, NBTSizeTracker.field_152451_a);
    }

    private static void writeUtf(DataOutputStream out, String str) throws IOException {
        if (str == null) {
            out.writeShort(-1);
            return;
        }
        byte[] utfBytes = str.getBytes(StandardCharsets.UTF_8);
        out.writeShort(utfBytes.length);
        out.write(utfBytes);
    }

    private static String readUtf(DataInputStream in) throws IOException {
        short len = in.readShort();
        if (len < 0) {
            return null;
        }
        byte[] utfBytes = new byte[len];
        in.readFully(utfBytes);
        return new String(utfBytes, StandardCharsets.UTF_8);
    }
}