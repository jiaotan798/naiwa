package com.naiwa;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.network.PacketDistributor;

/** NeoForge 提供的存档、实体入场与图腾接口；共享玩法只决定调用时机。 */
final class NaiwaPlatform {
    private NaiwaPlatform() {}

    static CompoundTag playerData(Player player) {return player.getPersistentData();}
    static boolean isInLevel(Entity entity) {return entity.isAddedToLevel();}

    static boolean allowTotem(ServerPlayer player, DamageSource damage, ItemStack stack, InteractionHand hand) {
        return CommonHooks.onLivingUseTotem(player, damage, stack, hand);
    }

    static void cureTotemEffects(ServerPlayer player) {player.removeAllEffects();}

    static void syncFlightHeight(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new NaiwaFlightHeight(player.getY()));
    }
}
