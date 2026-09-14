package local.freecaminteraction.blueprint.build;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import local.freecaminteraction.FreecamInteraction;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.ae2.Ae2Integration;
import local.freecaminteraction.blueprint.BlueprintBlockEntry;
import local.freecaminteraction.blueprint.BlueprintData;
import local.freecaminteraction.blueprint.BlueprintPartEntry;
import local.freecaminteraction.blueprint.BlueprintPartSupport;
import local.freecaminteraction.blueprint.MaterialRequirement;
import local.freecaminteraction.blueprint.VanillaBlueprintAdapter;
import local.freecaminteraction.blueprint.network.BlueprintNetwork;
import local.freecaminteraction.blueprint.network.BlueprintTaskManager;
import local.freecaminteraction.blueprint.network.PacketTaskSync;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockVine;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 服务端蓝图施工调度器。
 * <p>
 * 职责规范：
 * 1. 管理当前世界所有激活的建造施工进程（ActiveBuildSession）；
 * 2. 接入服务端 Tick（TickEvent.ServerTickEvent），每 Tick 按照预算（默认最多处理 16-32 个方块操作）推进各任务；
 * 3. 依赖拓扑排序（DependencySort）：
 *    - Phase 0 (SOLID_BASE): 固体实心方块优先放置（基体）；从底部 Y 递增，保证支撑；
 *    - Phase 1 (ATTACHED): 附着方块（火把、梯子、铁轨、门、告示牌、红石线等需要依附面的方块）后放置；
 *    - Phase 2 (PARTS): 附属部件（AE2 线缆部件、GT 覆盖板）在基体放置成功后再安装；
 *    - Phase 3 (FINALIZE_CONFIG): 连通性与配置恢复（GT 管线各面断通、TileEntity 静态配置）在最后一步进行更新通知；
 *    - 严格遵守规则：蓝图中的空气方块（Blocks.air）绝对不执行挖掘，不破坏已有地形。
 * 4. 结构复核与跳过：
 *    - 遇到已有方块与蓝图完全匹配（block, meta, 关键配置一致）时，直接跳过并增加进度计数，不重复扣除材料或耐久；
 *    - 遇到已有方块为异种方块或发生配置冲突时，记录冲突位置并跳过，禁止自动拆除、清空容器或破坏。
 * 5. 完工通知：
 *    - 当任务中所有有效方块与部件全部就位，更新任务状态为 COMPLETED，并触发广播同步。
 * 6. 调度控制接口：
 *    - startBuild(EntityPlayerMP player, String taskId)
 *    - pauseBuild(String taskId)
 *    - cancelBuild(String taskId)
 */
public final class BlueprintBuildScheduler {
    public static final BlueprintBuildScheduler INSTANCE = new BlueprintBuildScheduler();

    /** 每 Tick 处理的最多方块操作预算配额 (16-32) */
    public static final int DEFAULT_BUDGET_PER_TICK = 16;

    /** 激活的施工会话 Map<taskId, ActiveBuildSession> */
    private final Map<String, ActiveBuildSession> activeSessions = new ConcurrentHashMap<String, ActiveBuildSession>();
    private boolean registered = false;

    private BlueprintBuildScheduler() {}

    /**
     * 注册到 FML 事件总线监听服务端 Tick。
     */
    public synchronized void register() {
        if (!registered) {
            FMLCommonHandler.instance().bus().register(this);
            registered = true;
            ModLog.info("BlueprintBuildScheduler registered to FML bus.");
        }
    }

    /**
     * 施工操作阶段定义。
     */
    public enum BuildPhase {
        /** 0: 固体实心方块基体 */
        SOLID_BASE(0),
        /** 1: 依附方块（火把、梯子、告示牌、红石等） */
        ATTACHED(1),
        /** 2: 独立/附属部件（AE2 部件、GT 覆盖板） */
        PARTS(2),
        /** 3: 最终配置还原与连通性通知 */
        FINALIZE_CONFIG(3);

