package local.freecaminteraction.blueprint;

import cpw.mods.fml.common.registry.GameRegistry;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import local.freecaminteraction.ModLog;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 针对 Minecraft 原版方块及 TileEntity 的默认蓝图适配器。
 * <p>
 * 规则与规范：
 * 1. 禁止/拒绝非法与危险方块（基岩、地狱传送门、末地传送门、末地传送门门框、末地传送带、屏障等）。
 * 2. 处理原版普通方块及容器（箱子、熔炉、发射器、投掷器、漏斗、酿造台等）：
 *    严格剔除 "Items", "ItemsList" 等库存数据，以及流体、能量、运行进度；保留朝向、自定义命名等安全静态配置。
 * 3. 处理原版告示牌、头颅、旗帜/花盆等：保留文字、旋转朝向、颅骨类型等安全配置，滤除坐标 x, y, z 等世界位置键。
 * 4. 提取精确所需物料清单（通过 getPickBlock / 方块掉落映射）。
 * 5. 安全还原方块与 TileEntity 配置。
 */
public class VanillaBlueprintAdapter implements IBlueprintAdapter {
    public static final VanillaBlueprintAdapter INSTANCE = new VanillaBlueprintAdapter();

    private static final Set<Block> ILLEGAL_BLOCKS = new HashSet<Block>(Arrays.asList(
            Blocks.bedrock,
            Blocks.portal,
            Blocks.end_portal,
            Blocks.end_portal_frame,
            Blocks.command_block
    ));

    private static final Set<String> ILLEGAL_NAMES = new HashSet<String>(Arrays.asList(
            "minecraft:bedrock",
            "minecraft:portal",
            "minecraft:end_portal",
            "minecraft:end_portal_frame",
            "minecraft:command_block"
    ));

    // 需要严格剔除的库存、进度、能源、流体和位置相关键名
    private static final Set<String> UNSAFE_KEYS = new HashSet<String>(Arrays.asList(
            "Items",
            "items",
            "Inventory",
            "inventory",
            "Item",
            "BurnTime",
            "CookTime",
            "CookTimeTotal",
            "BrewTime",
            "TransferCooldown",
            "Energy",
            "energy",
            "Fluid",
            "fluid",
            "x",
            "y",
            "z"
    ));

    // 允许安全保留的原版 TileEntity 字段白名单（针对有 TileEntity 的原版方块）
    private static final Set<String> SAFE_CONTAINER_KEYS = new HashSet<String>(Arrays.asList(
            "CustomName",
            "Lock",
            "facing",
            "Facing",
            "Rot",
            "SkullType",
            "ExtraType",
            "Text1",
            "Text2",
            "Text3",
            "Text4",
            "note",
            "Record",
            "RecordItem",
            "Flower",
            "Data",
            BlueprintGregTechSupport.FRONT_FACING_KEY
    ));

    @Override
    public String getAdapterId() {
        return "vanilla";
    }

    @Override
    public boolean canHandle(World world, int x, int y, int z, Block block, TileEntity te) {
        if (block == null || block == Blocks.air) {
            return false;
        }
        if (isIllegalBlock(block)) {
            return false;
        }
        // 当 TileEntity 为空时，原版适配器可处理任意普通方块
        if (te == null) {
            return true;
        }
        // 若有 TileEntity，检查是否为原版 TileEntity
        String teClass = te.getClass().getName();
        return teClass.startsWith("net.minecraft.tileentity.");
    }

    public boolean isIllegalBlock(Block block) {
        if (block == null) return false;
        if (ILLEGAL_BLOCKS.contains(block)) return true;
        String name = Block.blockRegistry.getNameForObject(block);
        return name != null && ILLEGAL_NAMES.contains(name);
    }

    public boolean isIllegalBlockName(String registryName) {
        return registryName != null && ILLEGAL_NAMES.contains(registryName);
    }

