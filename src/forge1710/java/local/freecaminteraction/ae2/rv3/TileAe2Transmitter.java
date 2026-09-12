package local.freecaminteraction.ae2.rv3;

import java.util.EnumSet;
import java.util.UUID;
import appeng.api.networking.GridFlags;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import local.freecaminteraction.FreecamWandRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

/** 一台设备一个频道，不保载且只接入所在 ME 网络。 */
public final class TileAe2Transmitter extends AENetworkTile {
    private String instanceId = UUID.randomUUID().toString();
    private boolean lastActive;

    @Override protected AENetworkProxy createProxy() {
        AENetworkProxy proxy = new AENetworkProxy(this, "proxy",
                new ItemStack(FreecamWandRegistry.ae2Transmitter), true);
        proxy.setValidSides(EnumSet.allOf(ForgeDirection.class));
        proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        proxy.setIdlePowerUsage(8.0D);
        return proxy;
    }

    public String getInstanceId() { return instanceId; }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readTransferNbt(NBTTagCompound tag) {
        String stored = tag.getString("TransferInstanceId");
        if (stored != null && !stored.isEmpty()) instanceId = stored;
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeTransferNbt(NBTTagCompound tag) { tag.setString("TransferInstanceId", instanceId); }

    @Override public void gridChanged() {
        super.gridChanged();
        updateVisualState();
    }

    private void updateVisualState() {
        if (worldObj == null || worldObj.isRemote) return;
        boolean active = getProxy().isActive();
        if (active != lastActive || worldObj.getBlockMetadata(xCoord, yCoord, zCoord) != (active ? 1 : 0)) {
            lastActive = active;
            worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, active ? 1 : 0, 3);
        }
    }
}
