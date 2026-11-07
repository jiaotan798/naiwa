package com.naiwa.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

/** 独立的暖色亮部提取、双向模糊与夜景合成；不占用 GameRenderer 的其他后处理效果。 */
final class NaiwaAscensionBloom {
    private static PostChain chain;
    private static int width,height;
    private NaiwaAscensionBloom() {}

    /** 只在本机飞升时分配；缩放时同步半分辨率提取和四分之一分辨率模糊目标。 */
    static void render(float partial) {
        var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
        try {
            if(chain==null)chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),main,
                    new ResourceLocation("naiwa","shaders/post/holy_bloom.json"));
            if(width!=main.width||height!=main.height) {
                width=main.width;height=main.height;chain.resize(width,height);
                chain.getTempTarget("extract").resize(Math.max(1,width/2),Math.max(1,height/2),Minecraft.ON_OSX);
                for(String name:new String[]{"blur_h","blur_v"})
                    chain.getTempTarget(name).resize(Math.max(1,width/4),Math.max(1,height/4),Minecraft.ON_OSX);
                // 1.20.1 的后处理配置不识别 use_linear_filter，直接给自有纹理设定线性采样。
                for(String name:new String[]{"scene","extract","blur_h","blur_v"})
                    chain.getTempTarget(name).setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
            }
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
            chain.process(partial);
            // 合成先写独立目标，再仅拷颜色回主画布；不清空世界深度，也不读写同一张纹理。
            // 不经过另一个 ShaderInstance，防止原版 blit 着色器也触发光影的画布重绑定。
            var result=chain.getTempTarget("composite");
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,result.frameBufferId);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER,main.frameBufferId);
            com.mojang.blaze3d.platform.GlStateManager._glBlitFrameBuffer(0,0,width,height,0,0,width,height,org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT,org.lwjgl.opengl.GL11.GL_NEAREST);
        } catch(java.io.IOException error) {
            close();throw new java.io.UncheckedIOException("无法加载奶蛙飞升泛光",error);
        } finally {
            main.bindWrite(true);RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.defaultBlendFunc();
        }
    }

    /** 资源重载、世界替换和效果结束均归还自有帧缓冲，不关闭其他模组的处理链。 */
    static void close() {if(chain!=null){chain.close();chain=null;}width=height=0;}
}
