package com.naiwa.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class PortMouseMixin {
    @Inject(method="grabMouse",at=@At("HEAD"),cancellable=true)
    private void naiwa$noDesktopCapture(CallbackInfo info) {info.cancel();}
}
