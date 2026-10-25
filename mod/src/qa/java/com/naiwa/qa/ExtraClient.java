package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import org.lwjgl.glfw.GLFW;

/** 客户端通过原版挖矿、移动、交互、攻击和望远镜完成玩法；音效记录来自真实声道事件。 */
final class ExtraClient {
    private final String role=System.getProperty("naiwa.qa.role");private final boolean actor=role.equals("actor");
    private int last=-1,age,action,ticks,index,fuses,badFrames;private boolean done,abort;private String kind;
    private final Set<String> recorded=new HashSet<>();private String previous="";
    ExtraClient(){
        NeoForge.EVENT_BUS.addListener((PlaySoundSourceEvent e)->{if(e.getSound().getLocation().equals(ResourceLocation.withDefaultNamespace("entity.creeper.primed")))fuses++;});
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->tick());
    }
    private void require(boolean ok,String why){MultiplayerCheck.require(ok,role+" extras="+index+" phase="+last+": "+why);}
    private void release(Minecraft mc){mc.options.keyUp.setDown(false);}
    private void look(Minecraft mc,Vec3 point){Vec3 d=point.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));}
    private boolean walk(Minecraft mc,Vec3 point,double stop){Vec3 d=point.subtract(mc.player.position()).multiply(1,0,1);if(d.length()<stop){mc.options.keyUp.setDown(false);return true;}look(mc,point.add(0,1,0));mc.options.keyUp.setDown(true);return false;}
    private void slot(Minecraft mc,int slot){mc.player.getInventory().selected=slot;}
    private void screenshot(Minecraft mc,String label)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-extra-"+index+"-"+label+".png"));}}
    /** 双窗口缩小或复用会留下 1×1 主缓冲；恢复 GLFW 实际画布后等下一帧，不能把占位图算验收截图。 */
    private boolean viewport(Minecraft mc){
        long handle=mc.getWindow().getWindow();int[] w={0},h={0};GLFW.glfwGetFramebufferSize(handle,w,h);
        if(w[0]>=900&&h[0]>=650&&mc.getMainRenderTarget().width>=900&&mc.getMainRenderTarget().height>=650&&!mc.noRender){badFrames=0;return true;}
        require(++badFrames<200,"无法恢复实际窗口画布");GLFW.glfwShowWindow(handle);GLFW.glfwRestoreWindow(handle);mc.getWindow().setWindowed(1000,760);GLFW.glfwSetWindowSize(handle,1000,760);GLFW.glfwGetFramebufferSize(handle,w,h);
        if(w[0]>=900&&h[0]>=650){mc.getWindow().setWidth(w[0]);mc.getWindow().setHeight(h[0]);mc.noRender=false;mc.resizeDisplay();}return false;
    }
    private void chest(Minecraft mc){look(mc,Vec3.atCenterOf(ExtraCheck.CHEST));mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(ExtraCheck.CHEST),Direction.SOUTH,ExtraCheck.CHEST,false));}
    private void close(Minecraft mc){mc.player.closeContainer();mc.setScreen(null);}
    private void tick(){
        // 挖矿进度须按每客户端 tick 推进；其余阶段仍半速轮询屏障，避免探针把实际开采耗时翻倍。
        if(done||++ticks%2!=0&&!("diamond".equals(kind)&&last%10==0))return;var mc=Minecraft.getInstance();
        try {
            if(mc.player==null||mc.level==null||mc.getOverlay()!=null||mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)return;
            if(!viewport(mc))return;
            if(Boolean.getBoolean("naiwa.qa.settings"))require(NaiwaConfig.MIN_INTERVAL_SECONDS.get()==1&&NaiwaConfig.MAX_INTERVAL_SECONDS.get()==1&&NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.get(),"服务端配置覆盖客户端本地五至十分钟与禁止重复设置");
            // 连接标记只写一次；Windows 读者短暂持有文件时反复替换会无谓地碰到 AccessDenied。
            if(!Files.exists(MultiplayerCheck.ROOT.resolve(role+"-connected.ok")))MultiplayerCheck.write(role+"-connected.ok",Long.toString(ProcessHandle.current().pid()));
            String marker=Files.readString(MultiplayerCheck.ROOT.resolve("stage.txt")).trim();if(marker.isEmpty())return;int phase=Integer.parseInt(marker);
            require(phase!=-1,"服务端失败");if(phase==99){release(mc);mc.options.keyUse.setDown(false);MultiplayerCheck.write(role+"-result.txt","PASS eight real-client extra prank cases\n");done=true;mc.stop();return;}if(phase==0)return;
            if(phase!=last){release(mc);last=phase;age=action=0;previous="";String[] parts=Files.readString(MultiplayerCheck.ROOT.resolve("extra-case.txt")).trim().split(" ");index=Integer.parseInt(parts[0]);kind=parts[1];abort=Boolean.parseBoolean(parts[2]);if(phase%10==6)fuses=0;}
            age++;int sub=(phase-2000)%10;
            // 阶段屏障只松移动键，不能替用户提前放下望远镜；真正的释放留给 2 阶段的原版操作。
            if(sub==6&&!(actor&&kind.equals("scope")))mc.options.keyUse.setDown(false);
            if(actor&&kind.equals("scope")&&(sub==0||sub==1))mc.options.keyUse.setDown(true);
            var frogs=mc.level.getEntitiesOfClass(NaiwaEntity.class,ExtraCheck.AREA);NaiwaEntity frog=frogs.isEmpty()?null:frogs.getFirst();
            var baits=mc.level.getEntitiesOfClass(NaiwaDecoy.class,ExtraCheck.AREA,e->!e.isInvisible());NaiwaDecoy bait=baits.isEmpty()?null:baits.getFirst();
            if(frog!=null){String state=frog.animation().equals("laugh_sequence")?"laugh_sequence":frog.act();if(!state.isEmpty()&&state.equals(previous)&&recorded.add(index+"-"+state))screenshot(mc,state);previous=state;}
            if(age<5||Files.exists(MultiplayerCheck.ROOT.resolve(role+"-"+phase+".ok")))return;
            require(mc.gameMode.getPlayerMode()==GameType.SURVIVAL&&mc.getSingleplayerServer()==null,"真实专用服生存客户端");boolean ready=false;
            if(sub==6){
                slot(mc,0);if(actor){mc.player.setYRot(kind.equals("chest")?180:0);mc.player.setXRot(0);}
                if(actor&&kind.equals("scope")) {
                    require(mc.player.getInventory().getItem(1).is(Items.SPYGLASS),"新场景背包已完整同步望远镜");
                    slot(mc,1);mc.player.setYRot(0);mc.player.setXRot(0);mc.options.keyUse.setDown(true);
                    if(!mc.player.isUsingItem())mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
                    ready=age>=12&&mc.player.isUsingItem();
                } else ready=age>=12;
            }
            else if(sub==0){
                if(!actor){if(frog!=null)look(mc,frog.getEyePosition());else if(bait!=null)look(mc,bait.position());ready=kind.equals("diamond")?bait!=null:frog!=null;}
                else switch(kind){
                    case "creeper" -> {mc.player.setYRot(0);mc.player.setXRot(0);if(frog!=null){if(!abort)look(mc,frog.getEyePosition());ready=true;}}
                    case "diamond" -> {
                        slot(mc,0);look(mc,Vec3.atCenterOf(ExtraCheck.ORE));
                        if(!mc.level.getBlockState(ExtraCheck.ORE).isAir()) {
                            if(action++==0)mc.gameMode.startDestroyBlock(ExtraCheck.ORE,Direction.NORTH);else mc.gameMode.continueDestroyBlock(ExtraCheck.ORE,Direction.NORTH);
                            mc.player.swing(InteractionHand.MAIN_HAND);
                        }else {mc.gameMode.stopDestroyBlock();if(bait!=null){look(mc,bait.position());ready=true;}}
                    }
                    case "chest" -> {
                        if(action==0){slot(mc,0);chest(mc);action=1;return;}
                        if(action==1&&mc.player.containerMenu instanceof ChestMenu menu){require(menu.getSlot(0).getItem().is(Items.APPLE)&&menu.getSlot(0).getItem().getCount()==37,"实际打开箱内原物品");close(mc);action=2;if(abort){mc.player.setYRot(0);mc.player.setXRot(0);}}
                        ready=action==2&&frog!=null;
                    }
                    case "scope" -> {
                        slot(mc,1);mc.player.setYRot(0);mc.player.setXRot(0);
                        if(action==0){mc.options.keyUse.setDown(true);if(!mc.player.isUsingItem())mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);action=1;}
                        // 临时标志只由服务端保存，不能拿未同步的客户端字段判定；权威检查在 ExtraCheck。
                        ready=frog!=null&&frog.distanceToSqr(mc.player)>=64&&mc.player.isUsingItem();
                    }
                }
            } else if(sub==1){
                if(kind.equals("diamond")){require(bait!=null&&frog==null,"诱饵独立同步，奶蛙尚未显示");look(mc,bait.position());}
                else {if(frog==null)return;if(!(actor&&(kind.equals("scope")||abort&&(kind.equals("creeper")||kind.equals("chest")))))look(mc,frog.getEyePosition());if(kind.equals("creeper")){require(fuses==1,"双方各收到一次真实假引燃声");MultiplayerCheck.write(role+"-fuse-"+index+".txt","PASS one actual audio channel\n");}
                    if(kind.equals("chest")){var box=(net.minecraft.world.level.block.entity.ChestBlockEntity)mc.level.getBlockEntity(ExtraCheck.CHEST);require(frog.act().equals("chest_peek")&&box.getOpenNess(0)>.95F,"双端头部探出且箱盖保持打开");}}
                ready=true;
            } else if(sub==2){
                if(actor)switch(kind){
                    case "creeper" -> {if(!abort&&frog!=null)look(mc,frog.getEyePosition());}
                    case "diamond" -> {
                        if(frog!=null){release(mc);look(mc,frog.getEyePosition());}
                        else if(bait!=null){if(abort){if(walk(mc,bait.position(),2.5)&&action==0){mc.gameMode.attack(mc.player,bait);action=1;}}else walk(mc,bait.position(),1.4);}
                    }
                    case "chest" -> {
                        if(abort)walk(mc,new Vec3(.5,81,24),.5);
                        else {if(action==0){chest(mc);action=1;}else if(action==1&&mc.player.containerMenu instanceof ChestMenu menu){require(menu.getSlot(0).getItem().getCount()==37,"重新开箱仍有完整原物品");close(mc);action=2;}if(frog!=null)look(mc,frog.getEyePosition());}
                    }
                    case "scope" -> {if(abort){mc.player.setYRot(180);mc.player.setXRot(0);}else {if(action==0){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);action=1;}if(frog!=null)look(mc,frog.getEyePosition());}}
                } else if(frog!=null)look(mc,frog.getEyePosition());
                ready=abort?frog==null&&bait==null:frog!=null&&frog.animation().equals("laugh_sequence");
            } else if(sub==3){
                if(!abort){
                    if(frog!=null)look(mc,frog.getEyePosition());
                    if(actor&&mc.player.getInventory().countItem(Naiwa.GAS.get())==0){
                        if(frog==null)return;slot(mc,2);
                        if(walk(mc,frog.position(),2.5)){release(mc);look(mc,frog.getEyePosition());mc.gameMode.interact(mc.player,frog,InteractionHand.MAIN_HAND);}return;
                    }
                    ready=!actor||mc.player.getInventory().countItem(Naiwa.GAS.get())==1;
                } else ready=true;
                if(ready&&age>=10)screenshot(mc,"result");else ready=false;
            } else if(sub==4){
                require(bait==null,"双端都没有诱饵残留");require(frog==null,"双端临时演员清理");
                if(actor&&kind.equals("diamond")) {
                    int expected=com.google.gson.JsonParser.parseString(Files.readString(MultiplayerCheck.ROOT.resolve("diamond-loot-"+index+".json"))).getAsJsonObject().get("expected").getAsInt();
                    if(mc.player.getInventory().countItem(Items.DIAMOND)<expected) {
                        var drops=mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,ExtraCheck.AREA,e->e.getItem().is(Items.DIAMOND));
                        if(!drops.isEmpty())walk(mc,drops.getFirst().position(),.3);return;
                    }
                    require(mc.player.getInventory().countItem(Items.DIAMOND)==expected,"奶蛙消失后客户端实际拾取了完整原掉落");
                }
                ready=true;
            }
            if(ready){release(mc);if(actor&&kind.equals("scope")&&sub==2){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}screenshot(mc,"stage-"+sub);MultiplayerCheck.write(role+"-"+phase+".ok","PASS\n");}
        }catch(Throwable error){done=true;release(mc);error.printStackTrace();try{MultiplayerCheck.write(role+"-result.txt","FAIL "+error);}catch(Exception ignored){}mc.stop();}
    }
}