        private final int priority;

        BuildPhase(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }
    }

    /**
     * 拓扑排序后的单个可执行构建工作单元。
     */
    public static final class BuildUnit {
        private final BuildPhase phase;
        private final int dx, dy, dz;
        private final BlueprintBlockEntry blockEntry;
        private final BlueprintPartEntry partEntry;

        public BuildUnit(BuildPhase phase, int dx, int dy, int dz, BlueprintBlockEntry blockEntry, BlueprintPartEntry partEntry) {
            this.phase = phase;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.blockEntry = blockEntry;
            this.partEntry = partEntry;
        }

        public static BuildUnit forBlock(BuildPhase phase, BlueprintBlockEntry entry) {
            return new BuildUnit(phase, entry.getDx(), entry.getDy(), entry.getDz(), entry, null);
        }

        public static BuildUnit forPart(BlueprintPartEntry entry) {
            return new BuildUnit(BuildPhase.PARTS, entry.getDx(), entry.getDy(), entry.getDz(), null, entry);
        }

        public static BuildUnit forFinalize(BlueprintBlockEntry entry) {
            return new BuildUnit(BuildPhase.FINALIZE_CONFIG, entry.getDx(), entry.getDy(), entry.getDz(), entry, null);
        }

        public BuildPhase getPhase() {
            return phase;
        }

        public int getDx() {
            return dx;
        }

        public int getDy() {
            return dy;
        }

        public int getDz() {
            return dz;
        }

        public BlueprintBlockEntry getBlockEntry() {
            return blockEntry;
        }

        public BlueprintPartEntry getPartEntry() {
            return partEntry;
        }
    }

    /**
     * 激活的建造施工进程上下文。
     */
    public static final class ActiveBuildSession {
        private final String taskId;
        private final UUID builderUuid;
        private final int dimension;
        private final int anchorX, anchorY, anchorZ;
        private final BlueprintTaskManager.BuildTask task;
        private final List<BuildUnit> executionPlan;
        private final String ae2SourceId;
        private int currentIndex = 0;
        private boolean paused = false;
        private final List<String> conflicts = new ArrayList<String>();
        private int placedCount = 0;
        private int skippedCount = 0;

        public ActiveBuildSession(EntityPlayerMP player, BlueprintTaskManager.BuildTask task) {
            this.taskId = task.getTaskId();
            this.builderUuid = player.getUniqueID();
            this.dimension = task.getDimension();
            this.anchorX = task.getAnchorX();
            this.anchorY = task.getAnchorY();
            this.anchorZ = task.getAnchorZ();
            this.task = task;
            this.executionPlan = generateDependencySortedPlan(task.getBlueprintSnapshot());
            this.ae2SourceId = Ae2Integration.selectedSource(player);
        }

        public String getTaskId() {
            return taskId;
        }

        public UUID getBuilderUuid() {
            return builderUuid;
        }

        public int getDimension() {
            return dimension;
        }

        public int getAnchorX() {
            return anchorX;
        }

        public int getAnchorY() {
            return anchorY;
        }

        public int getAnchorZ() {
            return anchorZ;
        }

        public String getAe2SourceId() {
            return ae2SourceId;
        }

        public BlueprintTaskManager.BuildTask getTask() {
            return task;
        }

        public boolean isPaused() {
            return paused;
        }

        public void setPaused(boolean paused) {
            this.paused = paused;
        }

        public boolean isFinished() {
            return currentIndex >= executionPlan.size();
        }

        public int getTotalUnits() {
            return executionPlan.size();
        }

        public int getCurrentIndex() {
            return currentIndex;
        }

        public List<String> getConflicts() {
            return conflicts;
        }

        public int getPlacedCount() {
            return placedCount;
        }

        public int getSkippedCount() {
            return skippedCount;
        }
    }

