package com.naiwa.client;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.naiwa.Naiwa;
import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** 在世界绘制后使用自有深度和混合管线，复用飞升圣光、斜雨与光尘的形状和节奏。 */
public final class NaiwaAscensionVisuals {
    private static final IdentityHashMap<Player,FlightVisual> FLIGHTS = new IdentityHashMap<>();
    private static Object world;
    private static ByteBufferBuilder vertexMemory;
    private static MultiBufferSource.BufferSource buffers;

    private NaiwaAscensionVisuals() {}

    public static boolean active() {
        var mc = Minecraft.getInstance();
        return mc.level!=null && mc.player!=null && mc.player.level()==mc.level && mc.player.isAlive()
                && !mc.player.isSpectator() && mc.player.hasEffect(Naiwa.ASCENDING);
    }

    static void tick() {tickFlights();}

    static void disconnect() {
        FLIGHTS.clear();
        world=null;
        closeBuffers();
    }

    /** 以各角色为局部坐标原点，让着色器正确保留躯干周围的衰减区域。 */
    public static void render(CameraRenderState camera,float partial,Matrix4fc view,GraphicsResourceAllocator allocator) {
        var mc = Minecraft.getInstance();
        if (world!=mc.level || FLIGHTS.isEmpty()) {closeBuffers(); return;}
        if (buffers==null) {
            vertexMemory=new ByteBufferBuilder(65536);
            buffers=MultiBufferSource.immediate(vertexMemory);
        }
        var stack = RenderSystem.getModelViewStack();
        stack.pushMatrix().mul(view);
        try {
            for (var entry : FLIGHTS.entrySet()) {
                var player = entry.getKey();
                if (!visible(player,camera.pos)) continue;
                var effect = player.getEffect(Naiwa.ASCENDING);
                if (effect==null) continue;
                float time = player.tickCount+partial;
                float strength = NaiwaAscensionShapes.strength(effect.getAmplifier(),effect.getDuration(),partial);
                double x = Mth.lerp(partial,player.xOld,player.getX());
                double y = Mth.lerp(partial,player.yOld,player.getY());
                double z = Mth.lerp(partial,player.zOld,player.getZ());
                float floor = (float)(entry.getValue().baseY-y);
                float yaw = Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot);
                stack.pushMatrix().translate((float)(x-camera.pos.x),(float)(y-camera.pos.y),(float)(z-camera.pos.z));
                try {
                    var out = buffers.getBuffer(NaiwaRenderLayers.BEAM);
                    if (player==mc.player) {
                        var local = new Matrix4f();
                        NaiwaAscensionShapes.beam(out,local,yaw,strength,camera.orientation,floor);
                        NaiwaAscensionShapes.rain(out,local,time,yaw,strength,camera.orientation);
                    }
                    NaiwaAscensionShapes.dust(out,new Matrix4f().translation(0,floor,0),time,strength,camera.orientation);
                    buffers.endBatch(NaiwaRenderLayers.BEAM);
                } finally {stack.popMatrix();}
            }
        } finally {stack.popMatrix();}
        if (active()) NaiwaAscensionBloom.render(allocator);
    }

    private static void closeBuffers() {
        if (vertexMemory!=null) vertexMemory.close();
        vertexMemory=null;
        buffers=null;
    }

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
}
