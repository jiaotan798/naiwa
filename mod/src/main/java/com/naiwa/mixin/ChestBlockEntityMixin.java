package com.naiwa.mixin;

import com.naiwa.NaiwaEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
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
        if(level!=null&&level.isClientSide&&!level.getEntitiesOfClass(NaiwaEntity.class,new AABB(chest.getBlockPos()).inflate(.5,1,.5),
                n->n.isAlive()&&n.act().equals("chest_peek")&&n.blockPosition().equals(chest.getBlockPos())).isEmpty())callback.setReturnValue(1F);
    }
}
