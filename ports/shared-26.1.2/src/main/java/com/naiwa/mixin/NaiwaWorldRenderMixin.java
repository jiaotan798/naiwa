package com.naiwa.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.naiwa.client.NaiwaAscensionVisuals;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 世界帧图完成后再叠加圣光，使用当帧的相机、投影和资源池。 */
@Mixin(LevelRenderer.class)
abstract class NaiwaWorldRenderMixin {
    @Inject(method="renderLevel",at=@At("RETURN"))
    private void naiwa$worldEffects(GraphicsResourceAllocator allocator,DeltaTracker delta,boolean outline,CameraRenderState camera,
                                    Matrix4fc view,GpuBufferSlice terrainFog,Vector4f fogColor,boolean sky,
                                    ChunkSectionsToRender sections,CallbackInfo info) {
        NaiwaAscensionVisuals.render(camera,delta.getGameTimeDeltaPartialTick(false),view,allocator);
    }
}
