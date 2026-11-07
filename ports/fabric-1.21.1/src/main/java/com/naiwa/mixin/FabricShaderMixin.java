package com.naiwa.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric 的着色器注册入口由框架实例化对象，因此在实际 apply 后重绑画布和深度写入。 */
@Mixin(value=ShaderInstance.class,priority=900)
abstract class FabricShaderMixin {
    @Inject(method="apply",at=@At("TAIL"))
    private void naiwa$bindWorldTarget(CallbackInfo info) {
        String name = ((ShaderInstance)(Object)this).getName();
        boolean body = name.equals("naiwa:flight_body");
        if (!body && !name.equals("naiwa:holy_light")) return;
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        RenderSystem.depthMask(body);
    }
}
