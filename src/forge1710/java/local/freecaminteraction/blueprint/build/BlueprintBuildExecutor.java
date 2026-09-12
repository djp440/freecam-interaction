package local.freecaminteraction.blueprint.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import local.freecaminteraction.FreecamInteraction;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.WandTier;
import local.freecaminteraction.blueprint.BlueprintBlockEntry;
import local.freecaminteraction.blueprint.BlueprintData;
import local.freecaminteraction.blueprint.BlueprintPartEntry;
import local.freecaminteraction.blueprint.BlueprintPartSupport;
import local.freecaminteraction.blueprint.IBlueprintAdapter;
import local.freecaminteraction.blueprint.MaterialRequirement;
import local.freecaminteraction.blueprint.VanillaBlueprintAdapter;
import local.freecaminteraction.blueprint.network.BlueprintNetwork;
import local.freecaminteraction.blueprint.network.BlueprintTaskManager;
import local.freecaminteraction.blueprint.network.PacketTaskSync;
import local.freecaminteraction.blueprint.storage.BlueprintTask;
import local.freecaminteraction.blueprint.storage.TaskPermission;
import local.freecaminteraction.blueprint.storage.TaskStatus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/**
 * 蓝图单步建造执行与物料事务层。
 * <p>
 * 严格遵循 PLAN-blueprints-1710.md 契约：
 * 1. 资格与权限复核：
 *    1) 操作者必须处于自由视角模式 (FreecamInteraction.active(player))；
 *    2) 背包中任意一把法杖必须安装蓝图核心 (FreecamInteraction.hasBlueprintCoreWand(player))；
 *    3) 施工目标必须在操作者本人的合法交互范围内 (FreecamInteraction.inside(player, x, y, z))；
 *    4) 协作者必须要求任务权限为 TaskPermission.BUILDABLE (HIDDEN 或 VISIBLE_ONLY 时严厉拒绝)。
 * 2. 物料守恒与事务：
 *    1) 材料严格消耗操作者自身的背包 (player.inventory)，绝不动用任务主人的库存；创造模式玩家免材料；
 *    2) “预留物料 -> 实际放置 -> 提交扣除”三段式事务，防止放置被取消或异常时吞物料；
 *    3) 每成功放置 1 个方块或安装 1 个部件，扣除操作者法杖 1 点耐久；创造法杖免耐久；跳过/失败不扣耐久；
 *    4) 耐久降至 1 时触发标准自动接续，无可用法杖平滑停止施工并通知玩家。
 * 3. 多人并发防刷：
 *    - 并发坐标锁 (ConcurrentSet)，防止多位玩家同时点击同一任务时对相同坐标重复放置和重复扣料。
 */
public final class BlueprintBuildExecutor {

    public static final int DEFAULT_BATCH_BUDGET = 16;

    /**
     * 执行结果状态码
     */
    public enum BuildResultStatus {
        SUCCESS,
        SKIPPED_ALREADY_MATCHES,
        MISSING_MATERIALS,
        WAND_EXHAUSTED,
        OUT_OF_RANGE,
        PERMISSION_DENIED,
        NOT_IN_FREECAM,
        NO_CORE,
        COORDINATE_LOCKED,
        CONFLICT_OR_BLOCKED,
        TASK_INVALID
    }

    /**
     * 单步执行结果
     */
    public static final class BuildResult {
        public final BuildResultStatus status;
        public final int x, y, z;
        public final String message;

        public BuildResult(BuildResultStatus status, int x, int y, int z, String message) {
            this.status = status;
            this.x = x;
            this.y = y;
            this.z = z;
            this.message = message != null ? message : "";
        }

        public boolean isSuccess() {
            return status == BuildResultStatus.SUCCESS;
        }

        public boolean isSkipped() {
            return status == BuildResultStatus.SKIPPED_ALREADY_MATCHES;
        }

        public static BuildResult success(int x, int y, int z) {
            return new BuildResult(BuildResultStatus.SUCCESS, x, y, z, "ok");
        }

