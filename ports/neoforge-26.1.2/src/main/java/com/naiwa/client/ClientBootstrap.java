package com.naiwa.client;

import com.naiwa.Naiwa;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 只在物理客户端绑定 NeoForge 注册和渲染事件。 */
public final class ClientBootstrap {
    private ClientBootstrap() {}

    public static void init(IEventBus bus,ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,ConfigurationScreen::new);
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(Naiwa.ENTITY.get(),NaiwaRenderer::new);
            event.registerEntityRenderer(Naiwa.DECOY.get(),NaiwaDecoyRenderer::new);
            event.registerEntityRenderer(Naiwa.GAS_PROJECTILE.get(),ThrownItemRenderer::new);
        });
        bus.addListener((RegisterSpecialModelRendererEvent event) ->
                event.register(Identifier.fromNamespaceAndPath("naiwa","shield"),NaiwaShieldRenderer.Unbaked.CODEC));
        bus.addListener((RegisterParticleProvidersEvent event) -> {
            event.registerSpriteSet(Naiwa.GAS_CLOUD.get(),NaiwaGasParticles::cloud);
            event.registerSpriteSet(Naiwa.GAS_FACE.get(),NaiwaGasParticles::face);
        });
        bus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAboveAll(Identifier.fromNamespaceAndPath("naiwa","hud"),(graphics,delta) -> {
                    NaiwaScopeTrigger.portrait(graphics);
                    NaiwaShieldHud.render(graphics);
                }));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            NaiwaPlayerRenderer.tick();
            NaiwaTotemCamera.tick();
            NaiwaAscensionVisuals.tick();
            NaiwaScopeTrigger.render(ClientPacketDistributor::sendToServer);
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            NaiwaTotemCamera.disconnect();
            NaiwaAscensionVisuals.disconnect();
        });
    }
}
