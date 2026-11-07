package com.naiwa.client;

import com.naiwa.Naiwa;
import com.naiwa.NaiwaEntity;
import com.naiwa.NaiwaFlightHeight;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.core.particles.SimpleParticleType;

/** Fabric 26.1.2 入口。新版队列、着色器与源模型由两个加载器共用。 */
public final class NaiwaFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(Naiwa.ENTITY.get(),NaiwaRenderer::new);
        EntityRendererRegistry.register(Naiwa.DECOY.get(),NaiwaDecoyRenderer::new);
        EntityRendererRegistry.register(Naiwa.GAS_PROJECTILE.get(),ThrownItemRenderer::new);
        SpecialModelRenderers.ID_MAPPER.put(Naiwa.id("shield"),NaiwaShieldRenderer.Unbaked.CODEC);
        ParticleProviderRegistry.getInstance().register(Naiwa.GAS_CLOUD.get(),(ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>)NaiwaGasParticles::cloud);
        ParticleProviderRegistry.getInstance().register(Naiwa.GAS_FACE.get(),(ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>)NaiwaGasParticles::face);
        ClientPlayNetworking.registerGlobalReceiver(NaiwaFlightHeight.TYPE,(update,context) -> NaiwaFlightHeight.apply(context.player(),update.height()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            NaiwaPlayerRenderer.tick();
            NaiwaTotemCamera.tick();
            NaiwaAscensionVisuals.tick();
            NaiwaScopeTrigger.render(ClientPlayNetworking::send);
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
        HudElementRegistry.addLast(Naiwa.id("hud"),(graphics,delta) -> {
            NaiwaScopeTrigger.portrait(graphics);
            NaiwaShieldHud.render(graphics);
        });
    }
}
