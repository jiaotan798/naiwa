package com.naiwa.mixin;

import com.naiwa.NaiwaEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge 已更新实体入场标志后，再刷新箱内探头索引。 */
@Mixin(Entity.class)
abstract class NeoForgeEntityLifecycleMixin {
    @Inject(method="onAddedToLevel",at=@At("TAIL"),remap=false)
    private void naiwa$added(CallbackInfo info) {
        if ((Object)this instanceof NaiwaEntity frog) frog.updateChestPeeker(true);
    }

    @Inject(method="onRemovedFromLevel",at=@At("TAIL"),remap=false)
    private void naiwa$removed(CallbackInfo info) {
        if ((Object)this instanceof NaiwaEntity frog) frog.updateChestPeeker(false);
    }
}
