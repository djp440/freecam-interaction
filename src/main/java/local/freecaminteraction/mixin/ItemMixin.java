package local.freecaminteraction.mixin;

import local.freecaminteraction.FreecamActions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 桶仍走原版 use；仅把其内部玩家朝向射线替换为已由服务端复核的光标命中。 */
@Mixin(Item.class)
public abstract class ItemMixin {
    @Inject(method = "getPlayerPOVHitResult", at = @At("HEAD"), cancellable = true)
    private static void freecamBucketTarget(Level level, Player player, ClipContext.Fluid fluid,
            CallbackInfoReturnable<BlockHitResult> callback) {
        BlockHitResult target = FreecamActions.bucketTarget(player);
        if (target != null) callback.setReturnValue(target);
    }
}
