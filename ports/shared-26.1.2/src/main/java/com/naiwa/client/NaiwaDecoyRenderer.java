package com.naiwa.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.naiwa.NaiwaDecoy;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 渲染诱饵使用原版钻石的物品模型，真实掉落仍只由服务端保管。 */
final class NaiwaDecoyRenderer extends EntityRenderer<NaiwaDecoy,NaiwaDecoyRenderer.State> {
    private ItemStack diamond;
    private final ItemModelResolver items;

    NaiwaDecoyRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemModelResolver();
        shadowRadius = .15F;
    }

    static final class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        double time;
    }

    @Override public State createRenderState() {return new State();}

    @Override public void extractRenderState(NaiwaDecoy entity,State state,float partial) {
        super.extractRenderState(entity,state,partial);
        // 标题页重载会提前构造渲染器，此时新版物品组件尚未绑定到当前世界。
        if (diamond == null) diamond = new ItemStack(Items.DIAMOND);
        state.time = entity.level().getGameTime()+partial;
        items.updateForNonLiving(state.item,diamond,ItemDisplayContext.GROUND,entity);
    }

    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if (state.isInvisible) return;
        pose.pushPose();
        pose.translate(0,.18+Math.sin(state.time*.1)*.04,0);
        pose.mulPose(Axis.YP.rotationDegrees((float)state.time*3));
        pose.scale(.6F,.6F,.6F);
        state.item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        pose.popPose();
        super.submit(state,pose,collector,camera);
    }
}
