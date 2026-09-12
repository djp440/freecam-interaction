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
        if (player.isSneaking()) {
            if (!world.isRemote) {
                player.openGui(local.freecaminteraction.FreecamInteractionMod.instance,
                        local.freecaminteraction.FreecamWandRegistry.GUI_WAND_UPGRADE,
                        world, player.inventory.currentItem, 0, 0);
            }
            return stack;
        }
        if (world.isRemote) {
            local.freecaminteraction.client.FreecamClient.toggleFromItem();
        }
        return stack;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                player.openGui(local.freecaminteraction.FreecamInteractionMod.instance,
                        local.freecaminteraction.FreecamWandRegistry.GUI_WAND_UPGRADE,
                        world, player.inventory.currentItem, 0, 0);
            }
            return true;
        }
        if (world.isRemote) {
            local.freecaminteraction.client.FreecamClient.toggleFromItem();
        }
        return true;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                player.openGui(local.freecaminteraction.FreecamInteractionMod.instance,
                        local.freecaminteraction.FreecamWandRegistry.GUI_WAND_UPGRADE,
                        world, player.inventory.currentItem, 0, 0);
            }
            return !world.isRemote;
        }
        if (world.isRemote) {
            local.freecaminteraction.client.FreecamClient.toggleFromItem();
        }
        return true;
    }

    /**
     * 判断指定法杖是否安装有蓝图核心。
     */
    public static boolean hasBlueprintCore(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemFreecamWand)) return false;
        for (int i = 0; i < 4; i++) {
            ItemStack core = getUpgradeCore(stack, i);
            if (core != null && core.getItem() instanceof ItemBlueprintCore) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取单把法杖的移速核心倍率。非法重复 NBT 取最高档，不叠加。
     */
    public static int getSpeedMultiplier(ItemStack stack) {
        int multiplier = 1;
        if (stack == null || !(stack.getItem() instanceof ItemFreecamWand)) return multiplier;
        for (ItemStack core : loadUpgrades(stack)) {
            if (core != null && core.getItem() instanceof ItemSpeedCore) {
                multiplier = Math.max(multiplier, ((ItemSpeedCore) core.getItem()).cameraMultiplier);
            }
        }
        return multiplier;
    }

    /** 主背包 0..35 中所有法杖取最高移速档；不检查耐久。 */
    public static int getInventorySpeedMultiplier(EntityPlayer player) {
        int multiplier = 1;
        if (player == null || player.inventory == null || player.inventory.mainInventory == null) return multiplier;
        int limit = Math.min(36, player.inventory.mainInventory.length);
        for (int i = 0; i < limit; i++) {
            multiplier = Math.max(multiplier, getSpeedMultiplier(player.inventory.mainInventory[i]));
        }
        return multiplier;
    }

    public static double getPlayerSpeedMultiplier(int cameraMultiplier) {
        return cameraMultiplier >= 4 ? 1.5D : cameraMultiplier >= 2 ? 1.25D : 1.0D;
    }

    /**
     * 获取法杖指定升级槽位（0..3）中的核心物品。
     */
    public static ItemStack getUpgradeCore(ItemStack stack, int slot) {
        if (stack == null || slot < 0 || slot >= 4) return null;
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey("Upgrades", 9)) {
            return null;
        }
        net.minecraft.nbt.NBTTagList list = stack.getTagCompound().getTagList("Upgrades", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            net.minecraft.nbt.NBTTagCompound itemTag = list.getCompoundTagAt(i);
            int s = itemTag.getByte("Slot") & 0xFF;
            if (s == slot) {
                return ItemStack.loadItemStackFromNBT(itemTag);
            }
        }
        return null;
    }

    /**
     * 从法杖 NBT 加载 4 个升级槽位数据。
     */
    public static ItemStack[] loadUpgrades(ItemStack stack) {
        ItemStack[] cores = new ItemStack[4];
        if (stack == null || !stack.hasTagCompound() || !stack.getTagCompound().hasKey("Upgrades", 9)) {
            return cores;
        }
        net.minecraft.nbt.NBTTagList list = stack.getTagCompound().getTagList("Upgrades", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            net.minecraft.nbt.NBTTagCompound itemTag = list.getCompoundTagAt(i);
            int s = itemTag.getByte("Slot") & 0xFF;
            if (s >= 0 && s < 4) {
                cores[s] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }
        return cores;
    }

    /**
     * 将 4 个升级槽位数据保存至法杖 NBT。
     */
    public static void saveUpgrades(ItemStack stack, net.minecraft.inventory.IInventory inventory) {
        if (stack == null || inventory == null) return;
        net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new net.minecraft.nbt.NBTTagCompound();
            stack.setTagCompound(tag);
        }
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i < 4 && i < inventory.getSizeInventory(); i++) {
            ItemStack core = inventory.getStackInSlot(i);
            if (core != null) {
                net.minecraft.nbt.NBTTagCompound itemTag = new net.minecraft.nbt.NBTTagCompound();
                itemTag.setByte("Slot", (byte) i);
                core.writeToNBT(itemTag);
                list.appendTag(itemTag);
            }
        }
        if (list.tagCount() > 0) {
            tag.setTag("Upgrades", list);
        } else {
            tag.removeTag("Upgrades");
            if (tag.hasNoTags()) {
                stack.setTagCompound(null);
            }
        }
    }

    /**
     * 设置法杖指定升级槽位（0..3）中的核心物品（用于测试或快捷操作）。
     */
    public static void setUpgradeCore(ItemStack stack, int slot, ItemStack core) {
        if (stack == null || slot < 0 || slot >= 4) return;
        ItemStack[] cores = loadUpgrades(stack);
        cores[slot] = core;
        net.minecraft.inventory.InventoryBasic inv = new net.minecraft.inventory.InventoryBasic("tmp", false, 4);
        for (int i = 0; i < 4; i++) {
            inv.setInventorySlotContents(i, cores[i]);
        }
        saveUpgrades(stack, inv);
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
        ItemStack[] cores = loadUpgrades(stack);
        int count = 0;
        for (ItemStack c : cores) { if (c != null) count++; }
        tooltip.add("\u00A7e" + StatCollector.translateToLocal("gui.freecam_interaction.wand_upgrade.slots") + ": \u00A7f" + count + " / 4");
        for (int i = 0; i < 4; i++) {
            if (cores[i] != null) {
                tooltip.add("  \u00A77- " + cores[i].getDisplayName());
            }
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
