package com.naiwa;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** 客户端申请固定四格演员；使用状态、冷却、碰撞及镜内图片标记均由服务器裁定。 */
public record NaiwaScopeRequest(int distance) implements CustomPacketPayload {
    public static final Type<NaiwaScopeRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("naiwa","scope_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NaiwaScopeRequest> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,NaiwaScopeRequest::distance,NaiwaScopeRequest::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    /** 服务端拒绝任意距离和不满足当前开镜条件的请求。 */
    public void apply(ServerPlayer player) {
        if(distance==4)NaiwaPranks.startScope(player,distance);
    }
}
