package com.naiwa.client;

import com.naiwa.Naiwa;
import com.naiwa.NaiwaShield;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 按服务端同步的盾牌进度绘制 HUD；优先显示当前举起的那面盾。 */
final class NaiwaShieldHud {
    private NaiwaShieldHud() {}

    static void render(GuiGraphicsExtractor graphics) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        var stack = mc.player.getUseItem().is(Naiwa.SHIELD.get()) ? mc.player.getUseItem()
                : mc.player.getMainHandItem().is(Naiwa.SHIELD.get()) ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (!stack.is(Naiwa.SHIELD.get())) return;
        double elapsed = NaiwaShield.elapsed(stack,mc.level.getGameTime());
        int charge = elapsed >= 0 && elapsed < 6 ? 3 : NaiwaShield.charge(stack);
        int x = graphics.guiWidth()/2-45, y = graphics.guiHeight()-64;
        graphics.fill(x-1,y-1,x+91,y+7,0xC020241B);
        graphics.fill(x,y,x+90*charge/3,y+6,0xFFF2CD25);
        graphics.centeredText(mc.font,Component.translatable("hud.naiwa.shield",charge),x+45,y-12,0xFFF2E6AA);
    }
}
