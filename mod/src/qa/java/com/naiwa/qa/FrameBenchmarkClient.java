package com.naiwa.qa;

import com.google.gson.Gson;
import com.naiwa.NaiwaEntity;
import java.io.DataInputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/** 仅在独立 QA 客户端采集世界渲染帧间隔；相同夹具可加载任一基线 JAR。 */
final class FrameBenchmarkClient {
    private static final long WARMUP_NS=2_000_000_000L, SAMPLE_NS=5_000_000_000L;
    private final boolean matrix=Boolean.getBoolean("naiwa.qa.frameMatrix"),shader=Boolean.getBoolean("naiwa.qa.frameShader");
    private final boolean renderProfile=Boolean.getBoolean("naiwa.qa.frameRenderProfile"),profileStages=Boolean.getBoolean("naiwa.profileStages");
    private final long[] intervals=new long[20_000];
    private int phase,cleared,expected,samples,round;
    private long warmupUntil,start,previous;
    private double animationStart;
    private Method resetStages,snapshotStages;
    private String layout;
    private Map<String,Object> fixture;
    private boolean active,failed;

    FrameBenchmarkClient(){
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->tick());
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event)->{
            if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||!active||failed)return;
            if(!matrix){sample();return;}
            if(fixture==null&&System.nanoTime()>=warmupUntil){
                try{fixture=inspectFixture(event);}catch(Throwable error){fail(error);}
            }
        });
        // 只在整帧完成时计时，Iris 的阴影通道不能被计为独立屏幕帧。
        if(matrix)NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event)->{
            if(active&&!failed&&fixture!=null)sample();
        });
    }

    private void tick(){
        var mc=Minecraft.getInstance();
        if(failed||mc.level==null||mc.player==null||mc.screen!=null||mc.getOverlay()!=null)return;
        try {
            if(matrix&&Files.exists(MultiplayerCheck.ROOT.resolve("frame-complete.ok"))){mc.stop();return;}
            if(active){
                var frogs=frogs(mc);
                int actual=frogs.size();
                // 删除和新建实体由不同数据包同步；预热从最后一次数量变化后重新开始。
                if(actual!=expected){
                    MultiplayerCheck.require(start==0,"采样期间夹具实体数量变化："+actual+"/"+expected);
                    warmupUntil=System.nanoTime()+WARMUP_NS;
                }else if(renderProfile){
                    MultiplayerCheck.require(frogs.getFirst().clip(0).name().equals(layout),"单只对照的动画提前结束："+layout);
                }
                return;
            }
            var file=MultiplayerCheck.ROOT.resolve("frame-stage.txt");
            if(!Files.exists(file))return;
            String[] fields=Files.readString(file).trim().split(" ");
            int next=Integer.parseInt(fields[0]),count=Integer.parseInt(fields[1]);
            if(next<=phase)return;
            var frogs=frogs(mc);
            if(frogs.size()!=count)return;
            // 无布局的零只阶段只等待上一批移除；带布局的零只阶段需要采样空场。
            if(count==0&&fields.length==2){
                if(cleared!=next){MultiplayerCheck.write("frame-clear-"+next+".ok","PASS\n");cleared=next;}
                return;
            }
            // 生成与动画的实体数据分开同步。只在收到服务端指定片段后开始预热。
            if(renderProfile&&(!frogs.getFirst().animation().equals(fields[2])||!frogs.getFirst().clip(0).name().equals(fields[2])))return;
            var player=mc.player;
            if(Math.abs(player.getZ()-12.5)>.1||Math.abs(player.getX()-.5)>.1||Math.abs(player.getY()-81)>.1)return;
            var target=mc.getMainRenderTarget();
            if(target.width!=1000||target.height!=760)return;
            mc.options.hideGui=true;
            MultiplayerCheck.require(!mc.options.enableVsync().get()&&mc.options.framerateLimit().get()==260,"采样需关闭垂直同步且使用最高帧率档");
            if(matrix){
                layout=fields[2];round=Integer.parseInt(fields[3]);fixture=null;
                MultiplayerCheck.require(Math.abs(Mth.wrapDegrees(player.getYRot()-180))<.1&&Math.abs(player.getXRot())<.1&&
                        mc.options.fov().get()==70,"矩阵须使用固定朝向及 70° 视场角");
            }
            phase=next;expected=count;samples=0;start=0;previous=0;
            warmupUntil=System.nanoTime()+WARMUP_NS;active=true;
        }catch(Throwable error){fail(error);}
    }

    private List<NaiwaEntity> frogs(Minecraft mc){
        return mc.level.getEntitiesOfClass(NaiwaEntity.class,mc.player.getBoundingBox().inflate(25),NaiwaEntity::isAlive);
    }

    /** 计时前读取实际档位和相机投影。基线包没有两档接口，使用资源头中的高模面数。 */
    private Map<String,Object> inspectFixture(RenderLevelStageEvent event)throws Exception {
        if(shader){
            var apiClass=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            var api=apiClass.getMethod("getInstance").invoke(null);
            if((Boolean)apiClass.getMethod("isRenderingShadowPass").invoke(api))return null;
            MultiplayerCheck.require((Boolean)apiClass.getMethod("isShaderPackInUse").invoke(api),"矩阵指定的 Iris 光影未激活");
        }
        var mc=Minecraft.getInstance();
        var frogs=frogs(mc);
        if(frogs.size()!=expected)return null;
        if(frogs.isEmpty())return Map.of("layout",layout,"round",round,"shaderActive",shader,
                "highCount",0,"lowCount",0,"trianglesPerPass",0,"entities",List.of());
        var renderer=mc.getEntityRenderDispatcher().getRenderer(frogs.getFirst());
        Method selection=null;
        try{selection=renderer.getClass().getDeclaredMethod("usesSimplified",NaiwaEntity.class);}
        catch(NoSuchMethodException ignored){} // 旧版基线只有高模。
        int highFaces,lowFaces=0;
        if(selection==null){
            try(var in=new DataInputStream(mc.getResourceManager().open(ResourceLocation.fromNamespaceAndPath("naiwa","models/entity/naiwa.nwm")))){
                in.skipNBytes(20); // NAIWA001 文件头包含 8 字节魔数与三个计数，随后是面数。
                highFaces=in.readInt();
            }
        }else{
            selection.setAccessible(true);
            var faceCount=renderer.getClass().getDeclaredMethod("detailFaceCount",boolean.class);
            faceCount.setAccessible(true);
            highFaces=(Integer)faceCount.invoke(renderer,false);
            lowFaces=(Integer)faceCount.invoke(renderer,true);
            MultiplayerCheck.require(lowFaces>0&&lowFaces<highFaces,"简化网格面数无效");
        }
        var projection=new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        var camera=event.getCamera().getPosition();
        var target=mc.getMainRenderTarget();
        var entities=new ArrayList<Map<String,Object>>();
        var headCenters=new ArrayList<Double>();
        int lowCount=0;
        for(var frog:frogs){
            var box=frog.getBoundingBox();
            double minY=1,maxY=-1;
            for(double x:new double[]{box.minX,box.maxX})for(double y:new double[]{box.minY,box.maxY})for(double z:new double[]{box.minZ,box.maxZ}){
                var p=projection.transform(new Vector4f((float)(x-camera.x),(float)(y-camera.y),(float)(z-camera.z),1));
                MultiplayerCheck.require(p.w>0&&Math.abs(p.x)<p.w&&Math.abs(p.y)<p.w,"奶蛙身体越出测量画面："+frog.position());
                minY=Math.min(minY,p.y/p.w);maxY=Math.max(maxY,p.y/p.w);
            }
            var head=projection.transform(new Vector4f((float)(frog.getX()-camera.x),(float)(box.maxY-.25-camera.y),(float)(frog.getZ()-camera.z),1));
            double headX=(head.x/head.w*.5+.5)*target.width;
            MultiplayerCheck.require(headCenters.stream().noneMatch(other->Math.abs(other-headX)<25),"奶蛙的头部在画面中互相遮挡");
            headCenters.add(headX);
            boolean low=selection!=null&&(Boolean)selection.invoke(renderer,frog);
            if(low)lowCount++;
            entities.add(Map.of("x",frog.getX(),"y",frog.getY(),"z",frog.getZ(),"headX",headX,
                    "heightPx",(maxY-minY)*.5*target.height,"tier",low?"low":"high"));
        }
        return Map.of("layout",layout,"round",round,"shaderActive",shader,"lodSupported",selection!=null,
                "highCount",expected-lowCount,"lowCount",lowCount,"trianglesPerPass",(expected-lowCount)*highFaces+lowCount*lowFaces,
                "highFaces",highFaces,"lowFaces",lowFaces,"entities",entities);
    }

    private void sample(){
        long now=System.nanoTime();
        if(now<warmupUntil)return;
        try {
            if(start==0){
                if(renderProfile)animationStart=animationSeconds();
                // 分项计时只覆盖实际测量帧，排除预热、反射查询及截图读回。
                if(profileStages){resetProfileStages();now=System.nanoTime();}
                start=now;previous=now;return;
            }
            intervals[samples++]=now-previous;previous=now;
            if(now-start<SAMPLE_NS)return;
            active=false;
            MultiplayerCheck.require(samples>=30,"有效帧数不足");
            var mc=Minecraft.getInstance();
            var target=mc.getMainRenderTarget();
            var result=new LinkedHashMap<String,Object>();
            result.put("phase",phase);result.put("frogs",expected);
            result.put("sampleNanoseconds",now-start);result.put("frameNanoseconds",Arrays.copyOf(intervals,samples));
            result.put("resolution",target.width+"x"+target.height);result.put("gpu",GL11.glGetString(GL11.GL_RENDERER));
            result.put("renderDistance",mc.options.renderDistance().get());result.put("vsync",mc.options.enableVsync().get());
            result.put("fpsSetting",mc.options.framerateLimit().get());
            if(profileStages){
                var stages=snapshotStages.invoke(null);
                MultiplayerCheck.require(stages instanceof Map<?,?> map&&!map.isEmpty(),"分项计时缺少有效样本");
                result.put("profileStages",stages);
            }
            if(renderProfile){
                double end=animationSeconds();
                MultiplayerCheck.require(end>animationStart,"单只对照的动画时间没有推进");
                result.put("animation",Map.of("clip",layout,"startSeconds",animationStart,"endSeconds",end));
            }
            if(matrix){
                result.putAll(fixture);
                // GPU 读回和 PNG 压缩在计时结束后执行，只保留每组第一轮的同机位画面。
                if(round==1){
                    String screenshot="frame-"+layout+"-"+expected+".png";
                    try(var image=Screenshot.takeScreenshot(target)){
                        MultiplayerCheck.require(image.getWidth()==1000&&image.getHeight()==760,"有效帧缓冲截图");
                        image.writeToFile(MultiplayerCheck.ROOT.resolve(screenshot));
                    }
                    result.put("screenshot",screenshot);
                }
            }
            MultiplayerCheck.write("frames-"+phase+".json",new Gson().toJson(result));
        }catch(Throwable error){fail(error);}
    }

    /** 以客户端同步的动画时钟核对采样边界，大笑不能在计时内回到待机或重播。 */
    private double animationSeconds(){
        var frogs=frogs(Minecraft.getInstance());
        MultiplayerCheck.require(frogs.size()==1,"单只对照的夹具实体数量变化");
        var frog=frogs.getFirst();
        MultiplayerCheck.require(frog.animation().equals(layout)&&frog.clip(0).name().equals(layout),"单只对照的实际动画不符："+layout);
        return frog.animationSeconds(0);
    }

    /** 反射使普通帧率对照和未插桩基线能复用同一份 QA JAR。 */
    private void resetProfileStages()throws Exception {
        if(resetStages==null){
            var profile=Class.forName("com.naiwa.client.NaiwaRenderProfile");
            resetStages=profile.getDeclaredMethod("reset");
            snapshotStages=profile.getDeclaredMethod("snapshot");
            resetStages.setAccessible(true);
            snapshotStages.setAccessible(true);
        }
        resetStages.invoke(null);
    }

    private void fail(Throwable error){
        failed=true;
        try {MultiplayerCheck.write("frame-error.txt",error.toString());}
        catch(Exception writeFailure){error.addSuppressed(writeFailure);error.printStackTrace();}
    }
}
