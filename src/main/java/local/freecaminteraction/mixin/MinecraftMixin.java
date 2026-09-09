package local.freecaminteraction.mixin;

import local.freecaminteraction.client.FreecamClient;
import local.freecaminteraction.client.FreecamSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void freecamInput(CallbackInfo callback) {
        if (FreecamClient.active()) {
            FreecamSelection.beforeInput();
        }
    }

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean freecamContinueAttack(MouseHandler mouse) {
        return FreecamClient.active() ? FreecamSelection.continueAttack() : mouse.isMouseGrabbed();
    }
}
