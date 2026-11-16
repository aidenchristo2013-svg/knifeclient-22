package com.example.knifeclient.mixin;

import com.example.knifeclient.Features;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Crosshair: optionally hides the vanilla crosshair. require = 0 keeps this safe. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void knifeclient$crosshair(CallbackInfo ci) {
        if (Features.CROSS != null && Features.CROSS.enabled && Features.CH_HIDE.value) ci.cancel();
    }
}
