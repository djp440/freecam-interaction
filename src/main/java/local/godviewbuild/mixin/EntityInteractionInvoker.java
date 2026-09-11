package local.godviewbuild.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.network.ServerGamePacketListenerImpl$EntityInteraction")
public interface EntityInteractionInvoker {
    @Invoker("run")
    InteractionResult godviewInvokeRun(ServerPlayer player, Entity target, InteractionHand hand);
}
