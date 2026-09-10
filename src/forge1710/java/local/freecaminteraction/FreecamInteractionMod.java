package local.freecaminteraction;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "freecam_interaction", name = "自由视角交互", version = "0.1.0-forge1710-experiment",
        acceptedMinecraftVersions = "[1.7.10]")
public final class FreecamInteractionMod {
    @Mod.Instance("freecam_interaction")
    public static FreecamInteractionMod instance;

    @Mod.EventHandler
    public void initialize(FMLPreInitializationEvent event) {
        instance = this;
        ModLog.initialize();
        ModLog.info("Forge 1.7.10 experiment; side=" + event.getSide()
                + "; java=" + System.getProperty("java.version"));
        FreecamWandRegistry.initialize();
        FreecamChunkLoader.initialize(this);
        FreecamInteraction.initialize();
        if (event.getSide().isClient()) local.freecaminteraction.client.FreecamClient.initialize();
    }
}
