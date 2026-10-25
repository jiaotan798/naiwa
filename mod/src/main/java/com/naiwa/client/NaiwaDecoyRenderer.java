package com.naiwa.client;

import com.naiwa.NaiwaDecoy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 只绘制诱饵；真实暂存栈由服务端持有，大笑期间隐藏，退场后才生成原版物品实体。 */
final class NaiwaDecoyRenderer extends EntityRenderer<NaiwaDecoy> {
    private final ItemStack diamond=new ItemStack(Items.DIAMOND);
    NaiwaDecoyRenderer(EntityRendererProvider.Context context) {super(context);shadowRadius=.15F;}
    @Override public ResourceLocation getTextureLocation(NaiwaDecoy entity) {return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;}
    @Override public void render(NaiwaDecoy entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        if(entity.isInvisible())return;
        double time=entity.level().getGameTime()+partial;
        pose.pushPose();pose.translate(0,.18+Math.sin(time*.1)*.04,0);pose.mulPose(Axis.YP.rotationDegrees((float)time*3));
        pose.scale(.6F,.6F,.6F);
        Minecraft.getInstance().getItemRenderer().renderStatic(diamond,ItemDisplayContext.GROUND,light,OverlayTexture.NO_OVERLAY,pose,buffers,entity.level(),entity.getId());
        pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
    }
}
