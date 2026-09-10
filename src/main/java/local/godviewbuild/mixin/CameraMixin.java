package local.godviewbuild.mixin;

import local.godviewbuild.client.GodviewClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "getMaxZoom(F)F", at = @At("HEAD"), cancellable = true)
    private void godviewAvoidZoomCollapse(float distance, CallbackInfoReturnable<Float> cir) {
        if (GodviewClient.active()) {
            cir.setReturnValue(distance);
        }
    }
}
