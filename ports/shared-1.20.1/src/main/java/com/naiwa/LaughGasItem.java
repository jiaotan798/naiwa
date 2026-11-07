package com.naiwa;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 投掷成功才消耗瓶子；空瓶返还仅由工作台配方处理，破碎不返瓶。 */
public final class LaughGasItem extends Item {
    public LaughGasItem(Properties properties) {super(properties);}

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            var projectile = new LaughGasProjectile(Naiwa.GAS_PROJECTILE.get(), player, level);
            projectile.setItem(stack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), -20, .7F, 1);
            if (!level.addFreshEntity(projectile)) return InteractionResultHolder.fail(stack);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
