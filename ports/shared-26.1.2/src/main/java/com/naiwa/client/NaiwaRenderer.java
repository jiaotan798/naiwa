package com.naiwa.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.naiwa.NaiwaAnimations;
import com.naiwa.NaiwaEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;

/** 实体与道具先提取为帧状态，再按原模型原点提交到新版渲染队列。 */
public final class NaiwaRenderer extends EntityRenderer<NaiwaEntity,NaiwaRenderer.State> {
    private final ItemModelResolver items;
    private final NaiwaModel model = new NaiwaModel();

    public NaiwaRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemModelResolver();
        shadowRadius = .45F;
    }

    public static final class State extends EntityRenderState {
        NaiwaAnimations.ClipTime clip;
        String action;
        boolean crouched, holdingBook, hurt;
        float bodyYaw, pitch;
        final ItemStackRenderState prop = new ItemStackRenderState();
    }

    @Override public State createRenderState() {return new State();}

    @Override public boolean shouldRender(NaiwaEntity entity,Frustum frustum,double x,double y,double z) {
        return entity.shouldRender(x,y,z) && frustum.isVisible(entity.getBoundingBox().inflate(1.5));
    }

    @Override public void extractRenderState(NaiwaEntity entity,State state,float partial) {
        super.extractRenderState(entity,state,partial);
        state.clip = entity.clip(partial);
        state.action = entity.act();
        state.crouched = entity.isShiftKeyDown();
        state.holdingBook = entity.getMainHandItem().is(Items.ENCHANTED_BOOK);
        state.hurt = entity.hurtTime>0 || entity.deathTime>0;
        state.bodyYaw = Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot);
        state.pitch = entity.getXRot();
        state.prop.clear();
        if (state.holdingBook || state.action.startsWith("mine"))
            items.updateForLiving(state.prop,entity.getMainHandItem(),state.holdingBook ? ItemDisplayContext.FIXED : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,entity);
    }

    @Override protected float getShadowRadius(State state) {return "chest_peek".equals(state.action) ? 0 : .45F;}

    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if (!state.isInvisible) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-state.bodyYaw));
            if (state.crouched) pose.scale(1,.8F,1);
            boolean chest = state.action.equals("chest_peek");
            if (chest) {
                pose.pushPose();
                pose.translate(0,-.25,0);
                pose.scale(.9F,.9F,.9F);
            }
            model.submit(state.clip.name(),state.clip.seconds(),pose,collector,state.lightCoords,OverlayTexture.pack(0,state.hurt),
                    chest ? 4 : 0,state.action,state.pitch,state.holdingBook,-1);
            if (chest) pose.popPose();
            if (state.holdingBook || state.action.startsWith("mine")) {
                pose.pushPose();
                if (state.holdingBook) {
                    model.translateRightHand(pose);
                    pose.scale(.7F,.7F,.7F);
                } else {
                    boolean hidden = state.action.equals("mine_hide");
                    pose.translate(-.45,1.15,hidden ? -.5 : .65);
                    pose.mulPose(Axis.XP.rotationDegrees(hidden ? 30 : (float)Math.sin(state.clip.seconds()*7)*30));
                }
                state.prop.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
                pose.popPose();
            }
            pose.popPose();
        }
        super.submit(state,pose,collector,camera);
    }
}
