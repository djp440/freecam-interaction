package local.freecaminteraction;

import net.neoforged.fml.common.Mod;

@Mod(FreecamInteractionMod.MOD_ID)
public final class FreecamInteractionMod {
    public static final String MOD_ID = "freecam_interaction";

    public FreecamInteractionMod() {
        ModLog.initialize();
        ModLog.LOGGER.info("Freecam Interaction initialized; modId={}; Minecraft=1.21.1", MOD_ID);
    }
}
