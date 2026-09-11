package local.freecaminteraction.blueprint;

import cpw.mods.fml.common.registry.GameRegistry;
import java.util.Objects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 独立/附属部件条目模型（例如 AE2 部件、GT 覆盖板、线缆锚等）。
 * 记录相对宿主方块的偏移坐标、附着朝向面、部件类型、配置及物料消耗。
 */
public final class BlueprintPartEntry {
    private final int dx;
    private final int dy;
    private final int dz;
    private final int side; // 附着方向 0..5 (ForgeDirection)
    private final String partId; // 部件类型标识
    private final NBTTagCompound configTag; // 静态配置 NBT
    private final MaterialRequirement requiredMaterial; // 所需消耗物料

    public BlueprintPartEntry(int dx, int dy, int dz, int side, String partId,
                             NBTTagCompound configTag, MaterialRequirement requiredMaterial) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.side = side;
        this.partId = partId != null ? partId : "";
        this.configTag = configTag != null ? (NBTTagCompound) configTag.copy() : null;
        this.requiredMaterial = requiredMaterial;
    }

    public BlueprintPartEntry(int dx, int dy, int dz, int side, String partId,
                             NBTTagCompound configTag, ItemStack requiredItem) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.side = side;
        this.partId = partId != null ? partId : "";
        this.configTag = configTag != null ? (NBTTagCompound) configTag.copy() : null;
        this.requiredMaterial = requiredItem != null ? new MaterialRequirement(requiredItem, requiredItem.stackSize) : null;
    }

    public int getDx() {
        return dx;
    }

    public int getDy() {
        return dy;
    }

    public int getDz() {
        return dz;
    }

    public int getSide() {
        return side;
    }

    public String getPartId() {
        return partId;
    }

    public NBTTagCompound getConfigTag() {
        return configTag != null ? (NBTTagCompound) configTag.copy() : null;
    }

    public MaterialRequirement getRequiredMaterial() {
        return requiredMaterial;
    }

    public ItemStack getRequiredItemStack() {
        return requiredMaterial != null ? requiredMaterial.createSampleStack(requiredMaterial.getCount()) : null;
    }

    public boolean matches(BlueprintPartEntry other) {
        if (other == null) return false;
        if (dx != other.dx || dy != other.dy || dz != other.dz || side != other.side) return false;
        if (!Objects.equals(partId, other.partId)) return false;
        if (configTag == null || configTag.hasNoTags()) {
            return other.configTag == null || other.configTag.hasNoTags();
        }
        return configTag.equals(other.configTag);
    }
}
