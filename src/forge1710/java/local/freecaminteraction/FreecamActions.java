package local.freecaminteraction;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import local.freecaminteraction.WandTier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** 网络线程只保存一条待处理操作；游戏线程复核并调用原生实体/物品入口，并在动作成功后扣除法杖耐久。 */
public final class FreecamActions {
    public static final int ATTACK = 0, INTERACT = 1, FISH = 2, USE_BUCKET = 3;
    private static final Map<EntityPlayerMP, Action> PENDING = new ConcurrentHashMap<EntityPlayerMP, Action>();
    private static final Field BUCKET_CONTENT = ReflectionHelper.findField(ItemBucket.class, "isFull", "field_77876_a");
    private static final Map<EntityPlayerMP, long[]> LAST = new WeakHashMap<EntityPlayerMP, long[]>();
    private static SimpleNetworkWrapper channel;
    static EntityPlayer authorized;
    static boolean fishing;
    private static MovingObjectPosition bucketTarget;

    public static void initialize(SimpleNetworkWrapper network) {
        channel = network;
        channel.registerMessage(Handler.class, Action.class, 2, Side.SERVER);
        FMLCommonHandler.instance().bus().register(new FreecamActions());
    }

    public static void send(Action action) { channel.sendToServer(action); }
    public static void clear(EntityPlayerMP player) { PENDING.remove(player); LAST.remove(player); }

    public static boolean emptyBucket(ItemStack stack) {
        try { return stack != null && stack.getItem() instanceof ItemBucket && BUCKET_CONTENT.get(stack.getItem()) == Blocks.air; }
        catch (IllegalAccessException error) { throw new IllegalStateException("无法读取桶内容", error); }
    }

    /** 由 ItemBucket 字节码补丁调用；普通游戏操作保持原命中。 */
    public static MovingObjectPosition bucketHit(MovingObjectPosition original, EntityPlayer player) {
        return authorized == player && bucketTarget != null ? bucketTarget : original;
    }

    public static final class Action implements IMessage {
        public int kind, slot, target;
        public Vec3 start, end, point;
        public Action() {}
        public Action(int kind, int slot, int target, Vec3 start, Vec3 end, Vec3 point) {
            this.kind = kind; this.slot = slot; this.target = target; this.start = start; this.end = end; this.point = point;
        }
        public void fromBytes(ByteBuf b) {
            if (b.readableBytes() != 84) throw new IllegalArgumentException("Invalid freecam action");
            kind = b.readInt(); slot = b.readInt(); target = b.readInt();
            start = read(b); end = read(b); point = read(b);
        }
        public void toBytes(ByteBuf b) { b.writeInt(kind); b.writeInt(slot); b.writeInt(target); write(b, start); write(b, end); write(b, point); }
        private static void write(ByteBuf b, Vec3 v) { b.writeDouble(v.xCoord); b.writeDouble(v.yCoord); b.writeDouble(v.zCoord); }
        private static Vec3 read(ByteBuf b) { return Vec3.createVectorHelper(b.readDouble(), b.readDouble(), b.readDouble()); }
    }

    public static final class Handler implements IMessageHandler<Action, IMessage> {
        public IMessage onMessage(Action message, MessageContext context) {
            PENDING.put(context.getServerHandler().playerEntity, message);
            return null;
        }
    }

    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if (event.side != Side.SERVER || event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        Action action = PENDING.remove(player);
        if (action != null) {
            authorized = player;
            try { handle(player, action); }
            finally { authorized = null; fishing = false; bucketTarget = null; }
        }
    }

