package com.naiwa;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 飞升只同步服务端裁定的高度，避免普通传送的确认等待阻塞玩家横向移动包。 */
public record NaiwaFlightHeight(double height) implements CustomPacketPayload {
    public static final Type<NaiwaFlightHeight> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("naiwa","flight_height"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NaiwaFlightHeight> CODEC=StreamCodec.composite(ByteBufCodecs.DOUBLE,NaiwaFlightHeight::height,NaiwaFlightHeight::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    /** 保留水平位置、速度和原版碰撞；陈旧或非法高度不能挪动已结束飞升的玩家。 */
    public static void apply(Player player,double height){
        if(!player.isAlive()||!player.hasEffect(Naiwa.ASCENDING)||!Double.isFinite(height)||Math.abs(height-player.getY())>64)return;
        player.move(MoverType.SELF,new Vec3(0,height-player.getY(),0));
        player.setDeltaMovement(player.getDeltaMovement().multiply(1,0,1));player.fallDistance=0;
    }
}
