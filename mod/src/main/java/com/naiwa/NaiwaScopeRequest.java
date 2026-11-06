package com.naiwa;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 客户端申请固定四格演员；使用状态、冷却、碰撞及镜内图片标记均由服务器裁定。 */
public record NaiwaScopeRequest(int distance) implements CustomPacketPayload {
    public static final Type<NaiwaScopeRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("naiwa","scope_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NaiwaScopeRequest> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,NaiwaScopeRequest::distance,NaiwaScopeRequest::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(TYPE,CODEC,(request,context)->context.enqueueWork(()->{
            // 防止伪造负数、极远坐标或重复请求绕过场景规则；不为候选点加载新区块。
            if(request.distance==4&&context.player() instanceof ServerPlayer player)NaiwaPranks.startScope(player,request.distance);
        }));
    }
}
