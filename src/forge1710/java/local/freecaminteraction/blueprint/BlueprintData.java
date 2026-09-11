package local.freecaminteraction.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 蓝图数据根对象。
 * 记录蓝图唯一标识、作者 UUID、长宽高尺寸、基准原点偏移、创建时间、方块条目列表、独立部件列表及物料汇总。
 */
public final class BlueprintData {
    private final String id;
    private final String name;
    private final UUID authorUuid;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int originOffsetX;
    private final int originOffsetY;
    private final int originOffsetZ;
    private final long createdAt;
    private final List<BlueprintBlockEntry> blockEntries;
    private final List<BlueprintPartEntry> partEntries;

    public BlueprintData(String id, String name, UUID authorUuid,
                         int sizeX, int sizeY, int sizeZ,
                         int originOffsetX, int originOffsetY, int originOffsetZ,
                         long createdAt,
                         List<BlueprintBlockEntry> blockEntries,
                         List<BlueprintPartEntry> partEntries) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name != null ? name : "";
        this.authorUuid = authorUuid;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.originOffsetX = originOffsetX;
        this.originOffsetY = originOffsetY;
        this.originOffsetZ = originOffsetZ;
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.blockEntries = blockEntries != null
                ? Collections.unmodifiableList(new ArrayList<BlueprintBlockEntry>(blockEntries))
                : Collections.<BlueprintBlockEntry>emptyList();
        this.partEntries = partEntries != null
                ? Collections.unmodifiableList(new ArrayList<BlueprintPartEntry>(partEntries))
                : Collections.<BlueprintPartEntry>emptyList();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getAuthorUuid() {
        return authorUuid;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public int getOriginOffsetX() {
        return originOffsetX;
    }

    public int getOriginOffsetY() {
        return originOffsetY;
    }

    public int getOriginOffsetZ() {
        return originOffsetZ;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public List<BlueprintBlockEntry> getBlockEntries() {
        return blockEntries;
    }

    public List<BlueprintPartEntry> getPartEntries() {
        return partEntries;
    }

    /**
     * 选区总体积（网格方块格数）。
     */
    public int getTotalVolume() {
        return Math.max(0, sizeX) * Math.max(0, sizeY) * Math.max(0, sizeZ);
    }

    /**
     * 总方块记录条目数。
     */
    public int getTotalBlockCount() {
        return blockEntries.size();
    }

    /**
     * 独立部件总数。
     */
    public int getTotalPartCount() {
        return partEntries.size();
    }

    /**
     * 统计非空气方块条目数量。
     */
    public int getNonAirBlockCount() {
        int count = 0;
        for (BlueprintBlockEntry entry : blockEntries) {
            if (entry != null && !entry.isAir()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 汇总构建整张蓝图所需的全部物料清单。
     * 将方块与独立部件所需的物料同类合并计数。
     */
    public List<MaterialRequirement> getConsolidatedMaterials() {
        List<MaterialRequirement> consolidated = new ArrayList<MaterialRequirement>();
        // 1. 汇总方块需求
        for (BlueprintBlockEntry blockEntry : blockEntries) {
            if (blockEntry == null || blockEntry.isAir()) continue;
            for (MaterialRequirement req : blockEntry.getRequiredMaterials()) {
                mergeRequirement(consolidated, req);
            }
        }
        // 2. 汇总独立部件需求
        for (BlueprintPartEntry partEntry : partEntries) {
            if (partEntry == null) continue;
            MaterialRequirement req = partEntry.getRequiredMaterial();
            if (req != null) {
                mergeRequirement(consolidated, req);
            }
        }
        return Collections.unmodifiableList(consolidated);
    }

    private void mergeRequirement(List<MaterialRequirement> list, MaterialRequirement req) {
        if (req == null || req.getCount() <= 0) return;
        for (MaterialRequirement existing : list) {
            if (existing.isSameType(req)) {
                existing.addCount(req.getCount());
                return;
            }
        }
        list.add(new MaterialRequirement(req.getItemRegistryName(), req.getDamage(),
                                        req.getMatchTag(), req.getCount()));
    }

    /**
     * 校验选区尺寸是否合法（尺寸必须大于 0）。
     */
    public boolean isValidBounds() {
        return sizeX > 0 && sizeY > 0 && sizeZ > 0;
    }

    /**
     * 校验是否为空（无方块条目且无部件条目）。
     */
    public boolean isEmpty() {
        return blockEntries.isEmpty() && partEntries.isEmpty();
    }

    /**
     * 校验是否全部为空气方块（选区内没有任何实体方块或部件）。
     * 按照 PLAN-blueprints-1710.md 规范，全空气蓝图应当被明确拒绝。
     */
    public boolean isAllAir() {
        if (!partEntries.isEmpty()) {
            return false;
        }
        return getNonAirBlockCount() == 0;
    }
}
