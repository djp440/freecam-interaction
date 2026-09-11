package local.freecaminteraction.blueprint;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import local.freecaminteraction.ModLog;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/** GregTech 5 机器朝向与覆盖板支持；不引入任何 GT 编译期依赖。 */
public final class BlueprintGregTechSupport {
    public static final String FRONT_FACING_KEY = "freecamGtFrontFacing";
    public static final String COVER_DATA_KEY = "coverData";
    public static final String COVER_PART_PREFIX = "gregtech-cover:";

    private static boolean initDone;
    private static boolean available;
    private static Class<?> clsGregTechTile;
    private static Class<?> clsCoverable;
    private static Method mGetFrontFacing;
    private static Method mIsValidFacing;
    private static Method mSetFrontFacing;
    private static Method mGetCoverItem;
    private static Method mGetCoverData;
    private static Method mCanPlaceCoverItem;
    private static Method mSetCoverItem;
    private static Method mSetCoverData;
    private static Method mIssueCoverUpdate;

    private BlueprintGregTechSupport() {}

    private static synchronized void init() {
        if (initDone) return;
        initDone = true;
        try {
            clsGregTechTile = Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
            clsCoverable = Class.forName("gregtech.api.interfaces.tileentity.ICoverable");
            mGetFrontFacing = clsGregTechTile.getMethod("getFrontFacing");
            mIsValidFacing = clsGregTechTile.getMethod("isValidFacing", byte.class);
            mSetFrontFacing = clsGregTechTile.getMethod("setFrontFacing", byte.class);
            mGetCoverItem = clsCoverable.getMethod("getCoverItemAtSide", byte.class);
            mGetCoverData = clsCoverable.getMethod("getCoverDataAtSide", byte.class);
            mCanPlaceCoverItem = clsCoverable.getMethod("canPlaceCoverItemAtSide", byte.class, ItemStack.class);
            mSetCoverItem = clsCoverable.getMethod("setCoverItemAtSide", byte.class, ItemStack.class);
            mSetCoverData = clsCoverable.getMethod("setCoverDataAtSide", byte.class, int.class);
            mIssueCoverUpdate = clsCoverable.getMethod("issueCoverUpdate", byte.class);
            available = true;
        } catch (Throwable t) {
            available = false;
            ModLog.info("BlueprintGregTechSupport: GT API unavailable, support disabled (" + t + ")");
        }
    }

    public static boolean isGregTechTileEntity(Object te) {
        init();
        return available && te != null && clsGregTechTile.isInstance(te);
    }

    public static boolean isCoverEntry(BlueprintPartEntry part) {
        return part != null && part.getPartId().startsWith(COVER_PART_PREFIX);
    }