    /**
     * 启动或恢复任务施工。
     * 校验施工玩家的自由视角状态、生效法杖蓝图核心资格、世界范围以及任务权限。
     */
    public boolean startBuild(EntityPlayerMP player, String taskId) {
        if (player == null || taskId == null || taskId.isEmpty()) {
            return false;
        }

        // 1. 强校验：是否在自由视角模式
        if (!FreecamInteraction.active(player)) {
            ModLog.info("startBuild rejected: player " + player.getCommandSenderName() + " is not in freecam");
            notifyPlayer(player, "message.freecam_interaction.bp.need_freecam");
            return false;
        }

        // 2. 强校验：当前生效法杖是否装有蓝图核心
        if (!FreecamInteraction.hasBlueprintCoreWand(player)) {
            ModLog.info("startBuild rejected: player " + player.getCommandSenderName() + " has no active blueprint core");
            notifyPlayer(player, "message.freecam_interaction.bp.need_core");
            return false;
        }

        // 3. 校验任务存在与建造权限
        BlueprintTaskManager.BuildTask task = BlueprintTaskManager.getTask(taskId);
        if (task == null) {
            ModLog.info("startBuild rejected: task not found id=" + taskId);
            notifyPlayer(player, "message.freecam_interaction.bp.task_unavailable");
            return false;
        }

        if (task.getStatus() == BlueprintTaskManager.STATUS_COMPLETED || task.getStatus() == BlueprintTaskManager.STATUS_CANCELLED) {
            ModLog.info("startBuild rejected: task " + taskId + " is already finished/cancelled");
            notifyPlayer(player, "message.freecam_interaction.bp.task_unavailable");
            return false;
        }

        if (!task.canBuild(player)) {
            ModLog.info("startBuild rejected: player " + player.getCommandSenderName() + " has no build permission on task " + taskId);
            notifyPlayer(player, "message.freecam_interaction.bp.no_permission");
            return false;
        }

        // 4. 校验范围：任务基准点是否在玩家自由视角允许范围与同维度内
        if (player.dimension != task.getDimension()) {
            ModLog.info("startBuild rejected: dimension mismatch player=" + player.dimension + ", task=" + task.getDimension());
            notifyPlayer(player, "message.freecam_interaction.bp.build_out_of_range");
            return false;
        }
        if (!FreecamInteraction.inside(player, task.getAnchorX(), task.getAnchorY(), task.getAnchorZ())) {
            ModLog.info("startBuild rejected: task anchor (" + task.getAnchorX() + "," + task.getAnchorY() + "," + task.getAnchorZ() + ") is out of wand range");
            notifyPlayer(player, "message.freecam_interaction.bp.build_out_of_range");
            return false;
        }

        // 5. 检查是否已有该任务的活跃会话
        ActiveBuildSession session = activeSessions.get(taskId);
        if (session != null) {
            if (session.isPaused()) {
                session.setPaused(false);
                task.setStatus(BlueprintTaskManager.STATUS_BUILDING);
                broadcastTaskStatus(task);
                ModLog.info("Resumed active build session for task: " + taskId + " by " + player.getCommandSenderName());
                return true;
            }
            ModLog.info("Build session for task " + taskId + " is already running");
            return true;
        }

        // 6. 创建新的活跃会话并加入调度
        session = new ActiveBuildSession(player, task);
        activeSessions.put(taskId, session);
        task.setStatus(BlueprintTaskManager.STATUS_BUILDING);
        broadcastTaskStatus(task);
        ModLog.info("Started new build session for task: " + taskId + " by " + player.getCommandSenderName() + " [totalUnits=" + session.getTotalUnits() + "]");
        return true;
    }

    /**
     * 施工请求被拒或状态变化时给玩家反馈，避免“点了没反应”。
     */
    private static void notifyPlayer(EntityPlayerMP player, String messageKey) {
        if (player == null) return;
        try {
            player.addChatMessage(new ChatComponentTranslation(messageKey));
        } catch (Throwable ignored) {
            // 测试桩或无网络环境下忽略
        }
    }

