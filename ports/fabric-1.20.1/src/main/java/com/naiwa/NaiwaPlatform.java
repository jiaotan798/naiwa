package com.naiwa;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Fabric 版把共享玩法接到原版实体存档、世界和网络接口。 */
final class NaiwaPlatform {
    private NaiwaPlatform() {}

    static CompoundTag playerData(Player player) {return ((PlayerDataAccess)player).naiwa$data();}

    static boolean isInLevel(Entity entity) {return entity.level().getEntity(entity.getId()) == entity;}

    // Fabric 没有 NeoForge 的图腾取消事件；进入原版保命入口时使用原版规则。
    static boolean allowTotem(ServerPlayer player, DamageSource damage, ItemStack stack, InteractionHand hand) {return true;}

    static void cureTotemEffects(ServerPlayer player) {player.removeAllEffects();}

    static void syncFlightHeight(ServerPlayer player) {
        var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buffer.writeDouble(player.getY());
        ServerPlayNetworking.send(player,NaiwaFlightHeight.TYPE,buffer);
    }
}
