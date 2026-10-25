package com.naiwa.client;

import com.naiwa.NaiwaScopeRequest;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 按原版实际缩放后的投影申请前方演员，不改 FOV；窄视野或窄窗口会自动使用更远距离。 */
final class NaiwaScopeTrigger {
    private static int lastRequest=-100;
    static void register(){NeoForge.EVENT_BUS.addListener(NaiwaScopeTrigger::render);}
    private static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        if(mc.player==null||!mc.player.isScoping()){lastRequest=-100;return;}
        // 等原版开镜缩放稳定再出场；之后每秒重试一次，服务器拒绝冷却中或已有场景的重复请求。
        int used=mc.player.getTicksUsingItem();if(used<12||used-lastRequest<20)return;
        float width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight(),side=Math.min(width,height);
        var projection=event.getProjectionMatrix();double focal=Math.max(Math.abs(projection.m00())*width/side,Math.abs(projection.m11())*height/side);
        // 0.65 格包住当前源模型头部相对眼高的投影，并留镜框余量；由原版遮罩逐顶点实测核对。
        int distance=(int)Math.ceil(.65*focal);if(distance<8)distance=8;
        if(distance<=64)PacketDistributor.sendToServer(new NaiwaScopeRequest(distance));lastRequest=used;
    }
}
