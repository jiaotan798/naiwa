package com.naiwa.mixin;

import com.naiwa.client.NaiwaRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
abstract class NaiwaLivingRenderStateMixin implements NaiwaRenderState {
    @Unique private boolean naiwa$laughing;
    @Unique private int naiwa$stage, naiwa$remaining;
    @Unique private float naiwa$partial;

    @Override public void naiwa$setEffects(boolean laughing,int stage,int remaining,float partial) {
        naiwa$laughing=laughing; naiwa$stage=stage; naiwa$remaining=remaining; naiwa$partial=partial;
    }
    @Override public boolean naiwa$laughing() {return naiwa$laughing;}
    @Override public int naiwa$stage() {return naiwa$stage;}
    @Override public int naiwa$remaining() {return naiwa$remaining;}
    @Override public float naiwa$partial() {return naiwa$partial;}
}
