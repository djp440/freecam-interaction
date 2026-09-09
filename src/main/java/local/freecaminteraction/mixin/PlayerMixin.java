package local.freecaminteraction.mixin;

import local.freecaminteraction.FreecamInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true)
    private void freecamRange(BlockPos position, double padding, CallbackInfoReturnable<Boolean> callback) {
        Player player = (Player) (Object) this;
        if (FreecamInteraction.active(player)) {
            callback.setReturnValue(FreecamInteraction.allowed(player, position));
        }
    }
}
