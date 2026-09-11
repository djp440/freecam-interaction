package local.freecaminteraction.inventory;

import local.freecaminteraction.item.ItemBlueprintCore;
import local.freecaminteraction.item.ItemFreecamWand;
import local.freecaminteraction.item.IWandCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 法杖核心升级容器。
 * 提供 4 个升级核心槽位，同杖严格禁止重复安装同种核心。
 * 具备防刷防丢安全机制：锁定被编辑法杖槽位、安全快捷键/Shift搬运、关闭及变更安全保存 NBT。
 */
public class ContainerWandUpgrade extends Container {
    public static final int UPGRADE_SLOT_COUNT = 4;
    private final EntityPlayer player;
    private final int wandSlot; // 玩家主背包槽位索引 (0..35)
    private final IInventory upgradeInventory;
    private int lockedContainerSlot = -1;

    public ContainerWandUpgrade(InventoryPlayer playerInventory, int wandSlot) {
        this.player = playerInventory.player;
        this.wandSlot = wandSlot;
        this.upgradeInventory = new InventoryBasic("WandUpgrades", false, UPGRADE_SLOT_COUNT) {
            @Override
            public void markDirty() {
                super.markDirty();
                saveToWand();
            }
        };

        // 从法杖中读取已有升级核心
        ItemStack wand = getWandStack();
        if (wand != null && wand.getItem() instanceof ItemFreecamWand) {
            ItemStack[] existing = ItemFreecamWand.loadUpgrades(wand);
            for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
                if (existing[i] != null) {
                    this.upgradeInventory.setInventorySlotContents(i, existing[i]);
                }
            }
        }

