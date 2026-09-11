package local.freecaminteraction.blueprint.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import local.freecaminteraction.FreecamInteraction;
import local.freecaminteraction.blueprint.BlueprintBlockEntry;
import local.freecaminteraction.blueprint.BlueprintData;
import local.freecaminteraction.blueprint.BlueprintGregTechSupport;
import local.freecaminteraction.blueprint.BlueprintPartEntry;
import local.freecaminteraction.blueprint.BlueprintPartSupport;
import local.freecaminteraction.blueprint.VanillaBlueprintAdapter;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 服务端权威蓝图选区采集工具。
 * 客户端仅发送两端坐标意图；服务端严密复核模式、范围、边界、方块/部件与有效性。
 */
public final class BlueprintCaptureHelper {
    public static final int MAX_VOLUME = 32768; // 最大选区方块体积限制 (例如 32x32x32)

    private BlueprintCaptureHelper() {}

    public static final class CaptureResult {
        public final boolean success;
        public final String message;
        public final BlueprintData data;

        public CaptureResult(boolean success, String message, BlueprintData data) {
            this.success = success;
            this.message = message != null ? message : "";
            this.data = data;
        }
    }

    public static CaptureResult capture(EntityPlayerMP player, int x1, int y1, int z1, int x2, int y2, int z2, String name) {
        if (player == null) {
            return new CaptureResult(false, "player_null", null);
        }
        if (!FreecamInteraction.active(player)) {
            return new CaptureResult(false, "not_in_freecam", null);
        }
        World world = player.worldObj;
        if (world == null) {
            return new CaptureResult(false, "world_null", null);
        }

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        if (minY < 0 || maxY >= world.getHeight()) {
            return new CaptureResult(false, "height_out_of_bounds", null);
        }

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        long volume = (long) sizeX * (long) sizeY * (long) sizeZ;
        if (volume <= 0 || volume > MAX_VOLUME) {
            return new CaptureResult(false, "volume_out_of_bounds", null);
        }

        // 校验选区两端坐标是否在合法自由视角范围内
        if (!FreecamInteraction.inside(player, x1, y1, z1) || !FreecamInteraction.inside(player, x2, y2, z2)) {
            return new CaptureResult(false, "endpoint_out_of_range", null);
        }

        // 检查所有涉及的区块是否已加载
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.blockExists(cx << 4, 64, cz << 4)) {
                    return new CaptureResult(false, "chunk_not_loaded", null);
                }
            }
        }

        String safeName = (name != null) ? name.trim() : "";
        if (safeName.isEmpty()) {
            return new CaptureResult(false, "empty_name", null);
        }
        if (safeName.length() > 64) {
            safeName = safeName.substring(0, 64);
        }

        // 基准原点为选区起点 (x1, y1, z1)
        int originX = x1;
        int originY = y1;
        int originZ = z1;

        List<BlueprintBlockEntry> blockEntries = new ArrayList<BlueprintBlockEntry>();
        List<BlueprintPartEntry> partEntries = new ArrayList<BlueprintPartEntry>();

        VanillaBlueprintAdapter vanillaAdapter = VanillaBlueprintAdapter.INSTANCE;

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    Block block = world.getBlock(x, y, z);
                    if (block == null || block == Blocks.air) {
                        continue;
                    }
                    if (vanillaAdapter.isIllegalBlock(block)) {
                        return new CaptureResult(false, "illegal_block_detected", null);
                    }
                    int dx = x - originX;
                    int dy = y - originY;
                    int dz = z - originZ;
                    TileEntity te = world.getTileEntity(x, y, z);
                    // GT 覆盖板是独立物料，但宿主机器仍必须作为方块条目保存。
                    partEntries.addAll(BlueprintGregTechSupport.captureCovers(te, dx, dy, dz));
                    // AE2 总线方块只承载部件，本身不重复记物料。
                    if (BlueprintPartSupport.isPartHost(te)) {
                        partEntries.addAll(BlueprintPartSupport.captureParts(te, dx, dy, dz));
                        continue;
                    }
                    BlueprintBlockEntry entry = vanillaAdapter.capture(world, x, y, z, block, te);
                    if (entry != null) {
                        BlueprintBlockEntry offsetEntry = new BlueprintBlockEntry(
                                dx, dy, dz, entry.getBlockRegistryName(), entry.getBlock(),
                                entry.getMetadata(), entry.getTileTag(), entry.getAdapterId(),
                                entry.getRequiredMaterials()
                        );
                        blockEntries.add(offsetEntry);
                    }
                }
            }
        }

        UUID authorUuid = player.getUniqueID();
        String bpId = UUID.randomUUID().toString();
        BlueprintData blueprint = new BlueprintData(
                bpId, safeName, authorUuid,
                sizeX, sizeY, sizeZ,
                0, 0, 0,
                System.currentTimeMillis(),
                blockEntries, partEntries
        );

        if (blueprint.isAllAir() || blueprint.isEmpty()) {
            return new CaptureResult(false, "all_air_or_empty", null);
        }

        return new CaptureResult(true, "ok", blueprint);
    }
}
