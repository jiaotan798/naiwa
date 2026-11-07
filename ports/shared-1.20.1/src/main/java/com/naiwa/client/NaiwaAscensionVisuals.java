package com.naiwa.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.naiwa.Naiwa;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.IdentityHashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.mojang.blaze3d.platform.GlStateManager;

/** 世界完成后绘制飞升角色、斜向圣光及局部雨幕，再合成本机泛光；不改实体或世界状态。 */
public final class NaiwaAscensionVisuals {
    private static final State STATE=new State();
    private static final IdentityHashMap<Player,FlightVisual> FLIGHTS=new IdentityHashMap<>();
    private static Object world;
    private static final ResourceLocation BEAM_TEXTURE=new ResourceLocation("naiwa","textures/effect/ascension_beam.png");
    private static boolean worldPass;
    private static com.mojang.blaze3d.vertex.BufferBuilder vertexMemory;
    private static net.minecraft.client.renderer.MultiBufferSource.BufferSource ownedBuffers;
    private static ShaderInstance shader;
    private NaiwaAscensionVisuals() {}

    /** 独立透明着色器保留连续 alpha，不让光影把圣光归类为信标或发光实体后裁出硬边。 */
    static void shaders(ShaderRegistration registrar) throws java.io.IOException {
        NaiwaAscensionBloom.close();
        closeBuffers();
        registrar.register(new ResourceLocation("naiwa","holy_light"),DefaultVertexFormat.POSITION_TEX_COLOR,loaded->shader=loaded);
        NaiwaModel.shaders(registrar);
    }

    @FunctionalInterface
    interface ShaderRegistration {
        void register(ResourceLocation id, VertexFormat format, java.util.function.Consumer<ShaderInstance> loaded) throws java.io.IOException;
    }

    static boolean worldPass() {return worldPass;}

    public static boolean active() {
        var mc=Minecraft.getInstance();
        return mc.level!=null&&mc.player!=null&&mc.player.level()==mc.level&&mc.player.isAlive()
                &&!mc.player.isSpectator()&&mc.player.hasEffect(Naiwa.ASCENDING);
    }

    static void tick() {refresh();tickFlights();}

    static void disconnect() {
        if(STATE.update(null,-1,0))Minecraft.getInstance().gameRenderer.lightTexture().tick();
        FLIGHTS.clear();world=null;
        worldPass=false;NaiwaAscensionBloom.close();closeBuffers();
    }

    /** 同步包到达后的第一帧也初始化；结束即使不再绘制圣光，仍刷新缓存光照恢复正常。 */
    private static void refresh() {
        var mc=Minecraft.getInstance();var effect=active()?mc.player.getEffect(Naiwa.ASCENDING):null;
        // 世界替换可能先于下一次 tick 绘制，不能把旧世界的角色柔光带入新画面。
        if(world!=mc.level) {FLIGHTS.clear();world=mc.level;NaiwaAscensionBloom.close();closeBuffers();}
        if(STATE.update(effect==null?null:mc.player,effect==null?-1:effect.getAmplifier(),effect==null?0:effect.getDuration()))
            mc.gameRenderer.lightTexture().tick();
    }

    static void beginWorldPass() {worldPass=true;}

