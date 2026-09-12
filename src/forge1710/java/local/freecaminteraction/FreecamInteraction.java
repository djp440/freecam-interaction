package local.freecaminteraction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
import local.freecaminteraction.item.ItemFreecamWand;
import local.freecaminteraction.item.ItemFreecamWand.WandEntry;
import local.freecaminteraction.WandTier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.world.BlockEvent;

/** 模式消息在服务端 tick 应用；根据法杖等级与动态区块范围鉴权与扣费。 */
public final class FreecamInteraction {
    private static SimpleNetworkWrapper channel;
    public static volatile boolean available;
    public static volatile boolean acknowledged;
    private static volatile int clientEpoch;
    private static final Map<EntityPlayerMP, ModeRequest> PENDING = new ConcurrentHashMap<EntityPlayerMP, ModeRequest>();
    private static final Map<EntityPlayerMP, State> ACTIVE = new HashMap<EntityPlayerMP, State>();
    private static final Map<EntityPlayerMP, RayRecord> RAYS = new ConcurrentHashMap<EntityPlayerMP, RayRecord>();
    private static final UUID SPEED_CORE_MODIFIER_ID = UUID.fromString("6589f65e-78f1-4d06-884f-3213e70a69de");
    private static final String SPEED_CORE_MODIFIER_NAME = "freecam_interaction.speed_core";

    private static void updateSpeedModifier(EntityPlayerMP player) {
        int cameraMultiplier = ItemFreecamWand.getInventorySpeedMultiplier(player);
        double amount = ItemFreecamWand.getPlayerSpeedMultiplier(cameraMultiplier) - 1.0D;
        IAttributeInstance attribute = player.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        AttributeModifier current = attribute.getModifier(SPEED_CORE_MODIFIER_ID);
        if (current == null && amount == 0.0D) return;
        if (current != null && Double.compare(current.getAmount(), amount) == 0) return;
        if (current != null) attribute.removeModifier(current);
        if (amount > 0.0D) {
            attribute.applyModifier(new AttributeModifier(SPEED_CORE_MODIFIER_ID, SPEED_CORE_MODIFIER_NAME, amount, 2).setSaved(false));
        }
        ModLog.info("Speed core changed; player=" + player.getCommandSenderName() + "; camera=" + cameraMultiplier
                + "x; body=" + ItemFreecamWand.getPlayerSpeedMultiplier(cameraMultiplier) + "x");
    }

