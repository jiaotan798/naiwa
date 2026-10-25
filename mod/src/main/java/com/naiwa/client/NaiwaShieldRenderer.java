package com.naiwa.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.naiwa.NaiwaAnimations;
import com.naiwa.NaiwaShield;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** 奶蛙盾使用自己的像素纹理；探头和盾面共用朝外的 +Z，不根据观察视角翻转。 */
final class NaiwaShieldRenderer extends BlockEntityWithoutLevelRenderer {
    private final NaiwaModel model = new NaiwaModel();
    NaiwaShieldRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }
    /** 先绘制独立盾面，再按服务端同步时刻向盾外探头；允许盾面遮住持盾者视线。 */
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        NaiwaShieldSkin.draw(stack.hasFoil(),pose,buffers,light,overlay);
        double time = mc.level == null ? 0 : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        double elapsed = NaiwaShield.elapsed(stack, time);
        boolean laughing = elapsed >= 0 && elapsed < NaiwaShield.PERFORMANCE_TICKS;
        if (!laughing) return;
        // 两秒演出压缩原完整入场/大笑/收场；头从盾面向外探出，收场时连续缩回。
        double pop = Math.min(1, Math.min(elapsed / 6, (40-elapsed) / 8));
        var clip = NaiwaAnimations.resolve("laugh_sequence", elapsed / 40 * NaiwaAnimations.length("laugh_sequence"));
        pose.pushPose();
        // 同一个物品部件在所有视角都向 +Z 伸出；收场缩回零，常态只保留盾面上的像素纹章。
        // 头顶最高 0.55 格，低于盾顶 11/16 格；向外伸出即可，不为第一人称可见而越过盾缘。
        pose.translate(0,.15+.40*pop,.13+.36*pop);
        float scale = (float) ((.55+.15*pop)*pop);
        pose.scale(scale, scale, scale);
        model.draw(clip.name(), clip.seconds(), pose, buffers, light, overlay, true);
        pose.popPose();
    }
}
