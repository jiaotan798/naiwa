package com.naiwa.mixin;

import com.mojang.blaze3d.platform.Window;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 在实际 GLFW 窗口创建前关闭可见性、焦点及自动聚焦。 */
@Mixin(Window.class)
abstract class PortWindowMixin {
    @WrapOperation(method="<init>",remap=false,at=@At(value="INVOKE",target="Lnet/minecraftforge/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"))
    private long naiwa$hide(IntSupplier width,IntSupplier height,Supplier<String> title,LongSupplier window,Operation<Long> original) {
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
        return original.call(width,height,title,window);
    }
}
