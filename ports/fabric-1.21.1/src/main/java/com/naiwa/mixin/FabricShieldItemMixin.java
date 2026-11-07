package com.naiwa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.naiwa.Naiwa;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 扩展原版写死盾牌 ID 的两个结算点，让奶蛙盾使用相同的耐久损耗和破盾冷却。 */
@Mixin(Player.class)
abstract class FabricShieldItemMixin {
    @WrapOperation(method="hurtCurrentlyUsedShield",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean naiwa$shieldDamage(ItemStack stack, Item item, Operation<Boolean> original) {
        return stack.is(Naiwa.SHIELD.get()) || original.call(stack,item);
    }

    @WrapOperation(method="disableShield",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V"))
    private void naiwa$shieldCooldown(ItemCooldowns cooldowns, Item item, int ticks, Operation<Void> original) {
        var active = ((Player)(Object)this).getUseItem();
        original.call(cooldowns,active.is(Naiwa.SHIELD.get()) ? active.getItem() : item,ticks);
    }
}
