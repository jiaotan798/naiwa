package com.naiwa.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.naiwa.Naiwa;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class FabricHandMixin {
    @Inject(method="renderHandsWithItems",at=@At("HEAD"),cancellable=true)
    private void naiwa$hands(float partial, PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer player, int light, CallbackInfo info) {
        if (player.hasEffect(Naiwa.ASCENDING)) info.cancel();
    }
}
