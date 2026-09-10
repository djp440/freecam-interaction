package local.freecaminteraction;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/** 收集自由视角合法同步交互直接生成的物品实体，并在操作成功后放入操作者背包。 */
public final class FreecamDropCollector {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<Context>();

    FreecamDropCollector() {}

    public static Object beginBlock(ItemInWorldManager manager, EntityPlayer player, World world,
            int x, int y, int z, String kind) {
        EntityPlayerMP owner = player instanceof EntityPlayerMP ? (EntityPlayerMP) player
                : manager == null ? null : manager.thisPlayerMP;
        World actualWorld = world != null ? world : manager == null ? null : manager.theWorld;
        return begin(owner, actualWorld, x, y, z, kind);
    }

    public static Object begin(EntityPlayerMP player, World world, double x, double y, double z, String kind) {
        if (player == null || world == null || world.isRemote || !FreecamInteraction.active(player)
                || !FreecamInteraction.inside(player, FreecamRange.floor(x), FreecamRange.floor(y), FreecamRange.floor(z))) {
            return null;
        }
        Context context = new Context(CURRENT.get(), player, world, FreecamInteraction.getActiveTier(player),
                player.posX, player.boundingBox.minY, player.posZ, kind);
        CURRENT.set(context);
        return context;
    }

    public static void end(Object token, boolean success) {
        if (!(token instanceof Context)) return;
        Context context = (Context) token;
        if (CURRENT.get() != context) {
            CURRENT.remove();
            ModLog.info("Drop collection context mismatch; player=" + playerName(context.player));
            return;
        }
        CURRENT.set(context.parent);
        if (!success) return;
        int inserted = 0;
        int remaining = 0;
        for (EntityItem entity : context.items) {
            if (entity == null || entity.isDead || entity.worldObj != context.world
                    || !context.world.loadedEntityList.contains(entity)) continue;
            ItemStack stack = entity.getEntityItem();
            if (stack == null || stack.stackSize <= 0) continue;
            int before = stack.stackSize;
            insert(context.player, stack);
            inserted += before - stack.stackSize;
            if (stack.stackSize <= 0) entity.setDead(); else remaining += stack.stackSize;
        }
        if (inserted > 0) context.player.inventoryContainer.detectAndSendChanges();
        ModLog.info("Interaction drops settled; player=" + playerName(context.player)
                + "; kind=" + context.kind + "; inserted=" + inserted + "; remaining=" + remaining);
    }

    private static String playerName(EntityPlayer player) {
        if (player == null) return "unknown";
        try { return player.getCommandSenderName() != null ? player.getCommandSenderName() : "unknown"; }
        catch (Throwable ignored) { return "unknown"; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void entityJoined(EntityJoinWorldEvent event) {
        Context context = CURRENT.get();
        if (context == null || event.world != context.world || !(event.entity instanceof EntityItem)) return;
        EntityItem item = (EntityItem) event.entity;
        if (!FreecamRange.contains(context.world, context.tier, context.playerX, context.playerY, context.playerZ,
                FreecamRange.floor(item.posX), FreecamRange.floor(item.posY), FreecamRange.floor(item.posZ))) return;
        if (!context.seen.containsKey(item)) {
            context.seen.put(item, Boolean.TRUE);
            context.items.add(item);
        }
    }

    /** 原版背包规则的最小实现，避免创造模式满包时静默吞掉剩余物品。 */
    static int insert(EntityPlayerMP player, ItemStack stack) {
        int before = stack == null ? 0 : stack.stackSize;
        if (stack == null || stack.stackSize <= 0 || stack.getItem() == null) return 0;
        ItemStack[] slots = player.inventory.mainInventory;
        if (stack.isStackable()) {
            for (int i = 0; i < slots.length && stack.stackSize > 0; i++) {
                ItemStack existing = slots[i];
                if (existing == null || !existing.isItemEqual(stack)
                        || !ItemStack.areItemStackTagsEqual(existing, stack)) continue;
                int limit = Math.min(existing.getMaxStackSize(), player.inventory.getInventoryStackLimit());
                int moved = Math.min(stack.stackSize, limit - existing.stackSize);
                if (moved > 0) { existing.stackSize += moved; stack.stackSize -= moved; existing.animationsToGo = 5; }
            }
        }
        for (int i = 0; i < slots.length && stack.stackSize > 0; i++) {
            if (slots[i] != null) continue;
            int moved = Math.min(stack.stackSize, Math.min(stack.getMaxStackSize(), player.inventory.getInventoryStackLimit()));
            ItemStack copy = stack.copy();
            copy.stackSize = moved;
            copy.animationsToGo = 5;
            slots[i] = copy;
            stack.stackSize -= moved;
        }
        return before - stack.stackSize;
    }

    private static final class Context {
        final Context parent;
        final EntityPlayerMP player;
        final World world;
        final WandTier tier;
        final double playerX, playerY, playerZ;
        final String kind;
        final List<EntityItem> items = new ArrayList<EntityItem>();
        final Map<EntityItem, Boolean> seen = new IdentityHashMap<EntityItem, Boolean>();

        Context(Context parent, EntityPlayerMP player, World world, WandTier tier,
                double playerX, double playerY, double playerZ, String kind) {
            this.parent = parent; this.player = player; this.world = world; this.tier = tier;
            this.playerX = playerX; this.playerY = playerY; this.playerZ = playerZ; this.kind = kind;
        }
    }
}
