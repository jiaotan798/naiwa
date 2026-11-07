package com.naiwa;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** 客户端申请固定四格演员；使用状态、冷却、碰撞及镜内图片标记均由服务器裁定。 */
public record NaiwaScopeRequest(int distance) {
    public static final ResourceLocation TYPE = new ResourceLocation("naiwa","scope_view");
    /** 服务端拒绝任意距离和不满足当前开镜条件的请求。 */
    public void apply(ServerPlayer player) {
        if(distance==4)NaiwaPranks.startScope(player,distance);
    }
}
