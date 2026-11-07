package com.naiwa.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;

/** 隐藏窗口中验证客户端和飞升泛光能初始化；测试类只随验收入口运行。 */
public final class FabricClientSmoke implements ClientModInitializer {
    private boolean checked;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("naiwa.fabric.smoke")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
            // 首次启动可能先进入无障碍设置页，资源加载完成后即可验收。
            if (checked || client.screen==null || client.getOverlay()!=null) return;
            long window = client.getWindow().getWindow();
            if (GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_VISIBLE)!=GLFW.GLFW_FALSE
                    || GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_FOCUSED)!=GLFW.GLFW_FALSE || client.mouseHandler.isMouseGrabbed())
                throw new IllegalStateException("后台验收窗口违反隐藏与输入约束");
            NaiwaAscensionBloom.render(0);
            NaiwaAscensionBloom.close();
            checked = true;
            System.out.println("NAIWA_FABRIC_CLIENT_SMOKE_OK");
            client.stop();
        });
    }
}
