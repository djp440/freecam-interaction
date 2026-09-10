package local.freecaminteraction.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** 定点修改声音加载，并把原版、第三方容器及村民交易AI的玩家距离检查路由到模式范围。 */
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

    public static volatile boolean entityRendererPatched = false;

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        if (transformedName.equals("net.minecraft.client.renderer.EntityRenderer")) {
            return patchEntityRenderer(bytes);
        }
        if (transformedName.equals("net.minecraft.client.Minecraft")) {
            return patchMinecraftTick(bytes);
        }
        if (transformedName.equals("net.minecraft.entity.ai.EntityAITradePlayer")) {
            return patchTradeAi(bytes);
        }
        if (transformedName.equals("net.minecraft.item.ItemBucket")) {
            return patchBucketTarget(bytes);
        }
        if (transformedName.equals("net.minecraft.server.management.ItemInWorldManager")) {
            return patchBlockInteractions(bytes);
        }
        if (transformedName.equals("appeng.util.Platform")) {
            return patchAe2Platform(bytes);
        }
        if (transformedName.equals("appeng.parts.PartPlacement")) {
            return patchAe2PartPlacement(bytes);
        }
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

    private byte[] patchBlockInteractions(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (MethodNode method : new java.util.ArrayList<MethodNode>(node.methods)) {
            boolean harvest = (method.name.equals("tryHarvestBlock") || method.name.equals("func_73084_b"))
                    && method.desc.equals("(III)Z");
            boolean activate = (method.name.equals("activateBlockOrUseItem") || method.name.equals("func_73078_a"))
                    && method.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIIIFFF)Z");
            if (!harvest && !activate) continue;
            String originalName = "freecam$original$" + method.name;
            String wrapperName = method.name;
            method.name = originalName;
            MethodNode wrapper = new MethodNode(method.access, wrapperName, method.desc, method.signature,
                    method.exceptions == null ? null : method.exceptions.toArray(new String[method.exceptions.size()]));
            Type[] args = Type.getArgumentTypes(method.desc);
            int tokenLocal = 1;
            for (Type arg : args) tokenLocal += arg.getSize();
            int resultLocal = tokenLocal + 1;
            int errorLocal = resultLocal + 1;
            LabelNode start = new LabelNode(), finish = new LabelNode(), handler = new LabelNode();
            InsnList code = wrapper.instructions;
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            if (activate) code.add(new VarInsnNode(Opcodes.ALOAD, 1)); else code.add(new InsnNode(Opcodes.ACONST_NULL));
            if (activate) code.add(new VarInsnNode(Opcodes.ALOAD, 2)); else code.add(new InsnNode(Opcodes.ACONST_NULL));
            int xyz = activate ? 4 : 1;
            code.add(new VarInsnNode(Opcodes.ILOAD, xyz));
            code.add(new VarInsnNode(Opcodes.ILOAD, xyz + 1));
            code.add(new VarInsnNode(Opcodes.ILOAD, xyz + 2));
            code.add(new LdcInsnNode(harvest ? "break" : "use_block"));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "local/freecaminteraction/FreecamDropCollector", "beginBlock",
                    "(Lnet/minecraft/server/management/ItemInWorldManager;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;IIILjava/lang/String;)Ljava/lang/Object;", false));
            code.add(new VarInsnNode(Opcodes.ASTORE, tokenLocal));
            code.add(start);
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            int local = 1;
            for (Type arg : args) { code.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), local)); local += arg.getSize(); }
            code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, originalName, method.desc, false));
            code.add(new VarInsnNode(Opcodes.ISTORE, resultLocal));
            code.add(new VarInsnNode(Opcodes.ALOAD, tokenLocal));
            code.add(new InsnNode(Opcodes.ICONST_1));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "local/freecaminteraction/FreecamDropCollector", "end", "(Ljava/lang/Object;Z)V", false));
            code.add(new VarInsnNode(Opcodes.ILOAD, resultLocal));
            code.add(new InsnNode(Opcodes.IRETURN));
            code.add(finish);
            code.add(handler);
            code.add(new VarInsnNode(Opcodes.ASTORE, errorLocal));
            code.add(new VarInsnNode(Opcodes.ALOAD, tokenLocal));
            code.add(new InsnNode(Opcodes.ICONST_0));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "local/freecaminteraction/FreecamDropCollector", "end", "(Ljava/lang/Object;Z)V", false));
            code.add(new VarInsnNode(Opcodes.ALOAD, errorLocal));
            code.add(new InsnNode(Opcodes.ATHROW));
            wrapper.tryCatchBlocks.add(new TryCatchBlockNode(start, finish, handler, null));
            node.methods.add(wrapper);
            changed++;
        }
        if (changed != 2) throw new IllegalStateException("Freecam block interaction patch expected 2 methods, got " + changed);
        System.out.println("[Freecam] ItemInWorldManager drop contexts patched");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private byte[] patchBucketTarget(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("onItemRightClick") && !method.name.equals("func_77659_a")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if ((call.name.equals("getMovingObjectPositionFromPlayer") || call.name.equals("func_77621_a"))
                        && call.desc.equals("(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Z)Lnet/minecraft/util/MovingObjectPosition;")) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "local/freecaminteraction/FreecamActions", "bucketHit",
                            "(Lnet/minecraft/util/MovingObjectPosition;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/util/MovingObjectPosition;", false));
                    method.instructions.insert(call, hook);
                    changed++;
                }
            }
        }
        if (changed != 1) throw new IllegalStateException("Freecam ItemBucket target patch expected 1 site, got " + changed);
        System.out.println("[Freecam] ItemBucket target patched");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private byte[] patchTradeAi(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("shouldExecute") && !method.name.equals("func_75250_a")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if ((call.name.equals("getDistanceSqToEntity") || call.name.equals("func_70068_e"))
                            && call.desc.equals("(Lnet/minecraft/entity/Entity;)D")) {
                        call.setOpcode(Opcodes.INVOKESTATIC);
                        call.owner = "local/freecaminteraction/FreecamInteraction";
                        call.name = "distanceToEntity";
                        call.desc = "(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;)D";
                        call.itf = false;
                        changed++;
                    }
                }
            }
        }
        if (changed != 1) throw new IllegalStateException("Freecam Trade AI patch expected 1 site, got " + changed);
        System.out.println("[Freecam] EntityAITradePlayer distance check patched");
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

    private byte[] patchEntityRenderer(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("orientCamera") && !method.name.equals("func_78467_g")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if ((call.name.equals("rayTraceBlocks") || call.name.equals("func_72933_a"))
                        && (call.owner.equals("net/minecraft/client/multiplayer/WorldClient") || call.owner.equals("bjf")
                        || call.owner.equals("net/minecraft/world/World") || call.owner.equals("ahb"))
                        && call.desc.equals("(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/MovingObjectPosition;")) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = "local/freecaminteraction/client/FreecamClient";
                    call.name = "cameraRayTrace";
                    call.desc = "(Lnet/minecraft/world/World;Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/MovingObjectPosition;";
                    call.itf = false;
                    changed++;
                }
            }
        }
        if (changed != 1) throw new IllegalStateException("Freecam EntityRenderer rayTraceBlocks patch expected 1 site, got " + changed);
        entityRendererPatched = true;
        System.out.println("[Freecam] EntityRenderer orientCamera rayTrace patched");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private byte[] patchAe2Platform(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("getPlayerRay")) continue;
            if (!method.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;F)Lappeng/util/LookDirection;")) continue;
            LabelNode skip = new LabelNode();
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new VarInsnNode(Opcodes.FLOAD, 1));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "local/freecaminteraction/FreecamInteraction", "customPlayerRay",
                    "(Lnet/minecraft/entity/player/EntityPlayer;F)Ljava/lang/Object;", false));
            hook.add(new InsnNode(Opcodes.DUP));
            hook.add(new JumpInsnNode(Opcodes.IFNULL, skip));
            hook.add(new TypeInsnNode(Opcodes.CHECKCAST, "appeng/util/LookDirection"));
            hook.add(new InsnNode(Opcodes.ARETURN));
            hook.add(skip);
            hook.add(new InsnNode(Opcodes.POP));
            method.instructions.insert(hook);
            changed++;
        }
        if (changed != 1) throw new IllegalStateException("Freecam AE2 Platform patch expected 1 site, got " + changed);
        System.out.println("[Freecam] AE2 Platform getPlayerRay patched");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private byte[] patchAe2PartPlacement(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (MethodNode method : new java.util.ArrayList<MethodNode>(node.methods)) {
            if (!method.name.equals("place")) continue;
            if (!method.desc.equals("(Lnet/minecraft/item/ItemStack;IIIILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lappeng/parts/PartPlacement$PlaceType;I)Z")) continue;
            String originalName = "freecam$original$" + method.name;
            String wrapperName = method.name;
            method.name = originalName;
            MethodNode wrapper = new MethodNode(method.access, wrapperName, method.desc, method.signature,
                    method.exceptions == null ? null : method.exceptions.toArray(new String[method.exceptions.size()]));
            Type[] args = Type.getArgumentTypes(method.desc);
            int tokenLocal = 0;
            for (Type arg : args) tokenLocal += arg.getSize();
            int resultLocal = tokenLocal + 1;
            int errorLocal = resultLocal + 1;

            LabelNode proceed = new LabelNode();
            LabelNode start = new LabelNode(), finish = new LabelNode(), handler = new LabelNode();
            InsnList code = wrapper.instructions;

            code.add(new VarInsnNode(Opcodes.ALOAD, 5));
            code.add(new VarInsnNode(Opcodes.ALOAD, 6));
            code.add(new VarInsnNode(Opcodes.ILOAD, 1));
            code.add(new VarInsnNode(Opcodes.ILOAD, 2));
            code.add(new VarInsnNode(Opcodes.ILOAD, 3));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "local/freecaminteraction/FreecamInteraction", "beginAe2Placement",
                    "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;", false));
            code.add(new VarInsnNode(Opcodes.ASTORE, tokenLocal));

            code.add(new VarInsnNode(Opcodes.ALOAD, tokenLocal));
            code.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;"));
            code.add(new JumpInsnNode(Opcodes.IF_ACMPNE, proceed));
            code.add(new InsnNode(Opcodes.ICONST_0));
            code.add(new InsnNode(Opcodes.IRETURN));

            code.add(proceed);
            code.add(start);
            int local = 0;
            for (Type arg : args) { code.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), local)); local += arg.getSize(); }
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, originalName, method.desc, false));
            code.add(new VarInsnNode(Opcodes.ISTORE, resultLocal));
            code.add(new VarInsnNode(Opcodes.ALOAD, tokenLocal));
            code.add(new VarInsnNode(Opcodes.ALOAD, 5));
            code.add(new VarInsnNode(Opcodes.ILOAD, resultLocal));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "local/freecaminteraction/FreecamInteraction", "endAe2Placement",
                    "(Ljava/lang/Object;Lnet/minecraft/entity/player/EntityPlayer;Z)V", false));
            code.add(new VarInsnNode(Opcodes.ILOAD, resultLocal));
            code.add(new InsnNode(Opcodes.IRETURN));
            code.add(finish);

            code.add(handler);
            code.add(new VarInsnNode(Opcodes.ASTORE, errorLocal));
            code.add(new VarInsnNode(Opcodes.ALOAD, tokenLocal));
            code.add(new VarInsnNode(Opcodes.ALOAD, 5));
            code.add(new InsnNode(Opcodes.ICONST_0));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "local/freecaminteraction/FreecamInteraction", "endAe2Placement",
                    "(Ljava/lang/Object;Lnet/minecraft/entity/player/EntityPlayer;Z)V", false));
            code.add(new VarInsnNode(Opcodes.ALOAD, errorLocal));
            code.add(new InsnNode(Opcodes.ATHROW));

            wrapper.tryCatchBlocks.add(new TryCatchBlockNode(start, finish, handler, null));
            node.methods.add(wrapper);
            changed++;
        }
        if (changed != 1) throw new IllegalStateException("Freecam AE2 PartPlacement patch expected 1 site, got " + changed);
        System.out.println("[Freecam] AE2 PartPlacement place patched");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };
        node.accept(writer);
        return writer.toByteArray();
    }

    private byte[] patchMinecraftTick(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods) {
            if ((method.name.equals("func_147115_a") || method.name.equals("sendClickBlockToController"))
                    && method.desc.equals("(Z)V")) {
                InsnList prefix = new InsnList();
                LabelNode proceed = new LabelNode();
                prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "local/freecaminteraction/client/FreecamClient", "isFreecamActive", "()Z", false));
                prefix.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
                prefix.add(new InsnNode(Opcodes.RETURN));
                prefix.add(proceed);
                method.instructions.insert(prefix);
                changed++;
            }
        }
        if (changed != 1) throw new IllegalStateException("Freecam Minecraft tick patch expected 1 site, got " + changed);
        System.out.println("[Freecam] Minecraft sendClickBlockToController patched");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