    @Override
    public BlueprintBlockEntry capture(World world, int x, int y, int z, Block block, TileEntity te) {
        if (block == null || block == Blocks.air || isIllegalBlock(block)) {
            return null;
        }
        int meta = world.getBlockMetadata(x, y, z);
        String regName = Block.blockRegistry.getNameForObject(block);
        if (regName == null) {
            regName = "";
        }

        NBTTagCompound safeTileTag = null;
        if (te != null) {
            // GT 只保存 Mod 自有的静态朝向键，绝不把残缺机器 NBT 交给 readFromNBT。
            if (BlueprintGregTechSupport.isGregTechTileEntity(te)) {
                int frontFacing = BlueprintGregTechSupport.captureFrontFacing(te);
                if (frontFacing >= 0) {
                    safeTileTag = new NBTTagCompound();
                    safeTileTag.setInteger(BlueprintGregTechSupport.FRONT_FACING_KEY, frontFacing);
                }
            } else {
                NBTTagCompound rawTag = new NBTTagCompound();
                te.writeToNBT(rawTag);
                safeTileTag = sanitizeTileTag(rawTag);
            }
        }

        List<MaterialRequirement> materials = resolveMaterials(world, x, y, z, block, meta, te);
        return new BlueprintBlockEntry(0, 0, 0, regName, block, meta, safeTileTag, getAdapterId(), materials);
    }

    /**
     * 严格过滤并消毒 TileEntity NBT。
     */
    public NBTTagCompound sanitizeTileTag(NBTTagCompound raw) {
        if (raw == null || raw.hasNoTags()) {
            return null;
        }
        NBTTagCompound clean = new NBTTagCompound();
        String id = raw.getString("id");
        if (id != null && !id.isEmpty()) {
            clean.setString("id", id);
        }

        boolean hasSafeConfiguration = false;
        for (Object rawKey : raw.func_150296_c()) {
            String key = (String) rawKey;
            if ("id".equals(key)) continue;
            if (UNSAFE_KEYS.contains(key)) continue;
            if (SAFE_CONTAINER_KEYS.contains(key)) {
                clean.setTag(key, raw.getTag(key).copy());
                hasSafeConfiguration = true;
            }
        }
        // 只有类型 id 的不完整 NBT 不能回灌；GT 等模组机器会因此丢失机器 ID 并变成错误方块。
        return hasSafeConfiguration ? clean : null;
    }

    protected List<MaterialRequirement> resolveMaterials(World world, int x, int y, int z, Block block, int meta, TileEntity te) {
        if (block == null || block == Blocks.air || isIllegalBlock(block)) {
            return Collections.emptyList();
        }
        // 部分模组机器（如 GregTech）的世界 metadata 只是基型编号，不含机器身份；
        // 物品堆的 damage 才是机器 ID（放置时凭它创建元数据实体）。优先从 TileEntity 鸭子类型补正。
        int itemIdOverride = teItemIdOverride(te);
        ItemStack pickStack = null;
        try {
            pickStack = block.getPickBlock(null, world, x, y, z, null);
        } catch (Throwable ignored) {
            // 某些特殊方块在 fake/null 玩家下调用 getPickBlock 可能抛出异常，进入回退
        }
        if (pickStack == null || pickStack.getItem() == null) {
            Item item = Item.getItemFromBlock(block);
            if (item != null) {
                int damage = itemIdOverride >= 0 ? itemIdOverride : block.damageDropped(meta);
                pickStack = new ItemStack(item, 1, damage);
            }
        }
        if (pickStack != null && itemIdOverride >= 0) {
            pickStack.setItemDamage(itemIdOverride);
        }
        if (pickStack != null && pickStack.getItem() != null) {
            return Collections.singletonList(new MaterialRequirement(pickStack, 1));
        }
        return Collections.emptyList();
    }

