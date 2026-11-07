package com.naiwa.mixin;

import com.naiwa.NaiwaPranks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 在最终事件取消检查处原子结算：演员出场失败则原版继续发渔获，不会先吞奖励再等待下一 tick。 */
@Mixin(FishingHook.class)
abstract class ForgeFishingHookMixin {
    @Redirect(method="retrieve", at=@At(value="INVOKE", target="Lnet/minecraftforge/event/entity/player/ItemFishedEvent;isCanceled()Z", remap=false))
    private boolean naiwa$stolenCatch(ItemFishedEvent event) {
        // 沿用 Forge 取消分支完成收漂和耐久结算。
        // 此时所有监听器已执行，且只会在真正咬钩后的收竿进入；不能在等待/咬钩动画期间生成演员。
        return event.isCanceled() || (event.getEntity() instanceof ServerPlayer player
                && NaiwaPranks.stealFishingCatch(player,event.getHookEntity(),event.getDrops()));
    }
}
