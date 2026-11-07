package com.naiwa.mixin;

import com.naiwa.NaiwaEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原版船由驾驶客户端计算移动；补充停手时的划桨输入，保留原版浮力、碰撞及车辆同步。 */
@Mixin(Boat.class)
abstract class BoatMixin {
    @Shadow private boolean inputLeft;
    @Shadow private boolean inputRight;
    @Shadow private boolean inputUp;

    @Inject(method="setInput",at=@At("TAIL"))
    private void naiwa$steer(boolean left,boolean right,boolean up,boolean down,CallbackInfo callback) {
        // 原始按键优先：即使停止指令还在网络途中，玩家也能在当前帧重新接管。
        if(left||right||up||down)return;
        Boat boat=(Boat)(Object)this;
        NaiwaEntity frog=naiwa$boatActor();
        if(frog==null||!frog.animation().equals("auto"))return;
        int command=frog.boatSteering();
        if(command<1||command>3)return;
        inputUp=true;
        // 每四 tick 轻划一次方向桨，避免持续按左右键积累成原地高速转圈。
        inputLeft=command==2&&boat.tickCount%4==0;
        inputRight=command==3&&boat.tickCount%4==0;
    }

    @Unique
    private NaiwaEntity naiwa$boatActor() {
        var passengers=((Boat)(Object)this).getPassengers();
        return passengers.size()==2&&passengers.getFirst() instanceof Player
                &&passengers.get(1) instanceof NaiwaEntity frog&&(frog.act().equals("boat")||frog.boatSteering()==4)?frog:null;
    }

    @WrapOperation(method="clampRotation",at=@At(value="INVOKE",target="Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private float naiwa$lookBehind(float angle,float min,float max,Operation<Float> original) {
        // 原版 ±105° 看不到正后座；本轮允许玩家自行回头，大笑期间保留，退场即恢复原限制。
        return naiwa$boatActor()!=null?original.call(angle,-180F,180F):original.call(angle,min,max);
    }
}