    /**
     * 鸭子类型读取 TileEntity 上的 {@code getMetaTileID()}（GregTech 等元数据机器的机器 ID）。
     * 不存在时返回 -1，不引用任何模组类，避免编译期依赖。
     */
    public static int teItemIdOverride(TileEntity te) {
        if (te == null) {
            return -1;
        }
        try {
            Method m = te.getClass().getMethod("getMetaTileID");
            m.setAccessible(true); // 实现类可能是包私有（如匿名/内部类），反射调用前先提权
            Object result = m.invoke(te);
            return (result instanceof Number) ? ((Number) result).intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    @Override
    public List<MaterialRequirement> getRequiredMaterials(BlueprintBlockEntry entry) {
        if (entry == null || entry.isAir()) {
            return Collections.emptyList();
        }
        List<MaterialRequirement> list = entry.getRequiredMaterials();
        if (!list.isEmpty()) {
            return list;
        }
        Block block = entry.getBlock();
        if (block != null && !isIllegalBlock(block)) {
            Item item = Item.getItemFromBlock(block);
            if (item != null) {
                int damage = block.damageDropped(entry.getMetadata());
                return Collections.singletonList(new MaterialRequirement(new ItemStack(item, 1, damage), 1));
            }
        }
        return Collections.emptyList();
    }

    @Override
    public boolean canPlace(World world, int x, int y, int z, BlueprintBlockEntry entry) {
        if (world == null || entry == null || entry.isAir()) {
            return false;
        }
        Block targetBlock = entry.getBlock();
        if (targetBlock == null || isIllegalBlock(targetBlock)) {
            return false;
        }
        if (y < 0 || y >= world.getHeight()) {
            return false;
        }
        // 目标位置若已有方块，检查是否为可替换方块（如空气、高草、雪层等）
        Block existing = world.getBlock(x, y, z);
        return existing == null || existing.isAir(world, x, y, z) || existing.isReplaceable(world, x, y, z);
    }

    @Override
    public boolean place(World world, int x, int y, int z, BlueprintBlockEntry entry, EntityPlayer player) {
        if (!canPlace(world, x, y, z, entry)) {
            return false;
        }
        Block block = entry.getBlock();
        int meta = entry.getMetadata();
        // 模组方块优先走物品自身的放置钩子：GT 等机器依赖 ItemBlock.placeBlockAt 的覆写创建其元数据实体（MTE），
        // 仅 world.setBlock 会留下没有 MTE 的残缺 TileEntity（渲染为黑紫块）。
        // 原版方块保持原路径，避免触发原版 onBlockPlacedBy 的额外行为（例如箱子自动连体）。
        if (!placeModdedViaItemHook(world, x, y, z, entry, player, block, meta)) {
            // 放置方块并触发必要更新（flag = 3: 1=通知邻居, 2=通知客户端）
            if (!world.setBlock(x, y, z, block, meta, 3)) {
                return false;
            }
        }
        applyStaticConfiguration(world, x, y, z, entry);
        return true;
    }

    /** 放置与 FINALIZE_CONFIG 共用的静态配置恢复入口。 */
    public void applyStaticConfiguration(World world, int x, int y, int z, BlueprintBlockEntry entry) {
        NBTTagCompound storedTileTag = entry != null ? entry.getTileTag() : null;
        NBTTagCompound tileTag = sanitizeTileTag(storedTileTag);
        if (storedTileTag != null && tileTag == null) {
            ModLog.info("Skipped incomplete TileEntity config for "
                    + (entry != null ? entry.getBlockRegistryName() : "")
                    + " at (" + x + "," + y + "," + z + ")");
        }
        if (tileTag == null || world == null) return;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) return;
        if (tileTag.hasKey(BlueprintGregTechSupport.FRONT_FACING_KEY)) {
            int facing = tileTag.getInteger(BlueprintGregTechSupport.FRONT_FACING_KEY);
            if (!BlueprintGregTechSupport.applyFrontFacing(te, facing)) {
                ModLog.info("Failed to apply GT facing " + facing + " at (" + x + "," + y + "," + z + ")");
            }
        } else {
            NBTTagCompound working = (NBTTagCompound) tileTag.copy();
            working.setInteger("x", x);
            working.setInteger("y", y);
            working.setInteger("z", z);
            try {
                te.readFromNBT(working);
                te.markDirty();
            } catch (Throwable t) {
                ModLog.info("Failed to apply TileEntity config at (" + x + "," + y + "," + z + "): " + t);
            }
        }
        world.markBlockForUpdate(x, y, z);
    }

    /**
     * 使用物品自身的放置钩子放置模组方块（返回 false 时回退到原版 setBlock 路径）。
     */
    private boolean placeModdedViaItemHook(World world, int x, int y, int z, BlueprintBlockEntry entry,
                                           EntityPlayer player, Block block, int meta) {
        if (player == null || block == null) {
            return false;
        }
        String registryName = entry.getBlockRegistryName();
        if (registryName == null || registryName.startsWith("minecraft:")) {
            return false;
        }
        Item item = Item.getItemFromBlock(block);
        if (!(item instanceof ItemBlock)) {
            return false;
        }
        ItemStack stack = sampleStackFor(entry, item, meta);
        if (stack == null) {
            return false;
        }
        try {
            // 传入水平 side 仅用于完成物品放置；放置钩子后会从蓝图静态配置恢复 GT 正面。
            boolean ok = ((ItemBlock) item).placeBlockAt(stack, player, world, x, y, z, 2, 0.5F, 0.5F, 0.5F, meta);
            ModLog.info("VanillaBlueprintAdapter: modded ItemBlock place " + (ok ? "ok" : "FAILED")
                    + " for " + registryName + "@" + stack.getItemDamage() + " at (" + x + "," + y + "," + z + ")");
            return ok;
        } catch (Throwable t) {
            ModLog.info("VanillaBlueprintAdapter: ItemBlock placement hook failed for " + registryName
                    + " at (" + x + "," + y + "," + z + "): " + t);
            return false;
        }
    }

    /** 放置钩子所需样品物品：优先用采集时记录的实际物料（模组机器的损伤值即其机器 ID）。 */
    private static ItemStack sampleStackFor(BlueprintBlockEntry entry, Item item, int meta) {
        List<MaterialRequirement> materials = entry.getRequiredMaterials();
        if (materials != null) {
            for (MaterialRequirement requirement : materials) {
                ItemStack sample = requirement.createSampleStack(1);
                if (sample != null && sample.getItem() == item) {
                    return sample;
                }
            }
        }
        return new ItemStack(item, 1, meta);
    }

    @Override
    public boolean matches(World world, int x, int y, int z, BlueprintBlockEntry entry) {
        if (world == null || entry == null) {
            return false;
        }
        Block actualBlock = world.getBlock(x, y, z);
        if (entry.isAir()) {
            return actualBlock == null || actualBlock.isAir(world, x, y, z);
        }
        Block expectedBlock = entry.getBlock();
        if (expectedBlock == null || actualBlock != expectedBlock) {
            return false;
        }
        int actualMeta = world.getBlockMetadata(x, y, z);
        if (actualMeta != entry.getMetadata()) {
            return false;
        }
        NBTTagCompound expectedTag = sanitizeTileTag(entry.getTileTag());
        if (expectedTag == null) {
            return true;
        }
        TileEntity actualTe = world.getTileEntity(x, y, z);
        if (actualTe == null) {
            return false;
        }
        if (expectedTag.hasKey(BlueprintGregTechSupport.FRONT_FACING_KEY)) {
            return BlueprintGregTechSupport.matchesFrontFacing(actualTe, expectedTag);
        }
        NBTTagCompound actualRaw = new NBTTagCompound();
        actualTe.writeToNBT(actualRaw);
        NBTTagCompound actualClean = sanitizeTileTag(actualRaw);
        if (actualClean == null) {
            return false;
        }
        // 校验期望的安全配置字段是否完全匹配
        for (Object rawKey : expectedTag.func_150296_c()) {
            String key = (String) rawKey;
            if (!actualClean.hasKey(key)) {
                return false;
            }
            if (!Objects.equals(expectedTag.getTag(key), actualClean.getTag(key))) {
                return false;
            }
        }
        return true;
    }
}
