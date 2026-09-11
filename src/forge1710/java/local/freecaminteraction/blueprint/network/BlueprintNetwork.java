package local.freecaminteraction.blueprint.network;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import local.freecaminteraction.FreecamInteraction;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.blueprint.BlueprintData;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

/**
 * 蓝图系统网络通信层通道与调度中心。
 * 专属通道: "freecam_bp"
 * 消息 ID 范围: 10..20
 * 确保消息在两端均安全排队路由至主线程执行，防止线程并发与服务端加载客户端类错误。
 */
public final class BlueprintNetwork {
    public static final String CHANNEL_NAME = "freecam_bp";

    // Discriminator 分配 (10..20)
    public static final int ID_CAPTURE_REQUEST = 10;
    public static final int ID_CAPTURE_ACK = 11;
    public static final int ID_LIST_REQUEST = 12;
    public static final int ID_LIST_RESPONSE = 13;
    public static final int ID_SLICE = 14;
    public static final int ID_TASK_ACTION = 15;
    public static final int ID_TASK_SYNC = 16;
    public static final int ID_SLICE_REQUEST = 17;

    private static SimpleNetworkWrapper channel;

    // 主线程调度队列
    private static final Queue<Runnable> SERVER_QUEUE = new ConcurrentLinkedQueue<Runnable>();
    private static final Queue<Runnable> CLIENT_QUEUE = new ConcurrentLinkedQueue<Runnable>();

