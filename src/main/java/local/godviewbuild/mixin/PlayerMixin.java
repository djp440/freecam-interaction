package local.godviewbuild.mixin;

import local.godviewbuild.GodviewInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
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
}
