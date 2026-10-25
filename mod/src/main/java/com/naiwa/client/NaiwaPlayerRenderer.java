package com.naiwa.client;

import com.naiwa.Naiwa;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 变身只替换玩家绘制，身份、装备和背包不变；NaiwaEffectSync 补齐效果变化和旁观者首次追踪的同步。 */
final class NaiwaPlayerRenderer {
    private static NaiwaModel model;
    private static boolean activated;
    private static NaiwaModel model() {if (model==null) model=new NaiwaModel();return model;}
    static void register() {
        NeoForge.EVENT_BUS.addListener(NaiwaPlayerRenderer::player);
        NeoForge.EVENT_BUS.addListener(NaiwaPlayerRenderer::hand);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            var mc=Minecraft.getInstance();
            boolean active=mc.player!=null && mc.player.hasEffect(Naiwa.ASCENDING);
            if (active && !activated) mc.gameRenderer.displayItemActivation(new ItemStack(Naiwa.TOTEM.get()));
            activated=active;
        });
    }
    private static void player(RenderPlayerEvent.Pre e) {
        var player=e.getEntity(); var effect=player.getEffect(Naiwa.ASCENDING);
        if (effect==null || player.isInvisible()) return;
        e.setCanceled(true); var pose=e.getPoseStack(); pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(e.getPartialTick(),player.yBodyRotO,player.yBodyRot)));
        int stage=effect.getAmplifier();
        // 起手映射原生展臂入场，悬停和缓降保持同一姿势；浮动只影响模型，不累积实际高度。
        String clip=stage==0?"ascend_enter":"ascend_hover";
        double time=stage==0 ? Math.clamp((15-effect.getDuration()+e.getPartialTick())*.06,0,.6) : (player.tickCount+e.getPartialTick())/20.0;
        if (stage==2) pose.translate(0,Math.sin(time*3)*.025,0);
        // 本机黑夜里让圣光照亮飞升角色；只改模型绘制亮度，不生成实际光源或影响旁观者环境。
        int light=player==Minecraft.getInstance().player&&NaiwaAscensionVisuals.active()?net.minecraft.client.renderer.LightTexture.FULL_BRIGHT:e.getPackedLight();
        model().draw(clip,time,pose,e.getMultiBufferSource(),light,OverlayTexture.NO_OVERLAY,false);
        pose.popPose();
    }
    /** 飞升时第一人称不显示手或手持物，只取消绘制；效果结束后自然交回原版。 */
    private static void hand(RenderHandEvent e) {
        var player=Minecraft.getInstance().player;
        if (player==null) return;
        if (player.hasEffect(Naiwa.ASCENDING)) e.setCanceled(true);
    }
}
