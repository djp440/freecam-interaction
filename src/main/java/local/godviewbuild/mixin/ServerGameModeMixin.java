package local.godviewbuild.mixin;

import local.godviewbuild.GodviewInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerGameModeMixin {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;
    @Shadow private boolean isDestroyingBlock;
    @Shadow private boolean hasDelayedDestroy;
    @Shadow private BlockPos destroyPos;
    @Shadow private BlockPos delayedDestroyPos;
    @Unique private boolean godviewPreviouslyActive;

    @Inject(method = "tick", at = @At("HEAD"))
    private void godviewCancelMining(CallbackInfo callback) {
        boolean active = GodviewInteraction.active(player);
        boolean exited = godviewPreviouslyActive && !active;
        if (isDestroyingBlock && (exited || active && !GodviewInteraction.allowed(player, destroyPos))) {
            isDestroyingBlock = false;
            level.destroyBlockProgress(player.getId(), destroyPos, -1);
        }
        if (hasDelayedDestroy && (exited || active && !GodviewInteraction.allowed(player, delayedDestroyPos))) {
            hasDelayedDestroy = false;
            level.destroyBlockProgress(player.getId(), delayedDestroyPos, -1);
        }
        godviewPreviouslyActive = active;
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void godviewCheckBreak(BlockPos position, CallbackInfoReturnable<Boolean> callback) {
        if (GodviewInteraction.active(player) && !GodviewInteraction.allowed(player, position)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
    private void godviewAbortDelayedBreak(BlockPos position, ServerboundPlayerActionPacket.Action action,
            Direction face, int maximumHeight, int sequence, CallbackInfo callback) {
        if (GodviewInteraction.active(player) && action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK
                && position.equals(delayedDestroyPos)) {
            hasDelayedDestroy = false;
            level.destroyBlockProgress(player.getId(), position, -1);
        }
    }
}
