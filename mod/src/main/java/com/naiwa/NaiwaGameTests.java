package com.naiwa;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/** 专服验证真实注册、命令时间轴及重载恢复；测试类和结构不会进入玩家安装包。 */
@GameTestHolder("naiwa")
@PrefixGameTestTemplate(false)
public final class NaiwaGameTests {
    private static NaiwaEntity spawn(GameTestHelper helper) {
        var entity = helper.spawn(Naiwa.ENTITY.get(), 2, 3, 2);
        entity.setNoAi(true); entity.setNoGravity(true);
        return entity;
    }

    @GameTest(template="empty", timeoutTicks=30)
    public static void registrationAndAnimationCommand(GameTestHelper helper) {
        var entity = spawn(helper);
        helper.assertTrue(entity.getMaxHealth() == 20 && entity.animation().equals("auto"), "奶蛙属性和默认状态正确注册");
        var source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel());
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "naiwa animation " + entity.getUUID() + " wave");
        helper.assertTrue(entity.animation().equals("wave") && entity.animationSeconds(0) == 0, "真实命令同步动画名称和服务端起始时间");
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "naiwa animation " + entity.getUUID() + " missing");
        helper.assertTrue(entity.animation().equals("wave"), "非法动画不能改变当前状态");
        helper.succeed();
    }

    /** 同步动作先后顺序、重复更新和多演员清理必须精确登记，不能让某只退场误关其他演员的箱盖。 */
    @GameTest(template="empty", timeoutTicks=30)
    public static void chestPeekerRegistrationFollowsEntityLifecycle(GameTestHelper h) {
        var level=h.getLevel();int baseline=NaiwaEntity.chestPeekerCount(level);
        var first=Naiwa.ENTITY.get().create(level);var second=Naiwa.ENTITY.get().create(level);
        first.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,3,2)));
        second.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(3,3,2)));
        first.setNoAi(true);second.setNoAi(true);
        try {
            first.setAct("chest_peek");
            h.assertTrue(NaiwaEntity.chestPeekerCount(level)==baseline,"未入场演员不启用箱盖查询");
            h.assertTrue(level.addFreshEntity(first)&&level.addFreshEntity(second),"两个真实演员入场");
            h.assertTrue(NaiwaEntity.chestPeekerCount(level)==baseline+1,"入场前设置的探头动作不会漏登记");
            checkPeekQuery(h,first.blockPosition());checkPeekQuery(h,second.blockPosition());
            second.setAct("chest_peek");second.setAct("chest_peek");
            h.assertTrue(NaiwaEntity.chestPeekerCount(level)==baseline+2&&NaiwaEntity.hasChestPeekers(level),"入场后动作更新且重复设置不会多计");
            checkPeekQuery(h,first.blockPosition());checkPeekQuery(h,second.blockPosition());
            first.setAct("mine");
            h.assertTrue(NaiwaEntity.chestPeekerCount(level)==baseline+1,"退出探头动作只清理自己");
            checkPeekQuery(h,first.blockPosition());
            second.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(NaiwaEntity.chestPeekerCount(level)==baseline,"区块卸载立即释放登记");
            first.setAct("chest_peek");var oldPosition=first.blockPosition();first.setPos(first.position().add(2,0,0));
            checkPeekQuery(h,oldPosition);checkPeekQuery(h,first.blockPosition());first.discard();
            h.assertTrue(NaiwaEntity.chestPeekerCount(level)==baseline,"演员退场立即释放登记");
            h.assertTrue(NaiwaEntity.hasChestPeekers(level)==(baseline>0),"恢复原先查询开关，不影响其他场景");
            h.succeed();
        } finally {first.discard();second.discard();}
    }

    /** 与旧实体区段查询对照，覆盖位置变化及动作/退场，无需靠缓存箱盖值推测。 */
    private static void checkPeekQuery(GameTestHelper h,net.minecraft.core.BlockPos pos) {
        boolean old=!h.getLevel().getEntitiesOfClass(NaiwaEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(.5,1,.5),
                frog->frog.isAlive()&&frog.act().equals("chest_peek")&&frog.blockPosition().equals(pos)).isEmpty();
        h.assertTrue(old==NaiwaEntity.isChestPeeking(h.getLevel(),pos),"探头登记查询必须与原空间查询一致");
    }

    @GameTest(template="empty", timeoutTicks=215)
    public static void laughterSequenceRestoresNormalState(GameTestHelper helper) {
        var entity = spawn(helper); entity.play("laugh_sequence");
        helper.runAfterDelay(20, () -> helper.assertTrue(entity.clip(0).name().equals("laugh"), "入场结束后进入大笑"));
        helper.runAfterDelay(170, () -> helper.assertTrue(entity.clip(0).name().equals("laugh"), "旧版收场时刻仍须播放新模型的大笑"));
        helper.runAfterDelay(185, () -> helper.assertTrue(entity.clip(0).name().equals("laugh_exit"), "大笑结束后收起姿态"));
        helper.runAfterDelay(195, () -> {
            helper.assertTrue(entity.animation().equals("auto"), "完整大笑结束后恢复自动活动"); helper.succeed();
        });
    }

    @GameTest(template="empty", timeoutTicks=30)
    public static void automaticJumpUsesCurrentAirborneTime(GameTestHelper helper) {
        var entity = spawn(helper);
        helper.runAfterDelay(5, () -> {
            var clip = entity.clip(0);
            helper.assertTrue(clip.name().equals("jump") && clip.seconds() > 0 && clip.seconds() < 1,
                    "空中单次片段从本次离地播放，不能使用世界总时间直接跳到末帧");
            helper.succeed();
        });
    }

    @GameTest(template="empty", timeoutTicks=30)
    public static void persistentBodyDoesNotRestoreOldPerformance(GameTestHelper helper) {
        var entity = spawn(helper); entity.play("ascend_hover");
        CompoundTag data = new CompoundTag(); entity.saveWithoutId(data);
        var loaded = Naiwa.ENTITY.get().create(helper.getLevel()); loaded.load(data);
        helper.assertTrue(loaded.animation().equals("auto"), "存档保留生物数据，但不能重放上次临时动作");
        helper.assertTrue(loaded.getUUID().equals(entity.getUUID()) && loaded.getHealth() == entity.getHealth(), "恢复姿态不改变身份和生命值");
        entity.play("auto"); helper.assertTrue(entity.animation().equals("auto"), "循环动画可由服务端正常停止");
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=245)
    public static void originalLaughterStartsAfterEntryAndPlaysOncePerPerformance(GameTestHelper h) {
        var entity=spawn(h); var plays=new AtomicInteger();
        Consumer<PlayLevelSoundEvent.AtEntity> listener=event -> {
            if(event.getEntity()==entity && event.getSound()!=null && event.getSound().value()==Naiwa.LAUGH.get()) {
                h.assertTrue(event.getOriginalPitch()==1,"原声不变速"); plays.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(listener); entity.play("laugh_sequence");
        h.runAfterDelay(8, () -> h.assertTrue(plays.get()==0,"入场阶段尚不播放大笑音轨"));
        h.runAfterDelay(20, () -> {
            h.assertTrue(plays.get()==1,"进入大笑时由服务端广播一次原声");
            entity.play("auto"); entity.play("laugh_sequence");
        });
        h.runAfterDelay(28, () -> h.assertTrue(plays.get()==1,"新一轮重新等待入场，不能立即重复播放"));
        h.runAfterDelay(220, () -> {
            try {
                h.assertTrue(plays.get()==2 && entity.animation().equals("auto"),"两轮各播一次并恢复普通状态"); h.succeed();
            } finally {NeoForge.EVENT_BUS.unregister(listener);}
        });
    }
}
