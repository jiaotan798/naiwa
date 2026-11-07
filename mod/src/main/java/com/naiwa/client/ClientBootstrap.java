package com.naiwa.client;

import com.naiwa.Naiwa;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** 客户端入口单独隔离，专用服务端只注册实体及同步状态。 */
public final class ClientBootstrap {
    /** 原版举盾姿势、双视角物品渲染及 HUD 都从同步物品组件读取，不由客户端结算格挡。 */
    public static void init(IEventBus bus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        var clientEvents = NeoForge.EVENT_BUS;
        clientEvents.addListener((ClientTickEvent.Post event) -> {
            NaiwaPlayerRenderer.tick();
            NaiwaTotemCamera.tick();
            NaiwaAscensionVisuals.tick();
        });
        clientEvents.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            NaiwaTotemCamera.disconnect();
            NaiwaAscensionVisuals.disconnect();
        });
        clientEvents.addListener((RenderPlayerEvent.Pre event) -> {
            if (NaiwaPlayerRenderer.replacePlayer(event.getEntity(),event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight(),event.getPartialTick())) event.setCanceled(true);
        });
        clientEvents.addListener((RenderHandEvent event) -> {if (NaiwaPlayerRenderer.hidesHands()) event.setCanceled(true);});
        clientEvents.addListener((ViewportEvent.ComputeFogColor event) -> {
            if (NaiwaAscensionVisuals.active()) {event.setRed(.012F);event.setGreen(.016F);event.setBlue(.035F);}
        });
        clientEvents.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY) NaiwaAscensionVisuals.beginWorldPass();
            if (event.getStage()==RenderLevelStageEvent.Stage.AFTER_ENTITIES) NaiwaScopeTrigger.render(PacketDistributor::sendToServer);
            if (event.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL)
                NaiwaAscensionVisuals.render(event.getCamera(),event.getPartialTick().getGameTimeDeltaPartialTick(false),
                        event.getModelViewMatrix(),event.getProjectionMatrix());
        });
        bus.addListener((RegisterShadersEvent event) -> {
            try {NaiwaAscensionVisuals.shaders((id,format,loaded) ->
                    event.registerShader(new NaiwaWorldShader(event.getResourceProvider(),id,format),loaded));}
            catch(java.io.IOException failure){throw new java.io.UncheckedIOException("无法加载奶蛙圣光着色器",failure);}
        });
        bus.addListener((RegisterParticleProvidersEvent event) -> {
            event.registerSpriteSet(Naiwa.GAS_CLOUD.get(),NaiwaGasParticles::cloud);
            event.registerSpriteSet(Naiwa.GAS_FACE.get(),NaiwaGasParticles::face);
        });
        bus.addListener((RegisterGuiLayersEvent event)->event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("naiwa","scope_portrait"),
                (graphics,delta)->NaiwaScopeTrigger.portrait(graphics)));
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(Naiwa.ENTITY.get(), NaiwaRenderer::new));
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(Naiwa.DECOY.get(), NaiwaDecoyRenderer::new));
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(Naiwa.GAS_PROJECTILE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new));
        bus.addListener((RegisterClientExtensionsEvent event) -> event.registerItem(new IClientItemExtensions() {
            private NaiwaShieldRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new NaiwaShieldRenderer();
                return renderer;
            }
        }, Naiwa.SHIELD.get()));
        bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> ItemProperties.register(Naiwa.SHIELD.get(),
                ResourceLocation.withDefaultNamespace("blocking"), (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0)));
        bus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("naiwa", "shield_charge"),
                (graphics,delta) -> NaiwaShieldHud.render(graphics)));
        if (Boolean.getBoolean("naiwa.acceptance")) AcceptanceProbe.register();
    }
}
