package local.godviewbuild.mixin;

import local.godviewbuild.client.GodviewClient;
import local.godviewbuild.client.GodviewSelection;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void godviewPick(float partialTick, CallbackInfo callback) {
        if (GodviewClient.active()) {
            GodviewSelection.pick();
        }
    }
}
