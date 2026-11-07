package com.naiwa.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.naiwa.NaiwaPranks;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 仅在真正收竿并生成渔获时尝试出场，成功后清空该轮掉落，保留原版浮漂回收和耐久结算。 */
@Mixin(FishingHook.class)
abstract class FabricFishingMixin {
    @ModifyExpressionValue(method="retrieve",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<ItemStack> naiwa$catch(ObjectArrayList<ItemStack> drops) {
        FishingHook hook = (FishingHook)(Object)this;
        if (hook.getPlayerOwner() instanceof ServerPlayer player && NaiwaPranks.stealFishingCatch(player,hook,drops))
            return new ObjectArrayList<>();
        return drops;
    }
}
