package local.freecaminteraction.blueprint;

import cpw.mods.fml.common.registry.GameRegistry;
import java.util.Objects;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 蓝图物料需求模型。
 * 记录构建某一结构或部件所需的物品、元数据/损伤值、关键 NBT 特征及数量统计。
 */
public final class MaterialRequirement {
    public static final int WILDCARD_VALUE = 32767;

    private final String itemRegistryName;
    private final int damage;
    private final NBTTagCompound matchTag;
    private int count;
    private int placedCount;

    public MaterialRequirement(String itemRegistryName, int damage, NBTTagCompound matchTag, int count) {
        this.itemRegistryName = itemRegistryName != null ? itemRegistryName : "";
        this.damage = damage;
        this.matchTag = matchTag != null ? (NBTTagCompound) matchTag.copy() : null;
        this.count = Math.max(0, count);
        this.placedCount = 0;
    }

    public static String resolveItemName(Item item) {
        if (item == null) return "";
        try {
            GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(item);
            if (uid != null) {
                return uid.toString();
            }
        } catch (Throwable ignored) {
        }
        try {
            String name = Item.itemRegistry.getNameForObject(item);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (Throwable ignored) {
        }
        try {
            Block block = Block.getBlockFromItem(item);
            if (block != null) {
                String blockName = Block.blockRegistry.getNameForObject(block);
                if (blockName != null && !blockName.isEmpty()) {
                    return blockName;
                }
            }
        } catch (Throwable ignored) {
        }
        return item.getUnlocalizedName() != null ? item.getUnlocalizedName() : item.getClass().getSimpleName();
    }

    public MaterialRequirement(ItemStack stack, int count) {
        if (stack != null && stack.getItem() != null) {
            this.itemRegistryName = resolveItemName(stack.getItem());
            this.damage = stack.getItemDamage();
            this.matchTag = stack.getTagCompound() != null ? (NBTTagCompound) stack.getTagCompound().copy() : null;
            this.count = Math.max(0, count);
        } else {
            this.itemRegistryName = "";
            this.damage = 0;
            this.matchTag = null;
            this.count = Math.max(0, count);
        }
        this.placedCount = 0;
    }

    public String getItemRegistryName() {
        return itemRegistryName;
    }

    public int getDamage() {
        return damage;
    }

    public NBTTagCompound getMatchTag() {
        return matchTag != null ? (NBTTagCompound) matchTag.copy() : null;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = Math.max(0, count);
    }

    public void addCount(int additional) {
        if (additional > 0) {
            this.count += additional;
        }
    }

    public int getPlacedCount() {
        return placedCount;
    }

    public void setPlacedCount(int placedCount) {
        this.placedCount = Math.max(0, placedCount);
    }

    public void addPlacedCount(int placed) {
        if (placed > 0) {
            this.placedCount += placed;
        }
    }

    public int getRemainingCount() {
        return Math.max(0, count - placedCount);
    }

    public boolean isSatisfied() {
        return placedCount >= count;
    }

    /**
     * 判断玩家背包中的物品堆是否与本物料需求匹配。
     */
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
            return false;
        }
        String stackItemName = resolveItemName(stack.getItem());
        if (!Objects.equals(this.itemRegistryName, stackItemName)) {
            // 如果需求名是方块名，而物品是其对应 ItemBlock，再次尝试比对
            Block reqBlock = (Block) Block.blockRegistry.getObject(this.itemRegistryName);
            if (reqBlock == null || Block.getBlockFromItem(stack.getItem()) != reqBlock) {
                return false;
            }
        }
        if (this.damage != WILDCARD_VALUE && stack.getItemDamage() != WILDCARD_VALUE) {
            if (this.damage != stack.getItemDamage()) {
                return false;
            }
        }
        if (this.matchTag != null && !this.matchTag.hasNoTags()) {
            NBTTagCompound stackTag = stack.getTagCompound();
            if (stackTag == null) {
                return false;
            }
            for (Object rawKey : this.matchTag.func_150296_c()) {
                String key = (String) rawKey;
                if (!stackTag.hasKey(key)) {
                    return false;
                }
                if (!Objects.equals(this.matchTag.getTag(key), stackTag.getTag(key))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 判断两个物料需求项是否属于同一物料类别（用于清单统计合并）。
     */
    public boolean isSameType(MaterialRequirement other) {
        if (other == null) return false;
        if (!Objects.equals(this.itemRegistryName, other.itemRegistryName)) return false;
        if (this.damage != other.damage) return false;
        if (this.matchTag == null || this.matchTag.hasNoTags()) {
            return other.matchTag == null || other.matchTag.hasNoTags();
        }
        return this.matchTag.equals(other.matchTag);
    }

    public ItemStack createSampleStack(int stackSize) {
        if (itemRegistryName == null || itemRegistryName.isEmpty()) return null;
        Item item = (Item) Item.itemRegistry.getObject(itemRegistryName);
        if (item == null) {
            Block block = (Block) Block.blockRegistry.getObject(itemRegistryName);
            if (block != null) {
                item = Item.getItemFromBlock(block);
            }
        }
        if (item == null) return null;
        int d = (damage == WILDCARD_VALUE) ? 0 : damage;
        ItemStack stack = new ItemStack(item, Math.max(1, stackSize), d);
        if (matchTag != null) {
            stack.setTagCompound((NBTTagCompound) matchTag.copy());
        }
        return stack;
    }
}