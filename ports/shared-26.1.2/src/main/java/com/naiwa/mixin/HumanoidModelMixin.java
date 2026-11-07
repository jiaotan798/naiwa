package com.naiwa.mixin;

import com.naiwa.client.NaiwaPoses;
import com.naiwa.client.NaiwaRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 采用已提取的效果状态，在原版骨骼计算结束后叠加捧腹动作。 */
@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",at=@At("TAIL"))
    private void naiwa$laugh(HumanoidRenderState state,CallbackInfo info) {
        NaiwaPoses.laugh((HumanoidModel<?>)(Object)this,(NaiwaRenderState)state,state.ageInTicks);
    }
}
