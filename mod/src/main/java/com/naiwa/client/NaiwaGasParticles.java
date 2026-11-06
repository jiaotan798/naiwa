package com.naiwa.client;

import com.naiwa.Naiwa;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** 笑气破碎的五秒气团与表情；复用原版粒子透明通道，按网易公式控制生长、淡入淡出和弱反弹。 */
final class NaiwaGasParticles {
    private NaiwaGasParticles() {}
    static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(Naiwa.GAS_CLOUD.get(),sprites->(type,level,x,y,z,dx,dy,dz)->new GasParticle(level,x,y,z,sprites,false));
        event.registerSpriteSet(Naiwa.GAS_FACE.get(),sprites->(type,level,x,y,z,dx,dy,dz)->new GasParticle(level,x,y,z,sprites,true));
    }

    /** 20 Hz 下五秒为 100 tick；前 0.1 秒淡入、末 1.5 秒淡出，气团在前 1.2 秒长大。 */
    static float opacity(float age,boolean face) {
        return (face?.8F:.38F)*Math.clamp(age/2,0,1)*Math.clamp((100-age)/30,0,1);
    }
    static float size(float age,boolean face) {
        return face?.085F+.035F*(float)Math.sin(Math.PI*Math.clamp(age/100,0,1)):.14F+.28F*Math.clamp(age/24,0,1);
    }

    static final class GasParticle extends TextureSheetParticle {
        final boolean face;
        private final float spin;
        GasParticle(ClientLevel level,double x,double y,double z,SpriteSet sprites,boolean face) {
            super(level,x,y,z);this.face=face;pickSprite(sprites);lifetime=100;setSize(.06F,.06F);
            double angle=random.nextDouble()*Math.PI*2;
            Vec3 direction;
            if(face) {
                double vertical=random.nextDouble()*2-1,horizontal=Math.sqrt(1-vertical*vertical);
                direction=new Vec3(Math.cos(angle)*horizontal,vertical,Math.sin(angle)*horizontal);
                Vec3 offset=direction.scale(.16*Math.cbrt(random.nextDouble()));setPos(x+offset.x,y+offset.y,z+offset.z);
            } else direction=new Vec3(Math.cos(angle),.08+random.nextDouble()*.12,Math.sin(angle)).normalize();
            double speed=(face?2.5+random.nextDouble()*1.5:1.6+random.nextDouble()*3.4)/20;
            setParticleSpeed(direction.x*speed,direction.y*speed,direction.z*speed);
            roll=oRoll=face?(float)Math.toRadians(random.nextDouble()*40-20):0;
            spin=face?(float)Math.toRadians(random.nextDouble()*70-35)/20:0;
            updateAppearance(0);
        }

        private void updateAppearance(float time) {
            quadSize=size(time,face);alpha=opacity(time,face);
            setColor(face?1:1-.3F*Math.clamp(time/100,0,1),face?1:.9F,face?1:.42F);
        }

        @Override public void tick() {
            xo=x;yo=y;zo=z;oRoll=roll;
            if(++age>=lifetime){remove();return;}
            // 秒制加速度 0.05 换为每 tick 位移增量；阻力也按 20 Hz 换算，不把网易 30 Hz 脚本时钟直接照搬。
            yd+=.05/400;
            move(xd,yd,zd);
            double drag=1-(face?1.8:1.9)/20;
            xd*=drag;yd*=drag;zd*=drag;roll+=spin;updateAppearance(age);
        }

        /** 原版粒子贴地会停止移动；这里独立裁碰撞，以 0.15 恢复系数反弹，保留五秒自然消散。 */
        @Override public void move(double dx,double dy,double dz) {
            Vec3 actual=Entity.collideBoundingBox(null,new Vec3(dx,dy,dz),getBoundingBox(),level,java.util.List.of());
            setBoundingBox(getBoundingBox().move(actual));setLocationFromBoundingbox();
            if(dx!=actual.x)xd=-dx*.15;
            if(dy!=actual.y)yd=-dy*.15;
            if(dz!=actual.z)zd=-dz*.15;
            if(dx!=actual.x||dy!=actual.y||dz!=actual.z){xd*=.9;zd*=.9;}
        }

        @Override public float getQuadSize(float partial) {return size(age+partial,face);}
        @Override public ParticleRenderType getRenderType() {return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    }
}
