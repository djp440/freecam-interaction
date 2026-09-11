package local.freecaminteraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** 服务端唯一的自由视角末影指示广播入口。 */
public final class FreecamEffects {
    private static final int INTERACTION_PARTICLES = 24;
    private static final Map<EntityPlayerMP, List<int[]>> PLACEMENTS = new WeakHashMap<EntityPlayerMP, List<int[]>>();

    private FreecamEffects() {}

    public static void aura(EntityPlayerMP player) {
        if (player.ticksExisted % 2 != 0 || !(player.worldObj instanceof WorldServer)) return;
        WorldServer world = (WorldServer) player.worldObj;
        world.func_147487_a("portal", player.posX, player.boundingBox.minY + player.height * 0.55D, player.posZ,
                2, 0.55D, Math.max(0.4D, player.height * 0.45D), 0.55D, 0.02D);
    }

    public static synchronized void beginUse(EntityPlayer player) {
        if (player instanceof EntityPlayerMP && FreecamInteraction.active(player)) {
            PLACEMENTS.put((EntityPlayerMP) player, new ArrayList<int[]>());
        }
    }

    public static synchronized void recordPlacement(EntityPlayerMP player, int x, int y, int z) {
        List<int[]> positions = PLACEMENTS.get(player);
        if (positions == null) return;
        for (int[] position : positions) {
            if (position[0] == x && position[1] == y && position[2] == z) return;
        }
        positions.add(new int[] {x, y, z});
    }

    public static synchronized void finishUse(EntityPlayer player, World world, int x, int y, int z, boolean success) {
        if (!(player instanceof EntityPlayerMP)) return;
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        List<int[]> positions = PLACEMENTS.remove(serverPlayer);
        if (!success || !FreecamInteraction.active(serverPlayer) || !(world instanceof WorldServer)) return;
        if (positions == null || positions.isEmpty()) {
            interaction((WorldServer) world, x + 0.5D, y + 0.5D, z + 0.5D);
            return;
        }
        for (int[] position : positions) {
            interaction((WorldServer) world, position[0] + 0.5D, position[1] + 0.5D, position[2] + 0.5D);
        }
    }

    public static void block(EntityPlayerMP player, int x, int y, int z) {
        if (FreecamInteraction.active(player) && player.worldObj instanceof WorldServer) {
            interaction((WorldServer) player.worldObj, x + 0.5D, y + 0.5D, z + 0.5D);
        }
    }

    public static void finishBreak(ItemInWorldManager manager, int x, int y, int z, boolean success) {
        if (success && manager != null) block(manager.thisPlayerMP, x, y, z);
    }

    public static void entity(EntityPlayerMP player, Entity target) {
        if (FreecamInteraction.active(player) && target != null && target.worldObj instanceof WorldServer) {
            interaction((WorldServer) target.worldObj, target.posX, target.boundingBox.minY + target.height * 0.5D, target.posZ);
        }
    }

    public static synchronized void clear(EntityPlayerMP player) {
        PLACEMENTS.remove(player);
    }

    private static void interaction(WorldServer world, double x, double y, double z) {
        world.func_147487_a("portal", x, y, z, INTERACTION_PARTICLES, 0.45D, 0.45D, 0.45D, 0.08D);
    }
}
