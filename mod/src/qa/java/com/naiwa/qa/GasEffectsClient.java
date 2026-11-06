package com.naiwa.qa;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 统计实际粒子引擎里的对象及生命周期，截图直接读取 GPU，不用服务端发包意图代替本机显示。 */
final class GasEffectsClient {
    private final String role=System.getProperty("naiwa.qa.role");private final boolean actor=role.equals("actor");
    private int phase,age,first=-1,maxCloud,maxFaces;private boolean used,shot,done;private float grown,peakAlpha;
    GasEffectsClient(){NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->tick());}
    private static Object field(Object value,Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private List<Particle> particles(Minecraft mc)throws Exception {
        var all=new ArrayList<Particle>();
        var map=(Map<?,?>)field(mc.particleEngine,net.minecraft.client.particle.ParticleEngine.class,"particles");
        for(Object queue:map.values())for(Object particle:(Iterable<?>)queue)if(particle.getClass().getName().equals("com.naiwa.client.NaiwaGasParticles$GasParticle"))all.add((Particle)particle);
        return all;
    }
    private void image(Minecraft mc,String name)throws Exception {try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-gas-"+phase+"-"+name+".png"));}}
    private Map<String,Object> shader()throws Exception {
        var data=new LinkedHashMap<String,Object>();boolean enabled=false;
        try {var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");enabled=(Boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
            var iris=Class.forName("net.irisshaders.iris.Iris");data.put("pack",iris.getMethod("getCurrentPackName").invoke(null));
            MultiplayerCheck.require(!(Boolean)iris.getMethod("isFallback").invoke(null)&&iris.getMethod("getStoredError").invoke(null).toString().equals("Optional.empty"),"光影无回退或错误");}
        catch(ClassNotFoundException absent){}
        MultiplayerCheck.require(enabled==Boolean.getBoolean("naiwa.qa.shader"),"真实光影启用状态与本轮一致");data.put("enabled",enabled);return data;
    }
    private void tick(){if(done)return;var mc=Minecraft.getInstance();try {
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.getOverlay()!=null)return;
        if(mc.getMainRenderTarget().width!=1000||mc.getMainRenderTarget().height!=760){mc.getWindow().setWindowed(1000,760);org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1000,760);mc.resizeDisplay();return;}
        if(!Files.exists(MultiplayerCheck.ROOT.resolve(role+"-connected.ok")))MultiplayerCheck.write(role+"-connected.ok","PASS");
        int next=Integer.parseInt(Files.readString(MultiplayerCheck.ROOT.resolve("stage.txt")).trim());
        MultiplayerCheck.require(next!=-1,"服务端笑气粒子专项失败");
        if(next==99){MultiplayerCheck.write(role+"-result.txt","PASS actual 36 cloud + 4 faces per impact, growth/fade and five-second cleanup\n");done=true;mc.stop();return;}
        if(next==0)return;
        if(next!=phase){phase=next;age=maxCloud=maxFaces=0;first=-1;grown=peakAlpha=0;used=shot=false;}
        age++;mc.options.hideGui=true;mc.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
        mc.player.getInventory().selected=0;
        if(actor){mc.player.setYRot(0);mc.player.setXRot(25);}else{mc.player.setYRot(45);mc.player.setXRot(12);}
        int step=phase%10;
        if(step==1&&age>=30){image(mc,"baseline");MultiplayerCheck.write(role+"-gas-"+phase+".ok","PASS");return;}
        if(step!=2)return;
        if(actor&&!used){used=true;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
        int clouds=0,faces=0;
        for(var particle:particles(mc)){
            boolean face=(Boolean)field(particle,particle.getClass(),"face");if(face)faces++;else{clouds++;grown=Math.max(grown,particle instanceof net.minecraft.client.particle.SingleQuadParticle quad?quad.getQuadSize(0):0);peakAlpha=Math.max(peakAlpha,(Float)field(particle,Particle.class,"alpha"));}
            MultiplayerCheck.require(particle.getLifetime()==100&&Double.isFinite(particle.getPos().lengthSqr()),"粒子实际五秒寿命且坐标有效");
        }
        maxCloud=Math.max(maxCloud,clouds);maxFaces=Math.max(maxFaces,faces);
        if(first<0&&clouds+faces>0)first=age;
        if(first<0)return;
        if(!shot&&age-first>=25){shot=true;image(mc,"cloud");}
        if(age-first<115)return;
        MultiplayerCheck.require(maxCloud==36&&maxFaces==4,"每次只生成36团烟和4枚表情，实际="+maxCloud+"/"+maxFaces);
        MultiplayerCheck.require(grown>=.419F&&peakAlpha>=.379F&&clouds==0&&faces==0,"烟雾长大、淡入且五秒后回收");
        var data=new LinkedHashMap<String,Object>();data.put("clouds",maxCloud);data.put("faces",maxFaces);data.put("size",grown);data.put("alpha",peakAlpha);data.put("remaining",clouds+faces);data.put("case",phase/10==0?"block":"entity");
        data.put("shader",shader());image(mc,"cleared");MultiplayerCheck.write(role+"-gas-"+phase+".json",new Gson().toJson(data));MultiplayerCheck.write(role+"-gas-"+phase+".ok","PASS");
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write(role+"-result.txt","FAIL "+failure);}catch(Exception ignored){}mc.stop();}}
}
