package com.naiwa.client;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

/** 世界完成后的自有绘制通道：着色器应用完毕后才绑定主画布，保留原世界深度。 */
public final class NaiwaWorldShader extends ShaderInstance {
    public NaiwaWorldShader(ResourceProvider resources,ResourceLocation name,VertexFormat format)throws java.io.IOException {
        super(resources,name,format);
    }

    @Override public void apply() {
        super.apply();
        // Iris 的 ShaderInstance.apply 尾部可能 bindDefault；提前绑定会被覆盖，必须在 super 之后提交。
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        // Iris 应用未知实体着色器时会关闭深度写入；身体必须重新写深度，透明圣光则继续只写颜色。
        com.mojang.blaze3d.systems.RenderSystem.depthMask(getName().endsWith("flight_body"));
    }
}
