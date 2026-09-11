package local.freecaminteraction.client.renderer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.blueprint.BlueprintBlockEntry;
import local.freecaminteraction.blueprint.BlueprintData;
import local.freecaminteraction.blueprint.BlueprintPartEntry;
import local.freecaminteraction.blueprint.network.BlueprintClientCache;
import local.freecaminteraction.blueprint.network.BlueprintNetwork;
import local.freecaminteraction.blueprint.network.PacketBlueprintListResponse;
import local.freecaminteraction.blueprint.network.PacketBlueprintSliceRequest;
import local.freecaminteraction.blueprint.network.PacketTaskAction;
import local.freecaminteraction.client.FreecamClient;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/**
 * 客户端半透明虚影渲染器。
 * 纯客户端 OpenGL 渲染，无实体碰撞、不向世界放置假方块。
 * 覆盖两个场景：
 * 1) 投放预览中的虚影：当玩家从蓝图列表选定蓝图进入投放预览模式时，以光标所指位置为参考点 A 渲染半透明虚影（淡青/淡绿虚影网格），点击左键固定并发送 PacketTaskAction(CREATE_TASK)；
 * 2) 已固定的建造任务虚影：从 BlueprintClientCache 读取当前世界可见的任务。若任务允许模式外显示，退出自由视角后也能看见；若未完成，在实际落点渲染方块半透明轮廓。
 */
@SideOnly(Side.CLIENT)
public final class BlueprintGhostRenderer {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final RenderBlocks RENDER_BLOCKS = new RenderBlocks();
    public static final BlueprintGhostRenderer INSTANCE = new BlueprintGhostRenderer();

    // 投放预览状态
    private boolean placementPreviewActive = false;
    private String previewBlueprintId = null;
    private String previewBlueprintName = null;
    private BlueprintData previewBlueprintData = null;

    // 防止频繁重复请求切片数据
    private long lastSliceRequestTime = 0L;

    private BlueprintGhostRenderer() {}

    public void startPlacementPreview(String blueprintId, String blueprintName) {
        this.placementPreviewActive = true;
        this.previewBlueprintId = blueprintId;
        this.previewBlueprintName = blueprintName;
        this.previewBlueprintData = BlueprintClientCache.getBlueprint(blueprintId);
        if (this.previewBlueprintData == null) {
            requestBlueprintData(blueprintId);
        }
        ModLog.info("Started placement preview for blueprint: id=" + blueprintId + "; name=" + blueprintName);
    }

    public void cancelPlacementPreview() {
        if (this.placementPreviewActive) {
            ModLog.info("Cancelled placement preview for blueprint: " + this.previewBlueprintName);
        }
        this.placementPreviewActive = false;
        this.previewBlueprintId = null;
        this.previewBlueprintName = null;
        this.previewBlueprintData = null;
    }

    public boolean isPlacementPreviewActive() {
        return placementPreviewActive;
    }

    public String getPreviewBlueprintName() {
        return previewBlueprintName;
    }

    public void onPlacementClick(int x, int y, int z) {
        if (!placementPreviewActive || previewBlueprintId == null) return;
        BlueprintNetwork.sendToServer(PacketTaskAction.create(previewBlueprintId, x, y, z));
        ModLog.info("Placement confirmed at " + x + "," + y + "," + z + " for bp=" + previewBlueprintId);
        cancelPlacementPreview();
    }

