package local.freecaminteraction.blueprint.build;

import java.util.ArrayList;
import java.util.List;
import local.freecaminteraction.ae2.Ae2Integration;
import local.freecaminteraction.blueprint.MaterialRequirement;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** 一个施工单元的物料事务：背包优先，ME 补缺，失败退回实际取得的物品。 */
final class MaterialTransaction {
    private static final class TakenSlot {
        final int slot;
        final ItemStack stack;
        TakenSlot(int slot, ItemStack stack) { this.slot = slot; this.stack = stack; }
    }

    private final EntityPlayerMP player;
    private final List<TakenSlot> inventoryTaken = new ArrayList<TakenSlot>();
    private Object meReservation;
    private boolean finished;

    private MaterialTransaction(EntityPlayerMP player) { this.player = player; }

    static MaterialTransaction reserve(EntityPlayerMP player, List<MaterialRequirement> requirements, String source) {
        MaterialTransaction tx = new MaterialTransaction(player);
        if (player.capabilities.isCreativeMode || requirements == null || requirements.isEmpty()) return tx;

        int[] remainingBySlot = new int[Math.min(36, player.inventory.mainInventory.length)];
        for (int slot = 0; slot < remainingBySlot.length; slot++) {
            ItemStack stack = player.inventory.mainInventory[slot];
            remainingBySlot[slot] = stack == null ? 0 : stack.stackSize;
        }
        int[] deficits = new int[requirements.size()];
        for (int index = 0; index < requirements.size(); index++) {
            MaterialRequirement req = requirements.get(index);
            int remaining = req == null ? 0 : Math.max(0, req.getCount());
            for (int slot = 0; slot < remainingBySlot.length && remaining > 0; slot++) {
                ItemStack stack = player.inventory.mainInventory[slot];
                if (remainingBySlot[slot] > 0 && req.matches(stack)) {
                    int take = Math.min(remaining, remainingBySlot[slot]);
                    remainingBySlot[slot] -= take;
                    remaining -= take;
                }
            }
            deficits[index] = remaining;
        }

        boolean needsMe = false;
        for (int deficit : deficits) if (deficit > 0) { needsMe = true; break; }
        if (needsMe) {
            tx.meReservation = Ae2Integration.reserveMe(player, requirements, deficits, source);
            if (tx.meReservation == null) return null;
        }

        for (int index = 0; index < requirements.size(); index++) {
            MaterialRequirement req = requirements.get(index);
            int remaining = req == null ? 0 : Math.max(0, req.getCount());
            for (int slot = 0; slot < Math.min(36, player.inventory.mainInventory.length) && remaining > 0; slot++) {
                ItemStack stack = player.inventory.mainInventory[slot];
                if (stack == null || !req.matches(stack)) continue;
                int take = Math.min(remaining, stack.stackSize);
                ItemStack exact = stack.copy();
                exact.stackSize = take;
                tx.inventoryTaken.add(new TakenSlot(slot, exact));
                stack.stackSize -= take;
                remaining -= take;
                if (stack.stackSize <= 0) player.inventory.mainInventory[slot] = null;
            }
            remaining -= deficits[index];
            if (remaining > 0) {
                tx.rollback();
                return null;
            }
        }
        player.inventoryContainer.detectAndSendChanges();
        return tx;
    }

    void commit() {
        if (finished) return;
        finished = true;
        inventoryTaken.clear();
        Ae2Integration.commitMe(meReservation);
        meReservation = null;
    }

    void rollback() {
        if (finished) return;
        finished = true;
        for (int i = inventoryTaken.size() - 1; i >= 0; i--) restore(inventoryTaken.get(i));
        inventoryTaken.clear();
        Ae2Integration.rollbackMe(player, meReservation);
        meReservation = null;
        player.inventoryContainer.detectAndSendChanges();
    }

    private void restore(TakenSlot taken) {
        ItemStack current = player.inventory.mainInventory[taken.slot];
        if (current == null) {
            player.inventory.mainInventory[taken.slot] = taken.stack;
            return;
        }
        if (current.isItemEqual(taken.stack) && ItemStack.areItemStackTagsEqual(current, taken.stack)
                && current.stackSize + taken.stack.stackSize <= current.getMaxStackSize()) {
            current.stackSize += taken.stack.stackSize;
            return;
        }
        ItemStack remainder = taken.stack.copy();
        if (!player.inventory.addItemStackToInventory(remainder) && remainder.stackSize > 0) {
            EntityItem dropped = player.dropPlayerItemWithRandomChoice(remainder, false);
            if (dropped != null) dropped.delayBeforeCanPickup = 0;
        }
    }
}
