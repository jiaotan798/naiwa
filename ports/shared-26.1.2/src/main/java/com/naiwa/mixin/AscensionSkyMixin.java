package com.naiwa.mixin;

import com.naiwa.client.NaiwaAscensionVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只改变本机飞升帧提取出的天体状态，不写入时间或环境属性。 */
@Mixin(SkyRenderer.class)
abstract class AscensionSkyMixin {
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void naiwa$midnight(ClientLevel level,float partial,Camera camera,SkyRenderState sky,CallbackInfo info) {
        if (!NaiwaAscensionVisuals.active()) return;
        sky.sunAngle=180;
        sky.moonAngle=0;
        sky.starAngle=180;
        sky.starBrightness=.5F;
        sky.skyColor=0xFF090F22;
        sky.sunriseAndSunsetColor=0;
    }
}
