package com.naiwa.qa.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 后台探针直接驱动游戏按键，不捕获或重定位用户的桌面鼠标。 */
@Mixin(MouseHandler.class)
public abstract class BackgroundMouseMixin {
    @Inject(method="grabMouse",at=@At("HEAD"),cancellable=true)
    private void naiwa$noMouseCapture(CallbackInfo ci) {
        if(!Boolean.getBoolean("naiwa.qa.foreground"))ci.cancel();
    }
}
