package com.naiwa.mixin;

import com.naiwa.NaiwaGameplay;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 换成空目标可清除已存在的追击；跳过 setTarget 会保留旧目标。 */
@Mixin(Mob.class)
abstract class FabricMobTargetMixin {
    @ModifyVariable(method="setTarget",at=@At("HEAD"),argsOnly=true)
    private LivingEntity naiwa$target(LivingEntity target) {
        return target instanceof ServerPlayer player && NaiwaGameplay.protectedFromMobs(player) ? null : target;
    }
}
