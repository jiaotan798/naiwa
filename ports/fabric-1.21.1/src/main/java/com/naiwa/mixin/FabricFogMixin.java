package com.naiwa.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.naiwa.client.NaiwaAscensionVisuals;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只在本机飞升时覆盖当帧雾色，正常帧仍由原版根据生物群系计算。 */
@Mixin(FogRenderer.class)
abstract class FabricFogMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(method="setupColor",at=@At("RETURN"))
    private static void naiwa$fog(CallbackInfo info) {
        if (!NaiwaAscensionVisuals.active()) return;
        fogRed=.012F;
        fogGreen=.016F;
        fogBlue=.035F;
        RenderSystem.clearColor(fogRed,fogGreen,fogBlue,0);
    }
}
