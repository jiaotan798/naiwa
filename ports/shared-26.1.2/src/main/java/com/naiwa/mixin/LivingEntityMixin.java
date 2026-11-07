package com.naiwa.mixin;

import com.naiwa.NaiwaGameplay;
import com.naiwa.NaiwaEffectSync;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在原版保命入口结算奶蛙图腾，成功后跳过重复结算；效果增删或更新后调用 NaiwaEffectSync 补齐追踪者同步。 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method="onEffectAdded",at=@At("TAIL"))
    private void naiwa$added(MobEffectInstance effect,Entity source,CallbackInfo callback) {
        NaiwaEffectSync.changed((LivingEntity)(Object)this,effect,false);
    }
    @Inject(method="onEffectUpdated",at=@At("TAIL"))
    private void naiwa$updated(MobEffectInstance effect,boolean forced,Entity source,CallbackInfo callback) {
        NaiwaEffectSync.changed((LivingEntity)(Object)this,effect,false);
    }
    @Inject(method="onEffectsRemoved",at=@At("TAIL"))
    private void naiwa$removed(java.util.Collection<MobEffectInstance> effects,CallbackInfo callback) {
        for (var effect : effects) NaiwaEffectSync.changed((LivingEntity)(Object)this,effect,true);
    }
    @Inject(method="checkTotemDeathProtection", at=@At("HEAD"), cancellable=true)
    private void naiwa$totem(DamageSource source, CallbackInfoReturnable<Boolean> result) {
        if (NaiwaGameplay.useTotem((LivingEntity)(Object)this,source)) result.setReturnValue(true);
    }
}
