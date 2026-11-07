package com.naiwa.mixin;

import com.naiwa.NaiwaNeoForgePranks;
import net.minecraft.world.entity.projectile.FishingHook;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 在最终事件取消检查处原子结算：演员出场失败则原版继续发渔获，不会先吞奖励再等待下一 tick。 */
@Mixin(FishingHook.class)
abstract class FishingHookMixin {
    @Redirect(method="retrieve", at=@At(value="INVOKE", target="Lnet/neoforged/neoforge/event/entity/player/ItemFishedEvent;isCanceled()Z", remap=false))
    private boolean naiwa$stolenCatch(ItemFishedEvent event) {
        // NeoForge 的 getDrops() 是副本，清空它拦不住原版掉落；复用原版取消分支，保留收漂和 getRodDamage()。
        // 此时所有监听器已执行，且只会在真正咬钩后的收竿进入；不能在等待/咬钩动画期间生成演员。
        return event.isCanceled() || NaiwaNeoForgePranks.stealFishingCatch(event);
    }
}
