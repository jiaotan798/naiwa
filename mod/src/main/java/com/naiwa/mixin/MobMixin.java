package com.naiwa.mixin;

import com.naiwa.Naiwa;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 暂停 AI 而非整个实体 tick，保留效果倒计时、受伤、重力和到期恢复，不改存档中的 NoAI。 */
@Mixin(Mob.class)
abstract class MobMixin {
    @Inject(method="serverAiStep", at=@At("HEAD"), cancellable=true)
    private void naiwa$laugh(CallbackInfo callback) {
        Mob mob=(Mob)(Object)this;
        if (mob.hasEffect(Naiwa.LAUGHING)) {
            mob.getNavigation().stop();
            mob.setDeltaMovement(0,mob.getDeltaMovement().y,0);
            callback.cancel();
        }
    }
}
