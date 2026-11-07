package com.naiwa.client;

import com.naiwa.Naiwa;
import com.naiwa.NaiwaEntity;
import com.naiwa.NaiwaFlightHeight;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

/** 注册 Fabric 客户端入口；模型、圣光、笑气粒子和 HUD 复用同一份绘制代码。 */
public final class NaiwaFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(Naiwa.ENTITY.get(),NaiwaRenderer::new);
        EntityRendererRegistry.register(Naiwa.DECOY.get(),NaiwaDecoyRenderer::new);
        EntityRendererRegistry.register(Naiwa.GAS_PROJECTILE.get(),ThrownItemRenderer::new);
        ParticleFactoryRegistry.getInstance().register(Naiwa.GAS_CLOUD.get(),(ParticleFactoryRegistry.PendingParticleFactory<net.minecraft.core.particles.SimpleParticleType>)NaiwaGasParticles::cloud);
        ParticleFactoryRegistry.getInstance().register(Naiwa.GAS_FACE.get(),(ParticleFactoryRegistry.PendingParticleFactory<net.minecraft.core.particles.SimpleParticleType>)NaiwaGasParticles::face);
        CoreShaderRegistrationCallback.EVENT.register(registrar -> NaiwaAscensionVisuals.shaders(registrar::register));
        ItemProperties.register(Naiwa.SHIELD.get(),ResourceLocation.withDefaultNamespace("blocking"),
                (stack,level,entity,seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0);
        BuiltinItemRendererRegistry.INSTANCE.register(Naiwa.SHIELD.get(),new BuiltinItemRendererRegistry.DynamicItemRenderer() {
            private NaiwaShieldRenderer renderer;
            @Override public void render(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.ItemDisplayContext context,
                                         com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
                if (renderer == null) renderer = new NaiwaShieldRenderer();
                renderer.renderByItem(stack,context,pose,buffers,light,overlay);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(NaiwaFlightHeight.TYPE,(update,context) -> NaiwaFlightHeight.apply(context.player(),update.height()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            NaiwaPlayerRenderer.tick();
            NaiwaTotemCamera.tick();
            NaiwaAscensionVisuals.tick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> {
            NaiwaTotemCamera.disconnect();
            NaiwaAscensionVisuals.disconnect();
        });
        ClientEntityEvents.ENTITY_LOAD.register((entity,level) -> {
            if (entity instanceof NaiwaEntity frog) frog.updateChestPeeker(true);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity,level) -> {
            if (entity instanceof NaiwaEntity frog) frog.updateChestPeeker(false);
        });
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> NaiwaAscensionVisuals.beginWorldPass());
        WorldRenderEvents.AFTER_ENTITIES.register(context -> NaiwaScopeTrigger.render(ClientPlayNetworking::send));
        WorldRenderEvents.END.register(context -> NaiwaAscensionVisuals.render(context.camera(),
                context.tickCounter().getGameTimeDeltaPartialTick(false),context.positionMatrix(),context.projectionMatrix()));
        HudRenderCallback.EVENT.register((graphics,tick) -> {
            NaiwaScopeTrigger.portrait(graphics);
            NaiwaShieldHud.render(graphics);
        });
    }
}
