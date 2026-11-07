package com.naiwa.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.naiwa.NaiwaAnimations;
import com.naiwa.NaiwaShield;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.function.Consumer;

/** 盾牌物品模型取本栈同步的演出起点，探头与盾面仍朝同一方向。 */
final class NaiwaShieldRenderer implements SpecialModelRenderer<NaiwaShieldRenderer.Frame> {
    private final NaiwaModel model = new NaiwaModel();
    record Frame(double elapsed, boolean foil) {}

    @Override public Frame extractArgument(ItemStack stack) {
        var mc = Minecraft.getInstance();
        double time = mc.level == null ? 0 : mc.level.getGameTime()+mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return new Frame(NaiwaShield.elapsed(stack,time),stack.hasFoil());
    }

    @Override public void submit(Frame frame,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline) {
        if (frame == null) return;
        NaiwaShieldSkin.submit(foil || frame.foil(),pose,collector,light,overlay);
        double elapsed = frame.elapsed();
        if (elapsed<0 || elapsed>=NaiwaShield.PERFORMANCE_TICKS) return;
        double pop = Math.min(1,Math.min(elapsed/6,(40-elapsed)/8));
        var clip = NaiwaAnimations.resolve("laugh_sequence",elapsed/40*NaiwaAnimations.length("laugh_sequence"));
        pose.pushPose();
        pose.translate(0,.15+.40*pop,.13+.36*pop);
        float scale = (float)((.55+.15*pop)*pop);
        pose.scale(scale,scale,scale);
        model.submit(clip.name(),clip.seconds(),pose,collector,light,overlay,1,"",0,false,-1);
        pose.popPose();
    }

    @Override public void getExtents(Consumer<Vector3fc> out) {
        out.accept(new Vector3f(-.375F,-.6875F,-.3125F));
        out.accept(new Vector3f(.375F,.6875F,.125F));
    }

    record Unbaked() implements SpecialModelRenderer.Unbaked<Frame> {
        static final MapCodec<Unbaked> CODEC = MapCodec.unit(Unbaked::new);
        @Override public MapCodec<Unbaked> type() {return CODEC;}
        @Override public NaiwaShieldRenderer bake(SpecialModelRenderer.BakingContext context) {return new NaiwaShieldRenderer();}
    }
}
