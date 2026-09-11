package local.freecaminteraction.blueprint.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * 客户端 -> 服务端：任务操作请求。
 * 支持动作：
 * 1 - CREATE: 创建任务（落点坐标、蓝图 ID）
 * 2 - CANCEL: 取消任务（taskId）- 服务端强校验主人身份
 * 3 - CHANGE_PERMISSION: 变更权限（taskId, permission 0/1/2）- 服务端强校验主人身份
 * 4 - TOGGLE_SHOW_OUTSIDE: 切换模式外显示（taskId, showOutside）- 服务端强校验主人身份
 */
public final class PacketTaskAction implements IMessage {
    public static final byte ACTION_CREATE = 1;
    public static final byte ACTION_CANCEL = 2;
    public static final byte ACTION_CHANGE_PERMISSION = 3;
    public static final byte ACTION_TOGGLE_SHOW_OUTSIDE = 4;
    public static final byte ACTION_DELETE_BLUEPRINT = 5;
    public static final byte ACTION_BUILD = 6;
    public static final byte ACTION_START_BUILD = 6;

    public byte action;
    public String taskId;
    public String blueprintId;
    public int anchorX, anchorY, anchorZ;
    public byte permission; // 0, 1, 2
    public boolean showOutside;

    public PacketTaskAction() {}

    public static PacketTaskAction create(String blueprintId, int anchorX, int anchorY, int anchorZ) {
        PacketTaskAction p = new PacketTaskAction();
        p.action = ACTION_CREATE;
        p.blueprintId = blueprintId;
        p.anchorX = anchorX;
        p.anchorY = anchorY;
        p.anchorZ = anchorZ;
        return p;
    }

    public static PacketTaskAction cancel(String taskId) {
        PacketTaskAction p = new PacketTaskAction();
        p.action = ACTION_CANCEL;
        p.taskId = taskId;
        return p;
    }

    public static PacketTaskAction changePermission(String taskId, byte permission) {
        PacketTaskAction p = new PacketTaskAction();
        p.action = ACTION_CHANGE_PERMISSION;
        p.taskId = taskId;
        p.permission = permission;
        return p;
    }

    public static PacketTaskAction toggleShowOutside(String taskId, boolean showOutside) {
        PacketTaskAction p = new PacketTaskAction();
        p.action = ACTION_TOGGLE_SHOW_OUTSIDE;
        p.taskId = taskId;
        p.showOutside = showOutside;
        return p;
    }

    public static PacketTaskAction deleteBlueprint(String blueprintId) {
        PacketTaskAction p = new PacketTaskAction();
        p.action = ACTION_DELETE_BLUEPRINT;
        p.blueprintId = blueprintId;
        return p;
    }

    public static PacketTaskAction build(String taskId) {
        PacketTaskAction p = new PacketTaskAction();
        p.action = ACTION_BUILD;
        p.taskId = taskId;
        return p;
    }

    public static PacketTaskAction startBuild(String taskId) {
        return build(taskId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readByte();
        this.taskId = ByteBufUtils.readUTF8String(buf);
        this.blueprintId = ByteBufUtils.readUTF8String(buf);
        this.anchorX = buf.readInt();
        this.anchorY = buf.readInt();
        this.anchorZ = buf.readInt();
        this.permission = buf.readByte();
        this.showOutside = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        ByteBufUtils.writeUTF8String(buf, taskId != null ? taskId : "");
        ByteBufUtils.writeUTF8String(buf, blueprintId != null ? blueprintId : "");
        buf.writeInt(anchorX);
        buf.writeInt(anchorY);
        buf.writeInt(anchorZ);
        buf.writeByte(permission);
        buf.writeBoolean(showOutside);
    }
}
