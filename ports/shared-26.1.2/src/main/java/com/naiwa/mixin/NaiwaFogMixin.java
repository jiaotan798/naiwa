package com.naiwa.mixin;

import com.naiwa.client.NaiwaAscensionVisuals;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 飞升雾色沿用深蓝；各生物群系的雾距离仍由原版结算。 */
@Mixin(FogRenderer.class)
abstract class NaiwaFogMixin {
    @Inject(method="setupFog",at=@At("RETURN"))
    private void naiwa$color(CallbackInfoReturnable<FogData> info) {
        if (NaiwaAscensionVisuals.active()) {
            var color = info.getReturnValue().color;
            color.set(.012F,.016F,.035F,color.w);
        }
    }
}
