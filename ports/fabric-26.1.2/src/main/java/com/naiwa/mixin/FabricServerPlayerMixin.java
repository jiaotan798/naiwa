package com.naiwa.mixin;

import com.naiwa.NaiwaPranks;
import com.naiwa.PlayerDataAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 玩家持久状态随原版存档保存；仅迁移 NeoForge 已写入的奶蛙字段。 */
@Mixin(ServerPlayer.class)
abstract class FabricServerPlayerMixin implements PlayerDataAccess {
    @Unique private CompoundTag naiwa$state = new CompoundTag();

    @Override public CompoundTag naiwa$data() {return naiwa$state;}

    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void naiwa$load(net.minecraft.world.level.storage.ValueInput tag, CallbackInfo info) {
        var saved = tag.read("naiwa:data",CompoundTag.CODEC);
        if (saved.isPresent()) {
            naiwa$state = saved.get().copy();
            return;
        }
        naiwa$state = new CompoundTag();
        var legacy = tag.read("NeoForgeData",CompoundTag.CODEC).orElseGet(CompoundTag::new);
        for (String key : legacy.keySet()) {
            if (key.startsWith("naiwa.")) naiwa$state.put(key,legacy.get(key).copy());
        }
    }

    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void naiwa$save(net.minecraft.world.level.storage.ValueOutput tag, CallbackInfo info) {
        // 保留空标签，避免迁移后的已消费标志因旧数据重新加载。
        tag.store("naiwa:data",CompoundTag.CODEC,naiwa$state.copy());
    }

    @Inject(method="doCloseContainer",at=@At("HEAD"))
    private void naiwa$closed(CallbackInfo info) {
        var player = (ServerPlayer)(Object)this;
        NaiwaPranks.closedMenu(player,player.containerMenu);
    }
}
