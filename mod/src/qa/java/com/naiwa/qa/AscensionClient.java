package com.naiwa.qa;

import com.google.gson.Gson;
import com.naiwa.Naiwa;
import com.naiwa.client.NaiwaAscensionVisuals;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import org.lwjgl.opengl.GL11;

/** 读取 TCP 同步状态及实际 GPU 帧缓冲；截图不合成，Iris 必须真正启用指定光影才算覆盖。 */
final class AscensionClient {
    private final String role=System.getProperty("naiwa.qa.role");
    private int phase,age,frames;
    private int selectedPack;
    private boolean done;
    private int resizedFrames,oldBloom,deferredPlayers,shadowPlayers;
    private boolean bloomRebuilt,buffersFreed;
    private Throwable renderFailure;
    private final Set<Integer> visualStages=new HashSet<>();
    private final AscensionHands hands=Boolean.getBoolean("naiwa.qa.hands")?new AscensionHands():null;
    AscensionClient(){
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,(ClientTickEvent.Post e)->tick());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,(RenderLevelStageEvent e)->{
            if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;frames++;
            try {
                var chain=bloom();if(chain==null)return;
                var main=Minecraft.getInstance().getMainRenderTarget();
                MultiplayerCheck.require(chain.getTempTarget("extract").width==Math.max(1,main.width/2)
                        &&chain.getTempTarget("blur_h").width==Math.max(1,main.width/4)
                        &&chain.getTempTarget("blur_v").height==Math.max(1,main.height/4),"实际后处理目标尺寸匹配当前画布");
                if(main.width==960)resizedFrames++;
                if(oldBloom!=0&&System.identityHashCode(chain)!=oldBloom)bloomRebuilt=true;
            } catch(Throwable failure){renderFailure=failure;}
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre e)->{
            if(!e.getEntity().hasEffect(Naiwa.ASCENDING)||Minecraft.getInstance().screen!=null)return;
            try {
                // Iris 先单独绘制阴影，再进入主视图；阴影保留原模型入口，不属于独立合成阶段。
                if(shadowPass()) {MultiplayerCheck.require(e.isCanceled(),"阴影中也由奶蛙模型替换玩家");shadowPlayers++;return;}
                var method=NaiwaAscensionVisuals.class.getDeclaredMethod("worldPass");method.setAccessible(true);
                MultiplayerCheck.require((Boolean)method.invoke(null)&&e.isCanceled(),"世界内飞升角色必须推迟到独立绘制阶段");deferredPlayers++;
            }catch(Throwable failure){renderFailure=failure;}
        });
    }

    private boolean shadowPass()throws Exception {
        try {var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");return (Boolean)api.getMethod("isRenderingShadowPass").invoke(api.getMethod("getInstance").invoke(null));}
        catch(ClassNotFoundException absent){return false;}
    }

    private net.minecraft.client.renderer.PostChain bloom()throws Exception {
        var field=Class.forName("com.naiwa.client.NaiwaAscensionBloom").getDeclaredField("chain");field.setAccessible(true);
        return (net.minecraft.client.renderer.PostChain)field.get(null);
    }

    private Map<String,Object> post()throws Exception {
        var result=new LinkedHashMap<String,Object>();var chain=bloom();result.put("active",chain!=null);
        var buffers=NaiwaAscensionVisuals.class.getDeclaredField("ownedBuffers");buffers.setAccessible(true);Object owned=buffers.get(null);
        result.put("buffers",owned==null?"released":owned.getClass().getName());
        result.put("shaderTargets",ShaderTargetChecks.samples);
        result.put("bindings",ShaderTargetChecks.bindings);
        if(owned!=null)MultiplayerCheck.require(owned.getClass()==net.minecraft.client.renderer.MultiBufferSource.BufferSource.class,"实际使用自有即时缓冲，避免 Iris 延迟提交");
        if(chain!=null)for(String name:List.of("scene","extract","blur_h","blur_v","composite")){
            var target=chain.getTempTarget(name);result.put(name,List.of(target.width,target.height));
        }
        result.put("resizedFrames",resizedFrames);result.put("rebuilt",bloomRebuilt);result.put("retiredBuffersFreed",buffersFreed);result.put("deferredPlayerEvents",deferredPlayers);result.put("shadowPlayerEvents",shadowPlayers);
        return result;
    }
    /** 只读实际客户端特效生命周期，避免以服务端有状态冒充客户端已经创建和清理画面。 */
    private Map<String,Object> visual(Player player)throws Exception {
        var field=NaiwaAscensionVisuals.class.getDeclaredField("FLIGHTS");field.setAccessible(true);
        Object flight=((Map<?,?>)field.get(null)).get(player);
        var result=new LinkedHashMap<String,Object>();result.put("tracked",flight!=null);
        if(flight!=null)for(String name:List.of("stage")){
            var value=flight.getClass().getDeclaredField(name);value.setAccessible(true);result.put(name,value.get(flight));
        }
        return result;
    }
    private Map<String,Object> shader()throws Exception{
        var result=new LinkedHashMap<String,Object>();
        try{
            var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");var instance=api.getMethod("getInstance").invoke(null);
            result.put("enabled",api.getMethod("isShaderPackInUse").invoke(instance));
            var iris=Class.forName("net.irisshaders.iris.Iris");result.put("pack",iris.getMethod("getCurrentPackName").invoke(null));
            result.put("fallback",iris.getMethod("isFallback").invoke(null));
            var cases=MultiplayerCheck.ROOT.resolve("shader-cases.json");
            if(Files.exists(cases)){String[] packs=new Gson().fromJson(Files.readString(cases),String[].class);if(packs.length>0)MultiplayerCheck.require(result.get("pack").equals(packs[phase/20]),"实际启用指定光影："+result.get("pack"));}
            result.put("error",iris.getMethod("getStoredError").invoke(null).toString());
            MultiplayerCheck.require(Boolean.FALSE.equals(result.get("fallback"))&&result.get("error").equals("Optional.empty"),"光影不得处于错误或回退状态："+result);
            var clock=Class.forName("net.irisshaders.iris.uniforms.WorldTimeUniforms").getDeclaredMethod("getWorldDayTime");clock.setAccessible(true);
            int time=(Integer)clock.invoke(null);result.put("worldTimeUniform",time);
            MultiplayerCheck.require(time==(NaiwaAscensionVisuals.active()?18000:6000),"实际 Iris 光影时钟也须仅在本机飞升时为午夜："+time);
        }catch(ClassNotFoundException absent){result.put("enabled",false);result.put("pack","vanilla");}
        MultiplayerCheck.require(result.get("enabled").equals(Boolean.getBoolean("naiwa.qa.shader")),"光影实际启用状态必须与本轮要求一致："+result);
        return result;
    }
    private void tick(){if(done)return;var mc=Minecraft.getInstance();try{
        if(renderFailure!=null)throw new RuntimeException(renderFailure);
        // Iris 在首次进入世界前就创建缩放缓冲；必须先恢复窗口，不能等玩家入场后才修复 1×1 画布。
        if(mc.getWindow()==null||mc.getMainRenderTarget()==null)return;
        long window=mc.getWindow().getWindow();
        if(Boolean.getBoolean("naiwa.qa.foreground"))org.lwjgl.glfw.GLFW.glfwRestoreWindow(window);
        else {
            MultiplayerCheck.require(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_VISIBLE)==0
                    &&org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_FOCUSED)==0&&!mc.mouseHandler.isMouseGrabbed(),"后台验收必须隐藏、无焦点且不捕获鼠标");
            var audio=MultiplayerCheck.ROOT.resolve("audio-mute-status.json");
            if(!Files.exists(audio))return;
            com.google.gson.JsonArray muted;
            try {muted=com.google.gson.JsonParser.parseString(Files.readString(audio)).getAsJsonObject().getAsJsonArray("mutedPids");}
            catch(com.google.gson.JsonParseException|IllegalStateException incomplete){return;}
            if(muted.asList().stream().noneMatch(id->id.getAsLong()==ProcessHandle.current().pid()))return;
        }
        // 在原六秒悬停内短暂缩放，两端均不延长玩法计时；正式截图前恢复 1000×760。
        boolean resizing=phase%10==3&&age>=1&&age<6;int width=resizing?960:1000,height=resizing?720:760;
        if(mc.getMainRenderTarget().width!=width||mc.getMainRenderTarget().height!=height){mc.getWindow().setWindowed(width,height);org.lwjgl.glfw.GLFW.glfwSetWindowSize(window,width,height);mc.resizeDisplay();return;}
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.getOverlay()!=null)return;
        if(!Files.exists(MultiplayerCheck.ROOT.resolve(role+"-connected.ok")))MultiplayerCheck.write(role+"-connected.ok","PASS");
        String marker=Files.readString(MultiplayerCheck.ROOT.resolve("stage.txt")).trim();if(marker.isEmpty())return;int next=Integer.parseInt(marker);
        MultiplayerCheck.require(next!=-1,"服务端专项失败");
        if(next==99){MultiplayerCheck.write(role+"-result.txt","PASS actual GPU frames, both local/remote flights, local-only night, camera and sky restored\n");done=true;mc.stop();return;}if(next==0)return;
        if(next!=phase){phase=next;age=frames=0;}
        age++;int trial=phase/10,step=phase%10;boolean owner=role.equals(trial%2==1?"viewer":"actor");
        // 每个光影都轮流触发两次；只在基线准备期重载真实 Iris 管线，不改任何玩法状态。
        if(step==1&&trial/2!=selectedPack){
            String[] packs=new Gson().fromJson(Files.readString(MultiplayerCheck.ROOT.resolve("shader-cases.json")),String[].class);
            var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);
            config.getClass().getMethod("setShaderPackName",String.class).invoke(config,packs[trial/2]);
            config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,true);
            config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);selectedPack=trial/2;return;
        }
        Player subject=owner?mc.player:mc.level.players().stream().filter(p->p.getGameProfile().getName().equals(trial%2==1?"NaiwaViewer":"NaiwaActor")).findFirst().orElse(null);
        if(subject==null)return;
        var vfx=visual(subject);
        if(step==1){visualStages.clear();resizedFrames=oldBloom=deferredPlayers=shadowPlayers=0;bloomRebuilt=buffersFreed=false;ShaderTargetChecks.samples=0;}
        if(Boolean.TRUE.equals(vfx.get("tracked"))&&vfx.get("stage") instanceof Integer stage)visualStages.add(stage);
        mc.options.hideGui=true;
        // 使用真实方向键输入，不能直接改横向坐标来冒充允许移动。
        mc.options.keyRight.setDown(owner&&(step==2||step==3&&age<30));
        if(step==1){mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setYRot(owner?0:35);mc.player.setXRot(owner?-12:-15);}
        if(step>=2&&step<=4&&!owner){var direction=subject.getEyePosition().subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance())));}
        if(owner&&step==3&&age==30) {
            // 真实关闭自有 FBO 并检查释放，下一渲染帧必须重建；不改服务端阶段或延长飞升。
            var retired=bloom();MultiplayerCheck.require(retired!=null,"重建前泛光链存在");oldBloom=System.identityHashCode(retired);
            var close=Class.forName("com.naiwa.client.NaiwaAscensionBloom").getDeclaredMethod("close");close.setAccessible(true);close.invoke(null);
            buffersFreed=true;for(String name:List.of("scene","extract","blur_h","blur_v","composite"))
                buffersFreed&=retired.getTempTarget(name).frameBufferId==-1;
            MultiplayerCheck.require(buffersFreed,"关闭后释放所有自有 FBO");return;
        }
        if(owner&&step==3&&hands!=null&&hands.tick(mc,trial,age,shader()))return;
        // 准备阶段先等原版正午时间包到达，不应为尚未开始的手部窗口提前核验 Iris 时钟。
        if(owner&&(step==2||step==4||step==5)&&hands!=null&&hands.stage(mc,trial,step,age,shader()))return;
        // 仅验收探针为用户补拍正面，成品保持背面视角；先等待真实渲染数帧，再读取帧缓冲并立即恢复。
        if(owner&&step==3&&age>=70&&age<=80){
            mc.player.setYRot(mc.player.yBodyRot);mc.player.setXRot(-8);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if(age==80){
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-ascension-front-"+trial+".png"));}
                var chain=bloom();if(chain!=null)for(String name:List.of("scene","composite"))
                    try(var image=Screenshot.takeScreenshot(chain.getTempTarget(name))){image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-"+name+"-"+trial+".png"));}
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            return;
        }
        if(age<(step==1||step==5?35:step==3?30:6)||frames<3||Files.exists(MultiplayerCheck.ROOT.resolve(role+"-ascension-"+phase+".ok")))return;
        boolean flying=step>=2&&step<=4;
        if(flying&&(!subject.hasEffect(Naiwa.ASCENDING)||subject.getEffect(Naiwa.ASCENDING).getAmplifier()!=(step==2?1:step==3?2:3)))return;
        if(!flying&&subject.hasEffect(Naiwa.ASCENDING))return;
        if(flying)MultiplayerCheck.require(Boolean.TRUE.equals(vfx.get("tracked")),"本机与旁观者均已创建实际柔光金尘状态");
        if(step==5){
            MultiplayerCheck.require(Boolean.FALSE.equals(vfx.get("tracked")),"飞升结束后清理客户端状态");
            MultiplayerCheck.require(visualStages.containsAll(List.of(0,1,2,3)),"两端看到完整四阶段 stages="+visualStages);
            MultiplayerCheck.require(deferredPlayers>0,"实际玩家事件使用独立绘制阶段");
            MultiplayerCheck.require(ShaderTargetChecks.samples>0,"GPU 逐次主画布目标核验通过");
            if(owner)MultiplayerCheck.require(resizedFrames>0&&bloomRebuilt&&buffersFreed,"实际缩放与 FBO 释放重建通过");
        }
        // 文件阶段先到不代表 TCP 时间包已到；基线等服务端正午同步，不能把载入延迟误报成天空覆盖错误。
        if(step==1&&mc.level.getDayTime()!=6000)return;
        boolean local=flying&&owner;MultiplayerCheck.require(NaiwaAscensionVisuals.active()==local,"仅触发者启用黑夜圣光");
        MultiplayerCheck.require((bloom()!=null)==local,"泛光链仅在本机飞升时存在，结束立即释放");
        float expected=local?.5F:mc.level.dimensionType().timeOfDay(mc.level.getDayTime());float actual=mc.level.getTimeOfDay(0);
        MultiplayerCheck.require(Math.abs(expected-actual)<.0001&&mc.level.getDayTime()==6000,"实际运行时天空覆盖及不变的世界时钟");
        MultiplayerCheck.require(mc.options.getCameraType()==(local?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON),"本机飞升背面视角与结束恢复");
        var data=new LinkedHashMap<String,Object>();data.put("phase",phase);data.put("role",role);data.put("owner",owner);data.put("localVisuals",NaiwaAscensionVisuals.active());data.put("skyAngle",actual);data.put("worldTime",mc.level.getDayTime());data.put("camera",mc.options.getCameraType().name());data.put("subjectX",subject.getX());data.put("subjectY",subject.getY());data.put("subjectZ",subject.getZ());data.put("stage",subject.hasEffect(Naiwa.ASCENDING)?subject.getEffect(Naiwa.ASCENDING).getAmplifier():-1);data.put("frames",frames);data.put("shader",shader());data.put("gpu",GL11.glGetString(GL11.GL_RENDERER));
        data.put("vfx",vfx);data.put("post",post());data.put("observedVisualStages",visualStages);data.put("background",!Boolean.getBoolean("naiwa.qa.foreground"));
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){MultiplayerCheck.require(image.getWidth()==1000&&image.getHeight()==760,"真实有效画布");image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-ascension-"+phase+".png"));}
        MultiplayerCheck.write(role+"-ascension-"+phase+".json",new Gson().toJson(data));MultiplayerCheck.write(role+"-ascension-"+phase+".ok","PASS");
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write(role+"-result.txt","FAIL phase="+phase+" "+failure);}catch(Exception ignored){}mc.stop();}}
}
