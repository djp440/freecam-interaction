package local.freecaminteraction;

import java.lang.reflect.Method;
import java.util.*;
import local.freecaminteraction.WandTier;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

public final class FreecamChunkLoader {
    private static Method getWatcherMethod;
    private static Method addPlayerMethod;
    private static Method removePlayerMethod;
    private static boolean watcherReflectionInitialized = false;

    private FreecamChunkLoader() {}

    public static void initialize(Object mod) {
        ForgeChunkManager.setForcedChunkLoadingCallback(mod, new LoadingCallback() {
            @Override
            public void ticketsLoaded(List<Ticket> tickets, World world) {
                for (Ticket ticket : tickets) {
                    ForgeChunkManager.releaseTicket(ticket);
                }
                ModLog.info("Cleaned up lingering freecam chunk tickets on world load: " + tickets.size());
            }
        });
        initWatcherReflection();
        ModLog.info("Chunk loader initialized with ForgeChunkManager callback");
    }

    private static void initWatcherReflection() {
        if (watcherReflectionInitialized) return;
        watcherReflectionInitialized = true;
        try {
            for (Method m : PlayerManager.class.getDeclaredMethods()) {
                if ((m.getName().equals("getOrCreateChunkWatcher") || m.getName().equals("func_72690_a"))
                        && m.getParameterTypes().length == 3) {
                    getWatcherMethod = m;
                    getWatcherMethod.setAccessible(true);
                    break;
                }
            }

            Class<?> playerInstanceClass = null;
            for (Class<?> inner : PlayerManager.class.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("PlayerInstance")) {
                    playerInstanceClass = inner;
                    break;
                }
            }
            if (playerInstanceClass != null) {
                for (Method m : playerInstanceClass.getDeclaredMethods()) {
                    if ((m.getName().equals("addPlayer") || m.getName().equals("func_73255_a"))
                            && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == EntityPlayerMP.class) {
                        addPlayerMethod = m;
                        addPlayerMethod.setAccessible(true);
                    } else if ((m.getName().equals("removePlayer") || m.getName().equals("func_73252_b"))
                            && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == EntityPlayerMP.class) {
                        removePlayerMethod = m;
                        removePlayerMethod.setAccessible(true);
                    }
                }
            }
        } catch (Throwable error) {
            ModLog.info("PlayerManager chunk watcher reflection not fully available: " + error.getMessage());
        }
    }

    public static final class ChunkSession {
        public final EntityPlayerMP player;
        public final WandTier tier;
        public final List<Ticket> tickets = new ArrayList<Ticket>();
        public final Set<ChunkCoordIntPair> forcedChunks = new HashSet<ChunkCoordIntPair>();
        public final Set<ChunkCoordIntPair> extraWatchedChunks = new HashSet<ChunkCoordIntPair>();

        public ChunkSession(EntityPlayerMP player, WandTier tier) {
            this.player = player;
            this.tier = tier;
        }
    }

    public static ChunkSession createSession(Object mod, EntityPlayerMP player, WandTier tier) {
        if (player == null || player.worldObj == null) return null;
        ChunkSession session = new ChunkSession(player, tier);
        int cx = MathHelper.floor_double(player.posX / 16.0);
        int cz = MathHelper.floor_double(player.posZ / 16.0);
        int radius = tier.radius;

        List<ChunkCoordIntPair> toForce = new ArrayList<ChunkCoordIntPair>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                toForce.add(new ChunkCoordIntPair(cx + dx, cz + dz));
            }
        }

        int depth = ForgeChunkManager.getMaxChunkDepthFor("freecam_interaction");
        if (depth <= 0) depth = 25;
        int requiredTickets = (toForce.size() + depth - 1) / depth;

        int available = ForgeChunkManager.ticketCountAvailableFor(mod, player.worldObj);
        if (available < requiredTickets) {
            ModLog.info("Insufficient chunk tickets for player=" + player.getCommandSenderName()
                    + "; need=" + requiredTickets + "; available=" + available);
            return null;
        }

        int chunkIndex = 0;
        for (int i = 0; i < requiredTickets; i++) {
            Ticket ticket = ForgeChunkManager.requestTicket(mod, player.worldObj, Type.NORMAL);
            if (ticket == null) {
                releaseSession(session);
                ModLog.info("ForgeChunkManager.requestTicket returned null for " + player.getCommandSenderName());
                return null;
            }
            session.tickets.add(ticket);
            int maxForThisTicket = Math.min(depth, ticket.getMaxChunkListDepth());
            for (int c = 0; c < maxForThisTicket && chunkIndex < toForce.size(); c++) {
                ChunkCoordIntPair coord = toForce.get(chunkIndex++);
                ForgeChunkManager.forceChunk(ticket, coord);
                session.forcedChunks.add(coord);
            }
        }

        subscribeChunks(session, toForce);

        ModLog.info("Chunk session established; player=" + player.getCommandSenderName()
                + "; tier=" + tier.name() + "; tickets=" + session.tickets.size()
                + "; forcedChunks=" + session.forcedChunks.size()
                + "; extraWatched=" + session.extraWatchedChunks.size());
        return session;
    }

    private static void subscribeChunks(ChunkSession session, List<ChunkCoordIntPair> chunks) {
        if (session.player == null || !(session.player.worldObj instanceof WorldServer)) return;
        if (getWatcherMethod == null || addPlayerMethod == null) return;
        WorldServer ws = (WorldServer) session.player.worldObj;
        PlayerManager pm = ws.getPlayerManager();
        for (ChunkCoordIntPair coord : chunks) {
            if (pm.isPlayerWatchingChunk(session.player, coord.chunkXPos, coord.chunkZPos)) continue;
            try {
                Object watcher = getWatcherMethod.invoke(pm, coord.chunkXPos, coord.chunkZPos, true);
                if (watcher != null) {
                    addPlayerMethod.invoke(watcher, session.player);
                    session.extraWatchedChunks.add(coord);
                }
            } catch (Throwable t) {
                // Ignore per-chunk watch failure
            }
        }
    }

    public static void releaseSession(ChunkSession session) {
        if (session == null) return;
        if (session.player != null && session.player.worldObj instanceof WorldServer
                && getWatcherMethod != null && removePlayerMethod != null) {
            WorldServer ws = (WorldServer) session.player.worldObj;
            PlayerManager pm = ws.getPlayerManager();
            for (ChunkCoordIntPair coord : session.extraWatchedChunks) {
                try {
                    Object watcher = getWatcherMethod.invoke(pm, coord.chunkXPos, coord.chunkZPos, false);
                    if (watcher != null) {
                        removePlayerMethod.invoke(watcher, session.player);
                    }
                } catch (Throwable ignored) {}
            }
        }
        session.extraWatchedChunks.clear();

        for (Ticket ticket : session.tickets) {
            try {
                ForgeChunkManager.releaseTicket(ticket);
            } catch (Throwable ignored) {}
        }
        session.tickets.clear();
        session.forcedChunks.clear();
    }
}
