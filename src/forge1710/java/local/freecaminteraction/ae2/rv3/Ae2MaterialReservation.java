package local.freecaminteraction.ae2.rv3;

import java.util.ArrayList;
import java.util.List;
import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.blueprint.MaterialRequirement;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** 保留从 ME 实际取得的堆，回滚时不重建物品身份/NBT。 */
final class Ae2MaterialReservation {
    private final TileAe2Transmitter tile;
    private final List<IAEItemStack> taken = new ArrayList<IAEItemStack>();
    private boolean finished;

    private Ae2MaterialReservation(TileAe2Transmitter tile) { this.tile = tile; }

    static Ae2MaterialReservation reserve(EntityPlayerMP player, List rawRequirements, int[] deficits, String source) {
        if (source == null) return allZero(deficits) ? new Ae2MaterialReservation(null) : null;
        TileAe2Transmitter tile = Ae2Runtime.selectedTile(player);
        if (tile == null || !source.equals(tile.getInstanceId()) || !Ae2Runtime.has(tile, player, SecurityPermissions.EXTRACT)) return null;
        Ae2MaterialReservation reservation = new Ae2MaterialReservation(tile);
        try {
            IMEMonitor<IAEItemStack> monitor = tile.getProxy().getStorage().getItemInventory();
            PlayerSource actor = new PlayerSource(player, tile);
            List<IAEItemStack> requests = new ArrayList<IAEItemStack>();
            for (int i = 0; i < deficits.length; i++) {
                int missing = deficits[i];
                if (missing <= 0) continue;
                MaterialRequirement requirement = (MaterialRequirement) rawRequirements.get(i);
                for (IAEItemStack available : monitor.getStorageList()) {
                    if (missing <= 0) break;
                    ItemStack actual = available.getItemStack();
                    if (!requirement.matches(actual)) continue;
                    IAEItemStack request = requested(requests, actual);
                    long reserved = request == null ? 0L : request.getStackSize();
                    long amount = Math.min((long) missing, available.getStackSize() - reserved);
                    if (amount <= 0L) continue;
                    IAEItemStack cumulative = available.copy().setStackSize(reserved + amount);
                    IAEItemStack possible = monitor.extractItems(cumulative, Actionable.SIMULATE, actor);
                    long count = Math.max(0L, (possible == null ? 0L : possible.getStackSize()) - reserved);
                    if (count > 0) {
                        if (request == null) requests.add(available.copy().setStackSize(count));
                        else request.setStackSize(reserved + count);
                        missing -= (int) count;
                    }
                }
                if (missing > 0) return null;
            }
            for (IAEItemStack request : requests) {
                IAEItemStack extracted = AEApi.instance().storage().poweredExtraction(tile.getProxy().getEnergy(), monitor, request, actor);
                if (extracted == null || extracted.getStackSize() != request.getStackSize()) {
                    if (extracted != null) reservation.taken.add(extracted);
                    reservation.rollback(player);
                    return null;
                }
                reservation.taken.add(extracted);
            }
            return reservation;
        } catch (Throwable error) {
            ModLog.info("AE2 material reservation failed: " + error);
            reservation.rollback(player);
            return null;
        }
    }

    void commit() { finished = true; taken.clear(); }

    void rollback(EntityPlayerMP player) {
        if (finished) return;
        finished = true;
        for (IAEItemStack extracted : taken) {
            IAEItemStack remainder = extracted;
            try {
                if (tile != null && Ae2Runtime.has(tile, player, SecurityPermissions.INJECT)) {
                    remainder = AEApi.instance().storage().poweredInsert(tile.getProxy().getEnergy(),
                            tile.getProxy().getStorage().getItemInventory(), extracted, new PlayerSource(player, tile));
                }
            } catch (Throwable error) { ModLog.info("AE2 material rollback to network failed: " + error); }
            if (remainder != null && remainder.getStackSize() > 0) giveOrDrop(player, remainder.getItemStack());
        }
        taken.clear();
    }

    private static void giveOrDrop(EntityPlayerMP player, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        if (!player.inventory.addItemStackToInventory(stack)) {
            EntityItem item = player.dropPlayerItemWithRandomChoice(stack, false);
            if (item != null) item.delayBeforeCanPickup = 0;
        }
    }

    private static boolean allZero(int[] values) {
        for (int value : values) if (value > 0) return false;
        return true;
    }

    private static IAEItemStack requested(List<IAEItemStack> requests, ItemStack stack) {
        for (IAEItemStack request : requests) {
            ItemStack existing = request.getItemStack();
            if (existing != null && existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)) return request;
        }
        return null;
    }
}
