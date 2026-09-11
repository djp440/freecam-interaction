package local.godviewbuild.mixin;

import local.godviewbuild.GodviewInteraction;
import local.godviewbuild.GodviewRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true)
    private void godviewRange(BlockPos position, double padding, CallbackInfoReturnable<Boolean> callback) {
        Player player = (Player) (Object) this;
        if (GodviewInteraction.active(player)) {
            callback.setReturnValue(GodviewInteraction.allowed(player, position));
        }
    }

    @Inject(method = "canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z", at = @At("HEAD"), cancellable = true)
    private void godviewEntityRange(AABB bounds, double padding, CallbackInfoReturnable<Boolean> callback) {
        Player player = (Player) (Object) this;
        if (GodviewInteraction.active(player)) {
            var center = bounds.getCenter();
            callback.setReturnValue(GodviewRange.contains(player.getX(), player.getY(), player.getZ(),
                    center.x - 0.5, center.y - 0.5, center.z - 0.5));
        }
    }
}
