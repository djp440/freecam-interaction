package local.freecaminteraction.ae2.rv3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.definitions.IItemDefinition;
import appeng.api.networking.security.ISecurityGrid;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.common.registry.GameRegistry;
import io.netty.buffer.ByteBuf;
import local.freecaminteraction.FreecamGuiHandler;
import local.freecaminteraction.FreecamInteractionMod;
import local.freecaminteraction.FreecamWandRegistry;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.blueprint.network.BlueprintNetwork;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/** AE2 rv3-beta-6 的固定版本适配层。 */
public final class Ae2Runtime {
    private static final String BINDING = "Ae2Transfer";
    private static final String SELECTED = "FreecamAe2Selected";
    private static SimpleNetworkWrapper channel;

    private Ae2Runtime() {}

    public static void initialize(Object mod) {
        GameRegistry.registerItem(FreecamWandRegistry.ae2TransferCore, "ae2_transfer_core");
        GameRegistry.registerBlock(FreecamWandRegistry.ae2Transmitter, "ae2_transmitter");
        GameRegistry.registerTileEntity(TileAe2Transmitter.class, "freecam_interaction:ae2_transmitter");
        registerRecipes();
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("freecam_ae2");
        channel.registerMessage(OpenHandler.class, OpenMessage.class, 0, Side.SERVER);
        channel.registerMessage(SelectHandler.class, SelectMessage.class, 1, Side.SERVER);
        RemoteTerminals.initialize();
    }

    private static void registerRecipes() {
        ItemStack wireless = stack(AEApi.instance().definitions().materials().wireless());
        ItemStack processor = stack(AEApi.instance().definitions().materials().engProcessor());
        ItemStack access = stack(AEApi.instance().definitions().blocks().wireless());
        ItemStack crafting = stack(AEApi.instance().definitions().parts().craftingTerminal());
        ItemStack pattern = stack(AEApi.instance().definitions().parts().patternTerminal());
        ItemStack iface = stack(AEApi.instance().definitions().parts().interfaceTerminal());
        if (wireless != null && processor != null) GameRegistry.addShapelessRecipe(
                new ItemStack(FreecamWandRegistry.ae2TransferCore), wireless, processor, Items.ender_pearl, Items.diamond);
        if (access != null && crafting != null && pattern != null && iface != null) GameRegistry.addShapelessRecipe(
                new ItemStack(FreecamWandRegistry.ae2Transmitter), access, crafting, pattern, iface);
    }

    private static ItemStack stack(IItemDefinition definition) {
        return definition != null && definition.maybeStack(1).isPresent() ? definition.maybeStack(1).get() : null;
    }

    public static TileEntity newTransmitterTile() { return new TileAe2Transmitter(); }

    public static void bind(EntityPlayerMP player, ItemStack wand, World world, int x, int y, int z) {
        if (!local.freecaminteraction.ae2.Ae2Integration.hasCore(wand)) {
            player.addChatMessage(new ChatComponentTranslation("message.freecam_interaction.ae2.need_core"));
            return;
        }
        TileEntity raw = world.getTileEntity(x, y, z);
        if (!(raw instanceof TileAe2Transmitter)) return;
        TileAe2Transmitter tile = (TileAe2Transmitter) raw;
        if (!has(tile, player, SecurityPermissions.BUILD)) {
            player.addChatMessage(new ChatComponentTranslation("message.freecam_interaction.ae2.no_build"));
            return;
        }
        NBTTagCompound root = wand.getTagCompound();
        if (root == null) { root = new NBTTagCompound(); wand.setTagCompound(root); }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Version", 1);
        tag.setInteger("Dim", player.dimension);
        tag.setInteger("X", x); tag.setInteger("Y", y); tag.setInteger("Z", z);
        tag.setString("Id", tile.getInstanceId());
        root.setTag(BINDING, tag);
        persisted(player).setString(SELECTED, tile.getInstanceId());
        player.inventory.markDirty();
        player.addChatMessage(new ChatComponentTranslation("message.freecam_interaction.ae2.bound"));
        ModLog.info("AE2 transmitter bound: player=" + player.getCommandSenderName() + "; id=" + tile.getInstanceId()
                + "; dim=" + player.dimension + "; pos=" + x + "," + y + "," + z);
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound entity = player.getEntityData();
        if (!entity.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) entity.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return entity.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static String selectedSource(EntityPlayerMP player) {
        String selected = persisted(player).getString(SELECTED);
        List<TileAe2Transmitter> candidates = candidates(player);
        for (TileAe2Transmitter tile : candidates) if (tile.getInstanceId().equals(selected)) return selected;
        if (candidates.size() == 1) {
            selected = candidates.get(0).getInstanceId();
            persisted(player).setString(SELECTED, selected);
            return selected;
        }
        return null;
    }

    private static List<TileAe2Transmitter> candidates(EntityPlayer player) {
        List<TileAe2Transmitter> out = new ArrayList<TileAe2Transmitter>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < Math.min(36, player.inventory.mainInventory.length); i++) {
            ItemStack wand = player.inventory.mainInventory[i];
            if (!local.freecaminteraction.ae2.Ae2Integration.hasCore(wand) || wand == null || !wand.hasTagCompound()
                    || !wand.getTagCompound().hasKey(BINDING, 10)) continue;
            NBTTagCompound tag = wand.getTagCompound().getCompoundTag(BINDING);
            if (tag.getInteger("Dim") != player.dimension) continue;
            int x = tag.getInteger("X"), y = tag.getInteger("Y"), z = tag.getInteger("Z");
            if (!player.worldObj.getChunkProvider().chunkExists(x >> 4, z >> 4)) continue;
            TileEntity raw = player.worldObj.getTileEntity(x, y, z);
            if (raw instanceof TileAe2Transmitter) {
                TileAe2Transmitter tile = (TileAe2Transmitter) raw;
                if (tile.getInstanceId().equals(tag.getString("Id")) && seen.add(tile.getInstanceId())) out.add(tile);
            }
        }
        return out;
    }

