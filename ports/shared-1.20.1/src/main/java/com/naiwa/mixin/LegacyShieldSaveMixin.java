package com.naiwa.mixin;

import com.naiwa.Naiwa;
import com.naiwa.NaiwaShield;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 只从持久化结果移除瞬时动画。旧版网络另外读取物品标签，旁观者仍能收到起点。 */
@Mixin(ItemStack.class)
abstract class LegacyShieldSaveMixin {
    @Inject(method="save",at=@At("RETURN"))
    private void naiwa$save(CompoundTag output, CallbackInfoReturnable<CompoundTag> result) {
        if (((ItemStack)(Object)this).is(Naiwa.SHIELD.get())) NaiwaShield.stripTransientTag(result.getReturnValue());
    }
}
