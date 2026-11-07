package com.naiwa.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.naiwa.Naiwa;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** 用飞升模型接管角色提交，使用已同步的真实阶段与剩余时间。 */
public final class NaiwaPlayerRenderer {
    private static NaiwaModel model;
    private static boolean activated;
    private NaiwaPlayerRenderer() {}

    static void tick() {
        var mc = Minecraft.getInstance();
        boolean active = mc.player != null && mc.player.hasEffect(Naiwa.ASCENDING);
        if (active && !activated) mc.gameRenderer.displayItemActivation(new ItemStack(Naiwa.TOTEM.get()));
        activated=active;
    }

    public static boolean submit(AvatarRenderState avatar,PoseStack pose,SubmitNodeCollector collector) {
        var flight = (NaiwaRenderState)avatar;
        int stage = flight.naiwa$stage();
        if (stage<0 || avatar.isInvisible) return false;
        if (model == null) model = new NaiwaModel();
        float phase = 15-flight.naiwa$remaining()+flight.naiwa$partial();
        double time = stage==0 ? Math.clamp(phase*.06,0,.6) : avatar.ageInTicks/20.0;
        float reveal = stage==0 ? (float)Mth.smoothstep(Mth.clamp(phase/10F,0,1)) : 1;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-avatar.bodyRot));
        if (stage==2) pose.translate(0,Math.sin(time*3)*.025,0);
        model.submit(stage==0 ? "ascend_enter" : "ascend_hover",time,pose,collector,avatar.lightCoords,OverlayTexture.NO_OVERLAY,
                0,"",0,false,reveal);
        pose.popPose();
        return true;
    }
}
