package com.naiwa.mixin;

import com.naiwa.NaiwaEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 仅客户端保留演出箱盖打开；不伪造菜单/打开人数，不改变存储、红石或漏斗。大箱子沿用原版双盖取最大值。 */
@Mixin(ChestBlockEntity.class)
abstract class ChestBlockEntityMixin {
    @Inject(method="getOpenNess",at=@At("RETURN"),cancellable=true)
    private void naiwa$peekLid(float partialTick,CallbackInfoReturnable<Float> callback) {
        var chest=(ChestBlockEntity)(Object)this;var level=chest.getLevel();
        // 实时核对已登记演员，仍保留原空间条件和大箱子的双盖合并规则。
        if(level!=null&&level.isClientSide()&&NaiwaEntity.isChestPeeking(level,chest.getBlockPos()))callback.setReturnValue(1F);
    }
}
