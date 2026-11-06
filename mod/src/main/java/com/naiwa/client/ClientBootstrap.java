package com.naiwa.client;

import com.naiwa.Naiwa;
import com.naiwa.NaiwaShield;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** 客户端入口单独隔离，专用服务端只注册实体及同步状态。 */
public final class ClientBootstrap {
    /** 原版举盾姿势、双视角物品渲染及 HUD 都从同步物品组件读取，不由客户端结算格挡。 */
    public static void init(IEventBus bus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NaiwaPlayerRenderer.register();
        NaiwaTotemCamera.register();
        NaiwaAscensionVisuals.register();
        bus.addListener(NaiwaAscensionVisuals::shaders);
        bus.addListener(NaiwaGasParticles::register);
        NaiwaScopeTrigger.register();
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
        bus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("naiwa", "shield_charge"), (graphics, delta) -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
            // 双手都有奶蛙盾时优先显示当前正在格挡的那一面，收盾后才回到主手优先。
            var stack = mc.player.getUseItem().is(Naiwa.SHIELD.get()) ? mc.player.getUseItem()
                    : mc.player.getMainHandItem().is(Naiwa.SHIELD.get()) ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
            if (!stack.is(Naiwa.SHIELD.get())) return;
            double elapsed = NaiwaShield.elapsed(stack, mc.level.getGameTime());
            int charge = elapsed >= 0 && elapsed < 6 ? 3 : NaiwaShield.charge(stack);
            int x = graphics.guiWidth()/2 - 45, y = graphics.guiHeight()-64;
            graphics.fill(x-1,y-1,x+91,y+7,0xC020241B);
            graphics.fill(x,y,x+90*charge/3,y+6,0xFFF2CD25);
            graphics.drawCenteredString(mc.font, Component.translatable("hud.naiwa.shield",charge), x+45,y-12,0xFFF2E6AA);
        }));
        if (Boolean.getBoolean("naiwa.acceptance")) AcceptanceProbe.register();
    }
}