    private BlueprintNetwork() {}

    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);

        // 注册消息
        channel.registerMessage(CaptureRequestHandler.class, PacketCaptureRequest.class, ID_CAPTURE_REQUEST, Side.SERVER);
        channel.registerMessage(CaptureAckHandler.class, PacketCaptureAck.class, ID_CAPTURE_ACK, Side.CLIENT);
        channel.registerMessage(ListRequestHandler.class, PacketBlueprintListRequest.class, ID_LIST_REQUEST, Side.SERVER);
        channel.registerMessage(ListResponseHandler.class, PacketBlueprintListResponse.class, ID_LIST_RESPONSE, Side.CLIENT);
        channel.registerMessage(SliceHandler.class, PacketBlueprintSlice.class, ID_SLICE, Side.CLIENT);
        channel.registerMessage(TaskActionHandler.class, PacketTaskAction.class, ID_TASK_ACTION, Side.SERVER);
        channel.registerMessage(TaskSyncHandler.class, PacketTaskSync.class, ID_TASK_SYNC, Side.CLIENT);
        channel.registerMessage(SliceRequestHandler.class, PacketBlueprintSliceRequest.class, ID_SLICE_REQUEST, Side.SERVER);

        // 注册主线程 Tick 监听器
        FMLCommonHandler.instance().bus().register(new BlueprintNetworkTickListener());
        ModLog.info("Blueprint network layer initialized with channel: " + CHANNEL_NAME);
    }

    public static SimpleNetworkWrapper getChannel() {
        return channel;
    }

    public static void sendToServer(IMessage message) {
        if (channel != null && message != null) {
            channel.sendToServer(message);
        }
    }

    public static void sendTo(IMessage message, EntityPlayerMP player) {
        if (channel != null && message != null && player != null) {
            channel.sendTo(message, player);
        }
    }

    public static void sendToAllNear(IMessage message, int dimension, double x, double y, double z, double range) {
        if (channel != null && message != null) {
            channel.sendToAllAround(message, new NetworkRegistry.TargetPoint(dimension, x, y, z, range));
        }
    }

    public static void runOnServer(Runnable task) {
        if (task != null) {
            SERVER_QUEUE.add(task);
        }
    }

    public static void runOnClient(Runnable task) {
        if (task != null) {
            CLIENT_QUEUE.add(task);
        }
    }

    public static final class BlueprintNetworkTickListener {
        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                Runnable r;
                while ((r = SERVER_QUEUE.poll()) != null) {
                    try {
                        r.run();
                    } catch (Throwable error) {
                        ModLog.info("Error executing scheduled server network task: " + error.getMessage());
                    }
                }
            }
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                Runnable r;
                while ((r = CLIENT_QUEUE.poll()) != null) {
                    try {
                        r.run();
                    } catch (Throwable error) {
                        ModLog.info("Error executing scheduled client network task: " + error.getMessage());
                    }
                }
            }
        }
    }

    // ==========================================
    // Handlers
    // ==========================================

    public static final class CaptureRequestHandler implements IMessageHandler<PacketCaptureRequest, IMessage> {
        @Override
        public IMessage onMessage(final PacketCaptureRequest msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            runOnServer(new Runnable() {
                @Override
                public void run() {
                    handleCaptureRequest(player, msg);
                }
            });
            return null;
        }
    }

    private static void handleCaptureRequest(EntityPlayerMP player, PacketCaptureRequest msg) {
        BlueprintCaptureHelper.CaptureResult result = BlueprintCaptureHelper.capture(
                player, msg.x1, msg.y1, msg.z1, msg.x2, msg.y2, msg.z2, msg.name
        );
        if (!result.success || result.data == null) {
            sendTo(new PacketCaptureAck(false, "", "", result.message, 0), player);
            ModLog.info("Blueprint capture rejected for " + player.getCommandSenderName() + ": " + result.message);
            return;
        }

        // 保存到玩家个人蓝图库
        boolean saved = BlueprintStorageManager.saveBlueprint(result.data);
        if (saved) {
            sendTo(new PacketCaptureAck(true, result.data.getId(), result.data.getName(), "ok", result.data.getTotalBlockCount()), player);
            ModLog.info("Blueprint captured and saved: id=" + result.data.getId() + "; name=" + result.data.getName()
                    + "; blocks=" + result.data.getTotalBlockCount()
                    + "; parts=" + result.data.getTotalPartCount() + "; player=" + player.getCommandSenderName());
        } else {
            sendTo(new PacketCaptureAck(false, result.data.getId(), result.data.getName(), "save_failed", 0), player);
        }
    }

    public static final class CaptureAckHandler implements IMessageHandler<PacketCaptureAck, IMessage> {
        @Override
        public IMessage onMessage(final PacketCaptureAck msg, final MessageContext ctx) {
            runOnClient(new Runnable() {
                @Override
                public void run() {
                    ModLog.info("Received CaptureAck: success=" + msg.success + "; name=" + msg.blueprintName + "; msg=" + msg.message);
                }
            });
            return null;
        }
    }

    public static final class ListRequestHandler implements IMessageHandler<PacketBlueprintListRequest, IMessage> {
        @Override
        public IMessage onMessage(final PacketBlueprintListRequest msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            runOnServer(new Runnable() {
                @Override
                public void run() {
                    handleListRequest(player, msg);
                }
            });
            return null;
        }
    }

    private static void handleListRequest(EntityPlayerMP player, PacketBlueprintListRequest msg) {
        if (player == null) return;
        UUID uuid = player.getUniqueID();
        List<BlueprintData> bps = BlueprintStorageManager.getPlayerBlueprints(uuid);
        List<PacketBlueprintListResponse.BlueprintSummary> bpSummaries = new ArrayList<PacketBlueprintListResponse.BlueprintSummary>();
        for (BlueprintData bp : bps) {
            if (bp != null) {
                bpSummaries.add(new PacketBlueprintListResponse.BlueprintSummary(
                        bp.getId(), bp.getName(), bp.getSizeX(), bp.getSizeY(), bp.getSizeZ(),
                        bp.getTotalBlockCount(), bp.getCreatedAt()
                ));
            }
        }

        List<BlueprintTaskManager.BuildTask> tasks = BlueprintTaskManager.getTasksVisibleTo(player);
        List<PacketBlueprintListResponse.TaskSummary> taskSummaries = new ArrayList<PacketBlueprintListResponse.TaskSummary>();
        for (BlueprintTaskManager.BuildTask t : tasks) {
            if (t != null) {
                taskSummaries.add(new PacketBlueprintListResponse.TaskSummary(
                        t.getTaskId(), t.getBlueprintId(), t.getBlueprintName(), t.getOwnerName(),
                        t.getDimension(), t.getAnchorX(), t.getAnchorY(), t.getAnchorZ(),
                        t.getPermission(), t.isShowOutside(), t.getStatus(), t.isOwner(player)
                ));
            }
        }

        sendTo(new PacketBlueprintListResponse(msg.requestEpoch, bpSummaries, taskSummaries), player);
    }

    public static final class ListResponseHandler implements IMessageHandler<PacketBlueprintListResponse, IMessage> {
        @Override
        public IMessage onMessage(final PacketBlueprintListResponse msg, final MessageContext ctx) {
            runOnClient(new Runnable() {
                @Override
                public void run() {
                    // 更新客户端缓存的个人蓝图列表
                    BlueprintClientCache.setPersonalBlueprints(msg.blueprints);
                    // 更新客户端缓存的可见任务列表
                    for (PacketBlueprintListResponse.TaskSummary t : msg.tasks) {
                        BlueprintClientCache.updateTask(PacketTaskSync.upsert(
                                t.taskId, t.blueprintId, t.blueprintName, t.ownerName,
                                t.dimension, t.anchorX, t.anchorY, t.anchorZ,
                                t.permission, t.showOutside, t.status, t.isOwner
                        ));
                    }
                    ModLog.info("Received BlueprintListResponse: blueprints=" + msg.blueprints.size() + "; tasks=" + msg.tasks.size());
                }
            });
            return null;
        }
    }

    public static final class SliceRequestHandler implements IMessageHandler<PacketBlueprintSliceRequest, IMessage> {
        @Override
        public IMessage onMessage(final PacketBlueprintSliceRequest msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            runOnServer(new Runnable() {
                @Override
                public void run() {
                    handleSliceRequest(player, msg);
                }
            });
            return null;
        }
    }

    private static void handleSliceRequest(EntityPlayerMP player, PacketBlueprintSliceRequest msg) {
        if (player == null || msg.blueprintId == null || msg.blueprintId.isEmpty()) return;
        // 查找蓝图：可以是玩家个人蓝图，或者是某个对该玩家可见任务的快照蓝图
        BlueprintData bp = BlueprintStorageManager.loadBlueprint(player.getUniqueID(), msg.blueprintId);
        if (bp == null) {
            for (BlueprintTaskManager.BuildTask t : BlueprintTaskManager.getTasksVisibleTo(player)) {
                if (msg.blueprintId.equals(t.getBlueprintId()) && t.getBlueprintSnapshot() != null) {
                    bp = t.getBlueprintSnapshot();
                    break;
                }
            }
        }
        if (bp == null) {
            ModLog.info("Blueprint slice request denied: not found or not authorized for " + player.getCommandSenderName());
            return;
        }

        try {
            NBTTagCompound tag = BlueprintStorageManager.toNBT(bp);
            byte[] fullBytes = CompressedStreamTools.compress(tag);
            int totalBytes = fullBytes.length;
            int maxSlice = PacketBlueprintSlice.MAX_SLICE_SIZE;
            int totalSlices = (totalBytes + maxSlice - 1) / maxSlice;
            if (totalSlices == 0) totalSlices = 1;

            String transferId = UUID.randomUUID().toString();
            for (int i = 0; i < totalSlices; i++) {
                int start = i * maxSlice;
                int len = Math.min(maxSlice, totalBytes - start);
                byte[] sliceData = new byte[len];
                System.arraycopy(fullBytes, start, sliceData, 0, len);
                sendTo(new PacketBlueprintSlice(transferId, i, totalSlices, sliceData), player);
            }
            ModLog.info("Sent blueprint slices to " + player.getCommandSenderName() + ": slices=" + totalSlices + "; totalBytes=" + totalBytes);
        } catch (Throwable error) {
            ModLog.info("Failed to compress and slice blueprint: " + error.getMessage());
        }
    }

    public static final class SliceHandler implements IMessageHandler<PacketBlueprintSlice, IMessage> {
        @Override
        public IMessage onMessage(final PacketBlueprintSlice msg, final MessageContext ctx) {
            runOnClient(new Runnable() {
                @Override
                public void run() {
                    BlueprintData bp = BlueprintSliceAssembler.handleSlice(msg);
                    if (bp != null) {
                        BlueprintClientCache.putBlueprint(bp.getId(), bp);
                    }
                }
            });
            return null;
        }
    }

    public static final class TaskActionHandler implements IMessageHandler<PacketTaskAction, IMessage> {
        @Override
        public IMessage onMessage(final PacketTaskAction msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            runOnServer(new Runnable() {
                @Override
                public void run() {
                    handleTaskAction(player, msg);
                }
            });
            return null;
        }
    }

    private static void handleTaskAction(EntityPlayerMP player, PacketTaskAction msg) {
        if (player == null || !FreecamInteraction.active(player)) return;

        switch (msg.action) {
            case PacketTaskAction.ACTION_CREATE: {
                if (msg.blueprintId == null || msg.blueprintId.isEmpty()) return;
                BlueprintData bp = BlueprintStorageManager.loadBlueprint(player.getUniqueID(), msg.blueprintId);
                if (bp == null) {
                    ModLog.info("Task creation rejected: blueprint not found " + msg.blueprintId);
                    return;
                }
                BlueprintTaskManager.BuildTask task = BlueprintTaskManager.createTask(player, bp, msg.anchorX, msg.anchorY, msg.anchorZ);
                if (task != null) {
                    broadcastTaskSync(task, PacketTaskSync.TYPE_UPSERT, 64.0D);
                    ModLog.info("BuildTask created: id=" + task.getTaskId() + "; bp=" + bp.getName() + "; owner=" + player.getCommandSenderName());
                }
                break;
            }
            case PacketTaskAction.ACTION_CANCEL: {
                BlueprintTaskManager.BuildTask task = BlueprintTaskManager.getTask(msg.taskId);
                if (task == null) return;
                // 强校验主人身份
                if (!task.isOwner(player)) {
                    ModLog.info("Task cancel unauthorized: player=" + player.getCommandSenderName() + "; owner=" + task.getOwnerName());
                    return;
                }
                BlueprintTaskManager.removeTask(msg.taskId);
                task.setStatus(BlueprintTaskManager.STATUS_CANCELLED);
                broadcastTaskRemove(task, 64.0D);
                ModLog.info("BuildTask cancelled by owner: id=" + task.getTaskId());
                break;
            }
            case PacketTaskAction.ACTION_CHANGE_PERMISSION: {
                BlueprintTaskManager.BuildTask task = BlueprintTaskManager.getTask(msg.taskId);
                if (task == null) return;
                // 强校验主人身份
                if (!task.isOwner(player)) {
                    ModLog.info("Task permission change unauthorized: player=" + player.getCommandSenderName() + "; owner=" + task.getOwnerName());
                    return;
                }
                byte oldPerm = task.getPermission();
                byte newPerm = msg.permission;
                if (newPerm < 0 || newPerm > 2) return;
                task.setPermission(newPerm);

                // 若权限降为 HIDDEN，非主人玩家必须清理虚影缓存
                if (newPerm == BlueprintTaskManager.PERM_HIDDEN) {
                    broadcastTaskHidden(task, 64.0D);
                } else {
                    broadcastTaskSync(task, PacketTaskSync.TYPE_UPSERT, 64.0D);
                }
                ModLog.info("BuildTask permission changed: id=" + task.getTaskId() + "; old=" + oldPerm + "; new=" + newPerm);
                break;
            }
            case PacketTaskAction.ACTION_TOGGLE_SHOW_OUTSIDE: {
                BlueprintTaskManager.BuildTask task = BlueprintTaskManager.getTask(msg.taskId);
                if (task == null) return;
                // 强校验主人身份
                if (!task.isOwner(player)) {
                    ModLog.info("Task toggle show outside unauthorized: player=" + player.getCommandSenderName());
                    return;
                }
                task.setShowOutside(msg.showOutside);
                broadcastTaskSync(task, PacketTaskSync.TYPE_UPSERT, 64.0D);
                ModLog.info("BuildTask toggle show outside: id=" + task.getTaskId() + "; show=" + msg.showOutside);
                break;
            }
            case PacketTaskAction.ACTION_DELETE_BLUEPRINT: {
                if (msg.blueprintId == null || msg.blueprintId.isEmpty()) return;
                boolean deleted = BlueprintStorageManager.deleteBlueprint(player.getUniqueID(), msg.blueprintId);
                ModLog.info("Blueprint delete request by " + player.getCommandSenderName() + " for bp=" + msg.blueprintId + "; result=" + deleted);
                if (deleted) {
                    handleListRequest(player, new PacketBlueprintListRequest(0));
                }
                break;
            }
            case PacketTaskAction.ACTION_START_BUILD: {
                // 交给拓扑分步调度器（Phase0 基体 -> Phase1 附着 -> Phase2 部件 -> Phase3 配置）
                local.freecaminteraction.blueprint.build.BlueprintBuildScheduler.INSTANCE.startBuild(player, msg.taskId);
                break;
            }
            default:
                break;
        }
    }

    private static void broadcastTaskSync(BlueprintTaskManager.BuildTask task, byte type, double radius) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) obj;
                if (p.dimension == task.getDimension()) {
                    double distSq = p.getDistanceSq(task.getAnchorX(), task.getAnchorY(), task.getAnchorZ());
                    if (distSq <= radius * radius) {
                        boolean isOwner = task.isOwner(p);
                        if (isOwner || task.getPermission() != BlueprintTaskManager.PERM_HIDDEN) {
                            sendTo(PacketTaskSync.upsert(
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

    private static void broadcastTaskHidden(BlueprintTaskManager.BuildTask task, double radius) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) obj;
                if (p.dimension == task.getDimension()) {
                    double distSq = p.getDistanceSq(task.getAnchorX(), task.getAnchorY(), task.getAnchorZ());
                    if (distSq <= radius * radius) {
                        if (task.isOwner(p)) {
                            // 主人仍然可以看得到该任务更新
                            sendTo(PacketTaskSync.upsert(
                                    task.getTaskId(), task.getBlueprintId(), task.getBlueprintName(), task.getOwnerName(),
                                    task.getDimension(), task.getAnchorX(), task.getAnchorY(), task.getAnchorZ(),
                                    task.getPermission(), task.isShowOutside(), task.getStatus(), true
                            ), p);
                        } else {
                            // 其他玩家收到 REMOVE 命令，清除虚影缓存
                            sendTo(PacketTaskSync.remove(task.getTaskId()), p);
                        }
                    }
                }
            }
        }
    }

    /**
     * 广播任务移除（取消与完工共用）：客户端仅保留仍在进行的任务。
     */
    public static void broadcastTaskRemove(BlueprintTaskManager.BuildTask task, double radius) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) obj;
                if (p.dimension == task.getDimension()) {
                    double distSq = p.getDistanceSq(task.getAnchorX(), task.getAnchorY(), task.getAnchorZ());
                    if (distSq <= radius * radius) {
                        sendTo(PacketTaskSync.remove(task.getTaskId()), p);
                    }
                }
            }
        }
    }

    public static final class TaskSyncHandler implements IMessageHandler<PacketTaskSync, IMessage> {
        @Override
        public IMessage onMessage(final PacketTaskSync msg, final MessageContext ctx) {
            runOnClient(new Runnable() {
                @Override
                public void run() {
                    BlueprintClientCache.updateTask(msg);
                    ModLog.info("Received TaskSync: taskId=" + msg.taskId + "; type=" + msg.syncType + "; perm=" + msg.permission);
                }
            });
            return null;
        }
    }
}
