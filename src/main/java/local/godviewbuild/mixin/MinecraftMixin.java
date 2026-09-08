package local.godviewbuild.mixin;

import local.godviewbuild.client.GodviewClient;
import local.godviewbuild.client.GodviewSelection;
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
    private void godviewInput(CallbackInfo callback) {
        if (GodviewClient.active()) {
            GodviewSelection.beforeInput();
        }
    }

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean godviewContinueAttack(MouseHandler mouse) {
        return GodviewClient.active() ? GodviewSelection.continueAttack() : mouse.isMouseGrabbed();
    }
}
