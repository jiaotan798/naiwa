package com.naiwa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.naiwa.NaiwaGameplay;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** 在 Forge 的伤害事件全部结束、真实扣血后解除大笑。 */
@Mixin(LivingEntity.class)
abstract class ForgeDamageMixin {
    @WrapOperation(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void naiwa$hurt(LivingEntity entity, float health, Operation<Void> original) {
        float before = entity.getHealth();
        original.call(entity,health);
        NaiwaGameplay.afterDamage(entity,before-entity.getHealth());
    }
}
