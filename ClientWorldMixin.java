package com.example.knifeclient.mixin;

import com.example.knifeclient.Features;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom Sky: tints the sky colour. The vanilla colour is used only for its brightness, so the
 * sky still gets dark at night. Works whether getSkyColor returns an int or a Vec3d.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void knifeclient$sky(CallbackInfoReturnable cir) {
        if (Features.SKY == null || !Features.SKY.enabled) return;
        int rgb = Features.SKY_COLOR.rgb(0.0);
        double r = ((rgb >> 16) & 255) / 255.0;
        double g = ((rgb >> 8) & 255) / 255.0;
        double b = (rgb & 255) / 255.0;
        Object v = cir.getReturnValue();
        if (v instanceof Integer orig) {
            double br = Math.max((orig >> 16) & 255, Math.max((orig >> 8) & 255, orig & 255)) / 255.0;
            br = Math.max(0.0, Math.min(1.0, br));
            int out = ((int) (r * br * 255) << 16) | ((int) (g * br * 255) << 8) | (int) (b * br * 255);
            cir.setReturnValue(0xFF000000 | out);
        } else if (v instanceof Vec3d orig) {
            double br = Math.max(0.0, Math.min(1.0, Math.max(orig.x, Math.max(orig.y, orig.z))));
            cir.setReturnValue(new Vec3d(r * br, g * br, b * br));
        }
    }
}
