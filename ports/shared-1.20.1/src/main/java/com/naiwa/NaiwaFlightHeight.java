package com.naiwa;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 飞升只同步服务端裁定的高度，避免普通传送的确认等待阻塞玩家横向移动包。 */
public record NaiwaFlightHeight(double height) {
    public static final ResourceLocation TYPE = new ResourceLocation("naiwa","flight_height");
    /** 保留水平位置、速度和原版碰撞；陈旧或非法高度不能挪动已结束飞升的玩家。 */
    public static void apply(Player player,double height){
        if(!player.isAlive()||!player.hasEffect(Naiwa.ASCENDING)||!Double.isFinite(height)||Math.abs(height-player.getY())>64)return;
        player.move(MoverType.SELF,new Vec3(0,height-player.getY(),0));
        player.setDeltaMovement(player.getDeltaMovement().multiply(1,0,1));player.fallDistance=0;
    }
}
