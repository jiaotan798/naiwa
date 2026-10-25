package com.naiwa.mixin;

import com.naiwa.client.NaiwaPoses;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 玩家外层袖子在 super.setupAnim 之后复制，必须在玩家方法尾部再同步一次。 */
@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void naiwa$pose(LivingEntity entity,float walk,float speed,float time,float yaw,float pitch,CallbackInfo callback) {
        NaiwaPoses.laugh((PlayerModel<?>)(Object)this,entity,time);
    }
}
