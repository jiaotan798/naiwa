package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 仅预置地形和原版材料；十二轮整蛊均由实际客户端操作触发，不直接启动场景或发放奶蛙奖励。 */
final class SurvivalCheck {
    static final String[] KINDS={"mirror","door","tree","mine","fish","boat"};
    static final BlockPos ANCHOR=new BlockPos(0,81,0);
    static final AABB ARENA=new AABB(-16,75,-16,16,95,16);
    private int phase,ticks,started,kind,harvests,fishEvents,fishLoot,fishDamage;
    private NaiwaEntity frog;
    private Boat boat;
    private Husk hostile;
    private boolean quitting,mirrorCrouch,mirrorJump,doorGiggle,doorSerious;
    private float health;
    private ServerPlayer dead;
    private net.minecraft.world.entity.projectile.FishingHook lastHook;
    private Object inspectedScene;
    private final Map<Item,Integer> fishItems=new HashMap<>(),fishBefore=new HashMap<>();
    private Object field(Object target,String name) throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    SurvivalCheck() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((ItemFishedEvent e)->{
            if(e.getEntity().getGameProfile().getName().equals("NaiwaActor")) {
                fishEvents++;fishLoot+=e.getDrops().stream().mapToInt(ItemStack::getCount).sum();fishDamage+=e.getRodDamage();
                if(e.getEntity() instanceof ServerPlayer p) for(ItemStack drop:e.getDrops()) {
                    fishBefore.putIfAbsent(drop.getItem(),heldAndDropped(p,drop.getItem()));fishItems.merge(drop.getItem(),drop.getCount(),Integer::sum);
                }
            }
        });
    }
    private void require(boolean valid,String reason) {MultiplayerCheck.require(valid,"生存 stage="+phase+": "+reason);}
    private boolean ack(String role) {return Files.exists(MultiplayerCheck.ROOT.resolve(role+"-"+phase+".ok"));}
    private void stage(int next) throws Exception {
        phase=next;started=ticks;MultiplayerCheck.write("stage.txt",""+next);
        System.out.println("NAIWA_MULTIPLAYER survival stage="+next+" kind="+(kind<6?KINDS[kind]:"loop"));
    }
    private static void position(ServerPlayer p,double x,double z,float yaw) {
        p.stopRiding();p.stopUsingItem();p.setDeltaMovement(Vec3.ZERO);p.connection.teleport(x,81,z,yaw,0);
    }
    /** 阶段间只缩短等待以隔离场景；实际开始后不修改演出时钟，且核对真实五至十分钟冷却。 */
    private void fixture(ServerPlayer a,ServerPlayer v,boolean abort) throws Exception {
        var level=a.serverLevel();NaiwaPranks.cancel(a);NaiwaPranks.cancel(v);frog=null;boat=null;
        for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,ARENA,e->!(e instanceof ServerPlayer))) e.discard();
        for(int x=-12;x<=12;x++) for(int z=-12;z<=12;z++) for(int y=78;y<=86;y++)
            level.setBlock(new BlockPos(x,y,z),(y<=80?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        position(a,.5,3,180);position(v,3,-1,90);
        a.getPersistentData().putLong("naiwa.prank_next",0);a.getPersistentData().putString("naiwa.prank_last","");
        v.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);
        mirrorCrouch=false;mirrorJump=false;doorGiggle=false;doorSerious=false;fishEvents=fishLoot=fishDamage=0;
        fishItems.clear();fishBefore.clear();
        switch(KINDS[kind]) {
            case "mirror" -> {
                for(int x=-4;x<=4;x++) for(int y=81;y<=84;y++) level.setBlock(new BlockPos(x,y,0),Blocks.GLASS.defaultBlockState(),2);
                position(v,3,-3,55);
                position(a,.5,3,0);
            }
            case "door" -> {
                var lower=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER);
                level.setBlock(ANCHOR,lower,2);level.setBlock(ANCHOR.above(),lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
            }
            case "tree" -> level.setBlock(ANCHOR,Blocks.OAK_LOG.defaultBlockState(),2);
            case "mine" -> {
                level.setBlock(ANCHOR,Blocks.STONE.defaultBlockState(),2);
                for(int x=-5;x<=5;x++) for(int z=-2;z<=6;z++) level.setBlock(new BlockPos(x,85,z),Blocks.STONE.defaultBlockState(),2);
                position(v,3,2,90);
            }
            case "fish","boat" -> {
                for(int x=-2;x<=2;x++) for(int z=-1;z<=3;z++) for(int y=79;y<=80;y++) level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),2);
                position(a,.5,6,180);position(v,4,2,90);
                if(KINDS[kind].equals("boat")) {
                    boat=EntityType.BOAT.create(level);boat.setPos(.5,80.65,2.5);boat.setYRot(180);level.addFreshEntity(boat);
                    position(a,.5,5,180);position(v,3,2.5,90);
                }
            }
        }
        stage(200+kind*100+(abort?10:0));
    }
    private int count(ServerPlayer a,Item item) {return a.getInventory().countItem(item);}
    private int heldAndDropped(ServerPlayer a,Item item) {
        return a.serverLevel().players().stream().mapToInt(p->p.getInventory().countItem(item)).sum()
                +a.serverLevel().getEntitiesOfClass(ItemEntity.class,ARENA).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();
    }
    private void spawnHostile(ServerPlayer a,boolean lethal) {
        hostile=EntityType.HUSK.create(a.level());hostile.setPos(.5,81,1.5);
        if(lethal) hostile.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(40);
        hostile.setTarget(a);a.serverLevel().addFreshEntity(hostile);
    }
    private void tick(ServerTickEvent.Post event) {
        if(quitting) return;ticks++;
        try {
            var server=event.getServer();var a=server.getPlayerList().getPlayerByName("NaiwaActor");var v=server.getPlayerList().getPlayerByName("NaiwaViewer");
            require(ticks-started<4300,"阶段等待超时");if(a==null||v==null) return;
            if(phase==0) {
                if(!Files.exists(MultiplayerCheck.ROOT.resolve("actor-connected.ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-connected.ok"))) return;
                require(server.getPlayerList().getPlayerCount()==2,"两个真实连接");
                server.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                server.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                server.overworld().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false,server);server.overworld().setDayTime(1000);
                for(int x=-32;x<=32;x++) for(int z=-32;z<=32;z++) server.overworld().setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);
                for(var p:List.of(a,v)) {p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getFoodData().setFoodLevel(20);}
                if(System.getProperty("naiwa.qa.focus")!=null) kind=List.of(KINDS).indexOf(System.getProperty("naiwa.qa.focus"));
                // 工具、空瓶和原版图腾是测试初始材料；所有奶蛙物品必须在后续流程中实际获得。
                a.getInventory().items.set(0,new ItemStack(Items.IRON_AXE));a.getInventory().items.set(1,new ItemStack(Items.SHIELD));
                a.getInventory().items.set(2,new ItemStack(Items.IRON_PICKAXE));a.getInventory().items.set(3,new ItemStack(Items.FISHING_ROD));
                a.getInventory().items.set(4,new ItemStack(Items.GLASS_BOTTLE,16));a.getInventory().items.set(5,new ItemStack(Items.TOTEM_OF_UNDYING));
                a.getInventory().items.set(8,new ItemStack(Items.APPLE,4));v.getInventory().items.set(0,new ItemStack(Items.IRON_AXE));
                fixture(a,v,false);return;
            }
            if(phase>=200&&phase<800) {
                if(kind==4&&a.fishing!=null) lastHook=a.fishing;
                if(kind==4&&lastHook!=null&&frog!=null&&(ticks-started)%10==0) {
                    Vec3 land=inspectedScene==null?NaiwaPranks.findSpot(a,lastHook.position(),false):(Vec3)field(inspectedScene,"land");String blocked="";
                    Vec3 takeoff=inspectedScene==null?null:(Vec3)field(inspectedScene,"takeoff");
                    if(land!=null) for(int i=0;i<=15;i++) {
                        double t=i/15.0;Vec3 point=(takeoff==null?frog.position():takeoff).lerp(land,t).add(0,Math.sin(t*Math.PI)*1.2,0);
                        if(!a.level().noCollision(frog,new AABB(point.x-.45,point.y,point.z-.45,point.x+.45,point.y+2,point.z+.45))) blocked+=i+":"+point+";";
                    }
                    MultiplayerCheck.write("fish-diagnostics.txt","hook="+lastHook.position()+" land="+land+" frog="+frog.position()+" alive="+frog.isAlive()+" hurt="+frog.hurtTime+" source="+frog.getLastDamageSource()+" air="+frog.getAirSupply()+" events="+fishEvents+" loot="+fishLoot+" damage="+fishDamage+" blocked="+blocked+"\n");
                    MultiplayerCheck.write("fish-diagnostics-"+phase+".txt",Files.readString(MultiplayerCheck.ROOT.resolve("fish-diagnostics.txt")));
                }
                if((ticks-started)%100==0) MultiplayerCheck.write("diagnostics.txt","phase="+phase+" player="+a.position()+" yaw="+a.getYRot()+" pick="+a.pick(5,1,false)+" busy="+NaiwaPranks.busy(a)+" next="+a.getPersistentData().getLong("naiwa.prank_next")+" now="+a.level().getGameTime()+"\n");
                int step=phase%100;boolean abort=step>=10;int sub=step%10;
                if(kind==4&&sub==0&&fishEvents==0){
                    require(!NaiwaPranks.busy(a)&&a.serverLevel().getEntitiesOfClass(NaiwaEntity.class,ARENA,NaiwaEntity::isTemporary).isEmpty(),"真实等待和咬钩动画期间不生成演员");
                    require(a.getPersistentData().getLong("naiwa.prank_next")==0,"收竿确认前不消耗间隔");
                }
                if(frog!=null && frog.isAlive()) {
                    if(kind==0) {mirrorCrouch|=frog.isShiftKeyDown();mirrorJump|=frog.getY()>81.3;}
                    if(kind==1) {doorGiggle|=frog.act().equals("door_giggle");doorSerious|=doorGiggle && frog.act().equals("door");}
                }
                if(!ack("actor")||!ack("viewer")||ticks-started<12) return;
                if(sub==0) {
                    if(kind==4&&abort){
                        require(fishEvents==0&&a.fishing==null&&!NaiwaPranks.busy(a),"提前空收竿不伪造渔获和演员");
                        MultiplayerCheck.write("fish-early-reel-result.txt","PASS no actor during wait/bite, early empty reel, cooldown unchanged\n");stage(phase+3);return;
                    }
                    if(!NaiwaPranks.busy(a)) return;
                    if(kind==4)require(fishEvents==1&&a.fishing==null,"真实有渔获收竿结束后才能出现演员");
                    frog=a.serverLevel().getEntitiesOfClass(NaiwaEntity.class,ARENA,NaiwaEntity::isTemporary).getFirst();
                    // 诊断只读取实际场景选中的岸点，不能用漂移后重新搜索的候选点代替。
                    var scenes=NaiwaPranks.class.getDeclaredField("SCENES");scenes.setAccessible(true);
                    inspectedScene=((Map<?,?>)scenes.get(null)).get(a.getUUID());
                    long remaining=a.getPersistentData().getLong("naiwa.prank_next")-a.level().getGameTime();
                    require(remaining>5900 && remaining<=12000,"实际启动后设置共享冷却");
                    stage(phase+1);
                } else if(sub==1) {
                    if(kind!=4)require(!frog.animation().equals("laugh_sequence"),"露馅动作前不能提前成功");
                    if(kind==5) {
                        require(boat.getPassengers().contains(a)&&boat.getPassengers().contains(frog)&&boat.getPassengers().size()==2,"真实空座保留驾驶者");
                        require(boat.position().subtract(new Vec3(.5,boat.getY(),2.5)).horizontalDistance()>.1,"客户端实际划船带着后座演员移动");
                    }
                    stage(phase+1);
                } else if(sub==2) {
                    if(!abort) require(frog.isAlive(),"成功前演员提前消失；查看 fish-diagnostics.txt 和原版伤害来源");
                    if(abort) {
                        if(NaiwaPranks.busy(a)) return;
                        require(!frog.isAlive(),"中止移除临时演员");
                        if(kind==0) require(a.level().getBlockState(ANCHOR).is(Blocks.GLASS),"转头中止不破坏玻璃");
                        if(kind==2) require(a.level().getBlockState(ANCHOR).is(Blocks.OAK_LOG),"赶走演员不改树干");
                        if(kind==3) require(a.level().getBlockState(ANCHOR).is(Blocks.STONE),"未挖完便切盾，中止不修改矿物");
                        if(kind==4) require(fishEvents==0 && a.fishing==null,"空收竿中止不伪造渔获");
                        if(kind==5) require(!boat.isAlive(),"真实破船中止");
                        stage(phase+1);
                    } else {
                        if(!frog.animation().equals("laugh_sequence")) return;
                        if(kind==0) require(mirrorCrouch&&mirrorJump,"镜像同步真实蹲下及跳跃");
                        if(kind==1) require(doorGiggle&&doorSerious,"关门憋笑、第一次重开严肃、第二次抓现行");
                        if(kind==2) {
                            int drops=a.serverLevel().getEntitiesOfClass(ItemEntity.class,ARENA).stream().filter(e->e.getItem().is(Items.OAK_LOG)).mapToInt(e->e.getItem().getCount()).sum();
                            require(count(a,Items.OAK_LOG)+drops==1,"原版砍树只掉落一份原木");
                        }
                        if(kind==3) require(a.level().getBlockState(ANCHOR).isAir()&&a.getInventory().items.get(2).getDamageValue()==1,"玩家实际挖掉一格，演员不额外挖矿");
                        if(kind==4) {
                            require(fishEvents==1&&fishLoot>0&&fishDamage==1 && a.getInventory().items.get(3).getDamageValue()==1,"自然咬钩和真实收竿只扣一次耐久");
                            for(var entry:fishItems.entrySet()) require(heldAndDropped(a,entry.getKey())==fishBefore.get(entry.getKey()),"抢书成功后本次渔获既不入背包也不掉落");
                            require(frog.getMainHandItem().is(Items.ENCHANTED_BOOK)&&frog.getMainHandItem().hasFoil(),"大笑时保留发光附魔书道具");
                            MultiplayerCheck.write("fish-catch-result.txt","PASS events="+fishEvents+" withheld loot="+fishItems+" delivered=0 enchantedBookProp=true rodDamage="+fishDamage+" takeoff="+field(inspectedScene,"takeoff")+"\n");
                        }
                        if(kind==5) require(a.onGround()&&!a.isPassenger()&&boat.isAlive(),"玩家实际下船靠岸");
                        stage(phase+1);
                    }
                } else if(sub==3&&!abort) {
                    if(NaiwaPranks.busy(a)) return;
                    require(!frog.isAlive(),"大笑收场清理临时演员");
                    if(kind==4)require(heldAndDropped(a,Items.ENCHANTED_BOOK)==0,"退场不留下附魔书道具");
                    if(kind>=1&&kind<=3) {harvests++;require(count(a,Naiwa.GAS.get())==harvests,"露馅后实际采集一瓶");}
                    if(kind==5) require(boat.isAlive()&&boat.getPassengers().isEmpty(),"演出后原船和空座保留");
                    stage(phase+1);
                } else if(sub==4) fixture(a,v,true);
                else if(sub==3&&abort) {
                    MultiplayerCheck.write(KINDS[kind]+"-result.txt","PASS: survival client trigger, observer synchronization, reveal/laugh, cleanup, cancellation; authoritative cooldown preserved.\n");
                    if(System.getProperty("naiwa.qa.focus")!=null) {MultiplayerCheck.write("server-result.txt","PASS focused prank: "+KINDS[kind]+"\n");stage(99);quitting=true;return;}
                    if(++kind<6) fixture(a,v,false);
                    else {
                        NaiwaPranks.cancel(a);a.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);
                        // 清理最后一轮水池，后续只走已收集物品的使用循环。
                        for(int x=-12;x<=12;x++) for(int z=-12;z<=12;z++) for(int y=78;y<=86;y++) a.serverLevel().setBlock(new BlockPos(x,y,z),(y<=80?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
                        position(a,.5,4,180);position(v,3,3,125);require(count(a,Naiwa.GAS.get())==3,"三个实际整蛊奖励进入后续循环");spawnHostile(a,false);stage(900);
                    }
                }
                return;
            }
            if(!ack("actor")||!ack("viewer")||ticks-started<12) return;
            switch(phase) {
                case 900 -> {if(!hostile.hasEffect(Naiwa.LAUGHING)) return;require(count(a,Naiwa.GAS.get())==2,"真实投掷消耗一瓶采集奖励");stage(901);}
                case 901 -> {hostile.discard();a.getFoodData().setFoodLevel(10);spawnHostile(a,false);hostile.setNoAi(true);health=hostile.getHealth();stage(902);}
                case 902 -> {if(!a.hasEffect(Naiwa.LAUGHING)) return;stage(903);}
                case 903 -> {require(hostile.getHealth()==health,"自然漏气期间真实攻击不能伤害怪物");require(count(a,Items.APPLE)==3 && a.getFoodData().getFoodLevel()>10,"大笑期间仍可完成原版进食");stage(904);}
                case 904 -> {if(a.hasEffect(Naiwa.LAUGHING)) return;hostile.discard();stage(905);}
                case 905 -> {require(count(a,Naiwa.TOTEM.get())==1&&count(a,Items.TOTEM_OF_UNDYING)==0&&count(a,Naiwa.GAS.get())==1&&count(a,Items.GLASS_BOTTLE)==14,"实际生存合成材料消耗和一个空瓶返还");stage(906);}
                case 906 -> {require(a.getOffhandItem().is(Naiwa.TOTEM.get()),"客户端背包操作把合成物放入副手");position(a,.5,3,180);position(v,3,0,45);spawnHostile(a,true);stage(907);}
                case 907 -> {if(!a.hasEffect(Naiwa.ASCENDING)) return;require(a.isAlive()&&a.getOffhandItem().isEmpty(),"怪物实际攻击触发保命并消耗一次图腾");hostile.discard();stage(908);}
                case 908 -> {if(a.hasEffect(Naiwa.ASCENDING)) return;require(!a.isNoGravity(),"自然战斗保命结束恢复重力");stage(909);}
                case 909 -> {a.setRespawnPosition(a.level().dimension(),new BlockPos(8,81,8),180,true,false);dead=a;spawnHostile(a,true);stage(910);}
                case 910 -> {if(a.isAlive()) return;require(a.getPersistentData().getBoolean("naiwa.respawn_mock"),"没有图腾的实际战斗死亡记录复活待办");hostile.discard();stage(911);}
                case 911 -> {if(a==dead||!a.isAlive()) return;require(NaiwaPranks.busy(a),"实际原版重生触发复活点嘲笑");position(v,11,8,90);stage(912);}
                case 912 -> {
                    require(count(a,Naiwa.TOTEM.get())==0&&!a.hasEffect(Naiwa.ASCENDING)&&!a.isNoGravity(),"死亡重生没有图腾复制或状态残留");
                    int dropped=a.serverLevel().getEntitiesOfClass(ItemEntity.class,ARENA).stream().filter(e->e.getItem().is(Naiwa.GAS.get())).mapToInt(e->e.getItem().getCount()).sum();
                    require(dropped==1,"剩余一瓶笑气按原版死亡掉落一次");
                    MultiplayerCheck.write("server-result.txt","PASS: six real client survival triggers, six reveal/laugh paths, six cancellations, stolen fishing catch and book prop, collection x3, actual throw, natural leak and eating/attack denial, real crafting/refund/equipment, actual mob lethal hit, flight recovery, actual combat death/respawn and drops.\n");stage(99);quitting=true;
                }
            }
        } catch(Throwable failure) {
            quitting=true;failure.printStackTrace();try{MultiplayerCheck.write("server-result.txt","FAIL stage="+phase+": "+failure);MultiplayerCheck.write("stage.txt","-1");}catch(Exception ignored){}
        }
    }
}
