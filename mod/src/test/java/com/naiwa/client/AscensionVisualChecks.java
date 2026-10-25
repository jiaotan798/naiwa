package com.naiwa.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelTimeAccess;
import org.joml.Matrix4f;

/** 不启动客户端或 OpenGL，检查实际光锥输出、渲染接口前提和临时效果的生命周期。 */
final class AscensionVisualChecks {
    static void check() throws Exception {
        // 客户端覆盖的是原版默认天体角度方法；接口变化应在构建时暴露，不能静默改错目标。
        assert LevelTimeAccess.class.getMethod("getTimeOfDay",float.class).isDefault();
        assert ClientLevel.class.getMethod("getTimeOfDay",float.class).getDeclaringClass()==LevelTimeAccess.class;
        var state=new NaiwaAscensionVisuals.State();Object first=new Object(),second=new Object();
        assert !state.update(null,-1,0,0);
        assert state.update(first,0,15,64)&&state.baseY==64;
        assert !state.update(first,0,14,64.5)&&state.baseY==64;
        for(int stage=1;stage<=3;stage++)assert !state.update(first,stage,45,64+stage*2)&&state.baseY==64 : "跨阶段锁定光源底部，角色在锥体内飞升";
        assert state.update(first,0,15,70)&&state.baseY==70 : "同一角色再次触发应重新锁定高度";
        state.update(first,0,14,70);
        assert state.update(first,0,15,71)&&state.baseY==71 : "起手阶段再次触发也不能沿用旧位置";
        assert state.update(second,2,125,90)&&state.baseY==90 : "玩家实例变化后重置";
        assert state.update(null,-1,0,0)&&state.baseY==0 : "结束、死亡和退出清理临时效果";
        assert !state.update(null,-1,0,0) : "重复清理不重复刷新光照";

        var vertices=new Vertices();NaiwaAscensionVisuals.beam(vertices,new Matrix4f(),0,1,new org.joml.Vector3f(1,0,0));
        assert vertices.values.size()==8&&vertices.colors==8 : "连续纹理金色外晕及亮芯只需要两张光片";
        float low=Float.POSITIVE_INFINITY,high=Float.NEGATIVE_INFINITY,bottomRadius=0,topRadius=0;int lit=0;
        for(float[] v:vertices.values) {
            for(float value:v)assert Float.isFinite(value);
            low=Math.min(low,v[1]);high=Math.max(high,v[1]);float radius=(float)Math.hypot(v[0],v[2]);
            assert radius<=3.801F&&v[1]>=0&&v[1]<=32;
            assert v[3]==255&&v[4]>=160&&v[4]<=210&&v[5]>=20&&v[5]<=62 : "金色外晕与淡金亮芯，不能退回灰白幕布";
            assert v[6]>=0&&v[6]<=67 : "低透明度光片不遮住角色";
            if(v[6]>0)lit++;
            if(v[1]==0)bottomRadius=Math.max(bottomRadius,radius);
            if(v[1]==32)topRadius=Math.max(topRadius,radius);
        }
        assert low==0&&high==32&&Math.abs(bottomRadius-3.8)<.001&&Math.abs(topRadius-.18)<.001&&lit>0 : "上窄下宽，从天空向下照射";
        try(var resource=AscensionVisualChecks.class.getResourceAsStream("/assets/naiwa/textures/effect/ascension_beam.png")){
            var texture=javax.imageio.ImageIO.read(resource);assert texture.getWidth()==128&&texture.getHeight()==256;
            for(int x=0;x<128;x++)assert texture.getRGB(x,0)>>>24==0&&texture.getRGB(x,255)>>>24==0 : "上下边缘透明";
            for(int y=0;y<256;y++)assert texture.getRGB(0,y)>>>24==0&&texture.getRGB(127,y)>>>24==0 : "径向边缘透明";
            assert texture.getRGB(64,128)>>>24>200 : "中心仍有可见亮芯";
        }
        var shifted=new Vertices();NaiwaAscensionVisuals.beam(shifted,NaiwaAscensionVisuals.beamTransform(
                new net.minecraft.world.phys.Vec3(9,999,14),2,new net.minecraft.world.phys.Vec3(2,5,3)),80,1,new org.joml.Vector3f(1,0,0));
        for(int i=0;i<vertices.values.size();i++) {
            float[] a=vertices.values.get(i),b=shifted.values.get(i);
            assert Math.abs(b[0]-a[0]-7)<.0001&&Math.abs(b[1]-a[1]+3)<.0001&&Math.abs(b[2]-a[2]-11)<.0001 : "世界相对坐标变换正确";
        }
        var faded=new Vertices();NaiwaAscensionVisuals.beam(faded,new Matrix4f(),0,0,new org.joml.Vector3f(1,0,0));
        assert faded.values.stream().allMatch(v->v[6]==0) : "退场强度为零不能残留圣光";
        var dust=new Vertices();NaiwaAscensionVisuals.sparkles(dust,new Matrix4f(),100,1,new org.joml.Quaternionf());
        assert dust.values.size()==24*12&&dust.values.stream().allMatch(v->Float.isFinite(v[0])&&Float.isFinite(v[1])&&Float.isFinite(v[2])&&v[6]>=0&&v[6]<=77) : "小光尘格式与强度有效";
        System.out.println("飞升圣光：生命周期、午夜接口、金色连续纹理、光片坐标、渐隐与小光尘检查通过");
    }
    private static boolean same(float[] a,float[] b) {return a[0]==b[0]&&a[1]==b[1]&&a[2]==b[2];}
    private static final class Vertices implements VertexConsumer {
        final List<float[]> values=new ArrayList<>();int colors;
        @Override public VertexConsumer addVertex(float x,float y,float z) {values.add(new float[]{x,y,z,0,0,0,0});return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a) {float[] v=values.getLast();v[3]=r;v[4]=g;v[5]=b;v[6]=a;colors++;return this;}
        @Override public VertexConsumer setUv(float u,float v) {assert u>=0&&u<=1&&v>=0&&v<=1;return this;}
        @Override public VertexConsumer setUv1(int u,int v) {return this;}
        @Override public VertexConsumer setUv2(int u,int v) {return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z) {return this;}
    }
}
