import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import local.freecaminteraction.FreecamActions;
import local.freecaminteraction.FreecamTarget;
import local.freecaminteraction.FreecamRange;
import net.minecraft.util.Vec3;

public class LegacyActionCheck {
    // 仅测试用：跳过世界/实体构造，不启动游戏、不读取存档；射线算法仍调用真实 World 实现。
    public static class RayWorld extends net.minecraft.world.World {
        java.util.List entities;
        net.minecraft.block.Block solid, air;
        int wall;
        RayWorld() { super(null, "check", (net.minecraft.world.WorldProvider) null, null, null); }
        protected net.minecraft.world.chunk.IChunkProvider createChunkProvider() { return null; }
        protected int func_152379_p() { return 0; }
        public net.minecraft.entity.Entity getEntityByID(int id) { return null; }
        public net.minecraft.block.Block getBlock(int x, int y, int z) {
            return x == wall ? solid : air;
        }
        public int getBlockMetadata(int x, int y, int z) { return 0; }
        public java.util.List getEntitiesWithinAABBExcludingEntity(net.minecraft.entity.Entity e, net.minecraft.util.AxisAlignedBB b) { return entities; }
    }
    public static class Target extends net.minecraft.entity.passive.EntityCow {
        Target() { super(null); }
        public boolean isEntityAlive() { return true; }
        public boolean canBeCollidedWith() { return true; }
    }
    public static class RayBlock extends net.minecraft.block.Block {
        RayBlock(net.minecraft.block.material.Material material) { super(material); }
        public boolean canCollideCheck(int metadata, boolean liquids) { return getMaterial().isSolid(); }
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
        bounds.setAccessible(true);
        bounds.set(player, net.minecraft.util.AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 2, 1));
        Target entity = (Target) unsafe.allocateInstance(Target.class);
        entity.worldObj = world;
        bounds.set(entity, net.minecraft.util.AxisAlignedBB.getBoundingBox(5, 0, 0, 6, 2, 1));
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
    public static void main(String[] args) throws Exception {
        rayChecks();
        bucketPatchCheck();
        tradePatchCheck();
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
        assert FreecamRange.contains(0, 0, 0, -8.5, -0.5, -0.5);
        assert !FreecamRange.contains(0, 0, 0, 7.5, -0.5, -0.5);
        System.out.println("Legacy action checks passed: wire format, truncation, invalid rays, entity-center boundaries.");
    }
}
