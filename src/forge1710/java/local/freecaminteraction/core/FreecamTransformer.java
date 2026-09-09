package local.freecaminteraction.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** 定点修改声音加载，并把原版及第三方容器的玩家距离检查路由到模式范围。 */
public final class FreecamTransformer implements IClassTransformer {
    private static final Set<String> DISTANCE_CONTAINERS = new HashSet<String>(Arrays.asList(
            "net.minecraft.tileentity.TileEntityHopper",
            "net.minecraft.tileentity.TileEntityFurnace",
            "net.minecraft.tileentity.TileEntityEnderChest",
            "net.minecraft.tileentity.TileEntityDispenser",
            "net.minecraft.tileentity.TileEntityChest",
            "net.minecraft.tileentity.TileEntityBrewingStand",
            "net.minecraft.tileentity.TileEntityBeacon",
            "net.minecraft.inventory.ContainerEnchantment",
            "net.minecraft.inventory.ContainerRepair",
            "net.minecraft.inventory.ContainerWorkbench"));

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        boolean vanillaContainer = DISTANCE_CONTAINERS.contains(transformedName);
        if (vanillaContainer || hasContainerMethod(bytes)) return patchContainerDistance(transformedName, bytes, vanillaContainer);
        if (!transformedName.equals("net.minecraft.client.audio.SoundManager")) return bytes;
        if (isLwjgl3ify()) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("loadSoundSystem") && !method.name.equals("func_148608_i")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.owner.equals("java/lang/Thread") && call.name.equals("start") && call.desc.equals("()V")) {
                    // 在原有 synchronized 方法内完成加载，关闭 loaded 尚未置位时的重入窗口。
                    call.name = "run";
                    changed++;
                }
            }
        }
        if (changed != 1) throw new IllegalStateException("Freecam SoundManager patch expected 1 site, got " + changed);
        System.out.println("[Freecam] SoundManager initialization serialized");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isLwjgl3ify() {
        if (Boolean.getBoolean("freecam.lwjgl3ify") || Boolean.getBoolean("godview.lwjgl3ify")) return true;
        ClassLoader cl = FreecamTransformer.class.getClassLoader();
        try {
            Class<?> v = Class.forName("org.lwjgl.Version", false, cl);
            String ver = (String) v.getMethod("getVersion").invoke(null);
            if (ver != null && ver.startsWith("3")) return true;
        } catch (Throwable ignored) {}
        try {
            Class.forName("me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod", false, cl);
            return true;
        } catch (Throwable ignored) {}
        try {
            String prop = System.getProperty("org.lwjgl.version");
            if (prop != null && prop.startsWith("3")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean hasContainerMethod(byte[] bytes) {
        return contains(bytes, "isUseableByPlayer") || contains(bytes, "canInteractWith")
                || contains(bytes, "func_70300_a") || contains(bytes, "func_75145_c") || contains(bytes, "func_145971_a");
    }

    private static boolean contains(byte[] bytes, String value) {
        byte[] needle;
        try { needle = value.getBytes("UTF-8"); } catch (java.io.UnsupportedEncodingException impossible) { return false; }
        outer: for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (bytes[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    private byte[] patchContainerDistance(String className, byte[] bytes, boolean required) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("isUseableByPlayer") && !method.name.equals("func_70300_a")
                    && !method.name.equals("canInteractWith") && !method.name.equals("func_75145_c")
                    && !method.name.equals("func_145971_a")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ((call.owner.equals("net/minecraft/entity/Entity") || call.owner.equals("net/minecraft/entity/player/EntityPlayer"))
                        && (call.name.equals("getDistanceSq") || call.name.equals("func_70092_e"))
                        && call.desc.equals("(DDD)D")) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = "local/freecaminteraction/FreecamInteraction";
                    call.name = "distance";
                    call.desc = "(Lnet/minecraft/entity/player/EntityPlayer;DDD)D";
                    call.itf = false;
                    changed++;
                }
            }
        }
        if (required && changed != 1) throw new IllegalStateException("Freecam container distance patch expected 1 site in " + className + ", got " + changed);
        if (changed == 0) return bytes;
        System.out.println("[Freecam] Container distance patched: " + className);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
