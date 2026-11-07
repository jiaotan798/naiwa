package com.naiwa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.naiwa.NaiwaGameplay;
import com.naiwa.NaiwaShield;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 格挡和扣血取原版实际结算点，避免伤害尚未通过判定时改状态。 */
@Mixin(LivingEntity.class)
abstract class FabricLivingDamageMixin {
    @WrapOperation(method="applyItemBlocking",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/component/BlocksAttacks;hurtBlockingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;F)V"))
    private void naiwa$blocked(net.minecraft.world.item.component.BlocksAttacks component,net.minecraft.world.level.Level level,
                               net.minecraft.world.item.ItemStack stack,LivingEntity entity,net.minecraft.world.InteractionHand hand,
                               float blocked,Operation<Void> original) {
        if (blocked>0 && entity instanceof ServerPlayer player) NaiwaShield.block(player,null);
        original.call(component,level,stack,entity,hand,blocked);
    }

    @Inject(method="causeFallDamage",at=@At("HEAD"),cancellable=true)
    private void naiwa$fall(double distance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> info) {
        if (NaiwaGameplay.blocksFall((LivingEntity)(Object)this)) info.setReturnValue(false);
    }

    @WrapOperation(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void naiwa$hurt(LivingEntity entity, float health, Operation<Void> original) {
        float before = entity.getHealth();
        original.call(entity,health);
        NaiwaGameplay.afterDamage(entity,before-entity.getHealth());
    }
}