    /**
     * 暂停任务施工。
     */
    public boolean pauseBuild(String taskId) {
        if (taskId == null) return false;
        ActiveBuildSession session = activeSessions.get(taskId);
        if (session != null) {
            session.setPaused(true);
            BlueprintTaskManager.BuildTask task = session.getTask();
            if (task != null && task.getStatus() == BlueprintTaskManager.STATUS_BUILDING) {
                task.setStatus(BlueprintTaskManager.STATUS_PENDING);
                broadcastTaskStatus(task);
            }
            ModLog.info("Paused build session for task: " + taskId);
            return true;
        }
        return false;
    }

    /**
     * 停止并释放任务调度。
     */
    public boolean cancelBuild(String taskId) {
        if (taskId == null) return false;
        ActiveBuildSession session = activeSessions.remove(taskId);
        if (session != null) {
            session.setPaused(true);
            ModLog.info("Cancelled active build session for task: " + taskId);
            return true;
        }
        return false;
    }

    /**
     * 获取指定任务的活跃施工会话。
     */
    public ActiveBuildSession getSession(String taskId) {
        if (taskId == null) return null;
        return activeSessions.get(taskId);
    }

    /**
     * 服务端 Tick 驱动事件。
     * 每 Tick 遍历所有未暂停的活跃建造会话，在操作预算（DEFAULT_BUDGET_PER_TICK）内推进施工。
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (activeSessions.isEmpty()) {
            return;
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }

        Iterator<Map.Entry<String, ActiveBuildSession>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActiveBuildSession> entry = it.next();
            ActiveBuildSession session = entry.getValue();

            if (session.isPaused()) {
                continue;
            }

            // 查找施工玩家实体
            EntityPlayerMP player = getOnlinePlayerByUuid(server, session.getBuilderUuid());
            if (player == null || player.isDead || player.dimension != session.getDimension()) {
                ModLog.info("Pausing session " + session.getTaskId() + ": builder is offline or switched dimension");
                session.setPaused(true);
                session.getTask().setStatus(BlueprintTaskManager.STATUS_PENDING);
                broadcastTaskStatus(session.getTask());
                continue;
            }

            // 校验自由视角与核心资格
            if (!FreecamInteraction.active(player) || !FreecamInteraction.hasBlueprintCoreWand(player)) {
                ModLog.info("Pausing session " + session.getTaskId() + ": builder lost freecam or blueprint core");
                session.setPaused(true);
                session.getTask().setStatus(BlueprintTaskManager.STATUS_PENDING);
                broadcastTaskStatus(session.getTask());
                continue;
            }

            // 校验施工权限是否仍然满足
            if (!session.getTask().canBuild(player)) {
                ModLog.info("Aborting session " + session.getTaskId() + ": build permission revoked");
                session.setPaused(true);
                session.getTask().setStatus(BlueprintTaskManager.STATUS_PENDING);
                broadcastTaskStatus(session.getTask());
                it.remove();
                continue;
            }

            WorldServer world = server.worldServerForDimension(session.getDimension());
            if (world == null) {
                session.setPaused(true);
                continue;
            }

            // 推进本会话的施工步骤（受预算控制）
            boolean hasRemainingBudget = processSession(session, player, world, DEFAULT_BUDGET_PER_TICK);

            // 检查会话是否已经全部完成
            if (session.isFinished()) {
                BlueprintTaskManager.BuildTask finished = session.getTask();
                ModLog.info("Build session completed for task: " + session.getTaskId()
                        + " [placed=" + session.placedCount + ", skipped=" + session.skippedCount
                        + ", conflicts=" + session.conflicts.size() + "]");
                // 完工任务无追溯价值：直接从任务表移除并通知客户端撤下（与取消同路径），
                // 否则列表会长期保留已完工任务，再次点击只会得到“任务不存在或已结束”。
                if (BlueprintTaskManager.removeTask(session.getTaskId()) == null) {
                    session.setPaused(true);
                    finished.setStatus(BlueprintTaskManager.STATUS_PENDING);
                    notifyPlayer(player, "message.freecam_interaction.bp.task_save_failed");
                    continue;
                }
                BlueprintNetwork.broadcastTaskRemove(finished, 64.0D);
                notifyPlayer(player, "message.freecam_interaction.bp.task_completed");
                session.setPaused(true);
                it.remove();
            }
        }
    }

    /**
     * 单个会话在当前 Tick 执行有限步骤。
     */
    private boolean processSession(ActiveBuildSession session, EntityPlayerMP player, World world, int budget) {
        int ops = 0;
        while (ops < budget && !session.isFinished()) {
            BuildUnit unit = session.executionPlan.get(session.currentIndex);
            int wx = session.anchorX + unit.getDx();
            int wy = session.anchorY + unit.getDy();
            int wz = session.anchorZ + unit.getDz();

            // 越界检查
            if (wy < 0 || wy >= world.getHeight()) {
                session.currentIndex++;
                continue;
            }

            // 范围与加载检查：若目标坐标超出玩家自由视角范围或区块未加载，暂停等待
            if (!FreecamInteraction.inside(player, wx, wy, wz)) {
                ModLog.info("Session paused on task " + session.getTaskId() + ": location (" + wx + "," + wy + "," + wz + ") outside wand range");
                notifyPlayer(player, "message.freecam_interaction.bp.build_out_of_range");
                session.setPaused(true);
                session.getTask().setStatus(BlueprintTaskManager.STATUS_PENDING);
                broadcastTaskStatus(session.getTask());
                return false;
            }

            if (!world.blockExists(wx, wy, wz)) {
                // 区块未加载，暂停施工避免异常
                ModLog.info("Session paused on task " + session.getTaskId() + ": chunk at (" + (wx >> 4) + "," + (wz >> 4) + ") not loaded");
                notifyPlayer(player, "message.freecam_interaction.bp.chunk_unloaded");
                session.setPaused(true);
                session.getTask().setStatus(BlueprintTaskManager.STATUS_PENDING);
                broadcastTaskStatus(session.getTask());
                return false;
            }

            boolean advance = executeUnit(session, player, world, unit, wx, wy, wz);
            ops++;

            if (advance) {
                session.currentIndex++;
            } else {
                // 缺料或发生不可逾越阻塞，暂停会话保留进度
                session.setPaused(true);
                session.getTask().setStatus(BlueprintTaskManager.STATUS_PENDING);
                broadcastTaskStatus(session.getTask());
                return false;
            }
        }
        return true;
    }

