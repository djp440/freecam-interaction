package local.freecaminteraction.blueprint.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * 客户端 -> 服务端：请求指定蓝图的分片数据（用于虚影渲染）。
 */
public final class PacketBlueprintSliceRequest implements IMessage {
    public String blueprintId;

    public PacketBlueprintSliceRequest() {}

    public PacketBlueprintSliceRequest(String blueprintId) {
        this.blueprintId = blueprintId != null ? blueprintId : "";
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.blueprintId = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, blueprintId != null ? blueprintId : "");
    }
}
