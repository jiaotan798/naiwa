package com.naiwa.mixin;

import com.naiwa.Naiwa;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原版两只手共享外层矩阵；每次绘制独立压栈，避免捧腹变换累积到副手或世界。 */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
    private void naiwa$belly(AbstractClientPlayer p,float partial,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack pose,SubmitNodeCollector buffers,int light,CallbackInfo ci) {
        if (p.hasEffect(Naiwa.ASCENDING)) {ci.cancel(); return;}
        if (!p.hasEffect(Naiwa.LAUGHING)) return;
        pose.pushPose();
        boolean right=(hand==InteractionHand.MAIN_HAND)==(p.getMainArm()==HumanoidArm.RIGHT);
        float side=right?1:-1;
        pose.translate(-side*.25,-.2,.1);pose.mulPose(Axis.ZP.rotationDegrees(side*25));
    }
    @Inject(method="renderArmWithItem",at=@At("RETURN"))
    private void naiwa$restore(AbstractClientPlayer p,float partial,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack pose,SubmitNodeCollector buffers,int light,CallbackInfo ci) {
        if (p.hasEffect(Naiwa.LAUGHING)) pose.popPose();
    }
}