        public static BuildResult skipped(int x, int y, int z) {
            return new BuildResult(BuildResultStatus.SKIPPED_ALREADY_MATCHES, x, y, z, "already_matches");
        }

        public static BuildResult missingMaterials(int x, int y, int z) {
            return new BuildResult(BuildResultStatus.MISSING_MATERIALS, x, y, z, "missing_materials");
        }

        public static BuildResult wandExhausted(int x, int y, int z) {
            return new BuildResult(BuildResultStatus.WAND_EXHAUSTED, x, y, z, "wand_exhausted");
        }

        public static BuildResult outOfRange(int x, int y, int z) {
            return new BuildResult(BuildResultStatus.OUT_OF_RANGE, x, y, z, "out_of_range");
        }

        public static BuildResult permissionDenied(int x, int y, int z) {
            return new BuildResult(BuildResultStatus.PERMISSION_DENIED, x, y, z, "permission_denied");
        }

        public static BuildResult notInFreecam(int x, int y, int z) {
            return new BuildResult(BuildResultStatus.NOT_IN_FREECAM, x, y, z, "not_in_freecam");
        }

        public static BuildResult noCore(int x, int y, int z) {
            return new BuildResult(BuildResultStatus.NO_CORE, x, y, z, "no_blueprint_core");
        }

        public static BuildResult coordinateLocked(int x, int y, int z) {
            return new BuildResult(BuildResultStatus.COORDINATE_LOCKED, x, y, z, "coordinate_locked");
        }

        public static BuildResult conflictOrBlocked(int x, int y, int z) {
            return new BuildResult(BuildResultStatus.CONFLICT_OR_BLOCKED, x, y, z, "conflict_or_blocked");
        }

        public static BuildResult taskInvalid(int x, int y, int z) {
            return new BuildResult(BuildResultStatus.TASK_INVALID, x, y, z, "task_invalid");
        }
    }

    /**
     * 三段式物料预留与扣除事务
     */
    public static final class MaterialReservation {
        private final EntityPlayer player;
        private final Map<Integer, Integer> slotDeductions;
        private final boolean creativeBypass;
        private boolean committed = false;
        private boolean rolledBack = false;

        public MaterialReservation(EntityPlayer player, Map<Integer, Integer> slotDeductions, boolean creativeBypass) {
            this.player = player;
            this.slotDeductions = slotDeductions != null ? slotDeductions : Collections.<Integer, Integer>emptyMap();
            this.creativeBypass = creativeBypass;
        }

        public synchronized boolean commit() {
            if (committed || rolledBack) {
                return false;
            }
            if (creativeBypass) {
                committed = true;
                return true;
            }
            if (player == null || player.inventory == null || player.inventory.mainInventory == null) {
                rolledBack = true;
                return false;
            }

            // 二次确认各槽位物料仍然充足，防止在此期间发生并发变动
            for (Map.Entry<Integer, Integer> entry : slotDeductions.entrySet()) {
                int slot = entry.getKey();
                int deduct = entry.getValue();
                if (slot < 0 || slot >= player.inventory.mainInventory.length) {
                    rolledBack = true;
                    return false;
                }
                ItemStack stack = player.inventory.mainInventory[slot];
                if (stack == null || stack.stackSize < deduct) {
                    rolledBack = true;
                    return false;
                }
            }

            // 实际扣除
            for (Map.Entry<Integer, Integer> entry : slotDeductions.entrySet()) {
                int slot = entry.getKey();
                int deduct = entry.getValue();
                ItemStack stack = player.inventory.mainInventory[slot];
                if (stack != null) {
                    stack.stackSize -= deduct;
                    if (stack.stackSize <= 0) {
                        player.inventory.mainInventory[slot] = null;
                    }
                }
            }
            if (player.inventoryContainer != null) {
                player.inventoryContainer.detectAndSendChanges();
            }
            committed = true;
            return true;
        }

        public synchronized void rollback() {
            // 绝不扣除操作者背包任何物料
            rolledBack = true;
        }

        public boolean isCommitted() {
            return committed;
        }

        public boolean isRolledBack() {
            return rolledBack;
        }

