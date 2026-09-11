package local.freecaminteraction.blueprint;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import local.freecaminteraction.ModLog;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 模组附属部件支持层（AE2 线缆、线缆锚、面板等附属结构）。
 *
 * 全部通过反射访问模组 API，服务器未安装对应模组时不会产生类加载错误；
 * 采集与安装都复用模组自身 API（IPartHost.getPart/addPart、IBlocks.multiPart 的总线方块定义），
 * 不依赖鼠标点击语义，也不手写绕过模组的兜底放置。
 */
public final class BlueprintPartSupport {

    /** 中心槽位：ForgeDirection.UNKNOWN 的 ordinal，对应线缆本体。 */
    public static final int SIDE_CENTER = 6;

    private static boolean ae2InitDone = false;
    private static boolean ae2Available = false;
    private static Class<?> clsPartHost;
    private static Class<?> clsPart;
    private static Class<?> clsPartItem;
    private static Method mGetPart;
    private static Method mAddPart;
    private static Method mGetItemStack;
    private static Method mMaybeBlock;
    private static Method mMaybeItemBlock;
    private static Method mMaybeStack;
    private static Method mOptionalGet;
    private static Object partItemStackWorld;
    private static Object multiPartDefinition;

    private BlueprintPartSupport() {
    }

    private static synchronized void initAe2() {
        if (ae2InitDone) {
            return;
        }
        ae2InitDone = true;
        try {
            clsPartHost = Class.forName("appeng.api.parts.IPartHost");
            clsPart = Class.forName("appeng.api.parts.IPart");
            clsPartItem = Class.forName("appeng.api.parts.IPartItem");
            Class<?> clsPartItemStack = Class.forName("appeng.api.parts.PartItemStack");
            partItemStackWorld = clsPartItemStack.getField("World").get(null);

            mGetPart = clsPartHost.getMethod("getPart", ForgeDirection.class);
            mAddPart = clsPartHost.getMethod("addPart", ItemStack.class, ForgeDirection.class, EntityPlayer.class);
            mGetItemStack = clsPart.getMethod("getItemStack", clsPartItemStack);

            // 总线方块定义（AE2 自身定义，避免硬编码模组方块类名）
            Object api = Class.forName("appeng.api.AEApi").getMethod("instance").invoke(null);
            Object definitions = Class.forName("appeng.api.IAppEngApi").getMethod("definitions").invoke(api);
            Object blocks = Class.forName("appeng.api.definitions.IDefinitions").getMethod("blocks").invoke(definitions);
            multiPartDefinition = Class.forName("appeng.api.definitions.IBlocks").getMethod("multiPart").invoke(blocks);

            Class<?> clsBlockDefinition = Class.forName("appeng.api.definitions.IBlockDefinition");
            mMaybeBlock = clsBlockDefinition.getMethod("maybeBlock");
            mMaybeItemBlock = clsBlockDefinition.getMethod("maybeItemBlock");
            mMaybeStack = Class.forName("appeng.api.definitions.IItemDefinition").getMethod("maybeStack", int.class);
            mOptionalGet = Class.forName("com.google.common.base.Optional").getMethod("get");

            ae2Available = true;
        } catch (Throwable t) {
            ae2Available = false;
            ModLog.info("BlueprintPartSupport: AE2 API unavailable, part support disabled (" + t + ")");
        }
    }

    /** 目标 TileEntity 是否为可承载部件的宿主（AE2 IPartHost）。 */
    public static boolean isPartHost(TileEntity te) {
        initAe2();
        return ae2Available && te != null && clsPartHost.isInstance(te);
    }

    /**
     * 采集宿主上已安装的全部部件条目（中心槽位 + 六个朝向面）。
     * 无部件或宿主不合法时返回空列表。
     */
    public static List<BlueprintPartEntry> captureParts(TileEntity te, int dx, int dy, int dz) {
        if (!isPartHost(te)) {
            return Collections.emptyList();
        }
        List<BlueprintPartEntry> entries = new ArrayList<BlueprintPartEntry>();
        for (int side = 0; side <= SIDE_CENTER; side++) {
            Object part = getPart(te, side);
            if (part == null) {
                continue;
            }
            ItemStack stack = partItemStack(part);
            if (stack == null || stack.getItem() == null) {
                ModLog.info("BlueprintPartSupport: skip part without item, side=" + side
                        + " host=" + te.getClass().getName());
                continue;
            }
            ItemStack sample = stack.copy();
            sample.stackSize = 1;
            // 部件配置 NBT 暂不采集：AE2 部分部件（如接口）的 NBT 内含物品库存，
            // 按名删键无法保证不复制资源，待逐部件取证后再补配置恢复。
            entries.add(new BlueprintPartEntry(dx, dy, dz, side, partIdOf(sample), null,
                    new MaterialRequirement(sample, 1)));
        }
        return entries;
    }

