package com.naiwa.mixin;

import com.naiwa.client.NaiwaClientSmoke;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class PortClientSmokeMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void naiwa$smoke(CallbackInfo info) {
        NaiwaClientSmoke.tick((Minecraft)(Object)this);
    }
}
