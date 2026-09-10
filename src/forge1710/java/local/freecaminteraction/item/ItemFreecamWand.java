package local.freecaminteraction.item;

import java.util.List;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import local.freecaminteraction.WandTier;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public class ItemFreecamWand extends Item {
    public static final class WandEntry {
        public final int slot;
        public final ItemStack stack;
        public final WandTier tier;

        public WandEntry(int slot, ItemStack stack, WandTier tier) {
            this.slot = slot;
            this.stack = stack;
            this.tier = tier;
        }

        public int remainingDurability() {
            if (tier.maxDamage <= 0) return Integer.MAX_VALUE;
            return tier.maxDamage - stack.getItemDamage();
        }
    }

    public final WandTier tier;

    public ItemFreecamWand(WandTier tier) {
        this.tier = tier;
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
        setUnlocalizedName("freecam_interaction.wand_" + tier.id);
        setTextureName("freecam_interaction:wand_" + tier.id);
        if (tier.maxDamage > 0) {
            setMaxDamage(tier.maxDamage);
        }
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        if (repair == null) return false;
        if (tier == WandTier.NORMAL) {
            return repair.getItem() == Items.diamond;
        }
        if (tier == WandTier.ADVANCED) {
            return repair.getItem() == Item.getItemFromBlock(Blocks.diamond_block);
        }
        return false;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            local.freecaminteraction.client.FreecamClient.toggleFromItem();
        }
        return stack;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            local.freecaminteraction.client.FreecamClient.toggleFromItem();
        }
        return true;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            local.freecaminteraction.client.FreecamClient.toggleFromItem();
        }
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        String tierName = StatCollector.translateToLocal("item.freecam_interaction.wand_" + tier.id + ".name");
        String rangeName = StatCollector.translateToLocal("item.freecam_interaction.wand_" + tier.id + ".range");
        tooltip.add("\u00A76" + StatCollector.translateToLocal("tooltip.freecam_interaction.tier") + ": \u00A7f" + tierName);
        tooltip.add("\u00A7b" + StatCollector.translateToLocal("tooltip.freecam_interaction.range") + ": \u00A7f" + rangeName);
        if (tier.maxDamage > 0) {
            int remaining = Math.max(1, tier.maxDamage - stack.getItemDamage());
            tooltip.add("\u00A7a" + StatCollector.translateToLocal("tooltip.freecam_interaction.durability")
                    + ": \u00A7f" + remaining + " / " + tier.maxDamage);
        } else {
            tooltip.add("\u00A7d" + StatCollector.translateToLocal("tooltip.freecam_interaction.infinite"));
        }
    }

    /**
     * 在主背包（0..35）中按 Creative > Advanced > Normal 顺序扫描可用法杖。
     * 同级按槽位先后顺序排序。剩余耐久 <= 1 的法杖不可用。
     */
    public static WandEntry findBestWand(EntityPlayer player) {
        if (player == null || player.inventory == null) return null;
        WandEntry best = null;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof ItemFreecamWand) {
                ItemFreecamWand wand = (ItemFreecamWand) stack.getItem();
                int remaining = wand.tier.maxDamage <= 0 ? Integer.MAX_VALUE : (wand.tier.maxDamage - stack.getItemDamage());
                if (remaining <= 1) continue; // 剩余耐久 <= 1 不可用

                WandEntry current = new WandEntry(i, stack, wand.tier);
                if (best == null || isHigherPriority(current, best)) {
                    best = current;
                }
            }
        }
        return best;
    }

    private static boolean isHigherPriority(WandEntry a, WandEntry b) {
        if (a.tier.ordinal() > b.tier.ordinal()) return true;
        if (a.tier.ordinal() < b.tier.ordinal()) return false;
        return a.slot < b.slot;
    }
}
