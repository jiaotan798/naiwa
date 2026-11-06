package com.naiwa.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 按网易版参数绘制斜向圣光、局部雨幕与金尘；仅生成几何，不修改世界天气或光源。 */
final class NaiwaAscensionShapes {
    private NaiwaAscensionShapes() {}

    /** 起手半秒建立亮度，缓降最后半秒退尽；阶段同步中的额外五 tick 不属于演出。 */
    static float strength(int stage,int remaining,float partial) {
        return (float)Mth.smoothstep(Mth.clamp(stage==0?(15-remaining+partial)/10F:stage==3?(remaining-5-partial)/10F:1,0,1));
    }

    /** 视点贴近光片时减弱外晕；垂直视角用相机朝向的水平垂线，避免归一化零向量。 */
    static Vector3f horizontalRight(Quaternionf camera) {
        var right=new Vector3f(1,0,0).rotate(camera);right.y=0;
        return right.lengthSquared()<.0001F?new Vector3f(1,0,0):right.normalize();
    }

    /** 模型局部 +Z 是身体正面；光轴相对身体前后各九格、上下各八格，相机只控制亮源朝向。 */
    static void beam(VertexConsumer out,Matrix4f pose,float yaw,float strength,Quaternionf camera,float groundOffset) {
        float angle=yaw*Mth.DEG_TO_RAD;
        var front=new Vector3f(-Mth.sin(angle),0,Mth.cos(angle));
        var across=new Vector3f(front.z,0,-front.x);
        var axis=new Vector3f(front).mul(9).add(0,8,0).normalize();
        var radialUp=new Vector3f(axis).cross(across).normalize();
        var source=new Vector3f(front).mul(9).add(0,9.15F,0);
        var end=new Vector3f(front).mul(-9).add(0,-6.85F,0);
        quad(out,pose,source.x,source.y,source.z,horizontalRight(camera),new Vector3f(0,1,0).rotate(camera),4.5F,4.5F,.55F*strength,0,0);
        for(int layer=1;layer<8;layer++) {
            float spin=layer<2?0:(layer-2)*Mth.PI/6;
            var direction=new Vector3f(across).mul(Mth.cos(spin)).fma(Mth.sin(spin),radialUp);
            for(int corner=0;corner<4;corner++) {
                boolean top=corner>=2;float side=corner==0||corner==3?-1:1;
                var center=top?source:end;float radius=top?5.1F:7.8F;
                vertex(out,pose,center.x+direction.x*radius*side,center.y+direction.y*radius*side,center.z+direction.z*radius*side,
                        corner==0||corner==3?0:1,top?0:1,(layer==1?.28F:.085F)*strength,1,0);
            }
        }
        // 接触面使用柔和实心光斑，形状与身体受光分开，不改变脚下方块亮度。
        quad(out,pose,0,groundOffset+.025F,0,new Vector3f(1,0,0),new Vector3f(0,0,1),5.7F,5.7F,.75F*strength,2,0);
    }

    /** 与参考包一致的 1920 条局部雨丝：每秒下降十格，靠近斜向光轴时由灰蓝过渡到金色。 */
    static void rain(VertexConsumer out,Matrix4f pose,float tick,float yaw,float strength,Quaternionf camera) {
        float angle=yaw*Mth.DEG_TO_RAD;
        var axis=new Vector3f(-Mth.sin(angle)*9,8,Mth.cos(angle)*9).normalize();
        var right=horizontalRight(camera);var fall=new Vector3f(.22F,1,.06F).normalize();
        for(int i=0;i<1920;i++) {
            float bx=(fract(i*.75487766F+.13F)*2-1)*8,bz=(fract(i*.56984029F+.47F)*2-1)*8;
            float y=fract((fract(i*.61803399F)*18-tick*.5F)/18)*18-4;
            float x=bx+y*.22F,z=bz+y*.06F,dy=y-1.15F,dot=x*axis.x+dy*axis.y+z*axis.z;
            float dx=x-axis.x*dot,dz=z-axis.z*dot;dy-=axis.y*dot;
            float lit=1-smooth(3.75F,9,(float)Math.sqrt(dx*dx+dy*dy+dz*dz));
            float fade=smooth(-4,-2,y)*(1-smooth(11,14,y))*(1-smooth(6,9,(float)Math.hypot(bx,bz)));
            quad(out,pose,x,y,z,right,fall,.01F,(.22F+fract(i*.371F)*.26F)/2,fade*(.24F+.36F*lit)*strength,4,lit);
        }
    }

    /** 固定索引生成二十四枚上浮闪光，20 Hz 时钟与参考包的脚本时钟换算一致。 */
    static void dust(VertexConsumer out,Matrix4f pose,float tick,float strength,Quaternionf camera) {
        var right=new Vector3f(1,0,0).rotate(camera);var up=new Vector3f(0,1,0).rotate(camera);
        for(int i=0;i<24;i++) {
            float angle=i*2.399963F+tick*.0025F,radius=.65F+(i%7)*.23F;
            float y=fract((i*.417F+tick*.018F)/10)*10+.15F;
            float twinkle=(float)Math.pow(.5F+.5F*Mth.sin(tick*.04F+i*2.17F),3);
            float size=(.025F+(i%3)*.01F)*(1+twinkle*.7F);
            quad(out,pose,Mth.cos(angle)*radius,y,Mth.sin(angle)*radius,right,up,size,size,
                    Mth.sin((y-.15F)/10*Mth.PI)*(.22F+.5F*twinkle)*strength,3,0);
        }
    }

    static float incidence(float nx,float ny,float nz) {return Math.max(0,(ny*.8889F+nz)/(float)Math.sqrt(1+.8889F*.8889F));}

    /** 参考包同一顶点光照公式；最大通道值为三，提交时缩至字节颜色范围，片元阶段再还原。 */
    static void light(float incidence,float facing,float reveal,float[] target,int offset) {
        float rim=(float)Math.pow(Math.max(0,1-Math.abs(facing)),3)*(.25F+.75F*incidence);
        target[offset]=Mth.lerp(reveal,.75F,.2F+.6F*incidence+2.2F*rim);
        target[offset+1]=Mth.lerp(reveal,.75F,.19F+.54F*incidence+1.6F*rim);
        target[offset+2]=Mth.lerp(reveal,.75F,.17F+.39F*incidence+.7F*rim);
    }
    private static float fract(float value) {return value-(float)Math.floor(value);}
    private static float smooth(float low,float high,float value) {return (float)Mth.smoothstep(Mth.clamp((value-low)/(high-low),0,1));}

    private static void quad(VertexConsumer out,Matrix4f pose,float x,float y,float z,Vector3f right,Vector3f up,float halfWidth,float halfHeight,float alpha,int mode,float lit) {
        for(int corner=0;corner<4;corner++) {
            float dx=(corner==0||corner==3?-1:1)*halfWidth,dy=(corner<2?-1:1)*halfHeight;
            vertex(out,pose,x+right.x*dx+up.x*dy,y+right.y*dx+up.y*dy,z+right.z*dx+up.z*dy,
                    corner==0||corner==3?0:1,corner<2?0:1,alpha,mode,lit);
        }
    }

    /** 颜色字节仅编码材质种类、雨丝受光与透明度，实际金色由统一片元着色器计算。 */
    private static void vertex(VertexConsumer out,Matrix4f pose,float x,float y,float z,float u,float v,float alpha,int mode,float lit) {
        out.addVertex(pose,x,y,z).setColor(mode,(int)(Mth.clamp(lit,0,1)*255),0,(int)(Mth.clamp(alpha,0,1)*255)).setUv(u,v);
    }
}
