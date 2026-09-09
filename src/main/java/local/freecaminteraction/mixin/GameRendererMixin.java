package local.freecaminteraction.mixin;

import local.freecaminteraction.client.FreecamClient;
import local.freecaminteraction.client.FreecamSelection;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void freecamPick(float partialTick, CallbackInfo callback) {
        if (FreecamClient.active()) {
            FreecamSelection.pick();
        }
    }
}