    /**
     * 执行单个工作单元。
     */
    private boolean executeUnit(ActiveBuildSession session, EntityPlayerMP player, World world,
                                BuildUnit unit, int wx, int wy, int wz) {
        switch (unit.getPhase()) {
            case SOLID_BASE:
            case ATTACHED:
                return executeBlockUnit(session, player, world, unit.getBlockEntry(), wx, wy, wz);
            case PARTS:
                return executePartUnit(session, player, world, unit.getPartEntry(), wx, wy, wz);
            case FINALIZE_CONFIG:
                return executeFinalizeUnit(session, player, world, unit.getBlockEntry(), wx, wy, wz);
            default:
                return true;
        }
    }

    /**
     * 执行方块放置 / 复核。
     */
    private boolean executeBlockUnit(ActiveBuildSession session, EntityPlayerMP player, World world,
                                     BlueprintBlockEntry entry, int wx, int wy, int wz) {
        if (entry == null || entry.isAir()) {
            // 规则：蓝图中的空气方块（Blocks.air）绝对不执行挖掘，不破坏已有地形
            session.skippedCount++;
            return true;
        }

        Block actualBlock = world.getBlock(wx, wy, wz);
        int actualMeta = world.getBlockMetadata(wx, wy, wz);
        VanillaBlueprintAdapter adapter = VanillaBlueprintAdapter.INSTANCE;

        // 1. 结构复核：已有方块与蓝图完全匹配
        if (adapter.matches(world, wx, wy, wz, entry)) {
            session.skippedCount++;
            return true;
        }

        // 2. 结构冲突复核：已有方块存在且不是可替换方块（非空气/雪层/高草等），且与蓝图不匹配
        boolean isReplaceable = (actualBlock == null || actualBlock.isAir(world, wx, wy, wz) || actualBlock.isReplaceable(world, wx, wy, wz));
        if (!isReplaceable) {
            // 遇到已有方块为异种方块或发生配置冲突时，记录冲突位置并跳过，禁止自动拆除、清空容器或破坏
            String conflictMsg = "Block conflict at (" + wx + "," + wy + "," + wz + "): expected "
                    + entry.getBlockRegistryName() + ":" + entry.getMetadata()
                    + ", found " + Block.blockRegistry.getNameForObject(actualBlock) + ":" + actualMeta;
            session.conflicts.add(conflictMsg);
            ModLog.info("BuildTask " + session.getTaskId() + " - " + conflictMsg);
            session.skippedCount++;
            return true;
        }

        // 3. 预留物料；放置成功后才提交物料和耐久。
        List<MaterialRequirement> requiredMaterials = adapter.getRequiredMaterials(entry);
        if (!FreecamInteraction.canDeductUsage(player)) {
            notifyPlayer(player, "message.freecam_interaction.bp.wand_exhausted");
            return false;
        }
        MaterialTransaction materials = MaterialTransaction.reserve(player, requiredMaterials, session.getAe2SourceId());
        if (materials == null) {
            ModLog.info("BuildTask " + session.getTaskId() + " paused: missing materials for block "
                    + entry.getBlockRegistryName() + " at (" + wx + "," + wy + "," + wz + ")");
            return false;
        }

        // 4. 执行实际方块与 TileEntity 放置
        boolean success = adapter.place(world, wx, wy, wz, entry, player);
        if (success) {
            if (!FreecamInteraction.deductUsage(player)) {
                materials.rollback();
                ModLog.info("BuildTask " + session.getTaskId() + ": placed block but wand charge changed unexpectedly");
                return false;
            }
            materials.commit();
            session.placedCount++;
            return true;
        } else {
            materials.rollback();
            ModLog.info("BuildTask " + session.getTaskId() + ": adapter.place failed at (" + wx + "," + wy + "," + wz + ")");
            return true;
        }
    }