    /** 使用加载器提供的世界矩阵和相机，确保光影合成后仍按原位置绘制。 */
    static void render(Camera camera, float partial, Matrix4f modelView, Matrix4f projectionMatrix) {
        worldPass=false;
        if(!active())NaiwaAscensionBloom.close();
        refresh();if(FLIGHTS.isEmpty()||shader==null){closeBuffers();return;}
        var mc=Minecraft.getInstance();
        // 光影的天气阶段仍可能绑定其内部 G-buffer；最终世界阶段明确输出到主画布，避免圣光被后续合成覆盖。
        mc.getMainRenderTarget().bindWrite(false);
        // 世界结束后全局矩阵可能已为手部/界面重置；明确使用事件中的世界矩阵，并在绘制后还原。
        var view=RenderSystem.getModelViewStack();view.pushPose();view.last().pose().set(modelView);RenderSystem.applyModelViewMatrix();
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(projectionMatrix,sorting);
        try {
        // Iris 的共享实体缓冲会延迟批次，endBatch(type) 也可能为空操作；独立缓冲保证在本阶段提交。
        if(ownedBuffers==null){vertexMemory=new com.mojang.blaze3d.vertex.BufferBuilder(262144);ownedBuffers=net.minecraft.client.renderer.MultiBufferSource.immediate(vertexMemory);}
        var buffers=ownedBuffers;var type=BeamLayer.TYPE;
        try {
            // 角色先写入正确深度，之后的透明雨丝和光尘才能被躯干遮挡。
            for(Player player:FLIGHTS.keySet())if(visible(player,camera.getPosition()))NaiwaPlayerRenderer.world(player,partial,camera,buffers);
            var output=buffers.getBuffer(type);
            if(mc.player!=null)shader.safeGetUniform("BodyCenter").set((float)(Mth.lerp(partial,mc.player.xOld,mc.player.getX())-camera.getPosition().x),
                    (float)(Mth.lerp(partial,mc.player.yOld,mc.player.getY())+1.15-camera.getPosition().y),
                    (float)(Mth.lerp(partial,mc.player.zOld,mc.player.getZ())-camera.getPosition().z));
            for(var entry:FLIGHTS.entrySet()) {
                Player player=entry.getKey();FlightVisual flight=entry.getValue();
                if(!visible(player,camera.getPosition()))continue;
                float tick=player.tickCount+partial;
                var effect=player.getEffect(Naiwa.ASCENDING);if(effect==null)continue;
                int stage=effect.getAmplifier();float strength=NaiwaAscensionShapes.strength(stage,effect.getDuration(),partial);
                var position=new Vec3(Mth.lerp(partial,player.xOld,player.getX()),Mth.lerp(partial,player.yOld,player.getY()),Mth.lerp(partial,player.zOld,player.getZ()));
                Matrix4f body=beamTransform(position,position.y,camera.getPosition());
                float yaw=Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot);
                // 与参考包一致，圣光和雨幕仅本机拥有；旁观者保持原世界天空与天气。
                if(player==mc.player) {
                    NaiwaAscensionShapes.beam(output,body,yaw,strength,camera.rotation(),(float)(flight.baseY-position.y));
                    NaiwaAscensionShapes.rain(output,body,tick,yaw,strength,camera.rotation());
                }
                // 参考包的光尘与地面光斑以起飞脚高为基准，不能随身体升高形成悬空亮片。
                NaiwaAscensionShapes.dust(output,beamTransform(position,flight.baseY,camera.getPosition()),tick,strength,camera.rotation());
            }
        }
        finally {buffers.endBatch(type);}
        if(active())NaiwaAscensionBloom.render(partial);
        } finally {RenderSystem.setProjectionMatrix(projection,sorting);view.popPose();RenderSystem.applyModelViewMatrix();}
    }

    private static void closeBuffers() {if(vertexMemory!=null){vertexMemory.discard();vertexMemory=null;ownedBuffers=null;}}

    private static boolean visible(Player player,Vec3 camera) {
        var mc=Minecraft.getInstance();
        return player.level()==mc.level&&!player.isRemoved()&&!player.isInvisible()&&player.isAlive()&&!player.isSpectator()&&player.distanceToSqr(camera)<=64*64;
    }

    /** 每个客户端 tick 核对阶段和提示音；雨幕与光尘直接使用连续的玩家渲染时钟。 */
    private static void tickFlights() {
        var mc=Minecraft.getInstance();
        if(world!=mc.level) {FLIGHTS.clear();world=mc.level;}
        if(mc.level==null||mc.isPaused())return;
        FLIGHTS.entrySet().removeIf(entry->{
            Player p=entry.getKey();
            if(p.level()!=mc.level||p.isRemoved()||!p.isAlive()||p.isSpectator()||!mc.level.players().contains(p))return true;
            return !p.hasEffect(Naiwa.ASCENDING);
        });
        for(Player p:mc.level.players()) {
            var effect=p.getEffect(Naiwa.ASCENDING);
            if(effect==null||!p.isAlive()||p.isSpectator())continue;
            int stage=effect.getAmplifier();
            FlightVisual f=FLIGHTS.get(p);
            if(f==null||stage<f.stage||stage==0&&effect.getDuration()>f.remaining) {
                f=new FlightVisual(p.getY());FLIGHTS.put(p,f);
                // 只在看到真正起手时响提示；中途进入追踪范围不补播起飞声。
                if(stage==0)sound(p,.32F,1.2F);
            } else if(stage!=f.stage) sound(p,stage==2?.22F:.14F,stage==3?.8F:1.5F);
            f.stage=stage;f.remaining=effect.getDuration();
        }
    }

    private static void sound(Player p,float volume,float pitch) {
        p.level().playLocalSound(p.getX(),p.getY()+1,p.getZ(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,volume,pitch,false);
    }

    /** 登记只活在当前客户端世界；移除状态效果或离开追踪范围时立即清理。 */
    private static final class FlightVisual {
        int stage,remaining;
        final double baseY;
        FlightVisual(double baseY) {this.baseY=baseY;}
    }

    /** 此阶段原版 ModelView 已含相机旋转；这里只平移相对坐标，否则会二次旋转并偏离玩家。 */
    static Matrix4f beamTransform(Vec3 player,double baseY,Vec3 camera) {
        return new Matrix4f().translation((float)(player.x-camera.x),(float)(baseY-camera.y),(float)(player.z-camera.z));
    }

    /** 显式控制混合及深度写入；不借用带 alpha 截断的实体通道，也不把光片写入世界深度。 */
    private static final class BeamLayer extends RenderType {
        private static final ShaderStateShard PROGRAM=new ShaderStateShard(()->shader);
        private static final TextureStateShard TEXTURE=new TextureStateShard(BEAM_TEXTURE,true,false);
        static final RenderType TYPE=new BeamLayer();
        private BeamLayer(){super("naiwa_holy_light",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,256,false,true,
                ()->{PROGRAM.setupRenderState();TEXTURE.setupRenderState();LEQUAL_DEPTH_TEST.setupRenderState();RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);NO_CULL.setupRenderState();COLOR_WRITE.setupRenderState();},
                ()->{COLOR_WRITE.clearRenderState();NO_CULL.clearRenderState();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();LEQUAL_DEPTH_TEST.clearRenderState();TEXTURE.clearRenderState();PROGRAM.clearRenderState();});}
    }

    /** 跟踪本机环境借用的生命周期，阶段前进不重置光照；换实例或重新起手才刷新缓存。 */
    static final class State {
        private Object owner;
        private int stage=-1,remaining;
        boolean update(Object player,int nextStage,int duration) {
            if(player==null||nextStage<0) {boolean changed=owner!=null;owner=null;stage=-1;remaining=0;return changed;}
            boolean changed=owner!=player||nextStage<stage||nextStage==0&&duration>remaining;
            if(changed) owner=player;
            stage=nextStage;remaining=duration;return changed;
        }
    }
}