        public boolean isCreativeBypass() {
            return creativeBypass;
        }
    }

    // ==========================================
    // 并发坐标锁 (Concurrent Set)
    // ==========================================

    private static final Set<String> ACTIVE_COORDINATE_LOCKS =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private BlueprintBuildExecutor() {}

    private static String toKey(int dimension, int x, int y, int z) {
        return dimension + ":" + x + ":" + y + ":" + z;
    }

    private static String toPartKey(int dimension, int x, int y, int z, int side) {
        return dimension + ":" + x + ":" + y + ":" + z + "#" + side;
    }

    public static boolean tryLockCoordinate(int dimension, int x, int y, int z) {
        return ACTIVE_COORDINATE_LOCKS.add(toKey(dimension, x, y, z));
    }

    public static void unlockCoordinate(int dimension, int x, int y, int z) {
        ACTIVE_COORDINATE_LOCKS.remove(toKey(dimension, x, y, z));
    }

    public static boolean isCoordinateLocked(int dimension, int x, int y, int z) {
        return ACTIVE_COORDINATE_LOCKS.contains(toKey(dimension, x, y, z));
    }

    public static boolean tryLockPart(int dimension, int x, int y, int z, int side) {
        return ACTIVE_COORDINATE_LOCKS.add(toPartKey(dimension, x, y, z, side));
    }

    public static void unlockPart(int dimension, int x, int y, int z, int side) {
        ACTIVE_COORDINATE_LOCKS.remove(toPartKey(dimension, x, y, z, side));
    }

    public static boolean isPartLocked(int dimension, int x, int y, int z, int side) {
        return ACTIVE_COORDINATE_LOCKS.contains(toPartKey(dimension, x, y, z, side));
    }

    public static void clearLocks() {
        ACTIVE_COORDINATE_LOCKS.clear();
    }

    // ==========================================
    // 物料预留 (严格仅从操作者本人背包预留)
    // ==========================================

    /**
     * 预留物料。严格检查操作者自己的主背包 (0..35)，严禁动用任务主人的库存。
     * 创造模式玩家免材料。
     */
    public static MaterialReservation prepareReservation(EntityPlayer player, List<MaterialRequirement> requirements) {
        if (player == null) {
            return null;
        }
        if (player.capabilities != null && player.capabilities.isCreativeMode) {
            return new MaterialReservation(player, Collections.<Integer, Integer>emptyMap(), true);
        }
        if (requirements == null || requirements.isEmpty()) {
            return new MaterialReservation(player, Collections.<Integer, Integer>emptyMap(), false);
        }
        if (player.inventory == null || player.inventory.mainInventory == null) {
            return null;
        }

        Map<Integer, Integer> deductions = new HashMap<Integer, Integer>();

        for (MaterialRequirement req : requirements) {
            if (req == null || req.getCount() <= 0) {
                continue;
            }
            int remainingNeeded = req.getCount();

            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = player.inventory.mainInventory[slot];
                if (stack != null && req.matches(stack)) {
                    int alreadyReserved = deductions.containsKey(slot) ? deductions.get(slot) : 0;
                    int available = stack.stackSize - alreadyReserved;
                    if (available > 0) {
                        int take = Math.min(remainingNeeded, available);
                        deductions.put(slot, alreadyReserved + take);
                        remainingNeeded -= take;
                        if (remainingNeeded <= 0) {
                            break;
                        }
                    }
                }
            }

            if (remainingNeeded > 0) {
                // 任意物料不足，预留失败，不产生任何扣除
                return null;
            }
        }

