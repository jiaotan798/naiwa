package com.naiwa.mixin;

import com.naiwa.client.NaiwaAscensionVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iris 的光影时钟绕过天体角度接口；只覆盖绘制 uniform，保持世界时间和旁观者画面正常。 */
@Pseudo
@Mixin(targets="net.irisshaders.iris.uniforms.WorldTimeUniforms",remap=false)
abstract class IrisWorldTimeMixin {
    // 可选目标：未安装 Iris 时不加载；接口变动时允许跳过，不让基础模组无法启动。
    @Inject(method="getWorldDayTime",at=@At("RETURN"),cancellable=true,require=0)
    private static void naiwa$midnight(CallbackInfoReturnable<Integer> result) {
        if(NaiwaAscensionVisuals.active())result.setReturnValue(18000);
    }
}
