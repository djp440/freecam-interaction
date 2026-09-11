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

        System.out.println("Legacy action checks passed: wire format, truncation, invalid rays, entity-center boundaries, wand selection & attributes.");
    }
}
