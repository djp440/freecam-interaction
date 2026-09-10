package local.freecaminteraction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.world.BlockEvent;

/** 模式消息在服务端 tick 应用；挖掘、放置和权限仍走 Forge 原生链路。 */
public final class FreecamInteraction {
    private static SimpleNetworkWrapper channel;
    public static volatile boolean available;
    public static volatile boolean acknowledged;
    private static volatile int clientEpoch;
    private static final Map<EntityPlayerMP, ModeRequest> PENDING = new ConcurrentHashMap<EntityPlayerMP, ModeRequest>();
    private static final Map<EntityPlayerMP, State> ACTIVE = new HashMap<EntityPlayerMP, State>();

    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("freecam1710v2");
        channel.registerMessage(ServerMode.class, ModeRequest.class, 0, Side.SERVER);
        channel.registerMessage(ClientMode.class, ModeAck.class, 1, Side.CLIENT);
        FreecamActions.initialize(channel);
        FreecamInteraction events = new FreecamInteraction();
        FMLCommonHandler.instance().bus().register(events);
        MinecraftForge.EVENT_BUS.register(events);
    }

    public static void request(boolean enabled) {
        acknowledged = false;
        if (available && channel != null) channel.sendToServer(new ModeRequest(++clientEpoch, enabled));
    }

    public static boolean active(EntityPlayer player) {
        State state = ACTIVE.get(player);
        return state != null && state.dimension == player.dimension && player.isEntityAlive() && !player.isPlayerSleeping();
    }

    public static boolean inside(EntityPlayer player, int x, int y, int z) {
        return y >= 0 && y < player.worldObj.getHeight() && player.worldObj.blockExists(x, y, z)
                && FreecamRange.contains(player.posX, player.boundingBox.minY, player.posZ, x, y, z);
    }

    public static double distance(EntityPlayer player, double x, double y, double z) {
        if (active(player) && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && inside(player, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) return 0.0D;
        double deltaX = player.posX - x;
        double deltaY = player.posY - y;
        double deltaZ = player.posZ - z;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    public static double distanceToEntity(Entity entityA, Entity entityB) {
        if (entityB instanceof EntityPlayer && active((EntityPlayer) entityB) && FreecamTarget.allowed((EntityPlayer) entityB, entityA)) {
            return 0.0D;
        }
        if (entityA instanceof EntityPlayer && active((EntityPlayer) entityA) && FreecamTarget.allowed((EntityPlayer) entityA, entityB)) {
            return 0.0D;
        }
        return entityA.getDistanceSqToEntity(entityB);
    }

    private static void cancelMining(EntityPlayerMP player) {
        player.theItemInWorldManager.cancelDestroyingBlock(0, 0, 0);
    }

    private static void enable(EntityPlayerMP player) {
        State state = ACTIVE.get(player);
        if (state == null) {
            state = new State(player.dimension, player.theItemInWorldManager.getBlockReachDistance());
            ACTIVE.put(player, state);
        } else state.dimension = player.dimension;
        cancelMining(player);
        player.theItemInWorldManager.setBlockReachDistance(32.0D);
    }

    private static void clear(EntityPlayerMP player) {
        PENDING.remove(player);
        FreecamActions.clear(player);
        State state = ACTIVE.remove(player);
        cancelMining(player);
        if (state != null) player.theItemInWorldManager.setBlockReachDistance(state.previousReach);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.world.isRemote || !ACTIVE.containsKey(event.entityPlayer)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        if (active(player) && FreecamActions.authorized == player && FreecamActions.fishing
                && event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) return;
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR || event.face < 0 || event.face > 5 || !active(player)
                || !inside(player, event.x, event.y, event.z)) {
            event.setCanceled(true);
            cancelMining(player);
            ModLog.info("Rejected interaction: player=" + player.getCommandSenderName()
                    + "; target=" + event.x + "," + event.y + "," + event.z);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void breakBlock(BlockEvent.BreakEvent event) {
        if (event.world.isRemote || !ACTIVE.containsKey(event.getPlayer())) return;
        if (!active(event.getPlayer()) || !inside(event.getPlayer(), event.x, event.y, event.z)) {
            event.setCanceled(true);
            cancelMining((EntityPlayerMP) event.getPlayer());
        } else ModLog.info("Allowed break: player=" + event.getPlayer().getCommandSenderName()
                + "; target=" + event.x + "," + event.y + "," + event.z);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void place(BlockEvent.PlaceEvent event) {
        if (event.world.isRemote || !ACTIVE.containsKey(event.player)) return;
        if (!active(event.player) || !inside(event.player, event.x, event.y, event.z)) event.setCanceled(true);
        if (event instanceof BlockEvent.MultiPlaceEvent) {
            for (BlockSnapshot block : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) {
                if (!inside(event.player, block.x, block.y, block.z)) event.setCanceled(true);
            }
        }
        ModLog.info((event.isCanceled() ? "Rejected" : "Allowed") + " placement: player="
                + event.player.getCommandSenderName() + "; target=" + event.x + "," + event.y + "," + event.z);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void attackEntity(AttackEntityEvent event) {
        if (!event.entityPlayer.worldObj.isRemote && ACTIVE.containsKey(event.entityPlayer)
                && (FreecamActions.authorized != event.entityPlayer || !active(event.entityPlayer)
                    || !FreecamTarget.allowed(event.entityPlayer, event.target))) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void interactEntity(EntityInteractEvent event) {
        if (!event.entityPlayer.worldObj.isRemote && ACTIVE.containsKey(event.entityPlayer)
                && (FreecamActions.authorized != event.entityPlayer || !active(event.entityPlayer)
                    || !FreecamTarget.allowed(event.entityPlayer, event.target))) event.setCanceled(true);
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent event) {
        if (event.side != Side.SERVER || event.phase != TickEvent.Phase.START || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        ModeRequest mode = PENDING.remove(player);
        if (mode != null) {
            boolean enabled = mode.enabled && player.isEntityAlive() && !player.isPlayerSleeping();
            if (enabled) enable(player); else clear(player);
            channel.sendTo(new ModeAck(mode.epoch, enabled), player);
            ModLog.info("Server mode=" + enabled + "; player=" + player.getCommandSenderName());
        }
        if (ACTIVE.containsKey(player)) {
            if (!active(player)) clear(player);
            else player.theItemInWorldManager.setBlockReachDistance(32.0D);
        }
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.player instanceof EntityPlayerMP) clear((EntityPlayerMP) event.player); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { if (event.player instanceof EntityPlayerMP) clear((EntityPlayerMP) event.player); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.player instanceof EntityPlayerMP) clear((EntityPlayerMP) event.player); }

    @SubscribeEvent
    public void registration(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
        if (event.side == Side.CLIENT && event.registrations.contains("freecam1710v2")) {
            available = event.operation.equals("REGISTER");
            ModLog.info("Server interaction channel=" + available);
        }
    }

    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        available = acknowledged = false;
        clientEpoch++;
    }

    private static final class State {
        int dimension;
        final double previousReach;
        State(int dimension, double previousReach) { this.dimension = dimension; this.previousReach = previousReach; }
    }

    public static final class ModeRequest implements IMessage {
        int epoch;
        boolean enabled;
        public ModeRequest() { }
        ModeRequest(int epoch, boolean enabled) { this.epoch = epoch; this.enabled = enabled; }
        public void fromBytes(ByteBuf bytes) {
            if (bytes.readableBytes() != 5) throw new IllegalArgumentException("Invalid freecam mode request");
            epoch = bytes.readInt();
            enabled = bytes.readBoolean();
        }
        public void toBytes(ByteBuf bytes) { bytes.writeInt(epoch); bytes.writeBoolean(enabled); }
    }

    public static final class ModeAck implements IMessage {
        int epoch;
        boolean enabled;
        public ModeAck() { }
        ModeAck(int epoch, boolean enabled) { this.epoch = epoch; this.enabled = enabled; }
        public void fromBytes(ByteBuf bytes) {
            if (bytes.readableBytes() != 5) throw new IllegalArgumentException("Invalid freecam mode acknowledgement");
            epoch = bytes.readInt();
            enabled = bytes.readBoolean();
        }
        public void toBytes(ByteBuf bytes) { bytes.writeInt(epoch); bytes.writeBoolean(enabled); }
    }

    public static final class ServerMode implements IMessageHandler<ModeRequest, IMessage> {
        public IMessage onMessage(ModeRequest message, MessageContext context) {
            PENDING.put(context.getServerHandler().playerEntity, message);
            return null;
        }
    }

    public static final class ClientMode implements IMessageHandler<ModeAck, IMessage> {
        public IMessage onMessage(ModeAck message, MessageContext context) {
            if (message.epoch == clientEpoch) {
                acknowledged = message.enabled;
                ModLog.info("Server interaction mode acknowledged=" + message.enabled);
            }
            return null;
        }
    }
}
