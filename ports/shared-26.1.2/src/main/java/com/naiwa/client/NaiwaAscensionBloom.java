package com.naiwa.client;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import java.util.Set;

/** 新版后处理由 ShaderManager 随资源包重载管理；本机飞升才执行亮部提取和暖色合成。 */
final class NaiwaAscensionBloom {
    private static final Identifier EFFECT = Identifier.fromNamespaceAndPath("naiwa","holy_bloom");
    private NaiwaAscensionBloom() {}

    static void render(GraphicsResourceAllocator allocator) {
        var client = Minecraft.getInstance();
        var chain = chain();
        if (chain!=null) chain.process(client.getMainRenderTarget(),allocator);
    }

    static PostChain chain() {
        return Minecraft.getInstance().getShaderManager().getPostChain(EFFECT,Set.of(PostChain.MAIN_TARGET_ID));
    }
}