    private static void clearSpeedModifier(EntityPlayerMP player) {
        IAttributeInstance attribute = player.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        AttributeModifier current = attribute.getModifier(SPEED_CORE_MODIFIER_ID);
        if (current != null) attribute.removeModifier(current);
    }

    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("freecam1710v2");
        channel.registerMessage(ServerMode.class, ModeRequest.class, 0, Side.SERVER);
        channel.registerMessage(ClientMode.class, ModeAck.class, 1, Side.CLIENT);
        FreecamActions.initialize(channel);
        FreecamInteraction events = new FreecamInteraction();
        FMLCommonHandler.instance().bus().register(events);
        MinecraftForge.EVENT_BUS.register(events);
        MinecraftForge.EVENT_BUS.register(new FreecamDropCollector());
    }

    public static void request(boolean enabled) {
        acknowledged = false;
        if (available && channel != null) {
            channel.sendToServer(new ModeRequest(++clientEpoch, enabled));
        }
    }

    public static boolean active(EntityPlayer player) {
        State state = ACTIVE.get(player);
        return state != null && state.dimension == player.dimension && player.isEntityAlive() && !player.isPlayerSleeping();
    }

    public static WandTier getActiveTier(EntityPlayer player) {
        if (player == null) return WandTier.NORMAL;
        if (player.worldObj != null && player.worldObj.isRemote) {
            return local.freecaminteraction.client.FreecamClient.currentTier();
        }
        State state = ACTIVE.get(player);
        return state != null ? state.tier : WandTier.NORMAL;
    }

    public static int getActiveWandSlot(EntityPlayer player) {
        if (player == null) return -1;
        if (player.worldObj != null && player.worldObj.isRemote) {
            if (!local.freecaminteraction.client.FreecamClient.isFreecamActive()) return -1;
            ItemFreecamWand.WandEntry wand = ItemFreecamWand.findBestWand(player);
            return wand != null ? wand.slot : -1;
        }
        State state = ACTIVE.get(player);
        return (state != null && active(player)) ? state.selectedSlot : -1;
    }

    /**
     * 蓝图施工资格：只要背包中存在任意一把装有蓝图核心的法杖即为真。
     * 与 {@link ItemFreecamWand#findBestWand} 的语义一致：当前生效（耐久最优）的法杖可以是任一把，
     * 蓝图核心只要求“已拥有”，不要求装在这一把上；否则背包中同时存在无核心法杖时会永久无法施工。
     */
    public static boolean hasBlueprintCoreWand(EntityPlayer player) {
        if (player == null || !active(player)) return false;
        if (player.inventory == null || player.inventory.mainInventory == null) return false;
        ItemStack[] inventory = player.inventory.mainInventory;
        int limit = Math.min(36, inventory.length);
        for (int i = 0; i < limit; i++) {
            ItemStack stack = inventory[i];
            if (stack != null && stack.getItem() instanceof ItemFreecamWand && ItemFreecamWand.hasBlueprintCore(stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean inside(EntityPlayer player, int x, int y, int z) {
        WandTier tier = getActiveTier(player);
        return y >= 0 && y < player.worldObj.getHeight() && player.worldObj.blockExists(x, y, z)
                && FreecamRange.contains(player.worldObj, tier, player.posX, player.boundingBox.minY, player.posZ, x, y, z);
    }

    public static double distance(EntityPlayer player, double x, double y, double z) {
        if (active(player) && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && inside(player, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) {
            return 0.0D;
        }
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

    private static void enable(EntityPlayerMP player, WandEntry wand) {
        FreecamChunkLoader.ChunkSession session = FreecamChunkLoader.createSession(
                FreecamInteractionMod.instance, player, wand.tier);
        if (session == null) {
            if (channel != null) channel.sendTo(new ModeAck(clientEpoch, false, 0, 0), player);
            ModLog.info("Server rejected freecam mode for " + player.getCommandSenderName() + ": chunk allocation failed");
            return;
        }

        State state = ACTIVE.get(player);
        if (state == null) {
            state = new State(player.dimension, player.theItemInWorldManager.getBlockReachDistance(), wand.tier, wand.slot, session);
            ACTIVE.put(player, state);
        } else {
            state.dimension = player.dimension;
            state.tier = wand.tier;
            state.selectedSlot = wand.slot;
            if (state.chunkSession != null) FreecamChunkLoader.releaseSession(state.chunkSession);
            state.chunkSession = session;
        }
        state.lastContainer = player.openContainer;
        cancelMining(player);
        player.theItemInWorldManager.setBlockReachDistance(256.0D);
        if (channel != null) channel.sendTo(new ModeAck(clientEpoch, true, wand.tier.ordinal(), wand.tier.radius), player);
        ModLog.info("Server freecam enabled; player=" + player.getCommandSenderName() + "; tier=" + wand.tier.name() + "; slot=" + wand.slot);
    }

    public static void clear(EntityPlayerMP player) {
        PENDING.remove(player);
        FreecamActions.clear(player);
        FreecamEffects.clear(player);
        consumeRay(player);
        State state = ACTIVE.remove(player);
        cancelMining(player);
        if (state != null) {
            FreecamChunkLoader.releaseSession(state.chunkSession);
            player.theItemInWorldManager.setBlockReachDistance(state.previousReach);
            if (channel != null) channel.sendTo(new ModeAck(0, false, 0, 0), player);
            ModLog.info("Server freecam cleared; player=" + player.getCommandSenderName());
        }
    }

    /** 服务端唯一扣费入口：复核法杖 -> 扣除 1 耐久（钳制至少剩 1） -> 发生 2->1 则立即接续下一把 */
    public static boolean canDeductUsage(EntityPlayerMP player) {
        State state = ACTIVE.get(player);
        if (state == null || !active(player)) return false;
        if (state.tier == WandTier.CREATIVE) return true;
        if (state.selectedSlot >= 0 && state.selectedSlot < 36) {
            ItemStack stack = player.inventory.mainInventory[state.selectedSlot];
            if (stack != null && stack.getItem() instanceof ItemFreecamWand
                    && ((ItemFreecamWand) stack.getItem()).tier == state.tier
                    && stack.getMaxDamage() - stack.getItemDamage() > 1) return true;
        }
        return ItemFreecamWand.findBestWand(player) != null;
    }

    public static boolean deductUsage(EntityPlayerMP player) {
        State state = ACTIVE.get(player);
        if (state == null || !active(player)) return false;
        if (state.tier == WandTier.CREATIVE) {
            return true; // 创造法杖不扣耐久
        }

        ItemStack stack = null;
        if (state.selectedSlot >= 0 && state.selectedSlot < 36) {
            stack = player.inventory.mainInventory[state.selectedSlot];
        }
        if (stack == null || !(stack.getItem() instanceof ItemFreecamWand)
                || ((ItemFreecamWand) stack.getItem()).tier != state.tier) {
            WandEntry rechecked = ItemFreecamWand.findBestWand(player);
            if (rechecked == null) {
                clear(player);
                return false;
            }
            state.selectedSlot = rechecked.slot;
            state.tier = rechecked.tier;
            stack = rechecked.stack;
        }

        ItemFreecamWand wand = (ItemFreecamWand) stack.getItem();
        int damage = stack.getItemDamage();
        int maxDamage = wand.tier.maxDamage;
        int remaining = maxDamage - damage;

        if (remaining <= 1) {
            succeedWand(player, state);
            return false;
        }

        damage++;
        if (damage > maxDamage - 1) damage = maxDamage - 1; // 钳制最多损耗至剩余 1，绝不损坏消失
        stack.setItemDamage(damage);
        player.inventoryContainer.detectAndSendChanges();

        int newRemaining = maxDamage - damage;
        ModLog.info("Wand durability deducted; player=" + player.getCommandSenderName()
                + "; slot=" + state.selectedSlot + "; remaining=" + newRemaining);

        if (newRemaining == 1) {
            succeedWand(player, state);
        }
        return true;
    }

    private static void succeedWand(EntityPlayerMP player, State state) {
        WandEntry next = ItemFreecamWand.findBestWand(player);
        if (next != null) {
            state.selectedSlot = next.slot;
            if (next.tier != state.tier) {
                state.tier = next.tier;
                FreecamChunkLoader.releaseSession(state.chunkSession);
                state.chunkSession = FreecamChunkLoader.createSession(FreecamInteractionMod.instance, player, next.tier);
                if (channel != null) channel.sendTo(new ModeAck(0, true, next.tier.ordinal(), next.tier.radius), player);
            }
            ModLog.info("Wand succeeded to slot=" + next.slot + "; tier=" + next.tier.name());
        } else {
            ModLog.info("No more available wands for player=" + player.getCommandSenderName() + "; exiting freecam");
            clear(player);
        }
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
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void breakBlockSuccess(BlockEvent.BreakEvent event) {
        if (event.world.isRemote || !ACTIVE.containsKey(event.getPlayer()) || event.isCanceled()) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        if (active(player)) {
            deductUsage(player);
            ModLog.info("Allowed break: player=" + player.getCommandSenderName()
                    + "; target=" + event.x + "," + event.y + "," + event.z);
        }
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
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void placeSuccess(BlockEvent.PlaceEvent event) {
        if (event.world.isRemote || !ACTIVE.containsKey(event.player) || event.isCanceled()) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (active(player)) {
            if (event instanceof BlockEvent.MultiPlaceEvent) {
                for (BlockSnapshot block : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) {
                    FreecamEffects.recordPlacement(player, block.x, block.y, block.z);
                }
            } else {
                FreecamEffects.recordPlacement(player, event.x, event.y, event.z);
            }
            deductUsage(player);
            ModLog.info("Allowed placement: player=" + player.getCommandSenderName()
                    + "; target=" + event.x + "," + event.y + "," + event.z);
        }
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
        if (event.side != Side.SERVER || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;

        if (event.phase == TickEvent.Phase.START) {
            updateSpeedModifier(player);
            ModeRequest mode = PENDING.remove(player);
            if (mode != null) {
                clientEpoch = mode.epoch;
                boolean enabled = mode.enabled && player.isEntityAlive() && !player.isPlayerSleeping();
                if (enabled) {
                    WandEntry wand = ItemFreecamWand.findBestWand(player);
                    if (wand != null) {
                        enable(player, wand);
                    } else {
                        clear(player);
                        channel.sendTo(new ModeAck(mode.epoch, false, 0, 0), player);
                        ModLog.info("Server rejected freecam mode for " + player.getCommandSenderName() + ": no available wand");
                    }
                } else {
                    clear(player);
                    channel.sendTo(new ModeAck(mode.epoch, false, 0, 0), player);
                }
            }

            if (ACTIVE.containsKey(player)) {
                if (!active(player)) {
                    clear(player);
                } else {
                    player.theItemInWorldManager.setBlockReachDistance(256.0D);
                    FreecamEffects.aura(player);
                }
            }
        } else if (event.phase == TickEvent.Phase.END) {
            State state = ACTIVE.get(player);
            if (state != null) {
                if (player.openContainer != state.lastContainer) {
                    if (player.openContainer != player.inventoryContainer) {
                        deductUsage(player);
                        ModLog.info("Container opened: player=" + player.getCommandSenderName()
                                + "; container=" + player.openContainer.getClass().getSimpleName());
                    }
                    state.lastContainer = player.openContainer;
                }
            }
        }
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.player instanceof EntityPlayerMP) { clearSpeedModifier((EntityPlayerMP) event.player); clear((EntityPlayerMP) event.player); } }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { if (event.player instanceof EntityPlayerMP) { clearSpeedModifier((EntityPlayerMP) event.player); clear((EntityPlayerMP) event.player); } }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.player instanceof EntityPlayerMP) { clearSpeedModifier((EntityPlayerMP) event.player); clear((EntityPlayerMP) event.player); } }

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
        WandTier tier;
        int selectedSlot;
        Container lastContainer;
        FreecamChunkLoader.ChunkSession chunkSession;

        State(int dimension, double previousReach, WandTier tier, int selectedSlot, FreecamChunkLoader.ChunkSession chunkSession) {
            this.dimension = dimension;
            this.previousReach = previousReach;
            this.tier = tier;
            this.selectedSlot = selectedSlot;
            this.chunkSession = chunkSession;
        }
    }

    public static final class ModeRequest implements IMessage {
        public int epoch;
        public boolean enabled;
        public ModeRequest() { }
        public ModeRequest(int epoch, boolean enabled) { this.epoch = epoch; this.enabled = enabled; }
        public void fromBytes(ByteBuf bytes) {
            if (bytes.readableBytes() != 5) throw new IllegalArgumentException("Invalid freecam mode request");
            epoch = bytes.readInt();
            enabled = bytes.readBoolean();
        }
        public void toBytes(ByteBuf bytes) { bytes.writeInt(epoch); bytes.writeBoolean(enabled); }
    }

    public static final class ModeAck implements IMessage {
        public int epoch;
        public boolean enabled;
        public int tierOrdinal;
        public int radius;
        public ModeAck() { }
        public ModeAck(int epoch, boolean enabled, int tierOrdinal, int radius) {
            this.epoch = epoch; this.enabled = enabled; this.tierOrdinal = tierOrdinal; this.radius = radius;
        }
        public void fromBytes(ByteBuf bytes) {
            if (bytes.readableBytes() != 13) throw new IllegalArgumentException("Invalid freecam mode acknowledgement");
            epoch = bytes.readInt();
            enabled = bytes.readBoolean();
            tierOrdinal = bytes.readInt();
            radius = bytes.readInt();
        }
        public void toBytes(ByteBuf bytes) {
            bytes.writeInt(epoch);
            bytes.writeBoolean(enabled);
            bytes.writeInt(tierOrdinal);
            bytes.writeInt(radius);
        }
    }

    public static final class ServerMode implements IMessageHandler<ModeRequest, IMessage> {
        public IMessage onMessage(ModeRequest message, MessageContext context) {
            PENDING.put(context.getServerHandler().playerEntity, message);
            return null;
        }
    }

    public static final class ClientMode implements IMessageHandler<ModeAck, IMessage> {
        public IMessage onMessage(ModeAck message, MessageContext context) {
            if (message.epoch == clientEpoch || message.epoch == 0) {
                acknowledged = message.enabled;
                if (message.enabled) {
                    local.freecaminteraction.client.FreecamClient.onAck(message.tierOrdinal, message.radius);
                } else {
                    local.freecaminteraction.client.FreecamClient.onReject();
                }
                ModLog.info("Server interaction mode acknowledged=" + message.enabled + "; tier=" + message.tierOrdinal);
            }
            return null;
        }
    }

    public static final class RayRecord {
        public final net.minecraft.util.Vec3 start, end, point;
        public final long timestamp;

        public RayRecord(net.minecraft.util.Vec3 start, net.minecraft.util.Vec3 end, net.minecraft.util.Vec3 point, long timestamp) {
            this.start = start;
            this.end = end;
            this.point = point;
            this.timestamp = timestamp;
        }
    }

    public static void recordRay(EntityPlayerMP player, net.minecraft.util.Vec3 start, net.minecraft.util.Vec3 end, net.minecraft.util.Vec3 point) {
        if (player == null || !active(player)) return;
        if (!FreecamTarget.finite(start) || !FreecamTarget.finite(end) || !FreecamTarget.finite(point)) return;
        WandTier tier = getActiveTier(player);
        double cameraBound = Math.sqrt(3) * FreecamRange.cameraReach(tier) + 16;
        net.minecraft.util.Vec3 playerPos = net.minecraft.util.Vec3.createVectorHelper(player.posX, player.boundingBox.minY, player.posZ);
        if (start.squareDistanceTo(playerPos) > cameraBound * cameraBound) return;
        RAYS.put(player, new RayRecord(start, end, point, System.currentTimeMillis()));
    }

    public static void consumeRay(EntityPlayerMP player) {
        if (player != null) RAYS.remove(player);
    }

    public static Object customPlayerRay(EntityPlayer player, float eyeOffset) {
        if (!(player instanceof EntityPlayerMP) || !active(player)) return null;
        RayRecord ray = RAYS.get(player);
        if (ray == null) return null;
        if (System.currentTimeMillis() - ray.timestamp > 1500L) {
            RAYS.remove(player);
            return null;
        }
        try {
            Class<?> clazz = Class.forName("appeng.util.LookDirection", false, player.getClass().getClassLoader());
            return clazz.getConstructor(net.minecraft.util.Vec3.class, net.minecraft.util.Vec3.class).newInstance(ray.start, ray.end);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object beginAe2Placement(EntityPlayer player, net.minecraft.world.World world, int x, int y, int z) {
        if (!(player instanceof EntityPlayerMP) || !active(player)) return null;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        if (!inside(mp, x, y, z)) return Boolean.FALSE;
        return FreecamDropCollector.begin(mp, world, x, y, z, "ae2_part");
    }

    public static void endAe2Placement(Object token, EntityPlayer player, boolean success) {
        if (token != null && !(token instanceof Boolean)) {
            FreecamDropCollector.end(token, success);
        }
        if (success && player instanceof EntityPlayerMP && active(player)) {
            deductUsage((EntityPlayerMP) player);
            consumeRay((EntityPlayerMP) player);
        }
    }

    private static String playerName(EntityPlayer player) {
        if (player == null) return "unknown";
        try { return player.getCommandSenderName() != null ? player.getCommandSenderName() : "unknown"; }
        catch (Throwable ignored) { return "unknown"; }
    }
}
