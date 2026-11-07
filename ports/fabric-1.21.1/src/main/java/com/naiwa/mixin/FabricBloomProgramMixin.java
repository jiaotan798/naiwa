package com.naiwa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 原版后处理按 minecraft 命名空间查找。保留奶蛙泛光的独立资源路径及程序缓存名。 */
@Mixin(EffectInstance.class)
abstract class FabricBloomProgramMixin {
    @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/resources/ResourceLocation;withDefaultNamespace(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation naiwa$bloomJson(String path, Operation<ResourceLocation> original) {
        return naiwa$bloomPath(path,original);
    }

    @WrapOperation(method="getOrCreate",at=@At(value="INVOKE",target="Lnet/minecraft/resources/ResourceLocation;withDefaultNamespace(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    private static ResourceLocation naiwa$bloomSource(String path, Operation<ResourceLocation> original) {
        return naiwa$bloomPath(path,original);
    }

    private static ResourceLocation naiwa$bloomPath(String path, Operation<ResourceLocation> original) {
        String prefix = "shaders/program/naiwa:holy_";
        if (path.startsWith(prefix)) return ResourceLocation.fromNamespaceAndPath("naiwa","shaders/program/holy_"+path.substring(prefix.length()));
        return original.call(path);
    }
}
