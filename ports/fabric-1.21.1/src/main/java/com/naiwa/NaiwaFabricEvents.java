package com.naiwa;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;

/** Fabric 的通用事件；必须在原版结算内部触发的部分由专用 Mixin 接入。 */
final class NaiwaFabricEvents {
    private NaiwaFabricEvents() {}

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> NaiwaCommands.register(dispatcher));
        PayloadTypeRegistry.playC2S().register(NaiwaScopeRequest.TYPE,NaiwaScopeRequest.CODEC);
        PayloadTypeRegistry.playS2C().register(NaiwaFlightHeight.TYPE,NaiwaFlightHeight.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NaiwaScopeRequest.TYPE,(request,context) -> request.apply(context.player()));
        EntityTrackingEvents.START_TRACKING.register((target,viewer) -> NaiwaEffectSync.startedTracking(viewer,target));
        ServerEntityEvents.ENTITY_LOAD.register((entity,level) -> {
            if (entity instanceof NaiwaEntity frog) frog.updateChestPeeker(true);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,level) -> {
            if (entity instanceof NaiwaEntity frog) frog.updateChestPeeker(false);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            NaiwaShield.tick(server);
            NaiwaEncounters.tick(server);
            NaiwaGameplay.tick(server);
            NaiwaPranks.tick(server);
        });
        ServerLifecycleEvents.SERVER_STARTING.register(NaiwaConfig::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> NaiwaPranks.closeScenes());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            NaiwaShield.stopped();
            NaiwaEncounters.stopped();
            NaiwaGameplay.stopped(server);
            NaiwaPranks.stopped();
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> NaiwaGameplay.cleanup(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> leftWorld(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,oldWorld,newWorld) -> leftWorld(player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source) -> {
            if (entity instanceof ServerPlayer player) {
                NaiwaShield.clear(player.getUUID());
                NaiwaPranks.died(player);
            }
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer,newPlayer,alive) -> {
            NaiwaGameplay.copyFallProtection(oldPlayer,newPlayer,!alive);
            NaiwaPranks.copySchedule(oldPlayer,newPlayer,!alive);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount) -> !NaiwaGameplay.blocksDamage(entity,source));
        AttackEntityCallback.EVENT.register((player,level,hand,target,hit) ->
                player.hasEffect(Naiwa.LAUGHING) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player,level,hand) -> {
            var item = player.getItemInHand(hand);
            return player.hasEffect(Naiwa.LAUGHING) && NaiwaGameplay.attackingItem(item)
                    ? InteractionResultHolder.fail(item) : InteractionResultHolder.pass(item);
        });
        UseBlockCallback.EVENT.register((player,level,hand,hit) -> {
            if (player instanceof ServerPlayer serverPlayer && !player.isSpectator() && hand == InteractionHand.MAIN_HAND)
                NaiwaPranks.usedBlock(serverPlayer,hit.getBlockPos(),false);
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player,level,hand,pos,direction) -> {
            if (player instanceof ServerPlayer serverPlayer && !player.isSpectator()) NaiwaPranks.attackedBlock(serverPlayer,pos);
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level,player,pos,state,blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) NaiwaPranks.breakingBlock(serverPlayer,pos,state);
            return true;
        });
    }

    private static void leftWorld(ServerPlayer player) {
        NaiwaShield.clear(player.getUUID());
        NaiwaGameplay.cleanup(player);
        NaiwaPranks.cancel(player);
    }
}
