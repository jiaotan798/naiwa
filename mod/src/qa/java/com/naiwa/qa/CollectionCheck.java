package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 七组真实背包结算，不发送采集指令；成功场景调用正式嘲笑入口，观察两端真实收到的物品。 */
final class CollectionCheck {
    private int trial,step,age;
    private boolean initialized,done;
    CollectionCheck(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void stage(int next)throws Exception{step=next;age=0;MultiplayerCheck.write("stage.txt",""+(trial*10+step));System.out.println("NAIWA_MULTIPLAYER collection="+(trial*10+step));}
    private boolean ack(String role){return Files.exists(MultiplayerCheck.ROOT.resolve(role+"-collection-"+(trial*10+step)+".ok"));}
    private void prepare(ServerPlayer a,ServerPlayer v)throws Exception{
        for(var p:List.of(a,v)){
            NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);p.removeAllEffects();p.getInventory().clearContent();p.getInventory().selected=0;
            p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getFoodData().setFoodLevel(20);p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);
        }
        var owner=trial%2==0?a:v;var observer=trial%2==0?v:a;
        owner.connection.teleport(.5,81,.5,0,0);observer.connection.teleport(1.5,81,6.5,180,0);
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));observer.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE,2));
        if(trial==2||trial==3||trial==4||trial==6)for(int i=0;i<36;i++)owner.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        if(trial==0||trial==3||trial==4||trial==6)owner.getInventory().setItem(22,new ItemStack(Items.GLASS_BOTTLE,3));
        if(trial==1)owner.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.GLASS_BOTTLE,2));
        if(trial==2){owner.getInventory().setItem(22,new ItemStack(Items.GLASS_BOTTLE));owner.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.GLASS_BOTTLE,8));}
        if(trial==3)owner.getInventory().setItem(23,new ItemStack(Naiwa.GAS.get(),15));
        if(trial==6)owner.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Naiwa.GAS.get(),15));
        owner.inventoryMenu.broadcastFullState();observer.inventoryMenu.broadcastFullState();stage(1);
    }
    private void expected(ServerPlayer owner,ServerPlayer observer,boolean retry){
        int gas=trial==3||trial==6?16:(trial==4||trial==5)&&!retry?0:1;
        int bottles=trial==2?8:trial==1?1:trial==5&&!retry?0:(trial==4&&!retry?3:2);
        MultiplayerCheck.require(owner.getInventory().countItem(Naiwa.GAS.get())==gas,"自动交付数量 trial="+trial);
        MultiplayerCheck.require(owner.getInventory().countItem(Items.GLASS_BOTTLE)==bottles,"自动消耗数量 trial="+trial);
        MultiplayerCheck.require(observer.getInventory().countItem(Naiwa.GAS.get())==0&&observer.getMainHandItem().getCount()==2,"更近的旁观者没有抢走自动奖励");
    }
    private void tick(ServerTickEvent.Post event){if(done)return;try{
        var server=event.getServer();
        if(!initialized){initialized=true;var level=server.overworld();server.setDifficulty(Difficulty.PEACEFUL,true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)level.setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);
        }
        var a=server.getPlayerList().getPlayerByName("NaiwaActor");var v=server.getPlayerList().getPlayerByName("NaiwaViewer");
        if(a==null||v==null||!Files.exists(MultiplayerCheck.ROOT.resolve("actor-connected.ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-connected.ok")))return;
        if(step==0){prepare(a,v);return;}
        if(++age>1200)throw new AssertionError("自动采集专项超时 trial="+trial+" step="+step);
        var owner=trial%2==0?a:v;var observer=trial%2==0?v:a;
        if(age<35||!ack("actor")||!ack("viewer"))return;
        if(step==1){
            MultiplayerCheck.require(NaiwaPranks.mock(owner),"固定三格创建所属演员");
            stage(2);
        }else if(step==2){
            expected(owner,observer,false);
            // 在完整大笑的采集窗口内腾格或补瓶，验证失败未扣物品，且收场前仍可自动重试。
            if(trial==4)owner.getInventory().setItem(23,ItemStack.EMPTY);
            if(trial==5)owner.getInventory().setItem(22,new ItemStack(Items.GLASS_BOTTLE,3));
            stage(3);
        }else{
            expected(owner,observer,true);
            if(++trial<7)prepare(a,v);else{NaiwaPranks.cancel(a);NaiwaPranks.cancel(v);MultiplayerCheck.write("server-result.txt","PASS seven automatic inventory cases, owner-only, no click, late space/bottle retry and no duplication\n");MultiplayerCheck.write("stage.txt","99");done=true;}
        }
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write("server-result.txt","FAIL "+failure);MultiplayerCheck.write("stage.txt","-1");}catch(Exception ignored){}}}
}
