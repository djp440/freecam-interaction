package local.freecaminteraction.blueprint.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * 服务端 -> 客户端广播：任务状态与元数据同步数据包。
 * 当任务创建、权限改变、取消或完成时，向同维度可见范围内的玩家同步任务状态。
 * 若权限变为 HIDDEN，通知其他客户端立即清除该任务虚影缓存。
 */
public final class PacketTaskSync implements IMessage {
    public static final byte TYPE_UPSERT = 1;
    public static final byte TYPE_REMOVE = 2;

    public byte syncType; // 1 upsert, 2 remove
    public String taskId;
    public String blueprintId;
    public String blueprintName;
    public String ownerName;
    public int dimension;
    public int anchorX, anchorY, anchorZ;
    public byte permission; // 0 HIDDEN, 1 VISIBLE_NO_BUILD, 2 CAN_BUILD
    public boolean showOutside;
    public byte status; // 0 pending, 1 building, 2 completed, 3 cancelled
    public boolean isOwner;

    public PacketTaskSync() {}

    public static PacketTaskSync remove(String taskId) {
        PacketTaskSync p = new PacketTaskSync();
        p.syncType = TYPE_REMOVE;
        p.taskId = taskId;
        return p;
    }

    public static PacketTaskSync upsert(String taskId, String blueprintId, String blueprintName,
                                        String ownerName, int dimension, int anchorX, int anchorY, int anchorZ,
                                        byte permission, boolean showOutside, byte status, boolean isOwner) {
        PacketTaskSync p = new PacketTaskSync();
        p.syncType = TYPE_UPSERT;
        p.taskId = taskId;
        p.blueprintId = blueprintId;
        p.blueprintName = blueprintName;
        p.ownerName = ownerName;
        p.dimension = dimension;
        p.anchorX = anchorX;
        p.anchorY = anchorY;
        p.anchorZ = anchorZ;
        p.permission = permission;
        p.showOutside = showOutside;
        p.status = status;
        p.isOwner = isOwner;
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.syncType = buf.readByte();
        this.taskId = ByteBufUtils.readUTF8String(buf);
        if (this.syncType == TYPE_UPSERT) {
            this.blueprintId = ByteBufUtils.readUTF8String(buf);
            this.blueprintName = ByteBufUtils.readUTF8String(buf);
            this.ownerName = ByteBufUtils.readUTF8String(buf);
            this.dimension = buf.readInt();
            this.anchorX = buf.readInt();
            this.anchorY = buf.readInt();
            this.anchorZ = buf.readInt();
            this.permission = buf.readByte();
            this.showOutside = buf.readBoolean();
            this.status = buf.readByte();
            this.isOwner = buf.readBoolean();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(syncType);
        ByteBufUtils.writeUTF8String(buf, taskId != null ? taskId : "");
        if (syncType == TYPE_UPSERT) {
            ByteBufUtils.writeUTF8String(buf, blueprintId != null ? blueprintId : "");
            ByteBufUtils.writeUTF8String(buf, blueprintName != null ? blueprintName : "");
            ByteBufUtils.writeUTF8String(buf, ownerName != null ? ownerName : "");
            buf.writeInt(dimension);
            buf.writeInt(anchorX);
            buf.writeInt(anchorY);
            buf.writeInt(anchorZ);
            buf.writeByte(permission);
            buf.writeBoolean(showOutside);
            buf.writeByte(status);
            buf.writeBoolean(isOwner);
        }
    }
}
