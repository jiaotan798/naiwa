package com.naiwa;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.levelgen.Heightmap;

/** Fabric 注册入口，沿用原版奶蛙的资源 ID、属性和同步组件。 */
public final class Naiwa implements ModInitializer {
    public static final String ID = "naiwa";
    public static final Holder<MobEffect> LAUGHING = effect("laughing", MobEffectCategory.HARMFUL, 0xEACD36);
    public static final Holder<MobEffect> ASCENDING = effect("ascending", MobEffectCategory.BENEFICIAL, 0xFFF0A0);
    public static final Supplier<SimpleParticleType> GAS_CLOUD = register(BuiltInRegistries.PARTICLE_TYPE, "gas_cloud", FabricParticleTypes.simple());
    public static final Supplier<SimpleParticleType> GAS_FACE = register(BuiltInRegistries.PARTICLE_TYPE, "gas_face", FabricParticleTypes.simple());
    public static final Supplier<EntityType<LaughGasProjectile>> GAS_PROJECTILE = register(BuiltInRegistries.ENTITY_TYPE, "laugh_gas",
            EntityType.Builder.<LaughGasProjectile>of(LaughGasProjectile::new, MobCategory.MISC).sized(.25F,.25F)
                    .clientTrackingRange(4).updateInterval(10).build("naiwa:laugh_gas"));
    public static final Supplier<EntityType<NaiwaEntity>> ENTITY = register(BuiltInRegistries.ENTITY_TYPE, "naiwa",
            EntityType.Builder.of(NaiwaEntity::new, MobCategory.CREATURE).sized(.9F,2F)
                    .clientTrackingRange(10).updateInterval(2).build("naiwa:naiwa"));
    public static final Supplier<EntityType<NaiwaDecoy>> DECOY = register(BuiltInRegistries.ENTITY_TYPE, "diamond_decoy",
            EntityType.Builder.<NaiwaDecoy>of(NaiwaDecoy::new, MobCategory.MISC).sized(.5F,.5F)
                    .clientTrackingRange(8).updateInterval(1).build("naiwa:diamond_decoy"));
    public static final Supplier<LaughGasItem> GAS = register(BuiltInRegistries.ITEM, "laugh_gas_bottle",
            new LaughGasItem(new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE)));
    public static final Supplier<Item> TOTEM = register(BuiltInRegistries.ITEM, "naiwa_totem", new Item(new Item.Properties().stacksTo(1)));
    public static final Supplier<NaiwaShield> SHIELD = register(BuiltInRegistries.ITEM, "naiwa_shield",
            new NaiwaShield(new Item.Properties().durability(336).component(DataComponents.BANNER_PATTERNS,BannerPatternLayers.EMPTY)));
    public static final Supplier<Item> EGG = register(BuiltInRegistries.ITEM, "naiwa_spawn_egg",
            new SpawnEggItem(ENTITY.get(),0xF2CD25,0xEFE5C5,new Item.Properties()));
    public static final Supplier<SoundEvent> LAUGH = register(BuiltInRegistries.SOUND_EVENT, "laugh", SoundEvent.createVariableRangeEvent(id("laugh")));
    public static final Supplier<SoundEvent> SHIELD_LAUGH = register(BuiltInRegistries.SOUND_EVENT, "shield_laugh", SoundEvent.createVariableRangeEvent(id("shield_laugh")));
    // 格挡进度落盘，短暂的探头起点只同步，重进存档时不会补播。
    public static final Supplier<DataComponentType<Integer>> CHARGE = register(BuiltInRegistries.DATA_COMPONENT_TYPE, "shield_charge",
            DataComponentType.<Integer>builder().persistent(Codec.intRange(0,2)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<DataComponentType<Long>> LAUGH_START = register(BuiltInRegistries.DATA_COMPONENT_TYPE, "shield_laugh_start",
            DataComponentType.<Long>builder().networkSynchronized(ByteBufCodecs.VAR_LONG).build());

    public static ResourceLocation id(String path) {return ResourceLocation.fromNamespaceAndPath(ID,path);}

    private static <B,T extends B> Supplier<T> register(Registry<B> registry, String name, T value) {
        Registry.register(registry,id(name),value);
        return () -> value;
    }

    private static Holder<MobEffect> effect(String name, MobEffectCategory category, int color) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,id(name),new MobEffect(category,color) {});
    }

    @Override public void onInitialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,id("naiwa"),
                FabricItemGroup.builder().title(Component.translatable("itemGroup.naiwa")).icon(() -> new ItemStack(EGG.get()))
                        .displayItems((params,output) -> {output.accept(EGG.get()); output.accept(SHIELD.get()); output.accept(GAS.get()); output.accept(TOTEM.get());}).build());
        FabricDefaultAttributeRegistry.register(ENTITY.get(),NaiwaEntity.attributes());
        SpawnPlacements.register(ENTITY.get(),SpawnPlacementTypes.ON_GROUND,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type,level,reason,pos,random) -> level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) && level.getRawBrightness(pos,0)>8);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PLAINS,Biomes.SUNFLOWER_PLAINS,Biomes.FOREST,Biomes.BIRCH_FOREST,Biomes.SWAMP),
                MobCategory.CREATURE,ENTITY.get(),2,1,1);
        NaiwaFabricEvents.register();
    }
}
