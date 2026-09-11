package local.freecaminteraction.blueprint.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * 服务端 -> 客户端：蓝图二进制分片传输数据包。
 * 单片限制 16KB，避免 Netty 报文超限（2MB）或内存溢出。
 */
public final class PacketBlueprintSlice implements IMessage {
    public static final int MAX_SLICE_SIZE = 16384; // 16KB

    public String transferId;
    public int sliceIndex;
    public int totalSlices;
    public byte[] payload;

    public PacketBlueprintSlice() {}

    public PacketBlueprintSlice(String transferId, int sliceIndex, int totalSlices, byte[] payload) {
        this.transferId = transferId != null ? transferId : "";
        this.sliceIndex = sliceIndex;
        this.totalSlices = totalSlices;
        this.payload = payload != null ? payload : new byte[0];
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.transferId = ByteBufUtils.readUTF8String(buf);
        this.sliceIndex = buf.readInt();
        this.totalSlices = buf.readInt();
        int length = buf.readInt();
        if (length < 0 || length > MAX_SLICE_SIZE) {
            throw new IllegalArgumentException("Blueprint slice payload exceeds limit: " + length);
        }
        this.payload = new byte[length];
        if (length > 0) {
            buf.readBytes(this.payload);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, transferId != null ? transferId : "");
        buf.writeInt(sliceIndex);
        buf.writeInt(totalSlices);
        if (payload != null) {
            if (payload.length > MAX_SLICE_SIZE) {
                throw new IllegalArgumentException("Payload size " + payload.length + " exceeds limit " + MAX_SLICE_SIZE);
            }
            buf.writeInt(payload.length);
            buf.writeBytes(payload);
        } else {
            buf.writeInt(0);
        }
    }
}
