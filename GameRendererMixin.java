package com.example.knifeclient.mixin;

import com.example.knifeclient.Features;
import com.example.knifeclient.Zoom;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zoom + Custom FOV: narrows or overrides the field of view.
 *
 * In 1.21.2+ getFov returns a float, so this handles both float and double.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void knifeclient$zoom(CallbackInfoReturnable cir) {
        double f = Zoom.factor();
        boolean override = Features.FOV != null && Features.FOV.enabled;
        if (f <= 1.001 && !override) return;
        Object v = cir.getReturnValue();
        double cur;
        if (v instanceof Float x) cur = x;
        else if (v instanceof Double x) cur = x;
        else return;
        if (override) cur = Features.FOV_VALUE.value;
        if (f > 1.001) cur /= f;
        if (v instanceof Float) cir.setReturnValue((float) cur);
        else cir.setReturnValue(cur);
    }

    /** No Hurt Cam. require = 0 so a mapping difference just disables this one feature. */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void knifeclient$noHurt(CallbackInfo ci) {
        if (Features.NOHURT != null && Features.NOHURT.enabled) ci.cancel();
    }
}
