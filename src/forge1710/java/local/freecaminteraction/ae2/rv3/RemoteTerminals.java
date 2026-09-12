package local.freecaminteraction.ae2.rv3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import appeng.api.AEApi;
import appeng.api.networking.IGridNode;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerCraftingTerm;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.me.helpers.AENetworkProxy;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import local.freecaminteraction.FreecamGuiHandler;
import local.freecaminteraction.ModLog;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.Container;
import net.minecraftforge.common.util.ForgeDirection;

/** 为每个玩家保留一套虚拟原生 Part，让 rv3 容器/NEI/自动合成协议原样工作。 */
final class RemoteTerminals {
    private static final Map<UUID, Session> SERVER_SESSIONS = new HashMap<UUID, Session>();
    private static final Map<UUID, Session> CLIENT_SESSIONS = new HashMap<UUID, Session>();
    private static final ThreadLocal<TileAe2Transmitter> CONSTRUCTING = new ThreadLocal<TileAe2Transmitter>();
    private static final Lifecycle LIFECYCLE = new Lifecycle();

    private RemoteTerminals() {}

    static void initialize() {
        FMLCommonHandler.instance().bus().register(LIFECYCLE);
    }

    static Object serverGui(int id, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return null;
        if (id == FreecamGuiHandler.GUI_AE2_TARGET) {
            Container target = new net.minecraft.inventory.ContainerPlayer(player.inventory, false, player);
            Session existing = SERVER_SESSIONS.get(player.getUniqueID());
            if (existing != null) { existing.currentContainer = target; existing.closeAt = -1L; }
            return target;
        }
        if (id == FreecamGuiHandler.GUI_AE2_INVENTORY) {
            return new net.minecraft.inventory.ContainerPlayer(player.inventory, false, player);
        }
        TileAe2Transmitter tile = Ae2Runtime.selectedTile(player);
        if (tile == null || !tile.getProxy().isActive()) return null;
        Session session = session(player, tile);
        Object gui = id == FreecamGuiHandler.GUI_AE2_ITEM ? new ContainerMEMonitorable(player.inventory, session.crafting)
                : id == FreecamGuiHandler.GUI_AE2_CRAFTING ? new ContainerCraftingTerm(player.inventory, session.crafting)
                : id == FreecamGuiHandler.GUI_AE2_PATTERN ? new ContainerPatternTerm(player.inventory, session.pattern)
                : id == FreecamGuiHandler.GUI_AE2_INTERFACE ? new ContainerInterfaceTerminal(player.inventory, session.iface) : null;
        if (gui instanceof Container) session.currentContainer = (Container) gui;
        return gui;
    }

    static Object clientGui(int id, EntityPlayer player) {
        if (id == FreecamGuiHandler.GUI_AE2_TARGET) return new local.freecaminteraction.client.gui.GuiAe2TargetSelector(player);
        if (id == FreecamGuiHandler.GUI_AE2_INVENTORY) return new RemoteGuis.Inventory(player);
        Session session = clientSession(player);
        if (id == FreecamGuiHandler.GUI_AE2_ITEM) return new RemoteGuis.Items(player.inventory, session.crafting);
        if (id == FreecamGuiHandler.GUI_AE2_CRAFTING) return new RemoteGuis.Crafting(player.inventory, session.crafting);
        if (id == FreecamGuiHandler.GUI_AE2_PATTERN) return new RemoteGuis.Pattern(player.inventory, session.pattern);
        if (id == FreecamGuiHandler.GUI_AE2_INTERFACE) return new RemoteGuis.Interface(player.inventory, session.iface);
        return null;
    }

