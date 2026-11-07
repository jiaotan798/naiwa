package com.naiwa.mixin;

import com.naiwa.Naiwa;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 非人形怪物使用前倾和身体抖动适配大笑，不假设它们具有人类的手臂骨骼。 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingRendererMixin {
    @Inject(method="setupRotations",at=@At("TAIL"))
    private void naiwa$shake(LivingEntity entity,PoseStack pose,float time,float yaw,float partial,CallbackInfo callback) {
        if (entity.hasEffect(Naiwa.LAUGHING)) pose.mulPose(Axis.XP.rotationDegrees(8+(float)Math.sin((entity.tickCount+partial)*1.8)*3));
    }
}
