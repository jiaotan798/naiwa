package com.naiwa.qa.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 独立验收包复用已验证的后台窗口策略；正式成品不包含此 Mixin。 */
@Mixin(Window.class)
public abstract class BackgroundWindowMixin {
    @Inject(method="<init>",at=@At(value="INVOKE",target="Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J",remap=false))
    private void naiwa$background(CallbackInfo ci) {
        if(Boolean.getBoolean("naiwa.qa.foreground"))return;
        // 配套启动器禁用早期窗口，必须在真正创建窗口前禁用可见性和焦点，避免一闪而过。
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
        // 沿用本机此前早期窗口提供的 4.6 上下文，保持 Iris 实际测试环境。
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,6);
    }
}
