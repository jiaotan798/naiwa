package com.naiwa.qa;

import com.naiwa.*;
import com.naiwa.client.NaiwaMesh;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.*;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import org.lwjgl.glfw.GLFW;

/** 读取真实声道启动事件及成品蒙皮姿势；不伪造音效、实体动画或网络状态。 */
final class LaughSyncClient {
    private final String role=System.getProperty("naiwa.qa.role");
    private final boolean actor=role.equals("actor");
    private final ConcurrentLinkedQueue<SoundStarted> sounds=new ConcurrentLinkedQueue<>();
    private final List<String> samples=new ArrayList<>();
    private NaiwaMesh mesh;
    private float[] baseline,positions,normals;
    private int last=-1,age,badFrames,sample,repeat,soundCount;
    private boolean seen,done;
    private double soundStart=-1;
    private SoundInstance sound;
    private String audioHash;
    private record SoundStarted(SoundInstance sound,long nano,boolean playing){}
    LaughSyncClient(){
        NeoForge.EVENT_BUS.addListener((PlaySoundSourceEvent e)->{if(e.getSound().getLocation().equals(ResourceLocation.fromNamespaceAndPath("naiwa","laugh")))sounds.add(new SoundStarted(e.getSound(),System.nanoTime(),e.getChannel().playing()));});
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->tick());
    }
    private void require(boolean ok,String why){MultiplayerCheck.require(ok,role+" laugh-sync phase="+last+": "+why);}
    /** 双窗口可能产生占位缓冲；恢复真实窗口后等下一帧，保证截图不是 1×1。 */
    private boolean viewport(Minecraft mc){
        int[] w={0},h={0};long handle=mc.getWindow().getWindow();GLFW.glfwGetFramebufferSize(handle,w,h);
        if(w[0]>=900&&h[0]>=650&&mc.getMainRenderTarget().width>=900&&mc.getMainRenderTarget().height>=650&&!mc.noRender)return true;
        require(++badFrames<200,"无法恢复真实画布");GLFW.glfwShowWindow(handle);GLFW.glfwRestoreWindow(handle);GLFW.glfwSetWindowSize(handle,1000,760);GLFW.glfwGetFramebufferSize(handle,w,h);
        mc.getWindow().setWidth(w[0]);mc.getWindow().setHeight(h[0]);mc.noRender=false;mc.resizeDisplay();return false;
    }
    /** 位姿通过成品的 50 Hz 蒙皮读取器计算，与游戏渲染器使用同一份模型资源。 */
    private double displacement(String clip,double time){
        mesh.pose(clip,time,positions,normals);double max=0;
        // 4～15 号骨属于颈部/面部，准备段可以眨眼和变表情；只用躯干、手臂及腿判断身体起拍。
        for(int v=0;v<mesh.vertexCount;v++){
            boolean body=true;for(int bone:mesh.joints[v])if(bone>=4&&bone<=15)body=false;
            if(body)for(int axis=0;axis<3;axis++){int i=v*3+axis;max=Math.max(max,Math.abs(positions[i]-baseline[i]));}
        }
        return max;
    }
    private void tick(){
        if(done)return;var mc=Minecraft.getInstance();
        try{
            if(mc.player==null||mc.level==null||mc.getOverlay()!=null||mc.screen!=null||!viewport(mc))return;
            MultiplayerCheck.write(role+"-connected.ok",Long.toString(ProcessHandle.current().pid()));
            // 阶段文件被覆写时可能短暂为空；等下一 tick，不把屏障写入瞬间当成玩法故障。
            String marker=Files.readString(MultiplayerCheck.ROOT.resolve("stage.txt")).trim();if(marker.isEmpty())return;
            int phase=Integer.parseInt(marker);require(phase!=-1,"服务端失败");
            if(phase==99){MultiplayerCheck.write(role+"-result.txt","PASS: native audio channels, original-clock body motion, one sound per round, repeat interaction, end cleanup.\n");done=true;mc.stop();return;}
            if(phase==0)return;
            if(mesh==null){
                var resources=mc.getResourceManager();
                try(var in=resources.open(ResourceLocation.fromNamespaceAndPath("naiwa","models/entity/naiwa.nwm"))){mesh=new NaiwaMesh(in);}
                positions=new float[mesh.vertexCount*3];normals=new float[positions.length];baseline=new float[positions.length];mesh.pose("laugh",0,baseline,normals);
                try(var in=resources.open(ResourceLocation.fromNamespaceAndPath("naiwa","sounds/laugh_full.ogg"))){audioHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));}
            }
            if(phase!=last){last=phase;age=sample=repeat=soundCount=0;seen=false;soundStart=-1;sound=null;samples.clear();}
            age++;
            var frog=(NaiwaEntity)mc.level.getEntity(Integer.parseInt(Files.readString(MultiplayerCheck.ROOT.resolve("laugh-entity.txt"))));if(frog==null)return;
            Vec3 d=frog.position().add(0,1.1,0).subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));
            if(phase%2==1){if(age>=20)MultiplayerCheck.write(role+"-"+phase+".ok","PASS ready\n");return;}
            boolean full=phase!=6;double seconds=frog.animationSeconds(0);
            if(!frog.animation().equals("auto"))seen=true;
            if(actor&&phase==2&&(!seen||repeat<2&&seconds>1+repeat*2)){
                mc.gameMode.interact(mc.player,frog,InteractionHand.MAIN_HAND);if(seen)repeat++;
            }
            SoundStarted started;
            while((started=sounds.poll())!=null){
                require(started.playing(),"OpenAL 声道未实际播放");require(++soundCount==1,"同轮音效重复播放");sound=started.sound();
                soundStart=seconds-(System.nanoTime()-started.nano())/1_000_000_000.0;
                require(Math.abs(sound.getPitch()-1)<.001,"原声速被改变");
                require(Math.abs(soundStart-(full?.6:0))<.2,"声音启动与动画时钟偏差超过 0.2 秒："+soundStart);
            }
            if(seen&&!frog.animation().equals("auto")){
                double[] marks=full?new double[]{.3,1.5,2.75,4.5,7.9}:new double[]{.3,1.5,2.15,4.5,6.7};
                if(sample<marks.length&&seconds>=marks[sample]){
                    var clip=frog.clip(0);double delta=displacement(clip.name(),clip.seconds());
                    if(sample==1)require(delta<.01,"准备段不应提前开始身体大笑 seconds="+seconds+" displacement="+delta);
                    if(sample==2)require(delta>.1,"笑声起拍后身体必须开始运动");
                    samples.add(String.format(Locale.ROOT,"{\"seconds\":%.3f,\"clip\":\"%s\",\"clipSeconds\":%.3f,\"displacement\":%.6f}",seconds,clip.name(),clip.seconds(),delta));
                    try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){require(image.getWidth()>=900&&image.getHeight()>=650,"占位截图");image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-laugh-"+phase+"-"+sample+".png"));}sample++;
                }
            }
            // 等声道自然结束后才能确认完成，不能只看逻辑动作名已恢复 auto。
            if(seen&&frog.animation().equals("auto")&&sample==5&&soundCount==1&&!mc.getSoundManager().isActive(sound)&&!Files.exists(MultiplayerCheck.ROOT.resolve(role+"-"+phase+".ok"))){
                require(!actor||phase!=2||repeat==2,"没有实际发送重复交互包");
                double expectedMotion=(full?.6:0)+2.04;
                String report=String.format(Locale.ROOT,"{\"phase\":%d,\"audioSha256\":\"%s\",\"nativeSources\":%d,\"soundStart\":%.6f,\"audibleStart\":%.6f,\"motionStart\":%.6f,\"repeatInteractions\":%d,\"samples\":[%s],\"checks\":\"PASS\"}",phase,audioHash,soundCount,soundStart,soundStart+2.04,expectedMotion,repeat,String.join(",",samples));
                MultiplayerCheck.write(role+"-laugh-"+phase+".json",report);MultiplayerCheck.write(role+"-"+phase+".ok","PASS\n");
            }
        }catch(Throwable e){done=true;e.printStackTrace();try{MultiplayerCheck.write(role+"-result.txt","FAIL "+e);}catch(Exception ignored){}mc.stop();}
    }
}
