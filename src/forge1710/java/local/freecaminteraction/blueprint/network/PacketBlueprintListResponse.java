package local.freecaminteraction.blueprint.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 服务端 -> 客户端：个人蓝图列表与可见任务列表响应。
 */
public final class PacketBlueprintListResponse implements IMessage {
    public static final class BlueprintSummary {
        public String id;
        public String name;
        public int sizeX, sizeY, sizeZ;
        public int blockCount;
        public long createdAt;

        public BlueprintSummary() {}

        public BlueprintSummary(String id, String name, int sizeX, int sizeY, int sizeZ, int blockCount, long createdAt) {
            this.id = id != null ? id : "";
            this.name = name != null ? name : "";
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.blockCount = blockCount;
            this.createdAt = createdAt;
        }

        public void write(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, id);
            ByteBufUtils.writeUTF8String(buf, name);
            buf.writeInt(sizeX);
            buf.writeInt(sizeY);
            buf.writeInt(sizeZ);
            buf.writeInt(blockCount);
            buf.writeLong(createdAt);
        }

        public static BlueprintSummary read(ByteBuf buf) {
            BlueprintSummary s = new BlueprintSummary();
            s.id = ByteBufUtils.readUTF8String(buf);
            s.name = ByteBufUtils.readUTF8String(buf);
            s.sizeX = buf.readInt();
            s.sizeY = buf.readInt();
            s.sizeZ = buf.readInt();
            s.blockCount = buf.readInt();
            s.createdAt = buf.readLong();
            return s;
        }
    }

    public static final class TaskSummary {
        public String taskId;
        public String blueprintId;
        public String blueprintName;
        public String ownerName;
        public int dimension;
        public int anchorX, anchorY, anchorZ;
        public byte permission; // 0, 1, 2
        public boolean showOutside;
        public byte status; // 0 pending, 1 building, 2 completed, 3 cancelled
        public boolean isOwner;

        public TaskSummary() {}

        public TaskSummary(String taskId, String blueprintId, String blueprintName, String ownerName,
                           int dimension, int anchorX, int anchorY, int anchorZ,
                           byte permission, boolean showOutside, byte status, boolean isOwner) {
            this.taskId = taskId != null ? taskId : "";
            this.blueprintId = blueprintId != null ? blueprintId : "";
            this.blueprintName = blueprintName != null ? blueprintName : "";
            this.ownerName = ownerName != null ? ownerName : "";
            this.dimension = dimension;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.permission = permission;
            this.showOutside = showOutside;
            this.status = status;
            this.isOwner = isOwner;
        }

        public void write(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, taskId);
            ByteBufUtils.writeUTF8String(buf, blueprintId);
            ByteBufUtils.writeUTF8String(buf, blueprintName);
            ByteBufUtils.writeUTF8String(buf, ownerName);
            buf.writeInt(dimension);
            buf.writeInt(anchorX);
            buf.writeInt(anchorY);
            buf.writeInt(anchorZ);
            buf.writeByte(permission);
            buf.writeBoolean(showOutside);
            buf.writeByte(status);
            buf.writeBoolean(isOwner);
        }

        public static TaskSummary read(ByteBuf buf) {
            TaskSummary t = new TaskSummary();
            t.taskId = ByteBufUtils.readUTF8String(buf);
            t.blueprintId = ByteBufUtils.readUTF8String(buf);
            t.blueprintName = ByteBufUtils.readUTF8String(buf);
            t.ownerName = ByteBufUtils.readUTF8String(buf);
            t.dimension = buf.readInt();
            t.anchorX = buf.readInt();
            t.anchorY = buf.readInt();
            t.anchorZ = buf.readInt();
            t.permission = buf.readByte();
            t.showOutside = buf.readBoolean();
            t.status = buf.readByte();
            t.isOwner = buf.readBoolean();
            return t;
        }
    }

    public int responseEpoch;
    public List<BlueprintSummary> blueprints;
    public List<TaskSummary> tasks;

    public PacketBlueprintListResponse() {
        this.blueprints = new ArrayList<BlueprintSummary>();
        this.tasks = new ArrayList<TaskSummary>();
    }

    public PacketBlueprintListResponse(int responseEpoch, List<BlueprintSummary> blueprints, List<TaskSummary> tasks) {
        this.responseEpoch = responseEpoch;
        this.blueprints = blueprints != null ? blueprints : Collections.<BlueprintSummary>emptyList();
        this.tasks = tasks != null ? tasks : Collections.<TaskSummary>emptyList();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.responseEpoch = buf.readInt();
        int bpSize = buf.readInt();
        this.blueprints = new ArrayList<BlueprintSummary>(Math.max(0, bpSize));
        for (int i = 0; i < bpSize; i++) {
            blueprints.add(BlueprintSummary.read(buf));
        }
        int taskSize = buf.readInt();
        this.tasks = new ArrayList<TaskSummary>(Math.max(0, taskSize));
        for (int i = 0; i < taskSize; i++) {
            tasks.add(TaskSummary.read(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(responseEpoch);
        buf.writeInt(blueprints != null ? blueprints.size() : 0);
        if (blueprints != null) {
            for (BlueprintSummary s : blueprints) {
                s.write(buf);
            }
        }
        buf.writeInt(tasks != null ? tasks.size() : 0);
        if (tasks != null) {
            for (TaskSummary t : tasks) {
                t.write(buf);
            }
        }
    }
}
