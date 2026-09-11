package local.godviewbuild.mixin;

import local.godviewbuild.GodviewEffects;
import local.godviewbuild.GodviewInteraction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "net.minecraft.server.network.ServerGamePacketListenerImpl$1")
public abstract class ServerEntityInteractionMixin {
    @Redirect(method = "performInteraction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl$EntityInteraction;run(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult godviewEntityEffect(@Coerce Object interaction,
            ServerPlayer player, Entity target, InteractionHand hand) {
        InteractionResult result = ((EntityInteractionInvoker) interaction).godviewInvokeRun(player, target, hand);
        if (result.consumesAction() && GodviewInteraction.active(player)) {
            GodviewEffects.emitEntity(player, target);
        }
        return result;
    }
}