    static TileAe2Transmitter selectedTile(EntityPlayer player) {
        String id = player instanceof EntityPlayerMP ? selectedSource((EntityPlayerMP) player) : persisted(player).getString(SELECTED);
        if (id == null || id.isEmpty()) return null;
        for (TileAe2Transmitter tile : candidates(player)) if (id.equals(tile.getInstanceId())) return tile;
        return null;
    }

    static boolean has(TileAe2Transmitter tile, EntityPlayer player, SecurityPermissions permission) {
        try {
            return tile != null && tile.getProxy().isActive() && tile.getProxy().getSecurity().hasPermission(player, permission);
        } catch (Throwable ignored) { return false; }
    }

    public static void requestTerminal(int page) { if (channel != null) channel.sendToServer(new OpenMessage(page)); }

    public static void selectTarget(String id) { if (channel != null) channel.sendToServer(new SelectMessage(id)); }

    private static void open(EntityPlayerMP player, int page) {
        if (page == 6) {
            page = candidates(player).isEmpty() ? 4 : 1;
        }
        if (page == 5) {
            if (!candidates(player).isEmpty()) player.openGui(FreecamInteractionMod.instance,
                    FreecamGuiHandler.GUI_AE2_TARGET, player.worldObj, 0, 0, 0);
            return;
        }
        TileAe2Transmitter tile = selectedTile(player);
        if (tile == null && candidates(player).size() > 1) {
            player.openGui(FreecamInteractionMod.instance, FreecamGuiHandler.GUI_AE2_TARGET, player.worldObj, 0, 0, 0);
            return;
        }
        SecurityPermissions permission = page == 1 || page == 2 ? SecurityPermissions.CRAFT
                : page == 3 ? SecurityPermissions.BUILD : SecurityPermissions.EXTRACT;
        if (page != 4 && (tile == null || !has(tile, player, permission))) {
            player.addChatMessage(new ChatComponentTranslation("message.freecam_interaction.ae2.unavailable"));
            return;
        }
        int id = page == 0 ? FreecamGuiHandler.GUI_AE2_ITEM : page == 2 ? FreecamGuiHandler.GUI_AE2_PATTERN
                : page == 3 ? FreecamGuiHandler.GUI_AE2_INTERFACE : page == 4 ? FreecamGuiHandler.GUI_AE2_INVENTORY
                : FreecamGuiHandler.GUI_AE2_CRAFTING;
        player.openGui(FreecamInteractionMod.instance, id, player.worldObj, 0, 0, 0);
    }

    public static Object serverGui(int id, EntityPlayer player) { return RemoteTerminals.serverGui(id, player); }
    public static Object clientGui(int id, EntityPlayer player) { return RemoteTerminals.clientGui(id, player); }

    public static Object reserveMe(EntityPlayerMP player, List requirements, int[] deficits, String source) {
        return Ae2MaterialReservation.reserve(player, requirements, deficits, source);
    }
    public static void commitMe(Object reservation) { if (reservation instanceof Ae2MaterialReservation) ((Ae2MaterialReservation) reservation).commit(); }
    public static void rollbackMe(EntityPlayerMP player, Object reservation) { if (reservation instanceof Ae2MaterialReservation) ((Ae2MaterialReservation) reservation).rollback(player); }

    public static final class OpenMessage implements IMessage {
        private int page;
        public OpenMessage() {}
        OpenMessage(int page) { this.page = page; }
        @Override public void fromBytes(ByteBuf buf) { page = buf.readUnsignedByte(); }
        @Override public void toBytes(ByteBuf buf) { buf.writeByte(page); }
    }
    public static final class OpenHandler implements IMessageHandler<OpenMessage, IMessage> {
        @Override public IMessage onMessage(final OpenMessage msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            BlueprintNetwork.runOnServer(new Runnable() { @Override public void run() { open(player, msg.page); } });
            return null;
        }
    }

    public static final class SelectMessage implements IMessage {
        private String id;
        public SelectMessage() {}
        SelectMessage(String id) { this.id = id == null ? "" : id; }
        @Override public void fromBytes(ByteBuf buf) {
            int size = Math.min(64, buf.readUnsignedByte());
            byte[] data = new byte[size]; buf.readBytes(data);
            try { id = new String(data, "UTF-8"); } catch (Exception ignored) { id = ""; }
        }
        @Override public void toBytes(ByteBuf buf) {
            try {
                byte[] data = id.getBytes("UTF-8");
                buf.writeByte(Math.min(64, data.length)); buf.writeBytes(data, 0, Math.min(64, data.length));
            } catch (Exception ignored) { buf.writeByte(0); }
        }
    }
    public static final class SelectHandler implements IMessageHandler<SelectMessage, IMessage> {
        @Override public IMessage onMessage(final SelectMessage msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            BlueprintNetwork.runOnServer(new Runnable() { @Override public void run() {
                for (TileAe2Transmitter tile : candidates(player)) if (tile.getInstanceId().equals(msg.id)) {
                    persisted(player).setString(SELECTED, msg.id);
                    open(player, 1);
                    return;
                }
            }});
            return null;
        }
    }
}