    /**
     * 执行独立/附属部件安装（AE2 部件、GT 覆盖板等）。
     */
    private boolean executePartUnit(ActiveBuildSession session, EntityPlayerMP player, World world,
                                    BlueprintPartEntry partEntry, int wx, int wy, int wz) {
        if (partEntry == null) {
            return true;
        }

        // 宿主方块必须存在；空位仅允许 AE2 部件自建总线宿主（GT 覆盖板等必须已有机器本体）
        Block hostBlock = world.getBlock(wx, wy, wz);
        if ((hostBlock == null || hostBlock.isAir(world, wx, wy, wz))
                && !BlueprintPartSupport.canSelfHost(partEntry)) {
            String conflictMsg = "Part host missing at (" + wx + "," + wy + "," + wz + ") for part " + partEntry.getPartId();
            session.conflicts.add(conflictMsg);
            session.skippedCount++;
            return true;
        }

        // 已存在同类型部件：视为已完成，不重复扣料扣耐久
        if (BlueprintPartSupport.matchesInstalled(world, wx, wy, wz, partEntry)) {
            session.skippedCount++;
            return true;
        }

        MaterialRequirement mat = partEntry.getRequiredMaterial();
        List<MaterialRequirement> reqList = mat != null ? Collections.singletonList(mat) : Collections.<MaterialRequirement>emptyList();

        if (!FreecamInteraction.canDeductUsage(player)) {
            notifyPlayer(player, "message.freecam_interaction.bp.wand_exhausted");
            return false;
        }
        MaterialTransaction materials = MaterialTransaction.reserve(player, reqList, session.getAe2SourceId());
        if (materials == null) {
            ModLog.info("BuildTask " + session.getTaskId() + " paused: missing material for part "
                    + partEntry.getPartId() + " at (" + wx + "," + wy + "," + wz + ")");
            return false;
        }

        // 耐久已在改变世界前预检；服务端单线程内安装成功后再实际扣除。
        boolean installed = BlueprintPartSupport.installPart(world, wx, wy, wz, partEntry, player);
        if (installed) {
            if (!FreecamInteraction.deductUsage(player)) {
                materials.rollback();
                ModLog.info("BuildTask " + session.getTaskId() + ": installed part but wand charge changed unexpectedly");
                return false;
            }
            materials.commit();
            session.placedCount++;
        } else {
            materials.rollback();
            session.conflicts.add("Part install failed at (" + wx + "," + wy + "," + wz + ") for part "
                    + partEntry.getPartId());
            session.skippedCount++;
            ModLog.info("BuildTask " + session.getTaskId() + ": part install failed "
                    + partEntry.getPartId() + " at (" + wx + "," + wy + "," + wz + ")");
        }
        return true;
    }