    private void requestBlueprintData(String blueprintId) {
        long now = System.currentTimeMillis();
        if (now - lastSliceRequestTime > 2000L) {
            lastSliceRequestTime = now;
            BlueprintNetwork.sendToServer(new PacketBlueprintSliceRequest(blueprintId));
            ModLog.info("Requested blueprint slice data for ghost rendering: " + blueprintId);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (MC.theWorld == null || MC.thePlayer == null) return;
        EntityLivingBase viewEntity = (EntityLivingBase) MC.renderViewEntity;
        if (viewEntity == null) viewEntity = MC.thePlayer;

        double viewX = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * event.partialTicks;
        double viewY = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * event.partialTicks;
        double viewZ = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * event.partialTicks;

        boolean inFreecam = FreecamClient.isFreecamActive();

        // 1. 渲染已固定的建造任务虚影
        renderFixedTasks(viewX, viewY, viewZ, inFreecam);

        // 2. 渲染放置预览虚影（仅在自由视角且激活预览时）
        if (inFreecam && placementPreviewActive) {
            renderPlacementPreview(viewX, viewY, viewZ);
        }
    }

    private void renderFixedTasks(double viewX, double viewY, double viewZ, boolean inFreecam) {
        Collection<PacketBlueprintListResponse.TaskSummary> tasks = BlueprintClientCache.getAllTasks();
        if (tasks == null || tasks.isEmpty()) return;

        int playerDim = MC.theWorld.provider.dimensionId;

        for (PacketBlueprintListResponse.TaskSummary task : tasks) {
            if (task == null) continue;
            // 维度检查
            if (task.dimension != playerDim) continue;
            // 状态检查：已完成或已取消的不渲染虚影
            if (task.status == 2 || task.status == 3) continue; // 2=COMPLETED, 3=CANCELLED
            // 模式外显示开关检查：如果不在自由视角下，且 task 不允许模式外显示，则跳过
            if (!inFreecam && !task.showOutside) continue;

            BlueprintData data = BlueprintClientCache.getBlueprint(task.blueprintId);
            if (data == null) {
                requestBlueprintData(task.blueprintId);
                // 数据尚未到达时，至少渲染任务选区线框占位
                renderTaskPlaceholder(task, viewX, viewY, viewZ);
                continue;
            }

            renderBlueprintGhost(data, task.anchorX, task.anchorY, task.anchorZ, viewX, viewY, viewZ, 0.4F, 0.8F, 1.0F, 0.35F);
        }
    }

    private void renderPlacementPreview(double viewX, double viewY, double viewZ) {
        MovingObjectPosition hit = FreecamClient.getSelectionHit();
        if (hit == null) return;
        int targetX = hit.blockX;
        int targetY = hit.blockY;
        int targetZ = hit.blockZ;

        // 如果点击的是方块表面，可根据朝向偏移落点，方便贴在现有表面上
        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            Block b = MC.theWorld.getBlock(targetX, targetY, targetZ);
            if (b != null && !b.isAir(MC.theWorld, targetX, targetY, targetZ) && !b.isReplaceable(MC.theWorld, targetX, targetY, targetZ)) {
                targetX += net.minecraft.util.Facing.offsetsXForSide[hit.sideHit];
                targetY += net.minecraft.util.Facing.offsetsYForSide[hit.sideHit];
                targetZ += net.minecraft.util.Facing.offsetsZForSide[hit.sideHit];
            }
        }

        if (previewBlueprintData == null && previewBlueprintId != null) {
            previewBlueprintData = BlueprintClientCache.getBlueprint(previewBlueprintId);
            if (previewBlueprintData == null) {
                requestBlueprintData(previewBlueprintId);
            }
        }

        if (previewBlueprintData != null) {
            // 淡绿/淡青半透明预览虚影
            renderBlueprintGhost(previewBlueprintData, targetX, targetY, targetZ, viewX, viewY, viewZ, 0.2F, 1.0F, 0.6F, 0.4F);
        } else {
            // 数据未到达时的简易单格占位盒
            renderBox(targetX, targetY, targetZ, targetX + 1, targetY + 1, targetZ + 1, viewX, viewY, viewZ, 0.2F, 1.0F, 0.6F, 0.5F);
        }
    }

    private void renderTaskPlaceholder(PacketBlueprintListResponse.TaskSummary task, double viewX, double viewY, double viewZ) {
        renderBox(task.anchorX, task.anchorY, task.anchorZ,
                  task.anchorX + 1, task.anchorY + 1, task.anchorZ + 1,
                  viewX, viewY, viewZ, 1.0F, 0.8F, 0.2F, 0.4F);
    }

