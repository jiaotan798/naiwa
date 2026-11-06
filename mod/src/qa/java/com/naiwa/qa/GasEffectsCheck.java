package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 客户端真实使用瓶子，分别命中墙面和敌对生物；两端确认粒子自然回收后才开始下一轮。 */
final class GasEffectsCheck {
    private int trial,step,age;private boolean initialized,done,sawProjectile,settled;private Husk target;
    GasEffectsCheck(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private boolean ack(String role){return Files.exists(MultiplayerCheck.ROOT.resolve(role+"-gas-"+(trial*10+step)+".ok"));}
    private void stage(int next)throws Exception{step=next;age=0;MultiplayerCheck.write("stage.txt",""+(trial*10+step));System.out.println("NAIWA_MULTIPLAYER gas="+(trial*10+step));}
    private void prepare(ServerPlayer actor,ServerPlayer viewer)throws Exception {
        var level=actor.serverLevel();if(target!=null)target.discard();target=null;
        for(var p:List.of(actor,viewer)){NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);p.removeAllEffects();p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);}
        for(int x=-5;x<=5;x++)for(int z=-3;z<=7;z++)for(int y=80;y<=85;y++)level.setBlock(new BlockPos(x,y,z),(y==80?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        actor.connection.teleport(.5,81,.5,0,25);viewer.connection.teleport(4,81,0,40,10);
        if(trial==0)for(int x=-3;x<=3;x++)for(int y=81;y<=84;y++)level.setBlock(new BlockPos(x,y,3),Blocks.STONE.defaultBlockState(),2);
        else {target=EntityType.HUSK.create(level);target.setPos(.5,81,3.5);target.setNoAi(true);target.setNoGravity(true);level.addFreshEntity(target);}
        actor.getInventory().setItem(0,new ItemStack(Naiwa.GAS.get(),2));actor.inventoryMenu.broadcastFullState();
        sawProjectile=settled=false;stage(1);
    }
    private void tick(ServerTickEvent.Post event){if(done)return;try {
        var server=event.getServer();var actor=server.getPlayerList().getPlayerByName("NaiwaActor");var viewer=server.getPlayerList().getPlayerByName("NaiwaViewer");
        if(actor==null||viewer==null||!Files.exists(MultiplayerCheck.ROOT.resolve("actor-connected.ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-connected.ok")))return;
        if(!initialized){initialized=true;server.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);prepare(actor,viewer);return;}
        if(++age>1200)throw new AssertionError("笑气粒子阶段超时");
        if(step==1&&ack("actor")&&ack("viewer"))stage(2);
        else if(step==2){
            var projectiles=actor.serverLevel().getEntitiesOfClass(LaughGasProjectile.class,actor.getBoundingBox().inflate(16));sawProjectile|=!projectiles.isEmpty();
            if(sawProjectile&&projectiles.isEmpty()&&!settled){
                MultiplayerCheck.require(actor.getInventory().countItem(Naiwa.GAS.get())==1,"一次真实使用只消耗一瓶");
                MultiplayerCheck.require(target==null||target.hasEffect(Naiwa.LAUGHING),"实体碰撞同时触发原控场");settled=true;
            }
            if(settled&&ack("actor")&&ack("viewer")){
                if(++trial<2)prepare(actor,viewer);else{MultiplayerCheck.write("server-result.txt","PASS real block/entity impacts, one bottle each, original mob control\n");MultiplayerCheck.write("stage.txt","99");done=true;}
            }
        }
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write("server-result.txt","FAIL "+failure);MultiplayerCheck.write("stage.txt","-1");}catch(Exception ignored){}}}
}
