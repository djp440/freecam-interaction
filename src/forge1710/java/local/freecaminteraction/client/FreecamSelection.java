package local.freecaminteraction.client;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import local.freecaminteraction.FreecamRange;
import local.freecaminteraction.FreecamTarget;
import local.freecaminteraction.WandTier;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

/** 从本帧实际矩阵反投影，包含第三人称避障、FOV 与窗口尺寸。边界框按当前法杖等级绘制区块范围。 */
public final class FreecamSelection {
    private final FloatBuffer model = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer point = BufferUtils.createFloatBuffer(4);
    public MovingObjectPosition hit;
    public Vec3 rayStart, rayEnd;

    public void render(EntityLivingBase camera, boolean selecting) {
        Minecraft mc = Minecraft.getMinecraft();
        hit = null;
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, model);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        if (selecting) {
            Vec3 start = unproject(camera, 0);
            Vec3 end = unproject(camera, 1);
            if (start != null && end != null) {
                double length = start.distanceTo(end);
                if (length > 0 && Double.isFinite(length)) {
                    // 当前已加载世界内的有限射线；首个遮挡越界时不继续向后选取。
                    end = start.addVector((end.xCoord - start.xCoord) * 256 / length,
                            (end.yCoord - start.yCoord) * 256 / length,
                            (end.zCoord - start.zCoord) * 256 / length);
                    rayStart = start;
                    rayEnd = end;
                    hit = FreecamTarget.pick(mc.thePlayer, start, end);
                }
            }
        }
        if (mc.gameSettings.hideGUI) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1.0F);
            GL11.glTranslated(-camera.posX, -camera.posY, -camera.posZ);

            WandTier tier = FreecamClient.currentTier();
            int cx = FreecamRange.centerChunk(mc.thePlayer.posX);
            int cz = FreecamRange.centerChunk(mc.thePlayer.posZ);
            int r = tier == null ? WandTier.NORMAL.radius : tier.radius;
            int minX = FreecamRange.minBlock(cx, r);
            int maxX = FreecamRange.maxBlock(cx, r);
            int minZ = FreecamRange.minBlock(cz, r);
            int maxZ = FreecamRange.maxBlock(cz, r);

            GL11.glColor4f(0.65F, 0.95F, 0.9F, 0.35F);
            RenderGlobal.drawOutlinedBoundingBox(AxisAlignedBB.getBoundingBox(minX, 0, minZ, maxX, 256, maxZ), -1);

            if (hit != null) {
                if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    Block block = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);
                    block.setBlockBoundsBasedOnState(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);
                    AxisAlignedBB bounds = block.getSelectedBoundingBoxFromPool(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);
                    if (bounds != null) {
                        GL11.glLineWidth(2.0F);
                        GL11.glColor4f(0.8F, 1.0F, 0.95F, 0.8F);
                        RenderGlobal.drawOutlinedBoundingBox(bounds.expand(0.002, 0.002, 0.002), -1);
                    }
                } else if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit != null) {
                    AxisAlignedBB bounds = hit.entityHit.boundingBox;
                    if (bounds != null) {
                        GL11.glLineWidth(2.0F);
                        GL11.glColor4f(1.0F, 0.4F, 0.4F, 0.85F);
                        RenderGlobal.drawOutlinedBoundingBox(bounds.expand(0.02, 0.02, 0.02), -1);
                    }
                }
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private Vec3 unproject(EntityLivingBase camera, float depth) {
        point.clear();
        if (!GLU.gluUnProject(Mouse.getX(), Mouse.getY(), depth, model, projection, viewport, point)) return null;
        double x = point.get(0) + camera.posX;
        double y = point.get(1) + camera.posY;
        double z = point.get(2) + camera.posZ;
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) ? Vec3.createVectorHelper(x, y, z) : null;
    }
}
