package local.freecaminteraction.client;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import local.freecaminteraction.FreecamRange;
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

/** 从本帧实际矩阵反投影，包含第三人称避障、FOV 与窗口尺寸。 */
public final class FreecamSelection {
    private final FloatBuffer model = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer point = BufferUtils.createFloatBuffer(4);
    public MovingObjectPosition hit;

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
                    MovingObjectPosition candidate = mc.theWorld.rayTraceBlocks(start, end);
                    if (candidate != null && candidate.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                            && FreecamRange.contains(mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ,
                                    candidate.blockX, candidate.blockY, candidate.blockZ)) hit = candidate;
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
            int x = FreecamRange.minimumBlock(mc.thePlayer.posX);
            int y = FreecamRange.minimumBlock(mc.thePlayer.boundingBox.minY);
            int z = FreecamRange.minimumBlock(mc.thePlayer.posZ);
            GL11.glColor4f(0.65F, 0.95F, 0.9F, 0.35F);
            RenderGlobal.drawOutlinedBoundingBox(AxisAlignedBB.getBoundingBox(x, y, z, x + 16, y + 16, z + 16), -1);
            if (hit != null) {
                Block block = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);
                block.setBlockBoundsBasedOnState(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);
                AxisAlignedBB bounds = block.getSelectedBoundingBoxFromPool(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);
                if (bounds != null) {
                    GL11.glLineWidth(2.0F);
                    GL11.glColor4f(0.8F, 1.0F, 0.95F, 0.8F);
                    RenderGlobal.drawOutlinedBoundingBox(bounds.expand(0.002, 0.002, 0.002), -1);
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
