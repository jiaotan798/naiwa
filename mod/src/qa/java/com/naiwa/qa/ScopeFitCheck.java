package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 只校准镜内距离：让正式整蛊持续处于冷却，以原版望远镜和正常游戏投影测量源模型头部。 */
final class ScopeFitCheck {
    static final double[] DISTANCES={4,6,8,10,12,14,16};
    private int index=-1,age;private boolean done;private NaiwaEntity frog;
    ScopeFitCheck(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void tick(ServerTickEvent.Post event){if(done)return;try{
        var server=event.getServer();ServerPlayer a=server.getPlayerList().getPlayerByName("NaiwaActor"),v=server.getPlayerList().getPlayerByName("NaiwaViewer");
        if(a==null||v==null||!Files.exists(MultiplayerCheck.ROOT.resolve("actor-connected.ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-connected.ok")))return;
        if(index<0){var level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(1000);
            for(int x=-10;x<=10;x++)for(int z=-4;z<=25;z++)level.setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);
            for(var p:List.of(a,v)){p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);}
            a.getInventory().items.set(0,new ItemStack(Items.SPYGLASS));a.inventoryMenu.broadcastFullState();a.connection.teleport(.5,81,.5,0,0);v.connection.teleport(4,81,1,0,0);
            frog=Naiwa.ENTITY.get().create(level);frog.setNoAi(true);frog.setYRot(180);frog.setYHeadRot(180);frog.yBodyRot=180;level.addFreshEntity(frog);index=0;stage();return;
        }
        if(++age>600)throw new AssertionError("望远镜距离校准超时 index="+index);
        if(!Files.exists(MultiplayerCheck.ROOT.resolve("actor-fit-"+index+".ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-fit-"+index+".ok")))return;
        if(++index<DISTANCES.length)stage();else{frog.discard();MultiplayerCheck.write("server-result.txt","PASS native spyglass calibration\n");MultiplayerCheck.write("stage.txt","99");done=true;}
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write("server-result.txt","FAIL "+failure);MultiplayerCheck.write("stage.txt","-1");}catch(Exception ignored){}}}
    private void stage()throws Exception {age=0;frog.setPos(.5,81,.5+DISTANCES[index]);MultiplayerCheck.write("scope-fit-actor.txt",""+frog.getId());MultiplayerCheck.write("stage.txt",""+(index+1));System.out.println("NAIWA scope-fit distance="+DISTANCES[index]);}
}
