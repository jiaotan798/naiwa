package com.naiwa;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/** 两个 26.1.2 加载器共用注册键和新版盾牌格挡组件。 */
final class NaiwaContent {
    private NaiwaContent() {}

    static ResourceKey<EntityType<?>> entityKey(String id) {
        return ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(Naiwa.ID,id));
    }

    static Item.Properties properties(String id) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath(Naiwa.ID,id)));
    }

    /** 格挡延迟、伤害折减、扣耐久和破盾取 26.1.2 原版盾牌规则。 */
    static Item.Properties shield() {
        return properties("naiwa_shield").durability(336)
                .component(DataComponents.BANNER_PATTERNS,BannerPatternLayers.EMPTY)
                .repairable(ItemTags.WOODEN_TOOL_MATERIALS).equippableUnswappable(EquipmentSlot.OFFHAND)
                .delayedComponent(DataComponents.BLOCKS_ATTACKS,context -> new BlocksAttacks(0.25F,1,
                        List.of(new BlocksAttacks.DamageReduction(90,Optional.empty(),0,1)),
                        new BlocksAttacks.ItemDamageFunction(3,1,1),
                        Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                        Optional.of(SoundEvents.SHIELD_BLOCK),Optional.of(SoundEvents.SHIELD_BREAK)))
                .component(DataComponents.BREAK_SOUND,SoundEvents.SHIELD_BREAK);
    }
}
