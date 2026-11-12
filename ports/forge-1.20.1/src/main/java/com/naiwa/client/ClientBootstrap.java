package com.naiwa.client;

import com.naiwa.Naiwa;
import com.naiwa.NaiwaForgeNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
/** 客户端入口单独隔离，专用服务端只注册实体及同步状态。 */
public final class ClientBootstrap {
    /** 原版举盾姿势、双视角物品渲染及 HUD 都从同步物品组件读取，不由客户端结算格挡。 */
    public static void init(IEventBus bus) {
        var clientEvents = MinecraftForge.EVENT_BUS;
        clientEvents.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) return;
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
        var worldView=new org.joml.Matrix4f();
        clientEvents.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY) NaiwaAscensionVisuals.beginWorldPass();
            if (event.getStage()==RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                // 1.20.1 AFTER_LEVEL 传入的是 GameRenderer 的投影栈；实体阶段才持有真正的相机视图，必须按帧复制。
                worldView.set(event.getPoseStack().last().pose());
                NaiwaScopeTrigger.render(NaiwaForgeNetwork.CHANNEL::sendToServer);
            }
            if (event.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL)
                NaiwaAscensionVisuals.render(event.getCamera(),event.getPartialTick(),
                        worldView,event.getProjectionMatrix());
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
        bus.addListener((RegisterGuiOverlaysEvent event) -> {
            event.registerAboveAll("scope_portrait",(gui,graphics,partial,width,height) -> NaiwaScopeTrigger.portrait(graphics));
            event.registerAboveAll("shield_charge",(gui,graphics,partial,width,height) -> NaiwaShieldHud.render(graphics));
        });
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(Naiwa.ENTITY.get(), NaiwaRenderer::new));
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(Naiwa.DECOY.get(), NaiwaDecoyRenderer::new));
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(Naiwa.GAS_PROJECTILE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new));
        bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> ItemProperties.register(Naiwa.SHIELD.get(),
                new ResourceLocation("blocking"), (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0)));
    }

    public static IClientItemExtensions shieldExtensions() {
        return new IClientItemExtensions() {
            private NaiwaShieldRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new NaiwaShieldRenderer();
                return renderer;
            }
        };
    }

    public static void receiveFlight(double height) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) com.naiwa.NaiwaFlightHeight.apply(player,height);
    }
}
