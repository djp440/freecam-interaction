import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import local.freecaminteraction.FreecamActions;
import local.freecaminteraction.FreecamTarget;
import local.freecaminteraction.FreecamRange;
import net.minecraft.util.Vec3;

public class LegacyActionCheck {
    private static final class ByteLoader extends ClassLoader {
        ByteLoader() { super(LegacyActionCheck.class.getClassLoader()); }
        Class define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
    // 仅测试用：跳过世界/实体构造，不启动游戏、不读取存档；射线算法仍调用真实 World 实现。
    public static class RayWorld extends net.minecraft.world.World {
        java.util.List entities;
        net.minecraft.block.Block solid, air;
        int wall;
        java.util.Map customBlocks = new java.util.HashMap();
        RayWorld() { super(null, "check", (net.minecraft.world.WorldProvider) null, null, null); }
        protected net.minecraft.world.chunk.IChunkProvider createChunkProvider() { return null; }
        protected int func_152379_p() { return 0; }
        public net.minecraft.entity.Entity getEntityByID(int id) { return null; }
        public net.minecraft.block.Block getBlock(int x, int y, int z) {
            if (customBlocks != null) {
                Object custom = customBlocks.get(x + "," + y + "," + z);
                if (custom != null) return (net.minecraft.block.Block) custom;
            }
            return x == wall ? solid : air;
        }
        public int getBlockMetadata(int x, int y, int z) { return 0; }
        public java.util.List getEntitiesWithinAABBExcludingEntity(net.minecraft.entity.Entity e, net.minecraft.util.AxisAlignedBB b) { return entities; }
        public boolean blockExists(int x, int y, int z) { return true; }
        public int getHeight() { return 256; }
    }
    public static class Target extends net.minecraft.entity.passive.EntityCow {
        Target() { super(null); }
        public boolean isEntityAlive() { return true; }
        public boolean canBeCollidedWith() { return true; }
    }
    public static class RayBlock extends net.minecraft.block.Block {
        RayBlock(net.minecraft.block.material.Material material) { super(material); }
        public boolean canCollideCheck(int metadata, boolean liquids) { return getMaterial().isSolid(); }
        public void addCollisionBoxesToList(net.minecraft.world.World w, int x, int y, int z, net.minecraft.util.AxisAlignedBB mask, java.util.List list, net.minecraft.entity.Entity e) {
            if (getMaterial().isSolid()) {
                net.minecraft.util.AxisAlignedBB box = net.minecraft.util.AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
                if (mask.intersectsWith(box)) list.add(box);
            }
        }
        public net.minecraft.util.AxisAlignedBB getCollisionBoundingBoxFromPool(net.minecraft.world.World w, int x, int y, int z) {
            return getMaterial().isSolid() ? net.minecraft.util.AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1) : null;
        }
    }
    public static class DummyContainer extends net.minecraft.inventory.Container {
        public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p) { return true; }
        public void detectAndSendChanges() {}
    }
    private static void rayChecks() throws Exception {
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        RayWorld world = (RayWorld) unsafe.allocateInstance(RayWorld.class);
        world.solid = new RayBlock(net.minecraft.block.material.Material.rock);
        world.air = new RayBlock(net.minecraft.block.material.Material.air);
        net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) unsafe.allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
        player.worldObj = world;
        java.lang.reflect.Field bounds = net.minecraft.entity.Entity.class.getField("boundingBox");
        unsafe.putObject(player, unsafe.objectFieldOffset(bounds), net.minecraft.util.AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 2, 1));
        Target entity = (Target) unsafe.allocateInstance(Target.class);
        entity.worldObj = world;
        unsafe.putObject(entity, unsafe.objectFieldOffset(bounds), net.minecraft.util.AxisAlignedBB.getBoundingBox(5, 0, 0, 6, 2, 1));
        world.entities = java.util.Collections.singletonList(entity);
        world.wall = 7;
        Vec3 start = Vec3.createVectorHelper(0, 1, 0.5), end = Vec3.createVectorHelper(10, 1, 0.5);
        net.minecraft.util.MovingObjectPosition hit = FreecamTarget.pick(player, start, end);
        assert start.xCoord == 0 : "Block tracing mutated the camera ray origin";
        assert hit != null && hit.entityHit == entity : "Foreground entity lost to background block";
        world.wall = 3;
        hit = FreecamTarget.pick(player, start, end);
        assert hit != null && hit.entityHit == null && hit.blockX == 3 : "Entity selected through wall";
        world.wall = 7;
        entity.boundingBox.offset(4, 0, 0);
        assert FreecamTarget.pick(player, start, end).entityHit == null : "Entity behind block selected";
        System.out.println("Ray checks passed: unchanged origin, distant entity, foreground wall.");
    }
    private static void bucketPatchCheck() throws Exception {
        String name = "net.minecraft.item.ItemBucket";
        java.io.InputStream stream = LegacyActionCheck.class.getResourceAsStream("/" + name.replace('.', '/') + ".class");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            for (int count; (count = stream.read(buffer)) != -1;) bytes.write(buffer, 0, count);
        } finally { stream.close(); }
        byte[] patched = new local.freecaminteraction.core.FreecamTransformer().transform(name, name, bytes.toByteArray());
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(patched).accept(node, 0);
        int calls = 0;
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode
                        && ((org.objectweb.asm.tree.MethodInsnNode) instruction).name.equals("bucketHit")) calls++;
            }
        }
        assert calls == 1 : "Bucket target hook missing";
        assert FreecamActions.bucketHit(null, null) == null;
        System.out.println("Bucket patch check passed (real Minecraft bytecode)." );
    }

    private static void tradePatchCheck() throws Exception {
        String name = "net.minecraft.entity.ai.EntityAITradePlayer";
        java.io.InputStream stream = LegacyActionCheck.class.getResourceAsStream("/" + name.replace('.', '/') + ".class");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            for (int count; (count = stream.read(buffer)) != -1;) bytes.write(buffer, 0, count);
        } finally { stream.close(); }
        byte[] patched = new local.freecaminteraction.core.FreecamTransformer().transform(name, name, bytes.toByteArray());
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(patched).accept(node, 0);
        int calls = 0;
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode) {
                    org.objectweb.asm.tree.MethodInsnNode call = (org.objectweb.asm.tree.MethodInsnNode) instruction;
                    if (call.name.equals("distanceToEntity")) calls++;
                }
            }
        }
        assert calls == 1 : "Trade AI distance hook missing";
        System.out.println("Trade AI patch check passed (real Minecraft bytecode).");
    }

    private static void rendererPatchCheck() throws Exception {
        String name = "net.minecraft.client.renderer.EntityRenderer";
        java.io.InputStream stream = LegacyActionCheck.class.getResourceAsStream("/" + name.replace('.', '/') + ".class");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            for (int count; (count = stream.read(buffer)) != -1;) bytes.write(buffer, 0, count);
        } finally { stream.close(); }
        byte[] patched = new local.freecaminteraction.core.FreecamTransformer().transform(name, name, bytes.toByteArray());
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(patched).accept(node, 0);
        int rayCalls = 0, renderInfoCalls = 0;
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode) {
                    org.objectweb.asm.tree.MethodInsnNode call = (org.objectweb.asm.tree.MethodInsnNode) instruction;
                    if (call.name.equals("cameraRayTrace")) rayCalls++;
                    if (call.owner.equals("local/freecaminteraction/client/FreecamClient")
                            && call.name.equals("updateRenderInfoForCamera")) renderInfoCalls++;
                }
            }
        }
        assert rayCalls == 1 : "EntityRenderer cameraRayTrace hook missing";
        assert renderInfoCalls == 1 : "EntityRenderer particle-facing hook missing";
        System.out.println("EntityRenderer patch check passed (collision ray and particle-facing hooks)." );
    }

    private static void minecraftPatchCheck() throws Exception {
        String name = "net.minecraft.client.Minecraft";
        java.io.InputStream stream = LegacyActionCheck.class.getResourceAsStream("/" + name.replace('.', '/') + ".class");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            for (int count; (count = stream.read(buffer)) != -1;) bytes.write(buffer, 0, count);
        } finally { stream.close(); }
        byte[] patched = new local.freecaminteraction.core.FreecamTransformer().transform(name, name, bytes.toByteArray());
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(patched).accept(node, 0);
        int hooks = 0;
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            if ((method.name.equals("func_147115_a") || method.name.equals("sendClickBlockToController"))
                    && method.desc.equals("(Z)V")) {
                for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode) {
                        org.objectweb.asm.tree.MethodInsnNode call = (org.objectweb.asm.tree.MethodInsnNode) instruction;
                        if (call.owner.equals("local/freecaminteraction/client/FreecamClient")
                                && call.name.equals("isFreecamActive")) hooks++;
                    }
                }
            }
        }
        assert hooks == 1 : "Minecraft sendClickBlockToController isFreecamActive hook missing";
        System.out.println("Minecraft tick patch check passed (real Minecraft bytecode).");
    }

    private static void dropPatchCheck() throws Exception {
        String name = "net.minecraft.server.management.ItemInWorldManager";
        java.io.InputStream stream = LegacyActionCheck.class.getResourceAsStream("/" + name.replace('.', '/') + ".class");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            for (int count; (count = stream.read(buffer)) != -1;) bytes.write(buffer, 0, count);
        } finally { stream.close(); }
        byte[] patched = new local.freecaminteraction.core.FreecamTransformer().transform(name, name, bytes.toByteArray());
        Class verified = new ByteLoader().define(patched);
        assert verified.getDeclaredMethod("tryHarvestBlock", Integer.TYPE, Integer.TYPE, Integer.TYPE) != null;
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(patched).accept(node, 0);
        int begin = 0, end = 0, originals = 0;
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            if (method.name.startsWith("freecam$original$")) originals++;
            for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof org.objectweb.asm.tree.MethodInsnNode)) continue;
                String call = ((org.objectweb.asm.tree.MethodInsnNode) instruction).name;
                if (call.equals("beginBlock")) begin++;
                if (call.equals("end")) end++;
            }
        }
        assert originals == 2 && begin == 2 && end == 4 : "方块交互掉落上下文补丁不完整";
        System.out.println("Drop context patch check passed (harvest/use, success/exception cleanup).");
    }

    private static void dropInventoryCheck() throws Exception {
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) unsafe.allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        java.lang.reflect.Method insert = local.freecaminteraction.FreecamDropCollector.class
                .getDeclaredMethod("insert", net.minecraft.entity.player.EntityPlayerMP.class, net.minecraft.item.ItemStack.class);
        insert.setAccessible(true);

        net.minecraft.item.Item testedItem = new net.minecraft.item.Item().setMaxStackSize(64);
        net.minecraft.item.Item filler = new net.minecraft.item.Item().setMaxStackSize(64);
        net.minecraft.item.ItemStack existing = new net.minecraft.item.ItemStack(testedItem, 60);
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setString("machine", "preserved");
        existing.setTagCompound((net.minecraft.nbt.NBTTagCompound) tag.copy());
        player.inventory.mainInventory[0] = existing;
        net.minecraft.item.ItemStack incoming = new net.minecraft.item.ItemStack(testedItem, 10);
        incoming.setTagCompound((net.minecraft.nbt.NBTTagCompound) tag.copy());
        int moved = ((Integer) insert.invoke(null, player, incoming)).intValue();
        assert moved == 10 && incoming.stackSize == 0 : "moved=" + moved + ", remaining=" + incoming.stackSize
                + ", slot0=" + player.inventory.mainInventory[0].stackSize;
        assert player.inventory.mainInventory[0].stackSize == 64;
        assert player.inventory.mainInventory[1].stackSize == 6;
        assert "preserved".equals(player.inventory.mainInventory[1].getTagCompound().getString("machine"));

        for (int i = 0; i < 36; i++) player.inventory.mainInventory[i] = new net.minecraft.item.ItemStack(filler, 64);
        player.inventory.mainInventory[0] = new net.minecraft.item.ItemStack(testedItem, 63);
        incoming = new net.minecraft.item.ItemStack(testedItem, 5);
        moved = ((Integer) insert.invoke(null, player, incoming)).intValue();
        assert moved == 1 && incoming.stackSize == 4 : "部分容量必须保留落地余量";
        moved = ((Integer) insert.invoke(null, player, incoming)).intValue();
        assert moved == 0 && incoming.stackSize == 4 : "满包不得吞物品";
        System.out.println("Drop inventory checks passed: merge, NBT, partial capacity, full inventory.");
    }

    private static void reproductionAndCollisionCheck() throws Exception {
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        RayWorld world = (RayWorld) unsafe.allocateInstance(RayWorld.class);
        world.solid = new RayBlock(net.minecraft.block.material.Material.rock);
        world.air = new RayBlock(net.minecraft.block.material.Material.air);
        world.customBlocks = new java.util.HashMap();
        world.wall = -9999;

        // 场景：障碍方块在 (0, 65, -2)
        world.customBlocks.put("0,65,-2", world.solid);

        // 1. 复现原版 EntityRenderer.orientCamera 的 8 射线算法：
        // 锚点 1: (-0.12, 65.5, 0.5), yaw=0, pitch=0, d7=13.856406
        double d7_start = 13.856406;
        double d0_1 = -0.12, d1_1 = 65.5, d2_1 = 0.5;
        double d3 = 0, d5 = 0, d4 = d7_start;
        double d7_1 = d7_start;
        for (int k = 0; k < 8; ++k) {
            float f3 = (float)((k & 1) * 2 - 1) * 0.1F;
            float f4 = (float)((k >> 1 & 1) * 2 - 1) * 0.1F;
            float f5 = (float)((k >> 2 & 1) * 2 - 1) * 0.1F;
            net.minecraft.util.MovingObjectPosition hit = world.rayTraceBlocks(
                Vec3.createVectorHelper(d0_1 + f3, d1_1 + f4, d2_1 + f5),
                Vec3.createVectorHelper(d0_1 - d3 + f3 + f5, d1_1 - d5 + f4, d2_1 - d4 + f5));
            if (hit != null) {
                double dist = hit.hitVec.distanceTo(Vec3.createVectorHelper(d0_1, d1_1, d2_1));
                if (dist < d7_1) d7_1 = dist;
            }
        }
        assert Math.abs(d7_1 - d7_start) < 1e-5 : "原起点 (-0.12) 不应命中";

        // 锚点仅移动 0.02 格至 (-0.10, 65.5, 0.5)
        double d0_2 = -0.10, d1_2 = 65.5, d2_2 = 0.5;
        double d7_2 = d7_start;
        for (int k = 0; k < 8; ++k) {
            float f3 = (float)((k & 1) * 2 - 1) * 0.1F;
            float f4 = (float)((k >> 1 & 1) * 2 - 1) * 0.1F;
            float f5 = (float)((k >> 2 & 1) * 2 - 1) * 0.1F;
            net.minecraft.util.MovingObjectPosition hit = world.rayTraceBlocks(
                Vec3.createVectorHelper(d0_2 + f3, d1_2 + f4, d2_2 + f5),
                Vec3.createVectorHelper(d0_2 - d3 + f3 + f5, d1_2 - d5 + f4, d2_2 - d4 + f5));
            if (hit != null) {
                double dist = hit.hitVec.distanceTo(Vec3.createVectorHelper(d0_2, d1_2, d2_2));
                if (dist < d7_2) d7_2 = dist;
            }
        }
        // 确认复现：原版避障命中 (0,65,-2)，导致距离暴跌至约 2.5 格，跳动超 11 格！
        assert d7_2 < 3.0 : "原版避障未能复现跳位回缩";
        assert (d7_1 - d7_2) > 10.0 : "原版跳动距离应大于 10 格";
        System.out.println("Reproduction confirmed: vanilla 8-ray obstruction shrank distance by " + (d7_1 - d7_2));

        // 2. 验证 FreecamCollision（方案 A）：
        // 镜头位于光学中心 Z = 0.5 - 13.856406 = -13.356406
        Vec3 cam1 = local.freecaminteraction.client.FreecamCollision.cameraPos(d0_1, d1_1, d2_1, 0, 0);
        double[] accepted = local.freecaminteraction.client.FreecamCollision.solveTranslation(
            world, cam1.xCoord, cam1.yCoord, cam1.zCoord, 0.02, 0.0, 0.0);
        assert Math.abs(accepted[0] - 0.02) < 1e-9 : "镜头所在空间畅通，位移应完整接受";
        assert Math.abs(accepted[1]) < 1e-9 && Math.abs(accepted[2]) < 1e-9;
        Vec3 cam2 = local.freecaminteraction.client.FreecamCollision.cameraPos(d0_1 + accepted[0], d1_1, d2_1, 0, 0);
        double actualMove = cam1.distanceTo(cam2);
        assert Math.abs(actualMove - 0.02) < 1e-9 : "方案 A 消除跳位，实际位移恰好为 0.02";

        // 3. 贴墙滑动测试：
        // 放置一堵墙在 X = 2 (方块 x=2, y=65, z=0)
        world.customBlocks.put("2,65,0", world.solid);
        // 镜头在 (1.5, 65.5, 0.5)，尝试斜向冲向墙面 (dx = 1.0, dy = 0, dz = 1.0)
        // 镜头半径 0.20，到 X=2.0 表面最大容许移动 2.0 - 0.20 - 1.5 = 0.30
        double[] slide = local.freecaminteraction.client.FreecamCollision.solveTranslation(
            world, 1.5, 65.5, 0.5, 1.0, 0.0, 1.0);
        assert Math.abs(slide[0] - 0.30) < 1e-6 : "X 轴应被截停在贴墙处：预计 0.30，实际 " + slide[0];
        assert Math.abs(slide[2] - 1.0) < 1e-6 : "Z 轴无阻挡，应完整滑动：预计 1.0，实际 " + slide[2];

        // 4. 旋转防穿阻挡测试：
        // 锚点在 (0, 65.5, 0), yaw=0, pitch=0 -> 镜头在 (0, 65.5, -13.856406)
        // 在旋转轨迹上放置方块：当 yaw 转向 20 度时经过的位置
        Vec3 rotBlockPos = local.freecaminteraction.client.FreecamCollision.cameraPos(0, 65.5, 0, 15.0F, 0.0F);
        int bx = net.minecraft.util.MathHelper.floor_double(rotBlockPos.xCoord);
        int by = net.minecraft.util.MathHelper.floor_double(rotBlockPos.yCoord);
        int bz = net.minecraft.util.MathHelper.floor_double(rotBlockPos.zCoord);
        world.customBlocks.put(bx + "," + by + "," + bz, world.solid);
        float[] safeRot = local.freecaminteraction.client.FreecamCollision.solveRotation(
            world, 0, 65.5, 0, 0.0F, 0.0F, 30.0F, 0.0F);
        assert safeRot[0] < 30.0F : "旋转应在障碍前被安全截停";
        assert safeRot[0] >= 0.0F;

        // 5. 液面防下沉测试：
        // 在 y=60 处放置水块
        RayBlock water = new RayBlock(net.minecraft.block.material.Material.water);
        world.customBlocks.put("5,60,5", water);
        // 镜头在 (5.5, 61.5, 5.5)，向下移动 2.0 格
        // 液体表面为 y=61.0，镜头半径 0.20，停止高度为 61.20，最大下移量为 61.20 - 61.50 = -0.30
        double[] liquidDrop = local.freecaminteraction.client.FreecamCollision.solveTranslation(
            world, 5.5, 61.5, 5.5, 0.0, -2.0, 0.0);
        assert Math.abs(liquidDrop[1] - (-0.30)) < 1e-6 : "向下移动应被液面截停：预计 -0.30，实际 " + liquidDrop[1];

        System.out.println("FreecamCollision checks passed: zero-jump reproduction fix, wall sliding, rotation arc stop, liquid surface floor.");
    }

    private static void ae2PatchCheck() throws Exception {
        java.io.File jarFile = new java.io.File("run/mods/appliedenergistics2-rv3-beta-6.jar");
        if (!jarFile.exists()) {
            System.out.println("AE2 jar not present in run/mods, skipping AE2 bytecode patch verification.");
            return;
        }
        java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile);
        try {
            java.util.zip.ZipEntry entry = jar.getEntry("appeng/util/Platform.class");
            assert entry != null : "Platform.class entry missing in AE2 jar";
            java.io.InputStream stream = jar.getInputStream(entry);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try {
                byte[] buf = new byte[4096];
                for (int c; (c = stream.read(buf)) != -1;) bytes.write(buf, 0, c);
            } finally { stream.close(); }
            byte[] patched = new local.freecaminteraction.core.FreecamTransformer().transform("appeng.util.Platform", "appeng.util.Platform", bytes.toByteArray());
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(patched).accept(node, 0);
            int customRayCalls = 0;
            for (org.objectweb.asm.tree.MethodNode method : node.methods) {
                if (method.name.equals("getPlayerRay")) {
                    for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof org.objectweb.asm.tree.MethodInsnNode) {
                            org.objectweb.asm.tree.MethodInsnNode m = (org.objectweb.asm.tree.MethodInsnNode) insn;
                            if (m.name.equals("customPlayerRay")) customRayCalls++;
                        }
                    }
                }
            }
            assert customRayCalls == 1 : "Platform getPlayerRay hook missing";

            entry = jar.getEntry("appeng/parts/PartPlacement.class");
            assert entry != null : "PartPlacement.class entry missing in AE2 jar";
            stream = jar.getInputStream(entry);
            bytes = new java.io.ByteArrayOutputStream();
            try {
                byte[] buf = new byte[4096];
                for (int c; (c = stream.read(buf)) != -1;) bytes.write(buf, 0, c);
            } finally { stream.close(); }
            patched = new local.freecaminteraction.core.FreecamTransformer().transform("appeng.parts.PartPlacement", "appeng.parts.PartPlacement", bytes.toByteArray());
            node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(patched).accept(node, 0);
            int originalPlace = 0, beginAe2 = 0, endAe2 = 0;
            for (org.objectweb.asm.tree.MethodNode method : node.methods) {
                if (method.name.equals("freecam$original$place")) originalPlace++;
                for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof org.objectweb.asm.tree.MethodInsnNode) {
                        org.objectweb.asm.tree.MethodInsnNode m = (org.objectweb.asm.tree.MethodInsnNode) insn;
                        if (m.name.equals("beginAe2Placement")) beginAe2++;
                        if (m.name.equals("endAe2Placement")) endAe2++;
                    }
                }
            }
            assert originalPlace == 1 && beginAe2 == 1 && endAe2 == 2 : "PartPlacement place wrapper incomplete";
            System.out.println("AE2 bytecode patch check passed (Platform.getPlayerRay & PartPlacement.place).");
        } finally {
            jar.close();
        }
    }

    private static void ae2RayAndContextCheck() throws Exception {
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) unsafe.allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
        player.worldObj = (RayWorld) unsafe.allocateInstance(RayWorld.class);
        java.lang.reflect.Field bounds = net.minecraft.entity.Entity.class.getField("boundingBox");
        unsafe.putObject(player, unsafe.objectFieldOffset(bounds), net.minecraft.util.AxisAlignedBB.getBoundingBox(0, 60, 0, 1, 62, 1));
        net.minecraft.entity.DataWatcher dw = new net.minecraft.entity.DataWatcher(player);
        dw.addObject(6, Float.valueOf(20.0F));
        java.lang.reflect.Field dwField = net.minecraft.entity.Entity.class.getDeclaredField("dataWatcher");
        dwField.setAccessible(true);
        dwField.set(player, dw);
        for (java.lang.reflect.Field f : net.minecraft.entity.player.EntityPlayer.class.getDeclaredFields()) {
            if (f.getType().getName().contains("GameProfile")) {
                unsafe.putObject(player, unsafe.objectFieldOffset(f), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Ae2Tester"));
                break;
            }
        }
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        player.inventoryContainer = new DummyContainer();
        local.freecaminteraction.item.ItemFreecamWand wandNorm = new local.freecaminteraction.item.ItemFreecamWand(local.freecaminteraction.WandTier.NORMAL);
        player.inventory.mainInventory[0] = new net.minecraft.item.ItemStack(wandNorm, 1, 100);
        player.posX = 0.5; player.posZ = 0.5;

        assert local.freecaminteraction.FreecamInteraction.customPlayerRay(player, 1.62F) == null;
        assert local.freecaminteraction.FreecamInteraction.beginAe2Placement(player, player.worldObj, 5, 60, 5) == null;

        java.lang.reflect.Field activeField = local.freecaminteraction.FreecamInteraction.class.getDeclaredField("ACTIVE");
        activeField.setAccessible(true);
        java.util.Map activeMap = (java.util.Map) activeField.get(null);
        java.lang.reflect.Constructor stateCtor = Class.forName("local.freecaminteraction.FreecamInteraction$State")
                .getDeclaredConstructors()[0];
        stateCtor.setAccessible(true);
        Object state = stateCtor.newInstance(player.dimension, 5.0D, local.freecaminteraction.WandTier.NORMAL, 0, null);
        activeMap.put(player, state);

        try {
            Vec3 start = Vec3.createVectorHelper(0.5, 65.0, 0.5);
            Vec3 end = Vec3.createVectorHelper(5.5, 60.5, 5.5);
            Vec3 point = Vec3.createVectorHelper(5.0, 60.5, 5.0);
            local.freecaminteraction.FreecamInteraction.recordRay(player, start, end, point);

            assert local.freecaminteraction.FreecamInteraction.beginAe2Placement(player, player.worldObj, 500, 60, 500) == Boolean.FALSE;

            Object token = local.freecaminteraction.FreecamInteraction.beginAe2Placement(player, player.worldObj, 5, 60, 5);
            assert token != null && token != Boolean.FALSE : "有效范围应开启掉落上下文";

            local.freecaminteraction.FreecamInteraction.endAe2Placement(token, player, true);

            System.out.println("AE2 ray and placement context checks passed: active guard, out-of-bounds rejection, valid context & cleanup.");
        } finally {
            activeMap.remove(player);
            local.freecaminteraction.FreecamInteraction.consumeRay(player);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "no-gt".equals(args[0])) {
            assert !local.freecaminteraction.blueprint.BlueprintGregTechSupport.isGregTechTileEntity(null);
            assert local.freecaminteraction.blueprint.BlueprintGregTechSupport.captureCovers(null, 0, 0, 0).isEmpty();
            System.out.println("GT-absent reflection check passed: support disabled without class-loading failure.");
            return;
        }
        rayChecks();
        bucketPatchCheck();
        tradePatchCheck();
        rendererPatchCheck();
        minecraftPatchCheck();
        dropPatchCheck();
        dropInventoryCheck();
        reproductionAndCollisionCheck();
        ae2PatchCheck();
        ae2RayAndContextCheck();
        Vec3 start = Vec3.createVectorHelper(1, 2, 3), end = Vec3.createVectorHelper(-4, 5, 6);
        ByteBuf bytes = Unpooled.buffer();
        try {
            new FreecamActions.Action(2, 8, -1, start, end, end).toBytes(bytes);
            assert bytes.readableBytes() == 84;
            FreecamActions.Action decoded = new FreecamActions.Action();
            decoded.fromBytes(bytes);
            assert decoded.kind == 2 && decoded.slot == 8 && decoded.target == -1;
            assert decoded.start.squareDistanceTo(start) == 0 && decoded.end.squareDistanceTo(end) == 0;
            try { decoded.fromBytes(bytes); throw new AssertionError("Truncated payload accepted"); }
            catch (IllegalArgumentException expected) {}
        } finally { bytes.release(); }
        assert FreecamTarget.pick(null, null, end) == null;
        assert FreecamTarget.pick(null, start, start) == null;
        assert FreecamTarget.pick(null, start, Vec3.createVectorHelper(Double.NaN, 0, 0)) == null;
        assert FreecamTarget.pick(null, start, Vec3.createVectorHelper(1000, 0, 0)) == null;
        assert FreecamRange.contains(0, 0, 0, -32.0, 64.0, 0.0);
        assert !FreecamRange.contains(0, 0, 0, 48.0, 64.0, 0.0);

        // ModeRequest 报文格式校验
        ByteBuf reqBuf = Unpooled.buffer();
        try {
            new local.freecaminteraction.FreecamInteraction.ModeRequest(42, true).toBytes(reqBuf);
            assert reqBuf.readableBytes() == 5 : "ModeRequest 应为 5 字节";
            local.freecaminteraction.FreecamInteraction.ModeRequest reqDecoded = new local.freecaminteraction.FreecamInteraction.ModeRequest();
            reqDecoded.fromBytes(reqBuf);
            assert reqDecoded.epoch == 42 && reqDecoded.enabled;
            try { reqDecoded.fromBytes(reqBuf); throw new AssertionError("未拦截截断报文"); } catch (IllegalArgumentException expected) {}
        } finally { reqBuf.release(); }

        // ModeAck 报文格式校验
        ByteBuf ackBuf = Unpooled.buffer();
        try {
            new local.freecaminteraction.FreecamInteraction.ModeAck(42, true, 1, 3).toBytes(ackBuf);
            assert ackBuf.readableBytes() == 13 : "ModeAck 应为 13 字节";
            local.freecaminteraction.FreecamInteraction.ModeAck ackDecoded = new local.freecaminteraction.FreecamInteraction.ModeAck();
            ackDecoded.fromBytes(ackBuf);
            assert ackDecoded.epoch == 42 && ackDecoded.enabled && ackDecoded.tierOrdinal == 1 && ackDecoded.radius == 3;
            try { ackDecoded.fromBytes(ackBuf); throw new AssertionError("未拦截截断报文"); } catch (IllegalArgumentException expected) {}
        } finally { ackBuf.release(); }

        // 法杖物品与背包扫描选取断言
        local.freecaminteraction.item.ItemFreecamWand wandNorm = new local.freecaminteraction.item.ItemFreecamWand(local.freecaminteraction.WandTier.NORMAL);
        local.freecaminteraction.item.ItemFreecamWand wandAdv = new local.freecaminteraction.item.ItemFreecamWand(local.freecaminteraction.WandTier.ADVANCED);
        local.freecaminteraction.item.ItemFreecamWand wandCre = new local.freecaminteraction.item.ItemFreecamWand(local.freecaminteraction.WandTier.CREATIVE);
        assert wandNorm.getMaxDamage() == 2048;
        assert wandAdv.getMaxDamage() == 8192;
        assert wandCre.getMaxDamage() == 0;
        assert !wandNorm.getIsRepairable(new net.minecraft.item.ItemStack(wandNorm), null);
        assert !wandCre.getIsRepairable(new net.minecraft.item.ItemStack(wandCre), new net.minecraft.item.ItemStack(wandNorm));

        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        net.minecraft.entity.player.EntityPlayerMP testPlayer = (net.minecraft.entity.player.EntityPlayerMP) unsafe.allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
        testPlayer.inventory = new net.minecraft.entity.player.InventoryPlayer(testPlayer);

        // 槽位 0 放剩余 1 点耐久的普通法杖 -> 不可用，应返回 null
        testPlayer.inventory.mainInventory[0] = new net.minecraft.item.ItemStack(wandNorm, 1, 2047);
        assert local.freecaminteraction.item.ItemFreecamWand.findBestWand(testPlayer) == null : "耐久<=1 的法杖不可用";

        // 槽位 1 放剩余 2 点耐久的普通法杖 -> 可用，选取槽位 1
        testPlayer.inventory.mainInventory[1] = new net.minecraft.item.ItemStack(wandNorm, 1, 2046);
        local.freecaminteraction.item.ItemFreecamWand.WandEntry selected = local.freecaminteraction.item.ItemFreecamWand.findBestWand(testPlayer);
        assert selected != null && selected.slot == 1 && selected.tier == local.freecaminteraction.WandTier.NORMAL;

        // 槽位 5 放高级法杖 -> 高级 > 普通，选取槽位 5
        testPlayer.inventory.mainInventory[5] = new net.minecraft.item.ItemStack(wandAdv, 1, 100);
        selected = local.freecaminteraction.item.ItemFreecamWand.findBestWand(testPlayer);
        assert selected != null && selected.slot == 5 && selected.tier == local.freecaminteraction.WandTier.ADVANCED;

        // 槽位 8 放创造法杖 -> 创造 > 高级，选取槽位 8
        testPlayer.inventory.mainInventory[8] = new net.minecraft.item.ItemStack(wandCre, 1, 0);
        selected = local.freecaminteraction.item.ItemFreecamWand.findBestWand(testPlayer);
        assert selected != null && selected.slot == 8 && selected.tier == local.freecaminteraction.WandTier.CREATIVE;

        // 槽位 3 也放创造法杖 -> 同级槽位较小者优先，选取槽位 3
        testPlayer.inventory.mainInventory[3] = new net.minecraft.item.ItemStack(wandCre, 1, 0);
        selected = local.freecaminteraction.item.ItemFreecamWand.findBestWand(testPlayer);
        assert selected != null && selected.slot == 3 && selected.tier == local.freecaminteraction.WandTier.CREATIVE;

        wandUpgradeChecks();
        blueprintChecks();

        System.out.println("Legacy action checks passed: wire format, truncation, invalid rays, entity-center boundaries, wand selection & attributes, blueprint core models.");
    }

    private static void registerItemForCheck(net.minecraft.item.Item item, int id) throws Exception {
        java.lang.reflect.Field field = null;
        Class c = net.minecraft.item.Item.itemRegistry.getClass();
        while (c != null && field == null) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (net.minecraft.util.ObjectIntIdentityMap.class.isAssignableFrom(f.getType())) {
                    field = f;
                    break;
                }
            }
            c = c.getSuperclass();
        }
        if (field != null) {
            field.setAccessible(true);
            net.minecraft.util.ObjectIntIdentityMap map = (net.minecraft.util.ObjectIntIdentityMap) field.get(net.minecraft.item.Item.itemRegistry);
            map.func_148746_a(item, id);
        }
    }

    private static void registerNamedItemForCheck(net.minecraft.item.Item item, int id, String name) throws Exception {
        registerItemForCheck(item, id);
        java.lang.reflect.Field objects = net.minecraft.util.RegistrySimple.class.getDeclaredField("registryObjects");
        objects.setAccessible(true);
        ((java.util.Map) objects.get(net.minecraft.item.Item.itemRegistry)).put(name, item);
        java.lang.reflect.Field names = net.minecraft.util.RegistryNamespaced.class.getDeclaredField("field_148758_b");
        names.setAccessible(true);
        ((java.util.Map) names.get(net.minecraft.item.Item.itemRegistry)).put(item, name);
    }

    private static void wandUpgradeChecks() throws Exception {
        local.freecaminteraction.item.ItemBlueprintCore coreItem = new local.freecaminteraction.item.ItemBlueprintCore();
        registerItemForCheck(coreItem, 4000);
        assert coreItem.getItemStackLimit() == 1 : "Blueprint core stack size must be 1";
        assert "blueprint".equals(coreItem.getCoreId()) : "Core ID must be 'blueprint'";
        net.minecraft.item.ItemStack coreStack = new net.minecraft.item.ItemStack(coreItem, 1, 0);

        local.freecaminteraction.item.ItemFreecamWand wandItem = new local.freecaminteraction.item.ItemFreecamWand(local.freecaminteraction.WandTier.NORMAL);
        registerItemForCheck(wandItem, 4001);
        net.minecraft.item.ItemStack wandStack = new net.minecraft.item.ItemStack(wandItem, 1, 0);
        assert !local.freecaminteraction.item.ItemFreecamWand.hasBlueprintCore(wandStack) : "New wand should not have blueprint core";
        assert local.freecaminteraction.item.ItemFreecamWand.getUpgradeCore(wandStack, 0) == null;

        local.freecaminteraction.item.ItemFreecamWand.setUpgradeCore(wandStack, 2, coreStack);
        assert local.freecaminteraction.item.ItemFreecamWand.hasBlueprintCore(wandStack) : "Wand should detect blueprint core in slot 2";
        net.minecraft.item.ItemStack retrieved = local.freecaminteraction.item.ItemFreecamWand.getUpgradeCore(wandStack, 2);
        assert retrieved != null && retrieved.getItem() == coreItem : "Retrieved core must match blueprint core";
        assert local.freecaminteraction.item.ItemFreecamWand.getUpgradeCore(wandStack, 0) == null;

        net.minecraft.item.ItemStack[] allCores = local.freecaminteraction.item.ItemFreecamWand.loadUpgrades(wandStack);
        assert allCores.length == 4;
        assert allCores[0] == null && allCores[1] == null && allCores[2] != null && allCores[3] == null;

        // 清除核心
        local.freecaminteraction.item.ItemFreecamWand.setUpgradeCore(wandStack, 2, null);
        assert !local.freecaminteraction.item.ItemFreecamWand.hasBlueprintCore(wandStack);

        // 3. FreecamInteraction.hasBlueprintCoreWand 判定
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) unsafe.allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
        net.minecraft.entity.DataWatcher dw = new net.minecraft.entity.DataWatcher(player);
        dw.addObject(6, Float.valueOf(20.0F));
        java.lang.reflect.Field dwField = net.minecraft.entity.Entity.class.getDeclaredField("dataWatcher");
        dwField.setAccessible(true);
        dwField.set(player, dw);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        player.inventoryContainer = new DummyContainer();
        player.inventory.mainInventory[0] = wandStack;

        // 非自由视角下必然为 false
        assert !local.freecaminteraction.FreecamInteraction.hasBlueprintCoreWand(player) : "Must return false when freecam is inactive";

        // 模拟自由视角激活状态
        java.lang.reflect.Field activeField = local.freecaminteraction.FreecamInteraction.class.getDeclaredField("ACTIVE");
        activeField.setAccessible(true);
        java.util.Map activeMap = (java.util.Map) activeField.get(null);
        java.lang.reflect.Constructor stateCtor = Class.forName("local.freecaminteraction.FreecamInteraction$State")
                .getDeclaredConstructors()[0];
        stateCtor.setAccessible(true);
        Object state = stateCtor.newInstance(player.dimension, 5.0D, local.freecaminteraction.WandTier.NORMAL, 0, null);
        activeMap.put(player, state);

        try {
            assert local.freecaminteraction.FreecamInteraction.getActiveWandSlot(player) == 0;
            assert !local.freecaminteraction.FreecamInteraction.hasBlueprintCoreWand(player) : "Wand in slot 0 has no core";

            // 安装核心
            local.freecaminteraction.item.ItemFreecamWand.setUpgradeCore(wandStack, 0, coreStack);
            assert local.freecaminteraction.FreecamInteraction.hasBlueprintCoreWand(player) : "Wand in slot 0 has core";

            // 移除法杖
            player.inventory.mainInventory[0] = null;
            assert !local.freecaminteraction.FreecamInteraction.hasBlueprintCoreWand(player) : "Wand removed, qualification must be false";

            // 换成非法物品（例如石头）
            player.inventory.mainInventory[0] = new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.stone);
            assert !local.freecaminteraction.FreecamInteraction.hasBlueprintCoreWand(player) : "Non-wand item must return false";

            // 背包中同时存在无核心法杖（生效）与有核心法杖时，施工资格仍必须为真
            net.minecraft.item.ItemStack coreWand = new net.minecraft.item.ItemStack(wandItem, 1, 0);
            local.freecaminteraction.item.ItemFreecamWand.setUpgradeCore(coreWand, 0, coreStack);
            local.freecaminteraction.item.ItemFreecamWand.setUpgradeCore(wandStack, 0, null);
            player.inventory.mainInventory[0] = new net.minecraft.item.ItemStack(wandItem, 1, 0);
            player.inventory.mainInventory[5] = coreWand;
            assert local.freecaminteraction.FreecamInteraction.hasBlueprintCoreWand(player)
                    : "Any core-bearing wand in inventory must qualify";
            player.inventory.mainInventory[5] = new net.minecraft.item.ItemStack(wandItem, 1, 0);
            assert !local.freecaminteraction.FreecamInteraction.hasBlueprintCoreWand(player)
                    : "No core-bearing wand in inventory must not qualify";
            player.inventory.mainInventory[5] = null;
            player.inventory.mainInventory[0] = wandStack;
            // 还原 wandStack 的核心（后续容器测试依赖 slot0 已装核心）
            local.freecaminteraction.item.ItemFreecamWand.setUpgradeCore(wandStack, 0, coreStack);
        } finally {
            activeMap.remove(player);
        }

        // 4. ContainerWandUpgrade 容器槽位、安全机制与防刷验证
        player.inventory.mainInventory[0] = wandStack; // wandSlot = 0 (快捷栏第0格)
        local.freecaminteraction.inventory.ContainerWandUpgrade container =
                new local.freecaminteraction.inventory.ContainerWandUpgrade(player.inventory, 0);

        // 槽位总数应为 40 (4核心 + 27主背包 + 9快捷栏)
        assert container.inventorySlots.size() == 40 : "Container slots must be 40";
        // 快捷栏槽位 0 的容器槽位索引应为 31
        assert container.getLockedContainerSlot() == 31 : "Locked container slot should be 31 for wandSlot 0";

        net.minecraft.inventory.Slot lockedSlot = (net.minecraft.inventory.Slot) container.inventorySlots.get(31);
        assert !lockedSlot.canTakeStack(player) : "Locked slot must not allow taking stack";
        assert !lockedSlot.isItemValid(coreStack) : "Locked slot must not accept items";

        // 防刷 1: slotClick 直接点击锁定槽位拦截
        assert container.slotClick(31, 0, 0, player) == null : "Direct click on locked slot must be blocked";
        assert container.slotClick(31, 0, 1, player) == null : "Shift-click on locked slot must be blocked";

        // 防刷 2: 数字键换槽拦截 (mode == 2 且 clickedButton == 0)
        assert container.slotClick(5, 0, 2, player) == null : "Hotbar swap targeting locked wand slot must be blocked";

        // 核心槽位验证:
        net.minecraft.inventory.Slot coreSlot0 = (net.minecraft.inventory.Slot) container.inventorySlots.get(0);
        net.minecraft.inventory.Slot coreSlot1 = (net.minecraft.inventory.Slot) container.inventorySlots.get(1);

        // 仅接受核心物品，拒绝非核心物品
        assert !coreSlot1.isItemValid(new net.minecraft.item.ItemStack(net.minecraft.init.Items.diamond)) : "Core slot must reject non-core item";

        // 同杖防重复安装核心校验:
        // coreSlot0 此时已安装 blueprintCore (由 wandStack 构造时读取)
        assert coreSlot0.getHasStack() && coreSlot0.getStack().getItem() == coreItem;
        assert !coreSlot1.isItemValid(coreStack) : "Duplicate blueprint core must be rejected";

        // 取出 slot0 的核心后，slot1 与 slot0 应该能接受
        coreSlot0.putStack(null);
        coreSlot0.onSlotChanged();
        assert coreSlot0.isItemValid(coreStack) : "Empty core slot 0 should accept core";
        assert coreSlot1.isItemValid(coreStack) : "After removing from slot 0, slot 1 should accept core";

        // Shift-click 安全搬运测试:
        // 在玩家主背包槽位 9 (容器槽位 4) 放入蓝图核心
        net.minecraft.inventory.Slot invSlot4 = (net.minecraft.inventory.Slot) container.inventorySlots.get(4);
        invSlot4.putStack(coreStack.copy());
        invSlot4.onSlotChanged();

        // 从主背包 shift-click 蓝图核心 -> 自动移入空核心槽 0
        container.transferStackInSlot(player, 4);
        assert coreSlot0.getHasStack() && coreSlot0.getStack().getItem() == coreItem : "Shift-click core must transfer to core slot";
        assert !invSlot4.getHasStack() : "Origin slot should be empty";

        // 再次在槽位 4 放入一个重复的蓝图核心并 shift-click -> 核心槽已有同种核心，不可移入核心槽，转为快捷栏
        invSlot4.putStack(coreStack.copy());
        invSlot4.onSlotChanged();
        container.transferStackInSlot(player, 4);
        assert !coreSlot1.getHasStack() : "Duplicate core must NOT shift-click into core slots";

        // 普通物品在主背包 shift-click -> 绝不进入核心槽或锁定法杖槽
        net.minecraft.item.Item nonCoreItem = new net.minecraft.item.Item();
        registerItemForCheck(nonCoreItem, 4002);
        invSlot4.putStack(new net.minecraft.item.ItemStack(nonCoreItem, 16));
        invSlot4.onSlotChanged();
        container.transferStackInSlot(player, 4);
        assert !coreSlot1.getHasStack() : "Ordinary item must never transfer to core slots";

        // 从核心槽 0 shift-click 核心 -> 移回玩家背包
        container.transferStackInSlot(player, 0);
        assert !coreSlot0.getHasStack() : "Shift-click from core slot must remove it from core slot";

        // 关闭容器时安全保存 NBT 校验
        coreSlot0.putStack(coreStack.copy());
        coreSlot0.onSlotChanged();
        container.onContainerClosed(player);
        assert local.freecaminteraction.item.ItemFreecamWand.hasBlueprintCore(wandStack) : "Closing container must persist upgrades to wand NBT";

        System.out.println("Wand upgrade container checks passed: core attributes, 4-slot layout, locked wand anti-dupe, duplicate prevention, shift-click safety, and NBT persistence.");
    }

    private static void blueprintChecks() {
        // 1. MaterialRequirement 基础与匹配判定
        local.freecaminteraction.blueprint.MaterialRequirement req1 =
                new local.freecaminteraction.blueprint.MaterialRequirement("minecraft:stone", 0, null, 10);
        assert req1.getCount() == 10 && req1.getPlacedCount() == 0;
        assert req1.getRemainingCount() == 10 && !req1.isSatisfied();
        req1.addPlacedCount(4);
        assert req1.getPlacedCount() == 4 && req1.getRemainingCount() == 6;

        local.freecaminteraction.item.ItemFreecamWand testItem = new local.freecaminteraction.item.ItemFreecamWand(local.freecaminteraction.WandTier.NORMAL);
        net.minecraft.item.ItemStack wandStack = new net.minecraft.item.ItemStack(testItem, 1, 0);
        local.freecaminteraction.blueprint.MaterialRequirement reqItem =
                new local.freecaminteraction.blueprint.MaterialRequirement(wandStack, 5);
        assert reqItem.getCount() == 5;
        assert reqItem.matches(wandStack);

        net.minecraft.item.ItemStack otherStack = new net.minecraft.item.ItemStack(testItem, 1, 10);
        assert !reqItem.matches(otherStack);

        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setString("CustomName", "TestChest");
        net.minecraft.item.ItemStack customStack = new net.minecraft.item.ItemStack(testItem, 1, 0);
        customStack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        customStack.getTagCompound().setString("CustomName", "TestChest");
        customStack.getTagCompound().setInteger("OtherKey", 123);
        local.freecaminteraction.blueprint.MaterialRequirement nbtReq =
                new local.freecaminteraction.blueprint.MaterialRequirement(customStack, 1);
        assert nbtReq.matches(customStack);
        assert !nbtReq.matches(wandStack);

        // 2. BlueprintPartEntry 基础与物料
        local.freecaminteraction.blueprint.BlueprintPartEntry part =
                new local.freecaminteraction.blueprint.BlueprintPartEntry(0, 1, 0, 2, "cable_anchor", null,
                        new local.freecaminteraction.blueprint.MaterialRequirement("appliedenergistics2:item.ItemMultiPart", 120, null, 1));
        assert part.getSide() == 2 && "cable_anchor".equals(part.getPartId());
        assert part.getRequiredMaterial().getCount() == 1;

        // 2b. 部件支持层：中心槽位语义与空参降级（不得抛异常，不得误判为已安装）
        assert local.freecaminteraction.blueprint.BlueprintPartSupport.SIDE_CENTER == 6;
        assert net.minecraftforge.common.util.ForgeDirection.getOrientation(6)
                == net.minecraftforge.common.util.ForgeDirection.UNKNOWN
                : "中心槽位必须映射到 ForgeDirection.UNKNOWN（线缆本体）";
        assert "".equals(local.freecaminteraction.blueprint.BlueprintPartSupport.partIdOf(null));
        assert local.freecaminteraction.blueprint.BlueprintPartSupport
                .partIdOf(new net.minecraft.item.ItemStack(testItem, 1, 7))
                .equals(local.freecaminteraction.blueprint.MaterialRequirement.resolveItemName(testItem) + ":7");
        try {
            assert !local.freecaminteraction.blueprint.BlueprintPartSupport.isPartHost(null);
            assert local.freecaminteraction.blueprint.BlueprintPartSupport.captureParts(null, 0, 0, 0).isEmpty();
            assert !local.freecaminteraction.blueprint.BlueprintPartSupport.matchesInstalled(null, 0, 0, 0, part);
            assert !local.freecaminteraction.blueprint.BlueprintPartSupport.installPart(null, 0, 0, 0, part, null);
            assert !local.freecaminteraction.blueprint.BlueprintPartSupport.canSelfHost(part);
            assert !local.freecaminteraction.blueprint.BlueprintPartSupport.canSelfHost(null);

            // Guava Present 是包私有实现类，必须通过公开 Optional 基类调用 get()。
            Class<?> optionalClass = Class.forName("com.google.common.base.Optional");
            Object present = optionalClass.getMethod("of", Object.class).invoke(null, "ae2-bus");
            java.lang.reflect.Method optionalGet = local.freecaminteraction.blueprint.BlueprintPartSupport.class
                    .getDeclaredMethod("optionalGet", Object.class);
            optionalGet.setAccessible(true);
            assert "ae2-bus".equals(optionalGet.invoke(null, present))
                    : "AE2 definition Optional must unwrap without IllegalAccessException";

            // GT 机器 ID 补正：鸭子类型 getMetaTileID()，无该方法的 TE 与 null 均返回 -1
            net.minecraft.tileentity.TileEntity plainTe = new net.minecraft.tileentity.TileEntity();
            net.minecraft.tileentity.TileEntity metaTe = new net.minecraft.tileentity.TileEntity() {
                public int getMetaTileID() { return 4242; }
            };
            assert local.freecaminteraction.blueprint.VanillaBlueprintAdapter.teItemIdOverride(null) == -1;
            assert local.freecaminteraction.blueprint.VanillaBlueprintAdapter.teItemIdOverride(plainTe) == -1;
            assert local.freecaminteraction.blueprint.VanillaBlueprintAdapter.teItemIdOverride(metaTe) == 4242;
        } catch (Throwable t) {
            throw new AssertionError("部件支持层空参不得抛出异常: " + t);
        }

        gregTechBlueprintChecks();

        // 3. BlueprintBlockEntry 与物料
        java.util.List reqList = new java.util.ArrayList();
        reqList.add(reqItem);
        net.minecraft.block.Block solidBlock = new RayBlock(net.minecraft.block.material.Material.rock);
        local.freecaminteraction.blueprint.BlueprintBlockEntry blockEntry =
                new local.freecaminteraction.blueprint.BlueprintBlockEntry(0, 0, 0, solidBlock, 0, null, "vanilla", reqList);
        assert !blockEntry.isAir();
        assert blockEntry.getBlock() == solidBlock;

        // 4. BlueprintData 统计与校验
        java.util.List blocks = new java.util.ArrayList();
        blocks.add(blockEntry);
        java.util.List parts = new java.util.ArrayList();
        parts.add(part);
        local.freecaminteraction.blueprint.BlueprintData data =
                new local.freecaminteraction.blueprint.BlueprintData("bp-test-1", "Test Blueprint",
                        java.util.UUID.randomUUID(), 5, 5, 5, 0, 0, 0, System.currentTimeMillis(), blocks, parts);
        assert data.getTotalVolume() == 125;
        assert data.getTotalBlockCount() == 1;
        assert data.getTotalPartCount() == 1;
        assert data.getNonAirBlockCount() == 1;
        assert data.isValidBounds() && !data.isEmpty() && !data.isAllAir();

        java.util.List materials = data.getConsolidatedMaterials();
        assert materials.size() == 2;

        local.freecaminteraction.blueprint.BlueprintData emptyData =
                new local.freecaminteraction.blueprint.BlueprintData("empty", "Empty", null, 0, 0, 0, 0, 0, 0, 0, null, null);
        assert emptyData.isEmpty() && !emptyData.isValidBounds() && emptyData.isAllAir();

        // 5. VanillaBlueprintAdapter 安全过滤与非法方块禁止
        local.freecaminteraction.blueprint.VanillaBlueprintAdapter adapter =
                local.freecaminteraction.blueprint.VanillaBlueprintAdapter.INSTANCE;
        assert adapter.isIllegalBlockName("minecraft:bedrock");
        assert adapter.isIllegalBlockName("minecraft:portal");
        assert adapter.isIllegalBlockName("minecraft:end_portal");
        assert adapter.isIllegalBlockName("minecraft:command_block");
        assert !adapter.isIllegalBlockName("minecraft:chest");

        // NBT 过滤校验：剔除 Items/fluid/energy，保留 CustomName/facing
        net.minecraft.nbt.NBTTagCompound dirtyTag = new net.minecraft.nbt.NBTTagCompound();
        dirtyTag.setString("id", "Chest");
        dirtyTag.setString("CustomName", "SecureChest");
        dirtyTag.setInteger("facing", 3);
        dirtyTag.setInteger("x", 100);
        dirtyTag.setInteger("y", 64);
        dirtyTag.setInteger("z", -20);
        net.minecraft.nbt.NBTTagList itemsList = new net.minecraft.nbt.NBTTagList();
        net.minecraft.nbt.NBTTagCompound itemTag = new net.minecraft.nbt.NBTTagCompound();
        itemTag.setShort("id", (short) 1);
        itemTag.setByte("Count", (byte) 64);
        itemsList.appendTag(itemTag);
        dirtyTag.setTag("Items", itemsList);
        dirtyTag.setInteger("BurnTime", 200);

        net.minecraft.nbt.NBTTagCompound sanitized = adapter.sanitizeTileTag(dirtyTag);
        assert sanitized != null;
        assert "SecureChest".equals(sanitized.getString("CustomName"));
        assert sanitized.getInteger("facing") == 3;
        assert !sanitized.hasKey("Items");
        assert !sanitized.hasKey("BurnTime");
        assert !sanitized.hasKey("x") && !sanitized.hasKey("y") && !sanitized.hasKey("z");

        // 仅剩 TileEntity 类型 id 的残缺 NBT 不可回灌，否则 GT 机器会丢失 mID 并变成黑紫块。
        net.minecraft.nbt.NBTTagCompound idOnlyTag = new net.minecraft.nbt.NBTTagCompound();
        idOnlyTag.setString("id", "BaseMetaTileEntity");
        assert adapter.sanitizeTileTag(idOnlyTag) == null;

        local.freecaminteraction.blueprint.BlueprintBlockEntry legacyGtEntry =
                new local.freecaminteraction.blueprint.BlueprintBlockEntry(
                        0, 0, 0, new RayBlock(net.minecraft.block.material.Material.rock), 0, idOnlyTag, "vanilla",
                        java.util.Collections.<local.freecaminteraction.blueprint.MaterialRequirement>emptyList());
        local.freecaminteraction.blueprint.BlueprintData legacyGtBlueprint =
                new local.freecaminteraction.blueprint.BlueprintData(
                        "legacy-gt", "legacy-gt", java.util.UUID.randomUUID(),
                        1, 1, 1, 0, 0, 0, System.currentTimeMillis(),
                        java.util.Collections.singletonList(legacyGtEntry),
                        java.util.Collections.<local.freecaminteraction.blueprint.BlueprintPartEntry>emptyList());
        java.util.List<local.freecaminteraction.blueprint.build.BlueprintBuildScheduler.BuildUnit> legacyGtPlan =
                local.freecaminteraction.blueprint.build.BlueprintBuildScheduler.generateDependencySortedPlan(legacyGtBlueprint);
        assert legacyGtPlan.size() == 1;
        assert legacyGtPlan.get(0).getPhase()
                != local.freecaminteraction.blueprint.build.BlueprintBuildScheduler.BuildPhase.FINALIZE_CONFIG;

        System.out.println("Blueprint core checks passed: BlueprintData, BlockEntry, PartEntry, MaterialRequirement, VanillaAdapter sanitize & illegal blocks.");
        storageChecks();
        networkChecks();
        buildExecutorChecks();
    }

    private static void gregTechBlueprintChecks() {
        try {
            final byte[] facing = new byte[] { 4 };
            final net.minecraft.item.ItemStack[] covers = new net.minecraft.item.ItemStack[6];
            final int[] coverData = new int[6];
            final int[] coverUpdates = new int[1];
            net.minecraft.item.Item coverItem = new net.minecraft.item.Item().setUnlocalizedName("gt_cover_check");
            registerNamedItemForCheck(coverItem, 4020, "freecam_interaction:gt_cover_check");
            covers[1] = new net.minecraft.item.ItemStack(coverItem, 1, 7);
            covers[4] = new net.minecraft.item.ItemStack(coverItem, 1, 8);
            coverData[1] = 12345;
            coverData[4] = -77;

            Class<?> gtTile = Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
            Object fake = java.lang.reflect.Proxy.newProxyInstance(
                    gtTile.getClassLoader(), new Class<?>[] { gtTile }, new java.lang.reflect.InvocationHandler() {
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                    String name = method.getName();
                    if ("getFrontFacing".equals(name)) return Byte.valueOf(facing[0]);
                    if ("isValidFacing".equals(name)) {
                        int side = ((Byte) args[0]).byteValue();
                        return Boolean.valueOf(side >= 0 && side < 6);
                    }
                    if ("setFrontFacing".equals(name)) {
                        facing[0] = ((Byte) args[0]).byteValue();
                        return null;
                    }
                    if ("getCoverItemAtSide".equals(name)) return covers[((Byte) args[0]).byteValue()];
                    if ("getCoverDataAtSide".equals(name)) return Integer.valueOf(coverData[((Byte) args[0]).byteValue()]);
                    if ("canPlaceCoverItemAtSide".equals(name)) {
                        return Boolean.valueOf(covers[((Byte) args[0]).byteValue()] == null);
                    }
                    if ("setCoverItemAtSide".equals(name)) {
                        int side = ((Byte) args[0]).byteValue();
                        covers[side] = ((net.minecraft.item.ItemStack) args[1]).copy();
                        return null;
                    }
                    if ("setCoverDataAtSide".equals(name)) {
                        coverData[((Byte) args[0]).byteValue()] = ((Integer) args[1]).intValue();
                        return null;
                    }
                    if ("issueCoverUpdate".equals(name)) {
                        coverUpdates[0]++;
                        return null;
                    }
                    Class<?> type = method.getReturnType();
                    if (type == Boolean.TYPE) return Boolean.FALSE;
                    if (type == Byte.TYPE) return Byte.valueOf((byte) 0);
                    if (type == Short.TYPE) return Short.valueOf((short) 0);
                    if (type == Integer.TYPE) return Integer.valueOf(0);
                    if (type == Long.TYPE) return Long.valueOf(0L);
                    if (type == Float.TYPE) return Float.valueOf(0F);
                    if (type == Double.TYPE) return Double.valueOf(0D);
                    if (type == Character.TYPE) return Character.valueOf((char) 0);
                    return null;
                }
            });

            assert local.freecaminteraction.blueprint.BlueprintGregTechSupport.captureFrontFacing(fake) == 4;
            java.util.List<local.freecaminteraction.blueprint.BlueprintPartEntry> captured =
                    local.freecaminteraction.blueprint.BlueprintGregTechSupport.captureCovers(fake, 3, 2, 1);
            assert captured.size() == 2 : "Two GT covers must produce two part entries";
            assert captured.get(0).getSide() == 1 && captured.get(1).getSide() == 4;
            assert captured.get(0).getRequiredMaterial().getDamage() == 7;
            assert captured.get(1).getRequiredMaterial().getDamage() == 8;
            assert captured.get(0).getConfigTag().getInteger("coverData") == 12345;
            assert captured.get(1).getConfigTag().getInteger("coverData") == -77;

            local.freecaminteraction.blueprint.BlueprintPartEntry first = captured.get(0);
            covers[1] = null;
            coverData[1] = 0;
            assert local.freecaminteraction.blueprint.BlueprintGregTechSupport.installCover(fake, first);
            assert coverData[1] == 12345 && coverUpdates[0] > 0;
            assert local.freecaminteraction.blueprint.BlueprintGregTechSupport.matchesCover(fake, first);

            facing[0] = 2;
            assert local.freecaminteraction.blueprint.BlueprintGregTechSupport.applyFrontFacing(fake, 4);
            assert facing[0] == 4 : "GT facing must be restored independently of player yaw";

            net.minecraft.nbt.NBTTagCompound gtConfig = new net.minecraft.nbt.NBTTagCompound();
            gtConfig.setInteger(local.freecaminteraction.blueprint.BlueprintGregTechSupport.FRONT_FACING_KEY, 4);
            local.freecaminteraction.blueprint.BlueprintBlockEntry machine =
                    new local.freecaminteraction.blueprint.BlueprintBlockEntry(0, 0, 0,
                            new RayBlock(net.minecraft.block.material.Material.rock), 0, gtConfig, "vanilla",
                            java.util.Collections.<local.freecaminteraction.blueprint.MaterialRequirement>emptyList());
            local.freecaminteraction.blueprint.BlueprintData blueprint =
                    new local.freecaminteraction.blueprint.BlueprintData("gt", "gt", java.util.UUID.randomUUID(),
                            1, 1, 1, 0, 0, 0, System.currentTimeMillis(),
                            java.util.Collections.singletonList(machine), captured);
            java.util.List<local.freecaminteraction.blueprint.build.BlueprintBuildScheduler.BuildUnit> plan =
                    local.freecaminteraction.blueprint.build.BlueprintBuildScheduler.generateDependencySortedPlan(blueprint);
            assert plan.size() == 4;
            assert plan.get(0).getPhase() == local.freecaminteraction.blueprint.build.BlueprintBuildScheduler.BuildPhase.SOLID_BASE;
            assert plan.get(1).getPhase() == local.freecaminteraction.blueprint.build.BlueprintBuildScheduler.BuildPhase.PARTS;
            assert plan.get(2).getPhase() == local.freecaminteraction.blueprint.build.BlueprintBuildScheduler.BuildPhase.PARTS;
            assert plan.get(3).getPhase() == local.freecaminteraction.blueprint.build.BlueprintBuildScheduler.BuildPhase.FINALIZE_CONFIG;

            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            local.freecaminteraction.blueprint.storage.BlueprintStorage.writeBlueprint(
                    blueprint, new java.io.DataOutputStream(bytes));
            local.freecaminteraction.blueprint.BlueprintData restored =
                    local.freecaminteraction.blueprint.storage.BlueprintStorage.readBlueprint(
                            new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
            assert restored.getBlockEntries().get(0).getTileTag().getInteger(
                    local.freecaminteraction.blueprint.BlueprintGregTechSupport.FRONT_FACING_KEY) == 4;
            assert restored.getPartEntries().size() == 2;
            assert restored.getPartEntries().get(0).getRequiredMaterial().getDamage() == 7;
            assert restored.getPartEntries().get(0).getConfigTag().getInteger("coverData") == 12345;
            System.out.println("GT blueprint checks passed: facing, cover capture/install/data, BPFC round-trip, idempotence, and dependency order.");
        } catch (Throwable t) {
            throw new AssertionError("GT blueprint reflection checks failed", t);
        }
    }

    public static class DummySaveHandler implements net.minecraft.world.storage.ISaveHandler {
        private final java.io.File worldDir;
        public DummySaveHandler(java.io.File worldDir) { this.worldDir = worldDir; }
        public net.minecraft.world.storage.WorldInfo loadWorldInfo() { return null; }
        public void checkSessionLock() throws net.minecraft.world.MinecraftException {}
        public net.minecraft.world.chunk.storage.IChunkLoader getChunkLoader(net.minecraft.world.WorldProvider p) { return null; }
        public void saveWorldInfoWithPlayer(net.minecraft.world.storage.WorldInfo w, net.minecraft.nbt.NBTTagCompound t) {}
        public void saveWorldInfo(net.minecraft.world.storage.WorldInfo w) {}
        public net.minecraft.world.storage.IPlayerFileData getSaveHandler() { return null; }
        public void flush() {}
        public java.io.File getWorldDirectory() { return worldDir; }
        public java.io.File getMapFileFromName(String s) { return null; }
        public String getWorldDirectoryName() { return "dummyWorld"; }
    }

    public static class StorageWorld extends RayWorld {
        net.minecraft.world.storage.ISaveHandler saveHandler;
        java.util.Map customMeta = new java.util.HashMap();
        StorageWorld(java.io.File dir) {
            super();
            this.saveHandler = new DummySaveHandler(dir);
            this.air = net.minecraft.init.Blocks.air;
        }
        public net.minecraft.world.storage.ISaveHandler getSaveHandler() { return saveHandler; }
        public boolean setBlock(int x, int y, int z, net.minecraft.block.Block b, int meta, int notify) {
            if (customBlocks != null) customBlocks.put(x + "," + y + "," + z, b);
            if (customMeta != null) customMeta.put(x + "," + y + "," + z, Integer.valueOf(meta));
            return true;
        }
        public net.minecraft.tileentity.TileEntity getTileEntity(int x, int y, int z) {
            return null;
        }
        public int getBlockMetadata(int x, int y, int z) {
            if (customMeta != null) {
                Object m = customMeta.get(x + "," + y + "," + z);
                if (m != null) return ((Integer) m).intValue();
            }
            return 0;
        }
    }

    private static void storageChecks() {
        try {
            java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);

            java.io.File tempDir = java.nio.file.Files.createTempDirectory("bp_storage_test").toFile();
            tempDir.deleteOnExit();

            StorageWorld world = (StorageWorld) unsafe.allocateInstance(StorageWorld.class);
            world.saveHandler = new DummySaveHandler(tempDir);

            java.util.UUID authorUuid = java.util.UUID.randomUUID();
            java.util.UUID otherUuid = java.util.UUID.randomUUID();

            net.minecraft.entity.player.EntityPlayerMP ownerPlayer = (net.minecraft.entity.player.EntityPlayerMP) unsafe.allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
            ownerPlayer.worldObj = world;
            java.lang.reflect.Field entityUuidField = net.minecraft.entity.Entity.class.getDeclaredField("entityUniqueID");
            entityUuidField.setAccessible(true);
            entityUuidField.set(ownerPlayer, authorUuid);
            for (java.lang.reflect.Field f : net.minecraft.entity.player.EntityPlayer.class.getDeclaredFields()) {
                if (f.getType().getName().contains("GameProfile")) {
                    unsafe.putObject(ownerPlayer, unsafe.objectFieldOffset(f), new com.mojang.authlib.GameProfile(authorUuid, "OwnerTester"));
                    break;
                }
            }

            net.minecraft.entity.player.EntityPlayerMP otherPlayer = (net.minecraft.entity.player.EntityPlayerMP) unsafe.allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
            otherPlayer.worldObj = world;
            entityUuidField.set(otherPlayer, otherUuid);
            for (java.lang.reflect.Field f : net.minecraft.entity.player.EntityPlayer.class.getDeclaredFields()) {
                if (f.getType().getName().contains("GameProfile")) {
                    unsafe.putObject(otherPlayer, unsafe.objectFieldOffset(f), new com.mojang.authlib.GameProfile(otherUuid, "OtherTester"));
                    break;
                }
            }

            // --- 1. BlueprintStorage 基础存储与目录验证 ---
            java.io.File storageDir = local.freecaminteraction.blueprint.storage.BlueprintStorage.getStorageDir(world, authorUuid);
            assert storageDir.exists() && storageDir.isDirectory() : "Storage directory creation failed";
            assert storageDir.getAbsolutePath().contains(authorUuid.toString());

            // 构造测试蓝图数据
            java.util.List reqList = new java.util.ArrayList();
            reqList.add(new local.freecaminteraction.blueprint.MaterialRequirement("minecraft:cobblestone", 0, null, 16));
            java.util.List blocks = new java.util.ArrayList();
            blocks.add(new local.freecaminteraction.blueprint.BlueprintBlockEntry(0, 0, 0, "minecraft:cobblestone", null, 0, null, "vanilla", reqList));
            java.util.List parts = new java.util.ArrayList();
            parts.add(new local.freecaminteraction.blueprint.BlueprintPartEntry(0, 0, 0, 1, "test_part", null,
                    new local.freecaminteraction.blueprint.MaterialRequirement("minecraft:torch", 0, null, 1)));

            local.freecaminteraction.blueprint.BlueprintData originalBp =
                    new local.freecaminteraction.blueprint.BlueprintData("bp_test_alpha", "Alpha Castle",
                            authorUuid, 10, 5, 8, 1, 0, 2, System.currentTimeMillis(), blocks, parts);

            // 保存蓝图 (原子替换写入)
            boolean saved = local.freecaminteraction.blueprint.storage.BlueprintStorage.saveBlueprint(world, originalBp);
            assert saved : "saveBlueprint failed";
            java.io.File bpFile = new java.io.File(storageDir, "bp_test_alpha.bp");
            assert bpFile.exists() && bpFile.length() > 0 : "Blueprint file was not created";

            // 加载蓝图并校验分段解构字段
            local.freecaminteraction.blueprint.BlueprintData loadedBp =
                    local.freecaminteraction.blueprint.storage.BlueprintStorage.loadBlueprint(world, authorUuid, "bp_test_alpha");
            assert loadedBp != null : "loadBlueprint failed";
            assert "bp_test_alpha".equals(loadedBp.getId());
            assert "Alpha Castle".equals(loadedBp.getName());
            assert authorUuid.equals(loadedBp.getAuthorUuid());
            assert loadedBp.getSizeX() == 10 && loadedBp.getSizeY() == 5 && loadedBp.getSizeZ() == 8;
            assert loadedBp.getOriginOffsetX() == 1 && loadedBp.getOriginOffsetY() == 0 && loadedBp.getOriginOffsetZ() == 2;
            assert loadedBp.getTotalBlockCount() == 1;
            assert loadedBp.getTotalPartCount() == 1;
            assert loadedBp.getNonAirBlockCount() == 1;
            assert "minecraft:cobblestone".equals(loadedBp.getBlockEntries().get(0).getBlockRegistryName());
            assert "test_part".equals(loadedBp.getPartEntries().get(0).getPartId());

            // 列表查询
            java.util.List list = local.freecaminteraction.blueprint.storage.BlueprintStorage.listBlueprints(world, authorUuid);
            assert list.size() == 1 : "Expected 1 blueprint in list, got " + list.size();

            // 损坏文件隔离 (.corrupt)
            java.io.File badFile = new java.io.File(storageDir, "bp_corrupt_test.bp");
            java.nio.file.Files.write(badFile.toPath(), new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 });
            assert badFile.exists();
            local.freecaminteraction.blueprint.BlueprintData corruptLoaded =
                    local.freecaminteraction.blueprint.storage.BlueprintStorage.loadBlueprint(world, authorUuid, "bp_corrupt_test");
            assert corruptLoaded == null : "Corrupt blueprint must return null";
            assert !badFile.exists() : "Bad file should be renamed/isolated";
            java.io.File[] corruptList = storageDir.listFiles((dir, name) -> name.contains("corrupt"));
            assert corruptList != null && corruptList.length > 0 : "Corrupt file isolation missing";

            // --- 2. BlueprintTask 独立快照与权限状态 ---
            local.freecaminteraction.blueprint.storage.BlueprintTask task =
                    local.freecaminteraction.blueprint.storage.BlueprintTaskManager.createTask(
                            world, ownerPlayer, "bp_test_alpha", 100, 64, 200);
            assert task != null : "createTask failed";
            assert task.getTaskId() != null;
            assert authorUuid.equals(task.getOwnerUuid());
            assert task.getWorldId() == 0;
            assert task.getOriginX() == 100 && task.getOriginY() == 64 && task.getOriginZ() == 200;
            assert task.getPermission() == local.freecaminteraction.blueprint.storage.TaskPermission.VISIBLE_ONLY;
            assert task.isShowOutsideFreecam();
            assert task.getStatus() == local.freecaminteraction.blueprint.storage.TaskStatus.PENDING;
            assert task.getTotalBlocks() == 1;

            // 验证快照独立性：删除个人蓝图后，任务快照仍然完整存在且可加载
            boolean deleted = local.freecaminteraction.blueprint.storage.BlueprintStorage.deleteBlueprint(world, authorUuid, "bp_test_alpha");
            assert deleted : "deleteBlueprint failed";
            assert local.freecaminteraction.blueprint.storage.BlueprintStorage.loadBlueprint(world, authorUuid, "bp_test_alpha") == null;

            local.freecaminteraction.blueprint.storage.BlueprintTask reloadedTask =
                    local.freecaminteraction.blueprint.storage.BlueprintTaskManager.loadTask(world, task.getTaskId());
            assert reloadedTask != null : "Task should persist independently";
            assert reloadedTask.getSnapshot() != null;
            assert "Alpha Castle".equals(reloadedTask.getSnapshot().getName());
            assert reloadedTask.getSnapshot().getTotalBlockCount() == 1;

            // --- 3. 三档权限与协作施工鉴权 ---
            // 默认 VISIBLE_ONLY:
            // 主人: 可见，可建造
            assert task.isVisibleTo(authorUuid, true);
            assert task.isVisibleTo(authorUuid, false); // 主人在模式外仍可见
            assert task.isBuildableBy(authorUuid);
            // 协作者: 可见，但不可建造
            assert task.isVisibleTo(otherUuid, true);
            assert !task.isBuildableBy(otherUuid);

            // 非主人尝试修改权限 -> 严密拒绝
            boolean hackedPerm = local.freecaminteraction.blueprint.storage.BlueprintTaskManager.updatePermission(
                    world, otherPlayer, task.getTaskId(), local.freecaminteraction.blueprint.storage.TaskPermission.BUILDABLE);
            assert !hackedPerm : "Security: non-owner must not change permission";
            assert task.getPermission() == local.freecaminteraction.blueprint.storage.TaskPermission.VISIBLE_ONLY;

            // 主人修改为 BUILDABLE:
            boolean ownerPerm = local.freecaminteraction.blueprint.storage.BlueprintTaskManager.updatePermission(
                    world, ownerPlayer, task.getTaskId(), local.freecaminteraction.blueprint.storage.TaskPermission.BUILDABLE);
            assert ownerPerm : "Owner should be able to change permission";
            assert task.getPermission() == local.freecaminteraction.blueprint.storage.TaskPermission.BUILDABLE;
            assert task.isBuildableBy(otherUuid) : "Collaborator should be allowed to build when permission=BUILDABLE";

            // 主人修改为 HIDDEN:
            local.freecaminteraction.blueprint.storage.BlueprintTaskManager.updatePermission(
                    world, ownerPlayer, task.getTaskId(), local.freecaminteraction.blueprint.storage.TaskPermission.HIDDEN);
            assert task.isVisibleTo(authorUuid, true) : "Owner always sees task";
            assert !task.isVisibleTo(otherUuid, true) : "HIDDEN task must not be visible to collaborator";
            assert !task.isBuildableBy(otherUuid) : "HIDDEN task cannot be built by collaborator";

            // 模式外虚影控制 showOutsideFreecam:
            local.freecaminteraction.blueprint.storage.BlueprintTaskManager.updatePermission(
                    world, ownerPlayer, task.getTaskId(), local.freecaminteraction.blueprint.storage.TaskPermission.VISIBLE_ONLY);
            assert task.isVisibleTo(otherUuid, true);
            assert task.isVisibleTo(otherUuid, false); // 默认 showOutsideFreecam == true
            local.freecaminteraction.blueprint.storage.BlueprintTaskManager.setShowOutside(world, ownerPlayer, task.getTaskId(), false);
            assert !task.isVisibleTo(otherUuid, false) : "Collaborator cannot see task outside freecam when disabled";

            // 任务列表过滤 listTasksForPlayer:
            java.util.List ownerTasks = local.freecaminteraction.blueprint.storage.BlueprintTaskManager.listTasksForPlayer(world, ownerPlayer, true);
            assert ownerTasks.size() == 1;
            java.util.List otherTasks = local.freecaminteraction.blueprint.storage.BlueprintTaskManager.listTasksForPlayer(world, otherPlayer, false);
            assert otherTasks.isEmpty() : "Other player outside freecam should see 0 tasks when showOutside=false";

            // 取消任务 cancelTask:
            boolean hackedCancel = local.freecaminteraction.blueprint.storage.BlueprintTaskManager.cancelTask(world, otherPlayer, task.getTaskId());
            assert !hackedCancel : "Non-owner must not cancel task";
            assert task.getStatus() != local.freecaminteraction.blueprint.storage.TaskStatus.CANCELLED;

            boolean ownerCancel = local.freecaminteraction.blueprint.storage.BlueprintTaskManager.cancelTask(world, ownerPlayer, task.getTaskId());
            assert ownerCancel : "Owner should cancel task";
            assert task.getStatus() == local.freecaminteraction.blueprint.storage.TaskStatus.CANCELLED : "Task status was " + task.getStatus();
            assert !task.isBuildableBy(authorUuid) : "Cancelled task cannot be built";

            System.out.println("Blueprint storage & task manager checks passed: atomic replacement, corrupt isolation, independent snapshot, 3-tier permissions, security enforcement.");
        } catch (Throwable t) {
            throw new RuntimeException("storageChecks failed", t);
        }
    }

    private static void networkChecks() {
        try {
            // 1. PacketCaptureRequest & PacketCaptureAck
            local.freecaminteraction.blueprint.network.PacketCaptureRequest capReq =
                    new local.freecaminteraction.blueprint.network.PacketCaptureRequest(10, 64, 20, 15, 70, 25, "Castle");
            io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
            capReq.toBytes(buf);
            local.freecaminteraction.blueprint.network.PacketCaptureRequest capReqRead =
                    new local.freecaminteraction.blueprint.network.PacketCaptureRequest();
            capReqRead.fromBytes(buf);
            assert capReqRead.x1 == 10 && capReqRead.y1 == 64 && capReqRead.z1 == 20;
            assert capReqRead.x2 == 15 && capReqRead.y2 == 70 && capReqRead.z2 == 25;
            assert "Castle".equals(capReqRead.name);

            buf.clear();
            local.freecaminteraction.blueprint.network.PacketCaptureAck capAck =
                    new local.freecaminteraction.blueprint.network.PacketCaptureAck(true, "bp-123", "Castle", "ok", 42);
            capAck.toBytes(buf);
            local.freecaminteraction.blueprint.network.PacketCaptureAck capAckRead =
                    new local.freecaminteraction.blueprint.network.PacketCaptureAck();
            capAckRead.fromBytes(buf);
            assert capAckRead.success;
            assert "bp-123".equals(capAckRead.blueprintId);
            assert "Castle".equals(capAckRead.blueprintName);
            assert "ok".equals(capAckRead.message);
            assert capAckRead.totalBlocks == 42;

            // 2. PacketBlueprintListRequest & PacketBlueprintListResponse
            buf.clear();
            local.freecaminteraction.blueprint.network.PacketBlueprintListRequest listReq =
                    new local.freecaminteraction.blueprint.network.PacketBlueprintListRequest(99);
            listReq.toBytes(buf);
            local.freecaminteraction.blueprint.network.PacketBlueprintListRequest listReqRead =
                    new local.freecaminteraction.blueprint.network.PacketBlueprintListRequest();
            listReqRead.fromBytes(buf);
            assert listReqRead.requestEpoch == 99;

            buf.clear();
            java.util.List bpList = new java.util.ArrayList();
            bpList.add(new local.freecaminteraction.blueprint.network.PacketBlueprintListResponse.BlueprintSummary(
                    "bp-1", "House", 5, 4, 3, 60, 123456789L));
            java.util.List tList = new java.util.ArrayList();
            tList.add(new local.freecaminteraction.blueprint.network.PacketBlueprintListResponse.TaskSummary(
                    "t-1", "bp-1", "House", "Alice", 0, 10, 64, 10, (byte) 1, true, (byte) 0, true));
            local.freecaminteraction.blueprint.network.PacketBlueprintListResponse listResp =
                    new local.freecaminteraction.blueprint.network.PacketBlueprintListResponse(99, bpList, tList);
            listResp.toBytes(buf);
            local.freecaminteraction.blueprint.network.PacketBlueprintListResponse listRespRead =
                    new local.freecaminteraction.blueprint.network.PacketBlueprintListResponse();
            listRespRead.fromBytes(buf);
            assert listRespRead.responseEpoch == 99;
            assert listRespRead.blueprints.size() == 1;
            assert "House".equals(listRespRead.blueprints.get(0).name);
            assert listRespRead.tasks.size() == 1;
            assert "Alice".equals(listRespRead.tasks.get(0).ownerName);

            // 3. PacketBlueprintSlice & Assembler
            buf.clear();
            byte[] testPayload = new byte[100];
            for (int i = 0; i < testPayload.length; i++) testPayload[i] = (byte) i;
            local.freecaminteraction.blueprint.network.PacketBlueprintSlice slice =
                    new local.freecaminteraction.blueprint.network.PacketBlueprintSlice("trans-abc", 0, 2, testPayload);
            slice.toBytes(buf);
            local.freecaminteraction.blueprint.network.PacketBlueprintSlice sliceRead =
                    new local.freecaminteraction.blueprint.network.PacketBlueprintSlice();
            sliceRead.fromBytes(buf);
            assert "trans-abc".equals(sliceRead.transferId);
            assert sliceRead.sliceIndex == 0 && sliceRead.totalSlices == 2;
            assert sliceRead.payload.length == 100;

            // 4. PacketTaskAction
            buf.clear();
            local.freecaminteraction.blueprint.network.PacketTaskAction actCreate =
                    local.freecaminteraction.blueprint.network.PacketTaskAction.create("bp-1", 100, 65, -200);
            actCreate.toBytes(buf);
            local.freecaminteraction.blueprint.network.PacketTaskAction actRead =
                    new local.freecaminteraction.blueprint.network.PacketTaskAction();
            actRead.fromBytes(buf);
            assert actRead.action == local.freecaminteraction.blueprint.network.PacketTaskAction.ACTION_CREATE;
            assert "bp-1".equals(actRead.blueprintId);
            assert actRead.anchorX == 100 && actRead.anchorY == 65 && actRead.anchorZ == -200;

            buf.clear();
            local.freecaminteraction.blueprint.network.PacketTaskAction actPerm =
                    local.freecaminteraction.blueprint.network.PacketTaskAction.changePermission("task-99", (byte) 2);
            actPerm.toBytes(buf);
            actRead = new local.freecaminteraction.blueprint.network.PacketTaskAction();
            actRead.fromBytes(buf);
            assert actRead.action == local.freecaminteraction.blueprint.network.PacketTaskAction.ACTION_CHANGE_PERMISSION;
            assert "task-99".equals(actRead.taskId);
            assert actRead.permission == 2;

            buf.clear();
            local.freecaminteraction.blueprint.network.PacketTaskAction actDel =
                    local.freecaminteraction.blueprint.network.PacketTaskAction.deleteBlueprint("bp-del-1");
            actDel.toBytes(buf);
            actRead = new local.freecaminteraction.blueprint.network.PacketTaskAction();
            actRead.fromBytes(buf);
            assert actRead.action == local.freecaminteraction.blueprint.network.PacketTaskAction.ACTION_DELETE_BLUEPRINT;
            assert "bp-del-1".equals(actRead.blueprintId);

            buf.clear();
            local.freecaminteraction.blueprint.network.PacketTaskAction actBld =
                    local.freecaminteraction.blueprint.network.PacketTaskAction.build("task-bld-1");
            actBld.toBytes(buf);
            actRead = new local.freecaminteraction.blueprint.network.PacketTaskAction();
            actRead.fromBytes(buf);
            assert actRead.action == local.freecaminteraction.blueprint.network.PacketTaskAction.ACTION_BUILD;
            assert "task-bld-1".equals(actRead.taskId);

            // 5. PacketTaskSync
            buf.clear();
            local.freecaminteraction.blueprint.network.PacketTaskSync syncUpsert =
                    local.freecaminteraction.blueprint.network.PacketTaskSync.upsert(
                            "task-99", "bp-1", "House", "Bob", 0, 10, 64, 10, (byte) 2, true, (byte) 0, false);
            syncUpsert.toBytes(buf);
            local.freecaminteraction.blueprint.network.PacketTaskSync syncRead =
                    new local.freecaminteraction.blueprint.network.PacketTaskSync();
            syncRead.fromBytes(buf);
            assert syncRead.syncType == local.freecaminteraction.blueprint.network.PacketTaskSync.TYPE_UPSERT;
            assert "task-99".equals(syncRead.taskId);
            assert syncRead.permission == 2;
            assert !syncRead.isOwner;

            buf.clear();
            local.freecaminteraction.blueprint.network.PacketTaskSync syncRemove =
                    local.freecaminteraction.blueprint.network.PacketTaskSync.remove("task-99");
            syncRemove.toBytes(buf);
            syncRead = new local.freecaminteraction.blueprint.network.PacketTaskSync();
            syncRead.fromBytes(buf);
            assert syncRead.syncType == local.freecaminteraction.blueprint.network.PacketTaskSync.TYPE_REMOVE;
            assert "task-99".equals(syncRead.taskId);

            // 6. BlueprintClientCache 响应式监听器与状态验证
            final int[] listenerCalled = new int[] { 0 };
            local.freecaminteraction.blueprint.network.BlueprintClientCache.CacheListener testListener =
                    new local.freecaminteraction.blueprint.network.BlueprintClientCache.CacheListener() {
                        public void onCacheUpdated() {
                            listenerCalled[0]++;
                        }
                    };
            local.freecaminteraction.blueprint.network.BlueprintClientCache.clear();
            local.freecaminteraction.blueprint.network.BlueprintClientCache.addListener(testListener);

            // 设置个人蓝图列表
            java.util.List personalList = new java.util.ArrayList();
            personalList.add(new local.freecaminteraction.blueprint.network.PacketBlueprintListResponse.BlueprintSummary(
                    "bp-client-1", "Castle", 10, 20, 15, 300, 1000L));
            local.freecaminteraction.blueprint.network.BlueprintClientCache.setPersonalBlueprints(personalList);
            assert listenerCalled[0] == 1 : "Listener should be notified when personal blueprints set";
            assert local.freecaminteraction.blueprint.network.BlueprintClientCache.getPersonalBlueprints().size() == 1;
            assert "Castle".equals(local.freecaminteraction.blueprint.network.BlueprintClientCache.getPersonalBlueprints().get(0).name);

            // 更新任务
            local.freecaminteraction.blueprint.network.BlueprintClientCache.updateTask(syncUpsert);
            assert listenerCalled[0] == 2 : "Listener should be notified when task updated";
            assert local.freecaminteraction.blueprint.network.BlueprintClientCache.getTask("task-99") != null;
            assert local.freecaminteraction.blueprint.network.BlueprintClientCache.getAllTasks().size() == 1;

            // 移除任务
            local.freecaminteraction.blueprint.network.BlueprintClientCache.removeTask("task-99");
            assert listenerCalled[0] == 3 : "Listener should be notified when task removed";
            assert local.freecaminteraction.blueprint.network.BlueprintClientCache.getTask("task-99") == null;
            assert local.freecaminteraction.blueprint.network.BlueprintClientCache.getAllTasks().isEmpty();

            // 移除监听器后不再触发
            local.freecaminteraction.blueprint.network.BlueprintClientCache.removeListener(testListener);
            local.freecaminteraction.blueprint.network.BlueprintClientCache.clear();
            assert listenerCalled[0] == 3 : "Listener must not be called after removal";

            // 完工任务移除通路：服务端完工与取消共用 TYPE_REMOVE 同步包，客户端必须从列表撤下，
            // 否则已完工任务会长期滞留，再次点击只会得到“任务不存在或已结束”。
            local.freecaminteraction.blueprint.network.BlueprintClientCache.updateTask(syncUpsert);
            assert local.freecaminteraction.blueprint.network.BlueprintClientCache.getAllTasks().size() == 1;
            local.freecaminteraction.blueprint.network.BlueprintClientCache.updateTask(syncRemove);
            assert local.freecaminteraction.blueprint.network.BlueprintClientCache.getTask("task-99") == null
                    : "TYPE_REMOVE sync must drop the task (completed tasks are not kept)";
            assert local.freecaminteraction.blueprint.network.BlueprintClientCache.getAllTasks().isEmpty();

            System.out.println("Blueprint network packet checks passed: Capture, List, Slice, TaskAction(Delete/Build), TaskSync, BlueprintClientCache.");
        } catch (Throwable t) {
            throw new RuntimeException("networkChecks failed", t);
        }
    }

    private static void buildExecutorChecks() {
        try {
            java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);

            java.io.File tempDir = java.nio.file.Files.createTempDirectory("bp_build_test").toFile();
            tempDir.deleteOnExit();

            StorageWorld world = (StorageWorld) unsafe.allocateInstance(StorageWorld.class);
            world.customBlocks = new java.util.HashMap();
            world.customMeta = new java.util.HashMap();
            world.saveHandler = new DummySaveHandler(tempDir);
            world.air = net.minecraft.init.Blocks.air;
            world.solid = new RayBlock(net.minecraft.block.material.Material.rock);

            java.util.UUID authorUuid = java.util.UUID.randomUUID();
            java.util.UUID otherUuid = java.util.UUID.randomUUID();

            java.lang.reflect.Field bounds = net.minecraft.entity.Entity.class.getField("boundingBox");

            // 1. Owner 玩家初始化
            net.minecraft.entity.player.EntityPlayerMP ownerPlayer = (net.minecraft.entity.player.EntityPlayerMP) unsafe.allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
            ownerPlayer.worldObj = world;
            ownerPlayer.dimension = 0;
            ownerPlayer.posX = 0.5; ownerPlayer.posY = 60.0; ownerPlayer.posZ = 0.5;
            unsafe.putObject(ownerPlayer, unsafe.objectFieldOffset(bounds), net.minecraft.util.AxisAlignedBB.getBoundingBox(0, 60, 0, 1, 62, 1));
            ownerPlayer.inventory = new net.minecraft.entity.player.InventoryPlayer(ownerPlayer);
            ownerPlayer.inventoryContainer = new DummyContainer();
            ownerPlayer.capabilities = new net.minecraft.entity.player.PlayerCapabilities();

            java.lang.reflect.Field dwField = net.minecraft.entity.Entity.class.getDeclaredField("dataWatcher");
            dwField.setAccessible(true);
            net.minecraft.entity.DataWatcher dwOwner = new net.minecraft.entity.DataWatcher(ownerPlayer);
            dwOwner.addObject(6, Float.valueOf(20.0F));
            dwField.set(ownerPlayer, dwOwner);

            java.lang.reflect.Field entityUuidField = net.minecraft.entity.Entity.class.getDeclaredField("entityUniqueID");
            entityUuidField.setAccessible(true);
            entityUuidField.set(ownerPlayer, authorUuid);
            for (java.lang.reflect.Field f : net.minecraft.entity.player.EntityPlayer.class.getDeclaredFields()) {
                if (f.getType().getName().contains("GameProfile")) {
                    unsafe.putObject(ownerPlayer, unsafe.objectFieldOffset(f), new com.mojang.authlib.GameProfile(authorUuid, "OwnerTester"));
                    break;
                }
            }

            // 2. Collaborator 协作者玩家初始化
            net.minecraft.entity.player.EntityPlayerMP collabPlayer = (net.minecraft.entity.player.EntityPlayerMP) unsafe.allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
            collabPlayer.worldObj = world;
            collabPlayer.dimension = 0;
            collabPlayer.posX = 0.5; collabPlayer.posY = 60.0; collabPlayer.posZ = 0.5;
            unsafe.putObject(collabPlayer, unsafe.objectFieldOffset(bounds), net.minecraft.util.AxisAlignedBB.getBoundingBox(0, 60, 0, 1, 62, 1));
            collabPlayer.inventory = new net.minecraft.entity.player.InventoryPlayer(collabPlayer);
            collabPlayer.inventoryContainer = new DummyContainer();
            collabPlayer.capabilities = new net.minecraft.entity.player.PlayerCapabilities();
            net.minecraft.entity.DataWatcher dwCollab = new net.minecraft.entity.DataWatcher(collabPlayer);
            dwCollab.addObject(6, Float.valueOf(20.0F));
            dwField.set(collabPlayer, dwCollab);
            entityUuidField.set(collabPlayer, otherUuid);
            for (java.lang.reflect.Field f : net.minecraft.entity.player.EntityPlayer.class.getDeclaredFields()) {
                if (f.getType().getName().contains("GameProfile")) {
                    unsafe.putObject(collabPlayer, unsafe.objectFieldOffset(f), new com.mojang.authlib.GameProfile(otherUuid, "CollabTester"));
                    break;
                }
            }

            // 为协作者配置法杖与核心
            local.freecaminteraction.item.ItemBlueprintCore coreItem = new local.freecaminteraction.item.ItemBlueprintCore();
            registerItemForCheck(coreItem, 4010);
            net.minecraft.item.ItemStack coreStack = new net.minecraft.item.ItemStack(coreItem, 1, 0);

            local.freecaminteraction.item.ItemFreecamWand wandNormal = new local.freecaminteraction.item.ItemFreecamWand(local.freecaminteraction.WandTier.NORMAL);
            registerItemForCheck(wandNormal, 4011);
            net.minecraft.item.ItemStack wandStack = new net.minecraft.item.ItemStack(wandNormal, 1, 0);
            collabPlayer.inventory.mainInventory[0] = wandStack;

            // 注册测试材料物品与方块
            net.minecraft.item.Item cobbleItem = new net.minecraft.item.Item();
            registerItemForCheck(cobbleItem, 4012);
            RayBlock testSolid = new RayBlock(net.minecraft.block.material.Material.rock);

            // 构造任务方块条目与任务 (目标落点 2, 64, 2)
            java.util.List reqList = new java.util.ArrayList();
            reqList.add(new local.freecaminteraction.blueprint.MaterialRequirement(
                    new net.minecraft.item.ItemStack(cobbleItem), 1));
            local.freecaminteraction.blueprint.BlueprintBlockEntry entryCobble =
                    new local.freecaminteraction.blueprint.BlueprintBlockEntry(2, 0, 2, "minecraft:cobblestone", testSolid, 0, null, "vanilla", reqList);

            java.util.List blockList = new java.util.ArrayList();
            blockList.add(entryCobble);
            local.freecaminteraction.blueprint.BlueprintData bpData =
                    new local.freecaminteraction.blueprint.BlueprintData("bp-build-1", "BuildHouse",
                            authorUuid, 5, 5, 5, 0, 0, 0, System.currentTimeMillis(), blockList, null);

            local.freecaminteraction.blueprint.storage.BlueprintTask task =
                    new local.freecaminteraction.blueprint.storage.BlueprintTask("task-bld-1", authorUuid, "OwnerTester",
                            0, 0, 64, 0, bpData, local.freecaminteraction.blueprint.storage.TaskPermission.BUILDABLE, true,
                            local.freecaminteraction.blueprint.storage.TaskStatus.PENDING, System.currentTimeMillis(), System.currentTimeMillis(),
                            1, 0, null);

            // 模拟激活自由视角
            java.lang.reflect.Field activeField = local.freecaminteraction.FreecamInteraction.class.getDeclaredField("ACTIVE");
            activeField.setAccessible(true);
            java.util.Map activeMap = (java.util.Map) activeField.get(null);
            java.lang.reflect.Constructor stateCtor = Class.forName("local.freecaminteraction.FreecamInteraction$State")
                    .getDeclaredConstructors()[0];
            stateCtor.setAccessible(true);
            Object collabState = stateCtor.newInstance(collabPlayer.dimension, 5.0D, local.freecaminteraction.WandTier.NORMAL, 0, null);
            activeMap.put(collabPlayer, collabState);

            try {
                // --- 断言 1: 无核心拒绝 (NO_CORE) ---
                assert !local.freecaminteraction.FreecamInteraction.hasBlueprintCoreWand(collabPlayer) : "Active wand has no core yet";
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult resNoCore =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executeBlockStep(collabPlayer, task, entryCobble);
                assert resNoCore.status == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.NO_CORE
                        : "Expected NO_CORE, got " + resNoCore.status;
                assert world.getBlock(2, 64, 2) == net.minecraft.init.Blocks.air : "World should remain air on NO_CORE";

                // 安装蓝图核心
                local.freecaminteraction.item.ItemFreecamWand.setUpgradeCore(wandStack, 0, coreStack);
                assert local.freecaminteraction.FreecamInteraction.hasBlueprintCoreWand(collabPlayer) : "Wand should now have blueprint core";

                // --- 断言 2: 越界拒绝 (OUT_OF_RANGE) ---
                local.freecaminteraction.blueprint.BlueprintBlockEntry outEntry =
                        new local.freecaminteraction.blueprint.BlueprintBlockEntry(500, 0, 500, "minecraft:cobblestone", testSolid, 0, null, "vanilla", reqList);
                assert !local.freecaminteraction.FreecamInteraction.inside(collabPlayer, 500, 64, 500) : "500, 64, 500 must be out of range";
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult resOutOfRange =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executeBlockStep(collabPlayer, task, outEntry);
                assert resOutOfRange.status == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.OUT_OF_RANGE
                        : "Expected OUT_OF_RANGE, got " + resOutOfRange.status;

                // --- 断言 3: 三档权限拦截 (HIDDEN -> PERMISSION_DENIED, VISIBLE_ONLY -> PERMISSION_DENIED, BUILDABLE -> OK) ---
                collabPlayer.inventory.mainInventory[1] = new net.minecraft.item.ItemStack(cobbleItem, 10);

                // 3.1 HIDDEN 权限拦截
                task.setPermission(local.freecaminteraction.blueprint.storage.TaskPermission.HIDDEN);
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult resHidden =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executeBlockStep(collabPlayer, task, entryCobble);
                assert resHidden.status == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.PERMISSION_DENIED
                        : "Expected PERMISSION_DENIED for HIDDEN, got " + resHidden.status;
                assert collabPlayer.inventory.mainInventory[1].stackSize == 10 : "Materials must not be touched on PERMISSION_DENIED";

                // 3.2 VISIBLE_ONLY 权限拦截
                task.setPermission(local.freecaminteraction.blueprint.storage.TaskPermission.VISIBLE_ONLY);
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult resVisible =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executeBlockStep(collabPlayer, task, entryCobble);
                assert resVisible.status == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.PERMISSION_DENIED
                        : "Expected PERMISSION_DENIED for VISIBLE_ONLY, got " + resVisible.status;
                assert collabPlayer.inventory.mainInventory[1].stackSize == 10;

                // 恢复为 BUILDABLE
                task.setPermission(local.freecaminteraction.blueprint.storage.TaskPermission.BUILDABLE);

                // --- 断言 4: 协作者扣自己材料，严禁动用主人库存 (物料红线契约) ---
                // 主人拥有 64 个材料在槽位 1
                ownerPlayer.inventory.mainInventory[1] = new net.minecraft.item.ItemStack(cobbleItem, 64);
                // 协作者材料清零
                collabPlayer.inventory.mainInventory[1] = null;

                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult resNoMat =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executeBlockStep(collabPlayer, task, entryCobble);
                assert resNoMat.status == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.MISSING_MATERIALS
                        : "Expected MISSING_MATERIALS, got " + resNoMat.status;
                // 关键断言：绝对不能动用任务主人的库存！
                assert ownerPlayer.inventory.mainInventory[1].stackSize == 64 : "Owner inventory must NOT be touched by collaborator!";
                assert world.getBlock(2, 64, 2) == net.minecraft.init.Blocks.air;

                // 协作者放入 5 个材料
                collabPlayer.inventory.mainInventory[1] = new net.minecraft.item.ItemStack(cobbleItem, 5);
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult resSuccess =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executeBlockStep(collabPlayer, task, entryCobble);
                assert resSuccess.status == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.SUCCESS
                        : "Expected SUCCESS, got " + resSuccess.status;
                // 协作者自身消耗 1 个
                assert collabPlayer.inventory.mainInventory[1].stackSize == 4 : "Collaborator should deduct 1 material, remaining: " + collabPlayer.inventory.mainInventory[1].stackSize;
                // 主人库存依然毫发无损
                assert ownerPlayer.inventory.mainInventory[1].stackSize == 64 : "Owner inventory must remain 64!";
                // 世界方块成功放置
                assert world.getBlock(2, 64, 2) == testSolid : "World block should now be testSolid";
                // 成功放置扣除协作者生效法杖 1 点耐久
                assert wandStack.getItemDamage() == 1 : "Wand durability should be deducted by 1, damage=" + wandStack.getItemDamage();

                // --- 断言 5: 已有方块匹配跳过，不扣料、不扣耐久 ---
                int wandDmgBefore = wandStack.getItemDamage();
                int matBefore = collabPlayer.inventory.mainInventory[1].stackSize;
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult resSkip =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executeBlockStep(collabPlayer, task, entryCobble);
                assert resSkip.status == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.SKIPPED_ALREADY_MATCHES
                        : "Expected SKIPPED_ALREADY_MATCHES, got " + resSkip.status;
                assert collabPlayer.inventory.mainInventory[1].stackSize == matBefore : "Material must not be deducted on skip";
                assert wandStack.getItemDamage() == wandDmgBefore : "Wand durability must not be deducted on skip";

                // --- 断言 6: 并发防竞态 (Concurrent Coordinate Lock) ---
                local.freecaminteraction.blueprint.BlueprintBlockEntry entryRace =
                        new local.freecaminteraction.blueprint.BlueprintBlockEntry(3, 0, 3, "minecraft:cobblestone", testSolid, 0, null, "vanilla", reqList);

                // 外部线程持锁
                boolean lock1 = local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.tryLockCoordinate(0, 3, 64, 3);
                assert lock1 : "First lock on (0, 3, 64, 3) should succeed";
                boolean lockDouble = local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.tryLockCoordinate(0, 3, 64, 3);
                assert !lockDouble : "Second lock on the same coordinate must fail";

                // 另一个施工线程试图施工该坐标 -> 立即被 COORDINATE_LOCKED 拦截
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult resLocked =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executeBlockStep(collabPlayer, task, entryRace);
                assert resLocked.status == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.COORDINATE_LOCKED
                        : "Expected COORDINATE_LOCKED, got " + resLocked.status;
                assert collabPlayer.inventory.mainInventory[1].stackSize == matBefore : "Material must not be deducted when locked";

                // 释放锁
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.unlockCoordinate(0, 3, 64, 3);
                // 再次执行 -> 成功获取锁并施工
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult resUnlocked =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executeBlockStep(collabPlayer, task, entryRace);
                assert resUnlocked.status == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.SUCCESS;
                assert collabPlayer.inventory.mainInventory[1].stackSize == matBefore - 1;

                // --- 断言 7: 三段式事务之失败回滚 (Rollback on placement failure) ---
                // 在 (4, 64, 4) 放置一堵不可替换的基质岩墙，制造冲突阻挡
                world.customBlocks.put("4,64,4", world.solid);
                local.freecaminteraction.blueprint.BlueprintBlockEntry entryBlocked =
                        new local.freecaminteraction.blueprint.BlueprintBlockEntry(4, 0, 4, "minecraft:cobblestone", testSolid, 0, null, "vanilla", reqList);
                int matBeforeFail = collabPlayer.inventory.mainInventory[1].stackSize;
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult resFail =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executeBlockStep(collabPlayer, task, entryBlocked);
                assert resFail.status == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.CONFLICT_OR_BLOCKED;
                assert collabPlayer.inventory.mainInventory[1].stackSize == matBeforeFail : "Material must be rolled back and preserved on placement failure!";

                // --- 断言 8: 部件安装失败不扣物料或耐久 ---
                local.freecaminteraction.blueprint.BlueprintPartEntry failedPart =
                        new local.freecaminteraction.blueprint.BlueprintPartEntry(2, 0, 2, 2, "missing:test_part", null,
                                new local.freecaminteraction.blueprint.MaterialRequirement(
                                        new net.minecraft.item.ItemStack(cobbleItem), 1));
                int partMatBefore = collabPlayer.inventory.mainInventory[1].stackSize;
                int partWandBefore = wandStack.getItemDamage();
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult failedPartResult =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executePartStep(collabPlayer, task, failedPart);
                assert failedPartResult.status
                        == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.CONFLICT_OR_BLOCKED;
                assert collabPlayer.inventory.mainInventory[1].stackSize == partMatBefore
                        : "Failed part installation must not deduct materials";
                assert wandStack.getItemDamage() == partWandBefore
                        : "Failed part installation must not deduct wand durability";

                // --- 断言 9: 法杖耐久降至 1 时触发标准自动接续 ---
                wandStack.setItemDamage(2046); // maxDamage=2048, 剩余 2 点
                // 在槽位 5 放另一把满耐久普通法杖用于接续
                net.minecraft.item.ItemStack backupWand = new net.minecraft.item.ItemStack(wandNormal, 1, 0);
                collabPlayer.inventory.mainInventory[5] = backupWand;

                local.freecaminteraction.blueprint.BlueprintBlockEntry entryWand =
                        new local.freecaminteraction.blueprint.BlueprintBlockEntry(5, 0, 5, "minecraft:cobblestone", testSolid, 0, null, "vanilla", reqList);
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResult resWand =
                        local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.executeBlockStep(collabPlayer, task, entryWand);
                assert resWand.status == local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.BuildResultStatus.SUCCESS;
                // wandStack 耐久被扣至 2047 (剩余 1)，触发 succeedWand，激活槽位切换到 5
                assert wandStack.getItemDamage() == 2047;
                assert local.freecaminteraction.FreecamInteraction.getActiveWandSlot(collabPlayer) == 5
                        : "Expected wand handoff to slot 5, got " + local.freecaminteraction.FreecamInteraction.getActiveWandSlot(collabPlayer);

                System.out.println("BlueprintBuildExecutor checks passed: qualification (no-core, out-of-range), 3-tier permissions, collaborator material conservation, 3-phase transaction rollback, wand deduction & succession, and concurrent coordinate locking.");
            } finally {
                activeMap.remove(collabPlayer);
                local.freecaminteraction.blueprint.build.BlueprintBuildExecutor.clearLocks();
            }
        } catch (Throwable t) {
            throw new RuntimeException("buildExecutorChecks failed", t);
        }
    }
}