    /**
     * 执行连通性与配置恢复。
     * 在基体与部件全部就位后，最后一步触发方块与 TileEntity 的邻居通知与静态配置刷新。
     */
    private boolean executeFinalizeUnit(ActiveBuildSession session, EntityPlayerMP player, World world,
                                        BlueprintBlockEntry entry, int wx, int wy, int wz) {
        if (entry == null || entry.isAir()) {
            return true;
        }
        Block block = world.getBlock(wx, wy, wz);
        if (block != null && !block.isAir(world, wx, wy, wz)) {
            // 与放置路径共用同一安全入口；GT 朝向只走 setFrontFacing，绝不回灌残缺 NBT。
            VanillaBlueprintAdapter.INSTANCE.applyStaticConfiguration(world, wx, wy, wz, entry);
            world.notifyBlocksOfNeighborChange(wx, wy, wz, block);
            world.markBlockForUpdate(wx, wy, wz);
        }
        return true;
    }

    /**
     * 依赖拓扑排序（DependencySort）：
     * 1. 固体实心方块优先放置（基体），按 Y 坐标从下到上放置，保证附着物有地基；
     * 2. 附着方块（火把、梯子、铁轨、门、告示牌等依附物）后放置；
     * 3. 附属部件（AE2 线缆部件、GT 覆盖板）在基体成功放置后再安装；
     * 4. 连通性与配置恢复（GT 管线各面断通、TileEntity 静态配置）在最后一步进行更新通知；
     * 5. 严格遵守规则：蓝图中的空气方块（Blocks.air）绝对不执行挖掘，不破坏已有地形。
     */
    public static List<BuildUnit> generateDependencySortedPlan(BlueprintData blueprint) {
        if (blueprint == null) {
            return Collections.emptyList();
        }

        List<BlueprintBlockEntry> rawBlocks = blueprint.getBlockEntries();
        List<BlueprintPartEntry> rawParts = blueprint.getPartEntries();

        List<BuildUnit> solidUnits = new ArrayList<BuildUnit>();
        List<BuildUnit> attachedUnits = new ArrayList<BuildUnit>();
        List<BuildUnit> partUnits = new ArrayList<BuildUnit>();
        List<BuildUnit> finalizeUnits = new ArrayList<BuildUnit>();

        // 1. 分类方块为基体固体或附着物
        for (BlueprintBlockEntry entry : rawBlocks) {
            if (entry == null || entry.isAir()) {
                continue; // 过滤空气
            }
            if (isAttachedBlock(entry.getBlock())) {
                attachedUnits.add(BuildUnit.forBlock(BuildPhase.ATTACHED, entry));
            } else {
                solidUnits.add(BuildUnit.forBlock(BuildPhase.SOLID_BASE, entry));
            }
            // 仅真正包含可安全恢复配置的 NBT 才生成最终配置单元；
            // 只有 TileEntity id 的旧 GT 蓝图不能再次 readFromNBT 清空 MTE。
            if (VanillaBlueprintAdapter.INSTANCE.sanitizeTileTag(entry.getTileTag()) != null) {
                finalizeUnits.add(BuildUnit.forFinalize(entry));
            }
        }

        // 2. 基体固体方块：优先按 Y 递增排序（底层优先放置建立支撑），相同 Y 时按 X/Z 稳定排序
        Collections.sort(solidUnits, new Comparator<BuildUnit>() {
            @Override
            public int compare(BuildUnit o1, BuildUnit o2) {
                if (o1.getDy() != o2.getDy()) return Integer.compare(o1.getDy(), o2.getDy());
                if (o1.getDx() != o2.getDx()) return Integer.compare(o1.getDx(), o2.getDx());
                return Integer.compare(o1.getDz(), o2.getDz());
            }
        });

        // 3. 附着方块：同样按 Y 递增排序
        Collections.sort(attachedUnits, new Comparator<BuildUnit>() {
            @Override
            public int compare(BuildUnit o1, BuildUnit o2) {
                if (o1.getDy() != o2.getDy()) return Integer.compare(o1.getDy(), o2.getDy());
                if (o1.getDx() != o2.getDx()) return Integer.compare(o1.getDx(), o2.getDx());
                return Integer.compare(o1.getDz(), o2.getDz());
            }
        });

        // 4. 独立部件条目
        for (BlueprintPartEntry part : rawParts) {
            if (part != null) {
                partUnits.add(BuildUnit.forPart(part));
            }
        }

        // 合并完整依赖拓扑执行计划：Phase 0 -> Phase 1 -> Phase 2 -> Phase 3
        List<BuildUnit> plan = new ArrayList<BuildUnit>(solidUnits.size() + attachedUnits.size() + partUnits.size() + finalizeUnits.size());
        plan.addAll(solidUnits);
        plan.addAll(attachedUnits);
        plan.addAll(partUnits);
        plan.addAll(finalizeUnits);

        return Collections.unmodifiableList(plan);
    }

