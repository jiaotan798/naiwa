package com.naiwa.client;

import com.naiwa.NaiwaScopeRequest;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 真实开镜申请四格演员；服务端同步本轮观看者后才在原版镜框内显示参考图片。 */
final class NaiwaScopeTrigger {
    private static int lastRequest=-100;
    static void register(){NeoForge.EVENT_BUS.addListener(NaiwaScopeTrigger::render);}
    private static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        if(mc.player==null||!mc.player.isScoping()){lastRequest=-100;return;}
        // 等原版开镜缩放稳定再出场；之后每秒重试一次，服务器拒绝冷却中或已有场景的重复请求。
        int used=mc.player.getTicksUsingItem();if(used<12||used-lastRequest<20)return;
        PacketDistributor.sendToServer(new NaiwaScopeRequest(4));lastRequest=used;
    }

    /** 不按持有物品或附近任意奶蛙显示；松开、换世界、演员离场及打开界面均即时撤图。 */
    static void portrait(net.minecraft.client.gui.GuiGraphics graphics) {
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.player.isScoping()||mc.screen!=null)return;
        boolean owned=false;
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof com.naiwa.NaiwaEntity frog&&frog.isAlive()&&frog.scopeViewer()==mc.player.getId()){owned=true;break;}
        if(!owned)return;
        // 原版镜框内窗占 220/256，稳定开镜比例为 1.125；参考图片 300×314，保持原比例居中。
        int side=Math.round(Math.min(graphics.guiWidth(),graphics.guiHeight())*1.125F*220/256);
        int x=(graphics.guiWidth()-side)/2,y=(graphics.guiHeight()-side)/2,width=Math.round(side*300F/314);
        graphics.fill(x,y,x+side,y+side,0xFFB8B8B8);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        graphics.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("naiwa","textures/gui/scope_portrait.png"),
                x+(side-width)/2,y,width,side,0,0,300,314,300,314);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
}