        return new MaterialReservation(player, deductions, false);
    }

    // ==========================================
    // 权限与资格核验
    // ==========================================

    public static BuildResultStatus checkQualifications(EntityPlayerMP player, int targetX, int targetY, int targetZ,
                                                       UUID taskOwnerUuid, TaskPermission taskPermission) {
        if (player == null) {
            return BuildResultStatus.TASK_INVALID;
        }

        // 1. 操作者必须处于自由视角
        if (!FreecamInteraction.active(player)) {
            return BuildResultStatus.NOT_IN_FREECAM;
        }

        // 2. 操作者当前生效法杖必须安装蓝图核心
        if (!FreecamInteraction.hasBlueprintCoreWand(player)) {
            return BuildResultStatus.NO_CORE;
        }

        // 3. 施工目标方块必须在操作者本人的合法交互范围内
        if (!FreecamInteraction.inside(player, targetX, targetY, targetZ)) {
            return BuildResultStatus.OUT_OF_RANGE;
        }

        // 4. 若操作者非主人（协作者），任务权限必须为 TaskPermission.BUILDABLE
        boolean isOwner = (taskOwnerUuid != null && taskOwnerUuid.equals(player.getUniqueID()));
        if (!isOwner) {
            if (taskPermission != TaskPermission.BUILDABLE) {
                return BuildResultStatus.PERMISSION_DENIED;
            }
        }

        return BuildResultStatus.SUCCESS;
    }

    // ==========================================
    // 法杖耐久扣除与自动接续
    // ==========================================

    public static boolean deductWandDurability(EntityPlayerMP player) {
        if (player == null) {
            return false;
        }
        WandTier tier = FreecamInteraction.getActiveTier(player);
        if (tier == WandTier.CREATIVE) {
            return true; // 创造法杖免耐久
        }

        boolean deducted = FreecamInteraction.deductUsage(player);
        if (!deducted || !FreecamInteraction.active(player)) {
            notifyPlayer(player, "message.freecam_interaction.bp.wand_exhausted", "法杖耐久不足且无可用接续法杖，建造施工已停止");
            return false;
        }
        return true;
    }

    private static void notifyPlayer(EntityPlayer player, String messageKey, String fallbackText) {
        if (player == null) return;
        try {
            if (player instanceof EntityPlayerMP && ((EntityPlayerMP) player).playerNetServerHandler == null) {
                return; // 测试桩环境无网络连接
            }
            player.addChatMessage(new ChatComponentTranslation(messageKey));
        } catch (Throwable ignored) {
            try {
                player.addChatMessage(new ChatComponentText(fallbackText));
            } catch (Throwable ignored2) {}
        }
    }

    // ==========================================
    // 单一方块放置事务 (executeBlockStep)
    // ==========================================

    public static BuildResult executeBlockStep(EntityPlayerMP player, BlueprintTask task, BlueprintBlockEntry entry) {
        if (player == null || task == null || entry == null) {
            return BuildResult.taskInvalid(0, 0, 0);
        }
        if (task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.COMPLETED) {
            return BuildResult.taskInvalid(0, 0, 0);
        }

        int targetX = task.getOriginX() + entry.getDx();
        int targetY = task.getOriginY() + entry.getDy();
        int targetZ = task.getOriginZ() + entry.getDz();

        return executeBlockStepDirect(player, task.getOwnerUuid(), task.getPermission(),
                player.worldObj, targetX, targetY, targetZ, entry);
    }

    public static BuildResult executeBlockStep(EntityPlayerMP player, BlueprintTaskManager.BuildTask task, BlueprintBlockEntry entry) {
        if (player == null || task == null || entry == null) {
            return BuildResult.taskInvalid(0, 0, 0);
        }
        if (task.getStatus() == BlueprintTaskManager.STATUS_CANCELLED || task.getStatus() == BlueprintTaskManager.STATUS_COMPLETED) {
            return BuildResult.taskInvalid(0, 0, 0);
        }

        int targetX = task.getAnchorX() + entry.getDx();
        int targetY = task.getAnchorY() + entry.getDy();
        int targetZ = task.getAnchorZ() + entry.getDz();

        TaskPermission perm = TaskPermission.fromId(task.getPermission());
        return executeBlockStepDirect(player, task.getOwnerUuid(), perm,
                player.worldObj, targetX, targetY, targetZ, entry);
    }

    public static BuildResult executeBlockStepDirect(EntityPlayerMP player, UUID taskOwnerUuid, TaskPermission taskPermission,
                                                    World world, int targetX, int targetY, int targetZ, BlueprintBlockEntry entry) {
        // 1. 资格与权限复核
        BuildResultStatus checkStatus = checkQualifications(player, targetX, targetY, targetZ, taskOwnerUuid, taskPermission);
        if (checkStatus != BuildResultStatus.SUCCESS) {
            return new BuildResult(checkStatus, targetX, targetY, targetZ, checkStatus.name());
        }

        if (entry.isAir()) {
            return BuildResult.skipped(targetX, targetY, targetZ);
        }

        // 2. 并发坐标锁防竞态
        int dimension = (world != null && world.provider != null) ? world.provider.dimensionId : player.dimension;
        if (!tryLockCoordinate(dimension, targetX, targetY, targetZ)) {
            return BuildResult.coordinateLocked(targetX, targetY, targetZ);
        }

        try {
            IBlueprintAdapter adapter = VanillaBlueprintAdapter.INSTANCE;

            // 3. 已有结构与配置匹配时跳过，不重复扣料，不扣耐久
            if (adapter.matches(world, targetX, targetY, targetZ, entry)) {
                return BuildResult.skipped(targetX, targetY, targetZ);
            }

            // 4. 检查是否可安全放置（冲突不破坏原世界方块）
            if (!adapter.canPlace(world, targetX, targetY, targetZ, entry)) {
                return BuildResult.conflictOrBlocked(targetX, targetY, targetZ);
            }

            // 5. 三段式事务 - 步骤 1: 预留物料
            List<MaterialRequirement> required = adapter.getRequiredMaterials(entry);
            MaterialReservation reservation = prepareReservation(player, required);
            if (reservation == null) {
                return BuildResult.missingMaterials(targetX, targetY, targetZ);
            }

            // 6. 三段式事务 - 步骤 2: 实际放置
            boolean placed = false;
            try {
                placed = adapter.place(world, targetX, targetY, targetZ, entry, player);
            } catch (Throwable t) {
                ModLog.info("Exception placing block at (" + targetX + "," + targetY + "," + targetZ + "): " + t.getMessage());
                placed = false;
            }

            if (!placed) {
                // 放置失败，释放预留，绝不扣料
                reservation.rollback();
                return BuildResult.conflictOrBlocked(targetX, targetY, targetZ);
            }

            // 7. 三段式事务 - 步骤 3: 提交扣除物料
            boolean committed = reservation.commit();
            if (!committed) {
                ModLog.info("Failed to commit material reservation for " + player.getCommandSenderName());
            }

            // 8. 放置成功扣除法杖耐久 (创造法杖免耐久)
            boolean wandOk = deductWandDurability(player);
            if (!wandOk) {
                return BuildResult.wandExhausted(targetX, targetY, targetZ);
            }

            return BuildResult.success(targetX, targetY, targetZ);
        } finally {
            unlockCoordinate(dimension, targetX, targetY, targetZ);
        }
    }

    // ==========================================
    // 单一部件放置事务 (executePartStep)
    // ==========================================

    public static BuildResult executePartStep(EntityPlayerMP player, BlueprintTask task, BlueprintPartEntry part) {
        if (player == null || task == null || part == null) {
            return BuildResult.taskInvalid(0, 0, 0);
        }
        if (task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.COMPLETED) {
            return BuildResult.taskInvalid(0, 0, 0);
        }

        int targetX = task.getOriginX() + part.getDx();
        int targetY = task.getOriginY() + part.getDy();
        int targetZ = task.getOriginZ() + part.getDz();

        return executePartStepDirect(player, task.getOwnerUuid(), task.getPermission(),
                player.worldObj, targetX, targetY, targetZ, part);
    }

    public static BuildResult executePartStep(EntityPlayerMP player, BlueprintTaskManager.BuildTask task, BlueprintPartEntry part) {
        if (player == null || task == null || part == null) {
            return BuildResult.taskInvalid(0, 0, 0);
        }
        if (task.getStatus() == BlueprintTaskManager.STATUS_CANCELLED || task.getStatus() == BlueprintTaskManager.STATUS_COMPLETED) {
            return BuildResult.taskInvalid(0, 0, 0);
        }

        int targetX = task.getAnchorX() + part.getDx();
        int targetY = task.getAnchorY() + part.getDy();
        int targetZ = task.getAnchorZ() + part.getDz();

        TaskPermission perm = TaskPermission.fromId(task.getPermission());
        return executePartStepDirect(player, task.getOwnerUuid(), perm,
                player.worldObj, targetX, targetY, targetZ, part);
    }

    public static BuildResult executePartStepDirect(EntityPlayerMP player, UUID taskOwnerUuid, TaskPermission taskPermission,
                                                   World world, int targetX, int targetY, int targetZ, BlueprintPartEntry part) {
        // 1. 资格与权限复核
        BuildResultStatus checkStatus = checkQualifications(player, targetX, targetY, targetZ, taskOwnerUuid, taskPermission);
        if (checkStatus != BuildResultStatus.SUCCESS) {
            return new BuildResult(checkStatus, targetX, targetY, targetZ, checkStatus.name());
        }

        // 2. 并发部件锁
        int dimension = (world != null && world.provider != null) ? world.provider.dimensionId : player.dimension;
        if (!tryLockPart(dimension, targetX, targetY, targetZ, part.getSide())) {
            return BuildResult.coordinateLocked(targetX, targetY, targetZ);
        }

        try {
            // 已有同类型部件（含 GT 覆盖板静态数据校正）不重复扣料或耐久。
            if (BlueprintPartSupport.matchesInstalled(world, targetX, targetY, targetZ, part)) {
                return BuildResult.skipped(targetX, targetY, targetZ);
            }

            // 3. 物料需求准备
            List<MaterialRequirement> reqs = new ArrayList<MaterialRequirement>();
            if (part.getRequiredMaterial() != null) {
                reqs.add(part.getRequiredMaterial());
            }

            MaterialReservation reservation = prepareReservation(player, reqs);
            if (reservation == null) {
                return BuildResult.missingMaterials(targetX, targetY, targetZ);
            }

            // 先安装，再提交预留；安装失败不产生任何物料扣除。
            if (!BlueprintPartSupport.installPart(world, targetX, targetY, targetZ, part, player)) {
                reservation.rollback();
                return new BuildResult(BuildResultStatus.CONFLICT_OR_BLOCKED, targetX, targetY, targetZ,
                        "part_install_failed");
            }
            if (!reservation.commit()) {
                ModLog.info("Failed to commit part material reservation for " + player.getCommandSenderName());
                return BuildResult.conflictOrBlocked(targetX, targetY, targetZ);
            }

            // 成功安装后扣除法杖耐久。
            boolean wandOk = deductWandDurability(player);
            if (!wandOk) {
                return BuildResult.wandExhausted(targetX, targetY, targetZ);
            }

            return BuildResult.success(targetX, targetY, targetZ);
        } finally {
            unlockPart(dimension, targetX, targetY, targetZ, part.getSide());
        }
    }

    // ==========================================
    // 批量施工执行调度 (executeBatch)
    // ==========================================

    public static int executeBatch(EntityPlayerMP player, BlueprintTask task, int maxBudget) {
        if (player == null || task == null) return 0;
        BlueprintData snapshot = task.getSnapshot();
        if (snapshot == null) return 0;

        List<BlueprintBlockEntry> blocks = snapshot.getBlockEntries();
        int budget = maxBudget > 0 ? maxBudget : DEFAULT_BATCH_BUDGET;
        int placedCount = 0;
        int matchedOrPlacedTotal = 0;

        for (BlueprintBlockEntry entry : blocks) {
            if (entry == null || entry.isAir()) continue;

            BuildResult result = executeBlockStep(player, task, entry);
            if (result.isSuccess()) {
                placedCount++;
                matchedOrPlacedTotal++;
                if (placedCount >= budget) {
                    break;
                }
            } else if (result.isSkipped()) {
                matchedOrPlacedTotal++;
            } else if (result.status == BuildResultStatus.MISSING_MATERIALS) {
                notifyPlayer(player, "message.freecam_interaction.bp.out_of_materials", "材料不足，建造施工已暂停");
                break;
            } else if (result.status == BuildResultStatus.WAND_EXHAUSTED
                    || result.status == BuildResultStatus.NOT_IN_FREECAM
                    || result.status == BuildResultStatus.PERMISSION_DENIED) {
                break;
            }
        }

        task.setPlacedBlocks(matchedOrPlacedTotal);
        if (matchedOrPlacedTotal >= snapshot.getNonAirBlockCount() && snapshot.getNonAirBlockCount() > 0) {
            task.setStatus(TaskStatus.COMPLETED);
            ModLog.info("BuildTask completed: " + task.getTaskId());
        } else if (task.getStatus() == TaskStatus.PENDING && placedCount > 0) {
            task.setStatus(TaskStatus.IN_PROGRESS);
        }

        local.freecaminteraction.blueprint.storage.BlueprintTaskManager.saveTask(player.worldObj, task);
        return placedCount;
    }

    public static int executeBatch(EntityPlayerMP player, BlueprintTaskManager.BuildTask task, int maxBudget) {
        if (player == null || task == null) return 0;
        BlueprintData snapshot = task.getBlueprintSnapshot();
        if (snapshot == null) return 0;

        List<BlueprintBlockEntry> blocks = snapshot.getBlockEntries();
        int budget = maxBudget > 0 ? maxBudget : DEFAULT_BATCH_BUDGET;
        int placedCount = 0;
        int matchedOrPlacedTotal = 0;

        for (BlueprintBlockEntry entry : blocks) {
            if (entry == null || entry.isAir()) continue;

            BuildResult result = executeBlockStep(player, task, entry);
            if (result.isSuccess()) {
                placedCount++;
                matchedOrPlacedTotal++;
                if (placedCount >= budget) {
                    break;
                }
            } else if (result.isSkipped()) {
                matchedOrPlacedTotal++;
            } else if (result.status == BuildResultStatus.MISSING_MATERIALS) {
                notifyPlayer(player, "message.freecam_interaction.bp.out_of_materials", "材料不足，建造施工已暂停");
                break;
            } else if (result.status == BuildResultStatus.WAND_EXHAUSTED
                    || result.status == BuildResultStatus.NOT_IN_FREECAM
                    || result.status == BuildResultStatus.PERMISSION_DENIED) {
                break;
            }
        }

        if (matchedOrPlacedTotal >= snapshot.getNonAirBlockCount() && snapshot.getNonAirBlockCount() > 0) {
            task.setStatus(BlueprintTaskManager.STATUS_COMPLETED);
            ModLog.info("BuildTask completed: " + task.getTaskId());
        } else if (task.getStatus() == BlueprintTaskManager.STATUS_PENDING && placedCount > 0) {
            task.setStatus(BlueprintTaskManager.STATUS_BUILDING);
        }

        return placedCount;
    }

    // ==========================================
    // 网络层对接接口：响应 PacketTaskAction.ACTION_START_BUILD
    // ==========================================

    public static int executeTaskBuild(EntityPlayerMP player, String taskId) {
        if (player == null || taskId == null || taskId.isEmpty()) {
            return 0;
        }

        // 首先从持久化任务管理器查找
        BlueprintTask storageTask = local.freecaminteraction.blueprint.storage.BlueprintTaskManager.getTask(player.worldObj, taskId);
        if (storageTask != null) {
            int count = executeBatch(player, storageTask, DEFAULT_BATCH_BUDGET);
            ModLog.info("Executed build step for task=" + taskId + " by " + player.getCommandSenderName() + "; placed=" + count);
            return count;
        }

        // 回退查找网络层活动任务
        BlueprintTaskManager.BuildTask netTask = BlueprintTaskManager.getTask(taskId);
        if (netTask != null) {
            int count = executeBatch(player, netTask, DEFAULT_BATCH_BUDGET);
            ModLog.info("Executed build step for net task=" + taskId + " by " + player.getCommandSenderName() + "; placed=" + count);
            return count;
        }

        ModLog.info("Build action rejected: task not found taskId=" + taskId + " for player=" + player.getCommandSenderName());
        return 0;
    }
}