    /** 目标位置是否已存在同类型部件（施工幂等：已建成则不再扣料安装）。 */
    public static boolean matchesInstalled(World world, int x, int y, int z, BlueprintPartEntry part) {
        if (world == null || part == null) return false;
        TileEntity te = world.getTileEntity(x, y, z);
        if (BlueprintGregTechSupport.isCoverEntry(part)) {
            return BlueprintGregTechSupport.matchesCover(te, part)
                    || BlueprintGregTechSupport.reconcileExistingCover(te, part);
        }
        initAe2();
        if (!ae2Available) {
            return false;
        }
        if (!clsPartHost.isInstance(te)) {
            return false;
        }
        Object existing = getPart(te, part.getSide());
        if (existing == null) {
            return false;
        }
        ItemStack stack = partItemStack(existing);
        return stack != null && partIdOf(stack).equals(part.getPartId());
    }

    /**
     * 在目标位置真实安装部件；返回是否安装成功。
     * 宿主方块缺失时按模组自身定义放置总线方块，再通过 IPartHost.addPart 安装部件。
     */
    public static boolean installPart(World world, int x, int y, int z, BlueprintPartEntry part, EntityPlayer player) {
        if (world == null || part == null) return false;
        TileEntity te = world.getTileEntity(x, y, z);
        if (BlueprintGregTechSupport.isCoverEntry(part)) {
            boolean installed = BlueprintGregTechSupport.installCover(te, part);
            if (installed) world.markBlockForUpdate(x, y, z);
            return installed;
        }
        initAe2();
        if (!ae2Available) {
            return false;
        }
        ItemStack stack = part.getRequiredItemStack();
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        stack.stackSize = 1;

        if (!clsPartHost.isInstance(te)) {
            // 只有 AE2 部件才有资格在空位自建总线宿主（GT 覆盖板等必须已有机器本体）
            if (!isPartItem(stack) || !placeEmptyBus(world, x, y, z, player)) {
                return false;
            }
            te = world.getTileEntity(x, y, z);
            if (!clsPartHost.isInstance(te)) {
                return false;
            }
        }

        ForgeDirection dir = ForgeDirection.getOrientation(part.getSide());
        Object existing = getPart(te, part.getSide());
        if (existing != null) {
            ItemStack existingStack = partItemStack(existing);
            return existingStack != null && partIdOf(existingStack).equals(partIdOf(stack));
        }

        try {
            mAddPart.invoke(te, stack, dir, player);
        } catch (Throwable t) {
            ModLog.info("BlueprintPartSupport: addPart failed at (" + x + "," + y + "," + z + "): " + t);
            return false;
        }

        Object installed = getPart(te, part.getSide());
        if (installed == null) {
            return false;
        }
        ItemStack installedStack = partItemStack(installed);
        return installedStack != null && partIdOf(installedStack).equals(partIdOf(stack));
    }

    /** 部件物料标识（注册名 + 损伤值），用于类型比对与幂等判定。 */
    public static String partIdOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        return MaterialRequirement.resolveItemName(stack.getItem()) + ":" + stack.getItemDamage();
    }

    /** 该部件是否能在空位自建宿主（AE2 部件成立；GT 覆盖板等必须依附已存在的机器本体）。 */
    public static boolean canSelfHost(BlueprintPartEntry part) {
        if (BlueprintGregTechSupport.isCoverEntry(part)) return false;
        initAe2();
        return ae2Available && part != null && isPartItem(part.getRequiredItemStack());
    }

    private static boolean isPartItem(ItemStack stack) {
        try {
            return stack != null && clsPartItem.isInstance(stack.getItem());
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object getPart(TileEntity te, int side) {
        try {
            return mGetPart.invoke(te, ForgeDirection.getOrientation(side));
        } catch (Throwable t) {
            return null;
        }
    }

    private static ItemStack partItemStack(Object part) {
        try {
            Object result = mGetItemStack.invoke(part, partItemStackWorld);
            return result instanceof ItemStack ? (ItemStack) result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 使用模组自身定义的总线方块在空位创建部件宿主。 */
    private static boolean placeEmptyBus(World world, int x, int y, int z, EntityPlayer player) {
        try {
            Object busBlockObj = optionalGet(mMaybeBlock.invoke(multiPartDefinition));
            if (!(busBlockObj instanceof Block)) {
                return false;
            }
            Block busBlock = (Block) busBlockObj;
            if (!busBlock.canPlaceBlockAt(world, x, y, z)) {
                return false;
            }
            if (player != null) {
                Object itemBlockObj = optionalGet(mMaybeItemBlock.invoke(multiPartDefinition));
                Object stackObj = optionalGet(mMaybeStack.invoke(multiPartDefinition, Integer.valueOf(1)));
                if (itemBlockObj instanceof ItemBlock && stackObj instanceof ItemStack) {
                    // side=0/hit 中心：总线方块自身不带朝向语义
                    if (((ItemBlock) itemBlockObj).placeBlockAt((ItemStack) stackObj, player, world,
                            x, y, z, 0, 0.5F, 0.5F, 0.5F, 0)) {
                        return true;
                    }
                }
            }
            return world.setBlock(x, y, z, busBlock, 0, 3);
        } catch (Throwable t) {
            ModLog.info("BlueprintPartSupport: place bus failed at (" + x + "," + y + "," + z + "): " + t);
            return false;
        }
    }

    private static Object optionalGet(Object optional) throws Exception {
        if (optional == null) {
            return null;
        }
        if (mOptionalGet == null) {
            mOptionalGet = Class.forName("com.google.common.base.Optional").getMethod("get");
        }
        return mOptionalGet.invoke(optional);
    }
}
