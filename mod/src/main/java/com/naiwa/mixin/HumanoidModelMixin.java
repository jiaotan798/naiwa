package com.naiwa.mixin;

import com.naiwa.client.NaiwaPoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void naiwa$pose(LivingEntity entity,float walk,float speed,float time,float yaw,float pitch,CallbackInfo callback) {
        NaiwaPoses.laugh((HumanoidModel<?>)(Object)this,entity,time);
    }
}
