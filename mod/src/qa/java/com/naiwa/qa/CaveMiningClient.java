package com.naiwa.qa;

import com.google.gson.Gson;
import com.naiwa.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 原版游戏模式实际挖完方块，不用 START/ABORT 连发伪装持续挖掘。 */
final class CaveMiningClient {
    private final String role=System.getProperty("naiwa.qa.role");private final boolean actor=role.equals("actor");
    private int last=-1,age,action,index;private boolean done;private Vec3 lookout;private CaveMiningCheck.Trial trial;private final Set<String> frames=new HashSet<>();
    private final List<Map<String,Object>> cues=Collections.synchronizedList(new ArrayList<>());
    CaveMiningClient(){NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->tick());NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent e)->{
        var mc=Minecraft.getInstance();var sound=e.getSound();
        if(mc.level==null||trial==null||!sound.getLocation().toString().equals("minecraft:block.stone.hit"))return;
        // 原版挖掘本身也用石头音效；只统计来自已同步演员位置的真实音频声源。
        for(var frog:mc.level.getEntitiesOfClass(NaiwaEntity.class,trial.area()))if(frog.position().distanceToSqr(new Vec3(sound.getX(),sound.getY(),sound.getZ()))<.5)
            cues.add(Map.of("tick",mc.level.getGameTime(),"mined",mc.level.getBlockState(trial.target()).isAir(),"playing",e.getChannel().playing(),"volume",sound.getVolume(),"listenerDistance",mc.player.getEyePosition().distanceTo(new Vec3(sound.getX(),sound.getY(),sound.getZ())),"x",sound.getX(),"y",sound.getY(),"z",sound.getZ()));
    });}
    private void require(boolean b,String reason){MultiplayerCheck.require(b,role+" cave="+index+" phase="+last+": "+reason);}
    private void look(Minecraft mc,Vec3 p){Vec3 d=p.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));}
    private void slot(Minecraft mc,int slot){if(mc.player.getInventory().selected!=slot){mc.player.getInventory().selected=slot;mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));}}
    /** 实挖脚下后可能被坑壁挡住；只读挑选原生邻格，再用真实移动和跳跃走出去观察。 */
    private Vec3 viewpoint(Minecraft mc,NaiwaEntity frog) {
        for(int radius=1;radius<=2;radius++)for(int dy:new int[]{0,1,-1})for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++) {
            if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;
            Vec3 point=trial.player().add(x,dy,z);BlockPos feet=BlockPos.containing(point);
            var box=new net.minecraft.world.phys.AABB(point.x-.3,point.y,point.z-.3,point.x+.3,point.y+1.8,point.z+.3);
            if(mc.level.getBlockState(feet.below()).isFaceSturdy(mc.level,feet.below(),Direction.UP)&&mc.level.noCollision(box)&&!mc.level.containsAnyLiquid(box)
                    &&NaiwaGameplay.clearLine(mc.level,point.add(0,mc.player.getEyeHeight(),0),frog.getEyePosition()))return point;
        }
        return null;
    }
    private void image(Minecraft mc,String label)throws Exception{try(var png=Screenshot.takeScreenshot(mc.getMainRenderTarget())){require(png.getWidth()==1000&&png.getHeight()==760,"实际截图尺寸");png.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-cave-"+index+"-"+label+".png"));}}
    private void tick(){if(done)return;var mc=Minecraft.getInstance();try{
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null||mc.screen!=null)return;
        if(mc.getMainRenderTarget().width!=1000||mc.getMainRenderTarget().height!=760){long h=mc.getWindow().getWindow();org.lwjgl.glfw.GLFW.glfwShowWindow(h);org.lwjgl.glfw.GLFW.glfwRestoreWindow(h);mc.getWindow().setWindowed(1000,760);mc.resizeDisplay();return;}
        if(!Files.exists(MultiplayerCheck.ROOT.resolve(role+"-connected.ok")))MultiplayerCheck.write(role+"-connected.ok","PASS");String text=Files.readString(MultiplayerCheck.ROOT.resolve("stage.txt")).trim();if(text.isEmpty())return;int phase=Integer.parseInt(text);require(phase!=-1,"服务端失败");
        if(phase==99){MultiplayerCheck.write(role+"-result.txt","PASS all large cavern real mining trials\n");done=true;mc.stop();return;}if(phase==0)return;
        if(phase!=last){last=phase;index=(phase-3000)/10;age=action=0;lookout=null;trial=new Gson().fromJson(Files.readString(MultiplayerCheck.ROOT.resolve("cave-case.json")),CaveMiningCheck.Trial.class);mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);mc.gameMode.stopDestroyBlock();if((phase-3000)%10==6)cues.clear();}
        age++;int sub=(phase-3000)%10;int id=Integer.parseInt(Files.readString(MultiplayerCheck.ROOT.resolve("cave-actor.txt")).trim());NaiwaEntity frog=mc.level.getEntity(id) instanceof NaiwaEntity n?n:null;
        if(frog!=null){String state=frog.animation().equals("laugh_sequence")?"laugh_sequence":frog.act();if(!state.isEmpty()&&frames.add(index+state))image(mc,state);}
        if(age<6||Files.exists(MultiplayerCheck.ROOT.resolve(role+"-"+phase+".ok")))return;boolean ready=false;
        if(sub==6){if(actor){slot(mc,0);look(mc,Vec3.atCenterOf(trial.target()));}else look(mc,trial.player().add(0,1,0));ready=true;}
        else if(sub==0){
            if(actor){slot(mc,0);look(mc,Vec3.atCenterOf(trial.target()));if(!mc.level.getBlockState(trial.target()).isAir()){if(action++==0)mc.gameMode.startDestroyBlock(trial.target(),Direction.valueOf(trial.face()));else mc.gameMode.continueDestroyBlock(trial.target(),Direction.valueOf(trial.face()));mc.player.swing(InteractionHand.MAIN_HAND);}else {mc.gameMode.stopDestroyBlock();ready=frog!=null;}}
            else {if(frog!=null)look(mc,frog.getEyePosition());ready=frog!=null&&mc.level.getBlockState(trial.target()).isAir();}
            if(age==15)image(mc,"actual-digging");
        } else if(sub==1){if(frog!=null){
            require(cues.size()<=3,"身后只有三次追加敲击");
            if(actor&&cues.size()==3) {
                if(lookout==null&&!mc.player.hasLineOfSight(frog)){lookout=viewpoint(mc,frog);require(lookout!=null,"坑壁遮挡后有邻近原生观察点");}
                if(lookout!=null&&mc.player.position().subtract(lookout).horizontalDistance()>.18){look(mc,new Vec3(lookout.x,mc.player.getEyeY(),lookout.z));mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(true);return;}
                mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);lookout=null;
            }
            if(actor&&cues.size()<3)look(mc,Vec3.atCenterOf(trial.target()));else look(mc,frog.getEyePosition());
            if(cues.size()==3&&age>=35&&frog.act().equals("mine")&&mc.player.hasLineOfSight(frog)&&frames.add(index+"pretend-observed"))image(mc,"pretend-observed");
            ready=frog.animation().equals("laugh_sequence")&&cues.size()==3;
            if(ready){
                var player=actor?mc.player:mc.level.players().stream().filter(p->p.getGameProfile().getName().equals("NaiwaActor")).findFirst().orElseThrow();
                Vec3 direction=player.position().subtract(frog.position());float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
                require(Math.abs(net.minecraft.util.Mth.wrapDegrees(frog.yBodyRot-yaw))<12,"大笑开始时双方实际渲染身体已转向玩家，不能只验证服务器角度");
                require(frames.contains(index+"pretend-observed"),"听声回头后双方实际看到继续装挖");require(cues.stream().allMatch(c->Boolean.TRUE.equals(c.get("mined"))&&Boolean.TRUE.equals(c.get("playing"))&&Math.abs(((Number)c.get("volume")).floatValue()-1.5F)<.001F),"挖完之后双方实际以新音量播放身后声音");
                MultiplayerCheck.write(role+"-cave-"+index+"-audio.json",new Gson().toJson(cues));MultiplayerCheck.write(role+"-cave-"+index+"-facing.json",new Gson().toJson(Map.of("renderYaw",frog.yBodyRot,"expectedYaw",yaw)));
            }
        }}
        else if(sub==2){
            if(actor&&mc.player.getInventory().countItem(Naiwa.GAS.get())==0){require(frog!=null,"采气前演员存在");slot(mc,1);look(mc,frog.getEyePosition());
                // 脚下方块是真实挖掉的；靠近较高的身后地面时正常跳出坑，不能只向石壁持续前进。
                if(mc.player.distanceToSqr(frog)>7){mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(true);return;}
                mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);mc.gameMode.interact(mc.player,frog,InteractionHand.MAIN_HAND);return;}
            mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);
            ready=true;
        }
        if(ready){image(mc,"stage-"+sub);MultiplayerCheck.write(role+"-"+phase+".ok","PASS\n");}
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write(role+"-result.txt","FAIL "+failure);mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);mc.gameMode.stopDestroyBlock();}catch(Exception ignored){}mc.stop();}}
}