    /** 返回 0..5 的源机器正面；非 GT、管道或异常返回 -1。 */
    public static int captureFrontFacing(Object te) {
        if (!isGregTechTileEntity(te)) return -1;
        try {
            Object value = mGetFrontFacing.invoke(te);
            int facing = value instanceof Number ? ((Number) value).intValue() : -1;
            return facing >= 0 && facing < 6 ? facing : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    public static boolean applyFrontFacing(Object te, int facing) {
        if (!isGregTechTileEntity(te) || facing < 0 || facing >= 6) return false;
        try {
            if (!Boolean.TRUE.equals(mIsValidFacing.invoke(te, Byte.valueOf((byte) facing)))) return false;
            mSetFrontFacing.invoke(te, Byte.valueOf((byte) facing));
            boolean applied = captureFrontFacing(te) == facing;
            if (applied && te instanceof TileEntity) ((TileEntity) te).markDirty();
            return applied;
        } catch (Throwable t) {
            ModLog.info("BlueprintGregTechSupport: failed to apply facing " + facing + " to "
                    + te.getClass().getName() + ": " + t);
            return false;
        }
    }

    public static boolean matchesFrontFacing(Object te, NBTTagCompound config) {
        return config != null && config.hasKey(FRONT_FACING_KEY)
                && captureFrontFacing(te) == config.getInteger(FRONT_FACING_KEY);
    }

    /** 六面覆盖板转换为部件条目；物料仅保存注册名与 damage，不持久化运行时数字 ID。 */
    public static List<BlueprintPartEntry> captureCovers(Object te, int dx, int dy, int dz) {
        init();
        if (!available || te == null || !clsCoverable.isInstance(te)) return Collections.emptyList();
        List<BlueprintPartEntry> result = new ArrayList<BlueprintPartEntry>();
        try {
            for (int side = 0; side < 6; side++) {
                ItemStack stack = coverAt(te, side);
                if (stack == null || stack.getItem() == null) continue;
                NBTTagCompound config = new NBTTagCompound();
                config.setInteger(COVER_DATA_KEY,
                        ((Number) mGetCoverData.invoke(te, Byte.valueOf((byte) side))).intValue());
                String itemName = MaterialRequirement.resolveItemName(stack.getItem());
                MaterialRequirement material = new MaterialRequirement(itemName, stack.getItemDamage(), null, 1);
                result.add(new BlueprintPartEntry(dx, dy, dz, side,
                        COVER_PART_PREFIX + itemName + ":" + stack.getItemDamage(), config, material));
            }
        } catch (Throwable t) {
            ModLog.info("BlueprintGregTechSupport: failed to capture covers from "
                    + te.getClass().getName() + ": " + t);
            return Collections.emptyList();
        }
        return result;
    }

    public static boolean matchesCover(Object te, BlueprintPartEntry part) {
        if (!sameCoverType(te, part)) return false;
        try {
            return ((Number) mGetCoverData.invoke(te, Byte.valueOf((byte) part.getSide()))).intValue()
                    == coverData(part);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 已有同类型覆盖板只校正静态数据，不重复消耗覆盖板。 */
    public static boolean reconcileExistingCover(Object te, BlueprintPartEntry part) {
        if (!sameCoverType(te, part)) return false;
        try {
            byte side = (byte) part.getSide();
            if (((Number) mGetCoverData.invoke(te, Byte.valueOf(side))).intValue() != coverData(part)) {
                mSetCoverData.invoke(te, Byte.valueOf(side), Integer.valueOf(coverData(part)));
                mIssueCoverUpdate.invoke(te, Byte.valueOf(side));
                if (te instanceof TileEntity) ((TileEntity) te).markDirty();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean installCover(Object te, BlueprintPartEntry part) {
        init();
        if (!available || te == null || !clsCoverable.isInstance(te) || !isCoverEntry(part)
                || part.getSide() < 0 || part.getSide() >= 6) return false;
        if (reconcileExistingCover(te, part)) return true;
        try {
            byte side = (byte) part.getSide();
            if (coverAt(te, side) != null) return false;
            ItemStack stack = part.getRequiredItemStack();
            if (stack == null || stack.getItem() == null
                    || !Boolean.TRUE.equals(mCanPlaceCoverItem.invoke(te, Byte.valueOf(side), stack))) return false;
            stack.stackSize = 1;
            mSetCoverItem.invoke(te, Byte.valueOf(side), stack);
            if (!sameCoverType(te, part)) return false;
            mSetCoverData.invoke(te, Byte.valueOf(side), Integer.valueOf(coverData(part)));
            mIssueCoverUpdate.invoke(te, Byte.valueOf(side));
            if (te instanceof TileEntity) ((TileEntity) te).markDirty();
            return matchesCover(te, part);
        } catch (Throwable t) {
            ModLog.info("BlueprintGregTechSupport: failed to install " + part.getPartId()
                    + " on side " + part.getSide() + ": " + t);
            return false;
        }
    }

    private static boolean sameCoverType(Object te, BlueprintPartEntry part) {
        init();
        if (!available || te == null || !clsCoverable.isInstance(te) || !isCoverEntry(part)
                || part.getSide() < 0 || part.getSide() >= 6) return false;
        ItemStack existing = coverAt(te, part.getSide());
        MaterialRequirement expected = part.getRequiredMaterial();
        return existing != null && expected != null && expected.matches(existing);
    }

    private static ItemStack coverAt(Object te, int side) {
        try {
            Object value = mGetCoverItem.invoke(te, Byte.valueOf((byte) side));
            return value instanceof ItemStack ? (ItemStack) value : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int coverData(BlueprintPartEntry part) {
        NBTTagCompound config = part.getConfigTag();
        return config != null && config.hasKey(COVER_DATA_KEY) ? config.getInteger(COVER_DATA_KEY) : 0;
    }
}