        // 1. 核心升级槽位 (0..3)
        for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
            this.addSlotToContainer(new SlotWandCore(this, this.upgradeInventory, i, 53 + i * 18, 35));
        }

        // 2. 玩家主背包 (槽位 9..35) -> 容器槽位 4..30
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int invIndex = 9 + row * 9 + col;
                int x = 8 + col * 18;
                int y = 84 + row * 18;
                if (invIndex == wandSlot) {
                    SlotLockedWand locked = new SlotLockedWand(playerInventory, invIndex, x, y);
                    this.addSlotToContainer(locked);
                    this.lockedContainerSlot = this.inventorySlots.size() - 1;
                } else {
                    this.addSlotToContainer(new Slot(playerInventory, invIndex, x, y));
                }
            }
        }

        // 3. 玩家快捷栏 (槽位 0..8) -> 容器槽位 31..39
        for (int col = 0; col < 9; col++) {
            int invIndex = col;
            int x = 8 + col * 18;
            int y = 142;
            if (invIndex == wandSlot) {
                SlotLockedWand locked = new SlotLockedWand(playerInventory, invIndex, x, y);
                this.addSlotToContainer(locked);
                this.lockedContainerSlot = this.inventorySlots.size() - 1;
            } else {
                this.addSlotToContainer(new Slot(playerInventory, invIndex, x, y));
            }
        }
    }

    public ItemStack getWandStack() {
        if (wandSlot >= 0 && wandSlot < player.inventory.mainInventory.length) {
            return player.inventory.mainInventory[wandSlot];
        }
        return null;
    }

    public void saveToWand() {
        ItemStack wand = getWandStack();
        if (wand != null && wand.getItem() instanceof ItemFreecamWand) {
            ItemFreecamWand.saveUpgrades(wand, upgradeInventory);
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        ItemStack wand = getWandStack();
        return wand != null && wand.getItem() instanceof ItemFreecamWand && player.isEntityAlive();
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        saveToWand();
        super.onContainerClosed(player);
    }

    @Override
    public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
        // 锁定编辑中的法杖槽位（禁止移走、丢弃、拿取）
        if (slotId == lockedContainerSlot) {
            return null;
        }
        // mode == 2 为数字键换槽：若按下的数字键指向编辑法杖的快捷栏槽位，坚决拦截
        if (mode == 2 && clickedButton == wandSlot) {
            return null;
        }
        return super.slotClick(slotId, clickedButton, mode, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        ItemStack itemstack = null;
        Slot slot = (Slot) this.inventorySlots.get(slotIndex);

        if (slot != null && slot.getHasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();

            if (slotIndex < UPGRADE_SLOT_COUNT) {
                // 从核心槽位移出到玩家背包 (4..40)
                if (!this.mergeItemStack(itemstack1, UPGRADE_SLOT_COUNT, this.inventorySlots.size(), true)) {
                    return null;
                }
                slot.onSlotChange(itemstack1, itemstack);
            } else {
                // 从玩家背包移入
                if (isCoreItem(itemstack1)) {
                    boolean moved = false;
                    for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
                        Slot targetSlot = (Slot) this.inventorySlots.get(i);
                        if (!targetSlot.getHasStack() && targetSlot.isItemValid(itemstack1)) {
                            ItemStack single = itemstack1.splitStack(1);
                            targetSlot.putStack(single);
                            targetSlot.onSlotChanged();
                            moved = true;
                            break;
                        }
                    }
                    if (!moved) {
                        // 核心槽位已满或同类核心已存在，转为在背包内部转移
                        if (slotIndex >= UPGRADE_SLOT_COUNT && slotIndex < UPGRADE_SLOT_COUNT + 27) {
                            if (!this.mergeItemStack(itemstack1, UPGRADE_SLOT_COUNT + 27, this.inventorySlots.size(), false)) {
                                return null;
                            }
                        } else if (slotIndex >= UPGRADE_SLOT_COUNT + 27 && slotIndex < this.inventorySlots.size()) {
                            if (!this.mergeItemStack(itemstack1, UPGRADE_SLOT_COUNT, UPGRADE_SLOT_COUNT + 27, false)) {
                                return null;
                            }
                        }
                    }
                } else {
                    // 非核心物品：主背包 <-> 快捷栏
                    if (slotIndex >= UPGRADE_SLOT_COUNT && slotIndex < UPGRADE_SLOT_COUNT + 27) {
                        if (!this.mergeItemStack(itemstack1, UPGRADE_SLOT_COUNT + 27, this.inventorySlots.size(), false)) {
                            return null;
                        }
                    } else if (slotIndex >= UPGRADE_SLOT_COUNT + 27 && slotIndex < this.inventorySlots.size()) {
                        if (!this.mergeItemStack(itemstack1, UPGRADE_SLOT_COUNT, UPGRADE_SLOT_COUNT + 27, false)) {
                            return null;
                        }
                    }
                }
            }

            if (itemstack1.stackSize == 0) {
                slot.putStack(null);
            } else {
                slot.onSlotChanged();
            }

            if (itemstack1.stackSize == itemstack.stackSize) {
                return null;
            }

            slot.onPickupFromSlot(player, itemstack1);
        }

        return itemstack;
    }

    @Override
    protected boolean mergeItemStack(ItemStack stack, int startIndex, int endIndex, boolean reverse) {
        if (stack == null || stack.getItem() == null) return false;
        boolean changed = false;
        int k = reverse ? endIndex - 1 : startIndex;

        if (stack.isStackable()) {
            while (stack.stackSize > 0 && (!reverse && k < endIndex || reverse && k >= startIndex)) {
                if (k != lockedContainerSlot) {
                    Slot slot = (Slot) this.inventorySlots.get(k);
                    ItemStack inSlot = slot.getStack();
                    if (inSlot != null && inSlot.getItem() != null && inSlot.getItem() == stack.getItem()
                            && (!stack.getHasSubtypes() || stack.getItemDamage() == inSlot.getItemDamage())
                            && ItemStack.areItemStackTagsEqual(stack, inSlot)) {
                        int total = inSlot.stackSize + stack.stackSize;
                        int max = Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit());
                        if (total <= max) {
                            stack.stackSize = 0;
                            inSlot.stackSize = total;
                            slot.onSlotChanged();
                            changed = true;
                        } else if (inSlot.stackSize < max) {
                            stack.stackSize -= max - inSlot.stackSize;
                            inSlot.stackSize = max;
                            slot.onSlotChanged();
                            changed = true;
                        }
                    }
                }
                if (reverse) k--; else k++;
            }
        }

        if (stack.stackSize > 0) {
            k = reverse ? endIndex - 1 : startIndex;
            while (!reverse && k < endIndex || reverse && k >= startIndex) {
                if (k != lockedContainerSlot) {
                    Slot slot = (Slot) this.inventorySlots.get(k);
                    if (slot.isItemValid(stack)) {
                        ItemStack inSlot = slot.getStack();
                        if (inSlot == null) {
                            int max = Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit());
                            if (stack.stackSize <= max) {
                                slot.putStack(stack.copy());
                                slot.onSlotChanged();
                                stack.stackSize = 0;
                                changed = true;
                                break;
                            } else {
                                slot.putStack(stack.splitStack(max));
                                slot.onSlotChanged();
                                changed = true;
                            }
                        }
                    }
                }
                if (reverse) k--; else k++;
            }
        }

        return changed;
    }

    public static boolean isCoreItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        return stack.getItem() instanceof IWandCore || stack.getItem() instanceof ItemBlueprintCore;
    }

    public static boolean isSameCore(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getItem() instanceof IWandCore && b.getItem() instanceof IWandCore) {
            String idA = ((IWandCore) a.getItem()).getCoreId();
            String idB = ((IWandCore) b.getItem()).getCoreId();
            if (idA != null && idA.equals(idB)) return true;
        }
        return a.getItem() == b.getItem();
    }

    public int getWandSlot() {
        return wandSlot;
    }

    public int getLockedContainerSlot() {
        return lockedContainerSlot;
    }

    public IInventory getUpgradeInventory() {
        return upgradeInventory;
    }

    public static class SlotWandCore extends Slot {
        private final ContainerWandUpgrade container;

        public SlotWandCore(ContainerWandUpgrade container, IInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
            this.container = container;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            if (stack == null) return false;
            if (!isCoreItem(stack)) return false;
            // 校验同法杖内是否已存在同种核心
            for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
                if (i == getSlotIndex()) continue;
                ItemStack other = inventory.getStackInSlot(i);
                if (other != null && isSameCore(other, stack)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }

    public static class SlotLockedWand extends Slot {
        public SlotLockedWand(IInventory inventory, int slotIndex, int x, int y) {
            super(inventory, slotIndex, x, y);
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }
    }
}
