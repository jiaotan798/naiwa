package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 两端轮流用真实致命伤触发图腾；不延长演出，检查共享世界时间和天气不被本机特效改写。 */
final class AscensionCheck {
    private int trial,step,age;
    private boolean initialized,done;
    private double riseX;
    private final int totalTrials;
    AscensionCheck(){
        try {var file=MultiplayerCheck.ROOT.resolve("shader-cases.json");totalTrials=Files.exists(file)?Math.max(1,new com.google.gson.Gson().fromJson(Files.readString(file),String[].class).length)*2:2;}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private boolean ack(String role){return Files.exists(MultiplayerCheck.ROOT.resolve(role+"-ascension-"+(trial*10+step)+".ok"));}
    private void stage(int next)throws Exception{step=next;age=0;MultiplayerCheck.write("stage.txt",""+(trial*10+step));System.out.println("NAIWA_MULTIPLAYER ascension="+(trial*10+step));}
    private void prepare(ServerPlayer a,ServerPlayer v)throws Exception{
        var level=a.serverLevel();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        for(var p:List.of(a,v)){
            NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);p.removeAllEffects();p.setGameMode(GameType.SURVIVAL);
            p.getInventory().clearContent();p.setHealth(20);p.getFoodData().setFoodLevel(20);
            p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);
        }
        // 第二轮交换触发者，确保两个真实客户端都经历本机黑夜和旁观者正常白天。
        var owner=trial%2==1?v:a;var observer=trial%2==1?a:v;
        owner.connection.teleport(.5,81,.5,0,-12);observer.connection.teleport(8,81,-10,35,-15);
        owner.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Naiwa.TOTEM.get()));
        stage(1);
    }
    private void tick(ServerTickEvent.Post event){if(done)return;try{
        var server=event.getServer();var a=server.getPlayerList().getPlayerByName("NaiwaActor");var v=server.getPlayerList().getPlayerByName("NaiwaViewer");
        if(a==null||v==null||!Files.exists(MultiplayerCheck.ROOT.resolve("actor-connected.ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-connected.ok")))return;
        if(!initialized){initialized=true;var level=server.overworld();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)level.setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);
            prepare(a,v);return;
        }
        // 只有基线准备期允许光影重新编译；飞升各阶段仍使用原时钟，不延长演出掩盖帧率问题。
        if(++age>(step==1?6000:1200))throw new AssertionError("飞升专项阶段超时 trial="+trial+" step="+step);
        MultiplayerCheck.require(server.overworld().getDayTime()==6000&&!server.overworld().isRaining(),"本机黑夜不能改服务端白天和天气");
        var owner=trial%2==1?v:a;
        // 第一人称专项先拍空手，再用真实服务端背包同步触发主副手绘制；不向客户端伪造物品。
        String handsOwner=trial%2==1?"viewer":"actor";
        if(Boolean.getBoolean("naiwa.qa.hands")&&step==3&&owner.getMainHandItem().isEmpty()
                &&Files.exists(MultiplayerCheck.ROOT.resolve(handsOwner+"-hands-"+trial+"-0.ok"))){
            owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            owner.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(net.minecraft.world.item.Items.TORCH));
        }
        if(step==1&&age>=80&&ack("actor")&&ack("viewer")){
            owner.invulnerableTime=0;owner.hurt(owner.damageSources().generic(),1000);
            MultiplayerCheck.require(owner.isAlive()&&owner.getOffhandItem().isEmpty()&&owner.hasEffect(Naiwa.ASCENDING),"真实图腾保命并消耗");stage(2);
        }else if(step==2&&owner.hasEffect(Naiwa.ASCENDING)&&owner.getEffect(Naiwa.ASCENDING).getAmplifier()==2){
            if(Boolean.getBoolean("naiwa.qa.hands"))MultiplayerCheck.require(Files.exists(MultiplayerCheck.ROOT.resolve(handsOwner+"-hands-"+trial+"-4.ok")),"上升期完成隐藏手部截图");
            MultiplayerCheck.require(ack("actor")&&ack("viewer"),"两端实际采到上升阶段");
            MultiplayerCheck.require(Math.abs(owner.getY()-87)<.1,"六格上升");stage(3);
            MultiplayerCheck.require(Math.abs(owner.getX()-.5)>.2,"实际按键在上升期产生水平移动");riseX=owner.getX();
        }else if(step==3&&owner.hasEffect(Naiwa.ASCENDING)&&owner.getEffect(Naiwa.ASCENDING).getAmplifier()==3){
            if(Boolean.getBoolean("naiwa.qa.hands"))for(int handCase=0;handCase<3;handCase++)
                MultiplayerCheck.require(Files.exists(MultiplayerCheck.ROOT.resolve(handsOwner+"-hands-"+trial+"-"+handCase+".ok")),"原悬停时间内完成三种真实手部截图");
            MultiplayerCheck.require(ack("actor")&&ack("viewer"),"两端实际采到悬停阶段");stage(4);
            MultiplayerCheck.require(Math.abs(owner.getX()-riseX)>.2,"实际按键在六秒悬停期仍能移动");
        }else if(step==4&&!owner.hasEffect(Naiwa.ASCENDING)&&owner.onGround()){
            if(Boolean.getBoolean("naiwa.qa.hands"))MultiplayerCheck.require(Files.exists(MultiplayerCheck.ROOT.resolve(handsOwner+"-hands-"+trial+"-5.ok")),"缓降期完成隐藏手部截图");
            MultiplayerCheck.require(ack("actor")&&ack("viewer"),"两端实际采到下降阶段");
            MultiplayerCheck.require(!owner.isNoGravity(),"飞升后恢复重力");stage(5);
        }else if(step==5&&age>=80&&ack("actor")&&ack("viewer")){
            if(Boolean.getBoolean("naiwa.qa.hands"))MultiplayerCheck.require(Files.exists(MultiplayerCheck.ROOT.resolve(handsOwner+"-hands-"+trial+"-3.ok")),"落地后完成原版手部恢复截图");
            if(++trial<totalTrials)prepare(a,v);else{
                MultiplayerCheck.write("server-result.txt","PASS "+totalTrials+" real totems, both owners, horizontal input, six-block flight, actual landing, unchanged server time/weather\n");MultiplayerCheck.write("stage.txt","99");done=true;
            }
        }
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write("server-result.txt","FAIL "+failure);MultiplayerCheck.write("stage.txt","-1");}catch(Exception ignored){}}}
}
