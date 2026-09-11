package local.freecaminteraction.blueprint.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * 客户端 -> 服务端：查询个人蓝图列表与可见任务列表请求。
 */
public final class PacketBlueprintListRequest implements IMessage {
    public int requestEpoch;

    public PacketBlueprintListRequest() {}

    public PacketBlueprintListRequest(int requestEpoch) {
        this.requestEpoch = requestEpoch;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.requestEpoch = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(requestEpoch);
    }
}
