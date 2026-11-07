package com.naiwa.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;

/** 独立验收实例仅在资源重载完成后执行；测试类不随玩家安装包打包。 */
public final class NaiwaClientSmoke {
    private static boolean checked;
    private NaiwaClientSmoke() {}

    public static void tick(Minecraft client) {
        client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        if (checked || client.screen==null || client.getOverlay()!=null) return;
        long window = client.getWindow().getWindow();
        if (GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_VISIBLE)!=GLFW.GLFW_FALSE
                || GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_FOCUSED)!=GLFW.GLFW_FALSE || client.mouseHandler.isMouseGrabbed())
            throw new IllegalStateException("后台窗口违反隐藏与输入约束");
        NaiwaAscensionBloom.render(0);
        NaiwaAscensionBloom.close();
        checked = true;
        System.out.println("NAIWA_PORT_CLIENT_SMOKE_OK");
        client.stop();
    }
}
