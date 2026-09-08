package local.godviewbuild.mixin;

import local.godviewbuild.GodviewInteraction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true)
    private void godviewPlacement(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> callback) {
        if (GodviewInteraction.active(context.getPlayer())
                && !GodviewInteraction.allowed(context.getPlayer(), context.getClickedPos())) {
            callback.setReturnValue(false);
        }
    }
}
