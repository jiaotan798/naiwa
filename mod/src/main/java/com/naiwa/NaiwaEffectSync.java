package com.naiwa;

import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** 补齐奶蛙两种视觉效果的追踪同步；原版只向本人和乘客发送实时效果变化。 */
public final class NaiwaEffectSync {
    private NaiwaEffectSync() {}
    private static boolean visual(MobEffectInstance effect) {return effect.is(Naiwa.LAUGHING) || effect.is(Naiwa.ASCENDING);}

    /** 初次追踪发生在实体生成包之后；补当前剩余时间，旁观者无需等下一阶段才看到动作。 */
    static void startedTracking(ServerPlayer viewer, net.minecraft.world.entity.Entity target) {
        if (!(target instanceof LivingEntity living)) return;
        for (MobEffectInstance effect : living.getActiveEffects()) {
            if (visual(effect)) viewer.connection.send(new ClientboundUpdateMobEffectPacket(living.getId(),effect,false));
        }
    }

    /** 在效果实际新增、更新或移除后广播，覆盖飞升阶段切换、到期、受伤解除、清奶及换维度清理；本人仍走原版同步。 */
    public static void changed(LivingEntity entity,MobEffectInstance effect,boolean removed) {
        if (!(entity.level() instanceof ServerLevel level) || !visual(effect)) return;
        if (removed) level.getChunkSource().broadcast(entity,new ClientboundRemoveMobEffectPacket(entity.getId(),effect.getEffect()));
        else level.getChunkSource().broadcast(entity,new ClientboundUpdateMobEffectPacket(entity.getId(),effect,false));
    }
}
