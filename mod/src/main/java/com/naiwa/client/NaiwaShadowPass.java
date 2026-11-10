package com.naiwa.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** 通过可选的 Iris 公共接口识别实体阴影通道，原版和未提供该接口的加载器沿用主画面规则。 */
final class NaiwaShadowPass {
    private static final MethodHandle CHECK = findShadowCheck();

    static boolean active() {
        if (CHECK == null) return false;
        try {
            return (boolean) CHECK.invokeExact();
        } catch (Throwable error) {
            throw new IllegalStateException("无法读取 Iris 阴影通道状态", error);
        }
    }

    private static MethodHandle findShadowCheck() {
        try {
            var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            var instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }
}