    /**
     * 判断方块是否为附着型方块（需要依附于实体表面）。
     */
    public static boolean isAttachedBlock(Block block) {
        if (block == null) return false;
        if (block instanceof BlockTorch) return true;
        if (block instanceof BlockLadder) return true;
        if (block instanceof BlockSign) return true;
        if (block instanceof BlockLever) return true;
        if (block instanceof BlockButton) return true;
        if (block instanceof BlockDoor) return true;
        if (block instanceof BlockTrapDoor) return true;
        if (block instanceof BlockRailBase) return true;
        if (block instanceof BlockVine) return true;
        if (block instanceof BlockBush) return true;
        return false;
    }

    private static EntityPlayerMP getOnlinePlayerByUuid(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null || server.getConfigurationManager() == null) {
            return null;
        }
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) obj;
                if (uuid.equals(p.getUniqueID())) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * 广播任务状态同步包。
     */
    public static void broadcastTaskStatus(BlueprintTaskManager.BuildTask task) {
        if (task == null) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        double radius = 64.0D;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) obj;
                if (p.dimension == task.getDimension()) {
                    double distSq = p.getDistanceSq(task.getAnchorX(), task.getAnchorY(), task.getAnchorZ());
                    if (distSq <= radius * radius) {
                        boolean isOwner = task.isOwner(p);
                        if (isOwner || task.getPermission() != BlueprintTaskManager.PERM_HIDDEN) {
                            BlueprintNetwork.sendTo(PacketTaskSync.upsert(
                                    task.getTaskId(), task.getBlueprintId(), task.getBlueprintName(), task.getOwnerName(),
                                    task.getDimension(), task.getAnchorX(), task.getAnchorY(), task.getAnchorZ(),
                                    task.getPermission(), task.isShowOutside(), task.getStatus(), isOwner
                            ), p);
                        }
                    }
                }
            }
        }
    }
}
