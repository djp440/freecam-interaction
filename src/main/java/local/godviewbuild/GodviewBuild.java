package local.godviewbuild;

import net.neoforged.fml.common.Mod;

@Mod(GodviewBuild.MOD_ID)
public final class GodviewBuild {
    public static final String MOD_ID = "godview_build";

    public GodviewBuild() {
        ModLog.initialize();
        ModLog.LOGGER.info("Godview Build initialized; modId={}; Minecraft=1.21.1", MOD_ID);
    }
}
