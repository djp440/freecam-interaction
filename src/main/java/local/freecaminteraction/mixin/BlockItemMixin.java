package local.freecaminteraction.mixin;

import local.freecaminteraction.FreecamInteraction;
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
    private void freecamPlacement(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> callback) {
        if (FreecamInteraction.active(context.getPlayer())
                && !FreecamInteraction.allowed(context.getPlayer(), context.getClickedPos())) {
            callback.setReturnValue(false);
        }
    }
}
