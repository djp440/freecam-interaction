package local.freecaminteraction.blueprint;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 蓝图方块与结构适配器接口。
 * 规范方块采集过滤、所需物料提取、可放置性判定、真实世界安全放置以及世界匹配校验。
 */
public interface IBlueprintAdapter {

    /**
     * 适配器唯一标识（例如 "vanilla", "appliedenergistics2", "gregtech"）。
     */
    String getAdapterId();

    /**
     * 判断当前适配器是否能够处理指定位置的方块及 TileEntity。
     */
    boolean canHandle(World world, int x, int y, int z, Block block, TileEntity te);

    /**
     * 从世界中采集并构建该位置的安全配置条目。
     * 必须剔除库存物品、流体、电量和动态进度，仅保留安全静态配置。
     */
    BlueprintBlockEntry capture(World world, int x, int y, int z, Block block, TileEntity te);

    /**
     * 获取复建该条目所需的物料需求清单。
     */
    List<MaterialRequirement> getRequiredMaterials(BlueprintBlockEntry entry);

    /**
     * 校验在世界指定坐标是否可以安全放置该蓝图条目。
     */
    boolean canPlace(World world, int x, int y, int z, BlueprintBlockEntry entry);

    /**
     * 在世界中执行方块及配置的安全放置与还原。
     */
    boolean place(World world, int x, int y, int z, BlueprintBlockEntry entry, EntityPlayer player);

    /**
     * 校验世界中指定位置的实际方块、元数据及安全静态配置是否已与蓝图条目完全一致。
     */
    boolean matches(World world, int x, int y, int z, BlueprintBlockEntry entry);
}
