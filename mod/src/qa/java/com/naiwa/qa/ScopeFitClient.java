package com.naiwa.qa;

import com.google.gson.Gson;
import com.naiwa.*;
import com.naiwa.client.NaiwaMesh;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** 使用渲染事件的真实投影与原版镜圈透明区逐顶点检查，不能用普通视角或人为扩大 FOV 代替。 */
final class ScopeFitClient {
    private final String role=System.getProperty("naiwa.qa.role");private final boolean actor=role.equals("actor");
    private int phase,age,samples;private boolean done,fit=true;private NaiwaMesh mesh;private float[] points,normals;private boolean[] selected;private NativeImage mask;
    private double minX,minY,maxX,maxY;
    private final boolean live=Boolean.getBoolean("naiwa.qa.scopeLive");
    ScopeFitClient(){NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->tick());NeoForge.EVENT_BUS.addListener(this::render);}
    private void require(boolean ok,String reason){MultiplayerCheck.require(ok,role+" scope-fit="+phase+": "+reason);}
    private void tick(){if(done)return;var mc=Minecraft.getInstance();try{
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.getOverlay()!=null)return;
        if(live){liveTick(mc);return;}
        if(mc.getMainRenderTarget().width!=1000||mc.getMainRenderTarget().height!=760){mc.getWindow().setWindowed(1000,760);org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().getWindow());mc.resizeDisplay();return;}
        if(!Files.exists(MultiplayerCheck.ROOT.resolve(role+"-connected.ok")))MultiplayerCheck.write(role+"-connected.ok","PASS");
        String marker=Files.readString(MultiplayerCheck.ROOT.resolve("stage.txt")).trim();if(marker.isEmpty())return;int next=Integer.parseInt(marker);require(next!=-1,"服务端校准失败");
        if(next==99){MultiplayerCheck.write(role+"-result.txt","PASS native scope-fit measurements\n");mc.options.keyUse.setDown(false);done=true;mc.stop();return;}if(next==0)return;
        if(next!=phase){phase=next;age=samples=0;fit=true;minX=minY=Double.POSITIVE_INFINITY;maxX=maxY=Double.NEGATIVE_INFINITY;}
        age++;mc.options.fov().set(70);
        var frog=mc.level.getEntity(Integer.parseInt(Files.readString(MultiplayerCheck.ROOT.resolve("scope-fit-actor.txt")).trim()));
        if(actor){mc.player.setYRot(0);mc.player.setXRot(0);mc.options.keyUse.setDown(true);if(!mc.player.isUsingItem())mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
        else if(frog!=null){Vec3 d=frog.getEyePosition().subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));}
        if(age>=90&&!Files.exists(MultiplayerCheck.ROOT.resolve(role+"-fit-"+(phase-1)+".ok"))){
            if(actor){require(samples>=30,"有足够真实渲染采样");MultiplayerCheck.write("scope-fit-"+phase+".json",new Gson().toJson(Map.of("distance",ScopeFitCheck.DISTANCES[phase-1],"fov",70,"fits",fit,"samples",samples,"bounds",List.of(minX,minY,maxX,maxY))));}
            try(var png=Screenshot.takeScreenshot(mc.getMainRenderTarget())){png.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-scope-fit-"+phase+".png"));}
            MultiplayerCheck.write(role+"-fit-"+(phase-1)+".ok","PASS");
        }
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write(role+"-result.txt","FAIL "+failure);}catch(Exception ignored){}mc.stop();}}
    /** 校准和真实整蛊共用同一镜圈检查，真实模式不预置演员也不把镜头重新瞄向它。 */
    private void liveTick(Minecraft mc)throws Exception {
        if(!Files.exists(MultiplayerCheck.ROOT.resolve(role+"-connected.ok")))MultiplayerCheck.write(role+"-connected.ok","PASS");
        String marker=Files.readString(MultiplayerCheck.ROOT.resolve("stage.txt")).trim();if(marker.isEmpty())return;int next=Integer.parseInt(marker);require(next!=-1,"真实整蛊失败");
        if(next==99){mc.options.keyUse.setDown(false);mc.options.keyUp.setDown(false);MultiplayerCheck.write(role+"-result.txt","PASS six native scope configurations\n");done=true;mc.stop();return;}if(next==0)return;
        if(next!=phase){phase=next;age=samples=0;fit=true;minX=minY=Double.POSITIVE_INFINITY;maxX=maxY=Double.NEGATIVE_INFINITY;mc.options.keyUp.setDown(false);}
        int index=phase/10,sub=phase%10;int[] trial=ScopeLiveCheck.CASES[index];
        long window=mc.getWindow().getWindow();org.lwjgl.glfw.GLFW.glfwRestoreWindow(window);
        if(mc.getMainRenderTarget().width!=trial[1]||mc.getMainRenderTarget().height!=trial[2]){mc.getWindow().setWindowed(trial[1],trial[2]);org.lwjgl.glfw.GLFW.glfwSetWindowSize(window,trial[1],trial[2]);mc.resizeDisplay();return;}
        age++;mc.options.fov().set(trial[0]);
        var frog=mc.level.getEntity(Integer.parseInt(Files.readString(MultiplayerCheck.ROOT.resolve("scope-fit-actor.txt")).trim())) instanceof NaiwaEntity n?n:null;
        if(actor&&sub<=3){mc.player.getInventory().selected=0;mc.player.setYRot(0);mc.player.setXRot(trial[3]);mc.options.keyUse.setDown(true);if(!mc.player.isUsingItem())mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
        else if(frog!=null){Vec3 d=frog.getEyePosition().subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));}
        boolean ready=false;
        if(sub==1)ready=age>=35&&(!actor||mc.player.isScoping());
        if(sub==3&&age>=90){
            if(actor){require(samples>=30&&fit,"完整头部在原版镜圈内，采样="+samples+" fits="+fit+" bounds="+List.of(minX,minY,maxX,maxY));MultiplayerCheck.write("scope-live-"+index+".json",new Gson().toJson(Map.of("fov",trial[0],"width",trial[1],"height",trial[2],"pitch",trial[3],"fits",fit,"samples",samples,"bounds",List.of(minX,minY,maxX,maxY))));}
            ready=true;
        }
        if(sub==4){
            if(actor){mc.options.keyUse.setDown(false);if(mc.player.isUsingItem())mc.gameMode.releaseUsingItem(mc.player);
                if(trial[4]==1){mc.player.setYRot(180);mc.player.setXRot(0);ready=frog==null;}
                else if(mc.player.getInventory().countItem(Naiwa.GAS.get())==1){mc.options.keyUp.setDown(false);ready=true;}
                else if(frog!=null&&frog.animation().equals("laugh_sequence")){mc.player.getInventory().selected=1;double distance=frog.position().subtract(mc.player.position()).horizontalDistance();mc.options.keyUp.setDown(distance>2.5);if(distance<=2.5)mc.gameMode.interact(mc.player,frog,InteractionHand.MAIN_HAND);}
            }else ready=age>=10;
        }
        if(sub==5){require(frog==null,"双端临时演员已清除");ready=age>=10;}
        if(ready&&!Files.exists(MultiplayerCheck.ROOT.resolve(role+"-live-"+phase+".ok"))){try(var png=Screenshot.takeScreenshot(mc.getMainRenderTarget())){png.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-scope-live-"+index+"-"+sub+".png"));}MultiplayerCheck.write(role+"-live-"+phase+".ok","PASS");}
    }
    private void render(RenderLevelStageEvent event){if(!actor||done||phase==0||live&&phase%10!=3||age<25||age>85||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();try{
        if(!mc.player.isScoping())return;
        if(mesh==null){try(var in=mc.getResourceManager().open(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("naiwa","models/entity/naiwa.nwm"))){mesh=new NaiwaMesh(in);}points=new float[mesh.vertexCount*3];normals=points.clone();selected=new boolean[mesh.vertexCount];var head=mesh.headFaces();for(int f=0;f<mesh.faceCount;f++)if(head[f])for(int c=0;c<3;c++)selected[mesh.corners[f*3+c]]=true;try(var in=mc.getResourceManager().open(Gui.SPYGLASS_SCOPE_LOCATION)){mask=NativeImage.read(in);}}
        var frog=mc.level.getEntity(Integer.parseInt(Files.readString(MultiplayerCheck.ROOT.resolve("scope-fit-actor.txt")).trim())) instanceof NaiwaEntity n?n:null;if(frog==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);var clip=frog.clip(partial);mesh.poseAction(clip.name(),clip.seconds(),points,normals,frog.act(),frog.getXRot());
        var scaleField=net.minecraft.client.gui.Gui.class.getDeclaredField("scopeScale");scaleField.setAccessible(true);float scale=scaleField.getFloat(mc.gui);
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight(),side=Mth.floor(Math.min(w,h)*scale),left=(w-side)/2,top=(h-side)/2;
        var matrix=new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());Vec3 position=frog.getPosition(partial).subtract(event.getCamera().getPosition());float angle=(float)Math.toRadians(-Mth.rotLerp(partial,frog.yBodyRotO,frog.yBodyRot));float cos=(float)Math.cos(angle),sin=(float)Math.sin(angle);
        for(int v=0;v<mesh.vertexCount;v++)if(selected[v]){float x=points[v*3]/16,y=points[v*3+1]/16,z=points[v*3+2]/16;var p=matrix.transform(new Vector4f((float)position.x+x*cos+z*sin,(float)position.y+y,(float)position.z+z*cos-x*sin,1));
            double px=(p.x/p.w*.5+.5)*w,py=(.5-p.y/p.w*.5)*h;minX=Math.min(minX,px);minY=Math.min(minY,py);maxX=Math.max(maxX,px);maxY=Math.max(maxY,py);
            int u=(int)((px-left)/side*mask.getWidth()),t=(int)((py-top)/side*mask.getHeight());if(p.w<=0||px<0||px>=w||py<0||py>=h)fit=false;
            // 原版玻璃及反光本身含 13/27/39 的透明度，不能当作镜框；内框从 81 开始。
            for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)if(u+dx<0||u+dx>=mask.getWidth()||t+dy<0||t+dy>=mask.getHeight()||(mask.getPixelRGBA(u+dx,t+dy)>>>24)>39)fit=false;
        }samples++;
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write(role+"-result.txt","FAIL "+failure);}catch(Exception ignored){}mc.stop();}}
}
