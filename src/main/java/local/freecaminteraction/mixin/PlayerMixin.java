package local.freecaminteraction.mixin;

import local.freecaminteraction.FreecamInteraction;
import local.freecaminteraction.FreecamTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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

    @Inject(method = "canInteractWithEntity(Lnet/minecraft/world/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true)
    private void freecamEntityRange(Entity entity, double padding, CallbackInfoReturnable<Boolean> callback) {
        Player player = (Player) (Object) this;
        if (FreecamInteraction.active(player) && FreecamTarget.allowed(player, entity)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z", at = @At("HEAD"), cancellable = true)
    private void freecamEntityBoxRange(AABB box, double padding, CallbackInfoReturnable<Boolean> callback) {
        Player player = (Player) (Object) this;
        if (FreecamInteraction.active(player)) {
            var center = box.getCenter();
            if (local.freecaminteraction.FreecamRange.contains(player.getX(), player.getY(), player.getZ(),
                    center.x - 0.5, center.y - 0.5, center.z - 0.5)) {
                callback.setReturnValue(true);
            }
        }
    }
}
