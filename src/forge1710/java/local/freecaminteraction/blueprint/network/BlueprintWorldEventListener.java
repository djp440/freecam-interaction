package local.freecaminteraction.blueprint.network;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

public final class BlueprintWorldEventListener {
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new BlueprintWorldEventListener());
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.world.isRemote && event.world.provider.dimensionId == 0 && event.world instanceof WorldServer) {
            WorldServer ws = (WorldServer) event.world;
            if (ws.getSaveHandler() != null && ws.getSaveHandler().getWorldDirectory() != null) {
                BlueprintStorageManager.setSaveDirectory(ws.getSaveHandler().getWorldDirectory());
            }
        }
    }
}
