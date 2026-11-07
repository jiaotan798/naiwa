package com.naiwa;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 仅在 NeoForge 已确认交互或掉落的阶段将触发条件交给整蛊调度。 */
public final class NaiwaNeoForgePranks {
    private NaiwaNeoForgePranks() {}

    static void registerEvents() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post event) -> NaiwaPranks.tick(event.getServer()));
        bus.addListener(EventPriority.LOWEST, (LivingDeathEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) NaiwaPranks.died(player);
        });
        bus.addListener((PlayerEvent.Clone event) -> NaiwaPranks.copySchedule(event.getOriginal(), event.getEntity(), event.isWasDeath()));
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> NaiwaPranks.cancel((ServerPlayer)event.getEntity()));
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> NaiwaPranks.cancel((ServerPlayer)event.getEntity()));
        bus.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND)
                NaiwaPranks.usedBlock(player, event.getPos(), event.isCanceled());
        });
        bus.addListener((PlayerContainerEvent.Close event) -> {
            if (event.getEntity() instanceof ServerPlayer player) NaiwaPranks.closedMenu(player, event.getContainer());
        });
        bus.addListener((PlayerInteractEvent.LeftClickBlock event) -> {
            if (event.getEntity() instanceof ServerPlayer player) NaiwaPranks.attackedBlock(player, event.getPos());
        });
        bus.addListener(EventPriority.LOWEST, (BreakBlockEvent event) -> {
            if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player)
                NaiwaPranks.breakingBlock(player, event.getPos(), event.getState());
        });
        bus.addListener(EventPriority.LOWEST, NaiwaNeoForgePranks::diamondDrops);
        bus.addListener((ServerStoppingEvent event) -> NaiwaPranks.closeScenes());
        bus.addListener((ServerStoppedEvent event) -> NaiwaPranks.stopped());
    }

    /** 使用已计算的掉落，保留时运数量和物品组件；仅在演员接管成功后移除钻石。 */
    private static void diamondDrops(BlockDropsEvent event) {
        if (event.isCanceled() || !(event.getBreaker() instanceof ServerPlayer player)
                || !event.getState().is(BlockTags.DIAMOND_ORES) || !event.getTool().is(ItemTags.PICKAXES)) return;
        var diamonds = event.getDrops().stream().filter(item -> item.getItem().is(Items.DIAMOND)).toList();
        if (!diamonds.isEmpty() && NaiwaPranks.startDiamond(player, diamonds.stream().map(item -> item.getItem().copy()).toList()))
            event.getDrops().removeAll(diamonds);
    }

    /** 只在监听器均已执行的收竿结算点判断；被取消的原版事件不会安排演员。 */
    public static boolean stealFishingCatch(ItemFishedEvent event) {
        return !event.isCanceled() && event.getEntity() instanceof ServerPlayer player
                && NaiwaPranks.stealFishingCatch(player, event.getHookEntity(), event.getDrops());
    }
}
