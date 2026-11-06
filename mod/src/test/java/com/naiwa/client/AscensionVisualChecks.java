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
        assert !state.update(null,-1,0);
        assert state.update(first,0,15);
        assert !state.update(first,0,14);
        for(int stage=1;stage<=3;stage++)assert !state.update(first,stage,45) : "阶段前进不重复启动本机环境";
        assert state.update(first,0,15) : "同一角色再次起手应重置";
        state.update(first,0,14);
        assert state.update(first,0,15) : "起手再次触发也重新初始化";
        assert state.update(second,2,125) : "玩家实例变化后重置";
        assert state.update(null,-1,0) : "结束、死亡和退出清理临时效果";
        assert !state.update(null,-1,0) : "重复清理不重复刷新光照";

        var vertices=new Vertices();var camera=new org.joml.Quaternionf();
        NaiwaAscensionShapes.beam(vertices,new Matrix4f(),0,1,camera,0);
        assert vertices.values.size()==36&&vertices.colors==36 : "亮源、七张斜光片和贴地柔光";
        // 第一个亮源中心固定在身体前上方；转身体会转光轴，转相机只改变亮源面片朝向。
        for(float yaw:new float[]{0,90,180,270}) {
            var beam=new Vertices();NaiwaAscensionShapes.beam(beam,new Matrix4f(),yaw,1,camera,0);
            float cx=0,cy=0,cz=0;for(int i=0;i<4;i++){cx+=beam.values.get(i)[0]/4;cy+=beam.values.get(i)[1]/4;cz+=beam.values.get(i)[2]/4;}
            assert Math.abs(cy-9.15)<.0001&&Math.abs(Math.hypot(cx,cz)-9)<.001;
            assert Math.abs(cx+Math.sin(Math.toRadians(yaw))*9)<.001&&Math.abs(cz-Math.cos(Math.toRadians(yaw))*9)<.001;
        }
        try(var resource=AscensionVisualChecks.class.getResourceAsStream("/assets/naiwa/textures/effect/ascension_beam.png")){
            var texture=javax.imageio.ImageIO.read(resource);assert texture.getWidth()==128&&texture.getHeight()==256;
            for(int x=0;x<128;x++)assert texture.getRGB(x,0)>>>24==0&&texture.getRGB(x,255)>>>24==0 : "上下边缘透明";
            for(int y=0;y<256;y++)assert texture.getRGB(0,y)>>>24==0&&texture.getRGB(127,y)>>>24==0 : "径向边缘透明";
            assert texture.getRGB(64,128)>>>24>200 : "中心仍有可见亮芯";
        }
        var shifted=new Vertices();NaiwaAscensionShapes.beam(shifted,NaiwaAscensionVisuals.beamTransform(
                new net.minecraft.world.phys.Vec3(9,999,14),2,new net.minecraft.world.phys.Vec3(2,5,3)),0,1,camera,0);
        for(int i=0;i<vertices.values.size();i++) {
            float[] a=vertices.values.get(i),b=shifted.values.get(i);
            assert Math.abs(b[0]-a[0]-7)<.0001&&Math.abs(b[1]-a[1]+3)<.0001&&Math.abs(b[2]-a[2]-11)<.0001 : "世界相对坐标变换正确";
        }
        var faded=new Vertices();NaiwaAscensionShapes.beam(faded,new Matrix4f(),0,0,camera,0);
        assert faded.values.stream().allMatch(v->v[6]==0) : "退场强度为零不能残留圣光";
        var raised=new Vertices();NaiwaAscensionShapes.beam(raised,new Matrix4f(),0,1,camera,-6);
        for(int i=32;i<36;i++)assert Math.abs(raised.values.get(i)[1]+5.975)<.0001 : "角色升高六格后光斑仍在原脚高，而不是悬空跟随";
        assert NaiwaAscensionShapes.strength(0,15,0)==0&&NaiwaAscensionShapes.strength(0,5,0)==1;
        assert NaiwaAscensionShapes.strength(1,45,0)==1&&NaiwaAscensionShapes.strength(2,125,0)==1;
        assert NaiwaAscensionShapes.strength(3,65,0)==1&&NaiwaAscensionShapes.strength(3,5,0)==0;
        // 检查中间帧、所有阶段及反向流动；输出预算按一名飞升角色计算，不以帧率猜测性能。
        for(int stage=0;stage<4;stage++)for(float tick:new float[]{0,1,19.5F,100,10000,-13}) {
            var detail=new Vertices();var pose=new Matrix4f();
            NaiwaAscensionShapes.dust(detail,pose,tick,1,camera);
            NaiwaAscensionShapes.rain(detail,pose,tick,37,1,camera);
            assert detail.values.size()==7776 : "二十四枚金尘和 1920 条局部雨丝，固定几何预算";
            for(float[] vertex:detail.values) {
                for(float value:vertex)assert Float.isFinite(value);
                assert vertex[6]>=0&&vertex[6]<=255;
            }
            var zero=new Vertices();NaiwaAscensionShapes.dust(zero,pose,tick,0,camera);
            NaiwaAscensionShapes.rain(zero,pose,tick,37,0,camera);
            assert zero.values.stream().allMatch(v->v[6]==0) : "零强度完全退场";
        }
        var vertical=NaiwaAscensionShapes.horizontalRight(new org.joml.Quaternionf().rotationZ((float)Math.PI/2));
        assert Float.isFinite(vertical.x)&&Math.abs(vertical.length()-1)<.0001 : "极端视角不能产生无效几何";
        assert NaiwaAscensionShapes.incidence(0,0,1)>.74&&NaiwaAscensionShapes.incidence(0,0,-1)==0;
        assert NaiwaAscensionShapes.incidence(0,1,0)>.66 : "定向受光保留正背面和顶部差异";
        assert NaiwaModel.FLIGHT_FORMAT!=com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY
                &&NaiwaModel.FLIGHT_FORMAT.getVertexSize()==24 : "飞升角色使用与圣光相同的非实体顶点通道";
        float[] light=new float[3];NaiwaAscensionShapes.light(1,1,1,light,0);
        assert Math.abs(light[0]-.8F)<.00001&&Math.abs(light[1]-.73F)<.00001;
        NaiwaAscensionShapes.light(1,0,1,light,0);assert Math.abs(light[0]-3)<.00001 : "轮廓高光完整保留三倍范围";
        NaiwaAscensionShapes.light(0,1,1,light,0);assert Math.abs(light[0]-.2F)<.00001 : "背光面保留环境底色";
        try(var source=AscensionVisualChecks.class.getResourceAsStream("/assets/naiwa/shaders/post/holy_bloom.json")) {
            var chain=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(source,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assert chain.getAsJsonArray("passes").size()==5;
            for(var value:chain.getAsJsonArray("passes")) {
                var pass=value.getAsJsonObject();assert !pass.get("intarget").getAsString().equals(pass.get("outtarget").getAsString()) : "后处理不得读写同一目标";
            }
        }
        System.out.println("飞升圣光：生命周期、斜向光轴、雨幕金尘、定向受光、渐隐及独立后处理检查通过");
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
