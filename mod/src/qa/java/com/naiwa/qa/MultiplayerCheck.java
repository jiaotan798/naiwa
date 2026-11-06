package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 专用服负责夹具和权威断言，客户端通过真实网络交互；文件只作阶段屏障，不传送玩法数据。 */
@Mod("naiwa_qa")
public final class MultiplayerCheck {
    static final Path ROOT=Path.of(System.getProperty("naiwa.qa.root"));
    private int phase, ticks, started;
    private NaiwaEntity frog;
    private Husk target;
    private ServerPlayer departed, dead;
    private float health;
    private double viewerZ;
    private boolean quitting;
    public MultiplayerCheck() {
        if(Boolean.getBoolean("naiwa.qa.gasEffects")) {
            new GasEffectsCheck();
            if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)new GasEffectsClient();
            return;
        }
        if(Boolean.getBoolean("naiwa.qa.collection")) {
            new CollectionCheck();
            if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)new CollectionClient();
            return;
        }
        if(Boolean.getBoolean("naiwa.qa.ascension")) {
            new AscensionCheck();
            if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)new AscensionClient();
            return;
        }
        if(Boolean.getBoolean("naiwa.qa.scopeFit")) {
            if(Boolean.getBoolean("naiwa.qa.scopeLive"))new ScopeLiveCheck();else new ScopeFitCheck();
            if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)new ScopeFitClient();
            return;
        }
        if(Boolean.getBoolean("naiwa.qa.caveMining")) {
            new CaveMiningCheck();
            if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)new CaveMiningClient();
            return;
        }
        if(Boolean.getBoolean("naiwa.qa.extras")) {
            new ExtraCheck();
            if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)new ExtraClient();
            return;
        }
        if(Boolean.getBoolean("naiwa.qa.laughSync")) {
            new LaughSyncCheck();
            if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT) new LaughSyncClient();
            return;
        }
        if(Boolean.getBoolean("naiwa.qa.natural")) {
            new NaturalCheck();
            if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT) new NaturalClient();
            return;
        }
        if(Boolean.getBoolean("naiwa.qa.survival")) {
            new SurvivalCheck();
            if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT) new SurvivalClient();
            return;
        }
        NeoForge.EVENT_BUS.addListener(this::tick);
        if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT) new MultiplayerClient();
    }
    static void require(boolean ok,String message) {if (!ok) throw new AssertionError(message);}
    /** 同目录原子替换屏障文件，避免另一客户端读到截断后尚未写入的空内容。 */
    static void write(String name,String value) throws Exception {
        Path temporary=ROOT.resolve(name+".tmp-"+ProcessHandle.current().pid());
        Files.writeString(temporary,value);
        // Windows 上另一端瞬间读取屏障也会阻止重命名；只重试短暂共享冲突，仍原子替换，不退化成半截文件。
        for(int attempt=0;;attempt++)try {
            Files.move(temporary,ROOT.resolve(name),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);break;
        } catch(AccessDeniedException conflict) {if(attempt>=4)throw conflict;Thread.sleep(2L<<attempt);}
    }
    private boolean ack(String role) {return Files.exists(ROOT.resolve(role+"-"+phase+".ok"));}
    private void stage(int next) throws Exception {
        phase=next; started=ticks; write("stage.txt",Integer.toString(phase));
        System.out.println("NAIWA_MULTIPLAYER stage="+phase);
    }
    private static void reset(ServerPlayer p,double x,double z,float yaw) {
        NaiwaPranks.cancel(p); NaiwaGameplay.cleanup(p); p.stopUsingItem();p.getInventory().clearContent();
        p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getFoodData().setFoodLevel(20);
        p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);
        p.connection.teleport(x,81,z,yaw,0);p.setDeltaMovement(Vec3.ZERO);
    }
    private void damage(ServerPlayer p) {p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(target),3);}
    private void totem(ServerPlayer p) {
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Naiwa.TOTEM.get()));p.invulnerableTime=0;
        p.hurt(p.damageSources().generic(),1000);
        require(p.isAlive() && p.getOffhandItem().isEmpty() && p.hasEffect(Naiwa.ASCENDING),"真实致命伤害必须消耗一次图腾并保命");
    }
    private void tick(ServerTickEvent.Post event) {
        if (quitting) return;
        ticks++;
        try {
            MinecraftServer server=event.getServer();
            ServerPlayer a=server.getPlayerList().getPlayerByName("NaiwaActor"),v=server.getPlayerList().getPlayerByName("NaiwaViewer");
            require(ticks-started<3600,"阶段超时："+phase);
            if (phase==0) {
                if (a==null || v==null || !Files.exists(ROOT.resolve("actor-connected.ok")) || !Files.exists(ROOT.resolve("viewer-connected.ok"))) return;
                require(server.getPlayerList().getPlayerCount()==2,"必须是两个独立连接");
                var level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(1000);
                for(int x=-16;x<=16;x++) for(int z=-16;z<=16;z++) level.setBlockAndUpdate(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState());
                reset(a,.5,3,180);reset(v,2,2,145);
                frog=Naiwa.ENTITY.get().create(level);frog.setNoAi(true);frog.setPos(.5,81,.5);level.addFreshEntity(frog);
                a.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHIELD));stage(1);return;
            }
            if (phase==16 && a==null) {
                require(departed!=null && !departed.isNoGravity() && !departed.hasEffect(Naiwa.ASCENDING),"真实退出必须恢复重力和状态");
                stage(17);write("reconnect.txt","ready");return;
            }
            if (a==null || v==null) return;
            // 先等两个客户端完成本阶段；飞升的阶段推进额外跟随真实服务端效果，不人为冻结计时。
            if (phase==12 && (!ack("actor") || !ack("viewer") || a.getEffect(Naiwa.ASCENDING)==null || a.getEffect(Naiwa.ASCENDING).getAmplifier()!=2)) return;
            if (phase==13 && (!ack("actor") || !ack("viewer") || a.getEffect(Naiwa.ASCENDING)==null || a.getEffect(Naiwa.ASCENDING).getAmplifier()!=3)) return;
            if (phase==14 && (a.hasEffect(Naiwa.ASCENDING) || !ack("actor") || !ack("viewer"))) return;
            if (!ack("actor") || !ack("viewer") || ticks-started<20) return;
            switch(phase) {
                case 1 -> {
                    if (!a.getMainHandItem().is(Naiwa.SHIELD.get())) return;
                    frog.setPos(12,81,12);target=EntityType.HUSK.create(a.level());target.setNoAi(true);target.setPos(.5,81,1.5);a.serverLevel().addFreshEntity(target);
                    stage(2);
                }
                case 2 -> {if (!a.isBlocking()) return;damage(a);require(NaiwaShield.charge(a.getMainHandItem())==1,"第一次真实格挡");stage(3);}
                case 3 -> {damage(a);require(NaiwaShield.charge(a.getMainHandItem())==2,"第二次真实格挡");stage(4);}
                case 4 -> {v.connection.teleport(.5,81,0,0,0);viewerZ=v.getZ();damage(a);stage(5);}
                case 5 -> {
                    require(NaiwaShield.charge(a.getMainHandItem())==0 && v.getZ()<viewerZ-.1,"第三次格挡清零且真正击退另一个客户端");
                    target.discard();reset(a,.5,3,180);reset(v,2,2,145);frog.setPos(.5,81,.5);frog.play("laugh_sequence");
                    a.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE,2));v.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE,2));stage(6);
                }
                case 6 -> {
                    require(a.getInventory().countItem(Naiwa.GAS.get())+v.getInventory().countItem(Naiwa.GAS.get())==1,"两个真实采集包只交付一瓶");
                    require(a.getInventory().countItem(Items.GLASS_BOTTLE)+v.getInventory().countItem(Items.GLASS_BOTTLE)==3,"争抢只消耗一只空瓶");stage(7);
                }
                case 7 -> {
                    reset(a,.5,4,180);reset(v,3,3,135);frog.setPos(12,81,12);
                    a.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Naiwa.GAS.get(),2));
                    target=EntityType.HUSK.create(a.level());target.setPos(.5,81,1.5);target.setTarget(a);a.serverLevel().addFreshEntity(target);stage(8);
                }
                case 8 -> {
                    if (!target.hasEffect(Naiwa.LAUGHING)) return;
                    require(a.getMainHandItem().getCount()==1 && !target.isNoAi() && !a.hasEffect(Naiwa.LAUGHING),"真实投掷消耗一瓶，只控制怪物且不改 NoAI");
                    health=target.getHealth();stage(9);
                }
                case 9 -> {stage(91);}
                case 91 -> {
                    require(target.getHealth()<health && !target.hasEffect(Naiwa.LAUGHING),"客户端实际攻击解除怪物控制");
                    target.setNoAi(true);target.setPos(.5,81,2);reset(a,.5,4,180);reset(v,3,3,68);
                    a.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));health=target.getHealth();NaiwaGameplay.laugh(a,240);stage(10);
                }
                case 10 -> {require(target.getHealth()==health,"真实攻击包在玩家大笑时被阻止");v.setNoGravity(true);v.connection.teleport(300,100,300,0,0);stage(101);}
                case 101 -> {v.connection.teleport(3,81,3,68,0);v.setNoGravity(false);stage(102);}
                case 102 -> {if (a.hasEffect(Naiwa.LAUGHING)) return;stage(11);}
                case 11 -> {
                    target.discard();reset(a,.5,3,180);reset(v,3,0,45);totem(a);stage(12);
                }
                case 12 -> {require(Math.abs(a.getY()-87)<.05,"真实联网玩家上升六格");v.connection.teleport(3,81,0,45,-58);stage(13);}
                case 13 -> {stage(14);}
                case 14 -> {require(!a.isNoGravity(),"飞升结束恢复重力");v.connection.teleport(3,81,0,45,0);stage(15);}
                case 15 -> {totem(a);departed=a;stage(16);}
                case 17 -> {
                    require(!a.isNoGravity() && !a.hasEffect(Naiwa.ASCENDING),"重新连接不残留变身");
                    // 原版重登有 60 tick 伤害保护；不改保命逻辑，等正常保护结束再触发下一次致命伤。
                    if(a.tickCount<70) return;
                    reset(a,.5,3,180);totem(a);
                    var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);
                    // 使用明确的空气平台隔离随机地形；此阶段只检查换维度清理，不测试窒息或熔岩。
                    for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) for(int y=99;y<=104;y++)
                        nether.setBlockAndUpdate(new BlockPos(x,y,z),(y==99?Blocks.STONE:Blocks.AIR).defaultBlockState());
                    a.teleportTo(nether,.5,100,.5,Set.of(),180,0);
                    require(!a.isNoGravity() && !a.hasEffect(Naiwa.ASCENDING),"实际换维度清理飞升");stage(18);
                }
                case 18 -> {
                    a.teleportTo(server.overworld(),.5,81,3,Set.of(),180,0);NaiwaPranks.cancel(a);frog.setPos(12,81,12);
                    stage(181);
                }
                case 181 -> {
                    // 原版在跨维度确认前拒绝所有伤害，必须等真实客户端完成事务，不能直接清除保护字段。
                    if(a.isChangingDimension()) return;
                    a.setRespawnPosition(server.overworld().dimension(),new BlockPos(0,81,3),180,true,false);
                    a.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);dead=a;a.invulnerableTime=0;a.hurt(a.damageSources().fellOutOfWorld(),1000);
                    require(!a.isAlive(),"原版维度保护结束后真实死亡");stage(19);
                }
                case 19 -> {
                    if (a==dead || !a.isAlive()) return;
                    require(NaiwaPranks.busy(a),"原版重生新玩家实例触发复活点嘲笑");stage(20);
                }
                case 20 -> {
                    require(a.isAlive() && !a.hasEffect(Naiwa.ASCENDING) && !a.isNoGravity(),"死亡重生没有飞升残留");
                    write("server-result.txt","PASS: production JAR, two real players; conversion, three blocked hits and remote knockback, atomic competing collection, actual gas projectile and damage release, player attack denial, six-block flight, logout/reconnect, dimension change, real death/respawn.\n");
                    stage(99);quitting=true;
                }
            }
        } catch(Throwable failure) {
            quitting=true;failure.printStackTrace();try {write("server-result.txt","FAIL stage="+phase+": "+failure);write("stage.txt","-1");}catch(Exception ignored) {}
        }
    }
}
