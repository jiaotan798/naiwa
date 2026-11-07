package com.naiwa.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.naiwa.client.NaiwaPlayerRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 飞升模型接管玩家绘制，旁观者仍按同步效果的真实阶段渲染。 */
@Mixin(PlayerRenderer.class)
abstract class FabricPlayerRendererMixin {
    @Inject(method="render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At("HEAD"),cancellable=true)
    private void naiwa$player(AbstractClientPlayer player, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo info) {
        if (NaiwaPlayerRenderer.replacePlayer(player,pose,buffers,light,partial)) info.cancel();
    }
}
