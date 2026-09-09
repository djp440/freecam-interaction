package local.freecaminteraction;

import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = FreecamInteractionMod.MOD_ID)
public final class FreecamInteraction {
    private static final Map<Player, ResourceKey<Level>> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());

    private FreecamInteraction() {}

    public static boolean active(Player player) {
        return player != null && player.isAlive() && !player.isSleeping()
                && player.level().dimension().equals(ACTIVE.get(player));
    }

    public static void setActive(Player player, boolean enabled) {
        if (enabled && player.isAlive() && !player.isSleeping()) {
            ACTIVE.put(player, player.level().dimension());
        } else {
            ACTIVE.remove(player);
        }
        ModLog.LOGGER.info("Freecam interaction mode={}; player={}; side={}", active(player),
                player.getUUID(), player.level().isClientSide ? "client" : "server");
    }

    public static boolean allowed(Player player, BlockPos position) {
        return FreecamRange.contains(player.getX(), player.getY(), player.getZ(),
                position.getX(), position.getY(), position.getZ())
                && !player.level().isOutsideBuildHeight(position)
                && player.level().getWorldBorder().isWithinBounds(position)
                && player.level().hasChunkAt(position);
    }

    public record Mode(boolean enabled) implements CustomPacketPayload {
        public static final Type<Mode> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FreecamInteractionMod.MOD_ID, "mode"));
        public static final StreamCodec<ByteBuf, Mode> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Mode::enabled, Mode::new);

        @Override
        public Type<Mode> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = FreecamInteractionMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").optional().playToServer(Mode.TYPE, Mode.CODEC, (message, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    setActive(player, message.enabled());
                    if (!message.enabled() && player.containerMenu != player.inventoryMenu) {
                        player.closeContainer();
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (ACTIVE.containsKey(player) && !active(player)) {
            setActive(player, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        setActive(event.getEntity(), false);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        setActive(event.getEntity(), false);
    }

    @SubscribeEvent
    public static void onPlacement(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && active(player)) {
            boolean valid = allowed(player, event.getPos());
            if (event instanceof BlockEvent.EntityMultiPlaceEvent multiple) {
                valid &= multiple.getReplacedBlockSnapshots().stream().allMatch(snapshot -> allowed(player, snapshot.getPos()));
            }
            if (!valid) {
                event.setCanceled(true);
                ModLog.LOGGER.debug("Freecam placement rejected; player={}; position={}", player.getUUID(), event.getPos());
            }
        }
    }
}
