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

    @GameTest(template="empty", timeoutTicks=190)
    public static void laughterSequenceRestoresNormalState(GameTestHelper helper) {
        var entity = spawn(helper); entity.play("laugh_sequence");
        helper.runAfterDelay(20, () -> helper.assertTrue(entity.clip(0).name().equals("laugh"), "入场结束后进入大笑"));
        helper.runAfterDelay(155, () -> helper.assertTrue(entity.clip(0).name().equals("laugh_exit"), "大笑结束后收起姿态"));
        helper.runAfterDelay(170, () -> {
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

    @GameTest(template="empty", timeoutTicks=225)
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
        h.runAfterDelay(200, () -> {
            try {
                h.assertTrue(plays.get()==2 && entity.animation().equals("auto"),"两轮各播一次并恢复普通状态"); h.succeed();
            } finally {NeoForge.EVENT_BUS.unregister(listener);}
        });
    }
}
