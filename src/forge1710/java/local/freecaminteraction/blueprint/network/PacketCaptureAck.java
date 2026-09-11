package local.freecaminteraction.blueprint.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * 服务端 -> 客户端：选区蓝图采集应答。
 */
public final class PacketCaptureAck implements IMessage {
    public boolean success;
    public String blueprintId;
    public String blueprintName;
    public String message;
    public int totalBlocks;

    public PacketCaptureAck() {}

    public PacketCaptureAck(boolean success, String blueprintId, String blueprintName, String message, int totalBlocks) {
        this.success = success;
        this.blueprintId = blueprintId != null ? blueprintId : "";
        this.blueprintName = blueprintName != null ? blueprintName : "";
        this.message = message != null ? message : "";
        this.totalBlocks = totalBlocks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.success = buf.readBoolean();
        this.blueprintId = ByteBufUtils.readUTF8String(buf);
        this.blueprintName = ByteBufUtils.readUTF8String(buf);
        this.message = ByteBufUtils.readUTF8String(buf);
        this.totalBlocks = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(success);
        ByteBufUtils.writeUTF8String(buf, blueprintId != null ? blueprintId : "");
        ByteBufUtils.writeUTF8String(buf, blueprintName != null ? blueprintName : "");
        ByteBufUtils.writeUTF8String(buf, message != null ? message : "");
        buf.writeInt(totalBlocks);
    }
}
