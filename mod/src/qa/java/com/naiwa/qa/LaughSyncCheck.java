package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.Files;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 用真实空手交互、自动盾转化和管理员单片段命令，验证完整动作与旧单段入口。 */
final class LaughSyncCheck {
    private int phase,ticks,started;
    private long animationStart=-1;
    private NaiwaEntity frog;
    private boolean done;
    LaughSyncCheck(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private boolean ack(int stage){return Files.exists(MultiplayerCheck.ROOT.resolve("actor-"+stage+".ok"))&&Files.exists(MultiplayerCheck.ROOT.resolve("viewer-"+stage+".ok"));}
    private void stage(int next)throws Exception{phase=next;started=ticks;animationStart=-1;MultiplayerCheck.write("stage.txt",Integer.toString(next));System.out.println("NAIWA_MULTIPLAYER laugh-sync stage="+next);}
    /** 每轮重建演员并清空手持物，防止上一轮盾转化自动触发下一轮。 */
    private void prepare(ServerPlayer actor,ServerPlayer viewer)throws Exception{
        if(frog!=null)frog.discard();
        for(var p:new ServerPlayer[]{actor,viewer}){NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);}
        actor.connection.teleport(.5,81,3.2,180,10);viewer.connection.teleport(2.5,81,3.2,144,10);
        // 最后一个单片段回合降低观察者脚下平台，真实仰视下颌和前颈；不改变前两轮交互与盾牌验收。
        if(Boolean.getBoolean("naiwa.qa.faceClose")&&phase==4){
            for(int x=-1;x<=1;x++)for(int z=2;z<=4;z++){
                actor.serverLevel().setBlockAndUpdate(new BlockPos(x,80,z),Blocks.AIR.defaultBlockState());
                actor.serverLevel().setBlockAndUpdate(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState());
            }
            actor.connection.teleport(.5,80,3.2,180,0);
        }
        frog=Naiwa.ENTITY.get().create(actor.level());frog.setNoAi(true);frog.setPos(.5,81,.5);actor.serverLevel().addFreshEntity(frog);
        MultiplayerCheck.write("laugh-entity.txt",Integer.toString(frog.getId()));
    }
    private void tick(ServerTickEvent.Post event){
        if(done)return;ticks++;
        try{
            MultiplayerCheck.require(ticks-started<6000,"音画验收阶段超时 "+phase);
            var server=event.getServer();var a=server.getPlayerList().getPlayerByName("NaiwaActor");var v=server.getPlayerList().getPlayerByName("NaiwaViewer");
            if(a==null||v==null)return;
            if(phase==0){
                if(!Files.exists(MultiplayerCheck.ROOT.resolve("actor-connected.ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-connected.ok")))return;
                var level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)level.setBlockAndUpdate(new BlockPos(x,80,z),Blocks.SMOOTH_STONE.defaultBlockState());
                prepare(a,v);stage(1);return;
            }
            if(phase%2==1){
                if(!ack(phase))return;
                if(phase==3)a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.SHIELD));
                int next=phase+1;stage(next);
                if(next==6){server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"naiwa animation @e[type=naiwa:naiwa,limit=1] laugh");MultiplayerCheck.require(frog.animation().equals("laugh"),"真实动画命令必须成功");}
                return;
            }
            if(!frog.animation().equals("auto")){
                long start=frog.level().getGameTime()-Math.round(frog.animationSeconds(0)*20);
                if(animationStart<0)animationStart=start;
                MultiplayerCheck.require(start==animationStart,"重复交互不能重置动画或重播音效");
            }
            if(!ack(phase))return;
            MultiplayerCheck.require(animationStart>=0&&frog.animation().equals("auto"),"完整动作必须实际启动并正常结束");
            if(phase==4)MultiplayerCheck.require(a.getMainHandItem().is(Naiwa.SHIELD.get()),"自动盾转化仍须正常完成");
            if(phase==6){MultiplayerCheck.write("server-result.txt","PASS: empty-hand repeat interaction, automatic shield conversion, native animation command; unchanged animation start and normal completion.\n");stage(99);done=true;}
            else{prepare(a,v);stage(phase+1);}
        }catch(Throwable e){done=true;e.printStackTrace();try{MultiplayerCheck.write("server-result.txt","FAIL "+e);MultiplayerCheck.write("stage.txt","-1");}catch(Exception ignored){}}
    }
}