    private static void handle(EntityPlayerMP player, Action action) {
        if (!FreecamInteraction.active(player) || action.kind < ATTACK || action.kind > USE_BUCKET
                || action.slot < 0 || action.slot > 8 || player.inventory.currentItem != action.slot
                || !FreecamTarget.finite(action.start) || !FreecamTarget.finite(action.end) || !FreecamTarget.finite(action.point)) {
            reject(player, "state_or_payload"); return;
        }
        long now = player.worldObj.getTotalWorldTime();
        long[] last = LAST.get(player);
        if (last == null) { last = new long[] {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2}; LAST.put(player, last); }
        int index = action.kind == ATTACK ? 0 : 1;
        if (now - last[index] < (index == 0 ? 1 : 4)) return;
        last[index] = now;
        ItemStack item = player.getHeldItem();
        boolean rod = item != null && item.getItem() instanceof ItemFishingRod;
        boolean retrieve = action.kind == FISH && rod && player.fishEntity != null;
        MovingObjectPosition hit = null;

        if (!retrieve) {
            WandTier tier = FreecamInteraction.getActiveTier(player);
            double cameraBound = Math.sqrt(3) * FreecamRange.cameraReach(tier) + 16;
            if (action.start.squareDistanceTo(Vec3.createVectorHelper(player.posX, player.boundingBox.minY, player.posZ)) > cameraBound * cameraBound) {
                reject(player, "camera"); return;
            }
            if (action.kind == USE_BUCKET) {
                if (item == null || !(item.getItem() instanceof ItemBucket)) { reject(player, "not_bucket"); return; }
                hit = FreecamTarget.pickBlock(player, action.start, action.end, emptyBucket(item));
                if (hit == null || hit.hitVec.squareDistanceTo(action.point) > 0.25
                        || !emptyBucket(item) && !FreecamInteraction.inside(player,
                            hit.blockX + (hit.sideHit == 4 ? -1 : hit.sideHit == 5 ? 1 : 0),
                            hit.blockY + (hit.sideHit == 0 ? -1 : hit.sideHit == 1 ? 1 : 0),
                            hit.blockZ + (hit.sideHit == 2 ? -1 : hit.sideHit == 3 ? 1 : 0))) {
                    reject(player, "stale_occluded_or_outside_bucket_target"); return;
                }
            } else {
                hit = FreecamTarget.pick(player, action.start, action.end);
                if (hit == null) { reject(player, "no_target"); return; }
                int targetId = hit.entityHit == null ? -1 : hit.entityHit.getEntityId();
                if (targetId != action.target) { reject(player, "target_changed_or_occluded"); return; }
            }
        }
        if (action.kind == USE_BUCKET) {
            bucketTarget = hit;
            boolean success = false;
            try { success = player.theItemInWorldManager.tryUseItem(player, player.worldObj, item); }
            finally { bucketTarget = null; }
            if (success) {
                FreecamInteraction.deductUsage(player);
            }
        } else if (action.kind == FISH) {
            if (!rod) { reject(player, "not_fishing_rod"); return; }
            fishing = true;
            float yaw = player.rotationYaw, pitch = player.rotationPitch;
            boolean success = false;
            try {
                if (!retrieve) {
                    double x = hit.hitVec.xCoord - player.posX;
                    double y = hit.hitVec.yCoord - (player.posY + player.getEyeHeight());
                    double z = hit.hitVec.zCoord - player.posZ;
                    if (x * x + y * y + z * z < 1.0e-8) return;
                    player.rotationYaw = (float) Math.toDegrees(Math.atan2(-x, z));
                    player.rotationPitch = (float) -Math.toDegrees(Math.atan2(y, Math.hypot(x, z)));
                }
                PlayerInteractEvent event = ForgeEventFactory.onPlayerInteract(player, PlayerInteractEvent.Action.RIGHT_CLICK_AIR,
                        0, 0, 0, -1, player.worldObj);
                if (!event.isCanceled() && event.useItem != cpw.mods.fml.common.eventhandler.Event.Result.DENY) {
                    success = player.theItemInWorldManager.tryUseItem(player, player.worldObj, item);
                }
            } finally { player.rotationYaw = yaw; player.rotationPitch = pitch; fishing = false; }
            if (success) {
                FreecamInteraction.deductUsage(player);
            }
        } else if (hit.entityHit instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) hit.entityHit;
            WorldServer ws = (WorldServer) player.worldObj;
            if (action.kind == INTERACT) {
                boolean done = player.interactWith(living);
                ModLog.info("Entity interaction; player=" + player.getCommandSenderName() + "; target=" + living.getEntityId()
                        + "; handled=" + done + "; distanceSq=" + player.getDistanceSqToEntity(living)
                        + "; container=" + player.openContainer.getClass().getSimpleName());
                if (done) {
                    player.swingItem();
                    ws.getEntityTracker().func_151248_b(player, new S0BPacketAnimation(player, 0));
                    FreecamInteraction.deductUsage(player);
                }
            } else {
                if (living instanceof EntityPlayer && (!net.minecraft.server.MinecraftServer.getServer().isPVPEnabled()
                        || !player.canAttackPlayer((EntityPlayer) living))) return;
                double x = Math.max(living.boundingBox.minX, Math.min(player.posX, living.boundingBox.maxX));
                double y = Math.max(living.boundingBox.minY, Math.min(player.posY + player.getEyeHeight(), living.boundingBox.maxY));
                double z = Math.max(living.boundingBox.minZ, Math.min(player.posZ, living.boundingBox.maxZ));
                double reach = player.capabilities.isCreativeMode ? 6 : 3;
                Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
                boolean attacked = false;
                if (eye.squareDistanceTo(Vec3.createVectorHelper(x, y, z)) < reach * reach && player.canEntityBeSeen(living)) {
                    player.attackTargetEntityWithCurrentItem(living);
                    attacked = true;
                } else if (!living.isEntityInvulnerable() && living.canAttackWithItem() && living.hurtTime == 0
                        && !MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(player, living))) {
                    double dx = player.posX - living.posX, dz = player.posZ - living.posZ;
                    if (dx * dx + dz * dz < 1.0e-8) { dx = 0.01; dz = 0; }
                    living.knockBack(player, 0, dx, dz);
                    living.velocityChanged = true;
                    living.hurtTime = living.maxHurtTime = 10;
                    ws.getEntityTracker().func_151248_b(living, new S12PacketEntityVelocity(living));
                    ws.getEntityTracker().func_151248_b(living, new S19PacketEntityStatus(living, (byte) 2));
                    attacked = true;
                }
                if (attacked) {
                    player.swingItem();
                    ws.getEntityTracker().func_151248_b(player, new S0BPacketAnimation(player, 0));
                    FreecamInteraction.deductUsage(player);
                }
            }
        } else { reject(player, "not_living_target"); return; }
        player.inventoryContainer.detectAndSendChanges();
        ModLog.info("Action=" + action.kind + "; player=" + player.getCommandSenderName() + "; target=" + action.target);
    }

    private static void reject(EntityPlayerMP player, String reason) {
        ModLog.info("Action rejected; player=" + player.getCommandSenderName() + "; reason=" + reason);
    }
}
