package com.naiwa.qa;

import com.google.gson.Gson;
import com.naiwa.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 在新生成的原生大洞室实挖脚下地面及洞壁；不改地形、不直接启动或结束整蛊。 */
final class CaveMiningCheck {
    record Trial(String label,String tool,int x,int y,int z,double px,double py,double pz,double vx,double vy,double vz,String face,int height,int openCells) {
        BlockPos target(){return new BlockPos(x,y,z);} Vec3 player(){return new Vec3(px,py,pz);} Vec3 viewer(){return new Vec3(vx,vy,vz);}
        AABB area(){return new AABB(BlockPos.containing(player())).inflate(12);}
    }
    private final List<Trial> trials=new ArrayList<>();
    private final Map<BlockPos,BlockState> terrain=new HashMap<>();
    private int phase,index,ticks,started,settledAt=-1;private long digAt=-1;private boolean done,hidden;
    private NaiwaEntity frog;private long pretendStarted=-1;private boolean pretended;
    CaveMiningCheck(){NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,this::dig);}
    private void require(boolean ok,String message){MultiplayerCheck.require(ok,"cave mining case="+index+" phase="+phase+": "+message);}
    private Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private Object scene(ServerPlayer p)throws Exception{var f=NaiwaPranks.class.getDeclaredField("SCENES");f.setAccessible(true);return ((Map<?,?>)f.get(null)).get(p.getUUID());}
    private boolean ack(String role){return Files.exists(MultiplayerCheck.ROOT.resolve(role+"-"+phase+".ok"));}
    private void stage(int next)throws Exception{phase=next;started=ticks;MultiplayerCheck.write("stage.txt",""+phase);System.out.println("NAIWA_MULTIPLAYER cave-mining case="+index+" "+trials.get(index).label+" stage="+phase);}
    private boolean safe(ServerLevel l,Vec3 p){BlockPos b=BlockPos.containing(p);var box=new AABB(p.x-.45,p.y,p.z-.45,p.x+.45,p.y+2,p.z+.45);return l.getBlockState(b.below()).isFaceSturdy(l,b.below(),Direction.UP)&&l.noCollision(box)&&!l.containsAnyLiquid(box);}
    private boolean dry(ServerLevel l,Vec3 p){for(BlockPos b:BlockPos.betweenClosed(BlockPos.containing(p).offset(-2,-2,-2),BlockPos.containing(p).offset(2,3,2)))if(!l.getFluidState(b).isEmpty())return false;return true;}
    private Trial describe(ServerLevel l,String label,Item tool,BlockPos target,Vec3 p,Vec3 v,Direction face){int height=0,open=0;BlockPos feet=BlockPos.containing(p);while(height<48&&l.getBlockState(feet.above(height)).getCollisionShape(l,feet.above(height)).isEmpty())height++;for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)if(l.getBlockState(feet.offset(x,1,z)).getCollisionShape(l,feet.offset(x,1,z)).isEmpty())open++;return new Trial(label,BuiltInRegistries.ITEM.getKey(tool).toString(),target.getX(),target.getY(),target.getZ(),p.x,p.y,p.z,v.x,v.y,v.z,face.name(),height,open);}
    private void discover(ServerLevel l)throws Exception {
        Item[] tools={Items.WOODEN_PICKAXE,Items.STONE_PICKAXE,Items.IRON_PICKAXE,Items.GOLDEN_PICKAXE,Items.DIAMOND_PICKAXE,Items.NETHERITE_PICKAXE};
        // 坐标来自既有种子 8675309 的天然洞室记录，本轮重新生成世界；只读寻找旁边不同的原生地面。
        NaturalSite[] sites={new NaturalSite("mine","deepslate-large",new BlockPos(25,-1,-10),3),new NaturalSite("mine","stone-large",new BlockPos(38,24,-29),1)};
        for(var site:sites){
            for(int cx=(site.x-24)>>4;cx<=(site.x+24)>>4;cx++)for(int cz=(site.z-24)>>4;cz<=(site.z+24)>>4;cz++)l.getChunk(cx,cz);
            Vec3 origin=site.point(.5,0,3);List<Vec3> chosen=new ArrayList<>();int found=0;
            for(int r=4;r<=14&&found<6;r++)for(int dx=-r;dx<=r&&found<6;dx++)for(int dz=-r;dz<=r&&found<6;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
                for(int dy=-4;dy<=4&&found<6;dy++){
                    Vec3 p=Vec3.atBottomCenterOf(BlockPos.containing(origin).offset(dx,dy,dz));BlockPos floor=BlockPos.containing(p).below();
                    if(chosen.stream().anyMatch(q->q.distanceToSqr(p)<9)||!l.getBlockState(floor).is(BlockTags.BASE_STONE_OVERWORLD)||!l.getBlockState(floor.below()).isFaceSturdy(l,floor.below(),Direction.UP)||l.canSeeSky(BlockPos.containing(p))||!safe(l,p)||!dry(l,p))continue;
                    Vec3 v=null;for(int step:new int[]{0,1,-1})for(Vec3 offset:List.of(new Vec3(3,step,0),new Vec3(-3,step,0),new Vec3(0,step,3),new Vec3(0,step,-3)))if(v==null&&safe(l,p.add(offset))&&dry(l,p.add(offset)))v=p.add(offset);
                    if(v==null)continue;Trial trial=describe(l,site.label+"-floor-"+found,tools[found],floor,p,v,Direction.UP);
                    if(trial.height<10||trial.openCells<40)continue;chosen.add(p);trials.add(trial);found++;
                }
            }
            require(found==6,"原生大洞室有六处不同安全地面，净高至少十格且周边空气至少 40/81，实际="+found);
            Trial wall=null;
            for(var floor:List.copyOf(trials))if(wall==null&&floor.label.startsWith(site.label))for(BlockPos b:BlockPos.betweenClosed(BlockPos.containing(floor.player()).offset(-3,0,-3),BlockPos.containing(floor.player()).offset(3,2,3))) {
                if(wall!=null||!l.getBlockState(b).is(BlockTags.BASE_STONE_OVERWORLD)||Vec3.atCenterOf(b).distanceToSqr(floor.player().add(0,1.62,0))>16)continue;
                var hit=l.clip(new ClipContext(floor.player().add(0,1.62,0),Vec3.atCenterOf(b),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
                if(hit.getBlockPos().equals(b))wall=describe(l,site.label+"-wall",Items.IRON_PICKAXE,b.immutable(),floor.player(),floor.viewer(),hit.getDirection());
            }
            require(wall!=null,"大洞室干燥地面附近有可正常实挖的原生洞壁");trials.add(wall);
        }
        MultiplayerCheck.write("cave-trials.json",new Gson().toJson(Map.of("seed",8675309,"cases",trials)));
    }
    private void fixture(ServerPlayer a,ServerPlayer v)throws Exception {
        var l=a.serverLevel();var t=trials.get(index);NaiwaPranks.cancel(a);NaiwaPranks.cancel(v);frog=null;digAt=-1;hidden=false;pretendStarted=-1;pretended=false;terrain.clear();
        // 前一例是真正采矿，不能回填地面；后续洞壁对照或旁观者重用该处时，站在当前实际地面上。
        Vec3 pa=t.player(),pv=t.viewer();
        for(int dy=1;dy<=2&&!safe(l,pa);dy++)if(safe(l,t.player().add(0,-dy,0)))pa=t.player().add(0,-dy,0);
        for(int dy=1;dy<=2&&!safe(l,pv);dy++)if(safe(l,t.viewer().add(0,-dy,0)))pv=t.viewer().add(0,-dy,0);
        require(safe(l,pa)&&safe(l,pv),"不回填前例采矿方块，当前双端仍有原生安全地面");
        t=describe(l,t.label,BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(t.tool)),t.target(),pa,pv,Direction.valueOf(t.face));trials.set(index,t);
        MultiplayerCheck.write("cave-trials.json",new Gson().toJson(Map.of("seed",8675309,"cases",trials)));
        for(var e:l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,t.area(),e->!(e instanceof ServerPlayer)))e.discard();
        for(var p:List.of(a,v)){p.stopUsingItem();p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getFoodData().setFoodLevel(20);p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,99999,0,false,false));p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);p.getPersistentData().putString("naiwa.prank_last","");}
        a.getInventory().items.set(0,new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(t.tool))));a.getInventory().items.set(1,new ItemStack(Items.GLASS_BOTTLE));
        a.inventoryMenu.broadcastFullState();v.inventoryMenu.broadcastFullState();a.connection.teleport(t.px,t.py,t.pz,0,0);v.connection.teleport(t.vx,t.vy,t.vz,0,0);
        MultiplayerCheck.write("cave-case.json",new Gson().toJson(t));MultiplayerCheck.write("cave-actor.txt","-1");stage(3006+index*10);
    }
    private void dig(PlayerInteractEvent.LeftClickBlock e){
        if(done||phase<3000||phase%10!=0||digAt>=0||!e.getAction().name().equals("START")||!e.getEntity().getGameProfile().getName().equals("NaiwaActor")||!e.getPos().equals(trials.get(index).target()))return;
        digAt=e.getLevel().getGameTime();
        try{MultiplayerCheck.write("dig-"+index+".txt","actual START packet; tool="+e.getItemStack()+" pickaxeTag="+e.getItemStack().is(ItemTags.PICKAXES)+" seesSky="+e.getLevel().canSeeSky(e.getEntity().blockPosition())+" offhand="+e.getEntity().getOffhandItem()+" next="+e.getEntity().getPersistentData().getLong("naiwa.prank_next")+" now="+digAt+"\n");}catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private void tick(ServerTickEvent.Post e){
        if(done)return;ticks++;
        try{var server=e.getServer();var a=server.getPlayerList().getPlayerByName("NaiwaActor");var v=server.getPlayerList().getPlayerByName("NaiwaViewer");if(a==null||v==null||!Files.exists(MultiplayerCheck.ROOT.resolve("actor-connected.ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-connected.ok")))return;
            var l=server.overworld();if(phase==0){
                if(settledAt<0){l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);server.setDifficulty(Difficulty.PEACEFUL,true);a.setGameMode(GameType.SPECTATOR);v.setGameMode(GameType.SPECTATOR);a.connection.teleport(28,-1,-9.5,0,0);v.connection.teleport(36,24,-28.5,0,0);settledAt=ticks;}
                // 刚加载的瀑布仍会流动；先激活两个洞室，再只读挑选干燥场地，不能让水把实挖玩家冲出交互距离。
                if(ticks-settledAt<400)return;discover(l);fixture(a,v);return;
            }
            require(ticks-started<600,"阶段超时，digAt="+digAt+" frog="+frog);var t=trials.get(index);int sub=(phase-3000)%10;
            if(sub==6){if(ticks-started<100||!ack("actor")||!ack("viewer"))return;require(a.position().distanceToSqr(t.player())<.1&&v.position().distanceToSqr(t.viewer())<.1,"实挖前双端必须在既定站位，不得被水流或残留输入推走："+a.position());for(BlockPos b:BlockPos.betweenClosed(BlockPos.containing(t.player()).offset(-8,-2,-8),BlockPos.containing(t.player()).offset(8,4,8)))terrain.put(b.immutable(),l.getBlockState(b));a.getPersistentData().putLong("naiwa.prank_next",0);stage(phase-6);return;}
            if(frog==null&&NaiwaPranks.busy(a)){Object scene=scene(a);require(field(scene,"kind").equals("mine"),"实际挖掘应触发 mine，实际="+field(scene,"kind"));frog=(NaiwaEntity)field(scene,"frog");
                Vec3 forward=Vec3.directionFromRotation(0,a.getYRot()),offset=frog.position().subtract(a.position()).multiply(1,0,1);
                require(forward.dot(offset.normalize())<-.65,"演员必须在起镐时玩家身后扇区");
                require(offset.length()>=4.99&&offset.length()<=6.01,"身后出场保持五至六格水平距离，实际="+offset.length());
                var target=(BlockHitResult)field(scene,"mineFace");require(target!=null,"出场时有实际可见的近处方块");
                require(frog.getLookAngle().dot(target.getLocation().subtract(frog.getEyePosition()).normalize())>.99,"初始身体及头部看向近处方块");
                MultiplayerCheck.write("cave-"+index+"-spawn.json",new Gson().toJson(Map.of("player",a.position().toString(),"frog",frog.position().toString(),"rearDot",forward.dot(offset.normalize()),"horizontalDistance",offset.length(),"target",target.getBlockPos().toShortString())));MultiplayerCheck.write("cave-actor.txt",""+frog.getId());}
            if(frog!=null&&frog.isAlive()){hidden|=frog.act().equals("mine_hide");require(l.noCollision(frog,frog.getBoundingBox()),"演员没有穿入原生方块");}
            if(sub==0){if(digAt>=0&&l.getGameTime()-digAt>=20)require(frog!=null,"实际挖掘一秒后没有矿洞演员；镐、地下条件和共享冷却见 dig 记录");if(frog==null||!l.getBlockState(t.target()).isAir()||!ack("actor")||!ack("viewer"))return;require(a.getMainHandItem().getDamageValue()==1,"原版生存镐真正挖完一次，耐久只扣一");stage(phase+1);}
            else if(sub==1){
                Object active=scene(a);require(active!=null,"观察阶段场景结束：elapsed="+(l.getGameTime()-digAt)+" player="+a.position()+" frogAlive="+frog.isAlive()+" visible="+a.hasLineOfSight(frog));long observed=(long)field(active,"mineObserved");
                if(observed>=0){if(pretendStarted!=observed){pretendStarted=observed;pretended=false;}long elapsed=l.getGameTime()-observed;
                    if(elapsed<40){require(frog.act().equals("mine")&&!frog.animation().equals("laugh_sequence"),"玩家看向演员的前两秒继续装挖，不能提前转身或大笑");if(elapsed>=30)pretended=true;}
                }
                if(!frog.animation().equals("laugh_sequence")||!ack("actor")||!ack("viewer"))return;
                require(pretended&&hidden&&l.getGameTime()-pretendStarted>=60,"先装挖两秒，再转向约一秒，最后大笑");
                Vec3 toPlayer=a.getEyePosition().subtract(frog.getEyePosition());require(frog.getLookAngle().dot(toPlayer.normalize())>.97,"大笑开始前已真正转头看向玩家");
                MultiplayerCheck.write("cave-"+index+"-timing.json",new Gson().toJson(Map.of("observedAt",pretendStarted,"laughAt",field(active,"laughing"),"pretended",pretended,"turnHidden",hidden)));stage(phase+1);
            }
            else if(sub==2){if(NaiwaPranks.busy(a)||!ack("actor")||!ack("viewer"))return;require(a.getInventory().countItem(Naiwa.GAS.get())==1&&!frog.isAlive(),"实际采气一次且演员清理");for(var state:terrain.entrySet())if(!state.getKey().equals(t.target()))require(l.getBlockState(state.getKey()).equals(state.getValue()),"只有玩家实挖的方块可变化："+state.getKey());MultiplayerCheck.write("cave-case-"+index+"-result.txt","PASS "+new Gson().toJson(t)+"; real mined block, durability=1, mine/hide/laugh/collection/cleanup, terrain="+terrain.size()+"\n");if(++index<trials.size())fixture(a,v);else{MultiplayerCheck.write("server-result.txt","PASS "+trials.size()+" native large-cavern trials, six vanilla pickaxes, peaceful difficulty, real mining and collection\n");MultiplayerCheck.write("stage.txt","99");done=true;}}
        }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write("server-result.txt","FAIL "+failure);MultiplayerCheck.write("stage.txt","-1");}catch(Exception ignored){}}
    }
}
