package com.naiwa.client;

import com.naiwa.NaiwaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 保留三角网格与源贴图，模型脚底对应实体原点；骨骼演出不改变服务端碰撞。 */
public final class NaiwaRenderer extends EntityRenderer<NaiwaEntity> {
    private final NaiwaModel model = new NaiwaModel();

    public NaiwaRenderer(EntityRendererProvider.Context context) {
        super(context); shadowRadius = .45F;
    }

    @Override public ResourceLocation getTextureLocation(NaiwaEntity entity) {return model.texture();}

    @Override public boolean shouldRender(NaiwaEntity entity, Frustum frustum, double x, double y, double z) {
        // 捧腹、挥手和展臂超出身体碰撞箱；裁剪须覆盖这些姿势。
        return entity.shouldRender(x,y,z) && frustum.isVisible(entity.getBoundingBox().inflate(1.5));
    }

    @Override public void render(NaiwaEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        var clip = entity.clip(partialTick);
        pose.pushPose();
        // 原模型朝 +Z、16 编辑单位为 1 格，不沿用原版方块生物的倒置 Y 轴。
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));
        if(entity.isShiftKeyDown()) pose.scale(1,.8F,1);
        shadowRadius=entity.act().equals("chest_peek")?0:.45F;
        if(entity.act().equals("chest_peek")) {
            // 实体脚部在闭合箱体顶面；渲染降到开盖后的箱沿，只有现有头部从箱内露出。
            pose.pushPose();pose.translate(0,-.25,0);pose.scale(.9F,.9F,.9F);
            model.drawChestHead(clip.name(),clip.seconds(),pose,buffers,light,OverlayTexture.pack(0,entity.hurtTime>0||entity.deathTime>0));pose.popPose();
        } else model.drawAct(clip.name(),clip.seconds(),pose,buffers,light,OverlayTexture.pack(0,entity.hurtTime>0||entity.deathTime>0),entity.act(),entity.getXRot());
        if(entity.act().startsWith("mine")) {
            pose.pushPose();
            boolean hidden=entity.act().equals("mine_hide");
            pose.translate(-.45,1.15,hidden?-.5:.65);
            pose.mulPose(Axis.XP.rotationDegrees(hidden?30:(float)Math.sin(clip.seconds()*7)*30));
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(entity.getMainHandItem(),net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    light,OverlayTexture.NO_OVERLAY,pose,buffers,entity.level(),entity.getId());
            pose.popPose();
        }
        if(entity.getMainHandItem().is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
            pose.pushPose();
            model.translateRightHand(clip.name(),clip.seconds(),entity.act(),entity.getXRot(),pose);
            // 书面朝外供玩家看清，位置跟随手掌；继续使用同步的主手物品和原版附魔光效。
            pose.scale(.7F,.7F,.7F);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(entity.getMainHandItem(),net.minecraft.world.item.ItemDisplayContext.FIXED,
                    light,OverlayTexture.NO_OVERLAY,pose,buffers,entity.level(),entity.getId());
            pose.popPose();
        }
        pose.popPose(); super.render(entity,yaw,partialTick,pose,buffers,light);
    }
}
