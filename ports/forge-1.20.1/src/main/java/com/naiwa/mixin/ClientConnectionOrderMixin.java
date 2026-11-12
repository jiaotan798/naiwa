package com.naiwa.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将旧版客户端的协议判断、读开关和写包串行到连接事件循环，保持原生超时与回调行为。 */
@Mixin(Connection.class)
abstract class ClientConnectionOrderMixin {
    @Shadow private Channel channel;
    @Shadow private void sendPacket(Packet<?> packet, PacketSendListener listener) {throw new AssertionError();}

    /** 原生 send 已保证连接存在；只调度跨线程调用，事件循环内继续执行原实现。 */
    @Inject(method="sendPacket",at=@At("HEAD"),cancellable=true)
    private void naiwa$orderProtocolChange(Packet<?> packet, PacketSendListener listener, CallbackInfo info) {
        if (!channel.eventLoop().inEventLoop()) {
            // 连接激活期间从渲染线程关闭读事件，会与 Forge 延后的 autoRead 恢复竞争，留下 autoRead=true 但 OP_READ=0。
            // 必须连协议状态判断一起移入事件循环，不能只把最后的 writeAndFlush 入队。
            channel.eventLoop().execute(() -> sendPacket(packet,listener));
            info.cancel();
        }
    }
}
