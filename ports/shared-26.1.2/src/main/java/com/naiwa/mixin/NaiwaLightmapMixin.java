package com.naiwa.mixin;

import com.naiwa.client.NaiwaAscensionVisuals;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 状态开始和结束都强制刷新一次光照缓存；服务器的天空光保持原样。 */
@Mixin(LightmapRenderStateExtractor.class)
abstract class NaiwaLightmapMixin {
    @Unique private boolean naiwa$previous;

    @Inject(method="extract",at=@At("TAIL"))
    private void naiwa$night(LightmapRenderState state,float partial,CallbackInfo info) {
        boolean active = NaiwaAscensionVisuals.active();
        if (active) state.skyFactor=.08F;
        if (active || naiwa$previous) state.needsUpdate=true;
        naiwa$previous=active;
    }
}
