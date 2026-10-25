package com.naiwa.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.naiwa.Naiwa;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 本机飞升的黑夜、雾色与锥状圣光；只改本机绘制，不写世界时间、天气、光源或方块。 */
public final class NaiwaAscensionVisuals {
    private static final State STATE=new State();
    private static final ResourceLocation BEAM_TEXTURE=ResourceLocation.fromNamespaceAndPath("naiwa","textures/effect/ascension_beam.png");
    // 单位格；连续透明纹理提供径向衰减，避免摄像机进入实体锥面时出现分层色带。
    private static final float HEIGHT=32;
    private static ShaderInstance shader;
    private NaiwaAscensionVisuals() {}

    /** 独立透明着色器保留连续 alpha，不让光影把圣光归类为信标或发光实体后裁出硬边。 */
    static void shaders(RegisterShadersEvent event) {
        try {event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("naiwa","holy_light"),DefaultVertexFormat.POSITION_TEX_COLOR),loaded->shader=loaded);}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException("无法加载奶蛙圣光着色器",failure);}
    }

    public static boolean active() {
        var mc=Minecraft.getInstance();
        return mc.level!=null&&mc.player!=null&&mc.player.level()==mc.level&&mc.player.isAlive()
                &&!mc.player.isSpectator()&&mc.player.hasEffect(Naiwa.ASCENDING);
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->refresh());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event)->{
            if(STATE.update(null,-1,0,0))Minecraft.getInstance().gameRenderer.lightTexture().tick();
        });
        NeoForge.EVENT_BUS.addListener((ViewportEvent.ComputeFogColor event)->{
            if(active()) {event.setRed(.012F);event.setGreen(.016F);event.setBlue(.035F);}
        });
        NeoForge.EVENT_BUS.addListener(NaiwaAscensionVisuals::render);
    }

    /** 同步包到达后的第一帧也初始化；结束即使不再绘制圣光，仍刷新缓存光照恢复正常。 */
    private static void refresh() {
        var mc=Minecraft.getInstance();var effect=active()?mc.player.getEffect(Naiwa.ASCENDING):null;
        if(STATE.update(effect==null?null:mc.player,effect==null?-1:effect.getAmplifier(),effect==null?0:effect.getDuration(),effect==null?0:mc.player.getY()))
            mc.gameRenderer.lightTexture().tick();
    }

    private static void render(RenderLevelStageEvent event) {
        // 通用信标/发光实体通道会被光影重写 alpha 和泛光；独立通道只输出柔光本身，保留地形深度测试。
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        refresh();if(!active()||shader==null)return;
        var mc=Minecraft.getInstance();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // 光影的天气阶段仍可能绑定其内部 G-buffer；最终世界阶段明确输出到主画布，避免圣光被后续合成覆盖。
        mc.getMainRenderTarget().bindWrite(false);
        // 世界结束后全局矩阵可能已为手部/界面重置；明确使用事件中的世界矩阵，并在绘制后还原。
        var view=RenderSystem.getModelViewStack();view.pushMatrix();view.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(),sorting);
        try {
        var position=new Vec3(Mth.lerp(partial,mc.player.xOld,mc.player.getX()),mc.player.getY(),Mth.lerp(partial,mc.player.zOld,mc.player.getZ()));
        Matrix4f transform=beamTransform(position,STATE.baseY,event.getCamera().getPosition());
        var buffers=mc.renderBuffers().bufferSource();var type=BeamLayer.TYPE;
        float tick=mc.player.tickCount+partial;
        var effect=mc.player.getEffect(Naiwa.ASCENDING);
        // 起手半秒渐亮，缓降最后半秒渐隐；阶段切换不重新起拍，黑夜仍立即生效。
        float strength=(float)(effect.getAmplifier()==0?Mth.smoothstep(Mth.clamp((15-effect.getDuration()+partial)/10F,0,1))
                :effect.getAmplifier()==3?Mth.smoothstep(Mth.clamp((effect.getDuration()-5-partial)/10F,0,1)):1);
        var right=new Vector3f(1,0,0).rotate(event.getCamera().rotation());right.y=0;right.normalize();
        try {beam(buffers.getBuffer(type),transform,tick,strength,right);}
        finally {buffers.endBatch(type);}
        var dust=RenderType.dragonRays();
        try {sparkles(buffers.getBuffer(dust),transform,tick,strength,event.getCamera().rotation());}
        finally {buffers.endBatch(dust);}
        } finally {RenderSystem.setProjectionMatrix(projection,sorting);view.popMatrix();RenderSystem.applyModelViewMatrix();}
    }

    /** 此阶段原版 ModelView 已含相机旋转；这里只平移相对坐标，否则会二次旋转并偏离玩家。 */
    static Matrix4f beamTransform(Vec3 player,double baseY,Vec3 camera) {
        return new Matrix4f().translation((float)(player.x-camera.x),(float)(baseY-camera.y),(float)(player.z-camera.z));
    }

    /** 两张朝向摄像机的渐隐光片形成金色外晕和亮芯；只八个顶点，连续 UV 消除实体壳层的硬边。 */
    static void beam(VertexConsumer output,Matrix4f transform,float tick,float strength,Vector3f right) {
        float pulse=.96F+.04F*Mth.sin(tick*.035F);
        for(int layer=0;layer<2;layer++)for(int corner=0;corner<4;corner++){
            boolean top=corner>=2;float side=corner==0||corner==3?-1:1;
            float radius=top?.18F:layer==0?3.8F:1.5F;
            float x=right.x*radius*side,z=right.z*radius*side,y=top?HEIGHT:0;
            // 明确金色而非接近白色的灰幕；核心更亮，边缘透明度由纹理连续控制。
            output.addVertex(transform,x,y,z).setColor(1F,layer==0?.64F:.82F,layer==0?.08F:.24F,(layer==0?.26F:.2F)*pulse*strength)
                    .setUv(corner==0||corner==3?0:1,top?0:1);
        }
    }

    /** 少量细小光尘沿光锥缓慢上浮；面向真实摄像机，不创建世界粒子或把金色大颗粒铺满屏幕。 */
    static void sparkles(VertexConsumer output,Matrix4f transform,float tick,float strength,Quaternionf camera) {
        var right=new Vector3f(1,0,0).rotate(camera);var up=new Vector3f(0,1,0).rotate(camera);
        for(int i=0;i<24;i++){
            float angle=i*2.399963F+tick*.0015F,radius=.65F+(i%7)*.2F;
            float x=Mth.cos(angle)*radius,z=Mth.sin(angle)*radius,y=(i*.417F+tick*.012F)%10+.15F;
            float fade=Mth.sin((y-.15F)/10*(float)Math.PI),alpha=fade*(.12F+.18F*Mth.square(Mth.sin(tick*.025F+i)))*strength;
            float size=.018F+(i%3)*.006F;
            // 中心亮、四角透明的菱形只占数像素，深度测试继续由同一原版渲染类型处理。
            for(int side=0;side<4;side++){
                float a=side*(float)Math.PI/2,b=(side+1)*(float)Math.PI/2;
                output.addVertex(transform,x,y,z).setColor(1F,.93F,.66F,alpha);
                output.addVertex(transform,x+size*(right.x*Mth.cos(a)+up.x*Mth.sin(a)),y+size*(right.y*Mth.cos(a)+up.y*Mth.sin(a)),z+size*(right.z*Mth.cos(a)+up.z*Mth.sin(a))).setColor(1F,.93F,.66F,0F);
                output.addVertex(transform,x+size*(right.x*Mth.cos(b)+up.x*Mth.sin(b)),y+size*(right.y*Mth.cos(b)+up.y*Mth.sin(b)),z+size*(right.z*Mth.cos(b)+up.z*Mth.sin(b))).setColor(1F,.93F,.66F,0F);
            }
        }
    }

    /** 显式控制混合及深度写入；不借用带 alpha 截断的实体通道，也不把光片写入世界深度。 */
    private static final class BeamLayer extends RenderType {
        private static final ShaderStateShard PROGRAM=new ShaderStateShard(()->shader);
        private static final TextureStateShard TEXTURE=new TextureStateShard(BEAM_TEXTURE,true,false);
        static final RenderType TYPE=new BeamLayer();
        private BeamLayer(){super("naiwa_holy_light",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,256,false,true,
                ()->{PROGRAM.setupRenderState();TEXTURE.setupRenderState();TRANSLUCENT_TRANSPARENCY.setupRenderState();NO_CULL.setupRenderState();COLOR_WRITE.setupRenderState();},
                ()->{COLOR_WRITE.clearRenderState();NO_CULL.clearRenderState();TRANSLUCENT_TRANSPARENCY.clearRenderState();TEXTURE.clearRenderState();PROGRAM.clearRenderState();});}
    }

    /** 脚部高度在起手时锁定，角色在光锥内部上升；水平中心随玩家移动，阶段切换不跳动光源。 */
    static final class State {
        private Object owner;
        private int stage=-1,remaining;
        double baseY;
        boolean update(Object player,int nextStage,int duration,double y) {
            if(player==null||nextStage<0) {boolean changed=owner!=null;owner=null;stage=-1;remaining=0;baseY=0;return changed;}
            boolean changed=owner!=player||nextStage<stage||nextStage==0&&duration>remaining;
            if(changed) {owner=player;baseY=y;}
            stage=nextStage;remaining=duration;return changed;
        }
    }
}
