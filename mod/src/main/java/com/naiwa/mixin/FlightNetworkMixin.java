package com.naiwa.mixin;

import com.naiwa.Naiwa;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 图腾悬停是服务端授予的受控状态，仅在该状态中豁免原版悬空踢出计数。 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class FlightNetworkMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Inject(method="tick",at=@At("HEAD"))
    private void naiwa$allowedHover(CallbackInfo info){if(player.hasEffect(Naiwa.ASCENDING))clientIsFloating=false;}
}
