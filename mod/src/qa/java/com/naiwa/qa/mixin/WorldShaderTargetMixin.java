package com.naiwa.qa.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 每次自有着色器应用后核对目标，覆盖 Iris 在父类 apply 中重新绑定画布的情况。 */
@Mixin(targets="com.naiwa.client.NaiwaWorldShader",remap=false)
public abstract class WorldShaderTargetMixin {
    @Inject(method="apply",at=@At("RETURN"))
    private void naiwa$target(CallbackInfo ci) {com.naiwa.qa.ShaderTargetChecks.check((net.minecraft.client.renderer.ShaderInstance)(Object)this);}
}