    /**
     * 核心虚影渲染逻辑：遍历蓝图全部方块条目，对尚未完成的方块渲染半透明方块轮廓与面。
     */
    private void renderBlueprintGhost(BlueprintData data, int anchorX, int anchorY, int anchorZ,
                                     double viewX, double viewY, double viewZ,
                                     float r, float g, float b, float a) {
        if (data == null) return;
        List<BlueprintBlockEntry> blocks = data.getBlockEntries();
        renderPartHosts(data, blocks, anchorX, anchorY, anchorZ, viewX, viewY, viewZ, r, g, b, a);
        if (blocks == null || blocks.isEmpty()) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-viewX, -viewY, -viewZ);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_TEXTURE_2D);

            // 1. 绘制每个未建方块的外框线
            GL11.glLineWidth(1.5F);
            GL11.glColor4f(r, g, b, Math.min(1.0F, a * 2.0F));

            for (BlueprintBlockEntry entry : blocks) {
                if (entry == null || entry.isAir()) continue;
                int wx = anchorX + entry.getDx();
                int wy = anchorY + entry.getDy();
                int wz = anchorZ + entry.getDz();

                // 若世界中已有相同方块，视为已建，跳过虚影
                Block worldBlock = MC.theWorld.getBlock(wx, wy, wz);
                if (worldBlock != null && worldBlock != Blocks.air) {
                    Block expectedBlock = entry.getBlock();
                    int worldMeta = MC.theWorld.getBlockMetadata(wx, wy, wz);
                    if (worldBlock == expectedBlock && worldMeta == entry.getMetadata()) {
                        continue;
                    }
                }

                AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(wx, wy, wz, wx + 1, wy + 1, wz + 1);
                RenderGlobal.drawOutlinedBoundingBox(aabb, -1);
            }

            // 2. 绘制每个未建方块的半透明填充面
            GL11.glColor4f(r, g, b, a * 0.4F);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            for (BlueprintBlockEntry entry : blocks) {
                if (entry == null || entry.isAir()) continue;
                int wx = anchorX + entry.getDx();
                int wy = anchorY + entry.getDy();
                int wz = anchorZ + entry.getDz();

                Block worldBlock = MC.theWorld.getBlock(wx, wy, wz);
                if (worldBlock != null && worldBlock != Blocks.air) {
                    Block expectedBlock = entry.getBlock();
                    int worldMeta = MC.theWorld.getBlockMetadata(wx, wy, wz);
                    if (worldBlock == expectedBlock && worldMeta == entry.getMetadata()) {
                        continue;
                    }
                }

                double minX = wx, maxX = wx + 1;
                double minY = wy, maxY = wy + 1;
                double minZ = wz, maxZ = wz + 1;

                // 下
                tess.addVertex(minX, minY, minZ);
                tess.addVertex(maxX, minY, minZ);
                tess.addVertex(maxX, minY, maxZ);
                tess.addVertex(minX, minY, maxZ);
                // 上
                tess.addVertex(minX, maxY, maxZ);
                tess.addVertex(maxX, maxY, maxZ);
                tess.addVertex(maxX, maxY, minZ);
                tess.addVertex(minX, maxY, minZ);
                // 北
                tess.addVertex(minX, maxY, minZ);
                tess.addVertex(maxX, maxY, minZ);
                tess.addVertex(maxX, minY, minZ);
                tess.addVertex(minX, minY, minZ);
                // 南
                tess.addVertex(minX, minY, maxZ);
                tess.addVertex(maxX, minY, maxZ);
                tess.addVertex(maxX, maxY, maxZ);
                tess.addVertex(minX, maxY, maxZ);
                // 西
                tess.addVertex(minX, minY, minZ);
                tess.addVertex(minX, minY, maxZ);
                tess.addVertex(minX, maxY, maxZ);
                tess.addVertex(minX, maxY, minZ);
                // 东
                tess.addVertex(maxX, maxY, minZ);
                tess.addVertex(maxX, maxY, maxZ);
                tess.addVertex(maxX, minY, maxZ);
                tess.addVertex(maxX, minY, minZ);
            }
            tess.draw();

        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /** AE2 部件宿主不保存为方块条目，按坐标去重绘制宿主格，确保纯线缆蓝图可见。 */
    private void renderPartHosts(BlueprintData data, List<BlueprintBlockEntry> blocks,
                                 int anchorX, int anchorY, int anchorZ,
                                 double viewX, double viewY, double viewZ,
                                 float r, float g, float b, float a) {
        Set<String> occupied = new HashSet<String>();
        if (blocks != null) {
            for (BlueprintBlockEntry block : blocks) {
                if (block != null && !block.isAir()) {
                    occupied.add(block.getDx() + ":" + block.getDy() + ":" + block.getDz());
                }
            }
        }
        for (BlueprintPartEntry part : data.getPartEntries()) {
            if (part == null) continue;
            String key = part.getDx() + ":" + part.getDy() + ":" + part.getDz();
            if (!occupied.add(key)) continue;
            int wx = anchorX + part.getDx();
            int wy = anchorY + part.getDy();
            int wz = anchorZ + part.getDz();
            renderBox(wx, wy, wz, wx + 1, wy + 1, wz + 1, viewX, viewY, viewZ, r, g, b, a);
        }
    }

    private void renderBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                          double viewX, double viewY, double viewZ, float r, float g, float b, float a) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-viewX, -viewY, -viewZ);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_TEXTURE_2D);

            GL11.glLineWidth(2.0F);
            GL11.glColor4f(r, g, b, Math.min(1.0F, a * 2.0F));
            RenderGlobal.drawOutlinedBoundingBox(AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ), -1);

            GL11.glColor4f(r, g, b, a * 0.4F);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            // 下
            tess.addVertex(minX, minY, minZ); tess.addVertex(maxX, minY, minZ); tess.addVertex(maxX, minY, maxZ); tess.addVertex(minX, minY, maxZ);
            // 上
            tess.addVertex(minX, maxY, maxZ); tess.addVertex(maxX, maxY, maxZ); tess.addVertex(maxX, maxY, minZ); tess.addVertex(minX, maxY, minZ);
            // 北
            tess.addVertex(minX, maxY, minZ); tess.addVertex(maxX, maxY, minZ); tess.addVertex(maxX, minY, minZ); tess.addVertex(minX, minY, minZ);
            // 南
            tess.addVertex(minX, minY, maxZ); tess.addVertex(maxX, minY, maxZ); tess.addVertex(maxX, maxY, maxZ); tess.addVertex(minX, maxY, maxZ);
            // 西
            tess.addVertex(minX, minY, minZ); tess.addVertex(minX, minY, maxZ); tess.addVertex(minX, maxY, maxZ); tess.addVertex(minX, maxY, minZ);
            // 东
            tess.addVertex(maxX, maxY, minZ); tess.addVertex(maxX, maxY, maxZ); tess.addVertex(maxX, minY, maxZ); tess.addVertex(maxX, minY, minZ);
            tess.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
