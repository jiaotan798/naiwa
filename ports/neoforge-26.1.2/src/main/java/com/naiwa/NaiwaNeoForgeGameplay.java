package com.naiwa;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 把 NeoForge 的事件时序与取消结果接到奶蛙的玩法结算。 */
final class NaiwaNeoForgeGameplay {
    private NaiwaNeoForgeGameplay() {}

    static void registerShieldEvents() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOWEST, NaiwaNeoForgeGameplay::shieldBlock);
        bus.addListener((ServerTickEvent.Post event) -> NaiwaShield.tick(event.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> NaiwaShield.clear(event.getEntity().getUUID()));
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> NaiwaShield.clear(event.getEntity().getUUID()));
        bus.addListener((LivingDeathEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) NaiwaShield.clear(player.getUUID());
        });
        bus.addListener((ServerStoppedEvent event) -> NaiwaShield.stopped());
    }

    /** 举盾、绕盾、零伤害和已取消的攻击均不计进度。 */
    static void shieldBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked() && event.getBlockedDamage() > 0 && event.getEntity() instanceof ServerPlayer player)
            NaiwaShield.block(player, event.getDamageContainer());
    }

    static void registerEvents() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post event) -> NaiwaGameplay.tick(event.getServer()));
        bus.addListener((AttackEntityEvent event) -> {
            if (event.getEntity().hasEffect(Naiwa.LAUGHING)) event.setCanceled(true);
        });
        bus.addListener((PlayerInteractEvent.RightClickItem event) -> {
            if (event.getEntity().hasEffect(Naiwa.LAUGHING) && NaiwaGameplay.attackingItem(event.getItemStack())) event.setCanceled(true);
        });
        bus.addListener((LivingIncomingDamageEvent event) -> {
            if (NaiwaGameplay.blocksDamage(event.getEntity(), event.getSource())) event.setCanceled(true);
        });
        bus.addListener(EventPriority.LOWEST, (LivingChangeTargetEvent event) -> {
            if (event.getEntity() instanceof Mob && event.getNewAboutToBeSetTarget() instanceof ServerPlayer player
                    && NaiwaGameplay.protectedFromMobs(player)) {
                // 取消事件会保留旧目标；改为 null 才能清除恰好也指向受保护玩家的旧目标。
                event.setNewAboutToBeSetTarget(null);
            }
        });
        bus.addListener((LivingDamageEvent.Post event) -> NaiwaGameplay.afterDamage(event.getEntity(), event.getHealthDamage()));
        bus.addListener((LivingFallEvent event) -> {
            if (NaiwaGameplay.blocksFall(event.getEntity())) event.setCanceled(true);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> NaiwaGameplay.cleanup((ServerPlayer) event.getEntity()));
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> NaiwaGameplay.cleanup((ServerPlayer) event.getEntity()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> NaiwaGameplay.cleanup((ServerPlayer) event.getEntity()));
        bus.addListener((PlayerEvent.Clone event) -> NaiwaGameplay.copyFallProtection(event.getOriginal(), event.getEntity(), event.isWasDeath()));
        bus.addListener((ServerStoppedEvent event) -> NaiwaGameplay.stopped(event.getServer()));
    }
}
