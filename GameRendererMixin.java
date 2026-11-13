package com.example.knifeclient.mixin;

import com.example.knifeclient.Zoom;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: narrows the field of view while the zoom key is held. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void knifeclient$zoom(CallbackInfoReturnable<Double> cir) {
        double f = Zoom.factor();
        if (f > 1.001) {
            cir.setReturnValue(cir.getReturnValue() / f);
        }
    }
}
