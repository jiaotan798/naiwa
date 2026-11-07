package com.naiwa.client;

import com.naiwa.Naiwa;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** 变身只替换玩家绘制，身份、装备和背包不变；NaiwaEffectSync 补齐效果变化和旁观者首次追踪的同步。 */
public final class NaiwaPlayerRenderer {
    private static NaiwaModel model;
    private static boolean activated;
    private static NaiwaModel model() {if (model==null) model=new NaiwaModel();return model;}
    static void tick() {
        var mc=Minecraft.getInstance();
        boolean active=mc.player!=null && mc.player.hasEffect(Naiwa.ASCENDING);
        if (active && !activated) mc.gameRenderer.displayItemActivation(new ItemStack(Naiwa.TOTEM.get()));
        activated=active;
    }
    /** 返回 true 时由飞升模型接管绘制；世界通道会延迟到光影合成之后再画。 */
    public static boolean replacePlayer(net.minecraft.world.entity.player.Player player, PoseStack pose, MultiBufferSource buffers, int packedLight, float partial) {
        var effect=player.getEffect(Naiwa.ASCENDING);
        if (effect==null || player.isInvisible()) return false;
        // 主视图实体推迟至光影完成后绘制；天空之前的 Iris 阴影与物品栏预览仍沿用原模型入口。
        if(NaiwaAscensionVisuals.worldPass())return true;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot)));
        int stage=effect.getAmplifier();
        // 起手映射原生展臂入场，悬停和缓降保持同一姿势；浮动只影响模型，不累积实际高度。
        String clip=stage==0?"ascend_enter":"ascend_hover";
        double time=stage==0 ? net.minecraft.util.Mth.clamp((15-effect.getDuration()+partial)*.06,0,.6) : (player.tickCount+partial)/20.0;
        if (stage==2) pose.translate(0,Math.sin(time*3)*.025,0);
        // 本机黑夜里让圣光照亮飞升角色；只改模型绘制亮度，不生成实际光源或影响旁观者环境。
        int light=player==Minecraft.getInstance().player&&NaiwaAscensionVisuals.active()?net.minecraft.client.renderer.LightTexture.FULL_BRIGHT:packedLight;
        model().draw(clip,time,pose,buffers,light,OverlayTexture.NO_OVERLAY,false);
        pose.popPose();
        return true;
    }

    /** 使用与原玩家替换相同的朝向和片段，位置按实际渲染分数帧插值，不移动实体。 */
    static void world(net.minecraft.world.entity.player.Player player,float partial,net.minecraft.client.Camera camera,
                      net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers) {
        var effect=player.getEffect(Naiwa.ASCENDING);if(effect==null)return;
        if(player==Minecraft.getInstance().player&&!camera.isDetached())return;
        var pose=new com.mojang.blaze3d.vertex.PoseStack();var eye=camera.getPosition();
        pose.translate(Mth.lerp(partial,player.xOld,player.getX())-eye.x,Mth.lerp(partial,player.yOld,player.getY())-eye.y,Mth.lerp(partial,player.zOld,player.getZ())-eye.z);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot)));
        int stage=effect.getAmplifier();
        double time=stage==0?net.minecraft.util.Mth.clamp((15-effect.getDuration()+partial)*.06,0,.6):(player.tickCount+partial)/20.0;
        if(stage==2)pose.translate(0,Math.sin(time*3)*.025,0);
        float reveal=stage==0?(float)Mth.smoothstep(Mth.clamp((15-effect.getDuration()+partial)/10F,0,1)):1;
        model().drawFlight(stage==0?"ascend_enter":"ascend_hover",time,pose,buffers,reveal);
    }
    /** 飞升时第一人称不显示手或手持物，只取消绘制；效果结束后自然交回原版。 */
    static boolean hidesHands() {
        var player=Minecraft.getInstance().player;
        return player!=null && player.hasEffect(Naiwa.ASCENDING);
    }
}
