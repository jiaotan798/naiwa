package com.naiwa.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.naiwa.NaiwaPranks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 只在成功接管已计算的真实钻石后移除对应掉落，经验、时运和其它掉落继续由原版处理。 */
@Mixin(Block.class)
abstract class FabricBlockDropsMixin {
    @ModifyExpressionValue(method="dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"))
    private static List<ItemStack> naiwa$diamond(List<ItemStack> drops, BlockState state, Level level, BlockPos pos,
                                                BlockEntity blockEntity, Entity breaker, ItemStack tool) {
        if (!(breaker instanceof ServerPlayer player) || !state.is(BlockTags.DIAMOND_ORES) || !tool.is(ItemTags.PICKAXES)) return drops;
        var diamonds = drops.stream().filter(stack -> stack.is(Items.DIAMOND)).map(ItemStack::copy).toList();
        return !diamonds.isEmpty() && NaiwaPranks.startDiamond(player,diamonds)
                ? drops.stream().filter(stack -> !stack.is(Items.DIAMOND)).toList() : drops;
    }
}
