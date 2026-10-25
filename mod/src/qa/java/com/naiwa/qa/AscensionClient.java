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
    private final AscensionHands hands=Boolean.getBoolean("naiwa.qa.hands")?new AscensionHands():null;
    AscensionClient(){NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,(ClientTickEvent.Post e)->tick());NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e)->{if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL)frames++;});}
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
        // Iris 在首次进入世界前就创建缩放缓冲；必须先恢复窗口，不能等玩家入场后才修复 1×1 画布。
        if(mc.getWindow()==null||mc.getMainRenderTarget()==null)return;
        long window=mc.getWindow().getWindow();org.lwjgl.glfw.GLFW.glfwRestoreWindow(window);
        if(mc.getMainRenderTarget().width!=1000||mc.getMainRenderTarget().height!=760){mc.getWindow().setWindowed(1000,760);org.lwjgl.glfw.GLFW.glfwSetWindowSize(window,1000,760);mc.resizeDisplay();return;}
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
        mc.options.hideGui=true;
        // 使用真实方向键输入，不能直接改横向坐标来冒充允许移动。
        mc.options.keyRight.setDown(owner&&(step==2||step==3&&age<30));
        if(step==1){mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setYRot(owner?0:35);mc.player.setXRot(owner?-12:-15);}
        if(step>=2&&step<=4&&!owner){var direction=subject.getEyePosition().subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance())));}
        if(owner&&step==3&&hands!=null&&hands.tick(mc,trial,age,shader()))return;
        // 准备阶段先等原版正午时间包到达，不应为尚未开始的手部窗口提前核验 Iris 时钟。
        if(owner&&(step==2||step==4||step==5)&&hands!=null&&hands.stage(mc,trial,step,age,shader()))return;
        // 仅验收探针为用户补拍正面，成品保持背面视角；先等待真实渲染数帧，再读取帧缓冲并立即恢复。
        if(owner&&step==3&&age>=70&&age<=80){
            mc.player.setYRot(mc.player.yBodyRot);mc.player.setXRot(-8);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if(age==80){try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-ascension-front-"+trial+".png"));}mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);}
            return;
        }
        if(age<(step==1||step==5?35:step==3?30:6)||frames<3||Files.exists(MultiplayerCheck.ROOT.resolve(role+"-ascension-"+phase+".ok")))return;
        boolean flying=step>=2&&step<=4;
        if(flying&&(!subject.hasEffect(Naiwa.ASCENDING)||subject.getEffect(Naiwa.ASCENDING).getAmplifier()!=(step==2?1:step==3?2:3)))return;
        if(!flying&&subject.hasEffect(Naiwa.ASCENDING))return;
        // 文件阶段先到不代表 TCP 时间包已到；基线等服务端正午同步，不能把载入延迟误报成天空覆盖错误。
        if(step==1&&mc.level.getDayTime()!=6000)return;
        boolean local=flying&&owner;MultiplayerCheck.require(NaiwaAscensionVisuals.active()==local,"仅触发者启用黑夜圣光");
        float expected=local?.5F:mc.level.dimensionType().timeOfDay(mc.level.getDayTime());float actual=mc.level.getTimeOfDay(0);
        MultiplayerCheck.require(Math.abs(expected-actual)<.0001&&mc.level.getDayTime()==6000,"实际运行时天空覆盖及不变的世界时钟");
        MultiplayerCheck.require(mc.options.getCameraType()==(local?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON),"本机飞升背面视角与结束恢复");
        var data=new LinkedHashMap<String,Object>();data.put("phase",phase);data.put("role",role);data.put("owner",owner);data.put("localVisuals",NaiwaAscensionVisuals.active());data.put("skyAngle",actual);data.put("worldTime",mc.level.getDayTime());data.put("camera",mc.options.getCameraType().name());data.put("subjectX",subject.getX());data.put("subjectY",subject.getY());data.put("subjectZ",subject.getZ());data.put("stage",subject.hasEffect(Naiwa.ASCENDING)?subject.getEffect(Naiwa.ASCENDING).getAmplifier():-1);data.put("frames",frames);data.put("shader",shader());data.put("gpu",GL11.glGetString(GL11.GL_RENDERER));
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){MultiplayerCheck.require(image.getWidth()==1000&&image.getHeight()==760,"真实有效画布");image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-ascension-"+phase+".png"));}
        MultiplayerCheck.write(role+"-ascension-"+phase+".json",new Gson().toJson(data));MultiplayerCheck.write(role+"-ascension-"+phase+".ok","PASS");
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write(role+"-result.txt","FAIL phase="+phase+" "+failure);}catch(Exception ignored){}mc.stop();}}
}
