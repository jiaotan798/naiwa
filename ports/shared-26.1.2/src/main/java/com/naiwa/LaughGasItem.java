package com.naiwa;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 投掷成功才消耗瓶子；空瓶返还仅由工作台配方处理，破碎不返瓶。 */
public final class LaughGasItem extends Item {
    public LaughGasItem(Properties properties) {super(properties);}

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            var projectile = new LaughGasProjectile(Naiwa.GAS_PROJECTILE.get(),player,level,stack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), -20, .7F, 1);
            if (!level.addFreshEntity(projectile)) return InteractionResult.FAIL;
            stack.consume(1, player);
            player.getCooldowns().addCooldown(stack,10);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
}
