package com.naiwa.client;

import com.naiwa.NaiwaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/** 保留三角网格与源贴图，模型脚底对应实体原点；骨骼演出不改变服务端碰撞。 */
public final class NaiwaRenderer extends EntityRenderer<NaiwaEntity> {
    private final NaiwaModel model = new NaiwaModel();
    private final NaiwaModel simplified = new NaiwaModel(model);
    private final NaiwaDetail detail = new NaiwaDetail();

    public NaiwaRenderer(EntityRendererProvider.Context context) {
        super(context); shadowRadius = .45F;
    }

    @Override public ResourceLocation getTextureLocation(NaiwaEntity entity) {return model.texture();}

    @Override public boolean shouldRender(NaiwaEntity entity, Frustum frustum, double x, double y, double z) {
        // 捧腹、挥手和展臂超出身体碰撞箱；裁剪须覆盖这些姿势。
        return entity.shouldRender(x,y,z) && frustum.isVisible(entity.getBoundingBox().inflate(1.5));
    }

    /** 按主相机决定可见网格档位，避免用光影的阴影投影矩阵估算屏幕大小。 */
    boolean usesSimplified(NaiwaEntity entity) {
        if (entity.act().equals("chest_peek") || entity.getMainHandItem().is(Items.ENCHANTED_BOOK)) return false;
        var client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera();
        if (camera.getEntity() instanceof Player player && player.isScoping()) return false;
        return detail.useSimplified(entity, entity.distanceToSqr(camera.getPosition()), client.getWindow().getHeight(), client.options.fov().get());
    }

    int detailFaceCount(boolean useSimplified) {return (useSimplified ? simplified : model).faceCount();}

    @Override public void render(NaiwaEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        var clip = entity.clip(partialTick);
        boolean holdingBook=entity.getMainHandItem().is(Items.ENCHANTED_BOOK);
        pose.pushPose();
        // 原模型朝 +Z、16 编辑单位为 1 格，不沿用原版方块生物的倒置 Y 轴。
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));
        if(entity.isShiftKeyDown()) pose.scale(1,.8F,1);
        shadowRadius=entity.act().equals("chest_peek")?0:.45F;
        if(entity.act().equals("chest_peek")) {
            // 实体脚部在闭合箱体顶面；渲染降到开盖后的箱沿，只有现有头部从箱内露出。
            pose.pushPose();pose.translate(0,-.25,0);pose.scale(.9F,.9F,.9F);
            model.drawChestHead(clip.name(),clip.seconds(),pose,buffers,light,OverlayTexture.pack(0,entity.hurtTime>0||entity.deathTime>0),entity.act(),entity.getXRot(),holdingBook);pose.popPose();
        } else {
            // 阴影只需要轮廓，复用低模可少提交一轮高模顶点。持书仍走原手掌定位。
            boolean low = usesSimplified(entity) || !holdingBook && NaiwaShadowPass.active();
            var body = low ? simplified : model;
            body.drawAct(clip.name(),clip.seconds(),pose,buffers,light,OverlayTexture.pack(0,entity.hurtTime>0||entity.deathTime>0),entity.act(),entity.getXRot(),holdingBook);
        }
        if(entity.act().startsWith("mine")) {
            pose.pushPose();
            boolean hidden=entity.act().equals("mine_hide");
            pose.translate(-.45,1.15,hidden?-.5:.65);
            pose.mulPose(Axis.XP.rotationDegrees(hidden?30:(float)Math.sin(clip.seconds()*7)*30));
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(entity.getMainHandItem(),net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    light,OverlayTexture.NO_OVERLAY,pose,buffers,entity.level(),entity.getId());
            pose.popPose();
        }
        if(holdingBook) {
            pose.pushPose();
            model.translateRightHand(pose);
            // 书面朝外供玩家看清，位置跟随手掌；继续使用同步的主手物品和原版附魔光效。
            pose.scale(.7F,.7F,.7F);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(entity.getMainHandItem(),net.minecraft.world.item.ItemDisplayContext.FIXED,
                    light,OverlayTexture.NO_OVERLAY,pose,buffers,entity.level(),entity.getId());
            pose.popPose();
        }
        pose.popPose(); super.render(entity,yaw,partialTick,pose,buffers,light);
    }
}
