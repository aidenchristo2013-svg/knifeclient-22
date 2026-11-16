package com.example.knifeclient.mixin;

import com.example.knifeclient.Features;
import com.example.knifeclient.FeatureTick;
import com.example.knifeclient.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Glow Skin: glowing outline (and its colour) only for YOUR player, only on your screen.
 * Entity ESP: glowing outline on other entities, visible through walls, only on your screen.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isGlowing", at = @At("RETURN"), cancellable = true)
    private void knifeclient$glow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (Modules.GLOW.enabled && self == MinecraftClient.getInstance().player) {
            cir.setReturnValue(true);
        } else if (FeatureTick.espMatches(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true)
    private void knifeclient$glowColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (Modules.GLOW.enabled && self == MinecraftClient.getInstance().player) {
            cir.setReturnValue(Modules.glowColor());
        } else if (FeatureTick.espMatches(self)) {
            cir.setReturnValue(Features.ESP_COLOR.rgb(0.0));
        }
    }
}