    /** 客户端不加载远端区块；容器同步只需要一个本地占位宿主，真实网络始终由服务端会话持有。 */
    private static Session clientSession(EntityPlayer player) {
        UUID key = player.getUniqueID();
        Session current = CLIENT_SESSIONS.get(key);
        if (current == null) {
            TileAe2Transmitter placeholder = new TileAe2Transmitter();
            placeholder.setWorldObj(player.worldObj);
            placeholder.xCoord = (int) Math.floor(player.posX);
            placeholder.yCoord = (int) Math.floor(player.posY);
            placeholder.zCoord = (int) Math.floor(player.posZ);
            current = new Session(player, placeholder);
            CLIENT_SESSIONS.put(key, current);
        }
        return current;
    }

    private static Session session(EntityPlayer player, TileAe2Transmitter tile) {
        UUID key = player.getUniqueID();
        Map<UUID, Session> sessions = player.worldObj.isRemote ? CLIENT_SESSIONS : SERVER_SESSIONS;
        Session current = sessions.get(key);
        if (current == null || current.tile != tile || !current.id.equals(tile.getInstanceId())) {
            if (current != null && player instanceof EntityPlayerMP) finish((EntityPlayerMP) player, current);
            current = new Session(player, tile);
            sessions.put(key, current);
        }
        current.closeAt = -1L;
        return current;
    }

    private static ItemStack part(appeng.api.definitions.IItemDefinition definition) {
        return definition.maybeStack(1).get();
    }

    private static final class Session {
        final UUID owner;
        final EntityPlayer ownerPlayer;
        final TileAe2Transmitter tile;
        final String id;
        final RemoteCrafting crafting;
        final RemotePattern pattern;
        final RemoteInterface iface;
        long closeAt = -1L;
        Container currentContainer;
        Session(EntityPlayer player, TileAe2Transmitter tile) {
            this.owner = player.getUniqueID();
            this.ownerPlayer = player;
            this.tile = tile;
            this.id = tile.getInstanceId();
            CONSTRUCTING.set(tile);
            try {
                crafting = new RemoteCrafting(tile, part(AEApi.instance().definitions().parts().craftingTerminal()));
                pattern = new RemotePattern(tile, part(AEApi.instance().definitions().parts().patternTerminal()));
                iface = new RemoteInterface(tile, part(AEApi.instance().definitions().parts().interfaceTerminal()));
            } finally {
                CONSTRUCTING.remove();
            }
        }
    }

    private static TileAe2Transmitter host(TileAe2Transmitter tile) {
        return tile == null ? CONSTRUCTING.get() : tile;
    }

    private static void finish(EntityPlayerMP player, Session session) {
        if (session == null) return;
        SERVER_SESSIONS.remove(session.owner);
        List<ItemStack> returned = new ArrayList<ItemStack>();
        drain(session.crafting.getInventoryByName("crafting"), returned);
        drain(session.crafting.getViewCellStorage(), returned);
        drain(session.pattern.getInventoryByName("pattern"), returned);
        drain(session.pattern.getViewCellStorage(), returned);
        for (ItemStack stack : returned) giveOrDrop(player, stack);
        if (!returned.isEmpty()) {
            player.inventoryContainer.detectAndSendChanges();
            ModLog.info("AE2 remote terminal session returned " + returned.size() + " actual stacks; player="
                    + player.getCommandSenderName() + "; transmitter=" + session.id);
        }
    }

