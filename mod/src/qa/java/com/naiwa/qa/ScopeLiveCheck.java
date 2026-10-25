package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 空场只给原版望远镜，由成品客户端按真实投影发起；服务端核对生成、放下采集或转头退场。 */
final class ScopeLiveCheck {
    // FOV、窗口宽高、俯仰、是否取消；窄视野、宽视野、宽屏和抬头必须仍能看全头部。
    static final int[][] CASES={{30,1000,760,0,1},{70,1000,760,0,0},{110,1000,760,0,1},{70,1920,1080,0,0},{70,1000,760,-15,1},{70,1000,760,0,1}};
    private int index,phase,age;private boolean done;private NaiwaEntity frog;
    ScopeLiveCheck(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void require(boolean ok,String why){MultiplayerCheck.require(ok,"scope-live="+index+" phase="+phase+": "+why);}
    private boolean ack(){return Files.exists(MultiplayerCheck.ROOT.resolve("actor-live-"+phase+".ok"))&&Files.exists(MultiplayerCheck.ROOT.resolve("viewer-live-"+phase+".ok"));}
    private void stage(int next)throws Exception{age=0;phase=next;MultiplayerCheck.write("stage.txt",""+phase);System.out.println("NAIWA_MULTIPLAYER scope-live="+index+" phase="+phase);}
    private void fixture(ServerPlayer a,ServerPlayer v)throws Exception{
        NaiwaPranks.cancel(a);frog=null;
        for(var p:List.of(a,v)){p.stopUsingItem();p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getFoodData().setFoodLevel(20);p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);p.getPersistentData().putString("naiwa.prank_last","");}
        a.getInventory().items.set(0,new ItemStack(Items.SPYGLASS));a.getInventory().items.set(1,new ItemStack(Items.GLASS_BOTTLE,3));a.inventoryMenu.broadcastFullState();
        a.connection.teleport(.5,81,.5,0,CASES[index][3]);v.connection.teleport(4,81,1,0,0);MultiplayerCheck.write("scope-fit-actor.txt","-1");stage(index*10+1);
    }
    private NaiwaEntity sceneActor(ServerPlayer p)throws Exception{var field=NaiwaPranks.class.getDeclaredField("SCENES");field.setAccessible(true);Object scene=((Map<?,?>)field.get(null)).get(p.getUUID());if(scene==null)return null;var actor=scene.getClass().getDeclaredField("frog");actor.setAccessible(true);return (NaiwaEntity)actor.get(scene);}
    private void tick(ServerTickEvent.Post event){if(done)return;try{
        var server=event.getServer();var a=server.getPlayerList().getPlayerByName("NaiwaActor");var v=server.getPlayerList().getPlayerByName("NaiwaViewer");
        if(a==null||v==null||!Files.exists(MultiplayerCheck.ROOT.resolve("actor-connected.ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-connected.ok")))return;
        require(++age<1200,"阶段超时");
        if(phase==0){var level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(1000);for(int x=-10;x<=10;x++)for(int z=-4;z<=40;z++)level.setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);fixture(a,v);return;}
        int sub=phase%10;
        if(sub==1&&ack()){require(a.isUsingItem(),"真实开镜已同步");require(sceneActor(a)==null,"空场没有预置奶蛙");a.getPersistentData().putLong("naiwa.prank_next",0);stage(phase+1);}
        else if(sub==2){frog=sceneActor(a);if(frog==null)return;require(frog.isTemporary(),"成品生成临时演员");double distance=frog.position().subtract(a.position()).horizontalDistance();require(distance>=8&&distance<=64,"实际水平距离在协议范围内");MultiplayerCheck.write("scope-fit-actor.txt",""+frog.getId());MultiplayerCheck.write("scope-live-"+index+"-distance.txt",""+distance);stage(phase+1);}
        else if(sub==3&&ack()){require(NaiwaPranks.busy(a)&&a.isUsingItem(),"镜内测量期间演员与开镜保持");stage(phase+1);}
        else if(sub==4&&ack()){
            if(CASES[index][4]==1){if(NaiwaPranks.busy(a))return;require(!frog.isAlive()&&a.getInventory().countItem(Naiwa.GAS.get())==0,"转头清除无奖励");}
            else {if(NaiwaPranks.busy(a))return;require(!frog.isAlive()&&a.getInventory().countItem(Naiwa.GAS.get())==1,"放下镜筒、大笑后真实采集一瓶并清场");}
            stage(phase+1);
        }else if(sub==5&&ack()){MultiplayerCheck.write("scope-live-"+index+"-result.txt","PASS no resident actor, native scope head fit, "+(CASES[index][4]==1?"turn-away cleanup":"release laugh and collection")+"\n");if(++index<CASES.length)fixture(a,v);else {MultiplayerCheck.write("server-result.txt","PASS six native spyglass configurations, two real clients, temporary actors, release/collection and abort cleanup\n");MultiplayerCheck.write("stage.txt","99");done=true;}}
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write("server-result.txt","FAIL "+failure);MultiplayerCheck.write("stage.txt","-1");}catch(Exception ignored){}}}
}
