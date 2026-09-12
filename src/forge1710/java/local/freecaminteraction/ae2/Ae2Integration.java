package local.freecaminteraction.ae2;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import local.freecaminteraction.FreecamWandRegistry;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.item.ItemAe2TransferCore;
import local.freecaminteraction.item.ItemFreecamWand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import java.util.List;

/** 不含 AE2 类符号的可选依赖边界。 */
public final class Ae2Integration {
    private static final String RUNTIME = "local.freecaminteraction.ae2.rv3.Ae2Runtime";
    private static boolean loaded;

    private Ae2Integration() {}

    public static void initialize(Object mod) {
        loaded = Loader.isModLoaded("appliedenergistics2");
        if (!loaded) {
            ModLog.info("AE2 not loaded; transfer integration disabled");
            return;
        }
        try {
            FreecamWandRegistry.ae2TransferCore = new ItemAe2TransferCore();
            FreecamWandRegistry.ae2Transmitter = new BlockAe2Transmitter();
            Class.forName(RUNTIME).getMethod("initialize", Object.class).invoke(null, mod);
            ModLog.info("AE2 rv3 transfer integration enabled");
        } catch (Throwable error) {
            loaded = false;
            FreecamWandRegistry.ae2TransferCore = null;
            FreecamWandRegistry.ae2Transmitter = null;
            ModLog.info("AE2 transfer integration failed to initialize: " + error);
        }
    }

    public static boolean isLoaded() { return loaded; }

    public static TileEntity newTransmitterTile() {
        if (!loaded) return null;
        try { return (TileEntity) Class.forName(RUNTIME).getMethod("newTransmitterTile").invoke(null); }
        catch (Throwable error) { ModLog.info("Failed to create AE2 transmitter tile: " + error); return null; }
    }

    /** 服务端消费传输器绑定；客户端必须继续发送原版 C08 交互包。 */
    public static boolean tryBind(EntityPlayer player, ItemStack wand, World world, int x, int y, int z) {
        if (!loaded || world == null || world.getBlock(x, y, z) != FreecamWandRegistry.ae2Transmitter) return false;
        if (!world.isRemote) invoke("bind", new Class<?>[] { EntityPlayerMP.class, ItemStack.class, World.class, int.class, int.class, int.class },
                player, wand, world, x, y, z);
        return !world.isRemote;
    }

    public static boolean hasCore(ItemStack wand) {
        if (wand == null || FreecamWandRegistry.ae2TransferCore == null) return false;
        for (ItemStack core : ItemFreecamWand.loadUpgrades(wand)) if (core != null && core.getItem() == FreecamWandRegistry.ae2TransferCore) return true;
        return false;
    }

    public static boolean hasBoundCoreWand(EntityPlayer player) {
        if (!loaded || player == null || player.inventory == null) return false;
        for (int i = 0; i < Math.min(36, player.inventory.mainInventory.length); i++) {
            ItemStack wand = player.inventory.mainInventory[i];
            if (hasCore(wand) && wand.hasTagCompound() && wand.getTagCompound().hasKey("Ae2Transfer")) return true;
        }
        return false;
    }

    public static String selectedSource(EntityPlayerMP player) {
        Object value = invoke("selectedSource", new Class<?>[] { EntityPlayerMP.class }, player);
        return value instanceof String ? (String) value : null;
    }

    public static void requestTerminal(int page) {
        if (loaded) invoke("requestTerminal", new Class<?>[] { int.class }, page);
    }

    public static void selectTarget(String id) {
        if (loaded) invoke("selectTarget", new Class<?>[] { String.class }, id);
    }

    public static Object serverGui(int id, EntityPlayer player) {
        return invoke("serverGui", new Class<?>[] { int.class, EntityPlayer.class }, id, player);
    }

    public static Object clientGui(int id, EntityPlayer player) {
        return invoke("clientGui", new Class<?>[] { int.class, EntityPlayer.class }, id, player);
    }

    public static Object reserveMe(EntityPlayerMP player, List requirements, int[] deficits, String source) {
        return invoke("reserveMe", new Class<?>[] { EntityPlayerMP.class, List.class, int[].class, String.class }, player, requirements, deficits, source);
    }

    public static void commitMe(Object reservation) { invoke("commitMe", new Class<?>[] { Object.class }, reservation); }
    public static void rollbackMe(EntityPlayerMP player, Object reservation) { invoke("rollbackMe", new Class<?>[] { EntityPlayerMP.class, Object.class }, player, reservation); }

    private static Object invoke(String name, Class<?>[] types, Object... args) {
        if (!loaded) return null;
        try { return Class.forName(RUNTIME).getMethod(name, types).invoke(null, args); }
        catch (Throwable error) { ModLog.error("AE2 operation failed: " + name, error); return null; }
    }
}
