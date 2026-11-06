package com.naiwa;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 新玩法使用真实注册、碰撞、伤害和配方；假玩家复用盾牌测试的网络连接夹具。 */
@GameTestHolder("naiwa")
@PrefixGameTestTemplate(false)
public final class NaiwaGameplayTests {
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p=NaiwaShieldTests.player(h);
        BlockPos floor=BlockPos.containing(p.position()).below();
        for(int x=-8;x<=8;x++) for(int z=-8;z<=12;z++) h.getLevel().setBlock(floor.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
        p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);
        // 嵌入式连接没有客户端移动包；显式执行普通玩家更新，使状态效果按真实游戏节奏到期。
        h.onEachTick(p::doTick);
        return p;
    }
    /** 五至六格站位需要完整留在 16 格测试结构内，不能依赖夹具边界外的净空。 */
    private static ServerPlayer miningPlayer(GameTestHelper h) {
        ServerPlayer p=player(h);p.setPos(h.absoluteVec(new Vec3(8.5,3,8.5)));
        BlockPos floor=p.blockPosition().below();
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)h.getLevel().setBlock(floor.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
        return p;
    }
    private static void done(GameTestHelper h,ServerPlayer p,Runnable checks) {
        try {checks.run();h.succeed();} finally {NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);NaiwaShieldTests.remove(p);}
    }
    private static NaiwaEntity actor(ServerPlayer p) {
        return p.level().getEntitiesOfClass(NaiwaEntity.class,p.getBoundingBox().inflate(12),NaiwaEntity::isAlive).getFirst();
    }
    private static void face(ServerPlayer p,Vec3 point) {
        Vec3 delta=point.subtract(p.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));p.setYRot(yaw);p.setYHeadRot(yaw);
        p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())));
    }

    /** 命中槽位只作提示，直接改槽和原地改栈都须在同次查询立即生效。 */
    @GameTest(template="empty",timeoutTicks=30)
    public static void gasSlotHintMatchesLiveInventory(GameTestHelper h) {
        var p=player(h);
        try {
            for(int i=0;i<120;i++) {
                int slot=new int[]{0,12,35,net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND}[i%4];
                if(i%3==0)p.getInventory().setItem(slot,new ItemStack(Naiwa.GAS.get()));
                else if(i%3==1)p.getInventory().getItem(slot).setCount(0);
                else p.getInventory().setItem(slot,ItemStack.EMPTY);
                boolean expected=p.getInventory().items.stream().anyMatch(stack->stack.is(Naiwa.GAS.get()))||p.getOffhandItem().is(Naiwa.GAS.get());
                h.assertTrue(NaiwaGameplay.carrying(p)==expected,"携带命中缓存必须与实时背包一致："+i);
            }
            h.succeed();
        } finally {NaiwaGameplay.cleanup(p);NaiwaShieldTests.remove(p);}
    }

    /** 合并只减少空间查询，仍按每个玩家原范围清除目标，不能保护附近的无关玩家。 */
    @GameTest(template="empty",timeoutTicks=30)
    public static void groupedMobSweepKeepsOriginalRange(GameTestHelper h) {
        var first=player(h);var second=player(h);var other=player(h);second.setPos(first.position().add(3,0,0));
        var a=h.spawn(EntityType.HUSK,4,3,4);var b=h.spawn(EntityType.HUSK,5,3,4);var c=h.spawn(EntityType.PIGLIN,6,3,4);var unrelated=h.spawn(EntityType.HUSK,7,3,4);
        a.setNoAi(true);b.setNoAi(true);c.setNoAi(true);unrelated.setNoAi(true);
        try {
            // 猪灵 getTarget 本身来自 Brain，不能用它伪造两个互不相干的目标；另设尸壳验证无关目标保留。
            a.setTarget(first);b.setTarget(second);unrelated.setTarget(other);c.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,first);
            NaiwaGameplay.clearMobTargets(List.of(first,second));
            h.assertTrue(a.getTarget()==null&&b.getTarget()==null,"相近玩家共用查询后分别解除追击");
            h.assertTrue(unrelated.getTarget()==other&&!c.getBrain().isMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,first),"清理 Brain 攻击记忆时保留无关生物目标");
            a.setTarget(first);first.setPos(first.position().add(200,0,0));
            NaiwaGameplay.clearMobTargets(List.of(first,second));
            h.assertTrue(a.getTarget()==first,"远距离回退不扩大原保护范围");h.succeed();
        } finally {
            for(var p:List.of(first,second,other)) {NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);NaiwaShieldTests.remove(p);}
            a.discard();b.discard();c.discard();unrelated.discard();
        }
    }

    /** 连续受阻时只复用几何，原地拆墙后同次重试必须成功，不增加出场等待。 */
    @GameTest(template="empty",timeoutTicks=30)
    public static void mockGeometryReuseStillChecksChangedBlocks(GameTestHelper h) {
        var p=miningPlayer(h);p.setYRot(0);
        BlockPos base=p.blockPosition();
        try {
            for(int x=-1;x<=1;x++)for(int z=2;z<=5;z++)for(int y=-1;y<=3;y++)h.getLevel().setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(!NaiwaPranks.mock(p)&&!NaiwaPranks.mock(p),"同一位置受阻可连续重试");
            for(int x=-1;x<=1;x++)for(int z=2;z<=5;z++)for(int y=0;y<=3;y++)h.getLevel().setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            h.assertTrue(NaiwaPranks.mock(p),"原地移除障碍后立即出场，不复用失败结果");h.succeed();
        } finally {NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);NaiwaShieldTests.remove(p);}
    }

    /** 归属提示不能替代实例身份；取消一名玩家、重复清理及同玩家换场不能影响其他演员。 */
    @GameTest(template="empty",timeoutTicks=30)
    public static void actorLookupChecksCurrentSceneInstance(GameTestHelper h) {
        ServerPlayer first=player(h),second=player(h);second.setPos(first.position().add(6,0,0));
        try {
            var ordinary=Naiwa.ENTITY.get().create(h.getLevel());
            h.assertTrue(!NaiwaPranks.isActor(ordinary),"普通奶蛙没有场景身份");
            h.assertTrue(NaiwaPranks.mock(first),"第一名玩家建立嘲笑场景");var old=actor(first);
            h.assertTrue(NaiwaPranks.isActor(old),"不走 begin 的嘲笑场景也有演员身份");
            h.assertTrue(NaiwaPranks.mock(second),"第二名玩家建立独立场景");
            var other=totemActors(second).stream().filter(f->second.getUUID().equals(f.prankOwner)).findFirst().orElseThrow();
            ordinary.setUUID(old.getUUID());ordinary.prankOwner=first.getUUID();
            h.assertTrue(!NaiwaPranks.isActor(ordinary),"相同实体 UUID 和归属提示仍不能冒充当前实例");
            old.discard();
            h.assertTrue(NaiwaPranks.isActor(old),"实际场景清理前仍按场景实例识别，不提前按存活状态改规则");
            NaiwaPranks.cancel(first);NaiwaPranks.cancel(first);
            h.assertTrue(!NaiwaPranks.isActor(old)&&NaiwaPranks.isActor(other),"重复取消只释放本玩家身份");
            h.assertTrue(NaiwaPranks.mock(first),"同玩家可以建立下一场嘲笑");
            var current=totemActors(first).stream().filter(f->first.getUUID().equals(f.prankOwner)).findFirst().orElseThrow();
            h.assertTrue(NaiwaPranks.isActor(current)&&!NaiwaPranks.isActor(old),"旧演员提示不能指向新场景身份");
            NaiwaPranks.cancel(first);NaiwaPranks.cancel(second);
            h.assertTrue(!NaiwaPranks.isActor(current)&&!NaiwaPranks.isActor(other),"全部清理后无身份残留");
            h.succeed();
        } finally {
            NaiwaPranks.cancel(first);NaiwaPranks.cancel(second);NaiwaGameplay.cleanup(first);NaiwaGameplay.cleanup(second);
            NaiwaShieldTests.remove(first);NaiwaShieldTests.remove(second);
        }
    }

    /** 活动诱饵与同 UUID 的加载副本必须区分，取消后仍由既有结算入口恰好返还一次。 */
    @GameTest(template="empty",timeoutTicks=30)
    public static void decoyLookupKeepsRefundAndReloadIdentity(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);
        try {
            var bait=mineDiamond(h,p);
            h.assertTrue(NaiwaPranks.isDecoyActive(bait),"真实钻石场景登记诱饵");
            var tag=new net.minecraft.nbt.CompoundTag();bait.saveWithoutId(tag);
            var loaded=Naiwa.DECOY.get().create(h.getLevel());loaded.load(tag);
            h.assertTrue(loaded.prankOwner==null&&!NaiwaPranks.isDecoyActive(loaded),"重载不恢复归属或活动场景");
            loaded.prankOwner=p.getUUID();
            h.assertTrue(!NaiwaPranks.isDecoyActive(loaded),"同实体 UUID 加相同提示也不能冒充活动诱饵");
            NaiwaPranks.cancel(p);NaiwaPranks.cancel(p);bait.finishAt(bait.position());
            h.assertTrue(!NaiwaPranks.isDecoyActive(bait)&&looseDiamonds(p)==1,"取消和重复清理只交还一颗真实钻石");
            h.assertTrue(NaiwaPranks.mock(p),"随后可开始无诱饵场景");
            h.assertTrue(!NaiwaPranks.isDecoyActive(bait),"旧诱饵不能借新场景保留身份");
            h.succeed();
        } finally {NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);NaiwaShieldTests.remove(p);}
    }

    private static List<NaiwaEntity> totemActors(ServerPlayer p) {
        return p.level().getEntitiesOfClass(NaiwaEntity.class,p.getBoundingBox().inflate(12),f->f.isAlive()&&f.isTemporary());
    }

    /** 两格深水池共用原版浮漂；单元测试直接投递收竿事件，真实咬钩和掉落另由客户端验收覆盖。 */
    private static net.minecraft.world.entity.projectile.FishingHook fishingHook(GameTestHelper h,ServerPlayer p) {
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.FISHING_ROD));
        Vec3 water=p.position().add(0,0,4);BlockPos pool=BlockPos.containing(water).below();
        for(int x=-3;x<=3;x++)for(int z=-2;z<=3;z++)h.getLevel().setBlock(pool.offset(x,-2,z),Blocks.STONE.defaultBlockState(),2);
        for(int x=-2;x<=2;x++)for(int z=-1;z<=2;z++)for(int y=-1;y<=0;y++)h.getLevel().setBlock(pool.offset(x,y,z),Blocks.WATER.defaultBlockState(),2);
        var hook=new net.minecraft.world.entity.projectile.FishingHook(p,p.level(),0,0);hook.setPos(water.add(0,-.1,0));hook.setDeltaMovement(Vec3.ZERO);h.getLevel().addFreshEntity(hook);
        p.getPersistentData().putLong("naiwa.prank_next",0);return hook;
    }

    private static void noFishingActor(GameTestHelper h,ServerPlayer p,String reason) {
        h.assertTrue(!NaiwaPranks.busy(p)&&p.level().getEntitiesOfClass(NaiwaEntity.class,p.getBoundingBox().inflate(12),NaiwaEntity::isTemporary).isEmpty(),reason);
        h.assertTrue(p.getPersistentData().getLong("naiwa.prank_next")==0,"没有实际出场时不消耗共享间隔");
    }

    @GameTest(template="empty",timeoutTicks=50)
    public static void collectionIsOneAtomicBottlePerLaugh(GameTestHelper h) {
        ServerPlayer p=player(h); NaiwaEntity frog=h.spawn(Naiwa.ENTITY.get(),3,3,5);frog.setNoAi(true);frog.setNoGravity(true);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE,2));frog.play("laugh_sequence");
        h.assertTrue(!frog.collect(p,InteractionHand.MAIN_HAND),"入场期间不能采集");
        h.runAfterDelay(15,()->done(h,p,()->{
            h.assertTrue(p.getInventory().countItem(Naiwa.GAS.get())==1,"进入大笑自动进行一次交易");
            h.assertTrue(p.getMainHandItem().getCount()==1 && p.getInventory().countItem(Naiwa.GAS.get())==1,"一瓶换一瓶");
            h.assertTrue(!frog.collect(p,InteractionHand.MAIN_HAND) && p.getMainHandItem().getCount()==1,"同轮第二次交互不能吞瓶或复制笑气");
        }));
    }

    @GameTest(template="empty",timeoutTicks=50)
    public static void fullInventoryAndGlassDoNotConsumeBottles(GameTestHelper h) {
        ServerPlayer p=player(h); NaiwaEntity frog=h.spawn(Naiwa.ENTITY.get(),3,3,5);frog.setNoAi(true);frog.setNoGravity(true);
        for(int i=0;i<p.getInventory().items.size();i++) p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE,2));frog.play("laugh_sequence");
        h.runAfterDelay(15,()->done(h,p,()->{
            h.assertTrue(!frog.collect(p,InteractionHand.MAIN_HAND) && p.getMainHandItem().getCount()==2,"没有交付空间不消耗瓶子");
            p.getInventory().items.set(1,ItemStack.EMPTY);
            BlockPos wall=BlockPos.containing(p.position().add(0,1,1));h.getLevel().setBlock(wall,Blocks.GLASS.defaultBlockState(),3);
            h.assertTrue(!frog.collect(p,InteractionHand.MAIN_HAND),"玻璃保持真实交互遮挡");
        }));
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void autoGasUsesBackpackWithoutSelectingBottle(GameTestHelper h) {
        ServerPlayer p=player(h);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        p.getInventory().setItem(22,new ItemStack(Items.GLASS_BOTTLE,3));var frog=h.spawn(Naiwa.ENTITY.get(),3,3,5);frog.setNoAi(true);frog.setNoGravity(true);frog.play("laugh_sequence");
        h.runAfterDelay(35,()->done(h,p,()->{
            h.assertTrue(p.getMainHandItem().is(Items.IRON_PICKAXE),"背包自动采集不切换或替换手中工具");
            h.assertTrue(p.getInventory().getItem(22).getCount()==2&&p.getInventory().countItem(Naiwa.GAS.get())==1,"未选中的背包空瓶只兑换一次");
            h.assertTrue(!frog.collectAutomatically(p)&&p.getInventory().countItem(Naiwa.GAS.get())==1,"重复 tick 不复制奖励");
        }));
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void autoGasPrioritizesSingleBottleInFullInventory(GameTestHelper h) {
        ServerPlayer p=player(h);for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        p.getInventory().setItem(22,new ItemStack(Items.GLASS_BOTTLE));p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.GLASS_BOTTLE,8));
        var frog=h.spawn(Naiwa.ENTITY.get(),3,3,5);frog.setNoAi(true);frog.setNoGravity(true);frog.play("laugh_sequence");
        h.runAfterDelay(20,()->done(h,p,()->{
            h.assertTrue(p.getInventory().getItem(22).is(Naiwa.GAS.get())&&p.getOffhandItem().getCount()==8,"满背包优先原位替换单瓶，不先错误尝试整组");
        }));
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void autoGasUsesOffhandAndExistingGasStack(GameTestHelper h) {
        ServerPlayer p=player(h);for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        p.getInventory().setItem(22,new ItemStack(Naiwa.GAS.get(),15));p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.GLASS_BOTTLE,8));
        var frog=h.spawn(Naiwa.ENTITY.get(),3,3,5);frog.setNoAi(true);frog.setNoGravity(true);frog.play("laugh_sequence");
        h.runAfterDelay(20,()->done(h,p,()->{
            h.assertTrue(p.getOffhandItem().getCount()==7&&p.getInventory().countItem(Naiwa.GAS.get())==16,"无空格时可消耗副手一瓶并合并到未满笑气堆叠");
        }));
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void autoGasCanMergeIntoOffhandWithFullInventory(GameTestHelper h) {
        ServerPlayer p=player(h);for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        p.getInventory().setItem(22,new ItemStack(Items.GLASS_BOTTLE,3));p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Naiwa.GAS.get(),15));
        var frog=h.spawn(Naiwa.ENTITY.get(),3,3,5);frog.setNoAi(true);frog.setNoGravity(true);frog.play("laugh_sequence");
        h.runAfterDelay(20,()->done(h,p,()->h.assertTrue(p.getOffhandItem().getCount()==16&&p.getInventory().getItem(22).getCount()==2,"副手笑气可合并时不误判满包")));
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void autoGasDoesNotConsumeWhenFullAndCanRetry(GameTestHelper h) {
        ServerPlayer p=player(h);for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        p.getInventory().setItem(22,new ItemStack(Items.GLASS_BOTTLE,3));var frog=h.spawn(Naiwa.ENTITY.get(),3,3,5);frog.setNoAi(true);frog.setNoGravity(true);frog.play("laugh_sequence");
        h.runAfterDelay(20,()->{h.assertTrue(p.getInventory().getItem(22).getCount()==3&&p.getInventory().countItem(Naiwa.GAS.get())==0,"无空间绝不吞瓶");p.getInventory().setItem(23,ItemStack.EMPTY);});
        h.runAfterDelay(35,()->done(h,p,()->h.assertTrue(p.getInventory().getItem(22).getCount()==2&&p.getInventory().countItem(Naiwa.GAS.get())==1,"当前大笑期间腾出空间后可自动重试")));
    }

    @GameTest(template="empty",timeoutTicks=50)
    public static void autoSceneGasBelongsToOwnerAtSixBlocks(GameTestHelper h) {
        ServerPlayer p=player(h),other=NaiwaShieldTests.player(h);p.getInventory().setItem(22,new ItemStack(Items.GLASS_BOTTLE,3));
        h.assertTrue(NaiwaPranks.mock(p),"创建固定三格所属嘲笑场景");
        var frog=totemActors(p).getFirst();p.setPos(frog.position().add(0,0,-6));other.setPos(frog.position().add(1,0,0));other.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE));
        h.runAfterDelay(25,()->{
            try {h.assertTrue(frog.distanceToSqr(p)>9&&p.getInventory().countItem(Naiwa.GAS.get())==1,"不用赶到三格内也给本轮触发者自动结算");
                h.assertTrue(other.getMainHandItem().is(Items.GLASS_BOTTLE)&&other.getInventory().countItem(Naiwa.GAS.get())==0,"更近的路人不会自动抢走所属奖励");h.succeed();}
            finally {NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);NaiwaShieldTests.remove(p);NaiwaShieldTests.remove(other);}
        });
    }

    /** 新模型晚于旧版 170 tick 收场，演员、采气窗口与最终清理必须一并延长。 */
    @GameTest(template="empty",timeoutTicks=220)
    public static void longerLaughterKeepsActorAndLateCollection(GameTestHelper h) {
        ServerPlayer p=player(h);h.assertTrue(NaiwaPranks.mock(p),"安全位置创建所属嘲笑场景");
        var frog=totemActors(p).getFirst();
        h.runAfterDelay(172,()->{
            h.assertTrue(frog.isAlive()&&frog.clip(0).name().equals("laugh"),"经过旧版收场时刻仍保留演员及大笑末段");
            p.getInventory().setItem(22,new ItemStack(Items.GLASS_BOTTLE));
        });
        h.runAfterDelay(182,()->{
            h.assertTrue(p.getInventory().countItem(Naiwa.GAS.get())==1,"延长的大笑窗口中补瓶仍只自动兑换一次");
            h.assertTrue(frog.isAlive()&&frog.clip(0).name().equals("laugh_exit"),"完整恢复片段结束前不得清理演员");
        });
        h.runAfterDelay(200,()->done(h,p,()->h.assertTrue(!frog.isAlive()&&!NaiwaPranks.busy(p),"新模型完整演出结束后正常清理场景")));
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void residentAutoGasKeepsGlassAndPersistentCooldown(GameTestHelper h) {
        ServerPlayer p=player(h);p.getInventory().setItem(22,new ItemStack(Items.GLASS_BOTTLE,3));var frog=h.spawn(Naiwa.ENTITY.get(),3,3,5);frog.setNoAi(true);frog.setNoGravity(true);
        BlockPos wall=BlockPos.containing(p.position().add(0,1,1));h.getLevel().setBlock(wall,Blocks.GLASS.defaultBlockState(),3);frog.play("laugh_sequence");
        NaiwaEntity[] loaded={null};
        h.runAfterDelay(20,()->{h.assertTrue(p.getInventory().countItem(Naiwa.GAS.get())==0,"无所属常驻本体仍遵守玻璃遮挡");h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),3);});
        h.runAfterDelay(35,()->{
            h.assertTrue(p.getInventory().countItem(Naiwa.GAS.get())==1,"移除遮挡后自动采集");var saved=new net.minecraft.nbt.CompoundTag();frog.saveWithoutId(saved);frog.play("laugh_sequence");
            loaded[0]=Naiwa.ENTITY.get().create(h.getLevel());loaded[0].load(saved);loaded[0].play("laugh_sequence");
        });
        h.runAfterDelay(60,()->done(h,p,()->{
            h.assertTrue(loaded[0].animationSeconds(0)>.6&&!loaded[0].collectAutomatically(p),"真实时间经过入场后，重载仍不能绕过五分钟冷却");
            h.assertTrue(p.getInventory().countItem(Naiwa.GAS.get())==1&&p.getInventory().getItem(22).getCount()==2,"重播与重载都不重复兑换");
        }));
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void shapedLayoutsReturnOneEmptyBottle(GameTestHelper h) {
        // 穷举 2×2 与 3×3 的两材料摆法，覆盖有序布局的平移和水平镜像及原版余料结算。
        for(int size:new int[]{2,3})for(int gas=0;gas<size*size;gas++)for(int totem=0;totem<size*size;totem++)if(gas!=totem) {
            var stacks=new java.util.ArrayList<ItemStack>(java.util.Collections.nCopies(size*size,ItemStack.EMPTY));
            stacks.set(gas,new ItemStack(Naiwa.GAS.get()));stacks.set(totem,new ItemStack(Items.TOTEM_OF_UNDYING));
            var input=CraftingInput.of(size,size,stacks);
            var recipe=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value();
            h.assertTrue(recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe,"使用有序布局");
            ItemStack result=recipe.assemble(input,h.getLevel().registryAccess());
            h.assertTrue(result.is(Naiwa.TOTEM.get())&&result.getCount()==1,"产出一个图腾");
            h.assertTrue(recipe.getRemainingItems(input).stream().mapToInt(s->s.is(Items.GLASS_BOTTLE)?s.getCount():0).sum()==1,"每份恰返一个空瓶");
            h.assertTrue(input.items().stream().filter(s->!s.isEmpty()).allMatch(s->s.getCount()==1),"预览不修改输入");
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void gasPausesAiAndRealDamageEndsIt(GameTestHelper h) {
        ServerPlayer p=player(h); var mob=h.spawn(EntityType.HUSK,3,3,6);mob.setNoGravity(true);mob.setTarget(p);
        NaiwaGameplay.laugh(mob,50);
        h.runAfterDelay(10,()->{
            h.assertTrue(mob.hasEffect(Naiwa.LAUGHING) && !mob.isNoAi(),"暂停 AI 不污染持久化 NoAI");
            float health=p.getHealth();p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(mob),2);
            h.assertTrue(p.getHealth()==health,"大笑怪物不能主动攻击");
            mob.invulnerableTime=0;mob.hurt(mob.damageSources().generic(),1);
            h.assertTrue(!mob.hasEffect(Naiwa.LAUGHING),"真实受伤后提前恢复");
        });
        h.runAfterDelay(12,()->done(h,p,()->h.assertTrue(mob.getTarget()==p,"未删除原有目标，后续可恢复追击")));
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void playerLaughKeepsMovementAndExpires(GameTestHelper h) {
        ServerPlayer p=player(h);p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Naiwa.GAS.get(),5));
        h.assertTrue(NaiwaGameplay.carrying(p),"副手纳入携带");NaiwaGameplay.laugh(p,30);
        double speed=p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        h.runAfterDelay(5,()->{
            h.assertTrue(p.hasEffect(Naiwa.LAUGHING) && p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)==speed,"副作用不降低移动速度");
            var event=new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(p,h.spawn(EntityType.HUSK,5,3,5));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);h.assertTrue(event.isCanceled(),"主动攻击被暂停");
            p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);h.assertTrue(!NaiwaGameplay.carrying(p) && p.hasEffect(Naiwa.LAUGHING),"移除最后一瓶不解除已开始动作");
        });
        h.runAfterDelay(40,()->done(h,p,()->h.assertTrue(!p.hasEffect(Naiwa.LAUGHING),"大笑正常到期恢复")));
    }

    @GameTest(template="empty",timeoutTicks=270)
    public static void realTotemPipelineConsumesOneAndCompletesFlight(GameTestHelper h) {
        ServerPlayer p=player(h);p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Naiwa.TOTEM.get()));
        double base=p.getY();p.invulnerableTime=0;p.hurt(p.damageSources().generic(),100);
        h.assertTrue(p.isAlive() && p.getOffhandItem().isEmpty() && p.hasEffect(Naiwa.ASCENDING),"真实伤害管线完成一次保命与消耗");
        h.assertTrue(!p.getPersistentData().getBoolean("naiwa.respawn_mock"),"图腾保命不能记录真实死亡");
        h.assertTrue(totemActors(p).isEmpty(),"保命及飞升起手不能提前生成嘲笑演员");
        h.assertTrue(p.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION).getDuration()==1200
                &&p.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION).getDuration()==600
                &&p.getEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE).getDuration()==1200,"生命恢复及防火各六十秒，伤害吸收三十秒");
        h.runAfterDelay(55,()->h.assertTrue(Math.abs(p.getY()-base-6)<.02,"以触发脚部高度升高六格"));
        h.runAfterDelay(65,()->p.setPos(p.position().add(0,1,0)));
        h.runAfterDelay(100,()->h.assertTrue(Math.abs(p.getY()-base-6)<.02,"悬停不累计升高"));
        h.runAfterDelay(165,()->h.assertTrue(p.hasEffect(Naiwa.ASCENDING)&&p.getEffect(Naiwa.ASCENDING).getAmplifier()==2,"悬停已延长到六秒，原流程结束前仍保持保护姿态"));
        h.runAfterDelay(240,()->done(h,p,()->{
            h.assertTrue(!p.hasEffect(Naiwa.ASCENDING) && Math.abs(p.getY()-base)<.05,"三秒缓降后恢复外观");
            h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION),"保留原版保命恢复效果");
            h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION)&&NaiwaGameplay.protectedFromMobs(p),"落地恢复后仍有伤害吸收和生物攻击保护余量");
        }));
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void flightHeightKeepsHorizontalControlAndRejectsStaleUpdates(GameTestHelper h) {
        ServerPlayer p=player(h);double speed=p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        try {
            NaiwaGameplay.startFlight(p);Vec3 before=p.position();p.setDeltaMovement(.1,.5,.2);
            NaiwaFlightHeight.apply(p,p.getY()+1);
            h.assertTrue(p.getX()==before.x&&p.getZ()==before.z&&Math.abs(p.getY()-before.y-1)<.001,"高度同步不覆盖玩家水平位置");
            h.assertTrue(p.getDeltaMovement().x==.1&&p.getDeltaMovement().y==0&&p.getDeltaMovement().z==.2,"高度同步保留横向速度");
            h.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)==speed,"飞升不再附加减速");
            double y=p.getY();NaiwaFlightHeight.apply(p,Double.NaN);NaiwaFlightHeight.apply(p,Double.POSITIVE_INFINITY);NaiwaFlightHeight.apply(p,y+100);
            h.assertTrue(p.getY()==y,"非法高度不移动玩家");NaiwaGameplay.cleanup(p);NaiwaFlightHeight.apply(p,y+1);
            h.assertTrue(p.getY()==y,"飞升结束后的陈旧高度包不能继续抬升");h.succeed();
        } finally {NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);NaiwaShieldTests.remove(p);}
    }

    @GameTest(template="empty",timeoutTicks=240)
    public static void ceilingStopsFlightWithoutPhasing(GameTestHelper h) {
        ServerPlayer p=player(h);double base=p.getY();BlockPos roof=BlockPos.containing(p.position()).above(3);
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) h.getLevel().setBlock(roof.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Naiwa.TOTEM.get()));p.invulnerableTime=0;p.hurt(p.damageSources().generic(),100);
        h.runAfterDelay(40,()->{
            h.assertTrue(p.getY()<base+1.21 && p.getEffect(Naiwa.ASCENDING).getAmplifier()==2,"碰顶提前进入悬停，不穿透方块");
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) h.getLevel().setBlock(roof.offset(x,0,z),Blocks.AIR.defaultBlockState(),2);
        });
        h.runAfterDelay(60,()->h.assertTrue(p.getY()<base+1.21,"移除障碍后不补升"));
        h.runAfterDelay(210,()->done(h,p,()->h.assertTrue(!p.hasEffect(Naiwa.ASCENDING),"受阻流程正常恢复")));
    }

    @GameTest(template="empty",timeoutTicks=270)
    public static void deepDropKeepsOneLandingProtection(GameTestHelper h) {
        ServerPlayer p=player(h);p.setPos(p.position().add(0,20,0));NaiwaGameplay.startFlight(p);
        h.runAfterDelay(230,()->{
            // 缓降到原始半空高度仍未落地，保护必须与模型状态分开。
            h.assertTrue(!p.hasEffect(Naiwa.ASCENDING) && p.getPersistentData().getBoolean("naiwa.safe_fall"),"超时恢复人形但保留免摔");
            h.assertTrue(NaiwaGameplay.waitingForTotemLanding(p)&&totemActors(p).isEmpty(),"深坑中模型结束仍须等待真正落地，不在半空或原触发点出场");
            var first=new net.neoforged.neoforge.event.entity.living.LivingFallEvent(p,30,1);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(first);h.assertTrue(first.isCanceled(),"首次落地免摔");
            var second=new net.neoforged.neoforge.event.entity.living.LivingFallEvent(p,30,1);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(second);h.assertTrue(!second.isCanceled(),"免摔不能覆盖之后的下落");
        });
        h.runAfterDelay(235,()->done(h,p,()->{}));
    }

    @GameTest(template="empty",timeoutTicks=280)
    public static void totemMockWaitsForLandingAndUsesCurrentFacing(GameTestHelper h) {
        ServerPlayer p=player(h);double base=p.getY();
        NaiwaEntity resident=h.spawn(Naiwa.ENTITY.get(),5,3,3);resident.setNoAi(true);resident.setNoGravity(true);
        ItemStack prop=new ItemStack(Items.DIAMOND);resident.setItemInHand(InteractionHand.MAIN_HAND,prop);Vec3 residentPosition=resident.position();
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Naiwa.TOTEM.get()));p.invulnerableTime=0;p.hurt(p.damageSources().generic(),100);
        h.assertTrue(totemActors(p).isEmpty(),"图腾刚触发不提前出场");
        boolean[] landed={false};h.onEachTick(()->{if(landed[0])p.setOnGround(true);});
        h.runAfterDelay(60,()->{
            h.assertTrue(totemActors(p).isEmpty()&&resident.animation().equals("auto"),"飞升中不生成或借用附近奶蛙");
            p.setPos(p.position().add(4,0,2));p.setYRot(90);p.setXRot(-70);
        });
        h.runAfterDelay(225,()->h.assertTrue(totemActors(p).isEmpty(),"恢复人形之前仍不提前大笑"));
        h.runAfterDelay(235,()->{
            h.assertTrue(!p.hasEffect(Naiwa.ASCENDING),"先完成飞升恢复");
            // 假客户端不发送落地包；模拟当前落点的 onGround，生产代码还须检查真实方块支撑。
            p.setPos(p.getX(),base,p.getZ());p.setYRot(90);p.setOnGround(true);landed[0]=true;
        });
        h.runAfterDelay(250,()->{
            h.assertTrue(totemActors(p).size()==1&&!NaiwaGameplay.waitingForTotemLanding(p),"落地后只消费一次待办并生成一个演员");
            NaiwaEntity frog=totemActors(p).getFirst();Vec3 delta=frog.position().subtract(p.position()).multiply(1,0,1);
            h.assertTrue(Math.abs(delta.length()-3)<.01&&delta.normalize().dot(Vec3.directionFromRotation(0,p.getYRot()))>.999,"使用落地后的当前位置和水平朝向，严格位于正前方");
            h.assertTrue(frog.animation().equals("laugh_sequence")&&Math.abs(net.minecraft.util.Mth.wrapDegrees(frog.getYRot()-p.getYRot()-180))<.01,"正面朝向玩家播放完整大笑");
            h.assertTrue(resident.position().distanceToSqr(residentPosition)<.001&&resident.getMainHandItem()==prop&&resident.animation().equals("auto"),"不挪用侧面的常驻奶蛙，也不改它的物品和动作");
        });
        h.runAfterDelay(265,()->done(h,p,()->{h.assertTrue(totemActors(p).size()==1&&totemActors(p).getFirst().animationSeconds(0)>=1,"持续站地不重复生成或重启大笑");resident.discard();}));
    }

    @GameTest(template="empty",timeoutTicks=290)
    public static void totemMockRetriesBlockedFrontWithoutSideFallback(GameTestHelper h) {
        ServerPlayer p=player(h);BlockPos feet=p.blockPosition();double base=p.getY();
        for(int x=-2;x<=2;x++)for(int z=1;z<=5;z++)for(int y=0;y<=4;y++)h.getLevel().setBlock(feet.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        NaiwaGameplay.startFlight(p);boolean[] landed={false};h.onEachTick(()->{if(landed[0])p.setOnGround(true);});
        h.runAfterDelay(235,()->{p.setPos(p.getX(),base,p.getZ());p.setYRot(0);p.setOnGround(true);landed[0]=true;});
        h.runAfterDelay(250,()->{
            h.assertTrue(totemActors(p).isEmpty()&&NaiwaGameplay.waitingForTotemLanding(p),"前方被挡时继续等候，不穿墙、不转到安全的侧后方");
            for(int x=-2;x<=2;x++)for(int z=1;z<=5;z++)for(int y=0;y<=4;y++)h.getLevel().setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        });
        h.runAfterDelay(265,()->done(h,p,()->{
            h.assertTrue(totemActors(p).size()==1&&!NaiwaGameplay.waitingForTotemLanding(p),"前方恢复安全后只出场一次");
            Vec3 delta=totemActors(p).getFirst().position().subtract(p.position());h.assertTrue(Math.abs(delta.x)<.01&&delta.z>2.49,"受阻重试仍使用正前方");
        }));
    }

    @GameTest(template="empty",timeoutTicks=75)
    public static void totemWaterDoesNotCountAsLandingAndCleanupCancelsPending(GameTestHelper h) {
        ServerPlayer p=player(h);BlockPos feet=p.blockPosition();
        h.getLevel().setBlock(feet,Blocks.WATER.defaultBlockState(),2);h.getLevel().setBlock(feet.above(),Blocks.WATER.defaultBlockState(),2);
        NaiwaGameplay.startFlight(p);h.onEachTick(()->p.setOnGround(true));
        h.runAfterDelay(20,()->{
            h.assertTrue(!p.hasEffect(Naiwa.ASCENDING)&&NaiwaGameplay.waitingForTotemLanding(p)&&totemActors(p).isEmpty(),"入水结束飞升后，即使 onGround 为真也不能提前出场");
            NaiwaGameplay.cleanup(p);
            h.assertTrue(!NaiwaGameplay.waitingForTotemLanding(p),"退出、换维度和中断共用清理入口取消旧落地待办");
            h.getLevel().setBlock(feet,Blocks.AIR.defaultBlockState(),2);h.getLevel().setBlock(feet.above(),Blocks.AIR.defaultBlockState(),2);
        });
        h.runAfterDelay(40,()->done(h,p,()->h.assertTrue(totemActors(p).isEmpty(),"清理之后落地不会补播旧图腾嘲笑")));
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void totemProtectionClearsMobTargetsAndBlocksOwnedDamage(GameTestHelper h) {
        ServerPlayer p=player(h),other=NaiwaShieldTests.player(h);other.setPos(p.position().add(4,0,4));
        boolean previousPvp=p.getServer().isPvpAllowed();
        var melee=h.spawn(EntityType.HUSK,5,3,5);melee.setNoAi(true);melee.setTarget(p);
        var shooter=h.spawn(EntityType.SKELETON,6,3,5);shooter.setNoAi(true);
        var thinker=h.spawn(EntityType.PIGLIN,7,3,5);thinker.setNoAi(true);thinker.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,p);
        try {
            // GameTestServer 的 PvP 开关独立于生成的 server.properties；仅本次同步调用临时开启并在 finally 恢复。
            p.getServer().setPvpAllowed(true);
            NaiwaGameplay.startFlight(p);
            h.assertTrue(NaiwaGameplay.protectedFromMobs(p)&&melee.getTarget()==null
                    &&!thinker.getBrain().isMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,p),"触发立即清除普通追击与 Brain 攻击记忆");
            melee.setTarget(p);h.assertTrue(melee.getTarget()==null,"保护期间不能重新选中玩家");
            var targetEvent=new net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent(thinker,p,
                    net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.LivingTargetType.BEHAVIOR_TARGET);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(targetEvent);h.assertTrue(targetEvent.getNewAboutToBeSetTarget()==null,"Brain 行为的目标变更同样被阻止");
            float health=p.getHealth();var arrow=EntityType.ARROW.create(p.level());arrow.setOwner(shooter);
            p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(melee),7);
            p.invulnerableTime=0;p.hurt(p.damageSources().arrow(arrow,shooter),7);
            p.invulnerableTime=0;p.hurt(p.damageSources().indirectMagic(arrow,shooter),7);
            p.invulnerableTime=0;p.hurt(p.damageSources().explosion(melee,melee),7);
            h.assertTrue(p.getHealth()==health,"已发出的近战、箭矢、间接魔法和生物爆炸均不扣血");
            melee.setTarget(other);h.assertTrue(melee.getTarget()==other,"不阻止生物选择未受保护的其他玩家");
            p.invulnerableTime=0;p.hurt(p.damageSources().playerAttack(other),2);h.assertTrue(p.getHealth()<health,"玩家伤害仍走原规则，不改全局无敌状态");
            health=p.getHealth();p.invulnerableTime=0;p.hurt(p.damageSources().generic(),2);h.assertTrue(p.getHealth()<health,"普通环境伤害仍走原规则");h.succeed();
        } finally {
            p.getServer().setPvpAllowed(previousPvp);
            NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);NaiwaShieldTests.remove(p);NaiwaShieldTests.remove(other);melee.discard();shooter.discard();thinker.discard();
        }
    }

    @GameTest(template="empty",timeoutTicks=650)
    public static void totemMobProtectionLastsThirtySecondsThenExpires(GameTestHelper h) {
        ServerPlayer p=player(h);Vec3 ground=p.position();var mob=h.spawn(EntityType.HUSK,5,3,5);mob.setNoAi(true);NaiwaGameplay.startFlight(p);
        boolean[] landed={false};h.onEachTick(()->{if(landed[0])p.setOnGround(true);});
        h.runAfterDelay(240,()->{h.assertTrue(!p.hasEffect(Naiwa.ASCENDING),"延长的飞升流程已正常结束");p.setPos(ground);p.setOnGround(true);landed[0]=true;});
        h.runAfterDelay(300,()->h.assertTrue(NaiwaGameplay.protectedFromMobs(p),"飞升及落地后保留攻击保护余量"));
        h.runAfterDelay(590,()->{
            float health=p.getHealth();p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(mob),4);
            h.assertTrue(NaiwaGameplay.protectedFromMobs(p)&&p.getHealth()==health,"接近三十秒仍阻止攻击，不会提前到期");
        });
        h.runAfterDelay(610,()->done(h,p,()->{
            h.assertTrue(!NaiwaGameplay.protectedFromMobs(p),"三十秒结束后不会留下永久免疫");mob.setTarget(p);
            h.assertTrue(mob.getTarget()==p,"到期后允许生物重新选中玩家");float health=p.getHealth();p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(mob),4);
            h.assertTrue(p.getHealth()<health,"到期后原版攻击恢复扣血");mob.discard();
        }));
    }

    @GameTest(template="empty",timeoutTicks=70)
    public static void mirrorUsesFixedPlaneAndCancelsOnBrokenGlass(GameTestHelper h) {
        ServerPlayer p=player(h);BlockPos glass=BlockPos.containing(p.position()).offset(0,1,2);
        for(int x=-2;x<=2;x++) for(int y=-1;y<=1;y++) h.getLevel().setBlock(glass.offset(x,y,0),Blocks.GLASS.defaultBlockState(),2);
        p.setXRot(0);p.setYRot(0);p.setYHeadRot(0);NaiwaPranks.startMirror(p);
        h.assertTrue(NaiwaPranks.busy(p),"普通玻璃前启动镜像");
        NaiwaEntity frog=actor(p);p.setPos(p.position().add(.5,0,.2));
        h.runAfterDelay(5,()->{
            Vec3 expected=NaiwaPranks.reflect(p.position(),Direction.Axis.Z,glass.getZ()+.5);
            h.assertTrue(frog.position().distanceTo(expected)<.01,"沿窗口移动仍关于固定平面等距");
            h.getLevel().setBlock(glass,Blocks.AIR.defaultBlockState(),2);
        });
        h.runAfterDelay(10,()->done(h,p,()->h.assertTrue(!frog.isAlive() && !NaiwaPranks.busy(p),"破坏本次玻璃清理演员，不补播成功")));
    }

    /** 玩家贴墙时比奶蛙窄；靠近后横移仍须能完成整蛊，演员不能挤进玻璃。 */
    private static void mirrorFollowsAlongCloseWall(GameTestHelper h,Direction facing) {
        ServerPlayer p=player(h);p.setPos(h.absoluteVec(new Vec3(5.5,3,5.5)));
        Vec3 origin=p.position();BlockPos glass=p.blockPosition().relative(facing,2).above();
        Direction along=facing.getClockWise();
        for(int x=-2;x<=2;x++)for(int y=-1;y<=2;y++)h.getLevel().setBlock(glass.relative(along,x).offset(0,y,0),Blocks.GLASS.defaultBlockState(),2);
        p.setYRot(facing.toYRot());p.setYHeadRot(facing.toYRot());p.setXRot(0);NaiwaPranks.startMirror(p);
        h.assertTrue(NaiwaPranks.busy(p),"安全距离能启动镜像");NaiwaEntity frog=actor(p);
        // 普通玩家贴住完整玻璃时，中心离镜面仅 0.8 格；保持少量余量避免浮点边界。
        Vec3 close=origin.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(1.19));
        h.runAfterDelay(3,()->p.setPos(close));
        h.runAfterDelay(8,()->{
            h.assertTrue(!h.getLevel().getBlockCollisions(p,p.getBoundingBox()).iterator().hasNext(),"复现使用正常玩家能站立的贴墙位置");
            h.assertTrue(frog.isAlive()&&NaiwaPranks.busy(p),"靠近玻璃不会因奶蛙较宽立即取消");
        });
        h.runAfterDelay(12,()->p.setPos(close.add(Vec3.atLowerCornerOf(along.getNormal()).scale(.5))));
        h.runAfterDelay(20,()->{
            h.assertTrue(frog.isAlive()&&NaiwaPranks.busy(p),"贴近玻璃沿墙移动仍继续镜像");
            h.assertTrue(!h.getLevel().getBlockCollisions(frog,frog.getBoundingBox()).iterator().hasNext(),"镜像演员始终没有挤进玻璃或其他方块");
            Direction.Axis alongAxis=along.getAxis();
            h.assertTrue(Math.abs(frog.position().get(alongAxis)-p.position().get(alongAxis))<.01,"沿墙位置仍与玩家对应");
        });
        h.runAfterDelay(170,()->done(h,p,()->h.assertTrue(frog.isAlive()&&frog.animation().equals("laugh_sequence"),"贴近并沿墙移动后看满八秒能正常大笑")));
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void mirrorFollowsCloseSouthWall(GameTestHelper h) {mirrorFollowsAlongCloseWall(h,Direction.SOUTH);}

    @GameTest(template="empty",timeoutTicks=200)
    public static void mirrorFollowsCloseWestWall(GameTestHelper h) {mirrorFollowsAlongCloseWall(h,Direction.WEST);}

    @GameTest(template="empty",timeoutTicks=35)
    public static void mirrorCanStartCloseToGlass(GameTestHelper h) {
        ServerPlayer p=player(h);BlockPos glass=p.blockPosition().offset(0,1,2);
        for(int x=-2;x<=2;x++)for(int y=-1;y<=2;y++)h.getLevel().setBlock(glass.offset(x,y,0),Blocks.GLASS.defaultBlockState(),2);
        p.setPos(p.position().add(0,0,1.19));p.setYRot(0);p.setYHeadRot(0);p.setXRot(0);NaiwaPranks.startMirror(p);
        h.assertTrue(NaiwaPranks.busy(p),"已贴近玻璃也能开始，不受旧两格演员距离门槛限制");NaiwaEntity frog=actor(p);
        h.runAfterDelay(8,()->done(h,p,()->h.assertTrue(frog.isAlive(),"近墙出场后不会立即退场")));
    }

    @GameTest(template="empty",timeoutTicks=35)
    public static void mirrorStillCancelsWhenOtherWallBlocksMovement(GameTestHelper h) {
        ServerPlayer p=player(h);BlockPos glass=p.blockPosition().offset(0,1,2);
        for(int x=-2;x<=2;x++)for(int y=-1;y<=2;y++)h.getLevel().setBlock(glass.offset(x,y,0),Blocks.GLASS.defaultBlockState(),2);
        p.setYRot(0);p.setYHeadRot(0);p.setXRot(0);NaiwaPranks.startMirror(p);NaiwaEntity frog=actor(p);
        h.runAfterDelay(3,()->{
            BlockPos obstacle=frog.blockPosition().east();
            h.getLevel().setBlock(obstacle,Blocks.STONE.defaultBlockState(),2);
            h.getLevel().setBlock(obstacle.above(),Blocks.STONE.defaultBlockState(),2);
            p.setPos(p.position().add(1,0,0));
        });
        h.runAfterDelay(8,()->done(h,p,()->h.assertTrue(!frog.isAlive()&&!NaiwaPranks.busy(p),"修正近墙净空后仍不能穿过旁边的实心墙")));
    }

    @GameTest(template="empty",timeoutTicks=65)
    public static void doorReopenCaughtOnceAndKeepsRealDoor(GameTestHelper h) {
        ServerPlayer p=player(h);BlockPos door=BlockPos.containing(p.position()).offset(0,0,2);
        var lower=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN,true);
        h.getLevel().setBlock(door,lower,2);h.getLevel().setBlock(door.above(),lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        h.assertTrue(NaiwaPranks.startBlock(p,"door",door),"门另一侧有合法站位");NaiwaEntity frog=actor(p);face(p,frog.getEyePosition());
        h.runAfterDelay(2,()->h.getLevel().setBlock(door,lower.setValue(DoorBlock.OPEN,false),2));
        h.runAfterDelay(7,()->h.getLevel().setBlock(door,lower,2));
        h.runAfterDelay(11,()->h.getLevel().setBlock(door,lower.setValue(DoorBlock.OPEN,false),2));
        h.runAfterDelay(16,()->h.getLevel().setBlock(door,lower,2));
        h.runAfterDelay(23,()->done(h,p,()->{
            h.assertTrue(frog.animation().equals("laugh_sequence"),"第二次突然重开抓现行后完整大笑");
            h.assertTrue(h.getLevel().getBlockState(door).getValue(DoorBlock.OPEN),"整蛊不改真实门状态");
        }));
    }

    /** 关门等待不能提前笑或采气；以真实方块状态裁定三秒到期。 */
    @GameTest(template="empty",timeoutTicks=90)
    public static void closedDoorWaitsThreeSecondsThenLeaves(GameTestHelper h) {
        ServerPlayer p=player(h);BlockPos door=p.blockPosition().offset(0,0,2);
        var state=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN,true);
        h.getLevel().setBlock(door,state,2);h.getLevel().setBlock(door.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        h.assertTrue(NaiwaPranks.startBlock(p,"door",door),"建立门场景");var frog=actor(p);
        h.runAfterDelay(5,()->h.getLevel().setBlock(door,state.setValue(DoorBlock.OPEN,false),2));
        h.runAfterDelay(40,()->h.assertTrue(frog.isAlive()&&!frog.animation().equals("laugh_sequence")&&!frog.animation().equals("laugh_enter"),"关门等待保持严肃"));
        h.runAfterDelay(70,()->done(h,p,()->h.assertTrue(!frog.isAlive()&&!NaiwaPranks.busy(p),"关门三秒后离场")));
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void openDoorLaughsAfterThreeSeconds(GameTestHelper h) {
        ServerPlayer p=player(h);BlockPos door=p.blockPosition().offset(0,0,2);
        var state=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN,true);
        h.getLevel().setBlock(door,state,2);h.getLevel().setBlock(door.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        h.assertTrue(NaiwaPranks.startBlock(p,"door",door),"建立门场景");var frog=actor(p);
        h.runAfterDelay(55,()->h.assertTrue(!frog.animation().equals("laugh_sequence"),"三秒前不提前笑"));
        h.runAfterDelay(65,()->done(h,p,()->h.assertTrue(frog.animation().equals("laugh_sequence"),"三秒未关门则完整大笑")));
    }

    @GameTest(template="empty",timeoutTicks=65)
    public static void removedLogExposesTreeActorWithoutExtraDrops(GameTestHelper h) {
        ServerPlayer p=player(h);BlockPos log=BlockPos.containing(p.position()).offset(0,0,2);
        h.getLevel().setBlock(log,Blocks.OAK_LOG.defaultBlockState(),2);
        h.assertTrue(NaiwaPranks.startBlock(p,"tree",log),"树干对侧合法站位");NaiwaEntity frog=actor(p);
        h.getLevel().destroyBlock(log,true,p);face(p,frog.getEyePosition());
        h.runAfterDelay(25,()->done(h,p,()->{
            h.assertTrue(frog.animation().equals("laugh_sequence") && h.getLevel().getBlockState(log).isAir(),"扶空露馅后大笑，不回填树干");
            long count=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(log).inflate(3)).stream()
                    .mapToLong(item->item.getItem().is(Items.OAK_LOG)?item.getItem().getCount():0).sum();
            h.assertTrue(count+p.getInventory().countItem(Items.OAK_LOG)==1,"原木仅由原版破坏掉落一次，允许玩家正常拾取");
        }));
    }

    @GameTest(template="empty",timeoutTicks=160)
    public static void stoppedMiningHidesPropAndLaughs(GameTestHelper h) {
        ServerPlayer p=miningPlayer(h);BlockPos wall=BlockPos.containing(p.position()).offset(0,0,2);
        h.getLevel().setBlock(wall,Blocks.DIAMOND_ORE.defaultBlockState(),2);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setYRot(0);
        h.assertTrue(NaiwaPranks.startBlock(p,"mine",wall),"玩家身后有合法站位");NaiwaEntity frog=actor(p);face(p,frog.getEyePosition());
        // 即使收到破坏事件，也必须等待原版真正修改方块；保护领地取消挖掘不能触发露馅。
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(h.getLevel(),wall,h.getLevel().getBlockState(wall),p));
        h.runAfterDelay(35,()->{
            h.assertTrue(frog.act().equals("mine")&&!frog.animation().equals("laugh_sequence"),"只停镐或提前回头不能露馅，必须先实际挖掉方块");
            h.assertTrue(p.gameMode.destroyBlock(wall),"通过原版生存破坏流程触发 BreakEvent");
        });
        h.runAfterDelay(80,()->h.assertTrue(frog.act().equals("mine")&&!frog.animation().equals("laugh_sequence"),"被看到后仍继续装挖，不能立刻转头大笑"));
        h.runAfterDelay(110,()->h.assertTrue(frog.act().equals("mine_hide")&&!frog.animation().equals("laugh_sequence"),"装挖后先藏镐并转向玩家"));
        h.runAfterDelay(130,()->done(h,p,()->{
            h.assertTrue(frog.animation().equals("laugh_sequence") && frog.getMainHandItem().isEmpty(),"追加敲击并藏镐后大笑");
            h.assertTrue(h.getLevel().getBlockState(wall).isAir()&&p.getMainHandItem().getDamageValue()==1,"只有玩家真实挖掉一格并扣一次耐久");
        }));
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void miningFloorAndHighWallUseRearGround(GameTestHelper h) {
        ServerPlayer p=miningPlayer(h);p.setYRot(0);p.setXRot(90);
        for(BlockPos target:List.of(p.blockPosition().below(),p.blockPosition().offset(0,3,2))) {
            h.assertTrue(NaiwaPranks.startBlock(p,"mine",target),"挖脚下或高处均有身后站位");NaiwaEntity frog=actor(p);
            h.assertTrue(frog.getZ()<p.getZ()-4.9&&frog.getY()==p.getY()&&p.distanceToSqr(frog)>=24.9,"优先在五格正后方，以玩家脚下高度落地");
            h.assertTrue(h.getLevel().noCollision(frog,frog.getBoundingBox()),"演员不与玩家或地形碰撞");NaiwaPranks.cancel(p);p.getPersistentData().putString("naiwa.prank_last","");
        }
        done(h,p,()->{});
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void miningRearBlockedDoesNotUseFrontOrConsumeCooldown(GameTestHelper h) {
        ServerPlayer p=miningPlayer(h);p.setYRot(0);BlockPos feet=p.blockPosition();
        for(int x=-5;x<=5;x++)for(int z=-5;z<=-1;z++)for(int y=-2;y<=3;y++)h.getLevel().setBlock(feet.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        done(h,p,()->{
            h.assertTrue(!NaiwaPranks.startBlock(p,"mine",feet.below()),"身后无完整站位时放弃，不能改到玩家前面");
            h.assertTrue(!NaiwaPranks.busy(p)&&p.getPersistentData().getLong("naiwa.prank_next")==Long.MAX_VALUE,"未出场不消耗间隔");
        });
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void miningBlockedDirectRearUsesSafeRearSide(GameTestHelper h) {
        ServerPlayer p=miningPlayer(h);p.setYRot(0);BlockPos rear=BlockPos.containing(p.position().add(0,0,-5));
        for(int dy=0;dy<4;dy++)h.getLevel().setBlock(rear.above(dy),Blocks.STONE.defaultBlockState(),2);
        h.assertTrue(NaiwaPranks.startBlock(p,"mine",p.blockPosition().below()),"正后方受阻时可使用安全后侧");NaiwaEntity frog=actor(p);
        done(h,p,()->h.assertTrue(frog.getZ()<p.getZ()-1&&p.position().subtract(frog.position()).horizontalDistance()>=4.99&&h.getLevel().noCollision(frog,frog.getBoundingBox()),"后侧回退仍至少约五格且不穿墙"));
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void miningOnlyCloseRearSpaceDoesNotSpawn(GameTestHelper h) {
        ServerPlayer p=miningPlayer(h);p.setYRot(0);BlockPos feet=p.blockPosition();
        for(int x=-8;x<=8;x++)for(int z=-8;z<=-3;z++)for(int y=0;y<=3;y++)h.getLevel().setBlock(feet.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        done(h,p,()->{
            h.assertTrue(h.getLevel().noCollision(p.getBoundingBox().move(0,0,-2)),"夹具仍保留两格近处空间");
            h.assertTrue(!NaiwaPranks.startBlock(p,"mine",feet.below())&&!NaiwaPranks.busy(p),"只有近处能站也不能贴身出场");
            h.assertTrue(p.getPersistentData().getLong("naiwa.prank_next")==Long.MAX_VALUE,"没有远处站位不消耗间隔");
        });
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void miningFacesNearestVisibleBlockInsteadOfPlayersTarget(GameTestHelper h) {
        ServerPlayer p=miningPlayer(h);p.setYRot(0);
        BlockPos near=BlockPos.containing(p.position().add(0,0,-5)).offset(1,1,0);
        h.getLevel().setBlock(near,Blocks.STONE.defaultBlockState(),2);
        h.assertTrue(NaiwaPranks.startBlock(p,"mine",p.blockPosition().below()),"远处邻近方块不妨碍安全站位");NaiwaEntity frog=actor(p);
        var target=NaiwaPranks.miningTarget(frog);
        done(h,p,()->{
            h.assertTrue(target!=null&&target.getBlockPos().equals(near),"优先面向演员身边最近的可见方块，而不是五格外玩家脚下");
            h.assertTrue(frog.getLookAngle().dot(target.getLocation().subtract(frog.getEyePosition()).normalize())>.99,"身体朝向及头部俯仰真正看向该碰撞面");
            h.getLevel().setBlock(near,Blocks.AIR.defaultBlockState(),2);BlockPos ceiling=frog.blockPosition().above(3);
            h.getLevel().setBlock(ceiling,Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(NaiwaPranks.miningTarget(frog).getBlockPos().equals(ceiling),"眼睛上方更近的低顶面也必须参加比较，不能固定只挖地板");
        });
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void respawnMockConsumesPendingEventAtActualPosition(GameTestHelper h) {
        ServerPlayer p=player(h);p.getPersistentData().putBoolean("naiwa.respawn_mock",true);
        h.runAfterDelay(5,()->{
            h.assertTrue(!p.getPersistentData().getBoolean("naiwa.respawn_mock"),"实际复活后消费一次待触发事件");
            h.assertTrue(actor(p).animation().equals("laugh_sequence"),"在当前实际落点附近大笑");
        });
        h.runAfterDelay(10,()->done(h,p,()->h.assertTrue(p.level().getEntitiesOfClass(NaiwaEntity.class,p.getBoundingBox().inflate(8),NaiwaEntity::isAlive).size()==1,"重复 tick 不重复生成")));
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void projectileBurstsOnceRespectsWallsAndBossImmunity(GameTestHelper h) {
        ServerPlayer p=player(h);Vec3 center=p.position().add(0,0,4);
        var exposed=EntityType.HUSK.create(p.level());exposed.setPos(center.add(-1,0,0));exposed.setNoAi(true);exposed.setNoGravity(true);h.getLevel().addFreshEntity(exposed);
        var slime=EntityType.SLIME.create(p.level());slime.setPos(center.add(0,0,-1));slime.setNoAi(true);slime.setNoGravity(true);h.getLevel().addFreshEntity(slime);
        var hidden=EntityType.HUSK.create(p.level());hidden.setPos(center.add(2,0,0));hidden.setNoAi(true);hidden.setNoGravity(true);h.getLevel().addFreshEntity(hidden);
        BlockPos wall=BlockPos.containing(center).offset(1,0,0);
        h.getLevel().setBlock(wall,Blocks.GLASS.defaultBlockState(),2);h.getLevel().setBlock(wall.above(),Blocks.GLASS.defaultBlockState(),2);
        var gas=new LaughGasProjectile(Naiwa.GAS_PROJECTILE.get(),p,p.level());gas.setPos(center.add(0,1,0));h.getLevel().addFreshEntity(gas);
        var impact=new net.minecraft.world.phys.BlockHitResult(gas.position(),Direction.UP,BlockPos.containing(center),false);
        gas.onHit(impact);gas.onHit(impact);
        done(h,p,()->{
            h.assertTrue(exposed.hasEffect(Naiwa.LAUGHING) && exposed.getEffect(Naiwa.LAUGHING).getDuration()==50,"一次破碎产生一次普通怪物控场");
            h.assertTrue(slime.hasEffect(Naiwa.LAUGHING),"不继承 Monster 的普通敌对生物同样受控");
            h.assertTrue(!hidden.hasEffect(Naiwa.LAUGHING) && !p.hasEffect(Naiwa.LAUGHING),"玻璃遮挡生效，玩家不被投掷物控制");
            h.assertTrue(EntityType.WITHER.is(NaiwaGameplay.GAS_IMMUNE) && EntityType.WARDEN.is(NaiwaGameplay.GAS_IMMUNE),"首版 Boss 免疫由数据标签明确约束");
        });
    }

    @GameTest(template="empty",timeoutTicks=140)
    public static void fishingReelStealsCatchAndKeepsBookThroughLaugh(GameTestHelper h) {
        ServerPlayer p=player(h);var hook=fishingHook(h,p);
        h.runAfterDelay(25,()->{
            noFishingActor(h,p,"等待鱼咬钩时不能提前生成水下演员");
            var loot=List.of(new ItemStack(Items.COD));
            var event=new net.neoforged.neoforge.event.entity.player.ItemFishedEvent(loot,1,hook);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            noFishingActor(h,p,"收竿事件监听尚未结束时也不能提前入网");
            h.assertTrue(NaiwaPranks.stealFishingCatch(event),"事件结束后的结算点成功出场才截走渔获");
            h.assertTrue(!NaiwaPranks.stealFishingCatch(event),"重复通知不能截走第二次渔获或重复生成");
            h.assertTrue(!event.isCanceled() && event.getDrops().size()==1 && event.getRodDamage()==1,"不改事件副本和耐久，由原版取消分支结算一次");hook.discard();
        });
        h.runAfterDelay(28,()->{
            h.assertTrue(p.level().getEntitiesOfClass(NaiwaEntity.class,p.getBoundingBox().inflate(12),NaiwaEntity::isTemporary).size()==1,"重复通知只生成一只演员");face(p,actor(p).getEyePosition());
        });
        h.runAfterDelay(65,()->{
            NaiwaEntity frog=actor(p);face(p,frog.getEyePosition());
        });
        h.runAfterDelay(80,()->done(h,p,()->{
            NaiwaEntity frog=actor(p);
            h.assertTrue(frog.animation().equals("laugh_sequence")&&frog.getMainHandItem().is(Items.ENCHANTED_BOOK)&&frog.getMainHandItem().hasFoil(),"收竿出水后捧腹大笑仍手持发光附魔书");
            NaiwaPranks.cancel(p);
            h.assertTrue(!frog.isAlive()&&p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(12),e->e.getItem().is(Items.ENCHANTED_BOOK)).isEmpty(),"取消清理演员不掉落附魔书道具");
        }));
    }

    @GameTest(template="empty",timeoutTicks=90)
    public static void boatUsesRealSeatAndReleasesItOnExit(GameTestHelper h) {
        ServerPlayer p=player(h);Vec3 water=p.position().add(0,0,4);BlockPos pool=BlockPos.containing(water).below();
        for(int x=-2;x<=2;x++) for(int z=-1;z<=2;z++) h.getLevel().setBlock(pool.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        for(int x=-2;x<=2;x++) for(int z=-1;z<=2;z++) h.getLevel().setBlock(pool.offset(x,0,z),Blocks.WATER.defaultBlockState(),2);
        var boat=EntityType.BOAT.create(p.level());boat.setPos(water.add(0,-.45,0));h.getLevel().addFreshEntity(boat);p.startRiding(boat);
        p.getPersistentData().putLong("naiwa.prank_next",0);
        h.runAfterDelay(25,()->{
            h.assertTrue(boat.getPassengers().size()==2 && boat.getPassengers().contains(p),"只占真实空座，不挤走驾驶者: passengers="+boat.getPassengers().size()+" water="+boat.isInWater()+" submerged="+boat.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)+" riding="+(p.getVehicle()==boat)+" controller="+(boat.getControllingPassenger()==p)+" busy="+NaiwaPranks.busy(p)+" next="+p.getPersistentData().getLong("naiwa.prank_next")+" now="+p.level().getGameTime());
            p.stopRiding();p.setPos(water.add(3,0,0));p.setNoGravity(false);p.setOnGround(true);face(p,actor(p).getEyePosition());
        });
        h.runAfterDelay(40,()->{
            h.assertTrue(actor(p).animation().equals("laugh_sequence"),"靠岸下船看到后座演员后大笑");NaiwaPranks.cancel(p);
        });
        h.runAfterDelay(45,()->done(h,p,()->h.assertTrue(boat.isAlive() && boat.getPassengers().isEmpty(),"清理演员释放座位，保留原船")));
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void sinkingBobberKeepsFishActorAbovePoolFloor(GameTestHelper h) {
        ServerPlayer p=player(h);var hook=fishingHook(h,p);
        Vec3 water=p.position().add(0,0,4);BlockPos pool=BlockPos.containing(water).below();
        // 连续三 tick 保持下沉，避免浮漂自动回浮掩盖需要检查的池底碰撞。
        boolean[] sinking={false};
        h.onEachTick(()->{if(sinking[0]&&hook.isAlive()) {hook.setPos(water.add(0,-.95,0));hook.setDeltaMovement(Vec3.ZERO);}});
        p.getPersistentData().putLong("naiwa.prank_next",0);
        h.runAfterDelay(25,()->{
            noFishingActor(h,p,"浮漂尚未收回时不生成演员");
            // 原版咬钩会让浮漂下沉；演员比浮漂高，不能把脚部直接放进池底。
            sinking[0]=true;
            hook.setPos(water.add(0,-.95,0));hook.setDeltaMovement(Vec3.ZERO);
        });
        h.runAfterDelay(28,()->{
            noFishingActor(h,p,"咬钩下沉动画期间不能提前出场");
            var event=new net.neoforged.neoforge.event.entity.player.ItemFishedEvent(List.of(new ItemStack(Items.COD)),1,hook);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            h.assertTrue(NaiwaPranks.stealFishingCatch(event),"下沉浮漂仍可安全出场并截走渔获");
            hook.discard();
        });
        h.runAfterDelay(30,()->{NaiwaEntity frog=actor(p);h.assertTrue(frog.getY()>=pool.getY()-1-.001,"按水面找起跳点，不能因浮漂下沉而生在池底");face(p,frog.getEyePosition());});
        h.runAfterDelay(60,()->face(p,actor(p).getEyePosition()));
        h.runAfterDelay(75,()->done(h,p,()->h.assertTrue(actor(p).animation().equals("laugh_sequence"),"从实际安全位置起跳并落岸大笑")));
    }

    @GameTest(template="empty",timeoutTicks=90)
    public static void waitingForFishNeverSpawnsActor(GameTestHelper h) {
        ServerPlayer p=player(h);var hook=fishingHook(h,p);
        h.runAfterDelay(65,()->{hook.discard();done(h,p,()->noFishingActor(h,p,"等待阶段没有演员、冷却或额外诱导"));});
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void earlyFishingReelNeverStartsPrank(GameTestHelper h) {
        ServerPlayer p=player(h);var hook=fishingHook(h,p);
        h.runAfterDelay(25,()->h.assertTrue(hook.retrieve(p.getMainHandItem())==0,"未咬钩的原版空收竿没有额外耐久消耗"));
        h.runAfterDelay(30,()->done(h,p,()->noFishingActor(h,p,"空收竿不能出现演员或开始冷却")));
    }

    /** 只在自动化夹具内固定咬钩窗口，直接执行原版 retrieve 检查 Mixin；双客户端验收仍等待自然咬钩。 */
    private static int retrieveCatch(ServerPlayer p,net.minecraft.world.entity.projectile.FishingHook hook) {
        try {
            var nibble=net.minecraft.world.entity.projectile.FishingHook.class.getDeclaredField("nibble");nibble.setAccessible(true);nibble.setInt(hook,20);
            return hook.retrieve(p.getMainHandItem());
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("钓鱼结算夹具失败",error);}
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void nativeFishingSettlementWithholdsOnlySuccessfulPrank(GameTestHelper h) {
        ServerPlayer p=player(h);var hook=fishingHook(h,p);
        h.runAfterDelay(25,()->done(h,p,()->{
            h.assertTrue(retrieveCatch(p,hook)==1,"原版抢书结算返回一次鱼竿耐久");
            h.assertTrue(!hook.isAlive()&&p.fishing==null&&NaiwaPranks.busy(p),"原版收漂完成，场景已开始");
            h.assertTrue(p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(12)).isEmpty(),"成功抢书不生成任何本次渔获");
            NaiwaEntity frog=actor(p);h.assertTrue(frog.getMainHandItem().is(Items.ENCHANTED_BOOK),"服务端演员真实主手持书");
            frog.hurt(p.damageSources().playerAttack(p),1000);
            h.assertTrue(p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(12),e->e.getItem().is(Items.ENCHANTED_BOOK)).isEmpty(),"击杀临时演员也拿不到附魔书道具");
        }));
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void nativeFishingCooldownKeepsNormalLoot(GameTestHelper h) {
        ServerPlayer p=player(h);var hook=fishingHook(h,p);p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);
        h.runAfterDelay(25,()->done(h,p,()->{
            h.assertTrue(retrieveCatch(p,hook)==1,"普通收竿也只扣一次耐久");
            h.assertTrue(!NaiwaPranks.busy(p)&&!hook.isAlive(),"冷却期间只完成原版收竿");
            h.assertTrue(!p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(12)).isEmpty(),"未触发整蛊时真实渔获照常生成");
        }));
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void canceledFishingEventNeverSpawnsActor(GameTestHelper h) {
        ServerPlayer p=player(h);var hook=fishingHook(h,p);
        h.runAfterDelay(25,()->{
            var event=new net.neoforged.neoforge.event.entity.player.ItemFishedEvent(List.of(new ItemStack(Items.COD)),1,hook);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            // 模拟后执行的其他监听器取消收竿；待整个事件结束后读取最终结果，不能在 LOWEST 中抢先出场。
            event.setCanceled(true);h.assertTrue(!NaiwaPranks.stealFishingCatch(event),"最终被取消的事件不能出场或截走奖励");hook.discard();
        });
        h.runAfterDelay(30,()->done(h,p,()->noFishingActor(h,p,"收竿被取消后不能补发演员")));
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void emptyFishingLootNeverStartsPrank(GameTestHelper h) {
        ServerPlayer p=player(h);var hook=fishingHook(h,p);
        h.runAfterDelay(25,()->{var event=new net.neoforged.neoforge.event.entity.player.ItemFishedEvent(List.of(),1,hook);h.assertTrue(!NaiwaPranks.stealFishingCatch(event),"空渔获不走抢书结算");hook.discard();});
        h.runAfterDelay(30,()->done(h,p,()->noFishingActor(h,p,"无有效渔获时不补发假成功")));
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void shallowFishingCatchNeverSpawnsInFloor(GameTestHelper h) {
        ServerPlayer p=player(h);var hook=fishingHook(h,p);BlockPos pool=BlockPos.containing(p.position().add(0,0,4)).below();
        for(int x=-2;x<=2;x++)for(int z=-1;z<=2;z++)h.getLevel().setBlock(pool.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        h.runAfterDelay(25,()->{var event=new net.neoforged.neoforge.event.entity.player.ItemFishedEvent(List.of(new ItemStack(Items.COD)),1,hook);h.assertTrue(!NaiwaPranks.stealFishingCatch(event)&&event.getDrops().getFirst().is(Items.COD),"浅水拒绝整蛊，保留正常渔获");hook.discard();});
        h.runAfterDelay(30,()->done(h,p,()->noFishingActor(h,p,"浅水仍拒绝演员，不因调整时序而放宽身体空间")));
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void shorelineAboveFloatingBobberIsReachable(GameTestHelper h) {
        ServerPlayer p=player(h);Vec3 water=p.position().add(0,-.15,4);face(p,water.add(0,0,4));
        // 浮漂脚部经常略低于水面整数坐标；两格高的天然岸顶相对 floor(bobberY) 实际相差三格。
        BlockPos bank=BlockPos.containing(water.add(Vec3.directionFromRotation(0,p.getYRot()).scale(2.5))).above(2);
        // 原夹具是悬空单层平台；补实下方以免把平台底下的空腔误当作本测试的天然河岸。
        for(BlockPos b:BlockPos.betweenClosed(bank.offset(-4,-5,-4),bank.offset(4,-3,4)))h.getLevel().setBlock(b,Blocks.STONE.defaultBlockState(),2);
        for(int dy=0;dy<3;dy++)h.getLevel().setBlock(bank.below(dy),Blocks.GRASS_BLOCK.defaultBlockState(),2);
        Vec3 spot=NaiwaPranks.findSpot(p,water,false);
        done(h,p,()->h.assertTrue(spot!=null&&spot.equals(Vec3.atBottomCenterOf(bank.above())),"正常浮漂附近两格高岸应找到有支撑且不穿墙的岸顶: spot="+spot+" expected="+bank.above()+" water="+water));
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void fishLandingSkipsBankWithBlockedArc(GameTestHelper h) {
        ServerPlayer p=player(h);Vec3 water=p.position().add(0,-.1,4);face(p,water.add(0,0,4));
        BlockPos pool=BlockPos.containing(water);
        for(int x=-2;x<=2;x++)for(int z=-1;z<=2;z++) {
            h.getLevel().setBlock(pool.offset(x,-2,z),Blocks.STONE.defaultBlockState(),2);
            for(int dy=-1;dy<=0;dy++)h.getLevel().setBlock(pool.offset(x,dy,z),Blocks.WATER.defaultBlockState(),2);
        }
        BlockPos bank=BlockPos.containing(water.add(Vec3.directionFromRotation(0,p.getYRot()).scale(2.5))).above(2);
        for(int dy=0;dy<4;dy++)h.getLevel().setBlock(bank.below(dy),Blocks.STONE.defaultBlockState(),2);
        NaiwaEntity frog=Naiwa.ENTITY.get().create(h.getLevel());frog.setPos(water.add(0,-1.2,0));
        Vec3 first=NaiwaPranks.findSpot(p,water,false),reachable=NaiwaPranks.findSpot(p,water,false,frog);
        done(h,p,()->{
            h.assertTrue(first!=null&&!NaiwaPranks.fishPathClear(h.getLevel(),frog,frog.position(),first),"第一处高岸顶能站立，但原出水弧线会撞岸");
            h.assertTrue(reachable!=null&&!reachable.equals(first)&&NaiwaPranks.fishPathClear(h.getLevel(),frog,frog.position(),reachable),"改选完整弧线可达的其他岸点，不能直接穿岸或提前收场");
        });
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void fishingKeepsReachableBankWhenBobberDrifts(GameTestHelper h) {
        ServerPlayer p=player(h);
        Vec3 water=p.position().add(0,-.1,4);BlockPos pool=BlockPos.containing(water);
        for(int x=-3;x<=3;x++)for(int z=-1;z<=2;z++) {
            h.getLevel().setBlock(pool.offset(x,-2,z),Blocks.STONE.defaultBlockState(),2);
            for(int dy=-1;dy<=0;dy++)h.getLevel().setBlock(pool.offset(x,dy,z),Blocks.WATER.defaultBlockState(),2);
        }
        Vec3 original=NaiwaPranks.findSpot(p,water,false),drifted=water.add(0,0,-.8);
        NaiwaEntity frog=Naiwa.ENTITY.get().create(h.getLevel());frog.setPos(drifted.add(0,-1.2,0));
        // 使用真实水岸碰撞验证“旧岸仍可达、重新搜索会改选其他点”，不依赖假玩家的钓鱼计时或移动包。
        done(h,p,()->{
            h.assertTrue(original!=null&&NaiwaPranks.fishPathClear(h.getLevel(),frog,frog.position(),original),"原岸点完整弧线仍可达: original="+original+" from="+frog.position());
            h.assertTrue(!original.equals(NaiwaPranks.findSpot(p,drifted,false)),"漂移后重新搜索不会返回原岸点");
            h.assertTrue(original.equals(NaiwaPranks.fishLanding(p,frog,drifted,original)),"保留新搜索圈外仍可达的原岸点");
        });
    }

    @GameTest(template="empty",timeoutTicks=70)
    public static void boatCanRevealWhenOnlyBankSpaceIsOccupiedByPlayer(GameTestHelper h) {
        ServerPlayer p=player(h);Vec3 water=p.position().add(0,0,4);BlockPos pool=BlockPos.containing(water).below();
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++) {
            h.getLevel().setBlock(pool.offset(x,-2,z),Blocks.STONE.defaultBlockState(),2);
            for(int dy=-1;dy<=0;dy++)h.getLevel().setBlock(pool.offset(x,dy,z),Blocks.WATER.defaultBlockState(),2);
        }
        BlockPos bank=pool.offset(3,0,0);h.getLevel().setBlock(bank,Blocks.STONE.defaultBlockState(),2);
        var boat=EntityType.BOAT.create(p.level());boat.setPos(water.add(0,-.45,0));h.getLevel().addFreshEntity(boat);p.startRiding(boat);p.getPersistentData().putLong("naiwa.prank_next",0);
        h.runAfterDelay(25,()->{
            h.assertTrue(boat.getPassengers().size()==2,"后座演员实际登船");p.stopRiding();p.setPos(Vec3.atBottomCenterOf(bank.above()));p.setNoGravity(false);p.setOnGround(true);face(p,actor(p).getEyePosition());
            h.assertTrue(NaiwaPranks.findSpot(p,boat.position(),false)==null,"唯一的岸上空间由玩家占用，没有额外演员站位");
        });
        h.runAfterDelay(40,()->done(h,p,()->{h.assertTrue(actor(p).animation().equals("laugh_sequence"),"玩家已踩在岸上并看到后座，无需空闲岸点即可露馅");h.assertTrue(boat.isAlive(),"不改变原船");}));
    }

    @GameTest(template="empty",timeoutTicks=210)
    public static void boatTakesOverOnlyAfterIdleAndWaitsForPlayerLook(GameTestHelper h) {
        ServerPlayer p=player(h);p.setPos(h.absoluteVec(new Vec3(8.5,3,8.5)));Vec3 water=p.position();BlockPos pool=BlockPos.containing(water).below();
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++) {
            h.getLevel().setBlock(pool.offset(x,-2,z),Blocks.STONE.defaultBlockState(),2);
            for(int dy=-1;dy<=0;dy++)h.getLevel().setBlock(pool.offset(x,dy,z),Blocks.WATER.defaultBlockState(),2);
        }
        var boat=EntityType.BOAT.create(p.level());boat.setPos(water.add(0,-.45,0));h.getLevel().addFreshEntity(boat);p.startRiding(boat);
        boolean[] rowing={true},lookBehind={false},changed={false};int[] firstCommand={0};
        // 假连接没有真实按键包，按原版入口持续提交原始输入；方向只由测试玩家主动改变。
        h.onEachTick(()->{
            p.setPlayerInput(0,rowing[0]?1:0,false,false);
            if(boat.getPassengers().size()==2&&boat.getPassengers().get(1) instanceof NaiwaEntity frog) {
                if(lookBehind[0])face(p,frog.getEyePosition());else {p.setYRot(boat.getYRot());p.setXRot(0);}
                if(firstCommand[0]>0&&frog.boatSteering()>0&&frog.boatSteering()!=firstCommand[0])changed[0]=true;
            }
        });
        p.getPersistentData().putLong("naiwa.prank_next",0);
        h.runAfterDelay(25,()->{h.assertTrue(actor(p).boatSteering()==0,"玩家划船时不接管");rowing[0]=false;});
        h.runAfterDelay(45,()->{h.assertTrue(actor(p).boatSteering()>0,"停手半秒后接管划船");h.assertTrue(actor(p).animation().equals("auto"),"没有看向后座不能提前大笑");rowing[0]=true;});
        h.runAfterDelay(50,()->{h.assertTrue(actor(p).boatSteering()==0,"恢复原始划船输入即停止接管");rowing[0]=false;});
        h.runAfterDelay(75,()->{firstCommand[0]=actor(p).boatSteering();h.assertTrue(firstCommand[0]>0,"再次停手可以重新接管");});
        h.runAfterDelay(165,()->{h.assertTrue(changed[0],"接管期间会随机换方向");h.assertTrue(actor(p).animation().equals("auto"),"等待超过五秒也不提前大笑");lookBehind[0]=true;});
        h.runAfterDelay(180,()->{h.assertTrue(actor(p).animation().equals("laugh_sequence")&&actor(p).boatSteering()==4,"同船回头看见后座才大笑，并停止自动划船");});
        h.runAfterDelay(185,()->done(h,p,()->{
            NaiwaEntity frog=actor(p);NaiwaPranks.cancel(p);
            h.assertTrue(frog.boatSteering()==0&&!frog.isAlive()&&boat.isAlive()&&boat.getPassengers().size()==1&&p.getVehicle()==boat,"结束接管清理后座，原船和玩家驾驶位保留");
        }));
    }

    @GameTest(template="empty",timeoutTicks=25)
    public static void boatSteeringUsesVanillaMotionAndLetsDriverLookBehind(GameTestHelper h) {
        ServerPlayer p=player(h);var boat=EntityType.BOAT.create(p.level());boat.setPos(p.position().add(0,1,3));h.getLevel().addFreshEntity(boat);p.startRiding(boat);
        NaiwaEntity frog=h.spawn(Naiwa.ENTITY.get(),3,4,5);frog.temporary();frog.setAct("boat");frog.setBoatSteering(1);frog.startRiding(boat);
        done(h,p,()->{
            try {
                // 直接执行原版客户端使用的控制方法，验证已应用的 Mixin 和真实加速，不靠另写的移动公式代替。
                var control=net.minecraft.world.entity.vehicle.Boat.class.getDeclaredMethod("controlBoat");control.setAccessible(true);
                var clamp=net.minecraft.world.entity.vehicle.Boat.class.getDeclaredMethod("clampRotation",net.minecraft.world.entity.Entity.class);clamp.setAccessible(true);
                var left=net.minecraft.world.entity.vehicle.Boat.class.getDeclaredField("inputLeft");left.setAccessible(true);
                var right=net.minecraft.world.entity.vehicle.Boat.class.getDeclaredField("inputRight");right.setAccessible(true);
                var up=net.minecraft.world.entity.vehicle.Boat.class.getDeclaredField("inputUp");up.setAccessible(true);
                boat.setInput(false,false,false,false);Vec3 before=boat.position();control.invoke(boat);boat.move(net.minecraft.world.entity.MoverType.SELF,boat.getDeltaMovement());
                h.assertTrue(boat.position().subtract(before).horizontalDistance()>.03&&boat.getPaddleState(0)&&boat.getPaddleState(1),"停手时真实前进并划动原版双桨");
                boat.tickCount=4;frog.setBoatSteering(2);boat.setInput(false,false,false,false);h.assertTrue(left.getBoolean(boat)&&!right.getBoolean(boat)&&up.getBoolean(boat),"随机左转指令进入原版控制输入");
                frog.setBoatSteering(3);boat.setInput(false,false,false,false);h.assertTrue(!left.getBoolean(boat)&&right.getBoolean(boat)&&up.getBoolean(boat),"随机右转指令进入原版控制输入");
                boat.setInput(true,false,false,false);h.assertTrue(left.getBoolean(boat)&&!right.getBoolean(boat)&&!up.getBoolean(boat),"原始按键立即覆盖未到齐的接管停止同步");
                p.setYRot(boat.getYRot()+180);clamp.invoke(boat,p);h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(p.getYRot()-boat.getYRot()))>179,"本轮可主动回头看正后座");
                frog.setAct("");frog.setBoatSteering(4);frog.play("laugh_sequence");boat.setInput(false,false,false,false);h.assertTrue(!up.getBoolean(boat),"大笑期间停止接管");
                p.setYRot(boat.getYRot()+180);clamp.invoke(boat,p);h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(p.getYRot()-boat.getYRot()))>179,"大笑开始后仍能看见后座，不被视角限制弹回");
                frog.setBoatSteering(0);frog.stopRiding();frog.discard();p.setYRot(boat.getYRot()+180);clamp.invoke(boat,p);
                h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(p.getYRot()-boat.getYRot()))<=105.01,"退场后恢复原版视角限制");
                boat.setInput(false,false,false,false);h.assertTrue(!up.getBoolean(boat),"退场后不残留自动输入");
            } catch(ReflectiveOperationException e) {throw new AssertionError("原版船控制方法核对失败",e);}
            finally {frog.stopRiding();frog.discard();p.stopRiding();boat.discard();}
        });
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void shoreSearchIncludesBackDiagonalBeyondFirstRing(GameTestHelper h) {
        ServerPlayer p=player(h);Vec3 water=p.position().add(0,-.1,4);BlockPos pool=BlockPos.containing(water);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++) {
            h.getLevel().setBlock(pool.offset(x,-2,z),Blocks.STONE.defaultBlockState(),2);
            for(int dy=-1;dy<=0;dy++)h.getLevel().setBlock(pool.offset(x,dy,z),Blocks.WATER.defaultBlockState(),2);
        }
        // 玩家身旁另一侧的弯岸位于 3.5 格圈的侧后方；旧单圈只会看见水面与玩家占用的位置。
        BlockPos bank=BlockPos.containing(p.position()).offset(2,-1,1);
        h.getLevel().setBlock(BlockPos.containing(p.position()).below(),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(bank,Blocks.STONE.defaultBlockState(),2);
        Vec3 spot=NaiwaPranks.findSpot(p,water,false);
        done(h,p,()->h.assertTrue(Vec3.atBottomCenterOf(bank.above()).equals(spot),"曲岸侧后方的合法站位不能被固定单圈漏掉: spot="+spot+" bank="+bank));
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void twoPlayersCannotHarvestOneLaughTwiceOrResetByReload(GameTestHelper h) {
        ServerPlayer first=player(h),second=NaiwaShieldTests.player(h);second.setPos(first.position().add(1,0,0));
        first.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE));
        second.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE));
        NaiwaEntity frog=h.spawn(Naiwa.ENTITY.get(),3,3,5);frog.setNoAi(true);frog.setNoGravity(true);frog.play("laugh_sequence");
        h.runAfterDelay(15,()->{
            h.assertTrue(first.getMainHandItem().is(Naiwa.GAS.get()),"最近玩家自动采集成功");
            h.assertTrue(!frog.collect(second,InteractionHand.MAIN_HAND) && second.getMainHandItem().is(Items.GLASS_BOTTLE),"第二位玩家不重复获得，也不吞瓶");
            var saved=new net.minecraft.nbt.CompoundTag();frog.saveWithoutId(saved);
            var loaded=Naiwa.ENTITY.get().create(h.getLevel());loaded.load(saved);loaded.play("laugh_sequence");
            for(int i=0;i<15;i++) loaded.tick();
            h.assertTrue(!loaded.collect(second,InteractionHand.MAIN_HAND),"保存重载不能重置常驻本体的五分钟采集间隔");
            NaiwaShieldTests.remove(second);done(h,first,()->{});
        });
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void realDeathAndRespawnUseTheActualNewPlayer(GameTestHelper h) {
        ServerPlayer old=player(h);BlockPos spawn=BlockPos.containing(old.position()).offset(2,0,0);
        NaiwaEntity resident=h.spawn(Naiwa.ENTITY.get(),7,3,1);resident.setNoAi(true);resident.setNoGravity(true);
        Vec3 residentPosition=resident.position();ItemStack prop=new ItemStack(Items.DIAMOND);resident.setItemInHand(InteractionHand.MAIN_HAND,prop);
        old.setRespawnPosition(h.getLevel().dimension(),spawn,0,true,false);
        old.invulnerableTime=0;old.hurt(old.damageSources().generic(),100);
        h.assertTrue(!old.isAlive() && old.getPersistentData().getBoolean("naiwa.respawn_mock"),"真实死亡只记录一次待复活嘲笑");
        h.assertTrue(!NaiwaPranks.mock(old)&&totemActors(old).isEmpty(),"死亡阶段不在旧玩家或死亡位置出场");
        ServerPlayer fresh=h.getLevel().getServer().getPlayerList().respawn(old,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        h.assertTrue(fresh!=old && fresh.position().distanceTo(Vec3.atBottomCenterOf(spawn))<2,"使用原版实际复活对象与落点");
        fresh.setPos(fresh.position().add(0,0,1));fresh.setYRot(37);fresh.setXRot(-65);
        h.runAfterDelay(8,()->{
            h.assertTrue(!fresh.getPersistentData().getBoolean("naiwa.respawn_mock"),"新玩家实例消费待触发事件");
            h.assertTrue(totemActors(fresh).size()==1,"复活后只生成一个临时嘲笑演员");
            NaiwaEntity frog=totemActors(fresh).getFirst();Vec3 delta=frog.position().subtract(fresh.position()).multiply(1,0,1);
            h.assertTrue(Math.abs(delta.length()-3)<.01&&delta.normalize().dot(Vec3.directionFromRotation(0,fresh.getYRot()))>.999,"按新玩家当前落点和水平朝向正前方出场，不使用死亡点、旧床朝向或侧面常驻奶蛙");
            h.assertTrue(frog.animation().equals("laugh_sequence")&&Math.abs(net.minecraft.util.Mth.wrapDegrees(frog.getYRot()-fresh.getYRot()-180))<.01,"面向复活玩家播放完整大笑");
            h.assertTrue(resident.position().distanceToSqr(residentPosition)<.001&&resident.animation().equals("auto")&&resident.getMainHandItem()==prop,"常驻奶蛙的位置、动作及物品不变");
        });
        h.runAfterDelay(20,()->done(h,fresh,()->{
            h.assertTrue(totemActors(fresh).size()==1&&totemActors(fresh).getFirst().animationSeconds(0)>.5,"消费一次后不会重复生成或重启笑声");resident.discard();
        }));
    }

    @GameTest(template="empty",timeoutTicks=50)
    public static void respawnMockWaitsForFrontThenUsesNewFacing(GameTestHelper h) {
        ServerPlayer p=player(h);BlockPos feet=p.blockPosition();
        for(int x=-2;x<=2;x++)for(int z=1;z<=5;z++)for(int y=0;y<=4;y++)h.getLevel().setBlock(feet.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        NaiwaEntity resident=h.spawn(Naiwa.ENTITY.get(),6,3,3);resident.setNoAi(true);resident.setNoGravity(true);
        p.getPersistentData().putBoolean("naiwa.respawn_mock",true);p.getPersistentData().putLong("naiwa.prank_next",0);
        h.runAfterDelay(8,()->{
            h.assertTrue(totemActors(p).isEmpty()&&p.getPersistentData().getBoolean("naiwa.respawn_mock")&&NaiwaPranks.busy(p),"前方受阻时保留死亡待办，不在侧后方生成、不借常驻奶蛙、不让其他整蛊抢位");
            p.setYRot(90);p.setXRot(70);
        });
        h.runAfterDelay(18,()->done(h,p,()->{
            h.assertTrue(!p.getPersistentData().getBoolean("naiwa.respawn_mock")&&totemActors(p).size()==1,"转向有安全正前方后只出场一次");
            Vec3 delta=totemActors(p).getFirst().position().subtract(p.position());
            h.assertTrue(Math.abs(delta.z)<.01&&delta.x<-2.49,"重新按当前水平朝向计算，不固守复活时的朝向");resident.discard();
        }));
    }

    @GameTest(template="empty",timeoutTicks=280)
    public static void fatalLandingCancelsTotemMockAndOnlyMocksAfterRespawn(GameTestHelper h) {
        ServerPlayer old=player(h);Vec3 ground=old.position();BlockPos spawn=old.blockPosition().offset(2,0,0);
        old.setRespawnPosition(h.getLevel().dimension(),spawn,0,true,false);old.setPos(ground.add(0,20,0));NaiwaGameplay.startFlight(old);
        ServerPlayer[] fresh={null};
        h.runAfterDelay(235,()->{
            h.assertTrue(!old.hasEffect(Naiwa.ASCENDING)&&NaiwaGameplay.waitingForTotemLanding(old)&&totemActors(old).isEmpty(),"深坑中等待落地，不能提前嘲笑");
            // 走真实原版摔落伤害入口：本次落地受保护，之后的独立摔落没有永久免摔。
            float health=old.getHealth();old.causeFallDamage(40,1,old.damageSources().fall());
            h.assertTrue(old.isAlive()&&old.getHealth()==health&&!old.getPersistentData().getBoolean("naiwa.safe_fall"),"第一次实际摔落伤害被取消，并消费一次免摔资格");
            old.setPos(ground);old.setOnGround(true);old.invulnerableTime=0;old.causeFallDamage(40,1,old.damageSources().fall());
            h.assertTrue(!old.isAlive()&&!NaiwaGameplay.waitingForTotemLanding(old)&&old.getPersistentData().getBoolean("naiwa.respawn_mock"),"如果落地伤害仍导致死亡，立即取消落地演出，改记死亡嘲笑");
            h.assertTrue(totemActors(old).isEmpty(),"死亡时不在落地点留下一只图腾嘲笑演员");
            fresh[0]=h.getLevel().getServer().getPlayerList().respawn(old,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        });
        h.runAfterDelay(250,()->done(h,fresh[0],()->{
            h.assertTrue(fresh[0]!=old&&!NaiwaGameplay.waitingForTotemLanding(fresh[0]),"落地待办不转给重生玩家");
            h.assertTrue(!NaiwaGameplay.protectedFromMobs(fresh[0]),"重生玩家不继承死亡前的攻击保护");
            h.assertTrue(!fresh[0].getPersistentData().getBoolean("naiwa.respawn_mock")&&totemActors(fresh[0]).size()==1&&totemActors(fresh[0]).getFirst().animation().equals("laugh_sequence"),"实际复活点只触发一次死亡嘲笑，不重复播放旧图腾演出");
        }));
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void interruptedFlightAndReloadRestoreGravityAndEquipment(GameTestHelper h) {
        ServerPlayer p=player(h);var frog=h.spawn(Naiwa.ENTITY.get(),3,3,5);frog.setNoAi(true);frog.setNoGravity(true);
        var prop=new ItemStack(Items.DIAMOND);frog.setItemInHand(InteractionHand.MAIN_HAND,prop);
        NaiwaPranks.mock(p);NaiwaPranks.cancel(p);
        h.assertTrue(frog.getMainHandItem()==prop,"正前方嘲笑不挪用已有奶蛙的物品");
        var vehicle=EntityType.BOAT.create(p.level());vehicle.setPos(p.position());h.getLevel().addFreshEntity(vehicle);p.startRiding(vehicle);
        double triggerY=p.getY();
        NaiwaGameplay.startFlight(p);
        h.assertTrue(!p.isPassenger(),"飞升先离开载具，不能被座位拉回原高度");
        h.runAfterDelay(55,()->done(h,p,()->{
            h.assertTrue(Math.abs(p.getY()-triggerY-6)<.02,"载具中触发也按离座前的脚部高度升高六格");
            NaiwaGameplay.cleanup(p);
            h.assertTrue(!NaiwaGameplay.waitingForTotemLanding(p),"中断飞升同时取消落地演出待办");
            h.assertTrue(!NaiwaGameplay.protectedFromMobs(p),"中断同时清除攻击保护，不留下永久免疫");
            h.assertTrue(!p.hasEffect(Naiwa.ASCENDING) && p.isNoGravity(),"中断恢复进入飞升前的重力设置");
            h.assertTrue(p.getPersistentData().getBoolean("naiwa.safe_fall"),"半空中断仍保护本次落地");
            p.getPersistentData().putBoolean("naiwa.original_gravity",false);p.setNoGravity(true);NaiwaGameplay.cleanup(p);
            h.assertTrue(!p.isNoGravity(),"模拟崩溃重载后恢复存档中的原重力值");
        }));
    }

    @GameTest(template="empty",timeoutTicks=235)
    public static void nearbyFrogsReserveOneShieldConversion(GameTestHelper h) {
        ServerPlayer p=player(h);var first=h.spawn(Naiwa.ENTITY.get(),2,3,5);var second=h.spawn(Naiwa.ENTITY.get(),4,3,5);
        first.setNoAi(true);first.setNoGravity(true);second.setNoAi(true);second.setNoGravity(true);
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));
        h.runAfterDelay(25,()->h.assertTrue(first.animation().equals("laugh_sequence")!=second.animation().equals("laugh_sequence"),"两只本体只能有一只为同一玩家启动转化"));
        h.runAfterDelay(210,()->done(h,p,()->h.assertTrue(p.getOffhandItem().is(Naiwa.SHIELD.get()),"竞争结束后仍只原位交付一面奶蛙盾")));
    }

    private static ServerPlayer extraPlayer(GameTestHelper h) {
        ServerPlayer p=player(h);p.setOnGround(true);p.getPersistentData().putLong("naiwa.prank_next",0);return p;
    }
    private static NaiwaDecoy decoy(ServerPlayer p) {return p.level().getEntitiesOfClass(NaiwaDecoy.class,p.getBoundingBox().inflate(12)).getFirst();}
    private static void openChest(ServerPlayer p,BlockPos pos) {
        p.gameMode.useItemOn(p,p.level(),p.getMainHandItem(),InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }

    @GameTest(template="empty",timeoutTicks=70)
    public static void creeperPrankRequiresTurnAndPause(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);h.assertTrue(NaiwaPranks.startCreeper(p),"背后安全站位可开始假引燃声整蛊");NaiwaEntity frog=actor(p);
        h.runAfterDelay(5,()->{h.assertTrue(!frog.animation().equals("laugh_sequence"),"不转头不能成功");face(p,frog.getEyePosition());});
        h.runAfterDelay(10,()->h.assertTrue(!frog.animation().equals("laugh_sequence"),"转头后还须对视停顿半秒"));
        h.runAfterDelay(35,()->done(h,p,()->{
            h.assertTrue(frog.animation().equals("laugh_sequence"),"实际转头并保持对视才大笑");p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE));
            h.assertTrue(frog.collect(p,InteractionHand.MAIN_HAND),"新整蛊接入原子笑气采集");
        }));
    }

    @GameTest(template="empty",timeoutTicks=140)
    public static void ignoredCreeperScratchesAndLeavesWithoutReward(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);h.assertTrue(NaiwaPranks.startCreeper(p),"开始背后整蛊");NaiwaEntity frog=actor(p);
        h.runAfterDelay(75,()->{
            h.assertTrue(frog.act().equals("scratch")&&!frog.animation().equals("laugh_sequence"),"三秒无人回头后挠头，不假装成功");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE));h.assertTrue(!frog.collect(p,InteractionHand.MAIN_HAND),"失败不可收集笑气");
        });
        h.runAfterDelay(120,()->done(h,p,()->h.assertTrue(!NaiwaPranks.busy(p)&&!frog.isAlive()&&p.getPersistentData().getLong("naiwa.prank_next")>p.level().getGameTime(),"退场无残留，保留已开始的共享间隔")));
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void realCreeperFuseSuppressesFake(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);var creeper=EntityType.CREEPER.create(p.level());creeper.setNoAi(true);creeper.setPos(p.position().add(3,0,0));creeper.setSwellDir(1);h.getLevel().addFreshEntity(creeper);
        done(h,p,()->{h.assertTrue(!NaiwaPranks.startCreeper(p),"真实苦力怕引燃时不混入假声");noFishingActor(h,p,"未出场不占用间隔");creeper.discard();});
    }

    /** 通过真实生存挖矿计算掉落，不能用手工触发整蛊替代矿石、工具及原版掉落入口。 */
    private static NaiwaDecoy mineDiamond(GameTestHelper h,ServerPlayer p) {
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));p.setYRot(0);p.setXRot(0);
        BlockPos ore=p.blockPosition().offset(0,1,2);h.getLevel().setBlock(ore,Blocks.DIAMOND_ORE.defaultBlockState(),2);
        h.assertTrue(p.gameMode.destroyBlock(ore),"原版镐真正挖掉钻石矿");return decoy(p);
    }
    private static int looseDiamonds(ServerPlayer p) {return p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(12),e->e.getItem().is(Items.DIAMOND)).stream().mapToInt(e->e.getItem().getCount()).sum();}

    @GameTest(template="empty",timeoutTicks=350)
    public static void minedDiamondAvoidsForFourSecondsAndReturnsAfterActorDisappears(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);NaiwaDecoy bait=mineDiamond(h,p);Vec3 first=bait.position();
        h.assertTrue(p.level().getEntitiesOfClass(NaiwaEntity.class,p.getBoundingBox().inflate(12),NaiwaEntity::isTemporary).isEmpty(),"追逐前不提前暴露奶蛙");
        p.setPos(first.add(0,0,-1.4));
        h.runAfterDelay(15,()->{h.assertTrue(bait.position().subtract(p.position()).horizontalDistance()>=4,"避让到四格外");p.setPos(bait.position().add(0,0,-1.4));});
        h.runAfterDelay(35,()->{h.assertTrue(bait.position().subtract(p.position()).horizontalDistance()>=4,"再次接近继续避让");bait.interact(p,InteractionHand.MAIN_HAND);h.assertTrue(!bait.exposed(),"窗口内交互不能提前识破");});
        h.runAfterDelay(70,()->h.assertTrue(p.level().getEntitiesOfClass(NaiwaEntity.class,p.getBoundingBox().inflate(12),NaiwaEntity::isTemporary).isEmpty(),"四秒前不现身"));
        h.runAfterDelay(86,()->face(p,actor(p).getEyePosition()));
        var endpoint=new Vec3[1];
        h.runAfterDelay(105,()->{h.assertTrue(actor(p).animation().equals("laugh_sequence")&&bait.isAlive()&&bait.isInvisible(),"露馅大笑时只隐藏诱饵，真实钻石继续暂存");endpoint[0]=actor(p).position();h.assertTrue(looseDiamonds(p)==0&&p.getInventory().countItem(Items.DIAMOND)==0,"大笑期间不能提前拿到钻石");});
        h.runAfterDelay(320,()->done(h,p,()->{
            h.assertTrue(!bait.isAlive()&&!NaiwaPranks.busy(p)&&looseDiamonds(p)==1,"奶蛙消失后恰好交还原来一颗钻石");
            var drop=p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(12),e->e.getItem().is(Items.DIAMOND)).getFirst();
            h.assertTrue(drop.position().subtract(endpoint[0]).horizontalDistance()<.01,"返还钻石应原地落下，不随机抛离消失点："+drop.position()+" endpoint="+endpoint[0]);
        }));
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void attackingFakeDiamondExposesInsteadOfRewarding(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);NaiwaDecoy bait=mineDiamond(h,p);p.attack(bait);
        h.runAfterDelay(5,()->done(h,p,()->{
            h.assertTrue(bait.exposed()&&bait.isInvisible()&&actor(p).act().equals("diamond_caught"),"真实攻击戳穿诱饵，只露冷脸");
            h.assertTrue(!actor(p).animation().equals("laugh_sequence"),"提前识破不进笑气奖励窗口");NaiwaPranks.cancel(p);
            h.assertTrue(p.level().getEntitiesOfClass(NaiwaDecoy.class,p.getBoundingBox().inflate(12)).isEmpty(),"取消不留下诱饵");
            h.assertTrue(looseDiamonds(p)==1,"识破退场也交还真实钻石，不把原掉落当作失败惩罚");
        }));
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void interactingWithFakeDiamondAlsoExposes(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);NaiwaDecoy bait=mineDiamond(h,p);bait.interact(p,InteractionHand.MAIN_HAND);
        h.runAfterDelay(5,()->done(h,p,()->{h.assertTrue(bait.exposed()&&bait.isInvisible()&&!actor(p).animation().equals("laugh_sequence"),"右键识破不进入大笑奖励");NaiwaPranks.cancel(p);h.assertTrue(looseDiamonds(p)==1,"右键识破也原数交还");}));
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void diamondRefusesHolesAndDangerousFloor(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);BlockPos floor=p.blockPosition().below();var loot=List.of(new ItemStack(Items.DIAMOND));
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)if(Math.max(Math.abs(x),Math.abs(z))>=2)h.getLevel().setBlock(floor.offset(x,0,z),Blocks.AIR.defaultBlockState(),2);
        h.assertTrue(!NaiwaPranks.startDiamond(p,loot),"八个方向均无安全整段路线时不跨坑诱导");
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)if(Math.max(Math.abs(x),Math.abs(z))>=2)h.getLevel().setBlock(floor.offset(x,0,z),Blocks.MAGMA_BLOCK.defaultBlockState(),2);
        done(h,p,()->{h.assertTrue(!NaiwaPranks.startDiamond(p,loot)&&loot.getFirst().getCount()==1,"拒绝危险路线不能扣掉真实物品");noFishingActor(h,p,"路线不安全不出场、不开始间隔");});
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void diamondFortuneReturnsExactlyOriginalRollAndKeepsExperience(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);var tool=new ItemStack(Items.IRON_PICKAXE);
        tool.enchant(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE),3);p.setItemInHand(InteractionHand.MAIN_HAND,tool);
        BlockPos ore=p.blockPosition().offset(0,1,2);h.getLevel().setBlock(ore,Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(),2);int[] rolled={0,0};
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockDropsEvent> capture=e->{if(e.getBreaker()==p){rolled[0]=e.getDrops().stream().filter(item->item.getItem().is(Items.DIAMOND)).mapToInt(item->item.getItem().getCount()).sum();rolled[1]=e.getDroppedExperience();}};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,capture);
        try {h.assertTrue(p.gameMode.destroyBlock(ore)&&NaiwaPranks.busy(p),"时运镐实际开采深层钻石矿后接管本次掉落");}
        finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(capture);}
        done(h,p,()->{
            h.assertTrue(rolled[0]>=1&&looseDiamonds(p)==0,"时运已经由原版计算，整蛊期间没有真实掉落");NaiwaPranks.cancel(p);
            h.assertTrue(looseDiamonds(p)==rolled[0]&&tool.getDamageValue()==1,"原数交还本次时运结果，不重新抽取或多扣耐久");
            // 原版可能在生成时就合并同面值经验球；Value 是单份经验，Count 才是合并份数。
            int xp=p.level().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,p.getBoundingBox().inflate(12)).stream()
                    .mapToInt(orb->orb.getValue()*orb.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getInt("Count")).sum();
            h.assertTrue(xp==rolled[1],"原版开采经验不被暂存或重复发放");
        });
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void diamondSilkTouchAndCooldownKeepVanillaDrops(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);var tool=new ItemStack(Items.IRON_PICKAXE);
        tool.enchant(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH),1);p.setItemInHand(InteractionHand.MAIN_HAND,tool);
        BlockPos ore=p.blockPosition().offset(0,1,2);h.getLevel().setBlock(ore,Blocks.DIAMOND_ORE.defaultBlockState(),2);
        h.assertTrue(p.gameMode.destroyBlock(ore)&&!NaiwaPranks.busy(p)&&looseDiamonds(p)==0,"精准采集不把矿石改成钻石整蛊");
        h.assertTrue(p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(12),e->e.getItem().is(Items.DIAMOND_ORE)).size()==1,"精准采集的原矿照常掉落");
        p.getPersistentData().putLong("naiwa.prank_next",p.level().getGameTime()+1000);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));h.getLevel().setBlock(ore,Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(),2);
        done(h,p,()->h.assertTrue(p.gameMode.destroyBlock(ore)&&!NaiwaPranks.busy(p)&&looseDiamonds(p)==1,"冷却未结束时照常掉落真实钻石"));
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void diamondReloadReturnsComponentsAndCountOnce(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);var stack=new ItemStack(Items.DIAMOND,6);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("暂存钻石"));
        h.assertTrue(NaiwaPranks.startDiamond(p,List.of(stack)),"构造带原始组件的暂存栈");NaiwaDecoy original=decoy(p);var tag=original.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        original.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);NaiwaPranks.cancel(p);h.assertTrue(looseDiamonds(p)==0,"卸载旧实例后不能结算仍在保存副本中的钻石");
        NaiwaDecoy loaded=Naiwa.DECOY.get().create(p.level());loaded.load(tag);
        h.runAfterDelay(2,()->h.assertTrue(h.getLevel().addFreshEntity(loaded),"实际加载保存的暂存实体"));
        h.runAfterDelay(7,()->done(h,p,()->{
            h.assertTrue(looseDiamonds(p)==6&&!loaded.isAlive(),"重载时原数返还并清理暂存实体");
            var item=p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(16),e->e.getItem().is(Items.DIAMOND)).getFirst().getItem();
            h.assertTrue(ItemStack.matches(stack,item),"NBT 重载保留真实物品组件");loaded.finishAt(loaded.position());original.finishAt(original.position());h.assertTrue(looseDiamonds(p)==6,"重复清场或旧卸载对象不能复制返还");
        }));
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void diamondForcedRemovalReturnsOnce(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);NaiwaDecoy bait=mineDiamond(h,p);bait.discard();bait.finishAt(bait.position());
        h.runAfterDelay(5,()->done(h,p,()->h.assertTrue(looseDiamonds(p)==1&&!NaiwaPranks.busy(p),"强制删除诱饵也原数返还，后续场景清理不重复发放")));
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void diamondCanceledBreakNeverCapturesLoot(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));BlockPos ore=p.blockPosition().offset(0,1,2);h.getLevel().setBlock(ore,Blocks.DIAMOND_ORE.defaultBlockState(),2);
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> cancel=e->{if(e.getPlayer()==p)e.setCanceled(true);};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,cancel);
        try{done(h,p,()->h.assertTrue(!p.gameMode.destroyBlock(ore)&&!NaiwaPranks.busy(p)&&looseDiamonds(p)==0&&h.getLevel().getBlockState(ore).is(Blocks.DIAMOND_ORE),"被保护规则取消的开采不生成整蛊、不凭空造钻石"));}
        finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);}
    }

    @GameTest(template="empty",timeoutTicks=125)
    public static void miningSwingsDoNotTriggerIdleCreeperBeforeDiamondDrops(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));BlockPos ore=p.blockPosition().offset(0,1,2);h.getLevel().setBlock(ore,Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(),2);
        // 连续四秒以上真实挥臂，超过观察整蛊的三秒门槛；仍必须等开采成功后才选择钻石场景。
        h.onEachTick(()->{if(!h.getLevel().getBlockState(ore).isAir())p.swing(InteractionHand.MAIN_HAND);});
        h.runAfterDelay(90,()->{
            h.assertTrue(!NaiwaPranks.busy(p),"挥镐不能被误判成发呆而触发苦力怕代班");
            // 嵌入式连接没有原版地面移动包；实际开采前重新报告已验证的夹具站位。
            h.assertTrue(h.getLevel().getBlockState(p.blockPosition().below()).isFaceSturdy(h.getLevel(),p.blockPosition().below(),Direction.UP),"长时间挥臂后仍站在完整地面上");p.setOnGround(true);
            boolean broken=p.gameMode.destroyBlock(ore);
            h.assertTrue(broken&&!p.level().getEntitiesOfClass(NaiwaDecoy.class,p.getBoundingBox().inflate(12)).isEmpty(),"真正挖完钻石矿后仍可触发本轮钻石整蛊：broken="+broken+" ground="+p.onGround()+" next="+p.getPersistentData().getLong("naiwa.prank_next")+" now="+p.level().getGameTime());
        });
        h.runAfterDelay(100,()->done(h,p,()->{NaiwaPranks.cancel(p);h.assertTrue(looseDiamonds(p)==1,"活动计时修正不影响原数交还");}));
    }

    @GameTest(template="empty",timeoutTicks=110)
    public static void chestPeeksHeadAfterCloseAndKeepsStorageUsable(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);BlockPos pos=BlockPos.containing(p.position().add(0,0,3));h.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)p.level().getBlockEntity(pos);chest.setItem(0,new ItemStack(Items.APPLE,5));openChest(p,pos);
        h.assertTrue(p.containerMenu instanceof net.minecraft.world.inventory.ChestMenu&&!NaiwaPranks.busy(p),"开箱整理时不生成演员");
        h.runAfterDelay(5,p::closeContainer);
        h.runAfterDelay(20,()->{h.assertTrue(actor(p).act().equals("chest_peek")&&actor(p).getBbHeight()<1,"关界面后只探头，物理箱体没有两格高隐形身体");openChest(p,pos);});
        h.runAfterDelay(27,()->h.assertTrue(actor(p).act().equals("chest_peek")&&p.containerMenu instanceof net.minecraft.world.inventory.ChestMenu,"探头期间仍可打开同一个箱子，不跳出或翻倒"));
        h.runAfterDelay(40,()->{h.assertTrue(!actor(p).animation().equals("laugh_sequence"),"仍在存取物品时不抢先成功");p.closeContainer();face(p,actor(p).getEyePosition());});
        h.runAfterDelay(65,()->done(h,p,()->{
            h.assertTrue(actor(p).animation().equals("laugh_sequence")&&chest.getItem(0).getCount()==5,"完整大笑且箱内物品不变");
            h.assertTrue(net.minecraft.world.level.block.entity.ChestBlockEntity.getOpenCount(p.level(),pos)==0,"视觉开盖不伪造红石打开人数");
        }));
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void otherChestMenuCannotReuseCanceledClick(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);BlockPos first=BlockPos.containing(p.position().add(0,0,3)),second=first.offset(3,0,0);
        h.getLevel().setBlock(first,Blocks.CHEST.defaultBlockState(),3);h.getLevel().setBlock(second,Blocks.CHEST.defaultBlockState(),3);
        var click=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,first,new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(first),Direction.UP,first,false));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(click);click.setCanceled(true);
        p.openMenu((net.minecraft.world.level.block.entity.ChestBlockEntity)p.level().getBlockEntity(second));p.closeContainer();
        h.runAfterDelay(25,()->done(h,p,()->noFishingActor(h,p,"真实容器与被点击箱子不符时不能现身")));
    }

    private static NaiwaEntity distantFrog(GameTestHelper h,ServerPlayer p) {
        NaiwaEntity frog=Naiwa.ENTITY.get().create(p.level());frog.setPos(p.position().add(0,0,10));frog.setNoAi(true);frog.setNoGravity(true);h.getLevel().addFreshEntity(frog);return frog;
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void spyglassUseCreatesTemporaryActorAndReleaseLaughs(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);p.setYRot(0);p.setXRot(0);
        h.assertTrue(!NaiwaPranks.startScope(p,4),"没用望远镜不能出场");p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SPYGLASS));p.startUsingItem(InteractionHand.MAIN_HAND);
        h.assertTrue(NaiwaPranks.startScope(p,4),"周围没有常驻奶蛙也可直接触发");NaiwaEntity frog=actor(p);
        h.assertTrue(frog.isTemporary()&&frog.scopeViewer()==p.getId()&&Math.abs(frog.getZ()-p.getZ()-4)<.001&&!NaiwaPranks.startScope(p,4),"按镜内距离临时出场，重复申请不复制演员");
        h.runAfterDelay(8,()->{h.assertTrue(!frog.animation().equals("laugh_sequence"),"镜筒未放下不能直接大笑");p.stopUsingItem();face(p,frog.getEyePosition());});
        h.runAfterDelay(35,()->done(h,p,()->{
            h.assertTrue(frog.animation().equals("laugh_sequence")&&frog.isTemporary()&&frog.scopeViewer()==-1,"放下撤图后看到临时演员才笑");NaiwaPranks.cancel(p);
            h.assertTrue(!frog.isAlive(),"结束直接清除临时演员");
        }));
    }

    /** 全部改值与恢复在同一服务端调用内完成，不让并行 GameTest 读到专项设置。 */
    @GameTest(template="empty",timeoutTicks=20)
    public static void configurablePrankIntervalAndConsecutiveRule(GameTestHelper h) {
        int minimum=NaiwaConfig.MIN_INTERVAL_SECONDS.get(),maximum=NaiwaConfig.MAX_INTERVAL_SECONDS.get();
        boolean repeat=NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.get();ServerPlayer p=extraPlayer(h);
        try {
            h.assertTrue(minimum==300&&maximum==600&&!repeat,"默认保留五至十分钟及同种不连续");
            NaiwaConfig.MIN_INTERVAL_SECONDS.set(2);NaiwaConfig.MAX_INTERVAL_SECONDS.set(2);
            h.assertTrue(NaiwaPranks.startCreeper(p),"固定两秒设置下实际开始整蛊");
            h.assertTrue(p.getPersistentData().getLong("naiwa.prank_next")-p.level().getGameTime()==40,"秒正确换算为真实四十 tick 冷却");
            NaiwaPranks.cancel(p);NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.set(true);
            h.assertTrue(!NaiwaPranks.startCreeper(p),"开启重复也不能绕过尚未结束的冷却");
            NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.set(false);
            p.getPersistentData().putLong("naiwa.prank_next",0);
            h.assertTrue(!NaiwaPranks.startCreeper(p),"默认同种不连续");
            NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.set(true);
            h.assertTrue(NaiwaPranks.startCreeper(p),"开启开关后同一种整蛊实际再次开始");
            NaiwaConfig.MIN_INTERVAL_SECONDS.set(0);NaiwaConfig.MAX_INTERVAL_SECONDS.set(0);
            h.assertTrue(p.getPersistentData().getLong("naiwa.prank_next")-p.level().getGameTime()==40,"改设置不会重置已经排定的冷却");
            h.assertTrue(!NaiwaPranks.startCreeper(p),"零间隔仍不能重叠演出");NaiwaPranks.cancel(p);p.getPersistentData().putLong("naiwa.prank_next",0);
            h.assertTrue(NaiwaPranks.startCreeper(p)&&p.getPersistentData().getLong("naiwa.prank_next")==p.level().getGameTime(),"下一次开始按零额外间隔执行");
            NaiwaConfig.MIN_INTERVAL_SECONDS.set(3);NaiwaConfig.MAX_INTERVAL_SECONDS.set(1);var random=net.minecraft.util.RandomSource.create(123);
            for(int i=0;i<128;i++){int delay=NaiwaConfig.nextIntervalTicks(random);h.assertTrue(delay>=20&&delay<=60,"颠倒最小/最大输入仍在一至三秒闭区间");}
            NaiwaConfig.MIN_INTERVAL_SECONDS.set(86400);NaiwaConfig.MAX_INTERVAL_SECONDS.set(86400);
            h.assertTrue(NaiwaConfig.nextIntervalTicks(random)==1728000,"一天上限不会溢出");
            var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
            config.set("prankIntervalMinSeconds",-1);config.set("prankIntervalMaxSeconds",600);config.set("allowConsecutiveSamePrank",false);
            h.assertTrue(!NaiwaConfig.SPEC.isCorrect(config),"配置边界拒绝负数");config.set("prankIntervalMinSeconds",86401);
            h.assertTrue(!NaiwaConfig.SPEC.isCorrect(config),"配置边界拒绝超出一天");h.succeed();
        } finally {
            NaiwaConfig.MIN_INTERVAL_SECONDS.set(minimum);NaiwaConfig.MAX_INTERVAL_SECONDS.set(maximum);NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.set(repeat);
            NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);NaiwaShieldTests.remove(p);
        }
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void settingsDropRetiredDistancesWithoutChangingSchedule(GameTestHelper h) {
        var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        config.set("prankIntervalMinSeconds",17);config.set("prankIntervalMaxSeconds",29);config.set("allowConsecutiveSamePrank",true);
        config.set("respawnAppearDistance",6.0);config.set("totemLandingAppearDistance",8.0);
        NaiwaConfig.SPEC.correct(config);
        h.assertTrue(NaiwaConfig.SPEC.isCorrect(config)&&config.valueMap().size()==3,"旧配置校正后只保留三项");
        h.assertTrue(((Number)config.get("prankIntervalMinSeconds")).intValue()==17&&((Number)config.get("prankIntervalMaxSeconds")).intValue()==29
                &&Boolean.TRUE.equals(config.get("allowConsecutiveSamePrank")),"保留原间隔和重复选项");
        h.succeed();
    }

    /** 固定三格出场仍复查障碍，且不能绕过真实落地条件。 */
    @GameTest(template="empty",timeoutTicks=20)
    public static void fixedThreeBlockMockWaitsWhenBlocked(GameTestHelper h) {
        ServerPlayer p=player(h);p.setYRot(0);
        try {
            h.assertTrue(NaiwaPranks.mock(p),"复活正前方可出场");
            h.assertTrue(Math.abs(totemActors(p).getFirst().position().subtract(p.position()).horizontalDistance()-3)<.001,"固定三格");
            NaiwaPranks.cancel(p);p.setOnGround(true);
            h.assertTrue(NaiwaPranks.mockAfterTotemLanding(p),"真正落地可出场");NaiwaPranks.cancel(p);
            p.setOnGround(false);h.assertTrue(!NaiwaPranks.mockAfterTotemLanding(p),"空中不能嘲笑");
            BlockPos blocked=p.blockPosition().offset(0,0,3);
            for(int y=0;y<4;y++)h.getLevel().setBlock(blocked.above(y),Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(!NaiwaPranks.mock(p),"三格处受阻不退到四格或五格");h.succeed();
        } finally {NaiwaPranks.cancel(p);NaiwaGameplay.cleanup(p);NaiwaShieldTests.remove(p);}
    }

    @GameTest(template="empty",timeoutTicks=45)
    public static void spyglassLookAwayCancelsWithoutDuplicate(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);p.setYRot(0);p.setXRot(0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SPYGLASS));p.startUsingItem(InteractionHand.MAIN_HAND);h.assertTrue(NaiwaPranks.startScope(p,4),"开始贴脸");NaiwaEntity frog=actor(p);
        h.runAfterDelay(5,()->{p.stopUsingItem();p.setYRot(180);});
        h.runAfterDelay(12,()->done(h,p,()->{
            h.assertTrue(!NaiwaPranks.busy(p)&&!frog.isAlive()&&p.getYRot()==180,"转头清除演员，不强制扭转玩家视角");
            h.assertTrue(p.level().getEntitiesOfClass(NaiwaEntity.class,p.getBoundingBox().inflate(12),NaiwaEntity::isAlive).isEmpty(),"没有演员残留");p.stopUsingItem();
        }));
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void doubleChestReopenEitherHalfUsesSameContainer(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);BlockPos left=BlockPos.containing(p.position().add(0,0,3)),right=left.east();
        var state=Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,Direction.NORTH);
        h.getLevel().setBlock(left,state.setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.LEFT),3);
        h.getLevel().setBlock(right,state.setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.RIGHT),3);
        var first=(net.minecraft.world.level.block.entity.ChestBlockEntity)p.level().getBlockEntity(left);var second=(net.minecraft.world.level.block.entity.ChestBlockEntity)p.level().getBlockEntity(right);
        first.setItem(0,new ItemStack(Items.APPLE,5));second.setItem(0,new ItemStack(Items.COD,7));openChest(p,left);
        h.assertTrue(p.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu&&menu.getRowCount()==6,"真实大箱子有六行容器");p.closeContainer();
        h.runAfterDelay(15,()->{h.assertTrue(actor(p).act().equals("chest_peek"),"大箱子也在关界面后探头");openChest(p,right);});
        h.runAfterDelay(23,()->{h.assertTrue(actor(p).act().equals("chest_peek")&&p.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu&&menu.getRowCount()==6,"另一半重开仍可正常存取，不额外生成演员");p.closeContainer();face(p,actor(p).getEyePosition());});
        h.runAfterDelay(55,()->done(h,p,()->{h.assertTrue(actor(p).animation().equals("laugh_sequence")&&first.getItem(0).getCount()==5&&second.getItem(0).getCount()==7,"两半物品和大笑流程均保留");p.closeContainer();}));
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void breakingPeekingChestAlsoStopsLaughingHead(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);BlockPos pos=BlockPos.containing(p.position().add(0,0,3));h.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);openChest(p,pos);p.closeContainer();
        h.runAfterDelay(18,()->face(p,actor(p).getEyePosition()));
        h.runAfterDelay(60,()->{h.assertTrue(actor(p).animation().equals("laugh_sequence")&&actor(p).act().equals("chest_peek"),"大笑仍只显示箱内头部");h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);});
        h.runAfterDelay(65,()->done(h,p,()->h.assertTrue(!NaiwaPranks.busy(p)&&p.level().getEntitiesOfClass(NaiwaEntity.class,p.getBoundingBox().inflate(12),NaiwaEntity::isTemporary).isEmpty(),"大笑中拆箱也立即清理，不能悬空留下头部")));
    }

    @GameTest(template="empty",timeoutTicks=65)
    public static void fakeDiamondCannotBePickedUpOrHopperCollected(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);NaiwaDecoy bait=mineDiamond(h,p);ServerPlayer collector=player(h);BlockPos pos=BlockPos.containing(bait.position()).below();h.getLevel().setBlock(pos,Blocks.HOPPER.defaultBlockState(),3);
        var hopper=(net.minecraft.world.level.block.entity.HopperBlockEntity)p.level().getBlockEntity(pos);
        // 保持主人在避让范围外，独立验证漏斗与路人不能取物，不触发离开非完整漏斗地面的路线检查。
        p.setPos(bait.position().add(0,0,-6));collector.setPos(bait.position());
        h.runAfterDelay(40,()->done(h,p,()->{
            try{h.assertTrue(bait.isAlive()&&hopper.isEmpty()&&collector.getInventory().countItem(Items.DIAMOND)==0&&looseDiamonds(p)==0,"原版漏斗 tick 和另一玩家接触均取不到暂存钻石");}finally{NaiwaShieldTests.remove(collector);}
        }));
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void spyglassRefusesOccupiedActorSpaceAndInvalidDistance(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);p.setYRot(0);p.setXRot(0);for(int y=0;y<5;y++)h.getLevel().setBlock(BlockPos.containing(p.position().add(0,y,4)),Blocks.STONE.defaultBlockState(),3);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SPYGLASS));p.startUsingItem(InteractionHand.MAIN_HAND);
        done(h,p,()->{h.assertTrue(!NaiwaPranks.startScope(p,4)&&!NaiwaPranks.startScope(p,7)&&!NaiwaPranks.startScope(p,65),"目标占用或客户端距离越界均拒绝");h.assertTrue(!NaiwaPranks.busy(p)&&p.getPersistentData().getLong("naiwa.prank_next")==0,"失败不生成演员、不消耗间隔");p.stopUsingItem();});
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void spyglassNeverMovesPermanentFrog(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);NaiwaEntity original=distantFrog(h,p);original.setNoAi(false);Vec3 originalPos=original.position();original.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        p.setYRot(0);p.setXRot(0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SPYGLASS));p.startUsingItem(InteractionHand.MAIN_HAND);
        done(h,p,()->{
            h.assertTrue(NaiwaPranks.startScope(p,4)&&original.position().distanceToSqr(originalPos)<.001,"即使前方有常驻本体，也只生成新临时演员");
            NaiwaPranks.cancel(p);h.assertTrue(original.isAlive()&&!original.isNoAi()&&original.getMainHandItem().is(Items.IRON_PICKAXE),"常驻本体 AI 与真实道具不受影响");p.stopUsingItem();original.discard();
        });
    }

    @GameTest(template="empty",timeoutTicks=45)
    public static void spyglassSaveReloadRestoresAiAndKeepsRealEquipment(GameTestHelper h) {
        ServerPlayer p=extraPlayer(h);NaiwaEntity frog=distantFrog(h,p);frog.setNoAi(false);Vec3 original=frog.position();frog.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        // 直接构造 0.3.13 及以前的借用标记，保证升级存档仍能恢复本体；新版本不再创建这种场景。
        done(h,p,()->{
            var tag=frog.saveWithoutId(new net.minecraft.nbt.CompoundTag());tag.putBoolean("NoAI",true);var recovery=new net.minecraft.nbt.CompoundTag();recovery.putBoolean("NoAI",false);recovery.putDouble("X",original.x);recovery.putDouble("Y",original.y);recovery.putDouble("Z",original.z);tag.put("NaiwaScopeRecovery",recovery);
            NaiwaEntity loaded=Naiwa.ENTITY.get().create(p.level());loaded.load(tag);
            h.assertTrue(!loaded.isNoAi()&&loaded.position().distanceToSqr(original)<.001&&loaded.getMainHandItem().is(Items.IRON_PICKAXE)&&loaded.animation().equals("auto"),"实际实体序列化/重载恢复原 AI、原位、真实物品和自动动作");loaded.discard();NaiwaPranks.cancel(p);frog.discard();
        });
    }
}
