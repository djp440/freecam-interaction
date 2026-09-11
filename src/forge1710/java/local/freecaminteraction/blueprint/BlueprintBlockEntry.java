package local.freecaminteraction.blueprint;

import cpw.mods.fml.common.registry.GameRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 蓝图方块条目模型。
 * 记录相对原点坐标、方块注册名与引用、metadata、静态/配置 NBT、所属适配器与所需物料。
 */
public final class BlueprintBlockEntry {
    private final int dx;
    private final int dy;
    private final int dz;
    private final String blockRegistryName;
    private final Block block;
    private final int metadata;
    private final NBTTagCompound tileTag;
    private final String adapterId;
    private final List<MaterialRequirement> requiredMaterials;

    public BlueprintBlockEntry(int dx, int dy, int dz, String blockRegistryName, Block block,
                              int metadata, NBTTagCompound tileTag, String adapterId,
                              List<MaterialRequirement> requiredMaterials) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.blockRegistryName = blockRegistryName != null ? blockRegistryName : "";
        this.block = block;
        this.metadata = metadata;
        this.tileTag = tileTag != null ? (NBTTagCompound) tileTag.copy() : null;
        this.adapterId = adapterId != null ? adapterId : "vanilla";
        if (requiredMaterials != null && !requiredMaterials.isEmpty()) {
            this.requiredMaterials = Collections.unmodifiableList(new ArrayList<MaterialRequirement>(requiredMaterials));
        } else {
            this.requiredMaterials = Collections.emptyList();
        }
    }

    public BlueprintBlockEntry(int dx, int dy, int dz, Block block, int metadata,
                              NBTTagCompound tileTag, String adapterId,
                              List<MaterialRequirement> requiredMaterials) {
        this(dx, dy, dz,
             block != null && Block.blockRegistry.getNameForObject(block) != null
                 ? Block.blockRegistry.getNameForObject(block)
                 : "",
             block, metadata, tileTag, adapterId, requiredMaterials);
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

    public String getBlockRegistryName() {
        return blockRegistryName;
    }

    public Block getBlock() {
        if (block != null) {
            return block;
        }
        if (blockRegistryName != null && !blockRegistryName.isEmpty()) {
            return (Block) Block.blockRegistry.getObject(blockRegistryName);
        }
        return null;
    }

    public int getMetadata() {
        return metadata;
    }

    public NBTTagCompound getTileTag() {
        return tileTag != null ? (NBTTagCompound) tileTag.copy() : null;
    }

    public String getAdapterId() {
        return adapterId;
    }

    public List<MaterialRequirement> getRequiredMaterials() {
        return requiredMaterials;
    }

    public List<ItemStack> getRequiredItemStacks() {
        if (requiredMaterials.isEmpty()) {
            return Collections.emptyList();
        }
        List<ItemStack> stacks = new ArrayList<ItemStack>(requiredMaterials.size());
        for (MaterialRequirement req : requiredMaterials) {
            ItemStack sample = req.createSampleStack(req.getCount());
            if (sample != null) {
                stacks.add(sample);
            }
        }
        return Collections.unmodifiableList(stacks);
    }

    public boolean isAir() {
        Block b = getBlock();
        if (b != null) {
            if (b == net.minecraft.init.Blocks.air) return true;
            try {
                return b.isAir(null, 0, 0, 0);
            } catch (Throwable ignored) {
                return b.getMaterial() == net.minecraft.block.material.Material.air;
            }
        }
        if (blockRegistryName == null || blockRegistryName.isEmpty()) return true;
        return "minecraft:air".equals(blockRegistryName);
    }

    public boolean matches(BlueprintBlockEntry other) {
        if (other == null) return false;
        if (dx != other.dx || dy != other.dy || dz != other.dz) return false;
        if (!Objects.equals(blockRegistryName, other.blockRegistryName)) return false;
        if (metadata != other.metadata) return false;
        if (tileTag == null || tileTag.hasNoTags()) {
            return other.tileTag == null || other.tileTag.hasNoTags();
        }
        return tileTag.equals(other.tileTag);
    }
}
