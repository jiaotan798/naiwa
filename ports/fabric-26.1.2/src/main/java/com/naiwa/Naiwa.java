package com.naiwa;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.level.block.Blocks;
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
                    .clientTrackingRange(4).updateInterval(10).build(NaiwaContent.entityKey("laugh_gas")));
    public static final Supplier<EntityType<NaiwaEntity>> ENTITY = register(BuiltInRegistries.ENTITY_TYPE, "naiwa",
            EntityType.Builder.of(NaiwaEntity::new, MobCategory.CREATURE).sized(.9F,2F)
                    .clientTrackingRange(10).updateInterval(2).build(NaiwaContent.entityKey("naiwa")));
    public static final Supplier<EntityType<NaiwaDecoy>> DECOY = register(BuiltInRegistries.ENTITY_TYPE, "diamond_decoy",
            EntityType.Builder.<NaiwaDecoy>of(NaiwaDecoy::new, MobCategory.MISC).sized(.5F,.5F)
                    .clientTrackingRange(8).updateInterval(1).build(NaiwaContent.entityKey("diamond_decoy")));
    public static final Supplier<LaughGasItem> GAS = register(BuiltInRegistries.ITEM, "laugh_gas_bottle",
            new LaughGasItem(NaiwaContent.properties("laugh_gas_bottle").stacksTo(16).craftRemainder(Items.GLASS_BOTTLE)));
    public static final Supplier<Item> TOTEM = register(BuiltInRegistries.ITEM, "naiwa_totem", new Item(NaiwaContent.properties("naiwa_totem").stacksTo(1)));
    public static final Supplier<NaiwaShield> SHIELD = register(BuiltInRegistries.ITEM, "naiwa_shield",
            new NaiwaShield(NaiwaContent.shield()));
    public static final Supplier<Item> EGG = register(BuiltInRegistries.ITEM, "naiwa_spawn_egg",
            new SpawnEggItem(NaiwaContent.properties("naiwa_spawn_egg").spawnEgg(ENTITY.get())));
    public static final Supplier<SoundEvent> LAUGH = register(BuiltInRegistries.SOUND_EVENT, "laugh", SoundEvent.createVariableRangeEvent(id("laugh")));
    public static final Supplier<SoundEvent> SHIELD_LAUGH = register(BuiltInRegistries.SOUND_EVENT, "shield_laugh", SoundEvent.createVariableRangeEvent(id("shield_laugh")));
    // 格挡进度落盘，短暂的探头起点只同步，重进存档时不会补播。
    public static final Supplier<DataComponentType<Integer>> CHARGE = register(BuiltInRegistries.DATA_COMPONENT_TYPE, "shield_charge",
            DataComponentType.<Integer>builder().persistent(Codec.intRange(0,2)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<DataComponentType<Long>> LAUGH_START = register(BuiltInRegistries.DATA_COMPONENT_TYPE, "shield_laugh_start",
            DataComponentType.<Long>builder().networkSynchronized(ByteBufCodecs.VAR_LONG).build());

    public static Identifier id(String path) {return Identifier.fromNamespaceAndPath(ID,path);}

    private static <B,T extends B> Supplier<T> register(Registry<B> registry, String name, T value) {
        Registry.register(registry,id(name),value);
        return () -> value;
    }

    private static Holder<MobEffect> effect(String name, MobEffectCategory category, int color) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,id(name),new MobEffect(category,color) {});
    }

    @Override public void onInitialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,id("naiwa"),
                FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.naiwa")).icon(() -> new ItemStack(EGG.get()))
                        .displayItems((params,output) -> {output.accept(EGG.get()); output.accept(SHIELD.get()); output.accept(GAS.get()); output.accept(TOTEM.get());}).build());
        FabricDefaultAttributeRegistry.register(ENTITY.get(),NaiwaEntity.attributes());
        // 仅登记放置校验。群系生成池不加入奶蛙，整蛊独立创建临时演员。
        SpawnPlacements.register(ENTITY.get(),SpawnPlacementTypes.ON_GROUND,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type,level,reason,pos,random) -> level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) && level.getRawBrightness(pos,0)>8);
        NaiwaFabricEvents.register();
    }
}
