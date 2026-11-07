package com.naiwa;

import com.naiwa.client.ClientBootstrap;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 集中注册实体、物品与同步组件；玩法结算由服务端事件入口负责。 */
@Mod(Naiwa.ID)
public final class Naiwa {
    public static final String ID = "naiwa";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ID);
    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, ID);
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,net.minecraft.core.particles.SimpleParticleType> GAS_CLOUD =
            PARTICLES.register("gas_cloud",()->new net.minecraft.core.particles.SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,net.minecraft.core.particles.SimpleParticleType> GAS_FACE =
            PARTICLES.register("gas_face",()->new net.minecraft.core.particles.SimpleParticleType(false));
    public static final DeferredHolder<MobEffect, MobEffect> LAUGHING = EFFECTS.register("laughing",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0xEACD36) {});
    public static final DeferredHolder<MobEffect, MobEffect> ASCENDING = EFFECTS.register("ascending",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFFF0A0) {});
    public static final DeferredHolder<EntityType<?>, EntityType<LaughGasProjectile>> GAS_PROJECTILE = ENTITIES.register("laugh_gas",
            () -> EntityType.Builder.<LaughGasProjectile>of(LaughGasProjectile::new, MobCategory.MISC).sized(.25F,.25F)
                    .clientTrackingRange(4).updateInterval(10).build(NaiwaContent.entityKey("laugh_gas")));
    public static final DeferredHolder<Item, LaughGasItem> GAS = ITEMS.register("laugh_gas_bottle",
            () -> new LaughGasItem(NaiwaContent.properties("laugh_gas_bottle").stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)));
    public static final DeferredHolder<Item, Item> TOTEM = ITEMS.register("naiwa_totem", () -> new Item(NaiwaContent.properties("naiwa_totem").stacksTo(1)));
    public static final DeferredHolder<EntityType<?>, EntityType<NaiwaEntity>> ENTITY = ENTITIES.register("naiwa",
            () -> EntityType.Builder.of(NaiwaEntity::new, MobCategory.CREATURE).sized(.9F, 2F)
                    .clientTrackingRange(10).updateInterval(2).build(NaiwaContent.entityKey("naiwa")));
    public static final DeferredHolder<EntityType<?>, EntityType<NaiwaDecoy>> DECOY = ENTITIES.register("diamond_decoy",
            () -> EntityType.Builder.<NaiwaDecoy>of(NaiwaDecoy::new, MobCategory.MISC).sized(.5F,.5F)
                    .clientTrackingRange(8).updateInterval(1).build(NaiwaContent.entityKey("diamond_decoy")));
    public static final DeferredHolder<Item, Item> EGG = ITEMS.register("naiwa_spawn_egg",
            () -> new SpawnEggItem(NaiwaContent.properties("naiwa_spawn_egg").spawnEgg(ENTITY.get())));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUGH = SOUNDS.register("laugh",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ID,"laugh")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_LAUGH = SOUNDS.register("shield_laugh",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ID,"shield_laugh")));
    public static final DeferredHolder<Item, NaiwaShield> SHIELD = ITEMS.register("naiwa_shield",
            () -> new NaiwaShield(NaiwaContent.shield()));
    // 进度随物品存档、转交；短暂的探头时刻只同步，不写入存档，避免重进世界补播。
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGE = COMPONENTS.register("shield_charge",
            () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 2)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> LAUGH_START = COMPONENTS.register("shield_laugh_start",
            () -> DataComponentType.<Long>builder().networkSynchronized(ByteBufCodecs.VAR_LONG).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("naiwa",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.naiwa"))
                    .icon(() -> new ItemStack(EGG.get())).displayItems((parameters, output) -> {output.accept(EGG.get()); output.accept(SHIELD.get()); output.accept(GAS.get()); output.accept(TOTEM.get());}).build());

    public Naiwa(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, NaiwaConfig.SPEC);
        ENTITIES.register(bus); ITEMS.register(bus); COMPONENTS.register(bus); SOUNDS.register(bus); TABS.register(bus); EFFECTS.register(bus);PARTICLES.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) -> event.put(ENTITY.get(), NaiwaEntity.attributes().build()));
        bus.addListener((RegisterSpawnPlacementsEvent event) -> event.register(ENTITY.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)
                        && level.getRawBrightness(pos, 0) > 8, RegisterSpawnPlacementsEvent.Operation.REPLACE));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> NaiwaCommands.register(event.getDispatcher()));
        NaiwaNeoForgeGameplay.registerShieldEvents();
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> NaiwaEncounters.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> NaiwaEncounters.stopped());
        NaiwaNeoForgeGameplay.registerEvents();
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event) -> {
            if (event.getEntity() instanceof ServerPlayer viewer) NaiwaEffectSync.startedTracking(viewer,event.getTarget());
        });
        NaiwaNeoForgePranks.registerEvents();
        bus.addListener((RegisterPayloadHandlersEvent event) -> {
            event.registrar("2").playToServer(NaiwaScopeRequest.TYPE, NaiwaScopeRequest.CODEC, (request, context) ->
                    context.enqueueWork(() -> {if(context.player() instanceof ServerPlayer player) request.apply(player);}));
            event.registrar("1").playToClient(NaiwaFlightHeight.TYPE, NaiwaFlightHeight.CODEC, (update, context) ->
                    context.enqueueWork(() -> NaiwaFlightHeight.apply(context.player(),update.height())));
        });
        // 专用服务端不加载任何 Minecraft 客户端或渲染类。
        if (FMLEnvironment.getDist() == Dist.CLIENT) ClientBootstrap.init(bus, container);
    }
}
