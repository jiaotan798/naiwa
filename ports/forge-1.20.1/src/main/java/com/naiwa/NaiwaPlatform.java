package com.naiwa;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.network.PacketDistributor;

/** Forge 提供的存档、实体入场与图腾接口；共享玩法只决定调用时机。 */
final class NaiwaPlatform {
    private NaiwaPlatform() {}

    static CompoundTag playerData(Player player) {return player.getPersistentData();}
    static boolean isInLevel(Entity entity) {return entity.isAddedToWorld();}

    static boolean allowTotem(ServerPlayer player, DamageSource damage, ItemStack stack, InteractionHand hand) {
        return ForgeHooks.onLivingUseTotem(player, damage, stack, hand);
    }

    static void cureTotemEffects(ServerPlayer player) {player.removeAllEffects();}

    static void syncFlightHeight(ServerPlayer player) {
        NaiwaForgeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),new NaiwaFlightHeight(player.getY()));
    }
}
