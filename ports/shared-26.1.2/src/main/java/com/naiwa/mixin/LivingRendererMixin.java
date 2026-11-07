package com.naiwa.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.naiwa.Naiwa;
import com.naiwa.client.NaiwaPlayerRenderer;
import com.naiwa.client.NaiwaRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 笑气效果独立于生物模型类型；非人形仍有前倾和身体抖动。 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingRendererMixin {
    // 原版 AvatarRenderer 继承这个提交入口，NeoForge 在调用它前另有玩家绘制事件。
    @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at=@At("HEAD"),cancellable=true)
    private void naiwa$flight(LivingEntityRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,CallbackInfo info) {
        if (state instanceof AvatarRenderState avatar && NaiwaPlayerRenderer.submit(avatar,pose,collector)) info.cancel();
    }

    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",at=@At("TAIL"))
    private void naiwa$extract(LivingEntity entity,LivingEntityRenderState state,float partial,CallbackInfo info) {
        var effect = entity.getEffect(Naiwa.ASCENDING);
        ((NaiwaRenderState)state).naiwa$setEffects(entity.hasEffect(Naiwa.LAUGHING),
                effect == null ? -1 : effect.getAmplifier(),effect == null ? 0 : effect.getDuration(),partial);
    }

    @Inject(method="setupRotations",at=@At("TAIL"))
    private void naiwa$shake(LivingEntityRenderState state,PoseStack pose,float yaw,float scale,CallbackInfo info) {
        if (((NaiwaRenderState)state).naiwa$laughing())
            pose.mulPose(Axis.XP.rotationDegrees(8+(float)Math.sin(state.ageInTicks*1.8)*3));
    }
}
