package com.naiwa;

import com.naiwa.client.ClientBootstrap;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.core.component.DataComponents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

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
    public static final DeferredHolder<MobEffect, MobEffect> LAUGHING = EFFECTS.register("laughing",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0xEACD36) {});
    public static final DeferredHolder<MobEffect, MobEffect> ASCENDING = EFFECTS.register("ascending",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFFF0A0) {});
    public static final DeferredHolder<EntityType<?>, EntityType<LaughGasProjectile>> GAS_PROJECTILE = ENTITIES.register("laugh_gas",
            () -> EntityType.Builder.<LaughGasProjectile>of(LaughGasProjectile::new, MobCategory.MISC).sized(.25F,.25F)
                    .clientTrackingRange(4).updateInterval(10).build("naiwa:laugh_gas"));
    public static final DeferredHolder<Item, LaughGasItem> GAS = ITEMS.register("laugh_gas_bottle",
            () -> new LaughGasItem(new Item.Properties().stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)));
    public static final DeferredHolder<Item, Item> TOTEM = ITEMS.register("naiwa_totem", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<EntityType<?>, EntityType<NaiwaEntity>> ENTITY = ENTITIES.register("naiwa",
            () -> EntityType.Builder.of(NaiwaEntity::new, MobCategory.CREATURE).sized(.9F, 2F)
                    .clientTrackingRange(10).updateInterval(2).build("naiwa:naiwa"));
    public static final DeferredHolder<EntityType<?>, EntityType<NaiwaDecoy>> DECOY = ENTITIES.register("diamond_decoy",
            () -> EntityType.Builder.<NaiwaDecoy>of(NaiwaDecoy::new, MobCategory.MISC).sized(.5F,.5F)
                    .clientTrackingRange(8).updateInterval(1).build("naiwa:diamond_decoy"));
    public static final DeferredHolder<Item, Item> EGG = ITEMS.register("naiwa_spawn_egg",
            () -> new DeferredSpawnEggItem(ENTITY, 0xF2CD25, 0xEFE5C5, new Item.Properties()));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUGH = SOUNDS.register("laugh",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ID,"laugh")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_LAUGH = SOUNDS.register("shield_laugh",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ID,"shield_laugh")));
    public static final DeferredHolder<Item, NaiwaShield> SHIELD = ITEMS.register("naiwa_shield",
            () -> new NaiwaShield(new Item.Properties().durability(336).component(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)));
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
        ENTITIES.register(bus); ITEMS.register(bus); COMPONENTS.register(bus); SOUNDS.register(bus); TABS.register(bus); EFFECTS.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) -> event.put(ENTITY.get(), NaiwaEntity.attributes().build()));
        bus.addListener((RegisterSpawnPlacementsEvent event) -> event.register(ENTITY.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)
                        && level.getRawBrightness(pos, 0) > 8, RegisterSpawnPlacementsEvent.Operation.REPLACE));
        NeoForge.EVENT_BUS.addListener(NaiwaCommands::register);
        NaiwaShield.registerEvents();
        NaiwaEncounters.registerEvents();
        NaiwaGameplay.registerEvents();
        NaiwaEffectSync.registerEvents();
        NaiwaPranks.registerEvents();
        bus.addListener(NaiwaScopeRequest::register);
        bus.addListener(NaiwaFlightHeight::register);
        // 专用服务端不加载任何 Minecraft 客户端或渲染类。
        if (FMLEnvironment.dist == Dist.CLIENT) ClientBootstrap.init(bus, container);
    }
}
