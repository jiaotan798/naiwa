package com.naiwa;

import com.naiwa.client.ClientBootstrap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
/** 集中注册实体、物品与同步组件；玩法结算由服务端事件入口负责。 */
@Mod(Naiwa.ID)
public final class Naiwa {
    public static final String ID = "naiwa";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ID);
    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, ID);
    public static final RegistryObject<net.minecraft.core.particles.SimpleParticleType> GAS_CLOUD =
            PARTICLES.register("gas_cloud",()->new net.minecraft.core.particles.SimpleParticleType(false));
    public static final RegistryObject<net.minecraft.core.particles.SimpleParticleType> GAS_FACE =
            PARTICLES.register("gas_face",()->new net.minecraft.core.particles.SimpleParticleType(false));
    public static final MobEffect LAUGHING = new MobEffect(MobEffectCategory.HARMFUL,0xEACD36) {};
    public static final MobEffect ASCENDING = new MobEffect(MobEffectCategory.BENEFICIAL,0xFFF0A0) {};
    static {
        EFFECTS.register("laughing",() -> LAUGHING);
        EFFECTS.register("ascending",() -> ASCENDING);
    }
    public static final RegistryObject<EntityType<LaughGasProjectile>> GAS_PROJECTILE = ENTITIES.register("laugh_gas",
            () -> EntityType.Builder.<LaughGasProjectile>of(LaughGasProjectile::new, MobCategory.MISC).sized(.25F,.25F)
                    .clientTrackingRange(4).updateInterval(10).build("naiwa:laugh_gas"));
    public static final RegistryObject<LaughGasItem> GAS = ITEMS.register("laugh_gas_bottle",
            () -> new LaughGasItem(new Item.Properties().stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)));
    public static final RegistryObject<Item> TOTEM = ITEMS.register("naiwa_totem", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<EntityType<NaiwaEntity>> ENTITY = ENTITIES.register("naiwa",
            () -> EntityType.Builder.of(NaiwaEntity::new, MobCategory.CREATURE).sized(.9F, 2F)
                    .clientTrackingRange(10).updateInterval(2).build("naiwa:naiwa"));
    public static final RegistryObject<EntityType<NaiwaDecoy>> DECOY = ENTITIES.register("diamond_decoy",
            () -> EntityType.Builder.<NaiwaDecoy>of(NaiwaDecoy::new, MobCategory.MISC).sized(.5F,.5F)
                    .clientTrackingRange(8).updateInterval(1).build("naiwa:diamond_decoy"));
    public static final RegistryObject<Item> EGG = ITEMS.register("naiwa_spawn_egg",
            () -> new ForgeSpawnEggItem(ENTITY, 0xF2CD25, 0xEFE5C5, new Item.Properties()));
    public static final RegistryObject<SoundEvent> LAUGH = SOUNDS.register("laugh",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID,"laugh")));
    public static final RegistryObject<SoundEvent> SHIELD_LAUGH = SOUNDS.register("shield_laugh",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID,"shield_laugh")));
    public static final RegistryObject<NaiwaShield> SHIELD = ITEMS.register("naiwa_shield",
            () -> new ForgeShield(new Item.Properties().durability(336)));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("naiwa",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.naiwa"))
                    .icon(() -> new ItemStack(EGG.get())).displayItems((parameters, output) -> {output.accept(EGG.get()); output.accept(SHIELD.get()); output.accept(GAS.get()); output.accept(TOTEM.get());}).build());

    public Naiwa(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.SERVER,NaiwaConfig.SPEC);
        ENTITIES.register(bus); ITEMS.register(bus); SOUNDS.register(bus); TABS.register(bus); EFFECTS.register(bus); PARTICLES.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) -> event.put(ENTITY.get(),NaiwaEntity.attributes().build()));
        bus.addListener((SpawnPlacementRegisterEvent event) -> event.register(ENTITY.get(),SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type,level,reason,pos,random) -> level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) && level.getRawBrightness(pos,0)>8,
                SpawnPlacementRegisterEvent.Operation.REPLACE));
        NaiwaForgeEvents.register();
        NaiwaForgeNetwork.register();
        if (FMLEnvironment.dist == Dist.CLIENT) ClientBootstrap.init(bus);
    }

    private static final class ForgeShield extends NaiwaShield {
        ForgeShield(Properties properties) {super(properties);}
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> register) {
            register.accept(ClientBootstrap.shieldExtensions());
        }
    }
}
