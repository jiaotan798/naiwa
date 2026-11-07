package com.naiwa;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Forge 1.20.1 入口。掉落接管与实际扣血由结算点 Mixin 完成。 */
final class NaiwaForgeEvents {
    static void register() {
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener((RegisterCommandsEvent event) -> NaiwaCommands.register(event.getDispatcher()));
        bus.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) return;
            var server = event.getServer();
            NaiwaShield.tick(server);
            NaiwaEncounters.tick(server);
            NaiwaGameplay.tick(server);
            NaiwaPranks.tick(server);
        });
        bus.addListener((ServerStoppingEvent event) -> NaiwaPranks.closeScenes());
        bus.addListener((ServerStoppedEvent event) -> {
            NaiwaShield.stopped(); NaiwaEncounters.stopped();
            NaiwaGameplay.stopped(event.getServer()); NaiwaPranks.stopped();
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> NaiwaGameplay.cleanup((ServerPlayer)event.getEntity()));
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> leftWorld((ServerPlayer)event.getEntity()));
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> leftWorld((ServerPlayer)event.getEntity()));
        bus.addListener((PlayerEvent.Clone event) -> {
            NaiwaGameplay.copyFallProtection(event.getOriginal(),event.getEntity(),event.isWasDeath());
            NaiwaPranks.copySchedule(event.getOriginal(),event.getEntity(),event.isWasDeath());
        });
        bus.addListener((PlayerEvent.StartTracking event) -> {
            if (event.getEntity() instanceof ServerPlayer player) NaiwaEffectSync.startedTracking(player,event.getTarget());
        });
        bus.addListener(EventPriority.LOWEST,(ShieldBlockEvent event) -> {
            if (!event.isCanceled() && event.getBlockedDamage() > 0 && event.getEntity() instanceof ServerPlayer player)
                NaiwaShield.block(player,event);
        });
        bus.addListener(EventPriority.LOWEST,(LivingDeathEvent event) -> {
            if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
                NaiwaShield.clear(player.getUUID());
                NaiwaPranks.died(player);
            }
        });
        bus.addListener((LivingAttackEvent event) -> {
            if (NaiwaGameplay.blocksDamage(event.getEntity(),event.getSource())) event.setCanceled(true);
        });
        bus.addListener((LivingFallEvent event) -> {
            if (NaiwaGameplay.blocksFall(event.getEntity())) event.setCanceled(true);
        });
        bus.addListener(EventPriority.LOWEST,(LivingChangeTargetEvent event) -> {
            if (event.getEntity() instanceof Mob && event.getNewTarget() instanceof ServerPlayer player
                    && NaiwaGameplay.protectedFromMobs(player)) event.setNewTarget(null);
        });
        bus.addListener((AttackEntityEvent event) -> {
            if (event.getEntity().hasEffect(Naiwa.LAUGHING)) event.setCanceled(true);
        });
        bus.addListener((PlayerInteractEvent.RightClickItem event) -> {
            if (event.getEntity().hasEffect(Naiwa.LAUGHING) && NaiwaGameplay.attackingItem(event.getItemStack())) event.setCanceled(true);
        });
        bus.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND)
                NaiwaPranks.usedBlock(player,event.getPos(),event.isCanceled());
        });
        bus.addListener((PlayerInteractEvent.LeftClickBlock event) -> {
            if (event.getEntity() instanceof ServerPlayer player) NaiwaPranks.attackedBlock(player,event.getPos());
        });
        bus.addListener((PlayerContainerEvent.Close event) -> {
            if (event.getEntity() instanceof ServerPlayer player) NaiwaPranks.closedMenu(player,event.getContainer());
        });
        bus.addListener(EventPriority.LOWEST,(BlockEvent.BreakEvent event) -> {
            if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player)
                NaiwaPranks.breakingBlock(player,event.getPos(),event.getState());
        });
    }

    private static void leftWorld(ServerPlayer player) {
        NaiwaShield.clear(player.getUUID());
        NaiwaGameplay.cleanup(player);
        NaiwaPranks.cancel(player);
    }
}