    private static void drain(IInventory inventory, List<ItemStack> out) {
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack != null) {
                out.add(stack.copy());
                inventory.setInventorySlotContents(slot, null);
            }
        }
    }

    private static void giveOrDrop(EntityPlayerMP player, ItemStack stack) {
        if (player.inventory.addItemStackToInventory(stack)) return;
        EntityItem dropped = player.dropPlayerItemWithRandomChoice(stack, false);
        if (dropped != null) dropped.delayBeforeCanPickup = 0;
    }

    public static final class Lifecycle {
        @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.player); }
        @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.player); }
        @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { clear(event.player); }
        private void clear(EntityPlayer player) {
            if (player instanceof EntityPlayerMP) finish((EntityPlayerMP) player, SERVER_SESSIONS.get(player.getUniqueID()));
        }
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            for (Session session : new ArrayList<Session>(SERVER_SESSIONS.values())) {
                if (!(session.ownerPlayer instanceof EntityPlayerMP)) continue;
                EntityPlayerMP player = (EntityPlayerMP) session.ownerPlayer;
                boolean invalid = session.tile.isInvalid() || !session.tile.getProxy().isActive()
                        || player.dimension != session.tile.getWorldObj().provider.dimensionId;
                if (invalid) {
                    player.closeScreen();
                    finish(player, session);
                } else if (player.openContainer == session.currentContainer) {
                    session.closeAt = -1L;
                } else {
                    if (session.closeAt < 0L) session.closeAt = player.worldObj.getTotalWorldTime() + 1L;
                    else if (player.worldObj.getTotalWorldTime() >= session.closeAt) finish(player, session);
                }
            }
        }
    }

    private static IMEMonitor<IAEItemStack> items(TileAe2Transmitter tile) {
        try { return tile.getProxy().getStorage().getItemInventory(); } catch (Throwable ignored) { return null; }
    }
    private static IMEMonitor<IAEFluidStack> fluids(TileAe2Transmitter tile) {
        try { return tile.getProxy().getStorage().getFluidInventory(); } catch (Throwable ignored) { return null; }
    }

    private static final class RemoteCrafting extends PartCraftingTerminal {
        private final TileAe2Transmitter tile;
        RemoteCrafting(TileAe2Transmitter tile, ItemStack stack) { super(stack); this.tile = tile; }
        @Override public AENetworkProxy getProxy() { return host(tile).getProxy(); }
        @Override public IGridNode getGridNode(ForgeDirection side) { return host(tile).getGridNode(side); }
        @Override public IGridNode getGridNode() { return host(tile).getGridNode(ForgeDirection.UNKNOWN); }
        @Override public IGridNode getActionableNode() { return host(tile).getActionableNode(); }
        @Override public appeng.api.util.DimensionalCoord getLocation() { return host(tile).getLocation(); }
        @Override public IMEMonitor<IAEItemStack> getItemInventory() { return items(host(tile)); }
        @Override public IMEMonitor<IAEFluidStack> getFluidInventory() { return fluids(host(tile)); }
        @Override public void saveChanges() { }
    }

    private static final class RemotePattern extends PartPatternTerminal {
        private final TileAe2Transmitter tile;
        RemotePattern(TileAe2Transmitter tile, ItemStack stack) { super(stack); this.tile = tile; }
        @Override public AENetworkProxy getProxy() { return host(tile).getProxy(); }
        @Override public IGridNode getGridNode(ForgeDirection side) { return host(tile).getGridNode(side); }
        @Override public IGridNode getGridNode() { return host(tile).getGridNode(ForgeDirection.UNKNOWN); }
        @Override public IGridNode getActionableNode() { return host(tile).getActionableNode(); }
        @Override public appeng.api.util.DimensionalCoord getLocation() { return host(tile).getLocation(); }
        @Override public IMEMonitor<IAEItemStack> getItemInventory() { return items(host(tile)); }
        @Override public IMEMonitor<IAEFluidStack> getFluidInventory() { return fluids(host(tile)); }
        @Override public void saveChanges() { }
    }

    private static final class RemoteInterface extends PartInterfaceTerminal {
        private final TileAe2Transmitter tile;
        RemoteInterface(TileAe2Transmitter tile, ItemStack stack) { super(stack); this.tile = tile; }
        @Override public AENetworkProxy getProxy() { return host(tile).getProxy(); }
        @Override public IGridNode getGridNode(ForgeDirection side) { return host(tile).getGridNode(side); }
        @Override public IGridNode getGridNode() { return host(tile).getGridNode(ForgeDirection.UNKNOWN); }
        @Override public IGridNode getActionableNode() { return host(tile).getActionableNode(); }
        @Override public appeng.api.util.DimensionalCoord getLocation() { return host(tile).getLocation(); }
        @Override public void saveChanges() { }
    }
}
